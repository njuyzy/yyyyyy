package com.example.Japp.data;

import java.util.List;
import java.time.*;
import java.util.concurrent.ThreadLocalRandom;

public class order {
    private String id;
    private long startTime;
    private String from,to,routeImageUrl,customerName,estimatedDuration,estimatedStartTime,estimatedEndTime;
    List<String> tags;
    private int peopleCnt;
    enum OrderStatus{AVAILABLE, TAKEN_BY_OTHER, EXPIRED, ACCEPTED_BY_ME}
    private OrderStatus orderStatus;

    public static String Generate_id(){
        long timestamp= System.currentTimeMillis();
        int random= ThreadLocalRandom.current().nextInt(1000, 9999);
        return timestamp+""+random;
    }
    protected order(){
        this.id=Generate_id();
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