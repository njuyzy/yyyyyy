package com.example.Japp.network.agent;

import com.example.Japp.network.models.RouteNode;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class RouteAgentRequest {
    public final String requestId = UUID.randomUUID().toString();
    public final String memoryId;
    public final int accountId;
    public final String message;
    public final List<Turn> history = new ArrayList<>();
    public final List<RouteNode> currentRoute;
    public final JsonObject constraints;
    public Origin origin;

    public RouteAgentRequest(int accountId, String memoryId, String message,
                             List<RouteNode> nodes, JsonObject constraints) {
        if (memoryId == null || !memoryId.matches("[A-Za-z0-9_-]{1,128}")) {
            throw new IllegalArgumentException("请新建路线对话后重试");
        }
        this.accountId = accountId;
        this.memoryId = memoryId;
        this.message = message;
        this.currentRoute = nodes;
        this.constraints = constraints == null ? new JsonObject() : constraints;
    }

    public static String newMemoryId() {
        return "route-" + UUID.randomUUID();
    }

    public static boolean isValidMemoryId(String value) {
        return value != null && value.matches("[A-Za-z0-9_-]{1,128}");
    }

    public static final class Turn {
        public final String role;
        public final String content;
        public Turn(String role, String content) {
            this.role = role;
            this.content = content.length() > 8000 ? content.substring(0, 8000) : content;
        }
    }

    public static final class Origin {
        public final double longitude;
        public final double latitude;
        public Origin(double longitude, double latitude) {
            this.longitude = longitude;
            this.latitude = latitude;
        }
    }
}
