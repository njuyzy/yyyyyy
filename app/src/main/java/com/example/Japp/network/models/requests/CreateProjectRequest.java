package com.example.Japp.network.models.requests;

import com.google.gson.annotations.SerializedName;

public class CreateProjectRequest {

    @SerializedName("routeId")
    private final int routeId;

    @SerializedName("title")
    private final String title;

    @SerializedName("departureDate")
    private final String departureDate;

    @SerializedName("maxMembers")
    private final int maxMembers;

    @SerializedName("currentMembers")
    private final int currentMembers;

    @SerializedName("status")
    private final String status;

    public CreateProjectRequest(int routeId,
                                String title,
                                String departureDate,
                                int maxMembers,
                                int currentMembers,
                                String status) {
        this.routeId = routeId;
        this.title = title;
        this.departureDate = departureDate;
        this.maxMembers = maxMembers;
        this.currentMembers = currentMembers;
        this.status = status;
    }

    public int getRouteId() {
        return routeId;
    }

    public String getTitle() {
        return title;
    }

    public String getDepartureDate() {
        return departureDate;
    }

    public int getMaxMembers() {
        return maxMembers;
    }

    public int getCurrentMembers() {
        return currentMembers;
    }

    public String getStatus() {
        return status;
    }
}
