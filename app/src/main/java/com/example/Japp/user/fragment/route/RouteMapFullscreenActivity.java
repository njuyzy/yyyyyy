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
import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.List;

/** 点击对话气泡内地图后全屏展示路线 */
public class RouteMapFullscreenActivity extends AppCompatActivity {

    private static final String EXTRA_LATS = "route_lats";
    private static final String EXTRA_LNGS = "route_lngs";

    private MapView mapView;
    @Nullable
    private AMap aMap;
    @Nullable
    private List<LatLng> routePoints;
    private boolean mapCreated;

    public static void start(@NonNull Context context, @Nullable List<LatLng> points) {
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
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
            }
        });

        routePoints = readPointsFromIntent();
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
        if (aMap == null || routePoints == null || routePoints.size() < 2) {
            return;
        }
        try {
            RouteMapDrawHelper.drawRoute(aMap, routePoints);
        } catch (Exception ignored) {
            // 地图尚未就绪时忽略，等待 onMapLoaded 再次绘制
        }
    }

    private List<LatLng> readPointsFromIntent() {
        double[] lats = getIntent().getDoubleArrayExtra(EXTRA_LATS);
        double[] lngs = getIntent().getDoubleArrayExtra(EXTRA_LNGS);
        if (lats == null || lngs == null || lats.length == 0 || lats.length != lngs.length) {
            return RouteSampleData.getMockPolyline();
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
