package com.example.Japp.user.util;

import androidx.annotation.Nullable;

import com.google.gson.JsonElement;

import java.net.SocketTimeoutException;

public final class RoutePlanHelper {

    private RoutePlanHelper() {}

    public static String buildMemoryId(int accountId) {
        return "user-" + accountId;
    }

    public static int parseRouteId(@Nullable JsonElement data) {
        if (data == null || data.isJsonNull()) {
            return -1;
        }
        try {
            if (data.isJsonPrimitive()) {
                return data.getAsInt();
            }
        } catch (Exception ignored) {
            try {
                return (int) data.getAsLong();
            } catch (Exception ignored2) {
                return -1;
            }
        }
        return -1;
    }

    public static String failureMessage(Throwable t) {
        if (t instanceof SocketTimeoutException) {
            return "路线规划超时，请稍后重试";
        }
        String message = t.getMessage();
        if (message == null || message.isEmpty()) {
            return "网络错误，路线规划失败";
        }
        if (message.contains("timeout") || message.contains("Timeout")) {
            return "路线规划超时，请稍后重试";
        }
        return "网络错误，路线规划失败";
    }
}
