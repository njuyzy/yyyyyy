package com.example.Japp.network.models.requests;

public class AssignLeaderRequest {
    private int leaderAccountId;

    public AssignLeaderRequest(int leaderAccountId) {
        this.leaderAccountId = leaderAccountId;
    }

    public int getLeaderAccountId() {
        return leaderAccountId;
    }

    public void setLeaderAccountId(int leaderAccountId) {
        this.leaderAccountId = leaderAccountId;
    }
}
