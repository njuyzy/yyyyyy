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
import okhttp3.MediaType;
import okhttp3.ResponseBody;
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
        assertEquals(1, h.coordinator.state.history.size());
        assertEquals("user", h.coordinator.state.history.get(0).role);
    }

    @Test public void liveEmbeddingFailureEndsRunEvenWhenToolSnapshotStillSaysRunning() {
        Harness h = new Harness();
        h.replies.add(run("running", null));
        RouteAgent.Run failed = new Gson().fromJson("{\"runId\":\"" + ID + "\",\"status\":\"failed\","
                + "\"stage\":\"规划失败\",\"events\":[{\"tool\":\"search_places\",\"label\":\"检索候选地点\","
                + "\"status\":\"running\",\"elapsedMs\":null}],\"result\":null,"
                + "\"error\":\"I/O error on POST request for \\\"http://localhost:11434/v1/embeddings\\\": "
                + "finishConnect(..) failed with error(-111): Connection refused: localhost/127.0.1.1:11434\"}", RouteAgent.Run.class);
        h.replies.add(failed);
        h.start(); h.scheduler.tick();
        assertFalse(h.coordinator.state.busy);
        assertEquals("failed", h.coordinator.state.status);
        assertEquals(0, h.results);
        assertEquals(1, h.coordinator.state.history.size());
        assertEquals("研学需求", h.coordinator.state.history.get(0).content);
        assertNull(h.scheduler.next);
        assertTrue(h.error.contains("向量嵌入服务"));
        assertFalse(h.error.contains("localhost"));
        assertFalse(h.error.contains("11434"));
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
        RouteAgent.Result plan = validPlan();
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
        h.coordinator = new RouteAgentCoordinator(h.service, h.store, h.scheduler, () -> h.now);
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

    private static RouteAgent.Result validPlan() {
        return new Gson().fromJson("{\"kind\":\"plan\",\"feasible\":true,\"requiresAcceptance\":true,\"issues\":[],"
                + "\"nodes\":[{\"poiId\":\"B001\",\"name\":\"博物馆\",\"location\":\"118.8,32.0\","
                + "\"recommendedDuration\":60,\"visitTime\":\"第1天 09:00\"}],"
                + "\"days\":[{\"day\":1,\"totalMinutes\":60}]}", RouteAgent.Result.class);
    }

    private static RouteAgent.Result clarification() {
        RouteAgent.Result result = new RouteAgent.Result(); result.kind = "clarification";
        result.questions = Collections.singletonList("哪天出行？"); return result;
    }

    private static Response<RouteAgent.Run> httpError(int status) {
        return Response.error(status, ResponseBody.create(MediaType.parse("application/json"), "{\"error\":\"暂不可用\"}"));
    }

    @Test public void malformedCompletionDoesNotEnterHistoryOrBecomeApplicable() {
        Harness h = new Harness(); RouteAgent.Result invalid = validPlan(); invalid.feasible = false;
        h.replies.add(run("completed", invalid)); h.start();
        assertEquals(0, h.results); assertNull(h.coordinator.state.result);
        assertEquals(1, h.coordinator.state.history.size()); assertTrue(h.error.contains("校验"));
    }

    @Test public void transientHttpFailuresPollOriginalRunWithBoundedBackoff() {
        Harness h = new Harness(); h.replies.add(run("running", null));
        h.replies.add(httpError(503)); h.replies.add(httpError(429));
        h.replies.add(run("completed", clarification())); h.start();
        h.scheduler.tick(); assertEquals(2000, h.scheduler.delay); assertTrue(h.coordinator.state.busy);
        h.scheduler.tick(); assertEquals(4000, h.scheduler.delay);
        h.scheduler.tick(); assertEquals(1, h.results); assertEquals(1, h.created.size());
    }

    @Test public void repeatedHttpFailureStopsAndKeepsOriginalTaskForManualRetry() {
        Harness h = new Harness(); h.replies.add(run("running", null));
        for (int i = 0; i < 4; i++) h.replies.add(httpError(503));
        h.start(); for (int i = 0; i < 4; i++) h.scheduler.tick();
        assertEquals("transport_error", h.coordinator.state.status); assertEquals(ID, h.coordinator.state.runId);
        assertFalse(h.coordinator.state.busy); assertNull(h.scheduler.next);
    }

    @Test public void notFoundNeverRetriesAndDoesNotShowLoginAsInvalid() {
        Harness h = new Harness(); h.replies.add(run("running", null)); h.replies.add(httpError(404));
        h.start(); h.scheduler.tick();
        assertFalse(h.coordinator.state.busy); assertNull(h.scheduler.next); assertEquals(0, h.results);
        assertEquals("failed", h.coordinator.state.status);
    }

    @Test public void requestIsFrozenBeforeAnyRetryDespiteCallerMutations() {
        Harness h = new Harness(); h.replies.add(new IOException("lost reply")); h.replies.add(run("running", null));
        RouteAgent.Request request = RouteAgentHelper.request(1, "memory", "原需求", Collections.emptyList(), null, Collections.emptyList());
        h.coordinator.attach(h); h.coordinator.start(request, "[]");
        request.message = "后续修改"; request.constraints.addProperty("city", "上海");
        assertTrue(h.coordinator.retry()); assertEquals(h.created.get(0), h.created.get(1));
        assertEquals("原需求", h.coordinator.state.request.message);
    }

    @Test public void totalWaitLimitAllowsSafeRecoveryOfSameTask() {
        Harness h = new Harness(); h.replies.add(run("running", null)); h.start();
        h.now += 270001; h.scheduler.tick();
        assertEquals("transport_error", h.coordinator.state.status); assertTrue(h.error.contains("超时"));
        assertEquals(ID, h.coordinator.state.runId); assertNull(h.scheduler.next);
        h.replies.add(run("completed", clarification())); assertTrue(h.coordinator.retry());
        assertEquals(1, h.results); assertEquals(1, h.created.size());
    }

    @Test public void expiredPersistedRunDoesNotPollForeverAfterReattachment() {
        Harness h = new Harness(); h.replies.add(run("running", null)); h.start(); h.coordinator.detach();
        h.now += 270001;
        h.coordinator = new RouteAgentCoordinator(h.service, h.store, h.scheduler, () -> h.now);
        h.coordinator.attach(h); assertFalse(h.coordinator.state.busy);
        assertEquals("transport_error", h.coordinator.state.status); assertEquals(1, h.methods.size());
    }

    private static RouteAgent.Result discussionPlan() {
        RouteAgent.Result plan = validPlan();
        com.example.Japp.network.models.RouteNode second = new Gson().fromJson(
                new Gson().toJson(plan.nodes.get(0)), com.example.Japp.network.models.RouteNode.class);
        second.setPoiId("B002"); second.setName("第二场馆"); second.setAddress("场馆地址");
        plan.nodes.add(second); plan.constraints = new com.google.gson.JsonObject();
        plan.constraints.addProperty("budget", 500); plan.constraints.addProperty("city", "南京");
        return plan;
    }

    @Test public void pendingPlanIsPassedToNextRequestWithoutApplyingIt() {
        Harness h = new Harness(); h.replies.add(run("completed", discussionPlan())); h.start();
        RouteAgent.Request next = h.coordinator.prepareRequest(1, "memory", "第二个不要", Collections.emptyList());
        assertTrue(next.usesCandidateContext); assertEquals(2, next.currentRoute.size());
        assertEquals("B002", next.constraints.getAsJsonArray("avoid").get(0).getAsString());
        assertFalse(h.coordinator.state.discussionApplied);
        assertEquals("第二个不要", next.message);
        assertEquals(2, next.history.size());
        assertTrue(next.history.get(1).content.length() < 600);
        assertTrue(next.history.get(1).content.contains("第二场馆"));
    }

    @Test public void clarificationDoesNotEraseDiscussionPlanAcrossRestart() {
        Harness h = new Harness(); h.replies.add(run("completed", discussionPlan())); h.start();
        RouteAgent.Request next = h.coordinator.prepareRequest(1, "memory", "预算改成100元", Collections.emptyList());
        RouteAgent.Result question = clarification(); question.constraints = next.constraints.deepCopy();
        h.replies.add(run("completed", question)); h.coordinator.start(next, "[]");
        assertEquals("clarification", h.coordinator.state.result.kind);
        assertNotNull(h.coordinator.state.discussionPlan);
        h.coordinator.detach();
        h.coordinator = new RouteAgentCoordinator(h.service, h.store, h.scheduler, () -> h.now);
        RouteAgent.Request third = h.coordinator.prepareRequest(1, "memory", "换掉第二个", Collections.emptyList());
        assertTrue(third.usesCandidateContext); assertEquals(100, third.constraints.get("budget").getAsInt());
        assertEquals("B002", third.constraints.getAsJsonArray("avoid").get(0).getAsString());
        assertEquals(4, third.history.size());
    }

    @Test public void knownQuestionDoesNotStartAnotherTaskOrInvalidateProposal() {
        Harness h = new Harness(); h.replies.add(run("completed", discussionPlan())); h.start();
        int callsBefore = h.methods.size();
        String answer = h.coordinator.answerKnownQuestion("第二个的地址在哪里", Collections.emptyList());
        assertTrue(answer.contains("第二场馆")); assertTrue(answer.contains("场馆地址"));
        assertEquals(callsBefore, h.methods.size()); assertFalse(h.coordinator.state.busy);
        assertEquals("plan", h.coordinator.state.result.kind); assertFalse(h.coordinator.state.applied);
        assertEquals(4, h.coordinator.state.history.size());
    }

    @Test public void staleServerConstraintCannotSilentlyUndoLatestBudget() {
        Harness h = new Harness(); h.replies.add(run("completed", discussionPlan())); h.start();
        RouteAgent.Request next = h.coordinator.prepareRequest(1, "memory", "预算改成100元", Collections.emptyList());
        h.replies.add(run("completed", discussionPlan())); // Server incorrectly returns the old 500 budget.
        h.coordinator.start(next, "[]");
        assertEquals(1, h.results); assertTrue(h.error.contains("未确认本轮约束"));
        assertEquals(100, h.coordinator.state.constraints.get("budget").getAsInt());
        assertNotNull(h.coordinator.state.discussionPlan); assertNull(h.coordinator.state.result);
    }

    @Test public void explicitBudgetChangeIsRetainedWhenServerConfirmsIt() {
        Harness h = new Harness(); h.replies.add(run("completed", discussionPlan())); h.start();
        RouteAgent.Request next = h.coordinator.prepareRequest(1, "memory", "预算改成100元", Collections.emptyList());
        RouteAgent.Result revised = discussionPlan(); revised.constraints = next.constraints.deepCopy();
        h.replies.add(run("completed", revised)); h.coordinator.start(next, "[]");
        assertEquals(2, h.results); assertNull(h.error);
        assertEquals(100, h.coordinator.state.constraints.get("budget").getAsInt());
        assertEquals(4, h.coordinator.state.history.size());
    }

    @Test public void failedUserRequirementSurvivesButRetryDoesNotDuplicateIt() {
        Harness h = new Harness(); h.replies.add(new IOException("lost response"));
        h.replies.add(run("running", null)); h.start();
        assertEquals(1, h.coordinator.state.history.size());
        assertTrue(h.coordinator.retry()); assertEquals(1, h.coordinator.state.history.size());
        assertEquals("研学需求", h.coordinator.state.history.get(0).content);
    }

    @Test public void newConversationClearsDiscussionAndSemanticHistory() {
        Harness h = new Harness(); h.replies.add(run("completed", discussionPlan())); h.start();
        h.coordinator.newConversation();
        assertNull(h.coordinator.state.discussionPlan); assertNull(h.coordinator.state.constraints);
        assertNull(h.coordinator.state.pendingConstraintPatch); assertTrue(h.coordinator.state.history.isEmpty());
        RouteAgent.Request next = h.coordinator.prepareRequest(1, "new-memory", "新行程", Collections.emptyList());
        assertFalse(next.usesCandidateContext); assertTrue(next.currentRoute.isEmpty());
    }

    @Test public void applyingPlanRecordsConfirmationAndUsesActualRouteAfterwards() {
        Harness h = new Harness(); RouteAgent.Result plan = discussionPlan();
        h.replies.add(run("completed", plan)); h.start();
        List<com.example.Japp.network.models.RouteNode> actual = Collections.singletonList(plan.nodes.get(0));
        h.coordinator.markApplied(RouteAgentHelper.snapshot(actual));
        RouteAgent.Request next = h.coordinator.prepareRequest(1, "memory", "再调整", actual);
        assertFalse(next.usesCandidateContext); assertEquals(1, next.currentRoute.size());
        assertTrue(h.coordinator.state.history.get(2).content.contains("用户确认应用"));
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
        long now = 1000L;
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
        RouteAgentCoordinator coordinator = new RouteAgentCoordinator(service, store, scheduler, () -> now);
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
            else if (reply instanceof Response) callback.onResponse(this, (Response<RouteAgent.Run>) reply);
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
