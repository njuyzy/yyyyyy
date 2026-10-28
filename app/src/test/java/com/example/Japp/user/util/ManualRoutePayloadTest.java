package com.example.Japp.user.util;

import com.example.Japp.network.models.RouteNode;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class ManualRoutePayloadTest {
    @Test public void explicitDateAndAlreadyIsoConvertToLocalIsoSeconds() {
        assertEquals("2026-10-11T09:00:00", ManualRoutePayload.toIso("2026-10-11 09:00", "2026-10-20"));
        assertEquals("2026-10-11T09:00:15", ManualRoutePayload.toIso("2026-10-11T09:00:15", "2026-10-20"));
    }
    @Test public void relativeDaysResolveFromChosenDepartureAcrossMonth() {
        assertEquals("2026-11-01T09:00:00", ManualRoutePayload.toIso("第2天 09:00", "2026-10-31"));
        assertEquals("2026-10-31T10:30:00", ManualRoutePayload.toIso("第1天 10:30", "2026-10-31"));
    }
    @Test public void optionalAndClockOnlyTimesHandledWithoutInventingAnotherDate() {
        assertNull(ManualRoutePayload.toIso(null, "2026-10-11"));
        assertNull(ManualRoutePayload.toIso(" ", "2026-10-11"));
        assertEquals("2026-10-11T09:00:00", ManualRoutePayload.toIso("09:00", "2026-10-11"));
    }
    @Test public void preparingPayloadDoesNotMutateTheSavedDraft() {
        RouteNode node = new RouteNode(); node.setPoiId("A"); node.setName("博物馆"); node.setVisitTime("第2天 09:00");
        List<RouteNode> payload = ManualRoutePayload.prepare(Collections.singletonList(node), "2026-10-11");
        assertEquals("第2天 09:00", node.getVisitTime());
        assertEquals("2026-10-12T09:00:00", payload.get(0).getVisitTime());
        assertEquals("A", payload.get(0).getPoiId()); assertNotSame(node, payload.get(0));
    }
    @Test(expected = IllegalArgumentException.class) public void impossibleDateRejectedLocally() {
        ManualRoutePayload.toIso("2026-02-30 09:00", "2026-10-11");
    }
    @Test(expected = IllegalArgumentException.class) public void invalidRelativeDayNotSilentlyMapped() {
        ManualRoutePayload.toIso("第0天 09:00", "2026-10-11");
    }
}
