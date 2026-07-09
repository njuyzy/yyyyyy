package com.example.Japp.network.models.requests;

import com.google.gson.annotations.SerializedName;

public class PlanRouteRequest {

    @SerializedName("accountId")
    private final int accountId;

    @SerializedName("memoryId")
    private final String memoryId;

    @SerializedName(value = "text", alternate = {"prompt", "requirement", "content"})
    private final String text;

    public PlanRouteRequest(int accountId, String memoryId, String text) {
        this.accountId = accountId;
        this.memoryId = memoryId;
        this.text = text;
    }

    public int getAccountId() {
        return accountId;
    }

    public String getMemoryId() {
        return memoryId;
    }

    public String getText() {
        return text;
    }
}
