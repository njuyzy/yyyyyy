package com.example.Japp.user.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ProjectUiHelperTest {

    @Test
    public void currentLeaderCanAbandonActiveOrder() {
        assertTrue(ProjectUiHelper.canLeaderAbandon(ProjectUiHelper.STATUS_MATCHING));
        assertTrue(ProjectUiHelper.canLeaderAbandon(ProjectUiHelper.STATUS_CONFIRMED));
        assertTrue(ProjectUiHelper.canLeaderAbandon(ProjectUiHelper.STATUS_IN_PROGRESS));
        assertFalse(ProjectUiHelper.canLeaderAbandon(ProjectUiHelper.STATUS_DONE));
        assertFalse(ProjectUiHelper.canLeaderAbandon(ProjectUiHelper.STATUS_CANCELLED));
    }
}
