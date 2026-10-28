package com.example.Japp.network.agent;

import com.example.Japp.network.api.UserService;
import com.example.Japp.network.models.Result;
import com.example.Japp.network.models.RouteNode;
import com.google.gson.JsonElement;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class AttractionSyncCoordinatorTest {
    List<FakeCall<Result<JsonElement>>> calls;
    List<String> ids;
    AttractionSyncCoordinator sync;
    static final class Listener implements AttractionSyncCoordinator.Listener {
        int completed, errors, lastProgress;
        @Override public void onProgress(int done, int total) { lastProgress = done; }
        @Override public void onComplete() { completed++; }
        @Override public void onError(String message, boolean unauthorized) { errors++; }
    }
    Listener listener;
    @Before public void setup() {
        calls = new ArrayList<>(); ids = new ArrayList<>(); listener = new Listener();
        UserService api = (UserService) Proxy.newProxyInstance(UserService.class.getClassLoader(),
                new Class[]{UserService.class}, (proxy, method, arguments) -> {
                    if (!"syncAttraction".equals(method.getName())) throw new AssertionError(method.getName());
                    ids.add((String) arguments[0]); FakeCall<Result<JsonElement>> call = new FakeCall<>();
                    calls.add(call); return call;
                });
        sync = new AttractionSyncCoordinator(api);
    }
    static RouteNode node(String id) { RouteNode node = new RouteNode(); node.setPoiId(id); return node; }
    static Result<JsonElement> body(int code) { Result<JsonElement> result = new Result<>(); result.setCode(code); return result; }

    @Test public void allSuccessfulCallbacksRequiredBeforeApplication() {
        sync.sync(Arrays.asList(node("A"), node("B")), listener);
        calls.get(0).reply(body(1)); assertEquals(0, listener.completed);
        calls.get(1).reply(body(1)); assertEquals(1, listener.completed); assertEquals(2, listener.lastProgress);
    }
    @Test public void failurePreventsApplicationAndCancelsRemainingSyncs() {
        sync.sync(Arrays.asList(node("A"), node("B")), listener);
        calls.get(0).reply(body(0)); assertEquals(1, listener.errors); assertTrue(calls.get(1).cancelled);
        calls.get(1).reply(body(1)); assertEquals(0, listener.completed);
    }
    @Test public void boundedConcurrencyAndPoiDeduplication() {
        sync.sync(Arrays.asList(node("A"), node("B"), node("C"), node("D"), node("A")), listener);
        assertEquals(3, calls.size()); calls.get(0).reply(body(1)); assertEquals(4, calls.size());
        calls.get(1).reply(body(1)); calls.get(2).reply(body(1)); calls.get(3).reply(body(1));
        assertEquals(Arrays.asList("A", "B", "C", "D"), ids); assertEquals(1, listener.completed);
    }
    @Test public void cancelledOperationIgnoresLateSuccesses() {
        sync.sync(Arrays.asList(node("A")), listener); sync.cancel(); calls.get(0).reply(body(1));
        assertEquals(0, listener.completed); assertEquals(0, listener.errors);
    }
    @Test public void missingPoiFailsBeforeAnyNetworkWrite() {
        sync.sync(Arrays.asList(node(null)), listener);
        assertEquals(1, listener.errors); assertTrue(calls.isEmpty());
    }
}
