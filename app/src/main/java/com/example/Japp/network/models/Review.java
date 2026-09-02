package com.example.Japp.network.models;

import com.google.gson.annotations.SerializedName;

/** 评论接口的通用响应模型。 */
public class Review {

    @SerializedName("id")
    private int id;
    @SerializedName("projectId")
    private int projectId;
    @SerializedName("routeId")
    private int routeId;
    @SerializedName("fromAccountId")
    private int fromAccountId;
    @SerializedName("toAccountId")
    private int toAccountId;
    @SerializedName("reviewType")
    private String reviewType;
    @SerializedName("overallScore")
    private int overallScore;
    @SerializedName("content")
    private String content;
    @SerializedName("createdAt")
    private String createdAt;

    public int getId() { return id; }
    public int getProjectId() { return projectId; }
    public int getRouteId() { return routeId; }
    public int getFromAccountId() { return fromAccountId; }
    public int getToAccountId() { return toAccountId; }
    public String getReviewType() { return reviewType; }
    public int getOverallScore() { return overallScore; }
    public String getContent() { return content; }
    public String getCreatedAt() { return createdAt; }
}
