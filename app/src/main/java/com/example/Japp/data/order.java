package com.example.Japp.data;

import java.io.Serializable;
import java.util.List;
import java.time.*;
import java.util.concurrent.ThreadLocalRandom;

public class order implements Serializable {
    private String id;
    private long startTime;

    private int projectId;
    private String title;
    private String departureDate;
    private int currentMembers;

    private User customer;
    private String city;
    private String tag;
    private String routeImageUrl,estimatedDuration,estimatedStartTime,estimatedEndTime;

    private Route route;
    List<String> tags;

    public String getPeopleCnt() {
        return peopleCnt+"人";
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

    public int getProjectId() { return projectId; }
    public void setProjectId(int projectId) { this.projectId = projectId; }

    public String getTitle() { return title != null ? title : ""; }
    public void setTitle(String title) { this.title = title; }

    public String getDepartureDate() { return departureDate != null ? departureDate : ""; }
    public void setDepartureDate(String departureDate) { this.departureDate = departureDate; }

    public int getCurrentMembers() { return currentMembers; }
    public void setCurrentMembers(int currentMembers) { this.currentMembers = currentMembers; }
}