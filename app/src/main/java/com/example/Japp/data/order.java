package com.example.Japp.data;

import java.io.Serializable;
import java.util.List;
import java.time.*;
import java.util.concurrent.ThreadLocalRandom;

public class order implements Serializable {
    private String id;
    private long startTime;

    private User customer;
    private String from,to,routeImageUrl,estimatedDuration,estimatedStartTime,estimatedEndTime;
    List<String> tags;
    private int peopleCnt;
    enum OrderStatus{AVAILABLE, TAKEN_BY_OTHER, EXPIRED, ACCEPTED_BY_ME}
    private OrderStatus orderStatus;


    public order(){
        this.id=ID.Generate_id();
        this.startTime=System.currentTimeMillis();
    }
    public void set_peopleCnt(int num){
        this.peopleCnt=num;
    }

    public void set_from(String from){
        this.from=from;
    }

    public void set_to(String to){
        this.to=to;
    }
}