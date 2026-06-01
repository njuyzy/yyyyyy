package com.example.Japp.user.fragment.route;

import com.amap.api.maps.model.LatLng;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RouteChatItem {

    public static final int TYPE_USER = 0;
    public static final int TYPE_ASSISTANT_ROUTE = 1;

    private final int type;
    private final String text;
    private final long timestamp;
    private final int routeId;
    private final List<LatLng> polylinePoints;

    private RouteChatItem(int type, String text, long timestamp, int routeId, List<LatLng> polylinePoints) {
        this.type = type;
        this.text = text;
        this.timestamp = timestamp;
        this.routeId = routeId;
        this.polylinePoints = polylinePoints != null
                ? new ArrayList<>(polylinePoints)
                : new ArrayList<>();
    }

    public static RouteChatItem user(String text) {
        return new RouteChatItem(TYPE_USER, text, System.currentTimeMillis(), 0, null);
    }

    public static RouteChatItem assistantRoute(String text, List<LatLng> points) {
        return assistantRoute(text, points, 0);
    }

    public static RouteChatItem assistantRoute(String text, List<LatLng> points, int routeId) {
        return new RouteChatItem(TYPE_ASSISTANT_ROUTE, text, System.currentTimeMillis(), routeId, points);
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

    public boolean hasPolyline() {
        return polylinePoints.size() >= 2;
    }

    public List<LatLng> getPolylinePoints() {
        return Collections.unmodifiableList(polylinePoints);
    }

    public int getRouteId() {
        return routeId;
    }

    public boolean canPublish() {
        return type == TYPE_ASSISTANT_ROUTE && routeId > 0;
    }
}
