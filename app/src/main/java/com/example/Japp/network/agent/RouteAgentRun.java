package com.example.Japp.network.agent;

import java.util.List;

public final class RouteAgentRun {
    public String runId;
    public String status;
    public String stage;
    public String error;
    public List<Event> events;
    public RouteAgentResult result;

    public static final class Event {
        public String tool;
        public String label;
        public String status;
        public long elapsedMs;
    }
}
