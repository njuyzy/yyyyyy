package com.example.Japp.leader;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.amap.api.maps.model.LatLng;
import com.example.Japp.R;
import com.example.Japp.data.order;
import com.example.Japp.network.ApiClient;
import com.example.Japp.network.api.UserService;
import com.example.Japp.network.models.Account;
import com.example.Japp.network.models.ChatSession;
import com.example.Japp.network.models.Project;
import com.example.Japp.network.models.Result;
import com.example.Japp.network.models.RouteNode;
import com.example.Japp.network.models.requests.AssignLeaderRequest;
import com.example.Japp.network.models.requests.CreateSessionRequest;
import com.example.Japp.user.fragment.route.RouteMapDrawHelper;
import com.example.Japp.user.fragment.route.RouteMapFullscreenActivity;
import com.example.Japp.user.util.ProjectUiHelper;
import com.example.Japp.user.util.SessionHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
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

    private UserService service;
    private Project project;
    private TextView txtTitle;
    private TextView txtTime;
    private TextView txtMeta;
    private TextView txtOwner;
    private TextView txtStatus;
    private TextView txtRouteDetail;
    private TextView txtMembers;
    private MaterialButton btnJoin;
    private LinearLayout routeDetailRow;
    @Nullable
    private FrameLayout mapContainer;
    @Nullable
    private TextView mapTapHint;
    @Nullable
    private LeaderWalkRoutePlanner walkRoutePlanner;
    private List<RouteNode> cachedRouteNodes = new ArrayList<>();
    private final ArrayList<String> walkInstructions = new ArrayList<>();
    private final List<LatLng> plannedRoadPoints = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leader_order_detail);

        service = ApiClient.getClient().create(UserService.class);
        walkRoutePlanner = new LeaderWalkRoutePlanner(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        txtTitle = findViewById(R.id.txtTitle);
        txtTime = findViewById(R.id.txtTime);
        txtMeta = findViewById(R.id.txtMeta);
        txtOwner = findViewById(R.id.txtOwner);
        txtStatus = findViewById(R.id.txtStatus);
        txtRouteDetail = findViewById(R.id.txtRouteDetail);
        txtMembers = findViewById(R.id.txtMembers);
        btnJoin = findViewById(R.id.btnJoin);
        routeDetailRow = findViewById(R.id.routeDetailRow);
        mapContainer = findViewById(R.id.mapContainer);
        mapTapHint = findViewById(R.id.mapTapHint);

        if (routeDetailRow != null) {
            routeDetailRow.setOnClickListener(v -> openFullscreenRouteMap());
        }

        View.OnClickListener openFullscreen = v -> openFullscreenRouteMap();
        if (mapContainer != null) {
            mapContainer.setOnClickListener(openFullscreen);
        }
        if (mapTapHint != null) {
            mapTapHint.setOnClickListener(openFullscreen);
        }

        project = parseProjectFromIntent();
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
        service.getProject(project.getId()).enqueue(new Callback<Result<Project>>() {
            @Override
            public void onResponse(@NonNull Call<Result<Project>> call, @NonNull Response<Result<Project>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getCode() == 1) {
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
                setupAcceptButton();
            }
        });
    }

    @Nullable
    private Project parseProjectFromIntent() {
        String projectJson = getIntent().getStringExtra(EXTRA_PROJECT_JSON);
        if (!TextUtils.isEmpty(projectJson)) {
            return new Gson().fromJson(projectJson, Project.class);
        }
        String orderJson = getIntent().getStringExtra("order_json");
        if (!TextUtils.isEmpty(orderJson)) {
            order o = new Gson().fromJson(orderJson, order.class);
            if (o != null) {
                return orderToProject(o);
            }
        }
        return null;
    }

    private Project orderToProject(order o) {
        Project p = new Project();
        p.setId(o.getProjectId());
        p.setRouteId(o.getRouteId());
        p.setTitle(o.getTitle());
        p.setDepartureDate(o.getDepartureDate());
        p.setCreatedAt(o.getCreatedAt());
        p.setTag(o.getTag());
        p.setMaxMembers(o.getMaxMembers());
        p.setCurrentMembers(o.getCurrentMembers());
        return p;
    }

    private void bindProjectHeader() {
        String title = project.getTitle();
        txtTitle.setText(title == null || title.isEmpty() ? "研学拼单" : title);

        String city = ProjectUiHelper.regionAdcodeToCity(project.getRegionAdcode());
        txtMeta.setText(city.isEmpty() ? "未知城市" : city);

        if (txtMembers != null) {
            txtMembers.setText(project.getCurrentMembers() + "/" + project.getMaxMembers() + " 人");
        }

        if (txtTime != null) {
            String departureTime = project.getDepartureTime();
            String departureDate = project.getDepartureDate();
            String timeText;
            if (!TextUtils.isEmpty(departureTime)) {
                timeText = (TextUtils.isEmpty(departureDate) ? "当日" : departureDate)
                        + "  " + departureTime;
            } else if (!TextUtils.isEmpty(departureDate)) {
                timeText = departureDate;
            } else {
                timeText = "时间待定";
            }
            txtTime.setText(timeText);
        }

        ProjectUiHelper.bindStatusBadge(txtStatus, project.getStatus());
    }

    private void loadOwnerName() {
        service.getAccount(project.getOwnerAccountId()).enqueue(new Callback<Result<Account>>() {
            @Override
            public void onResponse(Call<Result<Account>> call, Response<Result<Account>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getCode() == 1) {
                    Account account = response.body().getData();
                    if (account != null) {
                        txtOwner.setText(account.getUsername());
                    }
                }
            }

            @Override
            public void onFailure(Call<Result<Account>> call, Throwable t) {
                txtOwner.setText("未知");
            }
        });
    }

    private void loadRouteDetail() {
        service.getRouteNodes(project.getRouteId()).enqueue(new Callback<Result<List<RouteNode>>>() {
            @Override
            public void onResponse(Call<Result<List<RouteNode>>> call, Response<Result<List<RouteNode>>> response) {
                if (!response.isSuccessful() || response.body() == null || response.body().getCode() != 1) {
                    txtRouteDetail.setText("暂无路线详情");
                    return;
                }
                List<RouteNode> nodes = response.body().getData();
                if (nodes == null || nodes.isEmpty()) {
                    txtRouteDetail.setText("暂无路线详情");
                    return;
                }
                Collections.sort(nodes, Comparator.comparingInt(RouteNode::getVisitOrder));
                cachedRouteNodes = nodes;
                txtRouteDetail.setText(buildRouteText(nodes));
                startRoutePlanning(nodes);
            }

            @Override
            public void onFailure(Call<Result<List<RouteNode>>> call, Throwable t) {
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

    private void startRoutePlanning(List<RouteNode> nodes) {
        if (mapContainer == null || walkRoutePlanner == null) {
            return;
        }
        if (!hasConfiguredAmapKey()) {
            return;
        }

        walkRoutePlanner.planSummary(nodes, new LeaderWalkRoutePlanner.Callback() {
            @Override
            public void onPlanningStarted() {
                // 步行指引 UI 已移除，仅在后台规划路径
            }

            @Override
            public void onPlanningFinished(@NonNull String summary,
                                           @NonNull ArrayList<String> instructions,
                                           boolean hadFailures) {
                handlePlanFinished(summary, instructions, new ArrayList<>(), hadFailures);
            }

            @Override
            public void onPlanningFinished(@NonNull String summary,
                                           @NonNull ArrayList<String> instructions,
                                           @NonNull List<LatLng> roadPolyline,
                                           boolean hadFailures) {
                handlePlanFinished(summary, instructions, roadPolyline, hadFailures);
            }

            @Override
            public void onPlanningFailed(@NonNull String message) {
                runOnUiThread(() -> Toast.makeText(orderDetailActivity.this, message, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void handlePlanFinished(@NonNull String summary,
                                    @NonNull ArrayList<String> instructions,
                                    @NonNull List<LatLng> roadPolyline,
                                    boolean hadFailures) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            walkInstructions.clear();
            walkInstructions.addAll(instructions);
            plannedRoadPoints.clear();
            plannedRoadPoints.addAll(roadPolyline);
            if (hadFailures) {
                Toast.makeText(orderDetailActivity.this,
                        R.string.route_planning_partial_fail, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void openFullscreenRouteMap() {
        if (cachedRouteNodes.isEmpty()) {
            Toast.makeText(this, R.string.route_map_no_coords, Toast.LENGTH_SHORT).show();
            return;
        }
        String title = getString(R.string.leader_route_planning_title);
        List<LatLng> waypoints = RouteMapDrawHelper.extractPointsFromNodes(cachedRouteNodes);
        // 优先使用高德规划得到的真实道路折线；尚未规划完成时回退为按站点连线
        if (plannedRoadPoints.size() >= 2) {
            RouteMapFullscreenActivity.start(this,
                    new ArrayList<>(plannedRoadPoints), waypoints, title);
        } else {
            RouteMapFullscreenActivity.startWithNodes(this, cachedRouteNodes, title);
        }
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
        View bottomBar = findViewById(R.id.bottomBar);
        if (bottomBar != null) {
            bottomBar.setVisibility(View.VISIBLE);
        }
        btnJoin.setVisibility(View.VISIBLE);
        btnJoin.setEnabled(true);
        btnJoin.setOnClickListener(null);
        btnJoin.setText("接单");

        int accountId = SessionHelper.getAccountId(this);
        Integer leaderId = project.getLeaderAccountId();
        if (ProjectUiHelper.hasAssignedLeader(leaderId)) {
            if (leaderId == accountId) {
                btnJoin.setEnabled(false);
                btnJoin.setText("已接单");
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
        service.assignLeader(project.getId(), new AssignLeaderRequest(leaderAccountId))
                .enqueue(new Callback<Result>() {
                    @Override
                    public void onResponse(Call<Result> call, Response<Result> response) {
                        if (response.code() == 401) {
                            Toast.makeText(orderDetailActivity.this, "登录已失效，请重新登录", Toast.LENGTH_SHORT).show();
                            SessionHelper.handleUnauthorized(orderDetailActivity.this);
                            return;
                        }
                        if (response.isSuccessful() && response.body() != null && response.body().getCode() == 1) {
                            btnJoin.setText("已接单");
                            project.setLeaderAccountId(leaderAccountId);
                            bindProjectHeader();
                            setResult(RESULT_OK);
                            createPublisherChatSession(leaderAccountId);
                        } else {
                            btnJoin.setEnabled(true);
                            String msg = response.body() != null ? response.body().getMsg() : "接单失败";
                            Toast.makeText(orderDetailActivity.this, msg, Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<Result> call, Throwable t) {
                        btnJoin.setEnabled(true);
                        Toast.makeText(orderDetailActivity.this, "网络错误，接单失败", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void createPublisherChatSession(int leaderAccountId) {
        int publisherAccountId = project.getOwnerAccountId();
        if (publisherAccountId <= 0) {
            Toast.makeText(this, "接单成功，但发布者信息无效，未能建立聊天", Toast.LENGTH_LONG).show();
            return;
        }

        CreateSessionRequest request = new CreateSessionRequest(
                project.getId(), publisherAccountId, leaderAccountId);
        service.createChatSession(request).enqueue(new Callback<Result<ChatSession>>() {
            @Override
            public void onResponse(Call<Result<ChatSession>> call,
                                   Response<Result<ChatSession>> response) {
                if (response.code() == 401) {
                    Toast.makeText(orderDetailActivity.this,
                            "接单成功，但登录已失效，未能建立聊天", Toast.LENGTH_LONG).show();
                    SessionHelper.handleUnauthorized(orderDetailActivity.this);
                    return;
                }
                if (response.isSuccessful() && response.body() != null
                        && response.body().getCode() == 1 && response.body().getData() != null) {
                    Toast.makeText(orderDetailActivity.this,
                            "接单成功，已建立与发布者的聊天", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(orderDetailActivity.this,
                            "接单成功，但聊天建立失败，请稍后重试", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<Result<ChatSession>> call, Throwable t) {
                Toast.makeText(orderDetailActivity.this,
                        "接单成功，但网络异常，聊天建立失败", Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (walkRoutePlanner != null) {
            walkRoutePlanner.cancel();
            walkRoutePlanner = null;
        }
        super.onDestroy();
    }
}
