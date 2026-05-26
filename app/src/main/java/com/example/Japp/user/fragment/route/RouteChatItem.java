package com.example.Japp.user.fragment.route;

import com.amap.api.maps.model.LatLng;

import java.util.Collections;
import java.util.List;

/**
 * 路线对话中的一条记录：用户要求 或 后端返回的路线说明。
 */
public class RouteChatItem {

    public static final int TYPE_USER = 0;
    public static final int TYPE_ASSISTANT_ROUTE = 1;

    private final int type;
    private final String text;
    private final long timestamp;
    /** 后端返回的折线路径（GCJ-02），用于地图连线；用户消息为 null */
    private final List<LatLng> polylinePoints;

    public RouteChatItem(int type, String text, long timestamp, List<LatLng> polylinePoints) {
        this.type = type;
        this.text = text;
        this.timestamp = timestamp;
        this.polylinePoints = polylinePoints != null ? polylinePoints : Collections.emptyList();
    }

    public static RouteChatItem user(String text) {
        return new RouteChatItem(TYPE_USER, text, System.currentTimeMillis(), null);
    }

    public static RouteChatItem assistantRoute(String text, List<LatLng> polylinePoints) {
        return new RouteChatItem(TYPE_ASSISTANT_ROUTE, text, System.currentTimeMillis(), polylinePoints);
    }

    public int getType() {
        return type;
    }

    public String getText() {
        return text;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public List<LatLng> getPolylinePoints() {
        return polylinePoints;
    }

    public boolean hasPolyline() {
        return polylinePoints != null && !polylinePoints.isEmpty();
    }
}
