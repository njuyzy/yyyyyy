package com.example.Japp.user.fragment.route;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.LatLngBounds;
import com.amap.api.maps.model.Marker;
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
    public static final int ROUTE_LINE_COLOR = 0xFF1E72FF;

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
        return drawRoute(aMap, roadPoints, waypoints, true);
    }

    /**
     * @param showOriginMarker false 时保留定位蓝点作为起点，不再叠加默认 Marker。
     */
    @Nullable
    public static Polyline drawRoute(@Nullable AMap aMap,
                                     @NonNull List<LatLng> roadPoints,
                                     @Nullable List<LatLng> waypoints,
                                     boolean showOriginMarker) {
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
            int firstMarker = showOriginMarker ? 0 : 1;
            for (int i = firstMarker; i < markers.size(); i++) {
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

    /**
     * 按节点最新 visitOrder 绘制醒目的数字站点，并把节点挂到 Marker 上供信息卡使用。
     */
    @Nullable
    public static Polyline drawRouteWithNodes(@Nullable AMap aMap,
                                              @NonNull List<LatLng> roadPoints,
                                              @NonNull List<RouteNode> nodes) {
        if (aMap == null || roadPoints.isEmpty()) {
            return null;
        }
        aMap.clear();
        Polyline polyline = null;
        if (roadPoints.size() >= 2) {
            polyline = aMap.addPolyline(new PolylineOptions()
                    .addAll(roadPoints)
                    .width(ROUTE_LINE_WIDTH)
                    .color(ROUTE_LINE_COLOR)
                    .geodesic(false));
        }

        List<RouteNode> ordered = new ArrayList<>(nodes);
        Collections.sort(ordered, Comparator.comparingInt(RouteNode::getVisitOrder));
        for (int i = 0; i < ordered.size(); i++) {
            RouteNode node = ordered.get(i);
            LatLng point = parseLocation(node.getLocation());
            if (point == null) {
                continue;
            }
            int number = node.getVisitOrder() > 0 ? node.getVisitOrder() : i + 1;
            Marker marker = aMap.addMarker(new MarkerOptions()
                    .position(point)
                    .anchor(0.5f, 0.94f)
                    .icon(BitmapDescriptorFactory.fromBitmap(createNumberedMarker(number)))
                    .title(TextUtils.isEmpty(node.getName()) ? "地点 " + number : node.getName())
                    .snippet(node.getAddress()));
            marker.setObject(node);
        }
        fitCamera(aMap, roadPoints, 48);
        return polyline;
    }

    @NonNull
    private static Bitmap createNumberedMarker(int number) {
        float density = Resources.getSystem().getDisplayMetrics().density;
        int width = Math.round(36f * density);
        int height = Math.round(43f * density);
        float centerX = width / 2f;
        float centerY = 16.5f * density;
        float radius = 13f * density;

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Path pointer = new Path();
        pointer.moveTo(centerX - 6f * density, centerY + 10f * density);
        pointer.lineTo(centerX, 40f * density);
        pointer.lineTo(centerX + 6f * density, centerY + 10f * density);
        pointer.close();

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x33000000);
        canvas.drawCircle(centerX + density, centerY + 2f * density,
                radius + 2f * density, paint);
        canvas.save();
        canvas.translate(density, 2f * density);
        canvas.drawPath(pointer, paint);
        canvas.restore();

        paint.setColor(Color.WHITE);
        canvas.drawCircle(centerX, centerY, radius + 2f * density, paint);
        canvas.drawPath(pointer, paint);
        paint.setColor(ROUTE_LINE_COLOR);
        canvas.drawCircle(centerX, centerY, radius, paint);
        canvas.drawPath(pointer, paint);

        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setFakeBoldText(true);
        paint.setTextSize((number >= 10 ? 10.5f : 13f) * density);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        float baseline = centerY - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText(String.valueOf(number), centerX, baseline, paint);
        return bitmap;
    }

    public static void fitCamera(@Nullable AMap aMap, @NonNull List<LatLng> points, int paddingPx) {
        if (aMap == null || points.isEmpty()) {
            return;
        }
        if (points.size() == 1) {
            try {
                aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(points.get(0), 14f));
            } catch (Throwable ignored) {
            }
            return;
        }
        LatLngBounds.Builder builder = LatLngBounds.builder();
        for (LatLng p : points) {
            builder.include(p);
        }
        try {
            // 改用 moveCamera：animateCamera 会在 GLThread 启动异步动画，与 Activity 销毁时
            // MapView.onDestroy() 在原生层释放 GL 上下文产生竞态，触发 libAMapSDK_MAP_v9_8_3.so
            // 的 nativeDestroy 崩溃。详情页镜头一次性移动即可，避免该竞态。
            aMap.moveCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), paddingPx));
        } catch (Throwable e) {
            try {
                aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(points.get(0), 13f));
            } catch (Throwable ignored) {
            }
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
