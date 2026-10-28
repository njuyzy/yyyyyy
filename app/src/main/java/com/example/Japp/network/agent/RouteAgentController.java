package com.example.Japp.network.agent;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.function.LongSupplier;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Poll until a terminal state, including deferred cancellation checkpoints. */
public final class RouteAgentController {
    public interface Listener {
        void onProgress(RouteAgentRun run);
        void onComplete(RouteAgentResult result);
        void onError(String message, boolean unauthorized);
        default void onCancelled() { onError("规划已取消", false); }
    }

    interface Scheduler {
        void postDelayed(Runnable task, long delay);
        void clear();
    }

    private final RouteAgentService service;
    private final Scheduler scheduler;
    private final LongSupplier clock;
    private Call<RouteAgentRun> call;
    private String runId;
    private int generation;
    private int failures;
    private long startedAt;
    private Listener listener;
    private boolean cancellationRequested;
    private boolean cancellationSubmitted;

    public RouteAgentController(RouteAgentService service) {
        this(service, androidScheduler(), SystemClock::elapsedRealtime);
    }

    RouteAgentController(RouteAgentService service, Scheduler scheduler, LongSupplier clock) {
        this.service = service;
        this.scheduler = scheduler;
        this.clock = clock;
    }

    private static Scheduler androidScheduler() {
        Handler handler = new Handler(Looper.getMainLooper());
        return new Scheduler() {
            @Override public void postDelayed(Runnable task, long delay) { handler.postDelayed(task, delay); }
            @Override public void clear() { handler.removeCallbacksAndMessages(null); }
        };
    }

    public void start(RouteAgentRequest request, Listener listener) {
        close();
        this.listener = listener;
        startedAt = clock.getAsLong();
        failures = 0;
        submit(request, generation, 0);
    }

    private void submit(RouteAgentRequest request, int owner, int attempt) {
        if (owner != generation) return;
        call = service.start(request);
        call.enqueue(new Callback<RouteAgentRun>() {
            @Override public void onResponse(Call<RouteAgentRun> pending, Response<RouteAgentRun> response) {
                if (owner == generation) handle(response, owner);
            }
            @Override public void onFailure(Call<RouteAgentRun> pending, Throwable error) {
                if (owner != generation || pending.isCanceled()) return;
                if (attempt < 2) {
                    // Keep the same request body to discover any task created before a lost response.
                    scheduler.postDelayed(() -> submit(request, owner, attempt + 1), 2000L * (attempt + 1));
                } else fail(cancellationRequested
                        ? "未能确认取消状态，请稍后重试，当前路线未变"
                        : "无法连接路线助手，请检查网络后重试", false);
            }
        });
    }

    private void poll(int owner) {
        if (owner != generation || runId == null) return;
        if (clock.getAsLong() - startedAt > 270000) {
            cancelRemote();
            fail(cancellationRequested ? "取消状态仍未确认，请稍后重试，当前路线未变"
                    : "规划耗时较长，请减少地点或明确优先级后重试", false);
            return;
        }
        call = service.status(runId);
        call.enqueue(new Callback<RouteAgentRun>() {
            @Override public void onResponse(Call<RouteAgentRun> pending, Response<RouteAgentRun> response) {
                if (owner == generation) handle(response, owner);
            }
            @Override public void onFailure(Call<RouteAgentRun> pending, Throwable error) {
                if (owner != generation || pending.isCanceled()) return;
                retryPoll(owner);
            }
        });
    }

    private void retryPoll(int owner) {
        if (++failures > 3) {
            cancelRemote();
            fail(cancellationRequested ? "暂未确认取消结果，请检查网络后重试"
                    : "获取规划进度失败，请检查网络后重试", false);
        } else scheduler.postDelayed(() -> poll(owner), failures * 2000L);
    }

    private void handle(Response<RouteAgentRun> response, int owner) {
        if (!response.isSuccessful() || response.body() == null) {
            int code = response.code();
            if (runId != null && (code >= 500 || code == 429)) {
                retryPoll(owner);
                return;
            }
            fail(AgentApiErrors.message(response, runId != null), code == 401);
            return;
        }
        failures = 0;
        RouteAgentRun run = response.body();
        if (run.runId == null || !run.runId.matches("[a-f0-9]{32}")) {
            fail("规划结果缺少必要信息，请重试", false);
            return;
        }
        runId = run.runId;
        if (listener != null) listener.onProgress(run);
        // Cancellation can be requested before the create response reveals the run ID.
        if (cancellationRequested && !cancellationSubmitted
                && ("queued".equals(run.status) || "running".equals(run.status))) {
            sendCancellation(owner, 0);
            return;
        }
        if ("completed".equals(run.status)) {
            Listener completed = listener;
            RouteAgentResult result = run.result;
            if (result == null || (!result.isValidClarification() && !result.isValidPlan())) {
                fail("规划结果未通过校验，请重新发送需求", false);
                return;
            }
            finish();
            if (completed != null) completed.onComplete(result);
        } else if ("failed".equals(run.status)) {
            fail(run.error == null ? "路线规划失败，请重试" : run.error, false);
        } else if ("cancelled".equals(run.status)) {
            Listener cancelled = listener;
            finish();
            if (cancelled != null) cancelled.onCancelled();
        } else if ("queued".equals(run.status) || "running".equals(run.status)) {
            scheduler.postDelayed(() -> poll(owner), 1800);
        } else fail("无法识别规划状态，请重试", false);
    }

    public boolean isCancellationRequested() { return cancellationRequested; }

    public void cancel() {
        if (listener == null || cancellationRequested) return;
        cancellationRequested = true;
        if (runId != null) {
            if (call != null) call.cancel();
            scheduler.clear();
            sendCancellation(generation, 0);
        }
        // Without runId, let the create response (or its idempotent retry) finish first.
    }

    private void sendCancellation(int owner, int attempt) {
        if (owner != generation || runId == null) return;
        cancellationSubmitted = true;
        call = service.cancel(runId);
        call.enqueue(new Callback<RouteAgentRun>() {
            @Override public void onResponse(Call<RouteAgentRun> pending, Response<RouteAgentRun> response) {
                if (owner != generation) return;
                if (!response.isSuccessful() && (response.code() >= 500 || response.code() == 429)
                        && attempt < 2) {
                    scheduler.postDelayed(() -> sendCancellation(owner, attempt + 1), 2000L * (attempt + 1));
                } else handle(response, owner);
                // A 200/running response is acknowledgement only; handle keeps polling.
            }
            @Override public void onFailure(Call<RouteAgentRun> pending, Throwable error) {
                if (owner != generation || pending.isCanceled()) return;
                if (attempt < 2) scheduler.postDelayed(
                        () -> sendCancellation(owner, attempt + 1), 2000L * (attempt + 1));
                else fail("取消请求尚未确认，请检查网络后重试，当前路线未变", false);
            }
        });
    }

    private void fail(String message, boolean unauthorized) {
        Listener failed = listener;
        finish();
        if (failed != null) failed.onError(message, unauthorized);
    }

    private void finish() {
        generation++;
        scheduler.clear();
        call = null;
        listener = null;
        runId = null;
        cancellationRequested = false;
        cancellationSubmitted = false;
    }

    private void cancelRemote() {
        if (runId == null) return;
        service.cancel(runId).enqueue(new Callback<RouteAgentRun>() {
            @Override public void onResponse(Call<RouteAgentRun> call, Response<RouteAgentRun> response) {}
            @Override public void onFailure(Call<RouteAgentRun> call, Throwable error) {}
        });
    }

    public void close() {
        // View disposal sends best-effort cancellation without claiming it already took effect.
        cancelRemote();
        if (call != null) call.cancel();
        finish();
    }
}
