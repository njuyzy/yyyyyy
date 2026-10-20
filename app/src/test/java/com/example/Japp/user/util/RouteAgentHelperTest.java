package com.example.Japp.user.util;

import com.example.Japp.network.api.UserService;
import com.example.Japp.network.models.RouteAgent;
import com.example.Japp.network.models.RouteNode;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.ResponseBody;
import org.junit.Test;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import static org.junit.Assert.*;

public class RouteAgentHelperTest {
    @Test public void requestUsesOnlyAllowedFieldsAndKeepsLatestAnswerOutOfHistory() {
        RouteNode node = new RouteNode();
        node.setPoiId("B001"); node.setName("博物馆"); node.setLocation("118.8,32.0");
        RouteAgent.Request request = RouteAgentHelper.request(7, "conversation-1", "南京，半天",
                Arrays.asList(new RouteAgent.Turn("user", "想去博物馆"),
                        new RouteAgent.Turn("assistant", "哪个城市，多久？"),
                        new RouteAgent.Turn("tool", "不应该发给 Agent")),
                new JsonObject(), Collections.singletonList(node));
        JsonObject json = new Gson().toJsonTree(request).getAsJsonObject();
        assertEquals(7, json.size()); // optional origin omitted
        assertEquals(2, request.history.size());
        assertEquals("南京，半天", request.message);
        assertFalse(json.getAsJsonArray("currentRoute").get(0).getAsJsonObject().has("location"));
        assertTrue(request.requestId.matches("[A-Za-z0-9_-]{1,100}"));
    }

    @Test public void historyIsBoundedAndDoesNotMutateStoredTurns() {
        List<RouteAgent.Turn> history = new ArrayList<>();
        for (int i = 0; i < 30; i++) history.add(new RouteAgent.Turn("user", "turn" + i));
        RouteAgent.Request request = RouteAgentHelper.request(1, "memory", "answer", history, null, Collections.emptyList());
        assertEquals(24, request.history.size());
        assertEquals("turn6", request.history.get(0).content);
        assertEquals(30, history.size());
    }

    @Test(expected = IllegalArgumentException.class) public void duplicatePoisAreRejectedBeforeSending() {
        RouteNode node = new RouteNode(); node.setPoiId("B001");
        RouteAgentHelper.request(1, "memory", "优化", Collections.emptyList(), null, Arrays.asList(node, node));
    }

    @Test(expected = IllegalArgumentException.class) public void memoryIdUsesBackend128CharacterLimit() {
        RouteAgentHelper.request(1, String.join("", Collections.nCopies(129, "a")),
                "需求", Collections.emptyList(), null, Collections.emptyList());
    }

    @Test public void relativeAndAbsoluteTimesBecomeIsoWithoutInventingADate() {
        assertEquals("2028-02-29T09:00:00", RouteAgentHelper.visitTimeToIso("第2天 09:00", LocalDate.of(2028, 2, 28)));
        assertEquals("2026-10-11T09:00:00", RouteAgentHelper.visitTimeToIso("2026-10-11 09:00", null));
        assertEquals("2026-10-11T09:00:00", RouteAgentHelper.visitTimeToIso("2026-10-11T09:00:00", null));
        assertNull(RouteAgentHelper.visitTimeToIso("", null));
    }

    @Test(expected = IllegalArgumentException.class) public void relativeTimeRequiresUserDate() {
        RouteAgentHelper.visitTimeToIso("第1天 09:00", null);
    }

    @Test public void agentErrorsAndValidationDetailsAreReadWithoutLegacyEnvelope() {
        assertEquals("会话已有活跃任务", RouteAgentHelper.error(409, "{\"error\":\"会话已有活跃任务\"}"));
        String error = RouteAgentHelper.error(422, "{\"detail\":[{\"loc\":[\"body\",\"memoryId\"],\"msg\":\"too long\"}]}");
        assertTrue(error.contains("memoryId"));
        assertTrue(error.contains("too long"));
        assertTrue(RouteAgentHelper.error(404, "<html>not found</html>").contains("已过期"));
        assertFalse(RouteAgentHelper.safeError("Bearer secret https://provider.example").contains("secret"));
        assertTrue(RouteAgentHelper.safeError("I/O error on POST http://localhost:11434/v1/embeddings: Connection refused")
                .contains("向量嵌入服务"));
    }

    @Test public void clarificationAndCostWarningsRemainVisible() {
        RouteAgent.Result result = new RouteAgent.Result();
        result.kind = "clarification"; result.message = "信息不足";
        result.questions = Arrays.asList("哪个城市？", "哪一天？");
        assertTrue(RouteAgentHelper.describe(result).contains("哪个城市？"));
        result.kind = "plan"; result.estimatedCost = 0; result.unknownCosts = 3;
        result.warnings = Collections.singletonList("某段为直线估算");
        String text = RouteAgentHelper.describe(result);
        assertTrue(text.contains("非全部费用"));
        assertTrue(text.contains("3 项"));
        assertTrue(text.contains("直线估算"));
    }

    @Test public void retrofitUsesAsyncEndpointsAndDoesNotTreat202AsCompleted() throws Exception {
        RouteAgent.Request request = RouteAgentHelper.request(1, "memory", "南京研学", Collections.emptyList(), null, Collections.emptyList());
        List<String> requests = new ArrayList<>();
        String id = "0123456789abcdef0123456789abcdef";
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain -> {
            requests.add(chain.request().method() + " " + chain.request().url().encodedPath());
            String status = chain.request().method().equals("POST") ? "queued" : "cancelled";
            return new okhttp3.Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(chain.request().method().equals("POST") ? 202 : 200).message("ok")
                    .body(ResponseBody.create(MediaType.parse("application/json"),
                            "{\"runId\":\"" + id + "\",\"status\":\"" + status
                                    + "\",\"stage\":\"展示文案\",\"events\":[],\"result\":null,\"error\":null}"))
                    .build();
        }).build();
        UserService service = new Retrofit.Builder().baseUrl("http://example.test/").client(client)
                .addConverterFactory(GsonConverterFactory.create()).build().create(UserService.class);
        RouteAgent.Run run = service.createAgentRun(request).execute().body();
        assertEquals("queued", run.status);
        assertNull(run.result);
        assertEquals(id, run.runId);
        service.getAgentRun(id).execute();
        service.cancelAgentRun(id).execute();
        assertEquals(Arrays.asList("POST /routes/agent/runs", "GET /routes/agent/runs/" + id,
                "DELETE /routes/agent/runs/" + id), requests);
    }

    @Test public void failedToolEventIsOnlyProgressNotRunFailure() {
        RouteAgent.Run run = new Gson().fromJson("{\"status\":\"running\",\"stage\":\"调整方案\",\"events\":[{\"label\":\"查询交通\",\"status\":\"failed\"}]}", RouteAgent.Run.class);
        String first = RouteAgentHelper.progress(run);
        assertEquals(first, RouteAgentHelper.progress(run));
        assertEquals("running", run.status);
        assertTrue(first.contains("本步骤未完成"));
    }
}
