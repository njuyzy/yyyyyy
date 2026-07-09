package com.example.Japp.user.fragment.route;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
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
import com.example.Japp.leader.LeaderWalkRoutePlanner;
import com.example.Japp.leader.WalkRouteDetailActivity;
import com.example.Japp.network.models.RouteNode;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 全屏展示路线，使用高德步行路径规划绘制路线 */
public class RouteMapFullscreenActivity extends AppCompatActivity {

    private static final String EXTRA_LATS = "route_lats";
    private static final String EXTRA_LNGS = "route_lngs";
    private static final String EXTRA_TITLE = "toolbar_title";
    public static final String EXTRA_ROUTE_NODES_JSON = "route_nodes_json";

    private MapView mapView;
    @Nullable
    private AMap aMap;
    @Nullable
    private List<LatLng> routePoints;
    @Nullable
    private List<RouteNode> routeNodes;
    @Nullable
    private LeaderWalkRoutePlanner walkRoutePlanner;
    @Nullable
    private LinearLayout bottomLayout;
    @Nullable
    private TextView routeTimeDes;
    @Nullable
    private TextView routeDetailDes;
    private final ArrayList<String> walkInstructions = new ArrayList<>();
    private String walkSummary = "";
    private boolean mapCreated;
    private boolean planningStarted;

    public static void start(@NonNull Context context, @Nullable List<LatLng> points) {
        start(context, points, null);
    }

    public static void start(@NonNull Context context, @Nullable List<LatLng> points,
                             @Nullable String toolbarTitle) {
        List<LatLng> safePoints = points;
        if (safePoints == null || safePoints.isEmpty()) {
            safePoints = RouteSampleData.getMockPolyline();
        }
        double[] lats = new double[safePoints.size()];
        double[] lngs = new double[safePoints.size()];
        for (int i = 0; i < safePoints.size(); i++) {
            lats[i] = safePoints.get(i).latitude;
            lngs[i] = safePoints.get(i).longitude;
        }
        Intent intent = new Intent(context, RouteMapFullscreenActivity.class);
        intent.putExtra(EXTRA_LATS, lats);
        intent.putExtra(EXTRA_LNGS, lngs);
        if (toolbarTitle != null && !toolbarTitle.isEmpty()) {
            intent.putExtra(EXTRA_TITLE, toolbarTitle);
        }
        if (!(context instanceof android.app.Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }

    public static void startWithNodes(@NonNull Context context, @NonNull List<RouteNode> nodes,
                                      @Nullable String toolbarTitle) {
        Intent intent = new Intent(context, RouteMapFullscreenActivity.class);
        intent.putExtra(EXTRA_ROUTE_NODES_JSON, new Gson().toJson(nodes));
        if (toolbarTitle != null && !toolbarTitle.isEmpty()) {
            intent.putExtra(EXTRA_TITLE, toolbarTitle);
        }
        if (!(context instanceof android.app.Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
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
            String title = getIntent().getStringExtra(EXTRA_TITLE);
            if (title != null && !title.isEmpty()) {
                toolbar.setTitle(title);
            }
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        bottomLayout = findViewById(R.id.bottom_layout);
        routeTimeDes = findViewById(R.id.firstline);
        routeDetailDes = findViewById(R.id.secondline);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
            }
        });

        routeNodes = readNodesFromIntent();
        routePoints = readPointsFromIntent();
        if (routeNodes == null || routeNodes.isEmpty()) {
            routeNodes = buildNodesFromPoints(routePoints);
        }

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
            aMap.setOnMapLoadedListener(this::startWalkRoutePlanning);
        }
    }

    private void startWalkRoutePlanning() {
        if (aMap == null || routeNodes == null || routeNodes.isEmpty() || planningStarted) {
            return;
        }
        planningStarted = true;
        walkRoutePlanner = new LeaderWalkRoutePlanner(this);
        walkRoutePlanner.plan(aMap, routeNodes, new LeaderWalkRoutePlanner.Callback() {
            @Override
            public void onPlanningStarted() {
                runOnUiThread(() -> showPlanningPanel(getString(R.string.route_planning_in_progress), false));
            }

            @Override
            public void onPlanningFinished(@NonNull String summary,
                                           @NonNull ArrayList<String> instructions,
                                           boolean hadFailures) {
                runOnUiThread(() -> {
                    walkSummary = summary;
                    walkInstructions.clear();
                    walkInstructions.addAll(instructions);
                    showPlanningPanel(summary, !instructions.isEmpty());
                    if (hadFailures) {
                        Toast.makeText(RouteMapFullscreenActivity.this,
                                R.string.route_planning_partial_fail, Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onPlanningFailed(@NonNull String message) {
                runOnUiThread(() -> {
                    hidePlanPanel();
                    Toast.makeText(RouteMapFullscreenActivity.this, message, Toast.LENGTH_SHORT).show();
                    drawFallbackRoute();
                });
            }
        });
    }

    private void showPlanningPanel(String firstLine, boolean clickable) {
        if (bottomLayout == null || routeTimeDes == null) {
            return;
        }
        routeTimeDes.setText(firstLine);
        if (routeDetailDes != null) {
            routeDetailDes.setVisibility(clickable ? View.VISIBLE : View.GONE);
        }
        bottomLayout.setVisibility(View.VISIBLE);
        bottomLayout.setClickable(clickable);
        if (clickable) {
            bottomLayout.setOnClickListener(v -> openWalkRouteDetail());
        } else {
            bottomLayout.setOnClickListener(null);
        }
    }

    private void hidePlanPanel() {
        if (bottomLayout != null) {
            bottomLayout.setVisibility(View.GONE);
            bottomLayout.setOnClickListener(null);
        }
    }

    private void openWalkRouteDetail() {
        if (walkInstructions.isEmpty()) {
            return;
        }
        Intent intent = new Intent(this, WalkRouteDetailActivity.class);
        intent.putStringArrayListExtra(WalkRouteDetailActivity.EXTRA_WALK_INSTRUCTIONS, walkInstructions);
        intent.putExtra(WalkRouteDetailActivity.EXTRA_WALK_SUMMARY, walkSummary);
        startActivity(intent);
    }

    private void drawFallbackRoute() {
        if (aMap == null || routePoints == null || routePoints.size() < 2) {
            return;
        }
        RouteMapDrawHelper.drawRoute(aMap, routePoints);
    }

    @Nullable
    private List<RouteNode> readNodesFromIntent() {
        String nodesJson = getIntent().getStringExtra(EXTRA_ROUTE_NODES_JSON);
        if (TextUtils.isEmpty(nodesJson)) {
            return null;
        }
        Type listType = new TypeToken<List<RouteNode>>() {
        }.getType();
        try {
            return new Gson().fromJson(nodesJson, listType);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private List<LatLng> readPointsFromIntent() {
        double[] lats = getIntent().getDoubleArrayExtra(EXTRA_LATS);
        double[] lngs = getIntent().getDoubleArrayExtra(EXTRA_LNGS);
        if (lats == null || lngs == null || lats.length == 0 || lats.length != lngs.length) {
            if (routeNodes != null && !routeNodes.isEmpty()) {
                return RouteMapDrawHelper.extractPointsFromNodes(routeNodes);
            }
            return RouteSampleData.getMockPolyline();
        }
        List<LatLng> list = new ArrayList<>(lats.length);
        for (int i = 0; i < lats.length; i++) {
            list.add(new LatLng(lats[i], lngs[i]));
        }
        return list;
    }

    @NonNull
    private List<RouteNode> buildNodesFromPoints(@Nullable List<LatLng> points) {
        List<RouteNode> nodes = new ArrayList<>();
        if (points == null) {
            return nodes;
        }
        for (int i = 0; i < points.size(); i++) {
            LatLng point = points.get(i);
            RouteNode node = new RouteNode();
            node.setVisitOrder(i + 1);
            node.setLocation(String.format(Locale.US, "%.6f,%.6f", point.longitude, point.latitude));
            node.setName("站点" + (i + 1));
            nodes.add(node);
        }
        return nodes;
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
            if (aMap != null && !planningStarted) {
                mapView.post(this::startWalkRoutePlanning);
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
        if (walkRoutePlanner != null) {
            walkRoutePlanner.cancel();
            walkRoutePlanner = null;
        }
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
