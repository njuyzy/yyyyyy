package com.example.Japp.data;

import java.util.concurrent.ThreadLocalRandom;

public class ID {
    public static String Generate_id(){
        long timestamp= System.currentTimeMillis();
        int random= ThreadLocalRandom.current().nextInt(1000, 9999);
        return timestamp+""+random;
    }
}
