package com.example.Japp.network.agent;

import com.google.gson.Gson;
import org.junit.Test;
import static org.junit.Assert.*;

public class RouteAgentResultTest {
    private RouteAgentResult plan() {
        return new Gson().fromJson("{\"kind\":\"plan\",\"feasible\":true,\"requiresAcceptance\":true,\"message\":\"研学方案\","
                + "\"nodes\":[{\"poiId\":\"A\",\"name\":\"博物馆\",\"location\":\"118.79,32.06\",\"recommendedDuration\":60}],"
                + "\"days\":[{\"day\":1,\"totalMinutes\":145,\"walkingMeters\":900,\"schedule\":["
                + "{\"name\":\"博物馆\",\"arrival\":\"09:00\",\"departure\":\"10:00\",\"learningGoal\":\"记录历史\"}]}],"
                + "\"unknownCosts\":1,\"warnings\":[\"费用待核实\"]}", RouteAgentResult.class);
    }

    @Test public void validatedProposalContainsScheduleAndUnknownCosts() {
        RouteAgentResult result = plan();
        assertTrue(result.isValidPlan());
        assertTrue(result.description().contains("145 分钟（含通勤和休息）"));
        assertTrue(result.description().contains("记录历史"));
        assertTrue(result.description().contains("1 项费用待核实"));
    }

    @Test public void badCoordinatesAndDuplicateIdsCannotBeApplied() {
        RouteAgentResult result = plan();
        result.nodes.get(0).setLocation("NaN,32");
        assertFalse(result.isValidPlan());
        result.nodes.get(0).setLocation("118.79,32.06");
        result.nodes.add(result.nodes.get(0));
        assertFalse(result.isValidPlan());
    }

    @Test public void infeasibleOrClarificationNeverMutatesRoute() {
        RouteAgentResult result = plan();
        result.feasible = false;
        assertFalse(result.isValidPlan());
        result.feasible = true;
        result.kind = "clarification";
        assertFalse(result.isValidPlan());
    }

    @Test public void zeroTransportEstimateNeverClaimsFreeTripAndWarningsRemainVisible() {
        RouteAgentResult result = plan(); result.estimatedCost = 0;
        result.warnings = java.util.Arrays.asList("某段为直线估算", "有3项费用未核实");
        assertTrue(result.description().contains("已知交通费用估算"));
        assertTrue(result.description().contains("不能据此判断全程免费"));
        assertTrue(result.description().contains("某段为直线估算"));
        assertTrue(result.description().contains("有3项费用未核实"));
    }
}
