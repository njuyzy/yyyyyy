package com.example.Japp.user.fragment.joinTeam;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.Japp.R;
import com.example.Japp.network.ApiClient;
import com.example.Japp.network.api.UserService;
import com.example.Japp.network.models.Account;
import com.example.Japp.network.models.Project;
import com.example.Japp.network.models.Result;
import com.example.Japp.network.models.RouteNode;
import com.example.Japp.user.TeamDetailActivity;
import com.example.Japp.user.UserMainActivity;
import com.example.Japp.user.util.ProjectUiHelper;
import com.example.Japp.user.util.SessionHelper;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TeamList extends Fragment {

    private RecyclerView recycler;
    private SwipeRefreshLayout swipeRefresh;
    private TextView txtEmpty;
    private TeamListAdapter adapter;
    private UserService service;
    private final List<TeamCardItem> teamItems = new ArrayList<>();

    private final ActivityResultLauncher<Intent> detailLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == android.app.Activity.RESULT_OK) {
                    loadTeams();
                }
            });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.user_fragment_join_team, container, false);

        recycler = view.findViewById(R.id.recycler);
        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        txtEmpty = view.findViewById(R.id.txtEmpty);
        ImageButton add = view.findViewById(R.id.add);

        service = ApiClient.getClient().create(UserService.class);
        adapter = new TeamListAdapter();
        adapter.setOnTeamClickListener(item -> {
            Intent intent = new Intent(requireContext(), TeamDetailActivity.class);
            intent.putExtra(TeamDetailActivity.EXTRA_PROJECT_JSON, new Gson().toJson(item.getProject()));
            detailLauncher.launch(intent);
        });

        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);

        if (swipeRefresh != null) {
            swipeRefresh.setOnRefreshListener(this::loadTeams);
        }
        add.setOnClickListener(v -> {
            if (requireActivity() instanceof UserMainActivity) {
                ((UserMainActivity) requireActivity()).switchToRouteTab();
            }
        });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadTeams();
    }

    private void loadTeams() {
        if (!SessionHelper.isLoggedIn(requireContext())) {
            stopRefreshing();
            showEmpty("请先登录后查看拼单");
            adapter.setItems(new ArrayList<>());
            return;
        }

        int accountId = SessionHelper.getAccountId(requireContext());
        if (swipeRefresh != null) {
            swipeRefresh.setRefreshing(true);
        }

        service.filterProjects(accountId, 1, 20, null, null, null, null, true)
                .enqueue(new Callback<Result<List<Project>>>() {
                    @Override
                    public void onResponse(Call<Result<List<Project>>> call,
                                           Response<Result<List<Project>>> response) {
                        if (!isAdded()) {
                            return;
                        }
                        stopRefreshing();
                        if (response.code() == 401) {
                            Toast.makeText(requireContext(), "登录已失效，请重新登录", Toast.LENGTH_SHORT).show();
                            SessionHelper.handleUnauthorized(requireContext());
                            return;
                        }
                        if (!response.isSuccessful() || response.body() == null || response.body().getCode() != 1) {
                            showEmpty("加载失败，下拉刷新重试");
                            adapter.setItems(new ArrayList<>());
                            return;
                        }
                        List<Project> projects = response.body().getData();
                        if (projects == null || projects.isEmpty()) {
                            showEmpty("暂无可加入的拼单，点击右上角 + 去设计路线并发布");
                            adapter.setItems(new ArrayList<>());
                            return;
                        }
                        enrichProjects(projects);
                    }

                    @Override
                    public void onFailure(Call<Result<List<Project>>> call, Throwable t) {
                        if (!isAdded()) {
                            return;
                        }
                        stopRefreshing();
                        showEmpty("网络错误，下拉刷新重试");
                        adapter.setItems(new ArrayList<>());
                    }
                });
    }

    private void enrichProjects(List<Project> projects) {
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
                public void onResponse(Call<Result<Account>> call, Response<Result<Account>> response) {
                    if (response.isSuccessful() && response.body() != null && response.body().getCode() == 1) {
                        Account account = response.body().getData();
                        if (account != null) {
                            item.setOwnerName(account.getUsername());
                        }
                    }
                    fetchRoute(item, project.getRouteId(), done, total);
                }

                @Override
                public void onFailure(Call<Result<Account>> call, Throwable t) {
                    fetchRoute(item, project.getRouteId(), done, total);
                }
            });
        }
    }

    private void fetchRoute(TeamCardItem item, int routeId, AtomicInteger done, int total) {
        service.getRouteNodes(routeId).enqueue(new Callback<Result<List<RouteNode>>>() {
            @Override
            public void onResponse(Call<Result<List<RouteNode>>> call, Response<Result<List<RouteNode>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getCode() == 1) {
                    List<RouteNode> nodes = response.body().getData();
                    item.setRouteSummary(ProjectUiHelper.buildRouteSummary(nodes));
                    item.setDuration(ProjectUiHelper.formatDuration(ProjectUiHelper.sumDurationMinutes(nodes)));
                }
                checkRefresh(done, total);
            }

            @Override
            public void onFailure(Call<Result<List<RouteNode>>> call, Throwable t) {
                checkRefresh(done, total);
            }
        });
    }

    private void checkRefresh(AtomicInteger done, int total) {
        if (done.incrementAndGet() == total && isAdded()) {
            requireActivity().runOnUiThread(() -> adapter.setItems(new ArrayList<>(teamItems)));
        }
    }

    private void stopRefreshing() {
        if (swipeRefresh != null) {
            swipeRefresh.setRefreshing(false);
        }
    }

    private void showEmpty(String message) {
        txtEmpty.setVisibility(View.VISIBLE);
        txtEmpty.setText(message);
        recycler.setVisibility(View.GONE);
    }
}
