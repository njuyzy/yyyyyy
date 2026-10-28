package com.example.Japp.network.agent;

import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class RouteAgentRequestTest {
    @Test public void freshMemoryUsesNoAccountNamespaceAndFitsBackendLimit() {
        String id = RouteAgentRequest.newMemoryId();
        assertTrue(RouteAgentRequest.isValidMemoryId(id)); assertTrue(id.length() <= 128);
        assertFalse(id.startsWith("user-")); assertNotEquals(id, RouteAgentRequest.newMemoryId());
    }
    @Test public void exact128BoundaryAcceptedAnd129Rejected() {
        assertTrue(RouteAgentRequest.isValidMemoryId(String.join("", Collections.nCopies(128, "a"))));
        assertFalse(RouteAgentRequest.isValidMemoryId(String.join("", Collections.nCopies(129, "a"))));
    }
    @Test(expected = IllegalArgumentException.class) public void requestRejectsOverlongMemoryBeforeCallingBackend() {
        new RouteAgentRequest(1, String.join("", Collections.nCopies(129, "a")), "需求", Collections.emptyList(), null);
    }
}
