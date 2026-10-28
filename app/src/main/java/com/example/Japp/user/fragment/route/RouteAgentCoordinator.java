package com.example.Japp.user.fragment.route;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import com.example.Japp.network.api.UserService;
import com.example.Japp.network.models.RouteAgent;
import com.example.Japp.user.util.RouteAgentHelper;
import com.example.Japp.user.util.RouteConversationContext;
import com.example.Japp.network.models.RouteNode;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;
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
        long startedAtMillis;
        RouteAgent.Result discussionPlan;
        String discussionSnapshot;
        boolean discussionApplied;
        JsonObject pendingConstraintPatch;
        int conversationVersion;
    }

    final State state;
    private final UserService service;
    private final Store store;
    private final Gson gson = new Gson();
    private final Scheduler scheduler;
    private final LongSupplier clock;
    private static final long WAIT_LIMIT_MILLIS = 270_000L;
    private Listener listener;
    private Call<RouteAgent.Run> call;
    private int generation;
    private int networkFailures;
    private final Runnable poll = this::query;

    RouteAgentCoordinator(Context context, int accountId, UserService service) {
        this(service, androidStore(context, accountId), androidScheduler());
    }

    RouteAgentCoordinator(UserService service, Store store, Scheduler scheduler) {
        this(service, store, scheduler, System::currentTimeMillis);
    }

    RouteAgentCoordinator(UserService service, Store store, Scheduler scheduler, LongSupplier clock) {
        this.service = service;
        this.store = store;
        this.scheduler = scheduler;
        this.clock = clock;
        State loaded = null;
        try { loaded = gson.fromJson(store.read(), State.class); }
        catch (RuntimeException ignored) { }
        state = loaded == null ? new State() : loaded;
        if (state.history == null) state.history = new ArrayList<>();
        if (state.busy && state.startedAtMillis == 0) state.startedAtMillis = clock.getAsLong();
        if (state.conversationVersion == 0) {
            if (state.result != null && "plan".equals(state.result.kind)) {
                state.discussionPlan = state.result;
                state.discussionApplied = state.applied;
                state.discussionSnapshot = state.applied ? state.appliedSnapshot : state.routeSnapshot;
            }
            for (int i = state.history.size() - 1; i >= 0; i--) {
                RouteAgent.Turn turn = state.history.get(i);
                if (state.result != null && turn != null && "assistant".equals(turn.role) && turn.content != null
                        && (turn.content.startsWith("候选路线") || turn.content.startsWith("需要补充信息"))) {
                    turn.content = RouteConversationContext.memorySummary(state.result);
                    break;
                }
            }
            state.conversationVersion = 1;
        }
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
            if (waitExpired()) return;
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
        state.request = gson.fromJson(gson.toJson(request), RouteAgent.Request.class);
        state.pendingConstraintPatch = request.explicitConstraintPatch == null
                ? new JsonObject() : request.explicitConstraintPatch.deepCopy();
        state.constraints = request.constraints == null ? new JsonObject() : request.constraints.deepCopy();
        // The user's words remain context even if a service dependency fails. Retries never add them again.
        remember("user", request.message);
        state.routeSnapshot = snapshot;
        state.runId = null;
        state.result = null;
        state.status = "queued";
        state.busy = true;
        state.cancelRequested = false;
        state.cancelAcknowledged = false;
        state.delivered = false;
        state.applied = false;
        state.appliedSnapshot = null;
        state.startedAtMillis = clock.getAsLong();
        networkFailures = 0;
        save();
        create();
    }

    boolean retry() {
        if (state.busy || state.request == null || !"transport_error".equals(state.status)) return false;
        state.busy = true;
        networkFailures = 0;
        state.startedAtMillis = clock.getAsLong();
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
        state.discussionPlan = null;
        state.discussionSnapshot = null;
        state.discussionApplied = false;
        state.pendingConstraintPatch = null;
        save();
    }

    RouteAgent.Request prepareRequest(int accountId, String memoryId, String message, List<RouteNode> actualNodes) {
        return RouteConversationContext.prepare(accountId, memoryId, message, state.history, state.constraints,
                actualNodes, state.discussionPlan, state.discussionApplied, state.discussionSnapshot);
    }

    String answerKnownQuestion(String message, List<RouteNode> actualNodes) {
        if (state.busy) return null;
        boolean candidate = state.discussionPlan != null && RouteAgentHelper.validResult(state.discussionPlan)
                && !state.discussionApplied && RouteAgentHelper.snapshot(actualNodes).equals(state.discussionSnapshot);
        List<RouteNode> nodes = candidate ? state.discussionPlan.nodes : actualNodes;
        String answer = RouteConversationContext.answerKnownQuestion(message, nodes, state.constraints);
        if (answer != null) {
            remember("user", message);
            remember("assistant", answer);
            save();
        }
        return answer;
    }

    void markApplied(String snapshot) {
        state.applied = true;
        state.appliedSnapshot = snapshot;
        state.discussionApplied = true;
        state.discussionSnapshot = snapshot;
        remember("assistant", "上一版候选方案已由用户确认应用；后续修改以实际当前路线为准。");
        save();
    }

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
            if (waitExpired()) return;
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
                if (response.code() != expectedStatus || response.body() == null) {
                    String raw = "";
                    try { if (response.errorBody() != null) raw = response.errorBody().string(); }
                    catch (Exception ignored) { }
                    boolean temporary = response.code() >= 500 || response.code() == 429;
                    if (temporary && state.runId != null && ++networkFailures <= 3) {
                        listener.onProgress("路线服务暂时繁忙，正在恢复原任务进度…", state.cancelRequested);
                        scheduler.post(poll, 2000L * networkFailures);
                    } else fail(RouteAgentHelper.error(response.code(), raw), response.code() == 401, temporary);
                    return;
                }
                networkFailures = 0;
                if (source.request().method().equals("DELETE")) state.cancelAcknowledged = true;
                receive(response.body());
            }
            @Override public void onFailure(Call<RouteAgent.Run> source, Throwable error) {
                if (expectedGeneration != generation || listener == null || source.isCanceled()) return;
                if (state.runId != null && ++networkFailures <= 3) {
                    listener.onProgress("连接暂时中断，正在恢复规划进度…", state.cancelRequested);
                    scheduler.post(poll, 2000L * networkFailures);
                } else {
                    fail(RouteAgentHelper.transportError(error), false, true);
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
        if ("completed".equals(run.status) && !RouteAgentHelper.validResult(run.result)) {
            fail("规划结果不完整或未通过校验，当前路线已保留，请重新规划", false, false);
            return;
        }
        if ("completed".equals(run.status)) {
            if ("plan".equals(run.result.kind) && !confirmsLatestPatch(run.result.constraints)) {
                fail("后端未确认本轮约束修改，当前路线已保留，请重试或重新确认条件", false, false);
                return;
            }
            state.result = run.result;
            state.constraints = RouteConversationContext.merge(
                    RouteConversationContext.merge(state.constraints, run.result.constraints), state.pendingConstraintPatch);
            if ("plan".equals(run.result.kind)) {
                state.discussionPlan = run.result;
                state.discussionSnapshot = state.routeSnapshot;
                state.discussionApplied = false;
            }
            if (!state.delivered) {
                remember("assistant", RouteConversationContext.memorySummary(run.result));
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

    private boolean waitExpired() {
        if (clock.getAsLong() - state.startedAtMillis <= WAIT_LIMIT_MILLIS) return false;
        fail(state.cancelRequested ? "取消状态仍未确认，可重试查询原任务；当前路线未变"
                : "等待规划结果超时，可重试查询原任务；当前路线未变", false, true);
        return true;
    }

    private void save() { store.write(gson.toJson(state)); }

    private void remember(String role, String text) {
        state.history.add(new RouteAgent.Turn(role, text));
        while (state.history.size() > 24) state.history.remove(0);
    }

    private boolean confirmsLatestPatch(JsonObject confirmed) {
        if (state.pendingConstraintPatch == null || state.pendingConstraintPatch.size() == 0) return true;
        if (confirmed == null) return false;
        for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry : state.pendingConstraintPatch.entrySet()) {
            if (!confirmed.has(entry.getKey())) return false;
            com.google.gson.JsonElement received = confirmed.get(entry.getKey());
            if (entry.getValue().isJsonArray() && received.isJsonArray()) {
                java.util.Set<com.google.gson.JsonElement> expectedSet = new java.util.HashSet<>();
                java.util.Set<com.google.gson.JsonElement> receivedSet = new java.util.HashSet<>();
                for (com.google.gson.JsonElement item : entry.getValue().getAsJsonArray()) expectedSet.add(item);
                for (com.google.gson.JsonElement item : received.getAsJsonArray()) receivedSet.add(item);
                if (!expectedSet.equals(receivedSet)) return false;
            } else if (!entry.getValue().equals(received)) return false;
        }
        return true;
    }
}
