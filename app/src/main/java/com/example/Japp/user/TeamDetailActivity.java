package com.example.Japp.user;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.amap.api.maps.AMap;
import com.amap.api.maps.MapView;
import com.amap.api.maps.MapsInitializer;
import com.amap.api.maps.model.LatLng;
import com.example.Japp.R;
import com.example.Japp.leader.LeaderWalkRoutePlanner;
import com.example.Japp.network.ApiClient;
import com.example.Japp.network.api.UserService;
import com.example.Japp.network.models.Account;
import com.example.Japp.network.models.Project;
import com.example.Japp.network.models.Result;
import com.example.Japp.network.models.RouteNode;
import com.example.Japp.network.models.requests.JoinProjectRequest;
import com.example.Japp.user.fragment.route.RouteMapDrawHelper;
import com.example.Japp.user.fragment.route.RouteMapFullscreenActivity;
import com.example.Japp.user.util.ProjectUiHelper;
import com.example.Japp.user.util.SessionHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TeamDetailActivity extends AppCompatActivity {

    public static final String EXTRA_PROJECT_JSON = "project_json";

    private UserService service;
    private Project project;
    private TextView txtTitle;
    private TextView txtMeta;
    private TextView txtOwner;
    private TextView txtMyPartySize;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_team_detail);

        MapsInitializer.updatePrivacyShow(getApplicationContext(), true, true);
        MapsInitializer.updatePrivacyAgree(getApplicationContext(), true);

        service = ApiClient.getClient().create(UserService.class);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        txtTitle = findViewById(R.id.txtTitle);
        txtMeta = findViewById(R.id.txtMeta);
        txtOwner = findViewById(R.id.txtOwner);
        txtMyPartySize = findViewById(R.id.txtMyPartySize);
        txtStatus = findViewById(R.id.txtStatus);
        txtRouteDetail = findViewById(R.id.txtRouteDetail);
        btnJoin = findViewById(R.id.btnJoin);
        mapContainer = findViewById(R.id.mapContainer);
        routeMapView = findViewById(R.id.routeMapView);
        mapTapHint = findViewById(R.id.mapTapHint);

        if (!hasConfiguredAmapKey()) {
            if (mapContainer != null) {
                mapContainer.setVisibility(View.GONE);
            }
        } else if (routeMapView != null) {
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
        restoreMyPartySize();
        loadOwnerName();
        loadRouteDetail();
        setupJoinButton();
    }

    private void bindProjectHeader() {
        String title = project.getTitle();
        txtTitle.setText(title == null || title.isEmpty() ? "研学拼单" : title);

        String city = ProjectUiHelper.regionAdcodeToCity(project.getRegionAdcode());
        String date = project.getDepartureDate() != null ? project.getDepartureDate() : "待定";
        txtMeta.setText((city.isEmpty() ? "未知城市" : city)
                + " · 出发 " + date
                + " · " + project.getCurrentMembers() + "/" + project.getMaxMembers() + " 人");

        txtStatus.setText(ProjectUiHelper.statusLabel(project.getStatus()));
    }

    private void loadOwnerName() {
        service.getAccount(project.getOwnerAccountId()).enqueue(new Callback<Result<Account>>() {
            @Override
            public void onResponse(Call<Result<Account>> call, Response<Result<Account>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getCode() == 1) {
                    Account account = response.body().getData();
                    if (account != null) {
                        txtOwner.setText("发起人：" + account.getUsername());
                    }
                }
            }

            @Override
            public void onFailure(Call<Result<Account>> call, Throwable t) {
                txtOwner.setText("发起人：未知");
            }
        });
    }

    private void loadRouteDetail() {
        service.getRouteNodes(project.getRouteId()).enqueue(new Callback<Result<List<RouteNode>>>() {
            @Override
            public void onResponse(Call<Result<List<RouteNode>>> call, Response<Result<List<RouteNode>>> response) {
                if (!response.isSuccessful() || response.body() == null || response.body().getCode() != 1) {
                    txtRouteDetail.setText("暂无路线详情");
                    hideMapSection();
                    return;
                }
                List<RouteNode> nodes = response.body().getData();
                if (nodes == null || nodes.isEmpty()) {
                    txtRouteDetail.setText("暂无路线详情");
                    hideMapSection();
                    return;
                }
                Collections.sort(nodes, Comparator.comparingInt(RouteNode::getVisitOrder));
                txtRouteDetail.setText(buildRouteText(nodes));
                bindRouteMap(nodes);
            }

            @Override
            public void onFailure(Call<Result<List<RouteNode>>> call, Throwable t) {
                txtRouteDetail.setText("路线加载失败");
                hideMapSection();
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
            hideMapSection();
            return;
        }

        if (mapContainer != null) {
            mapContainer.setVisibility(View.VISIBLE);
        }
        if (routeMapView != null) {
            routeMapView.post(this::startRoadRoutePlanning);
            routeMapView.postDelayed(this::startRoadRoutePlanning, 400);
        }
    }

    private void hideMapSection() {
        if (mapContainer != null) {
            mapContainer.setVisibility(View.GONE);
        }
    }

    private void drawRouteOnMap() {
        if (aMap == null || routePoints.size() < 2) {
            return;
        }
        List<LatLng> line = plannedRoadPoints.size() >= 2
                ? plannedRoadPoints : routePoints;
        RouteMapDrawHelper.drawRoute(aMap, line, routePoints);
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
                // 保持地图可交互，规划完成后自动替换为道路折线。
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
                plannedRoadPoints.clear();
                plannedRoadPoints.addAll(roadPolyline);
                drawRouteOnMap();
            }

            @Override
            public void onPlanningFailed(@NonNull String message) {
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
        List<LatLng> line = plannedRoadPoints.size() >= 2
                ? plannedRoadPoints : routePoints;
        RouteMapFullscreenActivity.start(this,
                new ArrayList<>(line), new ArrayList<>(routePoints), txtTitle.getText().toString());
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

    private void setupJoinButton() {
        int accountId = SessionHelper.getAccountId(this);
        if (project.getOwnerAccountId() == accountId) {
            btnJoin.setVisibility(View.GONE);
            return;
        }
        if (project.getCurrentMembers() >= project.getMaxMembers()) {
            btnJoin.setEnabled(false);
            btnJoin.setText("已满员");
            return;
        }
        if (!"OPEN".equals(project.getStatus()) && !"MATCHING".equals(project.getStatus())) {
            btnJoin.setEnabled(false);
            btnJoin.setText("暂不可加入");
            return;
        }

        btnJoin.setOnClickListener(v -> showJoinPartySizeDialog());
    }

    private void showJoinPartySizeDialog() {
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_join_party_size, null);
        TextInputEditText input = content.findViewById(R.id.editPartySize);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("填写参团人数")
                .setView(content)
                .setNegativeButton("取消", null)
                .setPositiveButton("确认加入", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String raw = input.getText() != null ? input.getText().toString().trim() : "";
                    int partySize;
                    try {
                        partySize = Integer.parseInt(raw);
                    } catch (NumberFormatException e) {
                        partySize = 0;
                    }
                    if (partySize <= 0) {
                        input.setError("人数至少为 1");
                        return;
                    }
                    int remaining = project.getMaxMembers() - project.getCurrentMembers();
                    if (remaining > 0 && partySize > remaining) {
                        input.setError("当前最多还可加入 " + remaining + " 人");
                        return;
                    }
                    dialog.dismiss();
                    joinProject(partySize);
                }));
        dialog.show();
    }

    private void joinProject(int partySize) {
        if (!SessionHelper.isLoggedIn(this)) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            SessionHelper.handleUnauthorized(this);
            return;
        }

        btnJoin.setEnabled(false);
        service.joinProject(project.getId(), new JoinProjectRequest(partySize))
                .enqueue(new Callback<Result>() {
            @Override
            public void onResponse(Call<Result> call, Response<Result> response) {
                if (response.code() == 401) {
                    Toast.makeText(TeamDetailActivity.this, "登录已失效，请重新登录", Toast.LENGTH_SHORT).show();
                    SessionHelper.handleUnauthorized(TeamDetailActivity.this);
                    return;
                }
                if (response.isSuccessful() && response.body() != null && response.body().getCode() == 1) {
                    Toast.makeText(TeamDetailActivity.this,
                            "加入成功，你代表 " + partySize + " 人", Toast.LENGTH_SHORT).show();
                    btnJoin.setText("已加入");
                    project.setCurrentMembers(project.getCurrentMembers() + partySize);
                    saveMyPartySize(partySize);
                    showMyPartySize(partySize);
                    bindProjectHeader();
                    setResult(RESULT_OK);
                } else {
                    btnJoin.setEnabled(true);
                    String msg = response.body() != null ? response.body().getMsg() : "加入失败";
                    Toast.makeText(TeamDetailActivity.this, msg, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Result> call, Throwable t) {
                btnJoin.setEnabled(true);
                Toast.makeText(TeamDetailActivity.this, "网络错误，加入失败", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String partySizePreferenceKey() {
        return "project_" + project.getId() + "_account_" + SessionHelper.getAccountId(this);
    }

    private void saveMyPartySize(int partySize) {
        getSharedPreferences("project_party_sizes", MODE_PRIVATE)
                .edit().putInt(partySizePreferenceKey(), partySize).apply();
    }

    private void restoreMyPartySize() {
        int partySize = getSharedPreferences("project_party_sizes", MODE_PRIVATE)
                .getInt(partySizePreferenceKey(), 0);
        if (partySize > 0) {
            showMyPartySize(partySize);
        }
    }

    private void showMyPartySize(int partySize) {
        if (txtMyPartySize != null) {
            txtMyPartySize.setText("你代表 " + partySize + " 人参团");
            txtMyPartySize.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapCreated && routeMapView != null) {
            routeMapView.onResume();
            startRoadRoutePlanning();
        }
    }

    @Override
    protected void onPause() {
        if (mapCreated && routeMapView != null) {
            routeMapView.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mapCreated && routeMapView != null) {
            routeMapView.onSaveInstanceState(outState);
        }
    }

    @Override
    protected void onDestroy() {
        if (walkRoutePlanner != null) {
            walkRoutePlanner.cancel();
            walkRoutePlanner = null;
        }
        if (mapCreated && routeMapView != null) {
            routeMapView.onDestroy();
            routeMapView = null;
        }
        aMap = null;
        super.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mapCreated && routeMapView != null) {
            routeMapView.onLowMemory();
        }
    }
}
