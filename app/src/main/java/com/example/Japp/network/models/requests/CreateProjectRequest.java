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
                                String title,
                                String departureDate,
                                int maxMembers,
                                int currentMembers,
                                String status) {
        this(routeId, title, departureDate, maxMembers, currentMembers, status,
                currentMembers, null, null, "MANUAL", null, null);
    }

    public CreateProjectRequest(int routeId,
                                String title,
                                String departureDate,
                                int maxMembers,
                                int currentMembers,
                                String status,
                                int representedCount,
                                String departureTime,
                                String startPoint,
                                String startPointType,
                                String leaderRequirements,
                                String participantRequirements) {
        this.routeId = routeId;
        this.title = title;
        this.departureDate = departureDate;
        this.maxMembers = maxMembers;
        this.currentMembers = currentMembers;
        this.status = status;
        this.representedCount = representedCount;
        this.departureTime = departureTime;
        this.startPoint = startPoint;
        this.startPointType = startPointType;
        this.leaderRequirements = leaderRequirements;
        this.participantRequirements = participantRequirements;
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

    public int getRepresentedCount() { return representedCount; }
    public String getDepartureTime() { return departureTime; }
    public String getStartPoint() { return startPoint; }
    public String getStartPointType() { return startPointType; }
    public String getLeaderRequirements() { return leaderRequirements; }
    public String getParticipantRequirements() { return participantRequirements; }
}
