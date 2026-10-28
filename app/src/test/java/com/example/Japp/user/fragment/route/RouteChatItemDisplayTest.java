package com.example.Japp.user.fragment.route;

import org.junit.Test;
import static org.junit.Assert.*;

public class RouteChatItemDisplayTest {
    @Test public void userMessageIsNeverFiltered() {
        String text = "沿道路步行到博物馆，再乘坐地铁2号线";
        RouteChatItem item = RouteChatItem.user(text);
        assertEquals(text, item.getText());
        item.setText(text);
        assertEquals(text, item.getText());
    }

    @Test public void restoredAssistantMessageDoesNotReshowOldDirections() {
        RouteChatItem item = RouteChatItem.restore(RouteChatItem.TYPE_ASSISTANT_PLAN,
                "候选路线\n\n交通安排：\n• A → B\n\n出行提醒：\n门票待核实", 123L, 0, null, null, false);
        assertFalse(item.getText().contains("交通安排"));
        assertFalse(item.getText().contains("A → B"));
        assertTrue(item.getText().contains("门票待核实"));
        assertEquals(123L, item.getTimestamp());
        assertEquals(RouteChatItem.TYPE_ASSISTANT_PLAN, item.getType());
    }

    @Test public void updatedAssistantTextAlsoHidesDirectionsWithoutLosingStudyGoal() {
        RouteChatItem item = RouteChatItem.assistantMessage("方案已应用");
        item.setText("从这里步行前往下一个景点，观察展品并完成记录卡。");
        assertFalse(item.getText().contains("步行前往"));
        assertTrue(item.getText().contains("完成记录卡"));
    }
}
