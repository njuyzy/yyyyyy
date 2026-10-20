package com.example.Japp.user.fragment.route;

import com.example.Japp.network.api.UserService;
import com.example.Japp.network.models.RouteAgent;
import com.example.Japp.user.util.RouteAgentHelper;
import com.google.gson.Gson;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import okhttp3.Request;
import okhttp3.RequestBody;
import okio.Timeout;
import org.junit.Test;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import static org.junit.Assert.*;

public class RouteAgentCoordinatorTest {
    private static final String ID = "0123456789abcdef0123456789abcdef";

    @Test public void acceptedTaskWaitsForPollingAnd200FailedShowsExecutionError() {
        Harness h = new Harness();
        h.replies.add(run("running", null));
        RouteAgent.Run failed = run("failed", null);
        failed.error = "地图鉴权失败";
        h.replies.add(failed);
        h.start();
        assertTrue(h.coordinator.state.busy);
        assertEquals(0, h.results);
        assertEquals(2000, h.scheduler.delay);
        h.scheduler.tick();
        assertFalse(h.coordinator.state.busy);
        assertEquals("地图鉴权失败", h.error);
        assertTrue(h.coordinator.state.history.isEmpty());
    }

    @Test public void clarificationCompletesAndNextAnswerIsNotDuplicatedInHistory() {
        Harness h = new Harness();
        RouteAgent.Result clarification = new RouteAgent.Result();
        clarification.kind = "clarification";
        clarification.questions = Collections.singletonList("哪个城市？");
        h.replies.add(run("completed", clarification));
        h.start();
        assertEquals(1, h.results);
        assertFalse(h.coordinator.state.busy);
        assertEquals(2, h.coordinator.state.history.size());
        RouteAgent.Request next = RouteAgentHelper.request(1, "memory", "南京",
                h.coordinator.state.history, h.coordinator.state.constraints, Collections.emptyList());
        assertEquals("南京", next.message);
        assertEquals(2, next.history.size());
        assertFalse(new Gson().toJson(next.history).contains("南京"));
    }

    @Test public void lostCreateResponseRetriesExactlyTheSameBodyAndRequestId() {
        Harness h = new Harness();
        h.replies.add(new IOException("lost 202"));
        h.replies.add(run("running", null));
        h.start();
        assertEquals("transport_error", h.coordinator.state.status);
        assertTrue(h.coordinator.retry());
        assertEquals(2, h.created.size());
        assertEquals(h.created.get(0), h.created.get(1));
    }

    @Test public void cancellationMayReturnCompletedPlanInsteadOfCancelled() {
        Harness h = new Harness();
        h.replies.add(run("running", null));
        RouteAgent.Result plan = new RouteAgent.Result(); plan.kind = "plan";
        h.replies.add(run("completed", plan));
        h.start();
        h.coordinator.cancel();
        assertEquals(1, h.results);
        assertEquals(0, h.cancelled);
        assertEquals("cancelAgentRun", h.methods.get(1));
        assertFalse(h.coordinator.state.busy);
        assertNull(h.scheduler.next);
    }

    @Test public void runningCancellationContinuesPollingUntilServerConfirms() {
        Harness h = new Harness();
        h.replies.add(run("running", null));
        h.replies.add(run("running", null));
        h.replies.add(run("cancelled", null));
        h.start();
        h.coordinator.cancel();
        assertTrue(h.coordinator.state.busy);
        h.scheduler.tick();
        assertEquals(1, h.cancelled);
        assertEquals("getAgentRun", h.methods.get(2));
        assertFalse(h.coordinator.state.busy);
    }

    @Test public void restoredRunQueriesOriginalTaskWithoutCreatingAnother() {
        Harness h = new Harness();
        h.replies.add(run("running", null));
        h.replies.add(run("cancelled", null));
        h.start();
        h.coordinator.detach();
        h.coordinator = new RouteAgentCoordinator(h.service, h.store, h.scheduler);
        h.coordinator.attach(h);
        assertEquals(1, h.created.size());
        assertEquals("getAgentRun", h.methods.get(1));
        assertEquals(1, h.cancelled);
    }

    private static RouteAgent.Run run(String status, RouteAgent.Result result) {
        RouteAgent.Run run = new RouteAgent.Run();
        run.runId = ID; run.status = status; run.stage = "显示文案"; run.result = result;
        return run;
    }

    private static class MemoryStore implements RouteAgentCoordinator.Store {
        String value = "";
        @Override public String read() { return value; }
        @Override public void write(String value) { this.value = value; }
    }

    private static class ManualScheduler implements RouteAgentCoordinator.Scheduler {
        Runnable next;
        long delay;
        @Override public void post(Runnable action, long delayMillis) { next = action; delay = delayMillis; }
        @Override public void remove(Runnable action) { if (next == action) next = null; }
        void tick() { Runnable action = next; next = null; assertNotNull(action); action.run(); }
    }

    private static class Harness implements RouteAgentCoordinator.Listener {
        final MemoryStore store = new MemoryStore();
        final ManualScheduler scheduler = new ManualScheduler();
        final Deque<Object> replies = new ArrayDeque<>();
        final List<String> created = new ArrayList<>();
        final List<String> methods = new ArrayList<>();
        int results;
        int cancelled;
        String error;
        final UserService service = (UserService) Proxy.newProxyInstance(UserService.class.getClassLoader(),
                new Class<?>[]{UserService.class}, (proxy, method, args) -> {
                    methods.add(method.getName());
                    String verb = "getAgentRun".equals(method.getName()) ? "GET" :
                            ("cancelAgentRun".equals(method.getName()) ? "DELETE" : "POST");
                    if ("POST".equals(verb)) created.add(new Gson().toJson(args[0]));
                    return new FakeCall(verb, replies.removeFirst());
                });
        RouteAgentCoordinator coordinator = new RouteAgentCoordinator(service, store, scheduler);
        void start() {
            coordinator.attach(this);
            coordinator.start(RouteAgentHelper.request(1, "memory", "研学需求", Collections.emptyList(), null,
                    Collections.emptyList()), "[]");
        }
        @Override public void onProgress(String text, boolean cancelling) { }
        @Override public void onResult(String runId, RouteAgent.Result result) { results++; }
        @Override public void onError(String message, boolean unauthorized) { error = message; }
        @Override public void onCancelled() { cancelled++; }
    }

    private static class FakeCall implements Call<RouteAgent.Run> {
        final String verb;
        final Object reply;
        boolean cancelled;
        boolean executed;
        FakeCall(String verb, Object reply) { this.verb = verb; this.reply = reply; }
        @Override public void enqueue(Callback<RouteAgent.Run> callback) {
            executed = true;
            if (reply instanceof Throwable) callback.onFailure(this, (Throwable) reply);
            else callback.onResponse(this, Response.success("POST".equals(verb) ? 202 : 200, (RouteAgent.Run) reply));
        }
        @Override public Response<RouteAgent.Run> execute() { throw new UnsupportedOperationException(); }
        @Override public boolean isExecuted() { return executed; }
        @Override public void cancel() { cancelled = true; }
        @Override public boolean isCanceled() { return cancelled; }
        @Override public Call<RouteAgent.Run> clone() { return new FakeCall(verb, reply); }
        @Override public Request request() {
            return new Request.Builder().url("http://example.test/routes/agent/runs")
                    .method(verb, "POST".equals(verb) ? RequestBody.create(null, "{}") : null).build();
        }
        @Override public Timeout timeout() { return Timeout.NONE; }
    }
}
