package com.example.Japp.data;

import java.io.Serializable;
import java.util.List;
import java.time.*;
import java.util.concurrent.ThreadLocalRandom;

public class order implements Serializable {
    private String id;
    private long startTime;

    private int projectId;
    private int routeId;
    private String title;
    private String departureDate;
    private int currentMembers;
    private String createdAt;

    private User customer;
    private String city;
    private String tag;
    private String routeImageUrl,estimatedDuration,estimatedStartTime,estimatedEndTime;

    private Route route;
    private List<RouteStop> routeStops;
    List<String> tags;

    public String getPeopleCnt() {
        return peopleCnt+"人";
    }

    public int getMaxMembers() {
        return peopleCnt;
    }

    private int peopleCnt;
    public static enum OrderStatus{AVAILABLE, TAKEN_BY_OTHER, EXPIRED, ACCEPTED_BY_ME}

    public OrderStatus getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(OrderStatus orderStatus) {
        this.orderStatus = orderStatus;
    }

    private OrderStatus orderStatus;


    public order(){
        this.id=ID.Generate_id();
        this.startTime=System.currentTimeMillis();
    }

    public User getCustomer(){
        if(customer==null)
            return new User();
        return customer;
    }
    public Route getRoute(){
        if(route==null)
            return new Route();
        return route;
    }
    public List<RouteStop> getRouteStops() {
        if (routeStops == null) return new java.util.ArrayList<>();
        return routeStops;
    }
    public String getEstimatedDuration(){return estimatedDuration;}


    public String getCity() { return city != null ? city : ""; }
    public void setCity(String city) { this.city = city; }

    public String getTag() { return tag != null ? tag : ""; }
    public void setTag(String tag) { this.tag = tag; }

    public void setCustomer(User customer) { this.customer = customer; }
    public void setEstimatedDuration(String estimatedDuration) { this.estimatedDuration = estimatedDuration; }

    public void set_peopleCnt(int num){
        this.peopleCnt=num;
    }
    public void setRoute(Route route){this.route=route;}
    public void setRouteStops(List<RouteStop> routeStops) { this.routeStops = routeStops; }

    public int getProjectId() { return projectId; }
    public void setProjectId(int projectId) { this.projectId = projectId; }

    public int getRouteId() { return routeId; }
    public void setRouteId(int routeId) { this.routeId = routeId; }

    public String getTitle() { return title != null ? title : ""; }
    public void setTitle(String title) { this.title = title; }

    public String getDepartureDate() { return departureDate != null ? departureDate : ""; }
    public void setDepartureDate(String departureDate) { this.departureDate = departureDate; }

    public int getCurrentMembers() { return currentMembers; }
    public void setCurrentMembers(int currentMembers) { this.currentMembers = currentMembers; }

    public String getCreatedAt() { return createdAt != null ? createdAt : ""; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}