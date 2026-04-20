package com.example.Japp.data;

import java.io.Serializable;

public class RouteStop implements Serializable {
    private int visitOrder;
    private String name;
    private String visitTime;
    private int recommendedDuration;
    private String notes;

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
}
