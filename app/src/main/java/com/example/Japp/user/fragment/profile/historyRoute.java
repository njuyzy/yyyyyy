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
import com.example.Japp.network.models.ProjectPage;
import com.example.Japp.network.models.Result;
import com.example.Japp.network.models.RouteNode;
import com.example.Japp.user.TeamDetailActivity;
import com.example.Japp.user.UserMainActivity;
import com.example.Japp.user.fragment.joinTeam.TeamCardItem;
import com.example.Japp.user.fragment.joinTeam.TeamListAdapter;
import com.example.Japp.user.fragment.route.SavedRouteStore;
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
    private SavedRouteAdapter savedRouteAdapter;
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
        adapter.setFavoriteEnabled(true);
        savedRouteAdapter = new SavedRouteAdapter(this::openSavedRoute);
        adapter.setOnTeamClickListener(item -> {
            Intent intent = new Intent(requireContext(), TeamDetailActivity.class);
            intent.putExtra(TeamDetailActivity.EXTRA_PROJECT_JSON, new Gson().toJson(item.getProject()));
            startActivity(intent);
        });

        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(new TwoAdapter(savedRouteAdapter, adapter));
        recycler.addItemDecoration(new InsetDividerDecoration(requireContext(), 14, 16));
        loadSavedRoutes();
        loadHistory();
        return view;
    }

    private void loadSavedRoutes() {
        if (!isAdded() || savedRouteAdapter == null) return;
        savedRouteAdapter.setRoutes(SavedRouteStore.getAll(requireContext()));
        updateEmptyState(null);
    }

    private void openSavedRoute(SavedRouteStore.SavedRoute route) {
        SavedRouteStore.requestOpen(requireContext(), route.getId());
        requireActivity().getSupportFragmentManager().popBackStackImmediate();
        if (requireActivity() instanceof UserMainActivity) {
            ((UserMainActivity) requireActivity()).switchToRouteTab();
        }
    }

    private void loadHistory() {
        if (!SessionHelper.isLoggedIn(requireContext())) {
            showEmpty("请先登录");
            return;
        }
        int accountId = SessionHelper.getAccountId(requireContext());
        service.getMyProjects("ALL", null, 1, 30)
                .enqueue(new Callback<Result<ProjectPage>>() {
            @Override
            public void onResponse(Call<Result<ProjectPage>> call,
                                   Response<Result<ProjectPage>> response) {
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
                ProjectPage page = response.body().getData();
                List<Project> projects = page == null ? null : page.getItems();
                if (projects == null || projects.isEmpty()) {
                    showEmpty("暂无历史路线");
                    return;
                }
                List<Project> historyProjects = new ArrayList<>();
                for (Project project : projects) {
                    if (project == null) {
                        continue;
                    }
                    boolean publishedByMe = project.getOwnerAccountId() == accountId;
                    boolean joinedByMe = "PARTICIPANT".equalsIgnoreCase(project.getViewerRole());
                    if (publishedByMe || joinedByMe) {
                        historyProjects.add(project);
                    }
                }
                if (historyProjects.isEmpty()) {
                    showEmpty("暂无历史路线");
                    return;
                }
                enrichProjects(historyProjects);
            }

            @Override
            public void onFailure(Call<Result<ProjectPage>> call, Throwable t) {
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
        adapter.setItems(new ArrayList<>());
        updateEmptyState(message);
    }

    private void updateEmptyState(String message) {
        boolean hasSavedRoutes = savedRouteAdapter != null
                && savedRouteAdapter.getItemCount() > 0;
        boolean hasProjectRoutes = adapter != null && adapter.getItemCount() > 0;
        if (hasSavedRoutes || hasProjectRoutes) {
            txtEmpty.setVisibility(View.GONE);
            if (hasSavedRoutes && message != null
                    && (message.contains("失败") || message.contains("错误"))) {
                Toast.makeText(requireContext(), "行程路线加载失败，已显示本地保存路线",
                        Toast.LENGTH_SHORT).show();
            }
            return;
        }
        txtEmpty.setVisibility(View.VISIBLE);
        txtEmpty.setText(message == null ? "暂无历史路线" : message);
    }

    @Override
    public void onResume() {
        super.onResume();
        loadSavedRoutes();
    }

    /** 兼容项目当前 RecyclerView 版本的双列表组合适配器。 */
    private static final class TwoAdapter
            extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int SECOND_TYPE_OFFSET = 1000;
        private final RecyclerView.Adapter first;
        private final RecyclerView.Adapter second;

        TwoAdapter(RecyclerView.Adapter first, RecyclerView.Adapter second) {
            this.first = first;
            this.second = second;
            RecyclerView.AdapterDataObserver observer = new RecyclerView.AdapterDataObserver() {
                @Override public void onChanged() { notifyDataSetChanged(); }
                @Override public void onItemRangeChanged(int start, int count) {
                    notifyDataSetChanged();
                }
                @Override public void onItemRangeInserted(int start, int count) {
                    notifyDataSetChanged();
                }
                @Override public void onItemRangeRemoved(int start, int count) {
                    notifyDataSetChanged();
                }
                @Override public void onItemRangeMoved(int from, int to, int count) {
                    notifyDataSetChanged();
                }
            };
            first.registerAdapterDataObserver(observer);
            second.registerAdapterDataObserver(observer);
        }

        @NonNull
        @Override
        @SuppressWarnings("unchecked")
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent,
                                                           int viewType) {
            if (viewType >= SECOND_TYPE_OFFSET) {
                return second.onCreateViewHolder(parent, viewType - SECOND_TYPE_OFFSET);
            }
            return first.onCreateViewHolder(parent, viewType);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            int firstCount = first.getItemCount();
            if (position < firstCount) {
                first.onBindViewHolder(holder, position);
            } else {
                second.onBindViewHolder(holder, position - firstCount);
            }
        }

        @Override
        public int getItemViewType(int position) {
            int firstCount = first.getItemCount();
            return position < firstCount
                    ? first.getItemViewType(position)
                    : SECOND_TYPE_OFFSET + second.getItemViewType(position - firstCount);
        }

        @Override
        public int getItemCount() {
            return first.getItemCount() + second.getItemCount();
        }
    }
}
