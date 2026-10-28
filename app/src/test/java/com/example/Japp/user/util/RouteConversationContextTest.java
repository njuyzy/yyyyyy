package com.example.Japp.user.util;

import com.example.Japp.network.models.RouteAgent;
import com.example.Japp.network.models.RouteNode;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class RouteConversationContextTest {
    private RouteAgent.Result plan() {
        try (java.io.InputStream input = getClass().getResourceAsStream("/agent/nanjing-live-plan-2026-10-28.json")) {
            return new Gson().fromJson(new java.io.InputStreamReader(input,
                    java.nio.charset.StandardCharsets.UTF_8), RouteAgent.Run.class).result;
        } catch (Exception error) { throw new AssertionError(error); }
    }
    private RouteAgent.Request prepare(String message, RouteAgent.Result plan) {
        return RouteConversationContext.prepare(1, "memory", message, Collections.emptyList(),
                plan.constraints, Collections.emptyList(), plan, false, "[]");
    }

    @Test public void unappliedProposalIsCurrentDiscussionWithoutChangingActualRoute() {
        RouteAgent.Result plan = plan(); List<RouteNode> actual = new ArrayList<>();
        RouteAgent.Request request = RouteConversationContext.prepare(1, "memory", "再轻松一些", Collections.emptyList(),
                plan.constraints, actual, plan, false, "[]");
        assertTrue(request.usesCandidateContext); assertEquals(3, request.currentRoute.size());
        assertTrue(actual.isEmpty()); assertEquals(plan.nodes.get(1).getPoiId(), request.currentRoute.get(1).poiId);
    }

    @Test public void manualEditsTakePriorityOverUnappliedStaleProposal() {
        RouteAgent.Result plan = plan(); List<RouteNode> actual = Collections.singletonList(plan.nodes.get(2));
        RouteAgent.Request request = RouteConversationContext.prepare(1, "memory", "调整一下", Collections.emptyList(),
                plan.constraints, actual, plan, false, "[]");
        assertFalse(request.usesCandidateContext); assertEquals(1, request.currentRoute.size());
        assertEquals(actual.get(0).getPoiId(), request.currentRoute.get(0).poiId);
    }

    @Test public void appliedPlanUsesActualNodesInsteadOfOriginalProposal() {
        RouteAgent.Result plan = plan(); List<RouteNode> actual = Collections.singletonList(plan.nodes.get(0));
        RouteAgent.Request request = RouteConversationContext.prepare(1, "memory", "优化一下", Collections.emptyList(),
                plan.constraints, actual, plan, true, RouteAgentHelper.snapshot(actual));
        assertFalse(request.usesCandidateContext); assertEquals(1, request.currentRoute.size());
    }

    @Test public void explicitBudgetChangeUpdatesWireConstraintWithoutMutatingPreviousPlan() {
        RouteAgent.Result plan = plan(); RouteAgent.Request request = prepare("预算改成100元，其他不变", plan);
        assertEquals(100, request.constraints.get("budget").getAsInt());
        assertEquals(500, plan.constraints.get("budget").getAsInt());
        assertEquals("预算改成100元，其他不变", request.message);
        String json = new Gson().toJson(request);
        assertFalse(json.contains("explicitConstraintPatch")); assertFalse(json.contains("usesCandidateContext"));
    }

    @Test public void explicitNumbersDoNotInferUnrelatedBudgetOrWalkingLimits() {
        JsonObject patch = RouteConversationContext.explicitPatch("场馆有100件藏品，展厅长500米，交通约20分钟");
        assertEquals(0, patch.size());
        assertEquals(0, RouteConversationContext.explicitPatch("不要把预算改成100元").size());
    }

    @Test public void constraintsCanBeExplicitlyCleared() {
        RouteAgent.Request request = prepare("取消预算限制，步行距离不限", plan());
        assertTrue(request.constraints.get("budget").isJsonNull());
        assertTrue(request.constraints.get("maxWalkingMeters").isJsonNull());
    }

    @Test public void negatedChangesDoNotClearLimitsOrForceTransportation() {
        assertEquals(0, RouteConversationContext.explicitPatch("不要取消预算限制，别取消步行上限，不要只坐公交").size());
        assertEquals(0, RouteConversationContext.explicitPatch("不要把开始时间改成10:00").size());
    }

    @Test public void explicitWalkingAndDurationUseCorrectUnits() {
        RouteAgent.Request request = prepare("步行上限改成1.5公里，每天4小时，结束时间改成15:00", plan());
        assertEquals(1500, request.constraints.get("maxWalkingMeters").getAsInt());
        assertEquals(240, request.constraints.get("dailyMinutes").getAsInt());
        assertEquals("15:00", request.constraints.get("finishByTime").getAsString());
    }

    @Test public void cityChangeDoesNotCarryOldCityProposal() {
        RouteAgent.Request request = prepare("城市改成上海，日期2026年11月2日，开始时间改为10:00", plan());
        assertEquals("上海", request.constraints.get("city").getAsString());
        assertEquals("2026-11-02", request.constraints.get("date").getAsString());
        assertEquals("10:00", request.constraints.get("startTime").getAsString());
        assertTrue(request.currentRoute.isEmpty()); assertFalse(request.usesCandidateContext);
    }

    @Test public void ordinalRemovalResolvesToActualPoiAndReleasesConflictingMustVisit() {
        RouteAgent.Result plan = plan(); String removedId = plan.nodes.get(1).getPoiId();
        plan.constraints.getAsJsonArray("mustVisit").add(plan.nodes.get(1).getName());
        RouteAgent.Request request = prepare("第二个不要，其他保持不变", plan);
        assertEquals(removedId, request.constraints.getAsJsonArray("avoid").get(0).getAsString());
        assertEquals(0, request.constraints.getAsJsonArray("mustVisit").size());
        assertEquals(3, request.currentRoute.size()); // The pre-edit route remains a baseline, not a silent application.
        assertEquals(1, plan.constraints.getAsJsonArray("mustVisit").size());
    }

    @Test public void negativeInstructionAndQuantitiesAreNotMistakenForOrdinalRemoval() {
        assertFalse(prepare("第二个不要换，只改预算100元", plan()).explicitConstraintPatch.has("avoid"));
        assertFalse(prepare("删掉2个景点", plan()).explicitConstraintPatch.has("avoid"));
        assertFalse(prepare("只保留第二个", plan()).explicitConstraintPatch.has("avoid"));
    }

    @Test(expected = IllegalArgumentException.class) public void invalidOrdinalCannotTriggerBlindDeletion() {
        prepare("删掉第二十个", plan());
    }
    @Test(expected = IllegalArgumentException.class) public void invalidBudgetRejectedBeforeNetwork() {
        prepare("预算改成-10元", plan());
    }
    @Test(expected = IllegalArgumentException.class) public void invalidDateRejectedBeforeNetwork() {
        prepare("出发日期改为2026-02-30", plan());
    }
    @Test(expected = IllegalArgumentException.class) public void overLimitWalkingRejectedBeforeNetwork() {
        prepare("步行上限改为200公里", plan());
    }

    @Test public void memoryIsCompactStructuredAndIndependentOfNavigationCopy() {
        RouteAgent.Result plan = plan(); plan.warnings = Collections.nCopies(100, "很长的提示");
        String memory = RouteConversationContext.memorySummary(plan);
        assertTrue(memory.length() < 600); assertTrue(memory.contains("尚不代表已应用"));
        assertTrue(memory.contains("2." + plan.nodes.get(1).getName()));
        assertTrue(memory.contains("budget=500")); assertFalse(memory.contains("导航"));
    }

    @Test public void factualQuestionUsesCurrentProposalWithoutReplanning() {
        RouteAgent.Result plan = plan(); String answer = RouteConversationContext.answerKnownQuestion(
                "第二个的地址在哪里？", plan.nodes, plan.constraints);
        assertTrue(answer.contains(plan.nodes.get(1).getName()));
        assertTrue(answer.contains(plan.nodes.get(1).getAddress()));
        assertTrue(RouteConversationContext.answerKnownQuestion("预算是多少？", plan.nodes, plan.constraints).contains("500"));
        assertTrue(RouteConversationContext.answerKnownQuestion("最后一站停留多久？", plan.nodes, plan.constraints).contains("90"));
    }

    @Test public void compoundModificationAndAmbiguousReferencesAreNotAnsweredAsSimpleFacts() {
        RouteAgent.Result plan = plan();
        assertNull(RouteConversationContext.answerKnownQuestion("第二个地址在哪，顺便换掉它", plan.nodes, plan.constraints));
        assertNull(RouteConversationContext.answerKnownQuestion("第一个和第二个的地址是什么", plan.nodes, plan.constraints));
        assertNull(RouteConversationContext.answerKnownQuestion("这个方案适合孩子吗", plan.nodes, plan.constraints));
    }

    @Test public void missingOpeningHoursAreNotInvented() {
        RouteAgent.Result plan = plan(); plan.nodes.get(1).setOpentimeToday(""); plan.nodes.get(1).setOpentimeWeek("");
        String answer = RouteConversationContext.answerKnownQuestion("第二个的开放时间是什么", plan.nodes, plan.constraints);
        assertTrue(answer.contains("未核实")); assertFalse(answer.contains("09:00"));
    }
}
