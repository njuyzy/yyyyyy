package com.example.Japp.data;

import java.io.Serializable;
import java.util.List;
import java.time.*;
import java.util.concurrent.ThreadLocalRandom;

public class order implements Serializable {
    private String id;
    private long startTime;

    private User customer;
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


    public void set_peopleCnt(int num){
        this.peopleCnt=num;
    }
    public void setRoute(Route route){this.route=route;}
}