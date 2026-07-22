package com.example.Japp.network.models.requests;

import com.google.gson.annotations.SerializedName;

public class CreateSessionRequest {

    @SerializedName("projectId")
    private final int projectId;

    @SerializedName("userAccountId")
    private final int userAccountId;

    @SerializedName("leaderAccountId")
    private final int leaderAccountId;

    public CreateSessionRequest(int projectId, int userAccountId, int leaderAccountId) {
        this.projectId = projectId;
        this.userAccountId = userAccountId;
        this.leaderAccountId = leaderAccountId;
    }

    public int getProjectId() {
        return projectId;
    }

    public int getUserAccountId() {
        return userAccountId;
    }

    public int getLeaderAccountId() {
        return leaderAccountId;
    }
}
