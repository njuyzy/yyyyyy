package com.example.Japp.network.models.requests;

import com.google.gson.annotations.SerializedName;

public class CreateSessionRequest {

    @SerializedName("projectId")
    private final int projectId;

    @SerializedName("memberAccountId")
    private final int memberAccountId;

    @SerializedName("leaderAccountId")
    private final int leaderAccountId;

    public CreateSessionRequest(int projectId, int memberAccountId, int leaderAccountId) {
        this.projectId = projectId;
        this.memberAccountId = memberAccountId;
        this.leaderAccountId = leaderAccountId;
    }

    public int getProjectId() {
        return projectId;
    }

    public int getMemberAccountId() {
        return memberAccountId;
    }

    public int getLeaderAccountId() {
        return leaderAccountId;
    }
}
