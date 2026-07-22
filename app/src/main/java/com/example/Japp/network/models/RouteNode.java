package com.example.Japp.network.models;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

public class RouteNode implements Serializable {
    @SerializedName("routeId")
    private int routeId;

    @SerializedName("visitOrder")
    private int visitOrder;

    @SerializedName("poiId")
    private String poiId;

    @SerializedName("name")
    private String name;

    @SerializedName("address")
    private String address;

    @SerializedName("visitTime")
    private String visitTime;

    @SerializedName("cityname")
    private String cityname;

    @SerializedName("recommendedDuration")
    private int recommendedDuration;

    @SerializedName("notes")
    private String notes;

    @SerializedName("location")
    private String location;

    // 地图信息卡本地使用，不参与接口序列化。
    private transient String photoUrl;

    public int getRouteId() { return routeId; }
    public void setRouteId(int routeId) { this.routeId = routeId; }
    public int getVisitOrder() { return visitOrder; }
    public void setVisitOrder(int visitOrder) { this.visitOrder = visitOrder; }
    public String getPoiId() { return poiId; }
    public void setPoiId(String poiId) { this.poiId = poiId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getVisitTime() { return visitTime; }
    public void setVisitTime(String visitTime) { this.visitTime = visitTime; }
    public String getCityname() { return cityname; }
    public void setCityname(String cityname) { this.cityname = cityname; }
    public int getRecommendedDuration() { return recommendedDuration; }
    public void setRecommendedDuration(int recommendedDuration) { this.recommendedDuration = recommendedDuration; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
}
