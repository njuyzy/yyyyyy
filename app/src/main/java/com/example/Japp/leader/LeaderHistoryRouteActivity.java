package com.example.Japp.leader;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
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
import com.google.android.material.appbar.MaterialToolbar;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_leader_history_route);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        recycler = findViewById(R.id.recycler);
        txtEmpty = findViewById(R.id.txtEmpty);

        adapter = new TeamListAdapter();
        adapter.setOnTeamClickListener(item -> {
            Intent intent = new Intent(this, orderDetailActivity.class);
            intent.putExtra(orderDetailActivity.EXTRA_PROJECT_JSON, new Gson().toJson(item.getProject()));
            startActivity(intent);
        });

        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        service = ApiClient.getClient().create(UserService.class);
        loadHistoryRoutes();
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
                    Integer leaderId = project.getLeaderAccountId();
                    if (leaderId != null && leaderId == accountId && !"OPEN".equals(project.getStatus())) {
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
        adapter.setItems(temp);
        txtEmpty.setVisibility(View.GONE);
        recycler.setVisibility(View.VISIBLE);

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
                adapter.setItems(new ArrayList<>(teamItems));
            });
        }
    }

    private void showEmpty() {
        teamItems.clear();
        adapter.setItems(new ArrayList<>());
        recycler.setVisibility(View.GONE);
        txtEmpty.setVisibility(View.VISIBLE);
    }
}
