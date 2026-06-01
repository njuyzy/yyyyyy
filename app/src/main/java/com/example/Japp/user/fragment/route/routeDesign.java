package com.example.Japp.user.fragment.route;



import android.os.Bundle;

import android.text.TextUtils;

import android.view.LayoutInflater;

import android.view.View;

import android.view.ViewGroup;

import android.widget.Button;

import android.widget.EditText;

import android.widget.Toast;



import androidx.annotation.NonNull;

import androidx.annotation.Nullable;

import androidx.fragment.app.Fragment;

import androidx.recyclerview.widget.LinearLayoutManager;

import androidx.recyclerview.widget.RecyclerView;



import com.amap.api.maps.MapsInitializer;

import com.amap.api.maps.model.LatLng;

import com.example.Japp.R;

import com.example.Japp.network.ApiClient;

import com.example.Japp.network.api.UserService;

import com.example.Japp.network.models.Result;

import com.example.Japp.network.models.RouteNode;

import com.example.Japp.network.models.requests.CreateProjectRequest;

import com.example.Japp.user.util.ProjectUiHelper;
import com.example.Japp.user.util.RoutePlanHelper;
import com.example.Japp.user.util.SessionHelper;

import com.google.gson.JsonElement;



import java.text.SimpleDateFormat;

import java.util.ArrayList;

import java.util.Calendar;

import java.util.Collections;

import java.util.Comparator;

import java.util.List;

import java.util.Locale;



import retrofit2.Call;

import retrofit2.Callback;

import retrofit2.Response;



/**

 * 用户端智能路线：对话式输入 + AI 规划 + 发布拼单。

 */

public class routeDesign extends Fragment {



    private RecyclerView chatRecyclerView;

    private EditText editMessage;

    private Button btnSend;

    private RouteChatAdapter adapter;

    private UserService service;



    @Override

    public void onCreate(@Nullable Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        MapsInitializer.updatePrivacyShow(requireContext(), true, true);

        MapsInitializer.updatePrivacyAgree(requireContext(), true);

    }



    @Override

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,

                             Bundle savedInstanceState) {

        View root = inflater.inflate(R.layout.user_fragment_route_design, container, false);

        service = ApiClient.getClient().create(UserService.class);



        chatRecyclerView = root.findViewById(R.id.chatRecyclerView);

        editMessage = root.findViewById(R.id.editMessage);

        btnSend = root.findViewById(R.id.btnSend);



        adapter = new RouteChatAdapter();

        adapter.setRecyclerView(chatRecyclerView);

        adapter.setMapCreateBundle(savedInstanceState);

        adapter.setListener(new RouteChatAdapter.RouteChatListener() {

            @Override

            public void onPublishClick(RouteChatItem item, int position) {

                publishProject(item);

            }



            @Override

            public void onMapClick(RouteChatItem item) {

                if (!isAdded() || adapter == null) {

                    return;

                }

                adapter.releaseMapsForFullscreen();

                List<LatLng> points = item.getPolylinePoints();

                Runnable openFullscreen = () -> {

                    if (isAdded()) {

                        RouteMapFullscreenActivity.start(requireActivity(), points);

                    }

                };

                if (chatRecyclerView != null) {

                    chatRecyclerView.postDelayed(openFullscreen, 200);

                } else {

                    openFullscreen.run();

                }

            }

        });



        chatRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        chatRecyclerView.setAdapter(adapter);

        btnSend.setOnClickListener(v -> sendRouteRequest());



        return root;

    }



    private void sendRouteRequest() {

        if (editMessage == null) {

            return;

        }

        String text = editMessage.getText().toString().trim();

        if (TextUtils.isEmpty(text)) {

            Toast.makeText(requireContext(), "请输入路线需求", Toast.LENGTH_SHORT).show();

            return;

        }

        if (!SessionHelper.isLoggedIn(requireContext())) {

            Toast.makeText(requireContext(), "请先登录", Toast.LENGTH_SHORT).show();

            SessionHelper.handleUnauthorized(requireContext());

            return;

        }



        adapter.addItem(RouteChatItem.user(text));

        editMessage.setText("");

        scrollChatToBottom();



        setSending(true);
        String memoryId = RoutePlanHelper.buildMemoryId(SessionHelper.getAccountId(requireContext()));

        service.planRouteByAi(memoryId, text).enqueue(new Callback<Result<JsonElement>>() {

            @Override
            public void onResponse(Call<Result<JsonElement>> call, Response<Result<JsonElement>> response) {

                if (!isAdded()) {

                    return;

                }

                setSending(false);

                if (response.code() == 401) {

                    Toast.makeText(requireContext(), "登录已失效，请重新登录", Toast.LENGTH_SHORT).show();

                    SessionHelper.handleUnauthorized(requireContext());

                    return;

                }

                if (!response.isSuccessful() || response.body() == null || response.body().getCode() != 1) {

                    String msg = response.body() != null ? response.body().getMsg() : "路线规划失败";

                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();

                    return;

                }

                int routeId = RoutePlanHelper.parseRouteId(response.body().getData());

                if (routeId <= 0) {

                    Toast.makeText(requireContext(), "未获取到有效路线", Toast.LENGTH_SHORT).show();

                    return;

                }

                loadRouteAndShow(routeId, text);

            }



            @Override

            public void onFailure(Call<Result<JsonElement>> call, Throwable t) {

                if (!isAdded()) {

                    return;

                }

                setSending(false);

                Toast.makeText(requireContext(), RoutePlanHelper.failureMessage(t), Toast.LENGTH_SHORT).show();

            }

        });

    }



    private void loadRouteAndShow(int routeId, String userRequirement) {

        service.getRouteNodes(routeId).enqueue(new Callback<Result<List<RouteNode>>>() {

            @Override

            public void onResponse(Call<Result<List<RouteNode>>> call, Response<Result<List<RouteNode>>> response) {

                if (!isAdded() || adapter == null) {

                    return;

                }

                List<RouteNode> nodes = null;

                if (response.isSuccessful() && response.body() != null && response.body().getCode() == 1) {

                    nodes = response.body().getData();

                }

                List<LatLng> points = extractPolyline(nodes);

                String description = buildDescription(userRequirement, nodes, routeId);

                adapter.addItem(RouteChatItem.assistantRoute(description, points, routeId));

                scrollChatToBottom();

                if (chatRecyclerView != null) {

                    chatRecyclerView.postDelayed(() -> adapter.refreshLastAssistantMap(), 400);

                }

            }



            @Override

            public void onFailure(Call<Result<List<RouteNode>>> call, Throwable t) {

                if (!isAdded() || adapter == null) {

                    return;

                }

                adapter.addItem(RouteChatItem.assistantRoute(

                        "路线已生成（ID: " + routeId + "），但节点加载失败，请稍后重试发布。",

                        RouteSampleData.getMockPolyline(), routeId));

                scrollChatToBottom();

            }

        });

    }



    private List<LatLng> extractPolyline(List<RouteNode> nodes) {

        List<LatLng> points = new ArrayList<>();

        if (nodes == null || nodes.isEmpty()) {

            return RouteSampleData.getMockPolyline();

        }

        List<RouteNode> ordered = new ArrayList<>(nodes);

        Collections.sort(ordered, Comparator.comparingInt(RouteNode::getVisitOrder));

        for (RouteNode node : ordered) {

            LatLng point = RouteMapDrawHelper.parseLocation(node.getLocation());

            if (point != null) {

                points.add(point);

            }

        }

        return points.size() >= 2 ? points : RouteSampleData.getMockPolyline();

    }



    private String buildDescription(String requirement, List<RouteNode> nodes, int routeId) {

        StringBuilder sb = new StringBuilder();

        sb.append("根据你的描述「").append(requirement).append("」，已生成路线方案：\n\n");

        if (nodes != null && !nodes.isEmpty()) {

            int totalMin = ProjectUiHelper.sumDurationMinutes(nodes);

            sb.append("• 预计总时长：约 ").append(ProjectUiHelper.formatDuration(totalMin)).append("\n");

            sb.append("• ").append(ProjectUiHelper.buildRouteSummary(nodes)).append("\n");

        }

        sb.append("• 路线编号：").append(routeId).append("\n\n");

        sb.append("确认无误可点击下方「发布」创建拼单。");

        return sb.toString();

    }



    private void publishProject(RouteChatItem item) {

        if (!item.canPublish()) {

            Toast.makeText(requireContext(), "当前路线暂不可发布", Toast.LENGTH_SHORT).show();

            return;

        }

        if (!SessionHelper.isLoggedIn(requireContext())) {

            Toast.makeText(requireContext(), "请先登录", Toast.LENGTH_SHORT).show();

            SessionHelper.handleUnauthorized(requireContext());

            return;

        }



        Calendar calendar = Calendar.getInstance();

        calendar.add(Calendar.DAY_OF_MONTH, 7);

        String departureDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.getTime());

        String title = item.getText().length() > 20

                ? item.getText().substring(0, 20) + "…"

                : item.getText();

        if (title.trim().isEmpty()) {

            title = "研学拼单";

        }



        CreateProjectRequest request = new CreateProjectRequest(

                item.getRouteId(),

                title,

                departureDate,

                10,

                1,

                "OPEN"

        );



        service.createProject(request).enqueue(new Callback<Result>() {

            @Override

            public void onResponse(Call<Result> call, Response<Result> response) {

                if (!isAdded()) {

                    return;

                }

                if (response.code() == 401) {

                    Toast.makeText(requireContext(), "登录已失效，请重新登录", Toast.LENGTH_SHORT).show();

                    SessionHelper.handleUnauthorized(requireContext());

                    return;

                }

                if (response.isSuccessful() && response.body() != null && response.body().getCode() == 1) {

                    Toast.makeText(requireContext(), "发布成功，可在拼单页查看", Toast.LENGTH_SHORT).show();

                } else {

                    String msg = response.body() != null ? response.body().getMsg() : "发布失败";

                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();

                }

            }



            @Override

            public void onFailure(Call<Result> call, Throwable t) {

                if (isAdded()) {

                    Toast.makeText(requireContext(), "网络错误，发布失败", Toast.LENGTH_SHORT).show();

                }

            }

        });

    }



    private void setSending(boolean sending) {

        if (btnSend != null) {

            btnSend.setEnabled(!sending);

            btnSend.setText(sending ? "AI规划中..." : "发送");

        }

        if (editMessage != null) {

            editMessage.setEnabled(!sending);

        }

    }



    private void scrollChatToBottom() {

        if (chatRecyclerView != null && adapter.getItemCount() > 0) {

            chatRecyclerView.scrollToPosition(adapter.getItemCount() - 1);

        }

    }



    @Override

    public void onResume() {

        super.onResume();

        if (adapter != null) {

            adapter.onHostResume();

            if (chatRecyclerView != null) {

                chatRecyclerView.post(() -> {

                    adapter.recreateVisibleMaps();

                    adapter.refreshLastAssistantMap();

                });

            }

        }

    }



    @Override

    public void onPause() {

        super.onPause();

        if (adapter != null) {

            adapter.onHostPause();

        }

    }



    @Override

    public void onDestroyView() {

        super.onDestroyView();

        if (adapter != null) {

            adapter.onHostDestroy();

        }

        chatRecyclerView = null;

        editMessage = null;

        btnSend = null;

    }



    @Override

    public void onLowMemory() {

        super.onLowMemory();

        if (adapter != null) {

            adapter.onHostLowMemory();

        }

    }

}


