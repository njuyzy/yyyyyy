package com.example.Japp.user.util;

import org.junit.Test;
import static org.junit.Assert.*;

public class RoutePlanTextTest {
    @Test public void legacyNavigationSectionIsHiddenButBudgetAndWarningsStay() {
        String text = "候选路线\n\n交通安排：\n• 第一站 → 第二站 · 公交约20分钟\n导航步骤\n\n"
                + "已知交通费用估算：0元\n未核价项目：5项\n\n出行提醒：\n某段为直线估算";
        String visible = RoutePlanText.withoutDirections(text);
        assertFalse(visible.contains("交通安排"));
        assertFalse(visible.contains("第一站 → 第二站"));
        assertFalse(visible.contains("导航步骤"));
        assertTrue(visible.contains("已知交通费用估算"));
        assertTrue(visible.contains("5项"));
        assertTrue(visible.contains("直线估算"));
    }

    @Test public void removeDirectionalClauseAndKeepEducationalContent() {
        String visible = RoutePlanText.withoutDirections("从这里步行前往下一个景点，观察历史展品。记录展陈差异。");
        assertFalse(visible.contains("步行前往"));
        assertTrue(visible.contains("观察历史展品"));
        assertTrue(visible.contains("记录展陈差异"));
        assertEquals("", RoutePlanText.withoutDirections("从这里乘坐地铁2号线到下一个景点"));
        assertEquals("", RoutePlanText.withoutDirections("公交站下车步行"));
    }

    @Test public void chineseAndEnglishTurnInstructionsAreNotShown() {
        assertEquals("", RoutePlanText.withoutDirections("向东走100米，然后右转。Walk to the next stop."));
        assertEquals("", RoutePlanText.withoutDirections("沿道路步行至下一个场馆。Turn left at the junction."));
    }

    @Test public void aggregateWalkingAndConstraintsAreNotTurnInstructions() {
        String text = "每日通勤步行不超过2000米。\n通勤步行约2136米\n第1天：约379分钟";
        assertEquals(text, RoutePlanText.withoutDirections(text));
    }

    @Test public void safetyAndUnverifiedCostWarningsAreRetained() {
        String warning = "因施工禁止沿道路步行。门票费用未核实，请预约后再出行。";
        assertEquals(warning, RoutePlanText.withoutDirections(warning));
        assertEquals("某段为直线估算", RoutePlanText.advisory("某段为直线估算"));
        assertEquals("", RoutePlanText.advisory("从这里乘坐地铁到下一站"));
    }

    @Test public void legacySectionEndsAtCostHeadingEvenWithoutBlankLine() {
        String visible = RoutePlanText.withoutDirections("交通安排：\n• A → B\n每人已知交通费用估算：0元\n出发前确认：\n费用待核实");
        assertFalse(visible.contains("A → B"));
        assertTrue(visible.contains("每人已知交通费用估算"));
        assertTrue(visible.contains("费用待核实"));
    }

    @Test public void normalItineraryAndEmptyInputsAreStable() {
        assertEquals("", RoutePlanText.withoutDirections(null));
        assertEquals("", RoutePlanText.withoutDirections(""));
        String text = "候选路线 · 等待确认\n\n1. 博物馆 · 09:00 · 60分钟\n学习目标：观察历史展品";
        assertEquals(text, RoutePlanText.withoutDirections(text));
        assertEquals(text, RoutePlanText.withoutDirections(RoutePlanText.withoutDirections(text)));
    }

    @Test public void factualAddressKeepsStreetButHidesParentheticalDirections() {
        assertEquals("地址：示例街4号", RoutePlanText.withoutDirections("地址：示例街4号(地铁站1号口步行210米)"));
        assertEquals("地址：示例街4号（需要预约）", RoutePlanText.withoutDirections("地址：示例街4号（需要预约）"));
    }
}
