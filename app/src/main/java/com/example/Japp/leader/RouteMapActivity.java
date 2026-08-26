package com.example.Japp.leader;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.MapView;
import com.amap.api.maps.MapsInitializer;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.LatLngBounds;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.PolylineOptions;
import com.amap.api.services.core.AMapException;
import com.amap.api.services.core.LatLonPoint;
import com.amap.api.services.route.BusRouteResult;
import com.amap.api.services.route.DriveRouteResult;
import com.amap.api.services.route.RideRouteResult;
import com.amap.api.services.route.RouteSearch;
import com.amap.api.services.route.WalkPath;
import com.amap.api.services.route.WalkRouteResult;
import com.amap.api.services.route.WalkStep;
import com.example.Japp.R;
import com.example.Japp.network.ApiClient;
import com.example.Japp.network.api.UserService;
import com.example.Japp.network.models.Result;
import com.example.Japp.network.models.RouteNode;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RouteMapActivity extends AppCompatActivity implements RouteSearch.OnRouteSearchListener {

    public static final String EXTRA_ROUTE_ID = "route_id";
    public static final String EXTRA_PROJECT_ID = "project_id";
    public static final String EXTRA_ROUTE_NODES_JSON = "route_nodes_json";
    public static final String EXTRA_USE_README_SAMPLE = "use_readme_sample";

    private MapView mapView;
    private AMap aMap;
    private RelativeLayout bottomLayout;
    private TextView routeTimeDes;
    private TextView routeDetailDes;
    private UserService service;
    private final Gson gson = new Gson();
    private final List<Integer> routeIdCandidates = new ArrayList<>();
    private final List<LatLng> routePoints = new ArrayList<>();
    private final List<String> routePointLabels = new ArrayList<>();
    private final List<LatLng> plannedPolylinePoints = new ArrayList<>();
    private final ArrayList<String> walkInstructions = new ArrayList<>();
    private int currentRouteCandidateIndex = 0;
    private int currentSegmentIndex = 0;
    private int totalWalkDistance = 0;
    private int totalWalkDuration = 0;
    private boolean routeHasFailedSegment = false;

    private RouteSearch routeSearch;

    private static final String TAG = "RouteMapActivity";
    private static final Pattern COORDINATE_PATTERN = Pattern.compile("(-?\\d+(?:\\.\\d+)?)");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_route_map);

        if (!hasConfiguredAmapKey()) {
            Toast.makeText(this, "未配置高德Key，请在local.properties配置AMAP_API_KEY", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        MapsInitializer.updatePrivacyShow(this, true, true);
        MapsInitializer.updatePrivacyAgree(this, true);

        mapView = findViewById(R.id.mapView);
        bottomLayout = findViewById(R.id.bottom_layout);
        routeTimeDes = findViewById(R.id.firstline);
        routeDetailDes = findViewById(R.id.secondline);
        mapView.onCreate(savedInstanceState);

        aMap = mapView.getMap();
        if (aMap != null) {
            LatLng defaultCenter = new LatLng(39.9042, 116.4074);
            aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultCenter, 10f));
        }
        try {
            routeSearch = new RouteSearch(this);
            routeSearch.setRouteSearchListener(this);
        } catch (AMapException e) {
            routeSearch = null;
            Toast.makeText(this, "路线服务初始化失败，仅显示途经地点", Toast.LENGTH_SHORT).show();
        }

        service = ApiClient.getClient().create(UserService.class);
        boolean useReadmeSample = getIntent() != null
                && getIntent().getBooleanExtra(EXTRA_USE_README_SAMPLE, false);
        if (useReadmeSample) {
            Toast.makeText(this, "未启用示例路线，请从服务器加载", Toast.LENGTH_SHORT).show();
            return;
        }
        int routeId = getIntent() != null ? getIntent().getIntExtra(EXTRA_ROUTE_ID, -1) : -1;
        int projectId = getIntent() != null ? getIntent().getIntExtra(EXTRA_PROJECT_ID, -1) : -1;
        initRouteCandidates(routeId, projectId);
        List<RouteNode> nodesFromIntent = parseNodesFromIntent();
        if (nodesFromIntent != null && !nodesFromIntent.isEmpty()) {
            boolean rendered = renderRoute(nodesFromIntent);
            if (!rendered) {
                loadRouteByCandidates();
            }
        } else {
            loadRouteByCandidates();
        }
    }

    private List<RouteNode> parseNodesFromIntent() {
        if (getIntent() == null) return null;
        String nodesJson = getIntent().getStringExtra(EXTRA_ROUTE_NODES_JSON);
        if (TextUtils.isEmpty(nodesJson)) return null;
        Type listType = new TypeToken<List<RouteNode>>() {
        }.getType();
        try {
            return gson.fromJson(nodesJson, listType);
        } catch (Exception e) {
            return null;
        }
    }

    private void initRouteCandidates(int routeId, int projectId) {
        routeIdCandidates.clear();
        if (routeId > 0) {
            routeIdCandidates.add(routeId);
        }
        if (projectId > 0 && projectId != routeId) {
            routeIdCandidates.add(projectId);
        }
        currentRouteCandidateIndex = 0;
    }

    private void loadRouteByCandidates() {
        if (routeIdCandidates.isEmpty()) {
            Toast.makeText(this, "路线不存在，无法加载地图", Toast.LENGTH_SHORT).show();
            return;
        }
        loadRoute(routeIdCandidates.get(currentRouteCandidateIndex));
    }

    private boolean tryNextRouteCandidate() {
        if (currentRouteCandidateIndex + 1 < routeIdCandidates.size()) {
            currentRouteCandidateIndex++;
            loadRoute(routeIdCandidates.get(currentRouteCandidateIndex));
            return true;
        }
        return false;
    }

    private void loadRoute(int routeId) {
        if (routeId <= 0) {
            if (tryNextRouteCandidate()) {
                return;
            }
            Toast.makeText(this, "路线不存在，无法加载地图", Toast.LENGTH_SHORT).show();
            return;
        }
        service.getRouteNodesRaw(routeId).enqueue(new Callback<Result<JsonElement>>() {
            @Override
            public void onResponse(Call<Result<JsonElement>> call, Response<Result<JsonElement>> response) {
                if (!response.isSuccessful() || response.body() == null || response.body().getCode() != 1) {
                    if (tryNextRouteCandidate()) {
                        return;
                    }
                    Toast.makeText(RouteMapActivity.this, "路线加载失败", Toast.LENGTH_SHORT).show();
                    return;
                }
                List<RouteNode> nodes = parseRouteNodes(response.body().getData());
                if (nodes == null || nodes.isEmpty()) {
                    if (tryNextRouteCandidate()) {
                        return;
                    }
                    Toast.makeText(RouteMapActivity.this, "路线为空（routeId=" + routeId + "）", Toast.LENGTH_SHORT).show();
                    return;
                }
                renderRoute(nodes);
            }

            @Override
            public void onFailure(Call<Result<JsonElement>> call, Throwable t) {
                if (tryNextRouteCandidate()) {
                    return;
                }
                Toast.makeText(RouteMapActivity.this, "网络错误，路线加载失败", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private List<RouteNode> parseRouteNodes(JsonElement data) {
        if (data == null || data.isJsonNull()) return new ArrayList<>();
        Type listType = new TypeToken<List<RouteNode>>() {
        }.getType();
        if (data.isJsonPrimitive() && data.getAsJsonPrimitive().isString()) {
            try {
                JsonElement parsed = JsonParser.parseString(data.getAsString());
                return parseRouteNodes(parsed);
            } catch (Exception ignored) {
                return new ArrayList<>();
            }
        }
        if (data.isJsonArray()) {
            List<RouteNode> direct = gson.fromJson(data, listType);
            if (direct != null && !direct.isEmpty()) {
                return direct;
            }
            return new ArrayList<>();
        }
        if (!data.isJsonObject()) {
            return new ArrayList<>();
        }
        JsonObject obj = data.getAsJsonObject();
        JsonArray array = findArrayField(obj, "data");
        if (array == null) array = findArrayField(obj, "nodes");
        if (array == null) array = findArrayField(obj, "routeNodes");
        if (array == null) array = findArrayField(obj, "stops");
        if (array == null) array = findArrayField(obj, "items");
        if (array != null) {
            return gson.fromJson(array, listType);
        }
        for (String key : obj.keySet()) {
            JsonElement child = obj.get(key);
            List<RouteNode> fromChild = parseRouteNodes(child);
            if (fromChild != null && !fromChild.isEmpty()) {
                return fromChild;
            }
        }
        if (obj.has("location") || obj.has("name") || obj.has("poiId")) {
            List<RouteNode> single = new ArrayList<>();
            single.add(gson.fromJson(obj, RouteNode.class));
            return single;
        }
        return new ArrayList<>();
    }

    private JsonArray findArrayField(JsonObject obj, String key) {
        if (obj == null || !obj.has(key)) return null;
        JsonElement element = obj.get(key);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    private boolean renderRoute(List<RouteNode> nodes) {
        if (aMap == null) return false;
        List<RouteNode> orderedNodes = new ArrayList<>(nodes);
        Collections.sort(orderedNodes, Comparator.comparingInt(RouteNode::getVisitOrder));
        aMap.clear();
        hidePlanPanel();
        routePointLabels.clear();

        List<LatLng> points = new ArrayList<>();
        for (int i = 0; i < orderedNodes.size(); i++) {
            RouteNode node = orderedNodes.get(i);
            LatLng point = parseLocation(node.getLocation());
            if (point == null) continue;
            points.add(point);
            int order = node.getVisitOrder() > 0 ? node.getVisitOrder() : (i + 1);
            String title = !TextUtils.isEmpty(node.getName())
                    ? node.getName()
                    : (!TextUtils.isEmpty(node.getAddress()) ? node.getAddress() : "未命名地点");
            routePointLabels.add(title);
            aMap.addMarker(new MarkerOptions()
                    .position(point)
                    .title(title)
                    .snippet(buildMarkerSnippet(node, order)));
        }

        if (points.isEmpty()) {
            Toast.makeText(this, "路线缺少坐标，无法绘制", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (points.size() >= 2) {
            startRoutePlanning(points);
        }

        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        for (LatLng point : points) {
            builder.include(point);
        }
        int padding = (int) (getResources().getDisplayMetrics().density * 48);
        aMap.moveCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), padding));
        return true;
    }

    private void startRoutePlanning(List<LatLng> points) {
        if (routeSearch == null) {
            return;
        }
        routePoints.clear();
        routePoints.addAll(points);
        plannedPolylinePoints.clear();
        walkInstructions.clear();
        currentSegmentIndex = 0;
        totalWalkDistance = 0;
        totalWalkDuration = 0;
        routeHasFailedSegment = false;
        requestNextWalkSegment();
    }

    private void requestNextWalkSegment() {
        if (routePoints.size() < 2) return;
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
        routeSearch.calculateWalkRouteAsyn(query);
    }

    private void appendDuplicateSegmentNote(int segmentIndex) {
        String fromLabel = getRoutePointLabel(segmentIndex);
        String toLabel = getRoutePointLabel(segmentIndex + 1);
        walkInstructions.add("第" + (segmentIndex + 1) + "段：" + fromLabel + " -> " + toLabel);
        walkInstructions.add("起点与终点重合，已跳过该段");
    }

    private void onPlanningFinished() {
        if (!routeHasFailedSegment && plannedPolylinePoints.size() >= 2) {
            drawPlannedPolyline();
            showWalkPlanPanel();
        }
        if (routeHasFailedSegment) {
            hidePlanPanel();
            Toast.makeText(this, "部分路段规划失败，已隐藏不准确的路线，仅显示途经地点", Toast.LENGTH_SHORT).show();
        }
    }

    private void showWalkPlanPanel() {
        if (bottomLayout == null || routeTimeDes == null || routeDetailDes == null) return;
        String summary = formatWalkTime(totalWalkDuration) + "（" + formatWalkDistance(totalWalkDistance) + "）";
        routeTimeDes.setText(summary);
        routeDetailDes.setText("点击查看步行路线详情");
        routeDetailDes.setVisibility(View.VISIBLE);
        bottomLayout.setVisibility(View.VISIBLE);
        bottomLayout.setOnClickListener(v -> {
            Intent intent = new Intent(RouteMapActivity.this, WalkRouteDetailActivity.class);
            intent.putStringArrayListExtra(WalkRouteDetailActivity.EXTRA_WALK_INSTRUCTIONS, walkInstructions);
            intent.putExtra(WalkRouteDetailActivity.EXTRA_WALK_SUMMARY, summary);
            startActivity(intent);
        });
    }

    private void hidePlanPanel() {
        if (bottomLayout != null) {
            bottomLayout.setVisibility(View.GONE);
            bottomLayout.setOnClickListener(null);
        }
    }

    private void drawPlannedPolyline() {
        if (aMap == null || plannedPolylinePoints.size() < 2) return;
        aMap.addPolyline(new PolylineOptions()
                .addAll(plannedPolylinePoints)
                .color(0xFF1E88E5)
                .width(8f));
    }

    private LatLonPoint toLatLonPoint(LatLng latLng) {
        return new LatLonPoint(latLng.latitude, latLng.longitude);
    }

    private void appendSegmentPoints(List<LatLng> segmentPoints) {
        if (segmentPoints == null || segmentPoints.isEmpty()) return;
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
                if (step.getPolyline() == null) continue;
                for (LatLonPoint point : step.getPolyline()) {
                    segmentPoints.add(new LatLng(point.getLatitude(), point.getLongitude()));
                }
            }
        }
        return segmentPoints;
    }

    private void appendWalkInstructions(WalkPath path) {
        appendWalkInstructions(path, currentSegmentIndex, false);
    }

    private void appendWalkInstructions(WalkPath path, int segmentIndex, boolean fallback) {
        String fromLabel = getRoutePointLabel(segmentIndex);
        String toLabel = getRoutePointLabel(segmentIndex + 1);
        walkInstructions.add("第" + (segmentIndex + 1) + "段：" + fromLabel + " -> " + toLabel);
        if (fallback) {
            walkInstructions.add("路径规划失败，该路段暂不展示路线");
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

    private String getRoutePointLabel(int index) {
        if (index >= 0 && index < routePointLabels.size()) {
            String label = routePointLabels.get(index);
            if (!TextUtils.isEmpty(label)) {
                return label;
            }
        }
        return "第" + (index + 1) + "站";
    }

    private void appendFallbackSegment() {
        appendWalkInstructions(null, currentSegmentIndex, true);
        routeHasFailedSegment = true;
        currentSegmentIndex++;
        requestNextWalkSegment();
    }

    @Override
    public void onBusRouteSearched(BusRouteResult busRouteResult, int i) {
    }

    @Override
    public void onDriveRouteSearched(DriveRouteResult result, int errorCode) {
    }

    @Override
    public void onWalkRouteSearched(WalkRouteResult result, int errorCode) {
        if (currentSegmentIndex >= routePoints.size() - 1) return;
        LatLng from = routePoints.get(currentSegmentIndex);
        LatLng to = routePoints.get(currentSegmentIndex + 1);
        if (errorCode != AMapException.CODE_AMAP_SUCCESS
                || result == null
                || result.getPaths() == null
                || result.getPaths().isEmpty()) {
            Log.w(TAG, "Walk route failed segment=" + currentSegmentIndex
                    + ", errorCode=" + errorCode
                    + ", from=" + from.latitude + "," + from.longitude
                    + ", to=" + to.latitude + "," + to.longitude);
            appendFallbackSegment();
            return;
        }
        WalkPath bestPath = result.getPaths().get(0);
        List<LatLng> segmentPoints = extractWalkPathPoints(bestPath, from, to);
        if (segmentPoints.size() < 2) {
            appendFallbackSegment();
            return;
        }
        totalWalkDistance += (int) bestPath.getDistance();
        totalWalkDuration += (int) bestPath.getDuration();
        appendWalkInstructions(bestPath);
        appendSegmentPoints(segmentPoints);
        currentSegmentIndex++;
        requestNextWalkSegment();
    }

    @Override
    public void onRideRouteSearched(RideRouteResult rideRouteResult, int errorCode) {
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

    private LatLng parseLocation(String location) {
        if (TextUtils.isEmpty(location)) return null;
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

    private String formatWalkTime(int seconds) {
        if (seconds < 60) return "1分钟以内";
        int minutes = seconds / 60;
        if (minutes < 60) return minutes + "分钟";
        int hours = minutes / 60;
        int remainMin = minutes % 60;
        if (remainMin == 0) return hours + "小时";
        return String.format(Locale.CHINA, "%d小时%d分钟", hours, remainMin);
    }

    private String formatWalkDistance(int meters) {
        if (meters < 1000) return meters + "米";
        return String.format(Locale.CHINA, "%.1f公里", meters / 1000f);
    }

    private boolean hasConfiguredAmapKey() {
        try {
            if (getPackageManager() == null) return false;
            String packageName = getPackageName();
            android.content.pm.ApplicationInfo appInfo = getPackageManager()
                    .getApplicationInfo(packageName, PackageManager.GET_META_DATA);
            if (appInfo.metaData == null) return false;
            String apiKey = appInfo.metaData.getString("com.amap.api.v2.apikey");
            return !TextUtils.isEmpty(apiKey) && !apiKey.contains("${");
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mapView != null) mapView.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mapView != null) mapView.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mapView != null) mapView.onSaveInstanceState(outState);
    }
}
