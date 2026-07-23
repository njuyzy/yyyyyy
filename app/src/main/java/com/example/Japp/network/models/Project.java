package com.example.Japp.network.models;

import com.google.gson.annotations.SerializedName;

public class Project {
    @SerializedName("id")
    private int id;

    @SerializedName("routeId")
    private int routeId;

    @SerializedName("regionAdcode")
    private String regionAdcode;

    @SerializedName("tag")
    private String tag;

    @SerializedName("ownerAccountId")
    private int ownerAccountId;

    @SerializedName("leaderAccountId")
    private Integer leaderAccountId;

    @SerializedName("title")
    private String title;

    @SerializedName("departureDate")
    private String departureDate;

    @SerializedName("departureTime")
    private String departureTime;

    @SerializedName("arrivalTime")
    private String arrivalTime;

    @SerializedName("startPointType")
    private String startPointType;

    @SerializedName("startPoint")
    private String startPoint;

    @SerializedName("leaderRequirements")
    private String leaderRequirements;

    @SerializedName("participantRequirements")
    private String participantRequirements;

    @SerializedName("representedCount")
    private int representedCount;

    @SerializedName("maxMembers")
    private int maxMembers;

    @SerializedName("currentMembers")
    private int currentMembers;

    @SerializedName("status")
    private String status;

    @SerializedName("createdAt")
    private String createdAt;

    @SerializedName("updatedAt")
    private String updatedAt;

    @SerializedName("publisherName")
    private String publisherName;

    @SerializedName("leaderName")
    private String leaderName;

    @SerializedName("availabilityStatus")
    private String availabilityStatus;

    @SerializedName("viewerRole")
    private String viewerRole;

    @SerializedName("canAccept")
    private boolean canAccept;

    @SerializedName("canJoin")
    private boolean canJoin;

    @SerializedName("groupId")
    private Long groupId;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getRouteId() { return routeId; }
    public void setRouteId(int routeId) { this.routeId = routeId; }

    public String getRegionAdcode() { return regionAdcode; }
    public void setRegionAdcode(String regionAdcode) { this.regionAdcode = regionAdcode; }

    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }

    public int getOwnerAccountId() { return ownerAccountId; }
    public void setOwnerAccountId(int ownerAccountId) { this.ownerAccountId = ownerAccountId; }

    public Integer getLeaderAccountId() { return leaderAccountId; }
    public void setLeaderAccountId(Integer leaderAccountId) { this.leaderAccountId = leaderAccountId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDepartureDate() { return departureDate; }
    public void setDepartureDate(String departureDate) { this.departureDate = departureDate; }

    public String getDepartureTime() { return departureTime; }
    public void setDepartureTime(String departureTime) { this.departureTime = departureTime; }

    public String getArrivalTime() { return arrivalTime; }
    public void setArrivalTime(String arrivalTime) { this.arrivalTime = arrivalTime; }

    public String getStartPointType() { return startPointType; }
    public String getStartPoint() { return startPoint; }
    public String getLeaderRequirements() { return leaderRequirements; }
    public String getParticipantRequirements() { return participantRequirements; }
    public int getRepresentedCount() { return representedCount; }

    public int getMaxMembers() { return maxMembers; }
    public void setMaxMembers(int maxMembers) { this.maxMembers = maxMembers; }

    public int getCurrentMembers() { return currentMembers; }
    public void setCurrentMembers(int currentMembers) { this.currentMembers = currentMembers; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public String getPublisherName() { return publisherName; }
    public String getLeaderName() { return leaderName; }
    public String getAvailabilityStatus() { return availabilityStatus; }
    public String getViewerRole() { return viewerRole; }
    public boolean isCanAccept() { return canAccept; }
    public boolean isCanJoin() { return canJoin; }
    public Long getGroupId() { return groupId; }
}
