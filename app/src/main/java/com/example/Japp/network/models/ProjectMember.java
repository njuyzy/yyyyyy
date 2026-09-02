package com.example.Japp.network.models;

import com.google.gson.annotations.SerializedName;

public class ProjectMember {

    @SerializedName("id")
    private int id;
    @SerializedName("projectId")
    private int projectId;
    @SerializedName("accountId")
    private int accountId;
    @SerializedName("joinStatus")
    private String joinStatus;
    @SerializedName("representedCount")
    private int representedCount;
    @SerializedName("joinedAt")
    private String joinedAt;

    public int getId() { return id; }
    public int getProjectId() { return projectId; }
    public int getAccountId() { return accountId; }
    public String getJoinStatus() { return joinStatus; }
    public int getRepresentedCount() { return representedCount; }
    public String getJoinedAt() { return joinedAt; }
}
