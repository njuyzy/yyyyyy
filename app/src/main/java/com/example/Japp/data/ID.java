package com.example.Japp.data;

import java.util.concurrent.ThreadLocalRandom;

public class ID {
    public static String Generate_id(){
        long timestamp= System.currentTimeMillis();
        int random= ThreadLocalRandom.current().nextInt(1000, 9999);
        return timestamp+""+random;
    }

    // ID转换工具
    public static String convertServerIdToLocalId(int serverId) {
        return "user_" + serverId;
    }

    public static int convertLocalIdToServerId(String localId) {
        if (localId == null || !localId.startsWith("user_")) {
            return -1; // 无效ID
        }
        try {
            return Integer.parseInt(localId.substring(5));
        } catch (NumberFormatException e) {
            return -1; // 转换失败
        }
    }

    public static boolean isValidServerId(int id) {
        return id > 0;
    }

    public static boolean isValidLocalId(String id) {
        if (id == null) return false;
        if (id.startsWith("user_")) {
            try {
                int serverId = Integer.parseInt(id.substring(5));
                return serverId > 0;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return false;
    }
}
