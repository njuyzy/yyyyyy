package com.example.Japp.user;

import com.example.Japp.Chat.util.ChatHistoryStore;

import android.content.res.ColorStateList;
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
import androidx.core.content.ContextCompat;

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
import com.example.Japp.network.models.ProjectMember;
import com.example.Japp.network.models.Result;
import com.example.Japp.network.models.RouteNode;
import com.example.Japp.network.models.requests.JoinProjectRequest;
import com.example.Japp.user.fragment.route.RouteMapDrawHelper;
import com.example.Japp.user.fragment.route.RouteMapFullscreenActivity;
import com.example.Japp.user.util.ProjectUiHelper;
import com.example.Japp.user.util.UserOrderPresentation;
import com.example.Japp.user.util.UserOrderDetailContent;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.core.widget.NestedScrollView;
import com.amap.api.maps.CameraUpdateFactory;
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
    private UserOrderDetailContent detailContent;
    private SwipeRefreshLayout refreshLayout;
    private boolean projectFresh;
    private boolean actionInFlight;
    private final List<Call<?>> detailCalls = new ArrayList<>();
    private int detailGeneration;

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
        detailContent = new UserOrderDetailContent(this);
        refreshLayout = findViewById(R.id.userOrderRefresh);
        refreshLayout.setColorSchemeColors(0xFF2563EB);
        refreshLayout.setOnRefreshListener(this::refreshProject);

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
                aMap.getUiSettings().setScrollGesturesEnabled(false);
                aMap.getUiSettings().setZoomGesturesEnabled(false);
                aMap.setOnMapClickListener(point -> openFullscreenMap());
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

        try {
            project = new Gson().fromJson(projectJson, Project.class);
        } catch (RuntimeException invalidData) {
            project = null;
        }
        if (project == null) {
            Toast.makeText(this, "项目数据无效", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        bindProjectHeader();
        restoreMyPartySize();
        setupJoinButton();
        refreshProject();
    }

    private void refreshProject() {
        if (actionInFlight) {
            refreshLayout.setRefreshing(false);
            return;
        }
        final int generation = ++detailGeneration;
        if (walkRoutePlanner != null) walkRoutePlanner.cancel();
        for (Call<?> call : detailCalls) call.cancel();
        detailCalls.clear();
        projectFresh = false;
        setupJoinButton();
        refreshLayout.setRefreshing(true);
        Call<Result<Project>> request = service.getProject(project.getId());
        detailCalls.add(request);
        request.enqueue(new Callback<Result<Project>>() {
            @Override public void onResponse(Call<Result<Project>> call, Response<Result<Project>> response) {
                if (!isCurrent(generation)) return;
                refreshLayout.setRefreshing(false);
                if (response.code() == 401) {
                    SessionHelper.handleUnauthorized(TeamDetailActivity.this);
                    return;
                }
                if (response.isSuccessful() && response.body() != null
                        && response.body().getCode() == 1 && response.body().getData() != null) {
                    project = response.body().getData();
                    projectFresh = true;
                    cachedRouteNodes.clear();
                    routePoints.clear();
                    plannedRoadPoints.clear();
                    routePlanningStarted = false;
                    ((android.widget.LinearLayout) findViewById(R.id.userOrderStops)).removeAllViews();
                    hideMapSection();
                    bindProjectHeader();
                    restoreMyPartySize();
                    loadMyPartySize();
                    findViewById(R.id.userOrderNotice).setVisibility(View.GONE);
                    setResult(RESULT_OK);
                } else showRefreshFailure();
                setupJoinButton();
                loadOwnerName();
                loadRouteDetail();
            }
            @Override public void onFailure(Call<Result<Project>> call, Throwable t) {
                if (!isCurrent(generation) || call.isCanceled()) return;
                refreshLayout.setRefreshing(false);
                showRefreshFailure();
                setupJoinButton();
                loadRouteDetail();
            }
        });
    }

    private boolean isCurrent(int generation) {
        return !isFinishing() && !isDestroyed() && generation == detailGeneration;
    }

    private void showRefreshFailure() {
        TextView notice = findViewById(R.id.userOrderNotice);
        notice.setText("暂时无法更新行程，当前展示上次的信息。点此重试或下拉刷新。");
        notice.setVisibility(View.VISIBLE);
        notice.setOnClickListener(v -> refreshProject());
    }

    private void bindProjectHeader() {
        String title = project.getTitle();
        txtTitle.setText(title == null || title.isEmpty() ? "研学拼单" : title);

        String city = UserOrderPresentation.city(ProjectUiHelper.regionAdcodeToCity(project.getRegionAdcode()));
        String tag = UserOrderPresentation.clean(project.getTag());
        txtMeta.setText(city.isEmpty() ? tag : city + (tag.isEmpty() ? "" : " · " + tag));
        txtMeta.setVisibility(txtMeta.length() == 0 ? View.GONE : View.VISIBLE);
        detailContent.bind(project);
        if (!cachedRouteNodes.isEmpty()) detailContent.bindStops(cachedRouteNodes, this::focusRouteNode);
    }

    private void loadOwnerName() {
        loadPersonName(project.getOwnerAccountId(), txtOwner, "发布者");
        if (ProjectUiHelper.hasAssignedLeader(project.getLeaderAccountId())) {
            loadPersonName(project.getLeaderAccountId(), findViewById(R.id.userOrderLeader), "领队");
        }
    }

    private void loadPersonName(int accountId, TextView view, String role) {
        final int generation = detailGeneration;
        Call<Result<Account>> request = service.getAccount(accountId);
        detailCalls.add(request);
        request.enqueue(new Callback<Result<Account>>() {
            @Override
            public void onResponse(Call<Result<Account>> call, Response<Result<Account>> response) {
                if (!isCurrent(generation)) return;
                if (response.isSuccessful() && response.body() != null && response.body().getCode() == 1) {
                    Account account = response.body().getData();
                    if (account != null && !UserOrderPresentation.clean(account.getUsername()).isEmpty()) {
                        view.setText(role + "  ·  " + account.getUsername());
                    }
                }
            }

            @Override
            public void onFailure(Call<Result<Account>> call, Throwable t) {
                // Keep the supplied display name when a separate profile cannot be loaded.
            }
        });
    }

    private void loadMyPartySize() {
        if (!"PARTICIPANT".equalsIgnoreCase(project.getViewerRole())) return;
        final int generation = detailGeneration;
        final int accountId = SessionHelper.getAccountId(this);
        Call<Result<List<ProjectMember>>> request = service.getProjectMembers(project.getId());
        detailCalls.add(request);
        request.enqueue(new Callback<Result<List<ProjectMember>>>() {
            @Override public void onResponse(Call<Result<List<ProjectMember>>> call,
                                             Response<Result<List<ProjectMember>>> response) {
                if (!isCurrent(generation) || !response.isSuccessful() || response.body() == null
                        || response.body().getCode() != 1 || response.body().getData() == null) return;
                for (ProjectMember member : response.body().getData()) {
                    if (member != null && member.getAccountId() == accountId && member.getRepresentedCount() > 0) {
                        saveMyPartySize(member.getRepresentedCount());
                        showMyPartySize(member.getRepresentedCount());
                        return;
                    }
                }
            }
            @Override public void onFailure(Call<Result<List<ProjectMember>>> call, Throwable t) {
                // Keep the last confirmed party size while the supplementary request is unavailable.
            }
        });
    }

    private void loadRouteDetail() {
        final int generation = detailGeneration;
        txtRouteDetail.setText("正在加载路线…");
        txtRouteDetail.setTextColor(0xFF637086);
        txtRouteDetail.setOnClickListener(null);
        Call<Result<List<RouteNode>>> request = service.getRouteNodes(project.getRouteId());
        detailCalls.add(request);
        request.enqueue(new Callback<Result<List<RouteNode>>>() {
            @Override
            public void onResponse(Call<Result<List<RouteNode>>> call, Response<Result<List<RouteNode>>> response) {
                if (!isCurrent(generation)) return;
                if (!response.isSuccessful() || response.body() == null || response.body().getCode() != 1) {
                    showRouteFailure();
                    return;
                }
                List<RouteNode> nodes = response.body().getData() == null
                        ? new ArrayList<>() : new ArrayList<>(response.body().getData());
                nodes.removeAll(Collections.singleton(null));
                if (nodes.isEmpty()) {
                    showRouteFailure();
                    return;
                }
                Collections.sort(nodes, Comparator.comparingInt(RouteNode::getVisitOrder));
                int duration = ProjectUiHelper.sumDurationMinutes(nodes);
                txtRouteDetail.setText(nodes.size() + " 处地点"
                        + (duration > 0 ? " · 建议游览 " + ProjectUiHelper.formatDuration(duration) : "")
                        + "\n点击站点展开地址、营业时间与游览提示");
                detailContent.bindStops(nodes, TeamDetailActivity.this::focusRouteNode);
                String routeCity = UserOrderPresentation.city(nodes.get(0).getCityname());
                if (!routeCity.isEmpty()) {
                    String tag = UserOrderPresentation.clean(project.getTag());
                    txtMeta.setText(routeCity + (tag.isEmpty() ? "" : " · " + tag));
                    txtMeta.setVisibility(View.VISIBLE);
                }
                bindRouteMap(nodes);
            }

            @Override
            public void onFailure(Call<Result<List<RouteNode>>> call, Throwable t) {
                if (!isCurrent(generation) || call.isCanceled()) return;
                showRouteFailure();
            }
        });
    }

    private void showRouteFailure() {
        txtRouteDetail.setText("暂未获取到路线详情，点击重新加载");
        txtRouteDetail.setTextColor(0xFF2563EB);
        txtRouteDetail.setOnClickListener(v -> loadRouteDetail());
        detailContent.routeUnavailable();
        hideMapSection();
    }

    private void focusRouteNode(RouteNode node) {
        List<LatLng> points = RouteMapDrawHelper.extractPointsFromNodes(Collections.singletonList(node));
        if (aMap == null || points.isEmpty()) {
            Toast.makeText(this, "该地点暂时无法在地图中展示，可复制地址查询", Toast.LENGTH_SHORT).show();
            return;
        }
        aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(points.get(0), 16f));
        NestedScrollView scroll = findViewById(R.id.userOrderScroll);
        android.graphics.Rect bounds = new android.graphics.Rect();
        mapContainer.getDrawingRect(bounds);
        scroll.offsetDescendantRectToMyCoords(mapContainer, bounds);
        scroll.smoothScrollTo(0, Math.max(0, bounds.top - 12));
    }

    private void bindRouteMap(List<RouteNode> nodes) {
        cachedRouteNodes.clear();
        cachedRouteNodes.addAll(nodes);
        routePoints.clear();
        routePoints.addAll(RouteMapDrawHelper.extractPointsFromNodes(nodes));
        plannedRoadPoints.clear();
        routePlanningStarted = false;

        if (routePoints.isEmpty() || aMap == null) {
            hideMapSection();
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

    private void hideMapSection() {
        if (mapContainer != null) {
            mapContainer.setVisibility(View.GONE);
        }
    }

    private void drawRouteOnMap() {
        if (aMap == null || routePoints.isEmpty()) {
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
        final int generation = detailGeneration;
        walkRoutePlanner.plan(aMap, cachedRouteNodes, new LeaderWalkRoutePlanner.Callback() {
            @Override
            public void onPlanningStarted() {
                if (!isCurrent(generation)) return;
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
                if (!isCurrent(generation)) return;
                plannedRoadPoints.clear();
                plannedRoadPoints.addAll(roadPolyline);
                drawRouteOnMap();
            }

            @Override
            public void onPlanningFailed(@NonNull String message) {
                if (!isCurrent(generation)) return;
                routePlanningStarted = false;
                drawRouteOnMap();
            }
        });
    }

    private void openFullscreenMap() {
        if (routePoints.isEmpty()) {
            Toast.makeText(this, R.string.route_map_no_coords, Toast.LENGTH_SHORT).show();
            return;
        }
        RouteMapFullscreenActivity.startWithNodes(this,
                new ArrayList<>(plannedRoadPoints),
                new ArrayList<>(cachedRouteNodes),
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

    private void setupJoinButton() {
        int accountId = SessionHelper.getAccountId(this);
        btnJoin.setVisibility(View.VISIBLE);
        btnJoin.setEnabled(true);
        btnJoin.setOnClickListener(null);

        stylePrimaryAction();
        if (!projectFresh) {
            btnJoin.setEnabled(false);
            btnJoin.setText("等待更新行程");
            return;
        }
        String phase = UserOrderPresentation.phase(project.getStatus());
        if (phase.isEmpty() || "DONE".equals(phase) || "CANCELLED".equals(phase)) {
            btnJoin.setEnabled(false);
            btnJoin.setText("DONE".equals(phase) ? "行程已完成"
                    : "CANCELLED".equals(phase) ? "行程已取消" : "暂不可操作");
            return;
        }

        if (project.getOwnerAccountId() == accountId) {
            setupPublisherAction();
            return;
        }
        if ("PARTICIPANT".equalsIgnoreCase(project.getViewerRole())) {
            setupParticipantAction();
            return;
        }
        Integer assignedLeaderId = project.getLeaderAccountId();
        if ("LEADER".equalsIgnoreCase(project.getViewerRole())
                || (assignedLeaderId != null && assignedLeaderId == accountId)) {
            setupLeaderAction();
            return;
        }
        stylePrimaryAction();
        btnJoin.setText("加入拼单");
        if (project.getMaxMembers() > 0
                && project.getCurrentMembers() >= project.getMaxMembers()) {
            btnJoin.setEnabled(false);
            btnJoin.setText("已满员");
            return;
        }
        if (!project.isCanJoin() && !"OPEN".equals(phase)) {
            btnJoin.setEnabled(false);
            btnJoin.setText("暂不可加入");
            return;
        }

        btnJoin.setOnClickListener(v -> showJoinPartySizeDialog());
    }

    private void setupPublisherAction() {
        actionInFlight = false;
        String status = ProjectUiHelper.normalizeStatus(project.getStatus());
        btnJoin.setEnabled(true);
        styleDangerAction();
        if (ProjectUiHelper.STATUS_DONE.equals(status)) {
            btnJoin.setEnabled(false);
            btnJoin.setText("行程已完成");
            return;
        }
        if (ProjectUiHelper.STATUS_CANCELLED.equals(status)) {
            btnJoin.setEnabled(false);
            btnJoin.setText("行程已取消");
            return;
        }
        if (ProjectUiHelper.STATUS_IN_PROGRESS.equals(status)) {
            btnJoin.setEnabled(false);
            btnJoin.setText("行程进行中");
            return;
        }
        btnJoin.setText("取消行程");
        btnJoin.setOnClickListener(v -> confirmCancelTrip());
    }

    private void setupParticipantAction() {
        actionInFlight = false;
        btnJoin.setEnabled(true);
        styleDangerAction();
        btnJoin.setText("退出行程");
        btnJoin.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle("退出行程")
                .setMessage("退出后将释放你所代表的名额；历史聊天仍会保留，但不能继续发消息。确定继续吗？")
                .setNegativeButton("暂不退出", null)
                .setPositiveButton("确认退出", (dialog, which) -> quitTrip())
                .show());
    }

    private void setupLeaderAction() {
        actionInFlight = false;
        btnJoin.setEnabled(true);
        styleDangerAction();
        btnJoin.setText("放弃带队");
        btnJoin.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle("放弃带队")
                .setMessage("放弃后订单将重新开放给其他领队；历史聊天仍会保留，但不能继续发消息。确定继续吗？")
                .setNegativeButton("暂不放弃", null)
                .setPositiveButton("确认放弃", (dialog, which) -> abandonTrip())
                .show());
    }

    private void quitTrip() {
        actionInFlight = true;
        btnJoin.setEnabled(false);
        btnJoin.setText("正在退出…");
        service.quitProject(project.getId()).enqueue(new Callback<Result>() {
            @Override
            public void onResponse(Call<Result> call, Response<Result> response) {
                if (response.code() == 401) {
                    SessionHelper.handleUnauthorized(TeamDetailActivity.this);
                    return;
                }
                if (!response.isSuccessful() || response.body() == null
                        || response.body().getCode() != 1) {
                    setupParticipantAction();
                    String message = "暂时无法退出，请刷新确认行程状态后重试";
                    Toast.makeText(TeamDetailActivity.this,
                            message, Toast.LENGTH_SHORT).show();
                    return;
                }
                ChatHistoryStore.markProjectReadOnly(TeamDetailActivity.this,
                        SessionHelper.getAccountId(TeamDetailActivity.this), project.getId(),
                        "你已退出行程，聊天记录仅供查看");
                setResult(RESULT_OK);
                Toast.makeText(TeamDetailActivity.this,
                        "已退出行程，历史聊天已转为只读", Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onFailure(Call<Result> call, Throwable t) {
                setupParticipantAction();
                Toast.makeText(TeamDetailActivity.this,
                        "网络异常，退出失败", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void abandonTrip() {
        actionInFlight = true;
        btnJoin.setEnabled(false);
        btnJoin.setText("正在放弃…");
        service.abandonProject(project.getId()).enqueue(new Callback<Result>() {
            @Override
            public void onResponse(Call<Result> call, Response<Result> response) {
                if (response.code() == 401) {
                    SessionHelper.handleUnauthorized(TeamDetailActivity.this);
                    return;
                }
                if (!response.isSuccessful() || response.body() == null
                        || response.body().getCode() != 1) {
                    setupLeaderAction();
                    String message = "暂时无法放弃带队，请刷新确认行程状态后重试";
                    Toast.makeText(TeamDetailActivity.this,
                            message, Toast.LENGTH_SHORT).show();
                    return;
                }
                ChatHistoryStore.markProjectReadOnly(TeamDetailActivity.this,
                        SessionHelper.getAccountId(TeamDetailActivity.this), project.getId(),
                        "你已放弃带队，聊天记录仅供查看");
                setResult(RESULT_OK);
                Toast.makeText(TeamDetailActivity.this,
                        "已放弃带队，订单已重新开放", Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onFailure(Call<Result> call, Throwable t) {
                setupLeaderAction();
                Toast.makeText(TeamDetailActivity.this,
                        "网络异常，操作失败", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void confirmCancelTrip() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("取消行程")
                .setMessage("取消后行程将结束，群聊记录会保留，但所有成员都不能继续发消息。确定继续吗？")
                .setNegativeButton("暂不取消", null)
                .setPositiveButton("确认取消", (dialog, which) -> cancelTrip())
                .show();
    }

    private void cancelTrip() {
        actionInFlight = true;
        btnJoin.setEnabled(false);
        btnJoin.setText("正在取消…");
        service.updateProjectStatus(project.getId(), ProjectUiHelper.STATUS_CANCELLED)
                .enqueue(new Callback<Result>() {
                    @Override
                    public void onResponse(Call<Result> call, Response<Result> response) {
                        if (response.code() == 401) {
                            SessionHelper.handleUnauthorized(TeamDetailActivity.this);
                            return;
                        }
                        if (!response.isSuccessful() || response.body() == null
                                || response.body().getCode() != 1) {
                            setupPublisherAction();
                            String message = "暂时无法取消，请刷新确认行程状态后重试";
                            Toast.makeText(TeamDetailActivity.this,
                                    message, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        ChatHistoryStore.markProjectReadOnly(TeamDetailActivity.this,
                                SessionHelper.getAccountId(TeamDetailActivity.this), project.getId(),
                                "发布者已取消行程，所有成员仅可查看历史消息");
                        project.setStatus(ProjectUiHelper.STATUS_CANCELLED);
                        bindProjectHeader();
                        setupPublisherAction();
                        setResult(RESULT_OK);
                        Toast.makeText(TeamDetailActivity.this,
                                "行程已取消", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onFailure(Call<Result> call, Throwable t) {
                        setupPublisherAction();
                        Toast.makeText(TeamDetailActivity.this,
                                "网络异常，取消失败", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void stylePrimaryAction() {
        btnJoin.setBackgroundTintList(new ColorStateList(
                new int[][]{new int[]{-android.R.attr.state_enabled}, new int[]{}},
                new int[]{0xFFE3E9F2, 0xFF2563EB}));
        btnJoin.setTextColor(new ColorStateList(
                new int[][]{new int[]{-android.R.attr.state_enabled}, new int[]{}},
                new int[]{0xFF78859A, 0xFFFFFFFF}));
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
                    int remaining = project.getMaxMembers() > 0
                            ? project.getMaxMembers() - project.getCurrentMembers()
                            : Integer.MAX_VALUE;
                    if (remaining != Integer.MAX_VALUE && partySize > remaining) {
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

        actionInFlight = true;
        btnJoin.setEnabled(false);
        btnJoin.setText("正在加入…");
        service.joinProject(project.getId(), new JoinProjectRequest(partySize))
                .enqueue(new Callback<Result>() {
            @Override
            public void onResponse(Call<Result> call, Response<Result> response) {
                actionInFlight = false;
                if (response.code() == 401) {
                    Toast.makeText(TeamDetailActivity.this, "登录已失效，请重新登录", Toast.LENGTH_SHORT).show();
                    SessionHelper.handleUnauthorized(TeamDetailActivity.this);
                    return;
                }
                if (response.isSuccessful() && response.body() != null && response.body().getCode() == 1) {
                    ChatHistoryStore.markProjectActive(TeamDetailActivity.this,
                            SessionHelper.getAccountId(TeamDetailActivity.this), project.getId());
                    Toast.makeText(TeamDetailActivity.this,
                            "加入成功，你代表 " + partySize + " 人", Toast.LENGTH_SHORT).show();
                    project.setViewerRole("PARTICIPANT");
                    project.setCurrentMembers(project.getCurrentMembers() + partySize);
                    saveMyPartySize(partySize);
                    showMyPartySize(partySize);
                    bindProjectHeader();
                    setupJoinButton();
                    setResult(RESULT_OK);
                    refreshProject();
                } else {
                    setupJoinButton();
                    Toast.makeText(TeamDetailActivity.this, "暂时无法加入，请刷新行程确认剩余名额", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Result> call, Throwable t) {
                actionInFlight = false;
                setupJoinButton();
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
        txtMyPartySize.setVisibility(View.GONE);
        if (project.getOwnerAccountId() == SessionHelper.getAccountId(this)) {
            partySize = project.getRepresentedCount();
        }
        if (partySize > 0 && ("PARTICIPANT".equalsIgnoreCase(project.getViewerRole())
                || project.getOwnerAccountId() == SessionHelper.getAccountId(this))) {
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
        ++detailGeneration;
        for (Call<?> call : detailCalls) call.cancel();
        detailCalls.clear();
        if (walkRoutePlanner != null) {
            walkRoutePlanner.cancel();
            walkRoutePlanner = null;
        }
        if (mapCreated && routeMapView != null) {
            if (aMap != null) {
                try {
                    aMap.stopAnimation();
                } catch (Throwable ignored) {
                }
            }
            try {
                routeMapView.setVisibility(android.view.View.GONE);
            } catch (Throwable ignored) {
            }
            if (routeMapView.getParent() != null) {
                try {
                    ((android.view.ViewGroup) routeMapView.getParent()).removeView(routeMapView);
                } catch (Throwable ignored) {
                }
            }
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
