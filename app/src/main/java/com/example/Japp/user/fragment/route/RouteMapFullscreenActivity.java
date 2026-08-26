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

    private MapView mapView;
    @Nullable
    private AMap aMap;
    @Nullable
    private List<LatLng> routePoints;
    @Nullable
    private List<LatLng> waypointPoints;
    private boolean mapCreated;

    public static void start(@NonNull Context context, @Nullable List<LatLng> points) {
        start(context, points, null);
    }

    public static void startWithNodes(@NonNull Context context,
                                      @Nullable List<RouteNode> nodes,
                                      @Nullable String title) {
        start(context, Collections.emptyList(),
                RouteMapDrawHelper.extractPointsFromNodes(nodes), title);
    }

    public static void start(@NonNull Context context,
                             @Nullable List<LatLng> roadPoints,
                             @Nullable List<LatLng> waypoints,
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
            RouteMapDrawHelper.drawRoute(aMap, routePoints, waypointPoints);
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
