package com.example.Japp.network.models;

import com.google.gson.annotations.SerializedName;

public class ChatSession {

    @SerializedName("id")
    private int id;

    @SerializedName("projectId")
    private int projectId;

    @SerializedName("userAccountId")
    private int userAccountId;

    @SerializedName("leaderAccountId")
    private Integer leaderAccountId;

    @SerializedName("projectTitle")
    private String projectTitle;

    @SerializedName("status")
    private String status;

    @SerializedName("currentUserRole")
    private String currentUserRole;

    @SerializedName("memberCount")
    private int memberCount;

    @SerializedName("latestMessage")
    private String latestMessage;

    @SerializedName("latestMessageAt")
    private String latestMessageAt;

    @SerializedName("latestMessageSenderAccountId")
    private Integer latestMessageSenderAccountId;

    @SerializedName("disabledAt")
    private String disabledAt;

    @SerializedName("createdAt")
    private String createdAt;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getProjectId() {
        return projectId;
    }

    public void setProjectId(int projectId) {
        this.projectId = projectId;
    }

    public int getUserAccountId() {
        return userAccountId;
    }

    public void setUserAccountId(int userAccountId) {
        this.userAccountId = userAccountId;
    }

    public Integer getLeaderAccountId() {
        return leaderAccountId;
    }

    public void setLeaderAccountId(Integer leaderAccountId) {
        this.leaderAccountId = leaderAccountId;
    }

    public String getProjectTitle() { return projectTitle; }
    public String getStatus() { return status; }
    public String getCurrentUserRole() { return currentUserRole; }
    public int getMemberCount() { return memberCount; }
    public String getLatestMessage() { return latestMessage; }
    public String getLatestMessageAt() { return latestMessageAt; }
    public Integer getLatestMessageSenderAccountId() { return latestMessageSenderAccountId; }
    public String getDisabledAt() { return disabledAt; }
    public String getCreatedAt() { return createdAt; }
}
