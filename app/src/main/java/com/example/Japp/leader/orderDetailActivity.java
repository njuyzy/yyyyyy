package com.example.Japp.leader;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.Japp.R;
import com.example.Japp.data.RouteStop;
import com.example.Japp.data.order;
import com.example.Japp.network.ApiClient;
import com.example.Japp.network.api.UserService;
import com.example.Japp.network.models.Result;
import com.example.Japp.network.models.RouteNode;
import com.example.Japp.network.models.requests.AssignLeaderRequest;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class orderDetailActivity extends AppCompatActivity {

    private UserService service;
    private TextView txtCreatedAt;
    private TextView txtRouteOverview;
    private TextView txtRouteDetail;
    private order currentOrder;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_detail);

        service = ApiClient.getClient().create(UserService.class);
        initialize();
    }

    private void initialize(){

        Button btnAccept=findViewById(R.id.btnAccept);
        btnAccept.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Accept_Order();
            }
        });

        ImageView imageView=findViewById(R.id.imgRoute);
        TextView price=findViewById(R.id.txtPrice);
        TextView title=findViewById(R.id.txtTitle);
        TextView meta=findViewById(R.id.txtMeta);
        TextView tags=findViewById(R.id.txtTags);
        txtCreatedAt=findViewById(R.id.txtCreatedAt);
        txtRouteOverview=findViewById(R.id.txtRouteOverview);
        txtRouteDetail=findViewById(R.id.txtRouteDetail);

        txtRouteDetail.setMovementMethod(new ScrollingMovementMethod());

        currentOrder = getOrderFromIntent();
        if (currentOrder != null) {
            String titleText = !currentOrder.getTitle().isEmpty() ? currentOrder.getTitle() : "研学项目";
            title.setText(titleText);

            String name = currentOrder.getCustomer().getUsername();
            String city = currentOrder.getCity();
            String date = currentOrder.getDepartureDate();
            String metaText = (city.isEmpty() ? "未知城市" : city)
                    + " · " + (name.isEmpty() ? "匿名" : name)
                    + (date.isEmpty() ? "" : " · 出发:" + date);
            meta.setText(metaText);

            String tag = currentOrder.getTag();
            String duration = currentOrder.getEstimatedDuration();
            String tagsText = (tag.isEmpty() ? "偏好：暂无" : "偏好：" + tag)
                    + (duration == null || duration.isEmpty() ? "" : " · 用时:" + duration);
            tags.setText(tagsText);

            String peopleText = "人数：" + currentOrder.getCurrentMembers() + "/" + currentOrder.getPeopleCnt();
            price.setText(peopleText);

            String createdAt = currentOrder.getCreatedAt();
            txtCreatedAt.setText(createdAt.isEmpty() ? "订单创建时间：暂无" : "订单创建时间：" + createdAt);

            if (!currentOrder.getRouteStops().isEmpty()) {
                renderRouteStops(currentOrder.getRouteStops());
            } else {
                loadRouteDetail(currentOrder.getRouteId());
            }
        }
        // TODO: 设置路线图片（目前使用占位图）
    }

    private void renderRouteStops(List<RouteStop> stops) {
        if (stops == null || stops.isEmpty()) {
            txtRouteOverview.setText("具体路线：暂无");
            txtRouteDetail.setText("路线详情：暂无");
            return;
        }
        Collections.sort(stops, Comparator.comparingInt(RouteStop::getVisitOrder));
        StringBuilder overview = new StringBuilder();
        StringBuilder detail = new StringBuilder();
        for (int i = 0; i < stops.size(); i++) {
            RouteStop stop = stops.get(i);
            String name = stop.getName();
            if (!name.isEmpty()) {
                if (overview.length() > 0) overview.append(" → ");
                overview.append(name);
            }
            String visitTime = stop.getVisitTime();
            String note = stop.getNotes();
            String duration = stop.getRecommendedDuration() > 0
                    ? stop.getRecommendedDuration() + "分钟" : "时长待定";
            String line = (i + 1) + ". "
                    + "参观时间：" + (TextUtils.isEmpty(visitTime) ? "待定" : visitTime)
                    + "\n景点：" + (name.isEmpty() ? "未命名景点" : name)
                    + "（" + duration + "）";
            detail.append(line);
            if (!TextUtils.isEmpty(note)) {
                detail.append("\n备注：").append(note);
            } else {
                detail.append("\n备注：暂无");
            }
            if (i < stops.size() - 1) {
                detail.append("\n\n");
            }
        }
        txtRouteOverview.setText(overview.length() == 0 ? "具体路线：暂无" : "具体路线：" + overview);
        txtRouteDetail.setText("路线详情：\n" + detail);
    }

    private void loadRouteDetail(int routeId) {
        if (routeId <= 0) {
            txtRouteOverview.setText("具体路线：暂无");
            txtRouteDetail.setText("路线详情：暂无");
            return;
        }
        service.getRouteNodes(routeId).enqueue(new Callback<Result<List<RouteNode>>>() {
            @Override
            public void onResponse(Call<Result<List<RouteNode>>> call, Response<Result<List<RouteNode>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getCode() == 1) {
                    List<RouteNode> nodes = response.body().getData();
                    if (nodes == null || nodes.isEmpty()) {
                        txtRouteOverview.setText("具体路线：暂无");
                        txtRouteDetail.setText("路线详情：暂无");
                        return;
                    }
                    Collections.sort(nodes, Comparator.comparingInt(RouteNode::getVisitOrder));
                    StringBuilder overview = new StringBuilder();
                    StringBuilder detail = new StringBuilder();
                    for (int i = 0; i < nodes.size(); i++) {
                        RouteNode node = nodes.get(i);
                        String name = node.getName() == null ? "" : node.getName();
                        if (!name.isEmpty()) {
                            if (overview.length() > 0) overview.append(" → ");
                            overview.append(name);
                        }
                        String visitTime = node.getVisitTime();
                        String note = node.getNotes();
                        String duration = node.getRecommendedDuration() > 0
                                ? node.getRecommendedDuration() + "分钟" : "时长待定";
                        String line = (i + 1) + ". "
                                + "参观时间：" + (TextUtils.isEmpty(visitTime) ? "待定" : visitTime)
                                + "\n景点：" + (name.isEmpty() ? "未命名景点" : name)
                                + "（" + duration + "）";
                        detail.append(line);
                        if (!TextUtils.isEmpty(note)) {
                            detail.append("\n备注：").append(note);
                        } else {
                            detail.append("\n备注：暂无");
                        }
                        if (i < nodes.size() - 1) {
                            detail.append("\n\n");
                        }
                    }
                    txtRouteOverview.setText(overview.length() == 0 ? "具体路线：暂无" : "具体路线：" + overview);
                    txtRouteDetail.setText("路线详情：\n" + detail);
                } else {
                    txtRouteOverview.setText("具体路线：加载失败");
                    txtRouteDetail.setText("路线详情：加载失败");
                }
            }

            @Override
            public void onFailure(Call<Result<List<RouteNode>>> call, Throwable t) {
                txtRouteOverview.setText("具体路线：加载失败");
                txtRouteDetail.setText("路线详情：加载失败");
            }
        });
    }

    private order getOrderFromIntent() {
        Intent intent = getIntent();
        if (intent == null) return null;
        Object extra = intent.getSerializableExtra("order_info");
        if (extra instanceof order) {
            return (order) extra;
        }
        return null;
    }
    private void Accept_Order(){
        if (currentOrder == null) return;
        SharedPreferences prefs = getSharedPreferences("user_pref", MODE_PRIVATE);
        int leaderAccountId = prefs.getInt("account_id", -1);
        if (leaderAccountId == -1) {
            Toast.makeText(this, "未登录，无法接单", Toast.LENGTH_SHORT).show();
            return;
        }
        service.assignLeader(currentOrder.getProjectId(), new AssignLeaderRequest(leaderAccountId))
                .enqueue(new Callback<Result>() {
                    @Override
                    public void onResponse(Call<Result> call, Response<Result> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getCode() == 1) {
                            Toast.makeText(orderDetailActivity.this, "接单成功", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(orderDetailActivity.this, "接单失败", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<Result> call, Throwable t) {
                        Toast.makeText(orderDetailActivity.this, "网络错误，接单失败", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}