package com.example.Japp.leader;

import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.amap.api.maps.AMap;
import com.amap.api.maps.MapView;
import com.amap.api.maps.MapsInitializer;
import com.amap.api.maps.model.LatLng;
import com.example.Japp.R;
import com.example.Japp.network.ApiClient;
import com.example.Japp.network.api.UserService;
import com.example.Japp.network.models.Account;
import com.example.Japp.network.models.Project;
import com.example.Japp.network.models.Result;
import com.example.Japp.network.models.RouteNode;
import com.example.Japp.user.fragment.route.RouteMapDrawHelper;
import com.example.Japp.user.fragment.route.RouteMapFullscreenActivity;
import com.example.Japp.user.util.ProjectUiHelper;
import com.example.Japp.user.util.SessionHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class orderDetailActivity extends AppCompatActivity {

    public static final String EXTRA_PROJECT_JSON = "project_json";

    private static final String TAG = "LeaderOrderDetail";

    private UserService service;
    private Project project;
    private TextView txtTitle;
    private TextView txtMeta;
    private TextView txtOwner;
    private TextView txtStatus;
    private TextView txtRouteDetail;
    private MaterialButton btnJoin;
    @Nullable
    private FrameLayout mapContainer;
    @Nullable
    private MapView routeMapView;
    @Nullable
    private TextView mapTapHint;
    @Nullable
    private AMap aMap;
    private final List<LatLng> routePoints = new ArrayList<>();
    private final List<LatLng> plannedRoadPoints = new ArrayList<>();
    private final List<RouteNode> cachedRouteNodes = new ArrayList<>();
    @Nullable
    private LeaderWalkRoutePlanner walkRoutePlanner;
    private boolean routePlanningStarted;
    private boolean mapCreated;

    // 用于在 onDestroy 时取消尚未返回的请求，避免回调访问已销毁的视图
    @Nullable
    private Call<?> pendingProjectCall;
    @Nullable
    private Call<?> pendingOwnerCall;
    @Nullable
    private Call<?> pendingRouteCall;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leader_order_detail);

        MapsInitializer.updatePrivacyShow(getApplicationContext(), true, true);
        MapsInitializer.updatePrivacyAgree(getApplicationContext(), true);

        service = ApiClient.getClient().create(UserService.class);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        txtTitle = findViewById(R.id.txtTitle);
        txtMeta = findViewById(R.id.txtMeta);
        txtOwner = findViewById(R.id.txtOwner);
        txtStatus = findViewById(R.id.txtStatus);
        txtRouteDetail = findViewById(R.id.txtRouteDetail);
        btnJoin = findViewById(R.id.btnJoin);
        mapContainer = findViewById(R.id.mapContainer);
        routeMapView = findViewById(R.id.routeMapView);
        mapTapHint = findViewById(R.id.mapTapHint);

        // 完全按用户端 TeamDetailActivity 的策略：未配置 AMap Key 时隐藏地图，避免原生层崩溃
        if (!hasConfiguredAmapKey()) {
            if (mapContainer != null) {
                mapContainer.setVisibility(View.GONE);
            }
        } else if (routeMapView != null) {
            try {
                routeMapView.onCreate(savedInstanceState);
                mapCreated = true;
                aMap = routeMapView.getMap();
                if (aMap != null) {
                    aMap.getUiSettings().setZoomControlsEnabled(false);
                    aMap.getUiSettings().setRotateGesturesEnabled(false);
                    aMap.getUiSettings().setTiltGesturesEnabled(false);
                    aMap.setOnMapLoadedListener(this::startRoadRoutePlanning);
                }
                walkRoutePlanner = new LeaderWalkRoutePlanner(this);
            } catch (Throwable t) {
                Log.w(TAG, "routeMapView.onCreate failed, hide map", t);
                if (mapContainer != null) {
                    mapContainer.setVisibility(View.GONE);
                }
                aMap = null;
                mapCreated = false;
            }
        }

        View.OnClickListener openFullscreen = v -> openFullscreenMap();
        if (mapContainer != null) {
            mapContainer.setOnClickListener(openFullscreen);
        }
        if (mapTapHint != null) {
            mapTapHint.setOnClickListener(openFullscreen);
        }

        String projectJson = getIntent().getStringExtra(EXTRA_PROJECT_JSON);
        if (TextUtils.isEmpty(projectJson)) {
            Toast.makeText(this, "项目数据无效", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        project = new Gson().fromJson(projectJson, Project.class);
        if (project == null) {
            Toast.makeText(this, "项目数据无效", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        bindProjectHeader();
        loadOwnerName();
        loadRouteDetail();
        setupAcceptButton();
        refreshProjectDetail();
    }

    private void refreshProjectDetail() {
        Call<Result<Project>> call = service.getProject(project.getId());
        pendingProjectCall = call;
        call.enqueue(new Callback<Result<Project>>() {
            @Override
            public void onResponse(@NonNull Call<Result<Project>> call,
                                   @NonNull Response<Result<Project>> response) {
                if (isFinishing() || isDestroyed()) return;
                if (response.code() == 401) {
                    SessionHelper.handleUnauthorized(orderDetailActivity.this);
                    return;
                }
                if (response.isSuccessful() && response.body() != null
                        && response.body().getCode() == 1) {
                    Project latest = response.body().getData();
                    if (latest != null) {
                        project = latest;
                        bindProjectHeader();
                    }
                }
                setupAcceptButton();
            }

            @Override
            public void onFailure(@NonNull Call<Result<Project>> call, @NonNull Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                setupAcceptButton();
            }
        });
    }

    private void bindProjectHeader() {
        String title = project.getTitle();
        txtTitle.setText(title == null || title.isEmpty() ? "研学拼单" : title);

        String city = ProjectUiHelper.regionAdcodeToCity(project.getRegionAdcode());
        String date = project.getDepartureDate() != null ? project.getDepartureDate() : "待定";
        txtMeta.setText((city.isEmpty() ? "未知城市" : city)
                + " · 出发 " + date
                + "\n已有人数 " + Math.max(0, project.getCurrentMembers())
                + " / 人数上限 " + Math.max(0, project.getMaxMembers()));

        txtStatus.setText(ProjectUiHelper.statusLabel(project.getStatus()));
    }

    private void loadOwnerName() {
        Call<Result<Account>> call = service.getAccount(project.getOwnerAccountId());
        pendingOwnerCall = call;
        call.enqueue(new Callback<Result<Account>>() {
            @Override
            public void onResponse(Call<Result<Account>> call, Response<Result<Account>> response) {
                if (isFinishing() || isDestroyed()) return;
                if (response.code() == 401) {
                    SessionHelper.handleUnauthorized(orderDetailActivity.this);
                    return;
                }
                if (response.isSuccessful() && response.body() != null && response.body().getCode() == 1) {
                    Account account = response.body().getData();
                    if (account != null) {
                        txtOwner.setText("发起人：" + account.getUsername());
                    }
                }
            }

            @Override
            public void onFailure(Call<Result<Account>> call, Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                txtOwner.setText("发起人：未知");
            }
        });
    }

    private void loadRouteDetail() {
        Call<Result<List<RouteNode>>> call = service.getRouteNodes(project.getRouteId());
        pendingRouteCall = call;
        call.enqueue(new Callback<Result<List<RouteNode>>>() {
            @Override
            public void onResponse(@NonNull Call<Result<List<RouteNode>>> call,
                                   @NonNull Response<Result<List<RouteNode>>> response) {
                if (isFinishing() || isDestroyed()) return;
                if (response.code() == 401) {
                    SessionHelper.handleUnauthorized(orderDetailActivity.this);
                    return;
                }
                if (!response.isSuccessful() || response.body() == null
                        || response.body().getCode() != 1) {
                    txtRouteDetail.setText("暂无路线详情");
                    return;
                }
                List<RouteNode> nodes = response.body().getData();
                if (nodes == null || nodes.isEmpty()) {
                    txtRouteDetail.setText("暂无路线详情");
                    return;
                }
                Collections.sort(nodes, Comparator.comparingInt(RouteNode::getVisitOrder));
                txtRouteDetail.setText(buildRouteText(nodes));
                bindRouteMap(nodes);
            }

            @Override
            public void onFailure(@NonNull Call<Result<List<RouteNode>>> call, @NonNull Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                txtRouteDetail.setText("路线加载失败");
            }
        });
    }

    private String buildRouteText(List<RouteNode> nodes) {
        StringBuilder sb = new StringBuilder();
        for (RouteNode node : nodes) {
            sb.append(node.getVisitOrder()).append(". ")
                    .append(node.getName() != null ? node.getName() : "未知景点");
            if (node.getRecommendedDuration() > 0) {
                sb.append("（约").append(node.getRecommendedDuration()).append("分钟）");
            }
            if (node.getNotes() != null && !node.getNotes().isEmpty()) {
                sb.append("\n   ").append(node.getNotes());
            }
            sb.append("\n\n");
        }
        return sb.toString().trim();
    }

    private void bindRouteMap(List<RouteNode> nodes) {
        cachedRouteNodes.clear();
        cachedRouteNodes.addAll(nodes);
        routePoints.clear();
        routePoints.addAll(RouteMapDrawHelper.extractPointsFromNodes(nodes));
        plannedRoadPoints.clear();
        routePlanningStarted = false;

        if (routePoints.size() < 2) {
            if (mapContainer != null) {
                mapContainer.setVisibility(View.GONE);
            }
            return;
        }

        if (mapContainer != null) {
            mapContainer.setVisibility(View.VISIBLE);
        }
        drawRouteOnMap();
        if (routeMapView != null) {
            routeMapView.post(this::startRoadRoutePlanning);
            routeMapView.postDelayed(this::startRoadRoutePlanning, 400);
        }
    }

    private void drawRouteOnMap() {
        if (aMap == null || routePoints.size() < 2) {
            return;
        }
        RouteMapDrawHelper.drawRouteWithNodes(
                aMap, plannedRoadPoints, cachedRouteNodes);
    }

    private void startRoadRoutePlanning() {
        if (aMap == null || cachedRouteNodes.size() < 2
                || walkRoutePlanner == null || routePlanningStarted) {
            return;
        }
        routePlanningStarted = true;
        walkRoutePlanner.plan(aMap, cachedRouteNodes, new LeaderWalkRoutePlanner.Callback() {
            @Override
            public void onPlanningStarted() {
                // 规划完成前只显示站点，避免短暂出现两点直连线。
                drawRouteOnMap();
            }

            @Override
            public void onPlanningFinished(@NonNull String summary,
                                           @NonNull ArrayList<String> instructions,
                                           boolean hadFailures) {
                // 使用带道路折线的重载。
            }

            @Override
            public void onPlanningFinished(@NonNull String summary,
                                           @NonNull ArrayList<String> instructions,
                                           @NonNull List<LatLng> roadPolyline,
                                           boolean hadFailures) {
                if (isFinishing() || isDestroyed()) return;
                plannedRoadPoints.clear();
                plannedRoadPoints.addAll(roadPolyline);
                drawRouteOnMap();
            }

            @Override
            public void onPlanningFailed(@NonNull String message) {
                if (isFinishing() || isDestroyed()) return;
                routePlanningStarted = false;
                drawRouteOnMap();
            }
        });
    }

    private void openFullscreenMap() {
        if (routePoints.size() < 2) {
            Toast.makeText(this, R.string.route_map_no_coords, Toast.LENGTH_SHORT).show();
            return;
        }
        RouteMapFullscreenActivity.start(this,
                new ArrayList<>(plannedRoadPoints),
                new ArrayList<>(routePoints),
                txtTitle.getText().toString());
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

    private void setupAcceptButton() {
        btnJoin.setVisibility(View.VISIBLE);
        btnJoin.setEnabled(true);
        btnJoin.setOnClickListener(null);
        btnJoin.setText("接单");
        stylePrimaryAction();

        int accountId = SessionHelper.getAccountId(this);
        Integer leaderId = project.getLeaderAccountId();
        if (ProjectUiHelper.hasAssignedLeader(leaderId)) {
            if (leaderId != null && leaderId == accountId) {
                setupLeaderAction();
            } else {
                btnJoin.setEnabled(false);
                btnJoin.setText("已有领队");
            }
            return;
        }

        String status = ProjectUiHelper.normalizeStatus(project.getStatus());
        if (ProjectUiHelper.STATUS_CONFIRMED.equals(status)) {
            btnJoin.setEnabled(false);
            btnJoin.setText("已确认");
            return;
        }
        if (ProjectUiHelper.STATUS_IN_PROGRESS.equals(status)) {
            btnJoin.setEnabled(false);
            btnJoin.setText("进行中");
            return;
        }
        if (ProjectUiHelper.STATUS_DONE.equals(status)) {
            btnJoin.setEnabled(false);
            btnJoin.setText("已完成");
            return;
        }
        if (ProjectUiHelper.STATUS_CANCELLED.equals(status)) {
            btnJoin.setEnabled(false);
            btnJoin.setText("已取消");
            return;
        }
        if (!ProjectUiHelper.isLeaderAcceptableStatus(project.getStatus())) {
            btnJoin.setEnabled(false);
            btnJoin.setText("暂不可接");
            return;
        }

        btnJoin.setOnClickListener(v -> acceptOrder());
    }

    private void setupLeaderAction() {
        String status = ProjectUiHelper.normalizeStatus(project.getStatus());
        if (ProjectUiHelper.STATUS_DONE.equals(status)) {
            btnJoin.setEnabled(false);
            btnJoin.setText("已完成");
            return;
        }
        if (ProjectUiHelper.STATUS_CANCELLED.equals(status)) {
            btnJoin.setEnabled(false);
            btnJoin.setText("已取消");
            return;
        }
        if (ProjectUiHelper.STATUS_IN_PROGRESS.equals(status)) {
            btnJoin.setEnabled(false);
            btnJoin.setText("进行中");
            return;
        }

        styleDangerAction();
        btnJoin.setEnabled(true);
        btnJoin.setText("放弃带队");
        btnJoin.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle("放弃带队")
                .setMessage("放弃后订单将重新开放给其他领队，你也会退出该行程群聊。确定继续吗？")
                .setNegativeButton("暂不放弃", null)
                .setPositiveButton("确认放弃", (dialog, which) -> abandonOrder())
                .show());
    }

    private void abandonOrder() {
        btnJoin.setEnabled(false);
        btnJoin.setText("正在放弃…");
        service.abandonProject(project.getId()).enqueue(new Callback<Result>() {
            @Override
            public void onResponse(Call<Result> call, Response<Result> response) {
                if (isFinishing() || isDestroyed()) return;
                if (response.code() == 401) {
                    SessionHelper.handleUnauthorized(orderDetailActivity.this);
                    return;
                }
                if (!response.isSuccessful() || response.body() == null
                        || response.body().getCode() != 1) {
                    setupLeaderAction();
                    String message = response.body() == null
                            ? "放弃带队失败" : response.body().getMsg();
                    Toast.makeText(orderDetailActivity.this,
                            message, Toast.LENGTH_SHORT).show();
                    return;
                }
                setResult(RESULT_OK);
                Toast.makeText(orderDetailActivity.this,
                        "已放弃带队，订单已重新开放", Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onFailure(Call<Result> call, Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                setupLeaderAction();
                Toast.makeText(orderDetailActivity.this,
                        "网络异常，操作失败", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void stylePrimaryAction() {
        btnJoin.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.accent)));
        btnJoin.setTextColor(ContextCompat.getColor(this, R.color.white));
        btnJoin.setStrokeWidth(0);
    }

    private void styleDangerAction() {
        int danger = ContextCompat.getColor(this, R.color.status_cancelled);
        btnJoin.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.white)));
        btnJoin.setTextColor(danger);
        btnJoin.setStrokeColor(ColorStateList.valueOf(danger));
        btnJoin.setStrokeWidth(Math.round(getResources().getDisplayMetrics().density));
        btnJoin.setElevation(0f);
        btnJoin.setStateListAnimator(null);
    }

    private void acceptOrder() {
        if (!SessionHelper.isLoggedIn(this)) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            SessionHelper.handleUnauthorized(this);
            return;
        }

        int leaderAccountId = SessionHelper.getAccountId(this);
        if (leaderAccountId <= 0) {
            Toast.makeText(this, "未登录，无法接单", Toast.LENGTH_SHORT).show();
            return;
        }

        btnJoin.setEnabled(false);
        service.acceptProject(project.getId())
                .enqueue(new Callback<Result>() {
                    @Override
                    public void onResponse(Call<Result> call, Response<Result> response) {
                        if (isFinishing() || isDestroyed()) return;
                        if (response.code() == 401) {
                            Toast.makeText(orderDetailActivity.this,
                                    "登录已失效，请重新登录", Toast.LENGTH_SHORT).show();
                            SessionHelper.handleUnauthorized(orderDetailActivity.this);
                            return;
                        }
                        if (response.isSuccessful() && response.body() != null
                                && response.body().getCode() == 1) {
                            project.setLeaderAccountId(leaderAccountId);
                            project.setStatus(ProjectUiHelper.STATUS_CONFIRMED);
                            bindProjectHeader();
                            setupAcceptButton();
                            setResult(RESULT_OK);
                            Toast.makeText(orderDetailActivity.this,
                                    "接单成功，已加入项目群聊", Toast.LENGTH_SHORT).show();
                        } else {
                            btnJoin.setEnabled(true);
                            String msg = response.body() != null
                                    ? response.body().getMsg() : "接单失败";
                            Toast.makeText(orderDetailActivity.this,
                                    msg, Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<Result> call, Throwable t) {
                        if (isFinishing() || isDestroyed()) return;
                        btnJoin.setEnabled(true);
                        Toast.makeText(orderDetailActivity.this,
                                "网络错误，接单失败", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapCreated && routeMapView != null) {
            try {
                routeMapView.onResume();
                startRoadRoutePlanning();
            } catch (Throwable t) {
                Log.w(TAG, "routeMapView.onResume failed", t);
            }
        }
    }

    @Override
    protected void onPause() {
        if (mapCreated && routeMapView != null) {
            try {
                routeMapView.onPause();
            } catch (Throwable t) {
                Log.w(TAG, "routeMapView.onPause failed", t);
            }
        }
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mapCreated && routeMapView != null) {
            try {
                routeMapView.onSaveInstanceState(outState);
            } catch (Throwable t) {
                Log.w(TAG, "routeMapView.onSaveInstanceState failed", t);
            }
        }
    }

    @Override
    protected void onDestroy() {
        cancelCall(pendingProjectCall);
        cancelCall(pendingOwnerCall);
        cancelCall(pendingRouteCall);
        pendingProjectCall = null;
        pendingOwnerCall = null;
        pendingRouteCall = null;

        if (walkRoutePlanner != null) {
            walkRoutePlanner.cancel();
            walkRoutePlanner = null;
        }
        if (mapCreated && routeMapView != null) {
            try {
                if (aMap != null) {
                    try {
                        aMap.stopAnimation();
                    } catch (Throwable ignored) {
                    }
                }
                // 主动从父容器移除并隐藏，防止 onDestroy 时 Surface 还持有 GL 上下文导致原生层崩溃
                try {
                    routeMapView.setVisibility(View.GONE);
                } catch (Throwable ignored) {
                }
                if (routeMapView.getParent() != null) {
                    try {
                        ((android.view.ViewGroup) routeMapView.getParent()).removeView(routeMapView);
                    } catch (Throwable ignored) {
                    }
                }
                routeMapView.onDestroy();
            } catch (Throwable t) {
                Log.w(TAG, "routeMapView.onDestroy failed", t);
            }
            routeMapView = null;
            mapCreated = false;
        }
        aMap = null;
        super.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mapCreated && routeMapView != null) {
            try {
                routeMapView.onLowMemory();
            } catch (Throwable t) {
                Log.w(TAG, "routeMapView.onLowMemory failed", t);
            }
        }
    }

    private void cancelCall(@Nullable Call<?> call) {
        if (call != null && !call.isCanceled()) {
            try {
                call.cancel();
            } catch (Throwable ignored) {
            }
        }
    }
}
