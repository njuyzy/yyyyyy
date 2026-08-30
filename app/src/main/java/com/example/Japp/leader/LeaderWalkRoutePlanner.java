package com.example.Japp.leader;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.amap.api.maps.AMap;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.PolylineOptions;
import com.amap.api.services.core.AMapException;
import com.amap.api.services.core.LatLonPoint;
import com.amap.api.services.route.BusRouteResult;
import com.amap.api.services.route.DrivePath;
import com.amap.api.services.route.DriveRouteResult;
import com.amap.api.services.route.DriveStep;
import com.amap.api.services.route.RideRouteResult;
import com.amap.api.services.route.RouteSearch;
import com.amap.api.services.route.WalkPath;
import com.amap.api.services.route.WalkRouteResult;
import com.amap.api.services.route.WalkStep;
import com.example.Japp.network.models.RouteNode;
import com.example.Japp.user.fragment.route.RouteMapDrawHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** 基于高德步行路径规划，在地图上绘制站点标记与步行路线 */
public class LeaderWalkRoutePlanner implements RouteSearch.OnRouteSearchListener {

    private static final String TAG = "LeaderWalkRoutePlanner";

    public interface Callback {
        void onPlanningStarted();

        void onPlanningFinished(@NonNull String summary,
                                @NonNull ArrayList<String> instructions,
                                boolean hadFailures);

        /**
         * 带道路折线的完成回调。默认转调三参数版本，兼容旧调用方。
         * @param roadPolyline 沿道路的折线点；道路规划不完整时返回空列表，禁止站点直连
         */
        default void onPlanningFinished(@NonNull String summary,
                                        @NonNull ArrayList<String> instructions,
                                        @NonNull List<LatLng> roadPolyline,
                                        boolean hadFailures) {
            onPlanningFinished(summary, instructions, hadFailures);
        }

        void onPlanningFailed(@NonNull String message);
    }

    private final Context appContext;
    @Nullable
    private RouteSearch routeSearch;
    @Nullable
    private AMap aMap;
    @Nullable
    private Callback callback;

    private final List<LatLng> routePoints = new ArrayList<>();
    private final List<String> routePointLabels = new ArrayList<>();
    private final List<LatLng> plannedPolylinePoints = new ArrayList<>();
    private final ArrayList<String> walkInstructions = new ArrayList<>();

    private int currentSegmentIndex;
    private int totalWalkDistance;
    private int totalWalkDuration;
    private boolean routeHasFailedSegment;
    private boolean requestingDriveFallback;

    public LeaderWalkRoutePlanner(@NonNull Context context) {
        appContext = context.getApplicationContext();
        try {
            // 必须使用 applicationContext；用 Activity 上下文在切回前台时容易触发 AMap 原生层崩溃
            routeSearch = new RouteSearch(appContext);
            routeSearch.setRouteSearchListener(this);
        } catch (AMapException e) {
            routeSearch = null;
            Log.w(TAG, "RouteSearch init failed", e);
        }
    }

    public void plan(@Nullable AMap map, @NonNull List<RouteNode> nodes, @NonNull Callback cb) {
        prepareNodes(map, nodes, cb);
    }

    /** 仅计算步行规划结果，不在地图上绘制（用于详情页等不适合嵌入 MapView 的场景） */
    public void planSummary(@NonNull List<RouteNode> nodes, @NonNull Callback cb) {
        prepareNodes(null, nodes, cb);
    }

    private void prepareNodes(@Nullable AMap map, @NonNull List<RouteNode> nodes, @NonNull Callback cb) {
        aMap = map;
        callback = cb;

        List<RouteNode> orderedNodes = new ArrayList<>(nodes);
        Collections.sort(orderedNodes, Comparator.comparingInt(RouteNode::getVisitOrder));

        if (aMap != null) {
            aMap.clear();
        }
        routePointLabels.clear();
        routePoints.clear();

        for (int i = 0; i < orderedNodes.size(); i++) {
            RouteNode node = orderedNodes.get(i);
            LatLng point = RouteMapDrawHelper.parseLocation(node.getLocation());
            if (point == null) {
                continue;
            }
            routePoints.add(point);
            int order = node.getVisitOrder() > 0 ? node.getVisitOrder() : (i + 1);
            String title = !TextUtils.isEmpty(node.getName())
                    ? node.getName()
                    : (!TextUtils.isEmpty(node.getAddress()) ? node.getAddress() : "未命名地点");
            routePointLabels.add(title);
            if (aMap != null) {
                aMap.addMarker(new MarkerOptions()
                        .position(point)
                        .title(title)
                        .snippet(buildMarkerSnippet(node, order)));
            }
        }

        if (routePoints.isEmpty()) {
            cb.onPlanningFailed("路线缺少坐标，无法规划");
            return;
        }

        if (aMap != null) {
            RouteMapDrawHelper.fitCamera(aMap, routePoints, 48);
        }

        if (routePoints.size() < 2) {
            cb.onPlanningFinished("单点路线", new ArrayList<>(), new ArrayList<>(routePoints), false);
            return;
        }

        cb.onPlanningStarted();
        startRoutePlanning();
    }

    public void cancel() {
        callback = null;
        aMap = null;
        if (routeSearch != null) {
            try {
                // 解绑监听器避免 Activity 销毁后仍收到异步回调导致的崩溃
                routeSearch.setRouteSearchListener(null);
            } catch (Throwable t) {
                Log.w(TAG, "detach listener failed", t);
            }
            routeSearch = null;
        }
    }

    private void startRoutePlanning() {
        if (routeSearch == null) {
            if (callback != null) {
                callback.onPlanningFailed("道路规划服务初始化失败");
            }
            return;
        }
        plannedPolylinePoints.clear();
        walkInstructions.clear();
        currentSegmentIndex = 0;
        totalWalkDistance = 0;
        totalWalkDuration = 0;
        routeHasFailedSegment = false;
        requestingDriveFallback = false;
        requestNextWalkSegment();
    }

    private void requestNextWalkSegment() {
        if (routePoints.size() < 2) {
            return;
        }
        if (currentSegmentIndex >= routePoints.size() - 1) {
            onPlanningFinished();
            return;
        }
        LatLng from = routePoints.get(currentSegmentIndex);
        LatLng to = routePoints.get(currentSegmentIndex + 1);
        if (isSamePoint(from, to)) {
            appendDuplicateSegmentNote(currentSegmentIndex);
            currentSegmentIndex++;
            requestNextWalkSegment();
            return;
        }
        RouteSearch.FromAndTo fromAndTo = new RouteSearch.FromAndTo(toLatLonPoint(from), toLatLonPoint(to));
        RouteSearch.WalkRouteQuery query = new RouteSearch.WalkRouteQuery(fromAndTo, RouteSearch.WalkDefault);
        requestingDriveFallback = false;
        routeSearch.calculateWalkRouteAsyn(query);
    }

    /** 步行路线不可用时再请求一次真实驾车道路，仍失败则只展示站点，不画直线。 */
    private void requestDriveFallback() {
        if (routeSearch == null || currentSegmentIndex < 0
                || currentSegmentIndex >= routePoints.size() - 1) {
            skipFailedSegment();
            return;
        }
        LatLng from = routePoints.get(currentSegmentIndex);
        LatLng to = routePoints.get(currentSegmentIndex + 1);
        RouteSearch.FromAndTo fromAndTo = new RouteSearch.FromAndTo(
                toLatLonPoint(from), toLatLonPoint(to));
        RouteSearch.DriveRouteQuery query = new RouteSearch.DriveRouteQuery(
                fromAndTo,
                RouteSearch.DRIVING_SINGLE_SHORTEST,
                null,
                null,
                "");
        requestingDriveFallback = true;
        routeSearch.calculateDriveRouteAsyn(query);
    }

    private void onPlanningFinished() {
        if (aMap != null && !routeHasFailedSegment && plannedPolylinePoints.size() >= 2) {
            drawPlannedPolyline();
        }
        notifyFinished(routeHasFailedSegment);
    }

    private void notifyFinished(boolean hadFailures) {
        if (callback == null) {
            return;
        }
        String summary;
        if (hadFailures) {
            summary = "部分路段未获取到真实道路，已隐藏不准确的路线连线";
        } else {
            summary = formatWalkTime(totalWalkDuration) + "（" + formatWalkDistance(totalWalkDistance) + "）";
        }
        List<LatLng> roadPolyline = !hadFailures && plannedPolylinePoints.size() >= 2
                ? new ArrayList<>(plannedPolylinePoints)
                : new ArrayList<>();
        callback.onPlanningFinished(summary, new ArrayList<>(walkInstructions),
                roadPolyline, hadFailures);
    }

    private void drawPlannedPolyline() {
        if (aMap == null || plannedPolylinePoints.size() < 2) {
            return;
        }
        aMap.addPolyline(new PolylineOptions()
                .addAll(plannedPolylinePoints)
                .color(RouteMapDrawHelper.ROUTE_LINE_COLOR)
                .width(RouteMapDrawHelper.ROUTE_LINE_WIDTH));
    }

    private LatLonPoint toLatLonPoint(LatLng latLng) {
        return new LatLonPoint(latLng.latitude, latLng.longitude);
    }

    private void appendSegmentPoints(List<LatLng> segmentPoints) {
        if (segmentPoints == null || segmentPoints.isEmpty()) {
            return;
        }
        if (!plannedPolylinePoints.isEmpty()
                && isSamePoint(plannedPolylinePoints.get(plannedPolylinePoints.size() - 1), segmentPoints.get(0))) {
            plannedPolylinePoints.addAll(segmentPoints.subList(1, segmentPoints.size()));
            return;
        }
        plannedPolylinePoints.addAll(segmentPoints);
    }

    private boolean isSamePoint(LatLng a, LatLng b) {
        return Math.abs(a.latitude - b.latitude) < 1e-6 && Math.abs(a.longitude - b.longitude) < 1e-6;
    }

    private List<LatLng> extractWalkPathPoints(WalkPath path, LatLng from, LatLng to) {
        List<LatLng> segmentPoints = new ArrayList<>();
        if (path != null && path.getSteps() != null) {
            for (WalkStep step : path.getSteps()) {
                if (step.getPolyline() == null) {
                    continue;
                }
                for (LatLonPoint point : step.getPolyline()) {
                    segmentPoints.add(new LatLng(point.getLatitude(), point.getLongitude()));
                }
            }
        }
        return segmentPoints;
    }

    private List<LatLng> extractDrivePathPoints(@Nullable DrivePath path) {
        List<LatLng> segmentPoints = new ArrayList<>();
        if (path == null || path.getSteps() == null) {
            return segmentPoints;
        }
        for (DriveStep step : path.getSteps()) {
            if (step.getPolyline() == null) {
                continue;
            }
            for (LatLonPoint point : step.getPolyline()) {
                segmentPoints.add(new LatLng(point.getLatitude(), point.getLongitude()));
            }
        }
        return segmentPoints;
    }

    private void appendWalkInstructions(@Nullable WalkPath path, int segmentIndex, boolean fallback) {
        String fromLabel = getRoutePointLabel(segmentIndex);
        String toLabel = getRoutePointLabel(segmentIndex + 1);
        walkInstructions.add("第" + (segmentIndex + 1) + "段：" + fromLabel + " -> " + toLabel);
        if (fallback) {
            walkInstructions.add("步行路线不可用，已改用驾车道路参考");
            return;
        }
        if (path == null || path.getSteps() == null || path.getSteps().isEmpty()) {
            walkInstructions.add("请沿当前道路前往下一站");
            return;
        }
        int stepOrder = 1;
        for (WalkStep step : path.getSteps()) {
            String instruction = step.getInstruction();
            if (!TextUtils.isEmpty(instruction)) {
                walkInstructions.add(stepOrder + ". " + instruction);
                stepOrder++;
            }
        }
        if (stepOrder == 1) {
            walkInstructions.add("请沿当前道路前往下一站");
        }
    }

    private void appendDriveInstructions(@NonNull DrivePath path, int segmentIndex) {
        String fromLabel = getRoutePointLabel(segmentIndex);
        String toLabel = getRoutePointLabel(segmentIndex + 1);
        walkInstructions.add("第" + (segmentIndex + 1) + "段：" + fromLabel + " -> " + toLabel);
        walkInstructions.add("步行路线不可用，以下为驾车道路参考");
        if (path.getSteps() == null) {
            return;
        }
        int stepOrder = 1;
        for (DriveStep step : path.getSteps()) {
            if (!TextUtils.isEmpty(step.getInstruction())) {
                walkInstructions.add(stepOrder + ". " + step.getInstruction());
                stepOrder++;
            }
        }
    }

    private void appendDuplicateSegmentNote(int segmentIndex) {
        String fromLabel = getRoutePointLabel(segmentIndex);
        String toLabel = getRoutePointLabel(segmentIndex + 1);
        walkInstructions.add("第" + (segmentIndex + 1) + "段：" + fromLabel + " -> " + toLabel);
        walkInstructions.add("起点与终点重合，已跳过该段");
    }

    private String getRoutePointLabel(int index) {
        if (index >= 0 && index < routePointLabels.size()) {
            String label = routePointLabels.get(index);
            if (!TextUtils.isEmpty(label)) {
                return label;
            }
        }
        return "第" + (index + 1) + "站";
    }

    private void skipFailedSegment() {
        String fromLabel = getRoutePointLabel(currentSegmentIndex);
        String toLabel = getRoutePointLabel(currentSegmentIndex + 1);
        walkInstructions.add("第" + (currentSegmentIndex + 1) + "段："
                + fromLabel + " -> " + toLabel);
        walkInstructions.add("道路规划失败，该段仅显示站点，不绘制直线");
        routeHasFailedSegment = true;
        requestingDriveFallback = false;
        currentSegmentIndex++;
        requestNextWalkSegment();
    }

    private String buildMarkerSnippet(RouteNode node, int order) {
        String time = TextUtils.isEmpty(node.getVisitTime()) ? "时间待定" : node.getVisitTime();
        String duration = node.getRecommendedDuration() > 0
                ? node.getRecommendedDuration() + "分钟"
                : "时长待定";
        String address = TextUtils.isEmpty(node.getAddress()) ? "地址待补充" : node.getAddress();
        String city = TextUtils.isEmpty(node.getCityname()) ? "城市待补充" : node.getCityname();
        String notes = TextUtils.isEmpty(node.getNotes()) ? "暂无备注" : node.getNotes();
        return "第" + order + "站"
                + "\n时间：" + time
                + "\n时长：" + duration
                + "\n地点：" + city + " " + address
                + "\n备注：" + notes;
    }

    private String formatWalkTime(int seconds) {
        if (seconds < 60) {
            return "1分钟以内";
        }
        int minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + "分钟";
        }
        int hours = minutes / 60;
        int remainMin = minutes % 60;
        if (remainMin == 0) {
            return hours + "小时";
        }
        return String.format(Locale.CHINA, "%d小时%d分钟", hours, remainMin);
    }

    private String formatWalkDistance(int meters) {
        if (meters < 1000) {
            return meters + "米";
        }
        return String.format(Locale.CHINA, "%.1f公里", meters / 1000f);
    }

    @Override
    public void onBusRouteSearched(BusRouteResult busRouteResult, int i) {
    }

    @Override
    public void onDriveRouteSearched(DriveRouteResult result, int errorCode) {
        if (callback == null || routeSearch == null || !requestingDriveFallback
                || currentSegmentIndex < 0
                || currentSegmentIndex >= routePoints.size() - 1) {
            return;
        }
        requestingDriveFallback = false;
        if (errorCode != AMapException.CODE_AMAP_SUCCESS
                || result == null
                || result.getPaths() == null
                || result.getPaths().isEmpty()) {
            Log.w(TAG, "Drive fallback failed segment=" + currentSegmentIndex
                    + ", errorCode=" + errorCode);
            skipFailedSegment();
            return;
        }
        DrivePath bestPath = result.getPaths().get(0);
        List<LatLng> segmentPoints = extractDrivePathPoints(bestPath);
        if (segmentPoints.size() < 2) {
            skipFailedSegment();
            return;
        }
        totalWalkDistance += (int) bestPath.getDistance();
        totalWalkDuration += (int) bestPath.getDuration();
        appendDriveInstructions(bestPath, currentSegmentIndex);
        appendSegmentPoints(segmentPoints);
        currentSegmentIndex++;
        requestNextWalkSegment();
    }

    @Override
    public void onWalkRouteSearched(WalkRouteResult result, int errorCode) {
        if (callback == null || routeSearch == null || routePoints.isEmpty()) {
            return;
        }
        if (currentSegmentIndex < 0 || currentSegmentIndex >= routePoints.size() - 1) {
            return;
        }
        LatLng from = routePoints.get(currentSegmentIndex);
        LatLng to = routePoints.get(currentSegmentIndex + 1);
        if (errorCode != AMapException.CODE_AMAP_SUCCESS
                || result == null
                || result.getPaths() == null
                || result.getPaths().isEmpty()) {
            Log.w(TAG, "Walk route failed segment=" + currentSegmentIndex + ", errorCode=" + errorCode);
            requestDriveFallback();
            return;
        }
        WalkPath bestPath = result.getPaths().get(0);
        List<LatLng> segmentPoints = extractWalkPathPoints(bestPath, from, to);
        if (segmentPoints.size() < 2) {
            requestDriveFallback();
            return;
        }
        totalWalkDistance += (int) bestPath.getDistance();
        totalWalkDuration += (int) bestPath.getDuration();
        appendWalkInstructions(bestPath, currentSegmentIndex, false);
        appendSegmentPoints(segmentPoints);
        currentSegmentIndex++;
        requestNextWalkSegment();
    }

    @Override
    public void onRideRouteSearched(RideRouteResult rideRouteResult, int errorCode) {
    }
}
