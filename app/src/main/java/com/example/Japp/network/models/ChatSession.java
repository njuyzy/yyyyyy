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
    private int leaderAccountId;

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

    public int getLeaderAccountId() {
        return leaderAccountId;
    }

    public void setLeaderAccountId(int leaderAccountId) {
        this.leaderAccountId = leaderAccountId;
    }
}
