package com.example.Japp.user.fragment.route;

import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.LatLngBounds;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.Polyline;
import com.amap.api.maps.model.PolylineOptions;
import com.example.Japp.data.RouteStop;
import com.example.Japp.network.models.RouteNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 在 AMap 上绘制研学主题色路线并自适应镜头。 */
public final class RouteMapDrawHelper {

    private static final String TAG = "RouteMapDrawHelper";
    private static final Pattern COORDINATE_PATTERN = Pattern.compile("(-?\\d+(?:\\.\\d+)?)");

    public static final float ROUTE_LINE_WIDTH = 20f;
    public static final int ROUTE_LINE_COLOR = 0xFF168B78;

    private RouteMapDrawHelper() {}

    @Nullable
    public static Polyline drawRoute(@Nullable AMap aMap, @NonNull List<LatLng> points) {
        return drawRoute(aMap, points, null);
    }

    /**
     * @param roadPoints 沿道路折线（可很密）
     * @param waypoints  站点坐标，用于打标记；为空时用折线首尾打点
     */
    @Nullable
    public static Polyline drawRoute(@Nullable AMap aMap,
                                     @NonNull List<LatLng> roadPoints,
                                     @Nullable List<LatLng> waypoints) {
        if (aMap == null || roadPoints.size() < 2) {
            return null;
        }
        aMap.clear();
        Polyline polyline = aMap.addPolyline(new PolylineOptions()
                .addAll(roadPoints)
                .width(ROUTE_LINE_WIDTH)
                .color(ROUTE_LINE_COLOR)
                .geodesic(false));

        List<LatLng> markers = (waypoints != null && !waypoints.isEmpty()) ? waypoints : roadPoints;
        if (markers.size() == 1) {
            aMap.addMarker(new MarkerOptions().position(markers.get(0)).title("地点"));
        } else {
            for (int i = 0; i < markers.size(); i++) {
                String title;
                if (i == 0) {
                    title = "起点";
                } else if (i == markers.size() - 1) {
                    title = "终点";
                } else {
                    title = "途经" + i;
                }
                aMap.addMarker(new MarkerOptions().position(markers.get(i)).title(title));
            }
        }

        fitCamera(aMap, roadPoints, 48);
        return polyline;
    }

    public static void fitCamera(@Nullable AMap aMap, @NonNull List<LatLng> points, int paddingPx) {
        if (aMap == null || points.isEmpty()) {
            return;
        }
        if (points.size() == 1) {
            aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(points.get(0), 14f));
            return;
        }
        LatLngBounds.Builder builder = LatLngBounds.builder();
        for (LatLng p : points) {
            builder.include(p);
        }
        try {
            aMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), paddingPx));
        } catch (Exception e) {
            aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(points.get(0), 13f));
        }
    }

    @NonNull
    public static List<LatLng> extractPointsFromNodes(@Nullable List<RouteNode> nodes) {
        List<LatLng> points = new ArrayList<>();
        if (nodes == null || nodes.isEmpty()) {
            return points;
        }
        List<RouteNode> ordered = new ArrayList<>(nodes);
        Collections.sort(ordered, Comparator.comparingInt(RouteNode::getVisitOrder));
        for (RouteNode node : ordered) {
            LatLng point = parseLocation(node.getLocation());
            if (point != null) {
                points.add(point);
            }
        }
        return points;
    }

    @NonNull
    public static List<LatLng> extractPointsFromStops(@Nullable List<RouteStop> stops) {
        List<LatLng> points = new ArrayList<>();
        if (stops == null || stops.isEmpty()) {
            return points;
        }
        List<RouteStop> ordered = new ArrayList<>(stops);
        Collections.sort(ordered, Comparator.comparingInt(RouteStop::getVisitOrder));
        for (RouteStop stop : ordered) {
            LatLng point = parseLocation(stop.getLocation());
            if (point != null) {
                points.add(point);
            }
        }
        return points;
    }

    @Nullable
    public static LatLng parseLocation(@Nullable String location) {
        if (TextUtils.isEmpty(location)) {
            return null;
        }
        String normalized = location.replace(";", ",").replace("，", ",");
        Matcher matcher = COORDINATE_PATTERN.matcher(normalized);
        double first;
        double second;
        if (matcher.find()) {
            try {
                first = Double.parseDouble(matcher.group(1));
            } catch (NumberFormatException e) {
                return null;
            }
        } else {
            return null;
        }
        if (matcher.find()) {
            try {
                second = Double.parseDouble(matcher.group(1));
            } catch (NumberFormatException e) {
                return null;
            }
        } else {
            return null;
        }
        double lng = first;
        double lat = second;
        if (Math.abs(lat) > 90 && Math.abs(lng) <= 90) {
            lat = first;
            lng = second;
        } else if (Math.abs(first) <= 90 && Math.abs(second) > 90) {
            lat = first;
            lng = second;
        }
        if (Math.abs(lat) > 90 || Math.abs(lng) > 180) {
            Log.w(TAG, "Invalid coordinate: " + location);
            return null;
        }
        return new LatLng(lat, lng);
    }
}
