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

    @SerializedName("representativeCount")
    private final int representativeCount;

    @SerializedName("departureTime")
    private final String departureTime;

    @SerializedName("startPoint")
    private final String startPoint;

    @SerializedName("startLongitude")
    private final Double startLongitude;

    @SerializedName("startLatitude")
    private final Double startLatitude;

    @SerializedName("leaderRequirements")
    private final String leaderRequirements;

    @SerializedName("memberRequirements")
    private final String memberRequirements;

    public CreateProjectRequest(int routeId,
                                String title,
                                String departureDate,
                                int maxMembers,
                                int currentMembers,
                                String status) {
        this(routeId, title, departureDate, maxMembers, currentMembers, status,
                currentMembers, null, null, null, null, null, null);
    }

    public CreateProjectRequest(int routeId,
                                String title,
                                String departureDate,
                                int maxMembers,
                                int currentMembers,
                                String status,
                                int representativeCount,
                                String departureTime,
                                String startPoint,
                                Double startLongitude,
                                Double startLatitude,
                                String leaderRequirements,
                                String memberRequirements) {
        this.routeId = routeId;
        this.title = title;
        this.departureDate = departureDate;
        this.maxMembers = maxMembers;
        this.currentMembers = currentMembers;
        this.status = status;
        this.representativeCount = representativeCount;
        this.departureTime = departureTime;
        this.startPoint = startPoint;
        this.startLongitude = startLongitude;
        this.startLatitude = startLatitude;
        this.leaderRequirements = leaderRequirements;
        this.memberRequirements = memberRequirements;
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

    public int getRepresentativeCount() { return representativeCount; }
    public String getDepartureTime() { return departureTime; }
    public String getStartPoint() { return startPoint; }
    public Double getStartLongitude() { return startLongitude; }
    public Double getStartLatitude() { return startLatitude; }
    public String getLeaderRequirements() { return leaderRequirements; }
    public String getMemberRequirements() { return memberRequirements; }
}
