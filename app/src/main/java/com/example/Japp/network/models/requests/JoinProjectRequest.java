package com.example.Japp.network.models.requests;

import com.google.gson.annotations.SerializedName;

public class JoinProjectRequest {

    @SerializedName("representativeCount")
    private final int representativeCount;

    public JoinProjectRequest(int representativeCount) {
        this.representativeCount = representativeCount;
    }

    public int getRepresentativeCount() {
        return representativeCount;
    }
}
