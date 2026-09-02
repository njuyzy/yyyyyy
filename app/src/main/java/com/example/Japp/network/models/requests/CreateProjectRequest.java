package com.example.Japp.network.models.requests;

import com.google.gson.annotations.SerializedName;

public class CreateProjectRequest {

    @SerializedName("routeId")
    private final int routeId;

    @SerializedName("leaderAccountId")
    private final Integer leaderAccountId;

    @SerializedName("title")
    private final String title;

    @SerializedName("departureDate")
    private final String departureDate;

    @SerializedName("maxMembers")
    private final Integer maxMembers;

    @SerializedName("status")
    private final String status;

    @SerializedName("representedCount")
    private final int representedCount;

    @SerializedName("departureTime")
    private final String departureTime;

    @SerializedName("startPoint")
    private final String startPoint;

    @SerializedName("startPointType")
    private final String startPointType;

    @SerializedName("leaderRequirements")
    private final String leaderRequirements;

    @SerializedName("participantRequirements")
    private final String participantRequirements;

    public CreateProjectRequest(int routeId,
                                Integer leaderAccountId,
                                String title,
                                String departureDate,
                                String departureTime,
                                String startPointType,
                                String startPoint,
                                String leaderRequirements,
                                String participantRequirements,
                                int representedCount,
                                Integer maxMembers,
                                String status) {
        this.routeId = routeId;
        this.leaderAccountId = leaderAccountId;
        this.title = title;
        this.departureDate = departureDate;
        this.departureTime = departureTime;
        this.startPointType = startPointType;
        this.startPoint = startPoint;
        this.leaderRequirements = leaderRequirements;
        this.participantRequirements = participantRequirements;
        this.representedCount = representedCount;
        this.maxMembers = maxMembers != null && maxMembers > 0 ? maxMembers : null;
        this.status = status;
    }

    public int getRouteId() {
        return routeId;
    }

    public Integer getLeaderAccountId() {
        return leaderAccountId;
    }

    public String getTitle() {
        return title;
    }

    public String getDepartureDate() {
        return departureDate;
    }

    public Integer getMaxMembers() {
        return maxMembers;
    }

    public String getStatus() {
        return status;
    }

    public int getRepresentedCount() { return representedCount; }
    public String getDepartureTime() { return departureTime; }
    public String getStartPoint() { return startPoint; }
    public String getStartPointType() { return startPointType; }
    public String getLeaderRequirements() { return leaderRequirements; }
    public String getParticipantRequirements() { return participantRequirements; }
}
