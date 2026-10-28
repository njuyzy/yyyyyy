package com.example.Japp.user.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ProjectUiHelperTest {

    @Test
    public void backendStatusesCollapseIntoThreeVisibleStages() {
        assertEquals("待接单", ProjectUiHelper.statusLabel(ProjectUiHelper.STATUS_OPEN));
        assertEquals("已接单", ProjectUiHelper.statusLabel(ProjectUiHelper.STATUS_MATCHING));
        assertEquals("已接单", ProjectUiHelper.statusLabel(ProjectUiHelper.STATUS_CONFIRMED));
        assertEquals("已接单", ProjectUiHelper.statusLabel(ProjectUiHelper.STATUS_IN_PROGRESS));
        assertEquals("已完成", ProjectUiHelper.statusLabel(ProjectUiHelper.STATUS_DONE));
    }

    @Test
    public void cancelledOrdersHaveNoVisibleStage() {
        assertEquals("", ProjectUiHelper.statusLabel(ProjectUiHelper.STATUS_CANCELLED));
    }

    @Test
    public void onlyOpenOrdersAreAvailableToLeaders() {
        assertTrue(ProjectUiHelper.isLeaderAvailableStatus(ProjectUiHelper.STATUS_OPEN));
        assertFalse(ProjectUiHelper.isLeaderAvailableStatus(ProjectUiHelper.STATUS_MATCHING));
        assertFalse(ProjectUiHelper.isLeaderAvailableStatus(ProjectUiHelper.STATUS_CONFIRMED));
        assertFalse(ProjectUiHelper.isLeaderAvailableStatus(ProjectUiHelper.STATUS_IN_PROGRESS));
        assertFalse(ProjectUiHelper.isLeaderAvailableStatus(ProjectUiHelper.STATUS_DONE));
        assertFalse(ProjectUiHelper.isLeaderAvailableStatus(ProjectUiHelper.STATUS_CANCELLED));
    }

    @Test
    public void acceptedStageContainsOnlyLegacyActiveStatuses() {
        assertFalse(ProjectUiHelper.isLeaderAcceptedStatus(ProjectUiHelper.STATUS_OPEN));
        assertTrue(ProjectUiHelper.isLeaderAcceptedStatus(ProjectUiHelper.STATUS_MATCHING));
        assertTrue(ProjectUiHelper.isLeaderAcceptedStatus(ProjectUiHelper.STATUS_CONFIRMED));
        assertTrue(ProjectUiHelper.isLeaderAcceptedStatus(ProjectUiHelper.STATUS_IN_PROGRESS));
        assertFalse(ProjectUiHelper.isLeaderAcceptedStatus(ProjectUiHelper.STATUS_DONE));
        assertFalse(ProjectUiHelper.isLeaderAcceptedStatus(ProjectUiHelper.STATUS_CANCELLED));
    }

    @Test
    public void currentLeaderCanOnlyAbandonConfirmedOrder() {
        assertFalse(ProjectUiHelper.canLeaderAbandon(ProjectUiHelper.STATUS_MATCHING));
        assertTrue(ProjectUiHelper.canLeaderAbandon(ProjectUiHelper.STATUS_CONFIRMED));
        assertFalse(ProjectUiHelper.canLeaderAbandon(ProjectUiHelper.STATUS_IN_PROGRESS));
        assertFalse(ProjectUiHelper.canLeaderAbandon(ProjectUiHelper.STATUS_DONE));
        assertFalse(ProjectUiHelper.canLeaderAbandon(ProjectUiHelper.STATUS_CANCELLED));
    }

    @Test
    public void leaderRatingIsHiddenUntilAtLeastOneReviewExists() {
        assertEquals("--", ProjectUiHelper.leaderRatingLabel(null, null));
        assertEquals("--", ProjectUiHelper.leaderRatingLabel(5.0, 0));
        assertEquals("--", ProjectUiHelper.leaderRatingLabel(0.0, 3));
    }

    @Test
    public void leaderRatingUsesOneDecimalForReviewedLeader() {
        assertEquals("4.8", ProjectUiHelper.leaderRatingLabel(4.75, 12));
    }
}
