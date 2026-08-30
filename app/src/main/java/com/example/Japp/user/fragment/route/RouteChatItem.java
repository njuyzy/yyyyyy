package com.example.Japp.user.fragment.route;

import com.amap.api.maps.model.LatLng;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RouteChatItem {

    public static final int TYPE_USER = 0;
    public static final int TYPE_ASSISTANT_ROUTE = 1;
    /** 路线助手状态/等待反馈（无地图、不可发布） */
    public static final int TYPE_ASSISTANT_STATUS = 2;

    private final int type;
    private String text;
    private final long timestamp;
    private final int routeId;
    private final List<LatLng> polylinePoints;
    private final List<LatLng> waypointPoints;
    private final boolean publishAllowed;

    private RouteChatItem(int type, String text, long timestamp, int routeId,
                          List<LatLng> polylinePoints, List<LatLng> waypointPoints,
                          boolean publishAllowed) {
        this.type = type;
        this.text = type == TYPE_ASSISTANT_ROUTE
                ? hideInternalRouteId(text)
                : (text != null ? text : "");
        this.timestamp = timestamp;
        this.routeId = routeId;
        this.publishAllowed = publishAllowed;
        this.polylinePoints = polylinePoints != null
                ? new ArrayList<>(polylinePoints)
                : new ArrayList<>();
        this.waypointPoints = waypointPoints != null
                ? new ArrayList<>(waypointPoints)
                : new ArrayList<>();
    }

    public static RouteChatItem user(String text) {
        return new RouteChatItem(
                TYPE_USER, text, System.currentTimeMillis(), 0, null, null, false);
    }

    public static RouteChatItem assistantStatus(String text) {
        return new RouteChatItem(
                TYPE_ASSISTANT_STATUS, text, System.currentTimeMillis(), 0, null, null, false);
    }

    public static RouteChatItem assistantRoute(String text, List<LatLng> points) {
        return assistantRoute(text, points, 0);
    }

    public static RouteChatItem assistantRoute(String text, List<LatLng> points, int routeId) {
        return assistantRoute(text, points, null, routeId);
    }

    public static RouteChatItem assistantRoute(String text,
                                               List<LatLng> roadPolyline,
                                               List<LatLng> waypoints,
                                               int routeId) {
        return assistantRoute(text, roadPolyline, waypoints, routeId, true);
    }

    public static RouteChatItem assistantRoute(String text,
                                               List<LatLng> roadPolyline,
                                               List<LatLng> waypoints,
                                               int routeId,
                                               boolean publishAllowed) {
        return new RouteChatItem(TYPE_ASSISTANT_ROUTE, text, System.currentTimeMillis(),
                routeId, roadPolyline, waypoints, publishAllowed);
    }

    static RouteChatItem restore(int type,
                                 String text,
                                 long timestamp,
                                 int routeId,
                                 List<LatLng> roadPolyline,
                                 List<LatLng> waypoints,
                                 boolean publishAllowed) {
        return new RouteChatItem(type, text, timestamp, routeId,
                roadPolyline, waypoints, publishAllowed);
    }

    public void setText(String text) {
        this.text = text != null ? text : "";
    }

    /** 兼容本地已保存的旧消息，路线 ID 仅供内部发布流程使用。 */
    private static String hideInternalRouteId(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replaceAll("(?m)^\\s*路线编号[：:]\\s*\\d+\\s*(?:\\r?\\n)?", "")
                .replaceAll("[（(]ID\\s*[：:]\\s*\\d+[）)]", "")
                .replaceAll("\\n{3,}", "\\n\\n")
                .trim();
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

    public List<LatLng> getWaypointPoints() {
        return Collections.unmodifiableList(waypointPoints);
    }

    public int getRouteId() {
        return routeId;
    }

    public boolean isStatus() {
        return type == TYPE_ASSISTANT_STATUS;
    }

    public boolean canPublish() {
        return type == TYPE_ASSISTANT_ROUTE && routeId > 0 && publishAllowed;
    }

    boolean isPublishAllowed() {
        return publishAllowed;
    }
}
