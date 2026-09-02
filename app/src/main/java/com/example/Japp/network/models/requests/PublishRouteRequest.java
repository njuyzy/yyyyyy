package com.example.Japp.network.models.requests;

import com.google.gson.annotations.SerializedName;

/** /routes/{routeId}/publish 的请求体；路线与发布状态由后端确定。 */
public class PublishRouteRequest {

    @SerializedName("title")
    private final String title;
    @SerializedName("representedCount")
    private final int representedCount;
    @SerializedName("departureDate")
    private final String departureDate;
    @SerializedName("departureTime")
    private final String departureTime;
    @SerializedName("startPointType")
    private final String startPointType;
    @SerializedName("startPoint")
    private final String startPoint;
    @SerializedName("leaderRequirements")
    private final String leaderRequirements;
    @SerializedName("participantRequirements")
    private final String participantRequirements;
    @SerializedName("maxMembers")
    private final Integer maxMembers;

    public PublishRouteRequest(String title, int representedCount,
                               String departureDate, String departureTime,
                               String startPointType, String startPoint,
                               String leaderRequirements, String participantRequirements,
                               int maxMembers) {
        this.title = title;
        this.representedCount = representedCount;
        this.departureDate = departureDate;
        this.departureTime = departureTime;
        this.startPointType = startPointType;
        this.startPoint = startPoint;
        this.leaderRequirements = leaderRequirements;
        this.participantRequirements = participantRequirements;
        this.maxMembers = maxMembers > 0 ? maxMembers : null;
    }

    public String getTitle() { return title; }
    public int getRepresentedCount() { return representedCount; }
    public String getDepartureDate() { return departureDate; }
    public String getDepartureTime() { return departureTime; }
    public String getStartPointType() { return startPointType; }
    public String getStartPoint() { return startPoint; }
    public String getLeaderRequirements() { return leaderRequirements; }
    public String getParticipantRequirements() { return participantRequirements; }
    public Integer getMaxMembers() { return maxMembers; }
}
