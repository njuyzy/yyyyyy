package com.example.Japp.leader;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.Japp.MainActivity;
import com.example.Japp.R;
import com.example.Japp.data.Route;
import com.example.Japp.data.RouteStop;
import com.example.Japp.data.User;
import com.example.Japp.data.order;
import com.example.Japp.leader.adapter.orderListAdapter;
import com.example.Japp.network.ApiClient;
import com.example.Japp.network.api.UserService;
import com.example.Japp.network.models.Account;
import com.example.Japp.network.models.Project;
import com.example.Japp.network.models.Result;
import com.example.Japp.network.models.RouteNode;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LeaderHistoryRouteActivity extends AppCompatActivity {

    private RecyclerView recycler;
    private TextView txtEmpty;
    private orderListAdapter adapter;
    private final List<order> orderList = new ArrayList<>();
    private UserService service;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_leader_history_route);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        ViewCompat.setOnApplyWindowInsetsListener(toolbar, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), systemBars.top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });
        toolbar.setNavigationOnClickListener(v -> finish());

        recycler = findViewById(R.id.recycler);
        txtEmpty = findViewById(R.id.txtEmpty);

        adapter = new orderListAdapter();
        adapter.setListData(orderList);
        adapter.setOrderOnClickListener(position -> {
            Intent intent = new Intent(this, orderDetailActivity.class);
            intent.putExtra("order_json", new Gson().toJson(orderList.get(position)));
            startActivity(intent);
        });

        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        service = ApiClient.getClient().create(UserService.class);
        loadHistoryRoutes();
    }

    private void loadHistoryRoutes() {
        SharedPreferences prefs = getSharedPreferences("user_pref", MODE_PRIVATE);
        int accountId = prefs.getInt("account_id", -1);
        if (accountId <= 0) {
            showEmpty();
            return;
        }

        String token = ApiClient.getToken();
        if (token == null || token.trim().isEmpty()) {
            Toast.makeText(this, "登录已失效，请重新登录", Toast.LENGTH_SHORT).show();
            handleUnauthorized();
            return;
        }

        service.getProjects(accountId, 1, 50).enqueue(new Callback<Result<List<Project>>>() {
            @Override
            public void onResponse(@NonNull Call<Result<List<Project>>> call,
                                   @NonNull Response<Result<List<Project>>> response) {
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

                List<order> tempList = new ArrayList<>(Collections.nCopies(history.size(), null));
                AtomicInteger done = new AtomicInteger(0);
                int total = history.size();

                for (int i = 0; i < total; i++) {
                    final int idx = i;
                    Project project = history.get(i);
                    order o = new order();
                    o.setProjectId(project.getId());
                    o.setRouteId(project.getRouteId());
                    o.setTitle(project.getTitle());
                    o.setDepartureDate(project.getDepartureDate());
                    o.setCreatedAt(project.getCreatedAt());
                    o.setTag(project.getTag() != null ? project.getTag() : "");
                    o.set_peopleCnt(project.getMaxMembers());
                    o.setCurrentMembers(project.getCurrentMembers());
                    tempList.set(idx, o);

                    fetchAccountAndRoute(o, project, tempList, idx, done, total);
                }
            }

            @Override
            public void onFailure(@NonNull Call<Result<List<Project>>> call, @NonNull Throwable t) {
                Toast.makeText(LeaderHistoryRouteActivity.this, "加载失败，请检查网络", Toast.LENGTH_SHORT).show();
                showEmpty();
            }
        });
    }

    private void fetchAccountAndRoute(order o, Project project,
                                      List<order> tempList, int idx,
                                      AtomicInteger done, int total) {
        service.getAccount(project.getOwnerAccountId()).enqueue(new Callback<Result<Account>>() {
            @Override
            public void onResponse(@NonNull Call<Result<Account>> call, @NonNull Response<Result<Account>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getCode() == 1) {
                    Account account = response.body().getData();
                    User customer = new User();
                    if (account != null) {
                        customer.setName(account.getUsername());
                    }
                    o.setCustomer(customer);
                }
                o.setCity(regionAdcodeToCity(project.getRegionAdcode()));
                fetchRouteAndFinish(o, project.getRouteId(), tempList, idx, done, total);
            }

            @Override
            public void onFailure(@NonNull Call<Result<Account>> call, @NonNull Throwable t) {
                o.setCity(regionAdcodeToCity(project.getRegionAdcode()));
                fetchRouteAndFinish(o, project.getRouteId(), tempList, idx, done, total);
            }
        });
    }

    private void fetchRouteAndFinish(order o, int routeId,
                                     List<order> tempList, int idx,
                                     AtomicInteger done, int total) {
        service.getRouteNodes(routeId).enqueue(new Callback<Result<List<RouteNode>>>() {
            @Override
            public void onResponse(@NonNull Call<Result<List<RouteNode>>> call,
                                   @NonNull Response<Result<List<RouteNode>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getCode() == 1) {
                    List<RouteNode> nodes = response.body().getData();
                    if (nodes != null && !nodes.isEmpty()) {
                        Collections.sort(nodes, Comparator.comparingInt(RouteNode::getVisitOrder));
                        int totalMin = 0;
                        Route route = new Route();
                        List<RouteStop> stops = new ArrayList<>();
                        for (RouteNode node : nodes) {
                            totalMin += node.getRecommendedDuration();
                            route.addAttraction(node.getName() != null ? node.getName() : "");
                            RouteStop stop = new RouteStop();
                            stop.setVisitOrder(node.getVisitOrder());
                            stop.setName(node.getName());
                            stop.setVisitTime(node.getVisitTime());
                            stop.setRecommendedDuration(node.getRecommendedDuration());
                            stop.setNotes(node.getNotes());
                            stop.setLocation(node.getLocation());
                            stop.setAddress(node.getAddress());
                            stop.setCityname(node.getCityname());
                            stops.add(stop);
                        }
                        o.setRoute(route);
                        o.setRouteStops(stops);
                        o.setEstimatedDuration(formatDuration(totalMin));
                    }
                }
                checkAndRefresh(done, total, tempList);
            }

            @Override
            public void onFailure(@NonNull Call<Result<List<RouteNode>>> call, @NonNull Throwable t) {
                checkAndRefresh(done, total, tempList);
            }
        });
    }

    private void checkAndRefresh(AtomicInteger done, int total, List<order> tempList) {
        if (done.incrementAndGet() == total) {
            runOnUiThread(() -> {
                orderList.clear();
                for (order o : tempList) {
                    if (o != null) {
                        orderList.add(o);
                    }
                }
                if (orderList.isEmpty()) {
                    showEmpty();
                } else {
                    txtEmpty.setVisibility(View.GONE);
                    recycler.setVisibility(View.VISIBLE);
                    adapter.setListData(orderList);
                }
            });
        }
    }

    private void showEmpty() {
        orderList.clear();
        adapter.setListData(orderList);
        recycler.setVisibility(View.GONE);
        txtEmpty.setVisibility(View.VISIBLE);
    }

    private String formatDuration(int totalMinutes) {
        if (totalMinutes <= 0) {
            return "暂无";
        }
        int hours = totalMinutes / 60;
        int mins = totalMinutes % 60;
        if (hours > 0 && mins > 0) {
            return hours + "小时" + mins + "分钟";
        }
        if (hours > 0) {
            return hours + "小时";
        }
        return mins + "分钟";
    }

    private String regionAdcodeToCity(String adcode) {
        if (adcode == null || adcode.length() < 4) {
            return "";
        }
        switch (adcode.substring(0, 4)) {
            case "1101": return "北京市";
            case "1201": return "天津市";
            case "2101": return "沈阳市";
            case "2201": return "长春市";
            case "2301": return "哈尔滨市";
            case "3101": return "上海市";
            case "3201": return "南京市";
            case "3301": return "杭州市";
            case "3501": return "福州市";
            case "3601": return "南昌市";
            case "3701": return "济南市";
            case "4101": return "郑州市";
            case "4201": return "武汉市";
            case "4301": return "长沙市";
            case "4401": return "广州市";
            case "4403": return "深圳市";
            case "4501": return "南宁市";
            case "5001": return "重庆市";
            case "5101": return "成都市";
            case "5201": return "贵阳市";
            case "5301": return "昆明市";
            case "6101": return "西安市";
            default: return adcode;
        }
    }

    private void handleUnauthorized() {
        getSharedPreferences("user_pref", MODE_PRIVATE).edit()
                .putBoolean("is_logged_in", false)
                .remove("account_id")
                .apply();
        ApiClient.clearToken();
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
