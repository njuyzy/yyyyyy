package com.example.Japp.data;

import java.io.Serializable;

public class RouteStop implements Serializable {
    private int visitOrder;
    private String name;
    private String visitTime;
    private int recommendedDuration;
    private String notes;
    private String location;
    private String address;
    private String cityname;

    public int getVisitOrder() { return visitOrder; }
    public void setVisitOrder(int visitOrder) { this.visitOrder = visitOrder; }

    public String getName() { return name != null ? name : ""; }
    public void setName(String name) { this.name = name; }

    public String getVisitTime() { return visitTime != null ? visitTime : ""; }
    public void setVisitTime(String visitTime) { this.visitTime = visitTime; }

    public int getRecommendedDuration() { return recommendedDuration; }
    public void setRecommendedDuration(int recommendedDuration) { this.recommendedDuration = recommendedDuration; }

    public String getNotes() { return notes != null ? notes : ""; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getLocation() { return location != null ? location : ""; }
    public void setLocation(String location) { this.location = location; }

    public String getAddress() { return address != null ? address : ""; }
    public void setAddress(String address) { this.address = address; }

    public String getCityname() { return cityname != null ? cityname : ""; }
    public void setCityname(String cityname) { this.cityname = cityname; }
}
