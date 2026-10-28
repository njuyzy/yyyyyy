package com.example.Japp.network.agent;

import com.google.gson.Gson;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import org.junit.Before;
import org.junit.Test;
import retrofit2.Call;
import retrofit2.Response;
import static org.junit.Assert.*;

public class RouteAgentControllerTest {
    static final String ID = "a67f99cfbb9f4532a1ee009988776655";
    static final class Api implements RouteAgentService {
        List<FakeCall<RouteAgentRun>> creates = new ArrayList<>(), polls = new ArrayList<>(), deletes = new ArrayList<>();
        List<RouteAgentRequest> requests = new ArrayList<>();
        @Override public Call<RouteAgentRun> start(RouteAgentRequest request) {
            requests.add(request); FakeCall<RouteAgentRun> call = new FakeCall<>(); creates.add(call); return call;
        }
        @Override public Call<RouteAgentRun> status(String id) {
            FakeCall<RouteAgentRun> call = new FakeCall<>(); polls.add(call); return call;
        }
        @Override public Call<RouteAgentRun> cancel(String id) {
            FakeCall<RouteAgentRun> call = new FakeCall<>(); deletes.add(call); return call;
        }
    }
    static final class Tasks implements RouteAgentController.Scheduler {
        ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        @Override public void postDelayed(Runnable task, long delay) { tasks.add(task); }
        @Override public void clear() { tasks.clear(); }
        void next() { tasks.remove().run(); }
    }
    static final class Listener implements RouteAgentController.Listener {
        int completed, cancelled, errors, progress;
        boolean unauthorized;
        String message;
        @Override public void onProgress(RouteAgentRun run) { progress++; }
        @Override public void onComplete(RouteAgentResult result) { completed++; }
        @Override public void onError(String message, boolean unauthorized) {
            errors++; this.message = message; this.unauthorized = unauthorized;
        }
        @Override public void onCancelled() { cancelled++; }
    }
    Api api; Tasks tasks; Listener listener; RouteAgentController controller;
    @Before public void setup() {
        api = new Api(); tasks = new Tasks(); listener = new Listener();
        controller = new RouteAgentController(api, tasks, () -> 0L);
        controller.start(new RouteAgentRequest(1, "route-session", "南京半日", Collections.emptyList(), null), listener);
    }
    static RouteAgentRun run(String status) {
        RouteAgentRun run = new RouteAgentRun(); run.runId = ID; run.status = status;
        run.stage = "任意服务端展示文案"; return run;
    }
    static RouteAgentRun clarification() {
        RouteAgentRun run = run("completed");
        run.result = new Gson().fromJson("{\"kind\":\"clarification\",\"questions\":[\"你计划在哪个城市？\"],\"nodes\":[]}", RouteAgentResult.class);
        return run;
    }
    static Response<RouteAgentRun> error(int code, String json) {
        return Response.error(code, ResponseBody.create(MediaType.parse("application/json"), json));
    }

    @Test public void acceptedCreationDoesNotDeliverSuccess() {
        api.creates.get(0).reply(run("queued"));
        assertEquals(0, listener.completed); tasks.next();
        api.polls.get(0).reply(clarification());
        assertEquals(1, listener.completed); assertEquals(0, listener.errors); assertTrue(tasks.tasks.isEmpty());
    }
    @Test public void deferredCancellationPollsUntilConfirmed() {
        api.creates.get(0).reply(run("running"));
        controller.cancel(); assertTrue(controller.isCancellationRequested());
        api.deletes.get(0).reply(run("running"));
        assertEquals(0, listener.cancelled); assertEquals(0, listener.completed);
        tasks.next(); api.polls.get(0).reply(run("cancelled"));
        assertEquals(1, listener.cancelled); assertEquals(0, listener.errors); assertTrue(tasks.tasks.isEmpty());
    }
    @Test public void cancellationBeforeRunIdWaitsForIdempotentCreation() {
        controller.cancel(); assertTrue(api.deletes.isEmpty());
        api.creates.get(0).reply(run("queued"));
        assertEquals(1, api.deletes.size());
        api.deletes.get(0).reply(run("cancelled"));
        assertEquals(1, listener.cancelled);
    }
    @Test public void completedBeforeCancelRemainsCompletedNotFalselyCancelled() {
        api.creates.get(0).reply(run("running")); controller.cancel();
        api.deletes.get(0).reply(clarification());
        assertEquals(1, listener.completed); assertEquals(0, listener.cancelled);
    }
    @Test public void notFoundIsTerminalAndNeverRetried() {
        api.creates.get(0).reply(run("queued")); tasks.next();
        api.polls.get(0).respond(error(404, "{\"error\":\"不存在\"}"));
        assertEquals(1, listener.errors); assertTrue(listener.message.contains("失效"));
        assertTrue(tasks.tasks.isEmpty()); assertEquals(1, api.polls.size());
    }
    @Test public void createTransportRetryRetainsExactRequestIdentity() {
        api.creates.get(0).failure(); tasks.next();
        assertSame(api.requests.get(0), api.requests.get(1));
        assertEquals(api.requests.get(0).requestId, api.requests.get(1).requestId);
    }
    @Test public void memoryValidationErrorIsActionableWithoutEchoingInput() {
        api.creates.get(0).respond(error(422,
                "{\"detail\":[{\"type\":\"string_too_long\",\"loc\":[\"body\",\"memoryId\"],\"input\":\"PRIVATE_VALUE\"}]}"));
        assertEquals(1, listener.errors); assertTrue(listener.message.contains("新建对话"));
        assertFalse(listener.message.contains("PRIVATE_VALUE"));
    }
    @Test public void executionFailureIsNotConfusedWithHttpSuccess() {
        RouteAgentRun failure = run("failed"); failure.error = "地图鉴权失败";
        api.creates.get(0).reply(failure);
        assertEquals(1, listener.errors); assertEquals(0, listener.completed);
        assertEquals("地图鉴权失败", listener.message);
    }
    @Test public void unconfirmedCancelFailureDoesNotReportCancelled() {
        api.creates.get(0).reply(run("queued")); controller.cancel();
        api.deletes.get(0).failure(); tasks.next();
        api.deletes.get(1).failure(); tasks.next();
        api.deletes.get(2).failure();
        assertEquals(0, listener.cancelled); assertEquals(1, listener.errors);
        assertTrue(listener.message.contains("尚未确认"));
    }
    @Test public void disposedViewIgnoresLateResponses() {
        api.creates.get(0).reply(run("queued")); controller.close();
        int before = listener.progress;
        api.creates.get(0).reply(clarification());
        assertEquals(before, listener.progress); assertEquals(0, listener.completed);
        assertTrue(tasks.tasks.isEmpty());
    }
}
