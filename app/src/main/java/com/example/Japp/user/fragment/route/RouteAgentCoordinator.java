package com.example.Japp.user.fragment.route;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import com.example.Japp.network.api.UserService;
import com.example.Japp.network.models.RouteAgent;
import com.example.Japp.user.util.RouteAgentHelper;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Account-scoped durable run state. Detaching stops transport, not the server task. */
final class RouteAgentCoordinator {
    interface Store {
        String read();
        void write(String value);
    }
    interface Scheduler {
        void post(Runnable action, long delayMillis);
        void remove(Runnable action);
    }
    interface Listener {
        void onProgress(String text, boolean cancelling);
        void onResult(String runId, RouteAgent.Result result);
        void onError(String message, boolean unauthorized);
        void onCancelled();
    }

    static final class State {
        String memoryId;
        List<RouteAgent.Turn> history = new ArrayList<>();
        JsonObject constraints;
        RouteAgent.Request request;
        String routeSnapshot;
        String runId;
        String status;
        RouteAgent.Result result;
        boolean busy;
        boolean cancelRequested;
        boolean cancelAcknowledged;
        boolean delivered;
        boolean applied;
        String appliedSnapshot;
    }

    final State state;
    private final UserService service;
    private final Store store;
    private final Gson gson = new Gson();
    private final Scheduler scheduler;
    private Listener listener;
    private Call<RouteAgent.Run> call;
    private int generation;
    private int networkFailures;
    private final Runnable poll = this::query;

    RouteAgentCoordinator(Context context, int accountId, UserService service) {
        this(service, androidStore(context, accountId), androidScheduler());
    }

    RouteAgentCoordinator(UserService service, Store store, Scheduler scheduler) {
        this.service = service;
        this.store = store;
        this.scheduler = scheduler;
        State loaded = null;
        try { loaded = gson.fromJson(store.read(), State.class); }
        catch (RuntimeException ignored) { }
        state = loaded == null ? new State() : loaded;
        if (state.history == null) state.history = new ArrayList<>();
    }

    private static Store androidStore(Context context, int accountId) {
        SharedPreferences preferences = context.getApplicationContext()
                .getSharedPreferences("route_agent_v1", Context.MODE_PRIVATE);
        String key = "account_" + accountId;
        return new Store() {
            @Override public String read() { return preferences.getString(key, ""); }
            @Override public void write(String value) { preferences.edit().putString(key, value).apply(); }
        };
    }

    private static Scheduler androidScheduler() {
        Handler handler = new Handler(Looper.getMainLooper());
        return new Scheduler() {
            @Override public void post(Runnable action, long delayMillis) { handler.postDelayed(action, delayMillis); }
            @Override public void remove(Runnable action) { handler.removeCallbacks(action); }
        };
    }

    void attach(Listener listener) {
        this.listener = listener;
        if (state.busy) {
            listener.onProgress(state.cancelRequested ? "正在取消规划…" : "正在恢复规划进度…", state.cancelRequested);
            if (state.runId == null) create();
            else if (state.cancelRequested) cancel();
            else query();
        }
    }

    void start(RouteAgent.Request request, String snapshot) {
        if (state.busy) return;
        generation++;
        state.memoryId = request.memoryId;
        state.request = request;
        state.routeSnapshot = snapshot;
        state.runId = null;
        state.result = null;
        state.status = "queued";
        state.busy = true;
        state.cancelRequested = false;
        state.cancelAcknowledged = false;
        state.delivered = false;
        state.applied = false;
        networkFailures = 0;
        save();
        create();
    }

    boolean retry() {
        if (state.busy || state.request == null || !"transport_error".equals(state.status)) return false;
        state.busy = true;
        networkFailures = 0;
        save();
        if (state.runId != null) {
            if (state.cancelRequested) cancel(); else query();
        } else create(); // Same immutable requestId AND body: safe after a lost 202 response.
        return true;
    }

    void cancel() {
        if (!state.busy) return;
        state.cancelRequested = true;
        save();
        if (listener != null) listener.onProgress("正在取消规划，等待服务确认…", true);
        if (state.runId == null) return; // Create may already have reached the server.
        generation++;
        if (call != null) call.cancel();
        scheduler.remove(poll);
        execute(service.cancelAgentRun(state.runId), 200);
    }

    void newConversation() {
        if (state.busy) return;
        generation++;
        state.memoryId = null;
        state.history.clear();
        state.constraints = null;
        state.request = null;
        state.result = null;
        state.runId = null;
        state.status = null;
        state.cancelRequested = false;
        save();
    }

    void markApplied(String snapshot) { state.applied = true; state.appliedSnapshot = snapshot; save(); }

    void detach() {
        listener = null;
        generation++;
        scheduler.remove(poll);
        if (call != null) call.cancel();
        save();
    }

    private void create() { execute(service.createAgentRun(state.request), 202); }
    private void query() {
        if (state.busy && listener != null && state.runId != null) {
            if (state.cancelRequested && !state.cancelAcknowledged) cancel();
            else execute(service.getAgentRun(state.runId), 200);
        }
    }

    private void execute(Call<RouteAgent.Run> next, int expectedStatus) {
        if (listener == null) return;
        final int expectedGeneration = generation;
        call = next;
        next.enqueue(new Callback<RouteAgent.Run>() {
            @Override public void onResponse(Call<RouteAgent.Run> source, Response<RouteAgent.Run> response) {
                if (expectedGeneration != generation || listener == null) return;
                networkFailures = 0;
                if (response.code() != expectedStatus || response.body() == null) {
                    String raw = "";
                    try { if (response.errorBody() != null) raw = response.errorBody().string(); }
                    catch (Exception ignored) { }
                    fail(RouteAgentHelper.error(response.code(), raw), response.code() == 401,
                            response.code() >= 500);
                    return;
                }
                if (source.request().method().equals("DELETE")) state.cancelAcknowledged = true;
                receive(response.body());
            }
            @Override public void onFailure(Call<RouteAgent.Run> source, Throwable error) {
                if (expectedGeneration != generation || listener == null || source.isCanceled()) return;
                if (state.runId != null && ++networkFailures <= 3) {
                    listener.onProgress("连接暂时中断，正在恢复规划进度…", state.cancelRequested);
                    scheduler.post(poll, 2000L * networkFailures);
                } else {
                    fail("连接中断，点击重试可查询原任务或安全重发原请求", false, true);
                }
            }
        });
    }

    private void receive(RouteAgent.Run run) {
        if (run.runId == null || !run.runId.matches("[a-f0-9]{32}")
                || (state.runId != null && !state.runId.equals(run.runId))) {
            fail("规划服务返回了无效任务标识", false, false); return;
        }
        state.runId = run.runId;
        state.status = run.status;
        if ("queued".equals(run.status) || "running".equals(run.status)) {
            save();
            listener.onProgress(state.cancelRequested ? "正在取消规划，等待服务确认…" : RouteAgentHelper.progress(run),
                    state.cancelRequested);
            // If cancellation was requested while creation was in flight, send DELETE now.
            if (state.cancelRequested && call.request().method().equals("POST")) cancel();
            else scheduler.post(poll, 2000);
            return;
        }
        state.busy = false;
        scheduler.remove(poll);
        if ("completed".equals(run.status) && run.result != null
                && ("plan".equals(run.result.kind) || "clarification".equals(run.result.kind))) {
            state.result = run.result;
            state.constraints = run.result.constraints;
            if (!state.delivered) {
                state.history.add(new RouteAgent.Turn("user", state.request.message));
                state.history.add(new RouteAgent.Turn("assistant", RouteAgentHelper.describe(run.result)));
                while (state.history.size() > 24) state.history.remove(0);
                state.delivered = true;
                save();
                listener.onResult(run.runId, run.result);
            }
        } else if ("cancelled".equals(run.status)) {
            save(); listener.onCancelled();
        } else {
            fail("failed".equals(run.status) ? RouteAgentHelper.safeError(run.error)
                    : "规划服务返回了无法识别的结果", false, false);
        }
    }

    private void fail(String message, boolean unauthorized, boolean recoverable) {
        state.busy = false;
        state.status = recoverable ? "transport_error" : "failed";
        scheduler.remove(poll);
        save();
        if (listener != null) listener.onError(message, unauthorized);
    }

    private void save() { store.write(gson.toJson(state)); }
}
