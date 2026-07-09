package com.example.Japp.user;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
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
import com.example.Japp.network.ApiClient;
import com.example.Japp.network.api.UserService;
import com.example.Japp.network.models.Account;
import com.example.Japp.network.models.ChatSession;
import com.example.Japp.network.models.Project;
import com.example.Japp.network.models.Result;
import com.example.Japp.network.models.RouteNode;
import com.example.Japp.network.models.requests.CreateSessionRequest;
import com.example.Japp.user.fragment.route.RouteMapDrawHelper;
import com.example.Japp.user.fragment.route.RouteMapFullscreenActivity;
import com.example.Japp.user.util.ProjectUiHelper;
import com.example.Japp.user.util.SessionHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
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
                aMap.setOnMapLoadedListener(this::drawRouteOnMap);
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

        ProjectUiHelper.bindStatusBadge(txtStatus, project.getStatus());
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
        routePoints.clear();
        routePoints.addAll(RouteMapDrawHelper.extractPointsFromNodes(nodes));

        if (routePoints.size() < 2) {
            hideMapSection();
            return;
        }

        if (mapContainer != null) {
            mapContainer.setVisibility(View.VISIBLE);
        }
        if (routeMapView != null) {
            routeMapView.post(this::drawRouteOnMap);
            routeMapView.postDelayed(this::drawRouteOnMap, 400);
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
        RouteMapDrawHelper.drawRoute(aMap, routePoints);
    }

    private void openFullscreenMap() {
        if (routePoints.size() < 2) {
            Toast.makeText(this, R.string.route_map_no_coords, Toast.LENGTH_SHORT).show();
            return;
        }
        RouteMapFullscreenActivity.start(this, new ArrayList<>(routePoints));
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

        btnJoin.setOnClickListener(v -> joinProject());
    }

    private void joinProject() {
        if (!SessionHelper.isLoggedIn(this)) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            SessionHelper.handleUnauthorized(this);
            return;
        }

        btnJoin.setEnabled(false);
        service.joinProject(project.getId()).enqueue(new Callback<Result>() {
            @Override
            public void onResponse(Call<Result> call, Response<Result> response) {
                if (response.code() == 401) {
                    Toast.makeText(TeamDetailActivity.this, "登录已失效，请重新登录", Toast.LENGTH_SHORT).show();
                    SessionHelper.handleUnauthorized(TeamDetailActivity.this);
                    return;
                }
                if (response.isSuccessful() && response.body() != null && response.body().getCode() == 1) {
                    Toast.makeText(TeamDetailActivity.this, "加入成功", Toast.LENGTH_SHORT).show();
                    btnJoin.setText("已加入");
                    project.setCurrentMembers(project.getCurrentMembers() + 1);
                    bindProjectHeader();
                    createChatSessionIfNeeded();
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

    private void createChatSessionIfNeeded() {
        Integer leaderId = project.getLeaderAccountId();
        if (leaderId == null || leaderId <= 0) {
            return;
        }
        int userId = SessionHelper.getAccountId(this);
        if (userId <= 0) {
            return;
        }
        CreateSessionRequest request = new CreateSessionRequest(project.getId(), userId, leaderId);
        service.createChatSession(request).enqueue(new Callback<Result<ChatSession>>() {
            @Override
            public void onResponse(Call<Result<ChatSession>> call, Response<Result<ChatSession>> response) {
                // session created or reused silently
            }

            @Override
            public void onFailure(Call<Result<ChatSession>> call, Throwable t) {
                // ignore
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapCreated && routeMapView != null) {
            routeMapView.onResume();
            drawRouteOnMap();
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
