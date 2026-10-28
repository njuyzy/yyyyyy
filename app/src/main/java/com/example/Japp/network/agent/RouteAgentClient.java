package com.example.Japp.network.agent;

import com.example.Japp.network.ApiClient;

public final class RouteAgentClient {
    private RouteAgentClient() {}

    public static RouteAgentService create() {
        // The integrated backend shares the existing URL and access-token interceptor.
        return ApiClient.getClient().create(RouteAgentService.class);
    }
}
