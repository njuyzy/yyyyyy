package com.example.Japp.leader;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.Japp.R;
import com.example.Japp.network.ApiClient;
import com.example.Japp.network.api.UserService;
import com.example.Japp.network.models.Account;
import com.example.Japp.network.models.Project;
import com.example.Japp.network.models.Result;
import com.example.Japp.network.models.RouteNode;
import com.example.Japp.user.fragment.joinTeam.TeamCardItem;
import com.example.Japp.user.fragment.joinTeam.TeamListAdapter;
import com.example.Japp.user.util.ProjectUiHelper;
import com.example.Japp.user.util.SessionHelper;
import com.example.Japp.util.DisplayCutoutAdapter;
import com.example.Japp.util.InsetDividerDecoration;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.ChipGroup;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LeaderHistoryRouteActivity extends AppCompatActivity {

    private RecyclerView recycler;
    private TextView txtEmpty;
    private TeamListAdapter adapter;
    private final List<TeamCardItem> teamItems = new ArrayList<>();
    private UserService service;
    private String selectedStatus = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.user_fragment_history_route);
        DisplayCutoutAdapter.apply(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        recycler = findViewById(R.id.recycler);
        txtEmpty = findViewById(R.id.txtEmpty);
        bindStatusFilter();

        adapter = new TeamListAdapter();
        adapter.setOnTeamClickListener(item -> {
            Intent intent = new Intent(this, orderDetailActivity.class);
            intent.putExtra(orderDetailActivity.EXTRA_PROJECT_JSON, new Gson().toJson(item.getProject()));
            startActivity(intent);
        });

        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);
        recycler.addItemDecoration(new InsetDividerDecoration(this, 14, 16));

        service = ApiClient.getClient().create(UserService.class);
        loadHistoryRoutes();
    }

    private void bindStatusFilter() {
        findViewById(R.id.chipStatusMatching).setVisibility(View.GONE);
        findViewById(R.id.chipStatusConfirmed).setVisibility(View.GONE);
        findViewById(R.id.chipStatusCancelled).setVisibility(View.GONE);

        ChipGroup group = findViewById(R.id.statusFilterGroup);
        group.check(R.id.chipStatusAll);
        group.setOnCheckedChangeListener((chipGroup, checkedId) -> {
            if (checkedId == R.id.chipStatusOpen) {
                selectedStatus = ProjectUiHelper.STATUS_OPEN;
            } else if (checkedId == R.id.chipStatusInProgress) {
                selectedStatus = ProjectUiHelper.STATUS_IN_PROGRESS;
            } else if (checkedId == R.id.chipStatusDone) {
                selectedStatus = ProjectUiHelper.STATUS_DONE;
            } else {
                selectedStatus = "";
            }
            applyStatusFilter(true);
        });
    }

    private void loadHistoryRoutes() {
        if (!SessionHelper.isLoggedIn(this)) {
            showEmpty();
            return;
        }

        int accountId = SessionHelper.getAccountId(this);
        if (accountId <= 0) {
            showEmpty();
            return;
        }

        service.getProjects(accountId, 1, 50).enqueue(new Callback<Result<List<Project>>>() {
            @Override
            public void onResponse(@NonNull Call<Result<List<Project>>> call,
                                   @NonNull Response<Result<List<Project>>> response) {
                if (response.code() == 401) {
                    Toast.makeText(LeaderHistoryRouteActivity.this, "登录已失效，请重新登录", Toast.LENGTH_SHORT).show();
                    SessionHelper.handleUnauthorized(LeaderHistoryRouteActivity.this);
                    return;
                }
                if (!response.isSuccessful() || response.body() == null || response.body().getCode() != 1) {
                    showEmpty();
                    return;
                }

                List<Project> projects = response.body().getData();
                if (projects == null || projects.isEmpty()) {
                    showEmpty();
                    return;
                }

                List<Project> history = new ArrayList<>();
                for (Project project : projects) {
                    if (project == null) {
                        continue;
                    }
                    Integer leaderId = project.getLeaderAccountId();
                    String status = ProjectUiHelper.normalizeStatus(project.getStatus());
                    if (leaderId != null && leaderId == accountId && isVisibleStatus(status)) {
                        history.add(project);
                    }
                }

                if (history.isEmpty()) {
                    showEmpty();
                    return;
                }

                enrichProjects(history);
            }

            @Override
            public void onFailure(@NonNull Call<Result<List<Project>>> call, @NonNull Throwable t) {
                Toast.makeText(LeaderHistoryRouteActivity.this, "加载失败，请检查网络", Toast.LENGTH_SHORT).show();
                showEmpty();
            }
        });
    }

    private void enrichProjects(List<Project> projects) {
        ProjectUiHelper.sortProjectsByStatus(projects);
        teamItems.clear();
        List<TeamCardItem> temp = new ArrayList<>();
        for (Project project : projects) {
            TeamCardItem item = new TeamCardItem(project);
            item.setCity(ProjectUiHelper.regionAdcodeToCity(project.getRegionAdcode()));
            temp.add(item);
            teamItems.add(item);
        }
        applyStatusFilter(false);

        AtomicInteger done = new AtomicInteger(0);
        int total = projects.size();
        for (int i = 0; i < projects.size(); i++) {
            final int index = i;
            Project project = projects.get(i);
            TeamCardItem item = temp.get(index);

            service.getAccount(project.getOwnerAccountId()).enqueue(new Callback<Result<Account>>() {
                @Override
                public void onResponse(@NonNull Call<Result<Account>> call, @NonNull Response<Result<Account>> response) {
                    if (response.isSuccessful() && response.body() != null && response.body().getCode() == 1) {
                        Account account = response.body().getData();
                        if (account != null) {
                            item.setOwnerName(account.getUsername());
                        }
                    }
                    fetchRoute(item, project.getRouteId(), done, total);
                }

                @Override
                public void onFailure(@NonNull Call<Result<Account>> call, @NonNull Throwable t) {
                    fetchRoute(item, project.getRouteId(), done, total);
                }
            });
        }
    }

    private void fetchRoute(TeamCardItem item, int routeId, AtomicInteger done, int total) {
        service.getRouteNodes(routeId).enqueue(new Callback<Result<List<RouteNode>>>() {
            @Override
            public void onResponse(@NonNull Call<Result<List<RouteNode>>> call,
                                   @NonNull Response<Result<List<RouteNode>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getCode() == 1) {
                    List<RouteNode> nodes = response.body().getData();
                    item.setRouteSummary(ProjectUiHelper.buildRouteSummary(nodes));
                    item.setDuration(ProjectUiHelper.formatDuration(ProjectUiHelper.sumDurationMinutes(nodes)));
                }
                checkRefresh(done, total);
            }

            @Override
            public void onFailure(@NonNull Call<Result<List<RouteNode>>> call, @NonNull Throwable t) {
                checkRefresh(done, total);
            }
        });
    }

    private void checkRefresh(AtomicInteger done, int total) {
        if (done.incrementAndGet() == total) {
            runOnUiThread(() -> {
                teamItems.sort((left, right) -> ProjectUiHelper.compareProjectsByStatus(
                        left.getProject(), right.getProject()));
                applyStatusFilter(false);
            });
        }
    }

    private static boolean isVisibleStatus(String status) {
        return ProjectUiHelper.STATUS_OPEN.equals(status)
                || ProjectUiHelper.STATUS_IN_PROGRESS.equals(status)
                || ProjectUiHelper.STATUS_DONE.equals(status);
    }

    private void applyStatusFilter(boolean animate) {
        if (adapter == null) {
            return;
        }
        List<TeamCardItem> filtered = new ArrayList<>();
        for (TeamCardItem item : teamItems) {
            Project project = item == null ? null : item.getProject();
            if (project != null && (selectedStatus.isEmpty() || selectedStatus.equals(
                    ProjectUiHelper.normalizeStatus(project.getStatus())))) {
                filtered.add(item);
            }
        }
        adapter.setItems(filtered);
        txtEmpty.setText(selectedStatus.isEmpty()
                ? "暂无历史路线"
                : "暂无" + ProjectUiHelper.statusLabel(selectedStatus) + "的历史路线");
        txtEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        recycler.setVisibility(View.VISIBLE);
        if (animate) {
            recycler.animate().cancel();
            recycler.setAlpha(0.72f);
            recycler.animate().alpha(1f).setDuration(160L).start();
        }
    }

    private void showEmpty() {
        teamItems.clear();
        adapter.setItems(new ArrayList<>());
        recycler.setVisibility(View.VISIBLE);
        txtEmpty.setVisibility(View.VISIBLE);
        txtEmpty.setText(selectedStatus.isEmpty()
                ? "暂无历史路线"
                : "暂无" + ProjectUiHelper.statusLabel(selectedStatus) + "的历史路线");
    }
}
