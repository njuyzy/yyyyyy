package com.example.Japp.user.fragment.route;



import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import com.example.Japp.leader.LeaderWalkRoutePlanner;
import com.example.Japp.network.ApiClient;
import com.example.Japp.network.api.UserService;
import com.example.Japp.network.models.Result;
import com.example.Japp.network.models.RouteNode;
import com.example.Japp.network.models.requests.CreateProjectRequest;
import com.example.Japp.user.util.ProjectUiHelper;
import com.example.Japp.user.util.RoutePlanHelper;
import com.example.Japp.user.util.SessionHelper;
import com.google.android.material.chip.Chip;
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

    private static final String[] WAITING_TIPS = {
            "路线助手正在理解你的需求…",
            "正在检索合适的研学景点…",
            "正在生成行程顺序与时长…",
            "AI 规划通常需要约 1 分钟，请稍候…",
            "马上就好，正在完善路线细节…"
    };

    private RecyclerView chatRecyclerView;
    private EditText editMessage;
    private Button btnSend;
    private View welcomePanel;
    private RouteChatAdapter adapter;
    private UserService service;
    private LeaderWalkRoutePlanner walkRoutePlanner;

    private final Handler waitingHandler = new Handler(Looper.getMainLooper());
    private int waitingStatusPosition = -1;
    private int waitingTipIndex = 0;
    private boolean waitingActive;

    private final Runnable waitingTipRunnable = new Runnable() {
        @Override
        public void run() {
            if (!waitingActive || !isAdded() || adapter == null || waitingStatusPosition < 0) {
                return;
            }
            // 顺序播放到最后一条后停住，不再从头循环
            if (waitingTipIndex >= WAITING_TIPS.length - 1) {
                return;
            }
            waitingTipIndex++;
            adapter.updateItemText(waitingStatusPosition, WAITING_TIPS[waitingTipIndex]);
            if (waitingTipIndex < WAITING_TIPS.length - 1) {
                waitingHandler.postDelayed(this, 4500);
            }
        }
    };



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
        walkRoutePlanner = new LeaderWalkRoutePlanner(requireContext());

        chatRecyclerView = root.findViewById(R.id.chatRecyclerView);

        editMessage = root.findViewById(R.id.editMessage);

        btnSend = root.findViewById(R.id.btnSend);
        welcomePanel = root.findViewById(R.id.welcomePanel);

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
                List<LatLng> road = item.getPolylinePoints();
                List<LatLng> waypoints = item.getWaypointPoints();
                Runnable openFullscreen = () -> {
                    if (isAdded()) {
                        RouteMapFullscreenActivity.start(requireActivity(), road, waypoints, null);
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
        setupSuggestionChips(root);
        updateWelcomeVisibility();

        return root;

    }

    private void setupSuggestionChips(View root) {
        bindSuggestionChip(root.findViewById(R.id.chipNanjing), R.string.route_chip_nanjing);
        bindSuggestionChip(root.findViewById(R.id.chipBeijing), R.string.route_chip_beijing);
        bindSuggestionChip(root.findViewById(R.id.chipSuzhou), R.string.route_chip_suzhou);
        bindSuggestionChip(root.findViewById(R.id.chipShanghai), R.string.route_chip_shanghai);
    }

    private void bindSuggestionChip(@Nullable Chip chip, int textRes) {
        if (chip == null) {
            return;
        }
        chip.setOnClickListener(v -> {
            if (editMessage == null) {
                return;
            }
            editMessage.setText(getString(textRes));
            editMessage.setSelection(editMessage.getText() != null ? editMessage.getText().length() : 0);
            sendRouteRequest();
        });
    }

    private void updateWelcomeVisibility() {
        if (welcomePanel == null || adapter == null) {
            return;
        }
        welcomePanel.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
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
        updateWelcomeVisibility();
        editMessage.setText("");
        scrollChatToBottom();

        setSending(true);
        startWaitingFeedback();

        int accountId = SessionHelper.getAccountId(requireContext());
        String memoryId = RoutePlanHelper.buildMemoryId(accountId);

        // 后端约定：POST /routes/ai/{memoryId}?message=... ，data 返回路线 ID
        service.planRouteByAi(memoryId, text).enqueue(new Callback<Result<JsonElement>>() {

            @Override
            public void onResponse(Call<Result<JsonElement>> call, Response<Result<JsonElement>> response) {
                if (!isAdded()) {
                    return;
                }

                Result<JsonElement> body = response.body();

                if (response.code() == 401) {
                    stopWaitingFeedback();
                    setSending(false);
                    showPlanError("登录已失效，请重新登录");
                    SessionHelper.handleUnauthorized(requireContext());
                    return;
                }

                if (!response.isSuccessful() || body == null || body.getCode() != 1) {
                    stopWaitingFeedback();
                    setSending(false);
                    showPlanError(RoutePlanHelper.readErrorMessage(response, body));
                    return;
                }

                int routeId = RoutePlanHelper.parseRouteId(body.getData());
                if (routeId <= 0) {
                    stopWaitingFeedback();
                    setSending(false);
                    showPlanError("规划成功但未返回路线编号，请稍后重试");
                    return;
                }

                updateWaitingFeedback("路线已生成，正在匹配道路路径…");
                loadRouteAndShow(routeId, text);
            }

            @Override
            public void onFailure(Call<Result<JsonElement>> call, Throwable t) {
                if (!isAdded()) {
                    return;
                }
                stopWaitingFeedback();
                setSending(false);
                showPlanError(RoutePlanHelper.failureMessage(t));
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

                if (nodes == null || nodes.isEmpty()) {
                    String fallbackText = "根据你的描述「" + userRequirement
                            + "」已生成路线（ID: " + routeId + "），但暂无景点详情。";
                    finishWithRoute(fallbackText, null, routeId,
                            RouteSampleData.getMockPolyline(), RouteSampleData.getMockPolyline(), null);
                    return;
                }

                updateWaitingFeedback("正在按真实道路规划步行路线…");
                final List<RouteNode> orderedNodes = new ArrayList<>(nodes);
                Collections.sort(orderedNodes, Comparator.comparingInt(RouteNode::getVisitOrder));
                List<LatLng> waypoints = RouteMapDrawHelper.extractPointsFromNodes(orderedNodes);

                if (waypoints.size() < 2 || walkRoutePlanner == null) {
                    finishWithRoute(userRequirement, orderedNodes, routeId, waypoints, waypoints, null);
                    return;
                }

                walkRoutePlanner.planSummary(orderedNodes, new LeaderWalkRoutePlanner.Callback() {
                    @Override
                    public void onPlanningStarted() {
                        // already showing waiting tip
                    }

                    @Override
                    public void onPlanningFinished(@NonNull String summary,
                                                   @NonNull ArrayList<String> instructions,
                                                   boolean hadFailures) {
                        // unused; prefer 4-arg overload
                    }

                    @Override
                    public void onPlanningFinished(@NonNull String summary,
                                                   @NonNull ArrayList<String> instructions,
                                                   @NonNull List<LatLng> roadPolyline,
                                                   boolean hadFailures) {
                        if (!isAdded()) {
                            return;
                        }
                        List<LatLng> road = (roadPolyline != null && roadPolyline.size() >= 2)
                                ? roadPolyline : waypoints;
                        finishWithRoute(userRequirement, orderedNodes, routeId, road, waypoints, summary);
                    }

                    @Override
                    public void onPlanningFailed(@NonNull String message) {
                        if (!isAdded()) {
                            return;
                        }
                        finishWithRoute(userRequirement, orderedNodes, routeId, waypoints, waypoints, null);
                    }
                });
            }

            @Override
            public void onFailure(Call<Result<List<RouteNode>>> call, Throwable t) {
                if (!isAdded() || adapter == null) {
                    return;
                }
                String fallbackText = "路线已生成（ID: " + routeId + "），但节点加载失败，请稍后重试发布。";
                finishWithRoute(fallbackText, null, routeId, RouteSampleData.getMockPolyline(),
                        RouteSampleData.getMockPolyline(), null);
            }
        });
    }

    private void finishWithRoute(String userRequirementOrText,
                                 @Nullable List<RouteNode> nodes,
                                 int routeId,
                                 @NonNull List<LatLng> roadPolyline,
                                 @NonNull List<LatLng> waypoints,
                                 @Nullable String walkSummary) {
        stopWaitingFeedback();
        setSending(false);
        if (!isAdded() || adapter == null) {
            return;
        }

        String description;
        if (nodes != null) {
            description = buildDescription(userRequirementOrText, nodes, routeId);
            if (!TextUtils.isEmpty(walkSummary)) {
                description = description + "\n\n步行参考：" + walkSummary;
            }
        } else {
            description = userRequirementOrText;
        }

        List<LatLng> road = roadPolyline.size() >= 2 ? roadPolyline : waypoints;
        List<LatLng> marks = waypoints.size() >= 1 ? waypoints : road;
        int statusPos = waitingStatusPosition;
        RouteChatItem item = RouteChatItem.assistantRoute(description, road, marks, routeId);
        if (statusPos >= 0 && statusPos < adapter.getItemCount()) {
            adapter.replaceItem(statusPos, item);
        } else {
            adapter.addItem(item);
        }
        waitingStatusPosition = -1;
        scrollChatToBottom();
        if (chatRecyclerView != null) {
            chatRecyclerView.postDelayed(() -> adapter.refreshLastAssistantMap(), 400);
        }
    }

    private void startWaitingFeedback() {
        stopWaitingFeedback();
        waitingActive = true;
        waitingTipIndex = 0;
        adapter.addItem(RouteChatItem.assistantStatus(WAITING_TIPS[0]));
        waitingStatusPosition = adapter.getLastItemPosition();
        scrollChatToBottom();
        if (WAITING_TIPS.length > 1) {
            waitingHandler.postDelayed(waitingTipRunnable, 4500);
        }
    }

    private void updateWaitingFeedback(String text) {
        if (!isAdded() || adapter == null || waitingStatusPosition < 0) {
            return;
        }
        // 阶段提示覆盖轮播，并停止继续切换
        stopWaitingFeedback();
        adapter.updateItemText(waitingStatusPosition, text);
        scrollChatToBottom();
    }

    private void stopWaitingFeedback() {
        waitingActive = false;
        waitingHandler.removeCallbacks(waitingTipRunnable);
    }

    private List<LatLng> extractPolyline(List<RouteNode> nodes) {
        List<LatLng> points = RouteMapDrawHelper.extractPointsFromNodes(nodes);
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



    private void showPlanError(String message) {
        stopWaitingFeedback();
        setSending(false);
        if (!isAdded() || adapter == null) {
            return;
        }
        String display = TextUtils.isEmpty(message) ? "路线规划失败" : message;
        Toast.makeText(requireContext(), display, Toast.LENGTH_SHORT).show();
        RouteChatItem item = RouteChatItem.assistantStatus(
                "规划失败：" + display + "\n\n请检查网络或登录状态后重试。");
        if (waitingStatusPosition >= 0 && waitingStatusPosition < adapter.getItemCount()) {
            adapter.replaceItem(waitingStatusPosition, item);
        } else {
            adapter.addItem(item);
        }
        waitingStatusPosition = -1;
        scrollChatToBottom();
    }

    private void setSending(boolean sending) {
        if (btnSend != null) {
            // 布局是圆形图标按钮，不要改文字，否则会撑破 44dp 圆按钮
            btnSend.setEnabled(!sending);
            btnSend.setAlpha(sending ? 0.45f : 1f);
            btnSend.setContentDescription(sending ? "AI规划中" : getString(R.string.route_send_desc));
        }
        if (editMessage != null) {
            editMessage.setEnabled(!sending);
            editMessage.setHint(sending ? "AI 正在规划路线…" : getString(R.string.route_input_hint));
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

        stopWaitingFeedback();
        if (walkRoutePlanner != null) {
            walkRoutePlanner.cancel();
        }

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


