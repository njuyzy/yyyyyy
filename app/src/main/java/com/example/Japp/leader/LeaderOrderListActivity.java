package com.example.Japp.leader;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.Japp.R;
import com.example.Japp.network.ApiClient;
import com.example.Japp.network.api.UserService;
import com.example.Japp.network.models.Account;
import com.example.Japp.network.models.Project;
import com.example.Japp.network.models.ProjectPage;
import com.example.Japp.network.models.Result;
import com.example.Japp.network.models.RouteNode;
import com.example.Japp.user.fragment.joinTeam.TeamCardItem;
import com.example.Japp.user.fragment.joinTeam.TeamListAdapter;
import com.example.Japp.user.util.ProjectUiHelper;
import com.example.Japp.user.util.SessionHelper;
import com.example.Japp.util.InsetDividerDecoration;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LeaderOrderListActivity extends AppCompatActivity {

    public static final String EXTRA_TYPE = "extra_type";
    public static final int TYPE_PENDING = 0;
    public static final int TYPE_COMPLETED = 1;

    private static final String TAG = "LeaderOrderList";
    private static final int PAGE_SIZE = 15;

    private RecyclerView recycler;
    private SwipeRefreshLayout swipeRefresh;
    private TextView txtEmpty;
    private TeamListAdapter adapter;
    private UserService service;

    private final List<TeamCardItem> items = new ArrayList<>();
    private final Set<Integer> projectIds = new HashSet<>();
    private final Map<Integer, RoutePresentation> routeCache = new HashMap<>();

    private int type;
    private int currentPage = 1;
    private int totalPages = 1;
    private boolean loading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leader_order_list);

        type = getIntent().getIntExtra(EXTRA_TYPE, TYPE_PENDING);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(type == TYPE_COMPLETED ? "已完成订单" : "待完成订单");
        toolbar.setNavigationOnClickListener(v -> finish());

        recycler = findViewById(R.id.recycler);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        txtEmpty = findViewById(R.id.txtEmpty);

        service = ApiClient.getClient().create(UserService.class);
        adapter = new TeamListAdapter();
        adapter.setOnTeamClickListener(item -> {
            Intent intent = new Intent(this, orderDetailActivity.class);
            intent.putExtra(orderDetailActivity.EXTRA_PROJECT_JSON,
                    new Gson().toJson(item.getProject()));
            startActivity(intent);
        });

        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);
        recycler.addItemDecoration(new InsetDividerDecoration(this, 14, 16));
        recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy <= 0 || loading || currentPage >= totalPages) return;
                LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (lm != null && lm.findLastVisibleItemPosition() >= adapter.getItemCount() - 3) {
                    loadPage(currentPage + 1);
                }
            }
        });

        swipeRefresh.setOnRefreshListener(this::refresh);

        loadPage(1);
    }

    private void refresh() {
        currentPage = 1;
        totalPages = 1;
        items.clear();
        projectIds.clear();
        adapter.setItems(new ArrayList<>());
        loadPage(1);
    }

    private void loadPage(int page) {
        if (loading) return;
        if (!SessionHelper.isLoggedIn(this)) {
            swipeRefresh.setRefreshing(false);
            showEmpty("请先登录");
            return;
        }

        int accountId = SessionHelper.getAccountId(this);
        if (accountId <= 0) {
            swipeRefresh.setRefreshing(false);
            showEmpty("账号信息缺失");
            return;
        }

        loading = true;
        String status = type == TYPE_COMPLETED ? "DONE" : null;
        // 待完成订单 = 领队已接但还未结束，包含 CONFIRMED / IN_PROGRESS，
        // 后端只支持单值过滤，这里先按 IN_PROGRESS 拉一页后再补 CONFIRMED。
        // 为简化实现，统一走 "relation=LEADING"，客户端再按状态过滤。
        service.getMyProjects("LEADING", status, page, PAGE_SIZE)
                .enqueue(new Callback<Result<ProjectPage>>() {
                    @Override
                    public void onResponse(@NonNull Call<Result<ProjectPage>> call,
                                           @NonNull Response<Result<ProjectPage>> response) {
                        loading = false;
                        swipeRefresh.setRefreshing(false);
                        if (response.code() == 401) {
                            SessionHelper.handleUnauthorized(LeaderOrderListActivity.this);
                            return;
                        }
                        if (!response.isSuccessful() || response.body() == null
                                || response.body().getCode() != 1) {
                            Toast.makeText(LeaderOrderListActivity.this,
                                    "加载失败，请下拉刷新重试", Toast.LENGTH_SHORT).show();
                            updateEmptyState();
                            return;
                        }
                        ProjectPage pageData = response.body().getData();
                        if (pageData == null) {
                            updateEmptyState();
                            return;
                        }
                        currentPage = pageData.getPageNum();
                        totalPages = Math.max(1, pageData.getPages());
                        appendProjects(pageData.getItems());
                        updateEmptyState();
                    }

                    @Override
                    public void onFailure(@NonNull Call<Result<ProjectPage>> call, @NonNull Throwable t) {
                        loading = false;
                        swipeRefresh.setRefreshing(false);
                        Log.e(TAG, "load orders failed", t);
                        Toast.makeText(LeaderOrderListActivity.this,
                                "网络错误，请检查网络后重试", Toast.LENGTH_SHORT).show();
                        updateEmptyState();
                    }
                });
    }

    private void appendProjects(List<Project> projects) {
        if (projects == null) return;
        List<TeamCardItem> fresh = new ArrayList<>();
        for (Project project : projects) {
            if (project == null || !projectIds.add(project.getId())) continue;
            if (!matchesType(project)) continue;
            TeamCardItem item = new TeamCardItem(project);
            item.setCity(ProjectUiHelper.regionAdcodeToCity(project.getRegionAdcode()));
            items.add(item);
            fresh.add(item);
        }
        items.sort((a, b) -> ProjectUiHelper.compareProjectsByStatus(
                a.getProject(), b.getProject()));
        adapter.setItems(new ArrayList<>(items));
        for (TeamCardItem item : fresh) {
            enrichRoute(item);
        }
    }

    private boolean matchesType(Project project) {
        String status = project.getStatus();
        if (TextUtils.isEmpty(status)) return false;
        if (type == TYPE_COMPLETED) {
            return "DONE".equals(status);
        }
        // 待完成订单：已接单但未结束，包含 CONFIRMED / IN_PROGRESS
        return "CONFIRMED".equals(status) || "IN_PROGRESS".equals(status);
    }

    private void enrichRoute(TeamCardItem item) {
        int routeId = item.getProject().getRouteId();
        if (routeId <= 0) return;
        RoutePresentation cached = routeCache.get(routeId);
        if (cached != null) {
            item.setRouteSummary(cached.summary);
            item.setDuration(cached.duration);
            adapter.setItems(new ArrayList<>(items));
            return;
        }
        service.getRouteNodes(routeId).enqueue(new Callback<Result<List<RouteNode>>>() {
            @Override
            public void onResponse(@NonNull Call<Result<List<RouteNode>>> call,
                                   @NonNull Response<Result<List<RouteNode>>> response) {
                if (!response.isSuccessful() || response.body() == null
                        || response.body().getCode() != 1) return;
                List<RouteNode> nodes = response.body().getData();
                RoutePresentation rp = new RoutePresentation(
                        ProjectUiHelper.buildRouteSummary(nodes),
                        ProjectUiHelper.formatDuration(ProjectUiHelper.sumDurationMinutes(nodes)));
                routeCache.put(routeId, rp);
                item.setRouteSummary(rp.summary);
                item.setDuration(rp.duration);
                adapter.setItems(new ArrayList<>(items));
            }

            @Override
            public void onFailure(@NonNull Call<Result<List<RouteNode>>> call, @NonNull Throwable t) {
                Log.w(TAG, "route summary failed routeId=" + routeId, t);
            }
        });
    }

    private void updateEmptyState() {
        if (items.isEmpty()) {
            txtEmpty.setVisibility(View.VISIBLE);
            txtEmpty.setText(type == TYPE_COMPLETED ? "暂无已完成订单" : "暂无待完成订单");
            recycler.setVisibility(View.GONE);
        } else {
            txtEmpty.setVisibility(View.GONE);
            recycler.setVisibility(View.VISIBLE);
        }
    }

    private void showEmpty(String message) {
        txtEmpty.setVisibility(View.VISIBLE);
        txtEmpty.setText(message);
        recycler.setVisibility(View.GONE);
    }

    private static final class RoutePresentation {
        final String summary;
        final String duration;
        RoutePresentation(String summary, String duration) {
            this.summary = summary;
            this.duration = duration;
        }
    }
}
