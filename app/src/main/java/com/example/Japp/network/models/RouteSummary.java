package com.example.Japp.network.models;

import com.google.gson.annotations.SerializedName;

public class RouteSummary {

    @SerializedName("id")
    private int id;
    @SerializedName("regionAdcode")
    private String regionAdcode;
    @SerializedName("tag")
    private String tag;
    @SerializedName("containsOutdatedAttractions")
    private boolean containsOutdatedAttractions;
    @SerializedName("createdAt")
    private String createdAt;

    public int getId() { return id; }
    public String getRegionAdcode() { return regionAdcode; }
    public String getTag() { return tag; }
    public boolean containsOutdatedAttractions() { return containsOutdatedAttractions; }
    public String getCreatedAt() { return createdAt; }
}
