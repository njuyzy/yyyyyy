package com.example.Japp.user.fragment.route;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.amap.api.maps.AMap;
import com.amap.api.maps.MapView;
import com.amap.api.maps.MapsInitializer;
import com.amap.api.maps.model.LatLng;
import com.example.Japp.R;
import com.example.Japp.network.models.RouteNode;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 点击对话气泡内地图后全屏展示路线 */
public class RouteMapFullscreenActivity extends AppCompatActivity {

    private static final String EXTRA_LATS = "route_lats";
    private static final String EXTRA_LNGS = "route_lngs";
    private static final String EXTRA_WAYPOINT_LATS = "waypoint_lats";
    private static final String EXTRA_WAYPOINT_LNGS = "waypoint_lngs";
    private static final String EXTRA_TITLE = "route_title";
    private static final String EXTRA_ROUTE_NODES = "route_nodes";

    private MapView mapView;
    @Nullable
    private AMap aMap;
    @Nullable
    private List<LatLng> routePoints;
    @Nullable
    private List<LatLng> waypointPoints;
    private boolean mapCreated;
    private List<RouteNode> routeNodes = Collections.emptyList();

    public static void start(@NonNull Context context, @Nullable List<LatLng> points) {
        start(context, points, null);
    }

    public static void startWithNodes(@NonNull Context context,
                                      @Nullable List<RouteNode> nodes,
                                      @Nullable String title) {
        startWithNodes(context, Collections.emptyList(), nodes, title);
    }

    public static void startWithNodes(@NonNull Context context,
                                      @Nullable List<LatLng> roadPoints,
                                      @Nullable List<RouteNode> nodes,
                                      @Nullable String title) {
        List<RouteNode> safeNodes = new ArrayList<>();
        if (nodes != null) {
            for (RouteNode node : nodes) {
                if (node != null) safeNodes.add(node);
            }
        }
        launch(context, roadPoints, RouteMapDrawHelper.extractPointsFromNodes(safeNodes),
                safeNodes, title);
    }

    public static void start(@NonNull Context context,
                             @Nullable List<LatLng> roadPoints,
                             @Nullable List<LatLng> waypoints,
                             @Nullable String title) {
        launch(context, roadPoints, waypoints, null, title);
    }

    private static void launch(@NonNull Context context,
                               @Nullable List<LatLng> roadPoints,
                               @Nullable List<LatLng> waypoints,
                               @Nullable List<RouteNode> nodes,
                               @Nullable String title) {
        List<LatLng> safePoints = roadPoints != null ? roadPoints : Collections.emptyList();
        double[] lats = new double[safePoints.size()];
        double[] lngs = new double[safePoints.size()];
        for (int i = 0; i < safePoints.size(); i++) {
            lats[i] = safePoints.get(i).latitude;
            lngs[i] = safePoints.get(i).longitude;
        }
        Intent intent = new Intent(context, RouteMapFullscreenActivity.class);
        intent.putExtra(EXTRA_LATS, lats);
        intent.putExtra(EXTRA_LNGS, lngs);
        if (nodes != null && !nodes.isEmpty()) {
            intent.putExtra(EXTRA_ROUTE_NODES, new Gson().toJson(nodes));
        }
        if (waypoints != null && !waypoints.isEmpty()) {
            double[] wLats = new double[waypoints.size()];
            double[] wLngs = new double[waypoints.size()];
            for (int i = 0; i < waypoints.size(); i++) {
                wLats[i] = waypoints.get(i).latitude;
                wLngs[i] = waypoints.get(i).longitude;
            }
            intent.putExtra(EXTRA_WAYPOINT_LATS, wLats);
            intent.putExtra(EXTRA_WAYPOINT_LNGS, wLngs);
        }
        if (!TextUtils.isEmpty(title)) {
            intent.putExtra(EXTRA_TITLE, title);
        }
        if (!(context instanceof android.app.Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }

    public static void start(@NonNull Context context,
                             @Nullable List<LatLng> points,
                             @Nullable String title) {
        start(context, points, null, title);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        MapsInitializer.updatePrivacyShow(getApplicationContext(), true, true);
        MapsInitializer.updatePrivacyAgree(getApplicationContext(), true);
        setContentView(R.layout.activity_route_map_fullscreen);

        if (!hasConfiguredAmapKey()) {
            Toast.makeText(this, "未配置高德Key，请在 local.properties 配置 AMAP_API_KEY", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> finish());
            String title = getIntent().getStringExtra(EXTRA_TITLE);
            if (!TextUtils.isEmpty(title)) {
                toolbar.setTitle(title);
            }
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
            }
        });

        routePoints = readPointsFromIntent();
        waypointPoints = readWaypointsFromIntent();
        routeNodes = readNodesFromIntent();
        mapView = findViewById(R.id.mapView);
        if (mapView == null) {
            Toast.makeText(this, "地图加载失败", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        try {
            mapView.onCreate(savedInstanceState);
            mapCreated = true;
            aMap = mapView.getMap();
        } catch (Exception e) {
            Toast.makeText(this, "地图初始化失败", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (aMap != null) {
            aMap.getUiSettings().setZoomControlsEnabled(true);
            aMap.getUiSettings().setCompassEnabled(true);
            aMap.setOnMapLoadedListener(this::drawRouteSafely);
        }
    }

    private void drawRouteSafely() {
        boolean hasRoad = routePoints != null && routePoints.size() >= 2;
        boolean hasWaypoints = waypointPoints != null && !waypointPoints.isEmpty();
        if (aMap == null || (!hasRoad && !hasWaypoints)) {
            return;
        }
        try {
            if (!routeNodes.isEmpty()) {
                RouteMapDrawHelper.drawRouteWithNodes(aMap, routePoints, routeNodes);
            } else {
                // Coordinate-only callers retain their existing marker presentation.
                RouteMapDrawHelper.drawRoute(aMap, routePoints, waypointPoints);
            }
        } catch (Exception ignored) {
            // 地图尚未就绪时忽略，等待 onMapLoaded 再次绘制
        }
    }

    private List<LatLng> readPointsFromIntent() {
        return readLatLngExtra(EXTRA_LATS, EXTRA_LNGS);
    }

    @Nullable
    private List<LatLng> readWaypointsFromIntent() {
        List<LatLng> points = readLatLngExtra(EXTRA_WAYPOINT_LATS, EXTRA_WAYPOINT_LNGS);
        return points.isEmpty() ? null : points;
    }

    private List<RouteNode> readNodesFromIntent() {
        List<RouteNode> nodes = new ArrayList<>();
        String json = getIntent().getStringExtra(EXTRA_ROUTE_NODES);
        if (TextUtils.isEmpty(json)) return nodes;
        try {
            RouteNode[] decoded = new Gson().fromJson(json, RouteNode[].class);
            if (decoded != null) {
                for (RouteNode node : decoded) {
                    if (node != null) nodes.add(node);
                }
            }
        } catch (RuntimeException ignored) {
            // Older or incomplete launch data can still use the coordinate fallback.
        }
        return nodes;
    }

    @NonNull
    private List<LatLng> readLatLngExtra(String latKey, String lngKey) {
        double[] lats = getIntent().getDoubleArrayExtra(latKey);
        double[] lngs = getIntent().getDoubleArrayExtra(lngKey);
        if (lats == null || lngs == null || lats.length == 0 || lats.length != lngs.length) {
            return new ArrayList<>();
        }
        List<LatLng> list = new ArrayList<>(lats.length);
        for (int i = 0; i < lats.length; i++) {
            list.add(new LatLng(lats[i], lngs[i]));
        }
        return list;
    }

    private boolean hasConfiguredAmapKey() {
        try {
            if (getPackageManager() == null) {
                return false;
            }
            android.content.pm.ApplicationInfo appInfo = getPackageManager()
                    .getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
            if (appInfo.metaData == null) {
                return false;
            }
            String apiKey = appInfo.metaData.getString("com.amap.api.v2.apikey");
            return !TextUtils.isEmpty(apiKey) && !apiKey.contains("${");
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapCreated && mapView != null) {
            mapView.onResume();
            if (aMap != null) {
                mapView.post(this::drawRouteSafely);
            }
        }
    }

    @Override
    protected void onPause() {
        if (mapCreated && mapView != null) {
            mapView.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mapCreated && mapView != null) {
            mapView.onSaveInstanceState(outState);
        }
    }

    @Override
    protected void onDestroy() {
        if (mapCreated && mapView != null) {
            mapView.onDestroy();
            mapView = null;
        }
        aMap = null;
        super.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mapCreated && mapView != null) {
            mapView.onLowMemory();
        }
    }
}
