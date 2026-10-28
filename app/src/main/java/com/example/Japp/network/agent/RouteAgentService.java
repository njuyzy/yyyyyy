package com.example.Japp.network.agent;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface RouteAgentService {
    @POST("routes/agent/runs")
    Call<RouteAgentRun> start(@Body RouteAgentRequest request);

    @GET("routes/agent/runs/{runId}")
    Call<RouteAgentRun> status(@Path("runId") String runId);

    @DELETE("routes/agent/runs/{runId}")
    Call<RouteAgentRun> cancel(@Path("runId") String runId);
}
