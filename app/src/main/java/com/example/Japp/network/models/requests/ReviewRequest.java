package com.example.Japp.network.models.requests;

import com.google.gson.annotations.SerializedName;

/** 创建或修改评论时使用的请求体。 */
public class ReviewRequest {

    @SerializedName("projectId")
    private final int projectId;
    @SerializedName("routeId")
    private final int routeId;
    @SerializedName("fromAccountId")
    private final int fromAccountId;
    @SerializedName("toAccountId")
    private final int toAccountId;
    @SerializedName("reviewType")
    private final String reviewType;
    @SerializedName("overallScore")
    private final int overallScore;
    @SerializedName("content")
    private final String content;

    public ReviewRequest(int projectId, int routeId,
                         int fromAccountId, int toAccountId,
                         String reviewType, int overallScore, String content) {
        this.projectId = projectId;
        this.routeId = routeId;
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.reviewType = reviewType;
        this.overallScore = overallScore;
        this.content = content;
    }

    public int getProjectId() { return projectId; }
    public int getRouteId() { return routeId; }
    public int getFromAccountId() { return fromAccountId; }
    public int getToAccountId() { return toAccountId; }
    public String getReviewType() { return reviewType; }
    public int getOverallScore() { return overallScore; }
    public String getContent() { return content; }
}
