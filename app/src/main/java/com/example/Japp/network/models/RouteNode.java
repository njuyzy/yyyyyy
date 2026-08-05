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

    @SerializedName("parentPoiId")
    private String parentPoiId;

    @SerializedName("visitTime")
    private String visitTime;

    @SerializedName("cityname")
    private String cityname;

    @SerializedName("citycode")
    private String citycode;

    @SerializedName("adcode")
    private String adcode;

    @SerializedName("adname")
    private String adname;

    @SerializedName("pcode")
    private String pcode;

    @SerializedName("pname")
    private String pname;

    @SerializedName("type")
    private String type;

    @SerializedName("typecode")
    private String typecode;

    @SerializedName("recommendedDuration")
    private int recommendedDuration;

    @SerializedName("notes")
    private String notes;

    @SerializedName("location")
    private String location;

    @SerializedName("distance")
    private String distance;

    @SerializedName("opentimeToday")
    private String opentimeToday;

    @SerializedName("opentimeWeek")
    private String opentimeWeek;

    @SerializedName("tel")
    private String tel;

    @SerializedName("attractionCreatedAt")
    private String attractionCreatedAt;

    @SerializedName("attractionUpdatedAt")
    private String attractionUpdatedAt;

    @SerializedName("createdAt")
    private String createdAt;

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
    public String getParentPoiId() { return parentPoiId; }
    public void setParentPoiId(String parentPoiId) { this.parentPoiId = parentPoiId; }
    public String getVisitTime() { return visitTime; }
    public void setVisitTime(String visitTime) { this.visitTime = visitTime; }
    public String getCityname() { return cityname; }
    public void setCityname(String cityname) { this.cityname = cityname; }
    public String getCitycode() { return citycode; }
    public void setCitycode(String citycode) { this.citycode = citycode; }
    public String getAdcode() { return adcode; }
    public void setAdcode(String adcode) { this.adcode = adcode; }
    public String getAdname() { return adname; }
    public void setAdname(String adname) { this.adname = adname; }
    public String getPcode() { return pcode; }
    public void setPcode(String pcode) { this.pcode = pcode; }
    public String getPname() { return pname; }
    public void setPname(String pname) { this.pname = pname; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getTypecode() { return typecode; }
    public void setTypecode(String typecode) { this.typecode = typecode; }
    public int getRecommendedDuration() { return recommendedDuration; }
    public void setRecommendedDuration(int recommendedDuration) { this.recommendedDuration = recommendedDuration; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getDistance() { return distance; }
    public void setDistance(String distance) { this.distance = distance; }
    public String getOpentimeToday() { return opentimeToday; }
    public void setOpentimeToday(String opentimeToday) { this.opentimeToday = opentimeToday; }
    public String getOpentimeWeek() { return opentimeWeek; }
    public void setOpentimeWeek(String opentimeWeek) { this.opentimeWeek = opentimeWeek; }
    public String getTel() { return tel; }
    public void setTel(String tel) { this.tel = tel; }
    public String getAttractionCreatedAt() { return attractionCreatedAt; }
    public void setAttractionCreatedAt(String attractionCreatedAt) {
        this.attractionCreatedAt = attractionCreatedAt;
    }
    public String getAttractionUpdatedAt() { return attractionUpdatedAt; }
    public void setAttractionUpdatedAt(String attractionUpdatedAt) {
        this.attractionUpdatedAt = attractionUpdatedAt;
    }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
}
