package com.example.Japp.network.models;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class LeaderProfile {
    @SerializedName("accountId")
    private int accountId;

    @SerializedName("username")
    private String username;

    @SerializedName("avatarUrl")
    private String avatarUrl;

    @SerializedName("regionCode")
    private String regionCode;

    @SerializedName("intro")
    private String intro;

    @SerializedName("averageRating")
    private Double averageRating;

    @SerializedName("ratingCount")
    private Integer ratingCount;

    @SerializedName("acceptedProjectCount")
    private int acceptedProjectCount;

    @SerializedName("completedProjectCount")
    private int completedProjectCount;

    @SerializedName("tagNames")
    private List<String> tagNames;

    @SerializedName("recentReviews")
    private List<LeaderReview> recentReviews;

    public int getAccountId() { return accountId; }
    public String getUsername() { return username; }
    public String getAvatarUrl() { return avatarUrl; }
    public String getRegionCode() { return regionCode; }
    public String getIntro() { return intro; }
    public Double getAverageRating() { return averageRating; }
    public Integer getRatingCount() { return ratingCount; }
    public int getAcceptedProjectCount() { return acceptedProjectCount; }
    public int getCompletedProjectCount() { return completedProjectCount; }
    public List<String> getTagNames() { return tagNames; }
    public List<LeaderReview> getRecentReviews() { return recentReviews; }
}
