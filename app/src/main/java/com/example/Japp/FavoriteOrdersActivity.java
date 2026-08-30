package com.example.Japp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.Japp.network.ApiClient;
import com.example.Japp.network.api.UserService;
import com.example.Japp.network.models.Project;
import com.example.Japp.network.models.Result;
import com.example.Japp.user.TeamDetailActivity;
import com.example.Japp.user.fragment.joinTeam.FavoriteOrderStore;
import com.example.Japp.user.fragment.joinTeam.TeamCardItem;
import com.example.Japp.user.fragment.joinTeam.TeamListAdapter;
import com.example.Japp.util.DisplayCutoutAdapter;
import com.example.Japp.util.InsetDividerDecoration;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.gson.Gson;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class FavoriteOrdersActivity extends AppCompatActivity {

    private TeamListAdapter adapter;
    private TextView txtEmpty;
    private UserService service;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favorite_orders);
        DisplayCutoutAdapter.apply(this);
        service = ApiClient.getClient().create(UserService.class);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        txtEmpty = findViewById(R.id.txtEmpty);
        RecyclerView recycler = findViewById(R.id.recycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.addItemDecoration(new InsetDividerDecoration(this, 14, 16));

        adapter = new TeamListAdapter();
        adapter.setFavoriteEnabled(true);
        adapter.setOnTeamClickListener(this::openOrder);
        adapter.setOnFavoriteChangedListener((item, favorite) -> loadFavorites());
        recycler.setAdapter(adapter);
        loadFavorites();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (adapter != null) loadFavorites();
    }

    private void loadFavorites() {
        List<TeamCardItem> favorites = FavoriteOrderStore.getAll(this);
        adapter.setItems(favorites);
        txtEmpty.setVisibility(favorites.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void openOrder(TeamCardItem item) {
        Project cached = item.getProject();
        if (cached == null || cached.getId() <= 0) return;
        service.getProject(cached.getId()).enqueue(new Callback<Result<Project>>() {
            @Override
            public void onResponse(Call<Result<Project>> call,
                                   Response<Result<Project>> response) {
                Project latest = response.isSuccessful() && response.body() != null
                        && response.body().getCode() == 1 && response.body().getData() != null
                        ? response.body().getData() : cached;
                launchOrderDetail(latest);
            }

            @Override
            public void onFailure(Call<Result<Project>> call, Throwable t) {
                launchOrderDetail(cached);
            }
        });
    }

    private void launchOrderDetail(Project project) {
        if (isFinishing()) return;
        Intent intent = new Intent(this, TeamDetailActivity.class);
        String json = new Gson().toJson(project);
        intent.putExtra(TeamDetailActivity.EXTRA_PROJECT_JSON, json);
        startActivity(intent);
    }
}
