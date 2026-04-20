package com.example.Japp.leader.fragment.order;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.Japp.R;
import com.example.Japp.data.Route;
import com.example.Japp.data.User;
import com.example.Japp.data.order;
import com.example.Japp.leader.adapter.orderListAdapter;
import com.example.Japp.leader.orderDetailActivity;
import com.example.Japp.network.ApiClient;
import com.example.Japp.network.api.UserService;
import com.example.Japp.network.models.Account;
import com.example.Japp.network.models.Project;
import com.example.Japp.network.models.Result;
import com.example.Japp.network.models.RouteNode;
import com.example.Japp.data.RouteStop;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import static android.content.Context.MODE_PRIVATE;

public class orderList extends Fragment {

    private RecyclerView recycler;
    private orderListAdapter adapter;
    private List<order> order_list;
    private UserService service;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.leader_fragment_order_list, container, false);

        recycler = view.findViewById(R.id.recycler);
        order_list = new ArrayList<>();

        adapter = new orderListAdapter();
        adapter.setListData(order_list);
        adapter.setOrderOnClickListener(position -> {
            Intent intent = new Intent(requireContext(), orderDetailActivity.class);
            intent.putExtra("order_info", order_list.get(position));
            startActivity(intent);
        });

        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);

        service = ApiClient.getClient().create(UserService.class);
        loadOrders();

        return view;
    }

    private void loadOrders() {
        SharedPreferences prefs = requireContext().getSharedPreferences("user_pref", MODE_PRIVATE);
        int accountId = prefs.getInt("account_id", -1);
        if (accountId == -1) return;

        final int finalAccountId = accountId;
        service.getProjects(finalAccountId, 1, 20).enqueue(new Callback<Result<List<Project>>>() {
            @Override
            public void onResponse(Call<Result<List<Project>>> call, Response<Result<List<Project>>> response) {
                if (!isAdded()) return;
                if (!response.isSuccessful()) {
                    Toast.makeText(requireContext(), "获取订单失败：" + response.code(), Toast.LENGTH_SHORT).show();
                    order_list.clear();
                    adapter.setListData(order_list);
                    return;
                }
                if (response.body() == null || response.body().getCode() != 1) {
                    Toast.makeText(requireContext(), "获取订单失败：服务器返回异常", Toast.LENGTH_SHORT).show();
                    order_list.clear();
                    adapter.setListData(order_list);
                    return;
                }
                List<Project> projects = response.body().getData();
                if (projects == null || projects.isEmpty()) {
                    Toast.makeText(requireContext(), "暂无项目数据", Toast.LENGTH_SHORT).show();
                    order_list.clear();
                    adapter.setListData(order_list);
                    return;
                }
                // 只显示状态为 OPEN 的项目（可接单）
                List<Project> available = new ArrayList<>();
                for (Project p : projects) {
                    if ("OPEN".equals(p.getStatus())) {
                        available.add(p);
                    }
                }
                if (available.isEmpty()) {
                    Toast.makeText(requireContext(), "暂无可接订单", Toast.LENGTH_SHORT).show();
                    order_list.clear();
                    adapter.setListData(order_list);
                    return;
                }

                List<order> tempList = new ArrayList<>();
                for (int i = 0; i < available.size(); i++) {
                    tempList.add(null);
                }
                AtomicInteger done = new AtomicInteger(0);
                int total = available.size();

                for (int i = 0; i < total; i++) {
                    final int idx = i;
                    Project project = available.get(i);
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

                    // 获取客户姓名
                    fetchAccountAndRoute(o, project, tempList, idx, done, total);
                }
            }

            @Override
            public void onFailure(Call<Result<List<Project>>> call, Throwable t) {
                if (isAdded()) {
                    Toast.makeText(requireContext(), "网络错误，无法加载订单", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void fetchAccountAndRoute(order o, Project project,
                                       List<order> tempList, int idx,
                                       AtomicInteger done, int total) {
        // 获取客户（ownerAccount）信息
        service.getAccount(project.getOwnerAccountId()).enqueue(new Callback<Result<Account>>() {
            @Override
            public void onResponse(Call<Result<Account>> call, Response<Result<Account>> response) {
                if (!isAdded()) return;
                if (response.isSuccessful() && response.body() != null && response.body().getCode() == 1) {
                    Account account = response.body().getData();
                    User customer = new User();
                    if (account != null) {
                        customer.setName(account.getUsername());
                        // 城市：从账户的 regionCode 暂无城市名，用 project 的 regionAdcode 显示
                    }
                    o.setCustomer(customer);
                }

                // 设置城市（用 regionAdcode 前4位对应城市，暂显示 adcode 或后续扩展）
                String adcode = project.getRegionAdcode();
                o.setCity(adcode != null ? regionAdcodeToCity(adcode) : "");

                // 获取路线节点计算用时
                fetchRouteAndFinish(o, project.getRouteId(), tempList, idx, done, total);
            }

            @Override
            public void onFailure(Call<Result<Account>> call, Throwable t) {
                if (!isAdded()) return;
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
            public void onResponse(Call<Result<List<RouteNode>>> call, Response<Result<List<RouteNode>>> response) {
                if (!isAdded()) return;
                if (response.isSuccessful() && response.body() != null && response.body().getCode() == 1) {
                    List<RouteNode> nodes = response.body().getData();
                    if (nodes != null && !nodes.isEmpty()) {
                        Collections.sort(nodes, Comparator.comparingInt(RouteNode::getVisitOrder));
                        // 计算总用时（分钟）
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
            public void onFailure(Call<Result<List<RouteNode>>> call, Throwable t) {
                if (!isAdded()) return;
                checkAndRefresh(done, total, tempList);
            }
        });
    }

    private void checkAndRefresh(AtomicInteger done, int total, List<order> tempList) {
        if (done.incrementAndGet() == total) {
            requireActivity().runOnUiThread(() -> {
                order_list.clear();
                for (order o : tempList) {
                    if (o != null) order_list.add(o);
                }
                adapter.setListData(order_list);
            });
        }
    }

    /** 将总分钟数格式化为 "X小时Y分钟" */
    private String formatDuration(int totalMinutes) {
        if (totalMinutes <= 0) return "暂无";
        int hours = totalMinutes / 60;
        int mins = totalMinutes % 60;
        if (hours > 0 && mins > 0) return hours + "小时" + mins + "分钟";
        if (hours > 0) return hours + "小时";
        return mins + "分钟";
    }

    /** 根据 adcode 前缀映射常见城市名，不匹配则返回 adcode */
    private String regionAdcodeToCity(String adcode) {
        if (adcode == null || adcode.length() < 4) return "";
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
}
