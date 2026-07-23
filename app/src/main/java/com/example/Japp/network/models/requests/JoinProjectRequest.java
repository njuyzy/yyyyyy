package com.example.Japp.network.models.requests;

import com.google.gson.annotations.SerializedName;

public class JoinProjectRequest {

    @SerializedName("representedCount")
    private final int representedCount;

    public JoinProjectRequest(int representedCount) {
        this.representedCount = representedCount;
    }

    public int getRepresentedCount() {
        return representedCount;
    }
}
