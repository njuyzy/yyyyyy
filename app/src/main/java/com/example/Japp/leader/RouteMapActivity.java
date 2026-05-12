package com.example.Japp.leader;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;
import android.content.pm.PackageManager;

import androidx.appcompat.app.AppCompatActivity;

import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.MapView;
import com.amap.api.maps.MapsInitializer;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.LatLngBounds;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.PolylineOptions;
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

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RouteMapActivity extends AppCompatActivity {

    public static final String EXTRA_ROUTE_ID = "route_id";
    public static final String EXTRA_PROJECT_ID = "project_id";
    public static final String EXTRA_ROUTE_NODES_JSON = "route_nodes_json";
    public static final String EXTRA_USE_README_SAMPLE = "use_readme_sample";

    private MapView mapView;
    private AMap aMap;
    private UserService service;
    private final Gson gson = new Gson();
    private int fallbackProjectId = -1;
    private final List<Integer> routeIdCandidates = new ArrayList<>();
    private int currentRouteCandidateIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_route_map);

        if (!hasConfiguredAmapKey()) {
            Toast.makeText(this, "未配置高德Key，请在local.properties配置AMAP_API_KEY", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // AMap SDK requires privacy consent prior to map creation.
        MapsInitializer.updatePrivacyShow(this, true, true);
        MapsInitializer.updatePrivacyAgree(this, true);

        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);

        aMap = mapView.getMap();
        if (aMap != null) {
            LatLng defaultCenter = new LatLng(39.9042, 116.4074);
            aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultCenter, 10f));
        }

        service = ApiClient.getClient().create(UserService.class);
        boolean useReadmeSample = getIntent() != null
                && getIntent().getBooleanExtra(EXTRA_USE_README_SAMPLE, false);
        if (useReadmeSample) {
            Toast.makeText(this, "未启用示例路线，请从服务器加载", Toast.LENGTH_SHORT).show();
            return;
        }
        int routeId = getIntent() != null ? getIntent().getIntExtra(EXTRA_ROUTE_ID, -1) : -1;
        fallbackProjectId = getIntent() != null ? getIntent().getIntExtra(EXTRA_PROJECT_ID, -1) : -1;
        initRouteCandidates(routeId, fallbackProjectId);
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
        Type listType = new TypeToken<List<RouteNode>>() {}.getType();
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
        Type listType = new TypeToken<List<RouteNode>>() {}.getType();
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
            aMap.addPolyline(new PolylineOptions()
                    .addAll(points)
                    .color(0xFF1E88E5)
                    .width(8f));
        }

        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        for (LatLng point : points) {
            builder.include(point);
        }
        int padding = (int) (getResources().getDisplayMetrics().density * 48);
        aMap.moveCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), padding));
        return true;
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
        String normalized = location.replace(";", ",");
        String[] parts = normalized.split(",");
        if (parts.length < 2) {
            parts = normalized.trim().split("\\s+");
        }
        if (parts.length < 2) return null;
        try {
            double first = Double.parseDouble(parts[0].trim());
            double second = Double.parseDouble(parts[1].trim());
            double lng = first;
            double lat = second;
            if (Math.abs(lat) > 90 && Math.abs(lng) <= 90) {
                lat = first;
                lng = second;
            }
            if (Math.abs(lat) > 90 || Math.abs(lng) > 180) return null;
            return new LatLng(lat, lng);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean hasConfiguredAmapKey() {
        try {
            if (getPackageManager() == null) return false;
            String packageName = getPackageName();
            android.content.pm.ApplicationInfo appInfo = getPackageManager()
                    .getApplicationInfo(packageName, PackageManager.GET_META_DATA);
            if (appInfo.metaData == null) return false;
            String apiKey = appInfo.metaData.getString("com.amap.api.v2.apikey");
            return !TextUtils.isEmpty(apiKey) && apiKey.contains("${") == false;
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
