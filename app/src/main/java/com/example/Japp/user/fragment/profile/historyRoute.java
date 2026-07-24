package com.example.Japp.user.fragment.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.Japp.R;
import com.example.Japp.network.ApiClient;
import com.example.Japp.network.api.UserService;
import com.example.Japp.network.models.Account;
import com.example.Japp.network.models.Project;
import com.example.Japp.network.models.Result;
import com.example.Japp.network.models.RouteNode;
import com.example.Japp.user.TeamDetailActivity;
import com.example.Japp.user.fragment.joinTeam.TeamCardItem;
import com.example.Japp.user.fragment.joinTeam.TeamListAdapter;
import com.example.Japp.user.util.ProjectUiHelper;
import com.example.Japp.user.util.SessionHelper;
import com.example.Japp.util.InsetDividerDecoration;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class historyRoute extends Fragment {

    private TeamListAdapter adapter;
    private TextView txtEmpty;
    private UserService service;
    private final List<TeamCardItem> items = new ArrayList<>();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.user_fragment_history_route, container, false);

        MaterialToolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());

        RecyclerView recycler = view.findViewById(R.id.recycler);
        txtEmpty = view.findViewById(R.id.txtEmpty);
        service = ApiClient.getClient().create(UserService.class);

        adapter = new TeamListAdapter();
        adapter.setOnTeamClickListener(item -> {
            Intent intent = new Intent(requireContext(), TeamDetailActivity.class);
            intent.putExtra(TeamDetailActivity.EXTRA_PROJECT_JSON, new Gson().toJson(item.getProject()));
            startActivity(intent);
        });

        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);
        recycler.addItemDecoration(new InsetDividerDecoration(requireContext(), 14, 16));
        loadHistory();
        return view;
    }

    private void loadHistory() {
        if (!SessionHelper.isLoggedIn(requireContext())) {
            showEmpty("请先登录");
            return;
        }
        int accountId = SessionHelper.getAccountId(requireContext());
        service.getOwnedProjects(accountId, accountId, 1, 30, false)
                .enqueue(new Callback<Result<List<Project>>>() {
            @Override
            public void onResponse(Call<Result<List<Project>>> call, Response<Result<List<Project>>> response) {
                if (!isAdded()) {
                    return;
                }
                if (response.code() == 401) {
                    SessionHelper.handleUnauthorized(requireContext());
                    return;
                }
                if (!response.isSuccessful() || response.body() == null || response.body().getCode() != 1) {
                    showEmpty("加载失败");
                    return;
                }
                List<Project> projects = response.body().getData();
                if (projects == null || projects.isEmpty()) {
                    showEmpty("暂无相关项目");
                    return;
                }
                List<Project> ownedProjects = new ArrayList<>();
                for (Project project : projects) {
                    if (project != null && project.getOwnerAccountId() == accountId) {
                        ownedProjects.add(project);
                    }
                }
                if (ownedProjects.isEmpty()) {
                    showEmpty("暂无自己发布的路线");
                    return;
                }
                enrichProjects(ownedProjects);
            }

            @Override
            public void onFailure(Call<Result<List<Project>>> call, Throwable t) {
                if (isAdded()) {
                    showEmpty("网络错误");
                }
            }
        });
    }

    private void enrichProjects(List<Project> projects) {
        items.clear();
        List<TeamCardItem> temp = new ArrayList<>();
        for (Project project : projects) {
            TeamCardItem item = new TeamCardItem(project);
            item.setCity(ProjectUiHelper.regionAdcodeToCity(project.getRegionAdcode()));
            temp.add(item);
            items.add(item);
        }
        adapter.setItems(temp);
        txtEmpty.setVisibility(View.GONE);

        AtomicInteger done = new AtomicInteger(0);
        int total = projects.size();
        for (int i = 0; i < projects.size(); i++) {
            TeamCardItem item = temp.get(i);
            Project project = projects.get(i);
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
                checkDone(done, total);
            }

            @Override
            public void onFailure(Call<Result<List<RouteNode>>> call, Throwable t) {
                checkDone(done, total);
            }
        });
    }

    private void checkDone(AtomicInteger done, int total) {
        if (done.incrementAndGet() == total && isAdded()) {
            requireActivity().runOnUiThread(() -> adapter.setItems(new ArrayList<>(items)));
        }
    }

    private void showEmpty(String message) {
        txtEmpty.setVisibility(View.VISIBLE);
        txtEmpty.setText(message);
        adapter.setItems(new ArrayList<>());
    }
}
