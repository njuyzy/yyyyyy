package com.example.Japp.network.models;

import com.google.gson.annotations.SerializedName;

/** 领队收到的用户评价（USER_TO_LEADER），对应 GET /reviews/leader/{accountId} 或 GET /leader/reviews */
public class LeaderReview {

    @SerializedName("id")
    private int id;

    @SerializedName("projectId")
    private int projectId;

    @SerializedName("routeId")
    private int routeId;

    @SerializedName("reviewerAccountId")
    private int reviewerAccountId;

    @SerializedName("reviewerName")
    private String reviewerName;

    @SerializedName("reviewerAvatarUrl")
    private String reviewerAvatarUrl;

    @SerializedName("overallScore")
    private int overallScore;

    @SerializedName("content")
    private String content;

    @SerializedName("createdAt")
    private String createdAt;

    public int getId() { return id; }
    public int getProjectId() { return projectId; }
    public int getRouteId() { return routeId; }
    public int getReviewerAccountId() { return reviewerAccountId; }
    public String getReviewerName() { return reviewerName; }
    public String getReviewerAvatarUrl() { return reviewerAvatarUrl; }
    public int getOverallScore() { return overallScore; }
    public String getContent() { return content; }
    public String getCreatedAt() { return createdAt; }
}
