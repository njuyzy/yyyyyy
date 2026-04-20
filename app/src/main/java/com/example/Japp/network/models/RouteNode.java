package com.example.Japp.network.models;

import com.google.gson.annotations.SerializedName;

public class RouteNode {
    @SerializedName("routeId")
    private int routeId;

    @SerializedName("visitOrder")
    private int visitOrder;

    @SerializedName("poiId")
    private String poiId;

    @SerializedName("name")
    private String name;

    @SerializedName("visitTime")
    private String visitTime;

    @SerializedName("cityname")
    private String cityname;

    @SerializedName("recommendedDuration")
    private int recommendedDuration;

    @SerializedName("notes")
    private String notes;

    public int getRouteId() { return routeId; }
    public int getVisitOrder() { return visitOrder; }
    public String getPoiId() { return poiId; }
    public String getName() { return name; }
    public String getVisitTime() { return visitTime; }
    public String getCityname() { return cityname; }
    public int getRecommendedDuration() { return recommendedDuration; }
    public String getNotes() { return notes; }
}
