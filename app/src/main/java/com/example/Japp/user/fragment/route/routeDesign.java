package com.example.Japp.user.fragment.route;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.MapView;
import com.amap.api.maps.MapsInitializer;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.services.core.AMapException;
import com.amap.api.services.core.LatLonPoint;
import com.amap.api.services.core.PoiItem;
import com.amap.api.services.poisearch.PoiResult;
import com.amap.api.services.poisearch.PoiSearch;
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

/** 地图常驻的研学路线页，支持自定义地点和 AI 路线两种模式。 */
public class routeDesign extends Fragment implements PoiSearch.OnPoiSearchListener {

    private static final String TAG = "RouteDesign";
    private static final String STATE_AI_MODE = "route_ai_mode";
    private static final LatLng DEFAULT_MAP_CENTER = new LatLng(32.0603, 118.7969);
    private static final String[] WAITING_TIPS = {
            "路线助手正在理解你的需求…",
            "正在检索合适的研学景点…",
            "正在生成行程顺序与时长…",
            "AI 规划通常需要约 1 分钟，请稍候…",
            "马上就好，正在完善路线细节…"
    };

    private MapView mainRouteMapView;
    private AMap mainMap;
    private View customRoutePanel;
    private View aiRoutePanel;
    private View welcomePanel;
    private Button btnRouteMode;
    private Button btnPlaceSearch;
    private Button btnSend;
    private EditText editPlaceSearch;
    private EditText editMessage;
    private TextView txtRouteStopCount;
    private TextView txtPlaceSearchStatus;
    private TextView txtAiRouteContext;
    private LinearLayout routeStopsContainer;
    private RecyclerView poiResultsRecyclerView;
    private RecyclerView chatRecyclerView;

    private PoiSearchAdapter poiSearchAdapter;
    private RouteChatAdapter adapter;
    private UserService service;
    private LeaderWalkRoutePlanner walkRoutePlanner;
    private LeaderWalkRoutePlanner customWalkPlanner;
    private PoiSearch poiSearch;

    private final List<RouteNode> editableRouteNodes = new ArrayList<>();
    private boolean aiMode;
    private int mapRouteRevision;

    private final Handler waitingHandler = new Handler(Looper.getMainLooper());
    private int waitingStatusPosition = -1;
    private int waitingTipIndex;
    private boolean waitingActive;

    private final Runnable waitingTipRunnable = new Runnable() {
        @Override
        public void run() {
            if (!waitingActive || !isAdded() || adapter == null || waitingStatusPosition < 0) {
                return;
            }
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

        bindViews(root);
        setupMainMap(savedInstanceState);
        setupCustomRouteEditor();
        setupAiRouteAssistant(root, savedInstanceState);

        aiMode = savedInstanceState != null && savedInstanceState.getBoolean(STATE_AI_MODE, false);
        applyMode();
        renderEditableStops();
        showDefaultMapIfNeeded();
        return root;
    }

    private void bindViews(View root) {
        mainRouteMapView = root.findViewById(R.id.mainRouteMapView);
        customRoutePanel = root.findViewById(R.id.customRoutePanel);
        aiRoutePanel = root.findViewById(R.id.aiRoutePanel);
        btnRouteMode = root.findViewById(R.id.btnRouteMode);
        btnPlaceSearch = root.findViewById(R.id.btnPlaceSearch);
        editPlaceSearch = root.findViewById(R.id.editPlaceSearch);
        txtRouteStopCount = root.findViewById(R.id.txtRouteStopCount);
        txtPlaceSearchStatus = root.findViewById(R.id.txtPlaceSearchStatus);
        txtAiRouteContext = root.findViewById(R.id.txtAiRouteContext);
        routeStopsContainer = root.findViewById(R.id.routeStopsContainer);
        poiResultsRecyclerView = root.findViewById(R.id.poiResultsRecyclerView);

        chatRecyclerView = root.findViewById(R.id.chatRecyclerView);
        editMessage = root.findViewById(R.id.editMessage);
        btnSend = root.findViewById(R.id.btnSend);
        welcomePanel = root.findViewById(R.id.welcomePanel);
    }

    private void setupMainMap(@Nullable Bundle savedInstanceState) {
        mainRouteMapView.onCreate(savedInstanceState);
        mainMap = mainRouteMapView.getMap();
        if (mainMap == null) {
            return;
        }
        mainMap.getUiSettings().setZoomControlsEnabled(false);
        mainMap.getUiSettings().setMyLocationButtonEnabled(false);
        mainMap.getUiSettings().setRotateGesturesEnabled(false);
        mainMap.getUiSettings().setTiltGesturesEnabled(false);
    }

    private void showDefaultMapIfNeeded() {
        if (mainMap != null && editableRouteNodes.isEmpty()) {
            mainMap.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_MAP_CENTER, 11f));
        }
    }

    private void setupCustomRouteEditor() {
        poiSearchAdapter = new PoiSearchAdapter(this::addPoiToRoute);
        poiResultsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        poiResultsRecyclerView.setAdapter(poiSearchAdapter);

        btnPlaceSearch.setOnClickListener(v -> searchPlaces());
        editPlaceSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchPlaces();
                return true;
            }
            return false;
        });

        btnRouteMode.setOnClickListener(v -> {
            aiMode = !aiMode;
            applyMode();
        });
    }

    private void setupAiRouteAssistant(View root, @Nullable Bundle savedInstanceState) {
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
                // 地图已经常驻在主界面，不再打开卡片内地图。
            }
        });
        chatRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        chatRecyclerView.setAdapter(adapter);
        btnSend.setOnClickListener(v -> sendRouteRequest());
        editMessage.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendRouteRequest();
                return true;
            }
            return false;
        });
        setupSuggestionChips(root);
        updateWelcomeVisibility();
    }

    private void applyMode() {
        if (customRoutePanel == null || aiRoutePanel == null || btnRouteMode == null) {
            return;
        }
        customRoutePanel.setVisibility(aiMode ? View.GONE : View.VISIBLE);
        aiRoutePanel.setVisibility(aiMode ? View.VISIBLE : View.GONE);
        btnRouteMode.setText(aiMode ? R.string.route_mode_custom : R.string.route_mode_ai);
        updateAiContextLabel();
        if (aiMode && editMessage != null) {
            editMessage.setHint(editableRouteNodes.isEmpty()
                    ? R.string.route_ai_input_hint
                    : R.string.route_ai_optimize_hint);
        }
    }

    private void searchPlaces() {
        if (editPlaceSearch == null) {
            return;
        }
        String keyword = editPlaceSearch.getText().toString().trim();
        if (TextUtils.isEmpty(keyword)) {
            txtPlaceSearchStatus.setText(R.string.route_place_search_hint);
            return;
        }

        txtPlaceSearchStatus.setVisibility(View.VISIBLE);
        txtPlaceSearchStatus.setText(R.string.route_searching);
        poiResultsRecyclerView.setVisibility(View.GONE);
        btnPlaceSearch.setEnabled(false);

        PoiSearch.Query query = new PoiSearch.Query(keyword, "", "");
        query.setPageSize(10);
        query.setPageNum(0);
        query.setExtensions(PoiSearch.EXTENSIONS_BASE);
        try {
            poiSearch = new PoiSearch(requireContext(), query);
            poiSearch.setLanguage(PoiSearch.CHINESE);
            poiSearch.setOnPoiSearchListener(this);
            poiSearch.searchPOIAsyn();
        } catch (AMapException e) {
            btnPlaceSearch.setEnabled(true);
            showPlaceSearchError(e.getErrorCode(), e.getErrorMessage());
        }
    }

    @Override
    public void onPoiSearched(PoiResult result, int errorCode) {
        if (!isAdded() || btnPlaceSearch == null) {
            return;
        }
        btnPlaceSearch.setEnabled(true);
        List<PoiItem> pois = result != null ? result.getPois() : null;
        if (errorCode != AMapException.CODE_AMAP_SUCCESS) {
            poiSearchAdapter.submitItems(null);
            poiResultsRecyclerView.setVisibility(View.GONE);
            txtPlaceSearchStatus.setVisibility(View.VISIBLE);
            showPlaceSearchError(errorCode, null);
            return;
        }
        if (pois == null || pois.isEmpty()) {
            poiSearchAdapter.submitItems(null);
            poiResultsRecyclerView.setVisibility(View.GONE);
            txtPlaceSearchStatus.setVisibility(View.VISIBLE);
            txtPlaceSearchStatus.setText(R.string.route_search_empty);
            return;
        }
        poiSearchAdapter.submitItems(pois);
        poiResultsRecyclerView.setVisibility(View.VISIBLE);
        txtPlaceSearchStatus.setVisibility(View.GONE);
    }

    @Override
    public void onPoiItemSearched(PoiItem item, int errorCode) {
        // 当前页面只使用关键词列表搜索。
    }

    private void showPlaceSearchError(int errorCode, @Nullable String sdkMessage) {
        String reason = describePlaceSearchError(errorCode);
        if (TextUtils.isEmpty(reason) && !TextUtils.isEmpty(sdkMessage)) {
            reason = sdkMessage;
        }
        if (TextUtils.isEmpty(reason)) {
            reason = getString(R.string.route_search_unknown_error);
        }
        Log.w(TAG, "AMap POI search failed, code=" + errorCode + ", reason=" + reason);
        txtPlaceSearchStatus.setVisibility(View.VISIBLE);
        txtPlaceSearchStatus.setText(getString(
                R.string.route_search_failed_with_code, reason, errorCode));
    }

    private String describePlaceSearchError(int errorCode) {
        switch (errorCode) {
            case AMapException.CODE_AMAP_SIGNATURE_ERROR:
                return "应用签名未通过，请检查 SHA1";
            case AMapException.CODE_AMAP_INVALID_USER_KEY:
                return "高德 Key 不正确或已过期";
            case AMapException.CODE_AMAP_SERVICE_NOT_AVAILBALE:
                return "POI 搜索服务不可用";
            case AMapException.CODE_AMAP_DAILY_QUERY_OVER_LIMIT:
                return "今日搜索配额已用完";
            case AMapException.CODE_AMAP_ACCESS_TOO_FREQUENT:
                return "搜索过于频繁，请稍后重试";
            case AMapException.CODE_AMAP_INVALID_USER_SCODE:
                return "安全码校验失败，请检查 SHA1";
            case AMapException.CODE_AMAP_USERKEY_PLAT_NOMATCH:
                return "Key 平台不匹配，需要 Android Key";
            case AMapException.CODE_AMAP_INSUFFICIENT_PRIVILEGES:
                return "Key 未开通搜索权限";
            case AMapException.CODE_AMAP_USER_KEY_RECYCLED:
                return "高德 Key 已被删除";
            case AMapException.CODE_AMAP_ENGINE_CONNECT_TIMEOUT:
            case AMapException.CODE_AMAP_ENGINE_RETURN_TIMEOUT:
            case AMapException.CODE_AMAP_CLIENT_SOCKET_TIMEOUT_EXCEPTION:
                return "连接高德服务超时";
            case AMapException.CODE_AMAP_CLIENT_UNKNOWHOST_EXCEPTION:
                return "无法解析高德服务地址，请检查网络";
            case AMapException.CODE_AMAP_CLIENT_NETWORK_EXCEPTION:
                return "网络连接失败";
            case AMapException.CODE_AMAP_SERVICE_INVALID_PARAMS:
            case AMapException.CODE_AMAP_CLIENT_INVALID_PARAMETER:
                return "搜索参数无效";
            case AMapException.CODE_AMAP_SERVICE_MAINTENANCE:
                return "高德搜索服务维护中";
            default:
                return null;
        }
    }

    private void addPoiToRoute(@NonNull PoiItem poi) {
        LatLonPoint point = poi.getLatLonPoint();
        if (point == null) {
            Toast.makeText(requireContext(), "该地点缺少坐标，无法加入路线", Toast.LENGTH_SHORT).show();
            return;
        }
        if (containsPoi(poi)) {
            Toast.makeText(requireContext(), "该地点已在路线中", Toast.LENGTH_SHORT).show();
            return;
        }

        RouteNode node = new RouteNode();
        node.setVisitOrder(editableRouteNodes.size() + 1);
        node.setPoiId(poi.getPoiId());
        node.setName(TextUtils.isEmpty(poi.getTitle()) ? "未命名地点" : poi.getTitle());
        node.setAddress(poi.getSnippet());
        node.setCityname(poi.getCityName());
        node.setRecommendedDuration(60);
        node.setLocation(point.getLongitude() + "," + point.getLatitude());
        editableRouteNodes.add(node);

        editPlaceSearch.setText("");
        poiSearchAdapter.submitItems(null);
        poiResultsRecyclerView.setVisibility(View.GONE);
        txtPlaceSearchStatus.setVisibility(View.VISIBLE);
        txtPlaceSearchStatus.setText(R.string.route_search_default_tip);
        renderEditableStops();
        updateMapFromEditableRoute(true);
    }

    private boolean containsPoi(PoiItem poi) {
        LatLonPoint candidate = poi.getLatLonPoint();
        for (RouteNode node : editableRouteNodes) {
            if (!TextUtils.isEmpty(poi.getPoiId()) && poi.getPoiId().equals(node.getPoiId())) {
                return true;
            }
            LatLng point = RouteMapDrawHelper.parseLocation(node.getLocation());
            if (point != null && candidate != null
                    && Math.abs(point.latitude - candidate.getLatitude()) < 0.000001
                    && Math.abs(point.longitude - candidate.getLongitude()) < 0.000001) {
                return true;
            }
        }
        return false;
    }

    private void renderEditableStops() {
        if (routeStopsContainer == null || txtRouteStopCount == null) {
            return;
        }
        routeStopsContainer.removeAllViews();
        txtRouteStopCount.setText(getString(R.string.route_stop_count_format, editableRouteNodes.size()));

        if (editableRouteNodes.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText(R.string.route_empty_stops);
            empty.setTextColor(ContextCompat.getColor(requireContext(), R.color.route_text_secondary));
            empty.setTextSize(12f);
            empty.setPadding(4, 6, 4, 6);
            routeStopsContainer.addView(empty);
            updateAiContextLabel();
            return;
        }

        for (int i = 0; i < editableRouteNodes.size(); i++) {
            final int position = i;
            RouteNode node = editableRouteNodes.get(i);
            Chip chip = new Chip(requireContext());
            chip.setText(getString(R.string.route_stop_chip_format, i + 1, node.getName()));
            chip.setTextSize(12f);
            chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.route_chip_text));
            chip.setChipBackgroundColor(ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.route_chip_bg)));
            chip.setChipStrokeColor(ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.route_chip_stroke)));
            chip.setChipStrokeWidth(1f);
            chip.setCloseIconVisible(true);
            chip.setCloseIconTint(ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.route_primary)));
            chip.setOnCloseIconClickListener(v -> removeRouteStop(position));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd(8);
            routeStopsContainer.addView(chip, params);
        }
        updateAiContextLabel();
    }

    private void removeRouteStop(int position) {
        if (position < 0 || position >= editableRouteNodes.size()) {
            return;
        }
        editableRouteNodes.remove(position);
        renumberRouteNodes();
        renderEditableStops();
        updateMapFromEditableRoute(true);
    }

    private void renumberRouteNodes() {
        for (int i = 0; i < editableRouteNodes.size(); i++) {
            editableRouteNodes.get(i).setVisitOrder(i + 1);
        }
    }

    private void updateMapFromEditableRoute(boolean planRoad) {
        mapRouteRevision++;
        int revision = mapRouteRevision;
        List<LatLng> points = RouteMapDrawHelper.extractPointsFromNodes(editableRouteNodes);
        if (mainMap == null) {
            return;
        }
        if (points.isEmpty()) {
            mainMap.clear();
            showDefaultMapIfNeeded();
            return;
        }
        if (points.size() == 1) {
            mainMap.clear();
            mainMap.addMarker(new MarkerOptions()
                    .position(points.get(0))
                    .title(editableRouteNodes.get(0).getName()));
            mainMap.animateCamera(CameraUpdateFactory.newLatLngZoom(points.get(0), 15f));
            return;
        }

        RouteMapDrawHelper.drawRoute(mainMap, points, points);
        if (!planRoad) {
            return;
        }
        if (customWalkPlanner != null) {
            customWalkPlanner.cancel();
        }
        customWalkPlanner = new LeaderWalkRoutePlanner(requireContext());
        List<RouteNode> snapshot = new ArrayList<>(editableRouteNodes);
        customWalkPlanner.planSummary(snapshot, new LeaderWalkRoutePlanner.Callback() {
            @Override
            public void onPlanningStarted() {
                // 已先绘制站点直连线，真实道路结果返回后再替换。
            }

            @Override
            public void onPlanningFinished(@NonNull String summary,
                                           @NonNull ArrayList<String> instructions,
                                           boolean hadFailures) {
                // 使用带道路折线的重载。
            }

            @Override
            public void onPlanningFinished(@NonNull String summary,
                                           @NonNull ArrayList<String> instructions,
                                           @NonNull List<LatLng> roadPolyline,
                                           boolean hadFailures) {
                if (!isAdded() || mainMap == null || revision != mapRouteRevision) {
                    return;
                }
                RouteMapDrawHelper.drawRoute(mainMap, roadPolyline, points);
            }

            @Override
            public void onPlanningFailed(@NonNull String message) {
                // 保留已经绘制的站点直连线。
            }
        });
    }

    private void updateAiContextLabel() {
        if (txtAiRouteContext == null) {
            return;
        }
        if (editableRouteNodes.isEmpty()) {
            txtAiRouteContext.setText(R.string.route_ai_new_route);
        } else {
            txtAiRouteContext.setText(getString(
                    R.string.route_ai_existing_format, editableRouteNodes.size()));
        }
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
        String requestText = buildAiRequestText(text);
        service.planRouteByAi(memoryId, requestText).enqueue(new Callback<Result<JsonElement>>() {
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

    private String buildAiRequestText(String userText) {
        if (editableRouteNodes.isEmpty()) {
            return userText;
        }
        StringBuilder context = new StringBuilder("当前地图路线依次包含：");
        for (int i = 0; i < editableRouteNodes.size(); i++) {
            if (i > 0) {
                context.append(" → ");
            }
            context.append(editableRouteNodes.get(i).getName());
        }
        context.append("。请基于这条已有路线处理用户要求：").append(userText);
        return context.toString();
    }

    private void loadRouteAndShow(int routeId, String userRequirement) {
        service.getRouteNodes(routeId).enqueue(new Callback<Result<List<RouteNode>>>() {
            @Override
            public void onResponse(Call<Result<List<RouteNode>>> call,
                                   Response<Result<List<RouteNode>>> response) {
                if (!isAdded() || adapter == null) {
                    return;
                }
                List<RouteNode> nodes = null;
                if (response.isSuccessful() && response.body() != null
                        && response.body().getCode() == 1) {
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
                    finishWithRoute(userRequirement, orderedNodes, routeId,
                            waypoints, waypoints, null);
                    return;
                }

                walkRoutePlanner.planSummary(orderedNodes, new LeaderWalkRoutePlanner.Callback() {
                    @Override
                    public void onPlanningStarted() {
                    }

                    @Override
                    public void onPlanningFinished(@NonNull String summary,
                                                   @NonNull ArrayList<String> instructions,
                                                   boolean hadFailures) {
                    }

                    @Override
                    public void onPlanningFinished(@NonNull String summary,
                                                   @NonNull ArrayList<String> instructions,
                                                   @NonNull List<LatLng> roadPolyline,
                                                   boolean hadFailures) {
                        if (!isAdded()) {
                            return;
                        }
                        List<LatLng> road = roadPolyline.size() >= 2 ? roadPolyline : waypoints;
                        finishWithRoute(userRequirement, orderedNodes, routeId,
                                road, waypoints, summary);
                    }

                    @Override
                    public void onPlanningFailed(@NonNull String message) {
                        if (isAdded()) {
                            finishWithRoute(userRequirement, orderedNodes, routeId,
                                    waypoints, waypoints, null);
                        }
                    }
                });
            }

            @Override
            public void onFailure(Call<Result<List<RouteNode>>> call, Throwable t) {
                if (!isAdded() || adapter == null) {
                    return;
                }
                String fallbackText = "路线已生成（ID: " + routeId
                        + "），但节点加载失败，请稍后重试发布。";
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
                description += "\n\n步行参考：" + walkSummary;
            }
            editableRouteNodes.clear();
            editableRouteNodes.addAll(nodes);
            renumberRouteNodes();
            renderEditableStops();
        } else {
            description = userRequirementOrText;
        }

        List<LatLng> road = roadPolyline.size() >= 2 ? roadPolyline : waypoints;
        List<LatLng> marks = !waypoints.isEmpty() ? waypoints : road;
        mapRouteRevision++;
        if (mainMap != null && !road.isEmpty()) {
            if (road.size() >= 2) {
                RouteMapDrawHelper.drawRoute(mainMap, road, marks);
            } else {
                mainMap.clear();
                mainMap.addMarker(new MarkerOptions().position(road.get(0)));
                mainMap.animateCamera(CameraUpdateFactory.newLatLngZoom(road.get(0), 15f));
            }
        }

        RouteChatItem item = RouteChatItem.assistantRoute(description, road, marks, routeId);
        int statusPos = waitingStatusPosition;
        if (statusPos >= 0 && statusPos < adapter.getItemCount()) {
            adapter.replaceItem(statusPos, item);
        } else {
            adapter.addItem(item);
        }
        waitingStatusPosition = -1;
        scrollChatToBottom();
    }

    private String buildDescription(String requirement, List<RouteNode> nodes, int routeId) {
        StringBuilder sb = new StringBuilder();
        sb.append("已根据「").append(requirement).append("」更新地图路线：\n\n");
        if (nodes != null && !nodes.isEmpty()) {
            int totalMin = ProjectUiHelper.sumDurationMinutes(nodes);
            sb.append("• 预计总时长：约 ").append(ProjectUiHelper.formatDuration(totalMin)).append("\n");
            sb.append("• ").append(ProjectUiHelper.buildRouteSummary(nodes)).append("\n");
        }
        sb.append("• 路线编号：").append(routeId).append("\n\n");
        sb.append("可切回「自定义」继续增删地点，或直接发布拼单。");
        return sb.toString();
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
        stopWaitingFeedback();
        adapter.updateItemText(waitingStatusPosition, text);
        scrollChatToBottom();
    }

    private void stopWaitingFeedback() {
        waitingActive = false;
        waitingHandler.removeCallbacks(waitingTipRunnable);
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
        String departureDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(calendar.getTime());
        String title = item.getText().length() > 20
                ? item.getText().substring(0, 20) + "…"
                : item.getText();
        if (title.trim().isEmpty()) {
            title = "研学拼单";
        }

        CreateProjectRequest request = new CreateProjectRequest(
                item.getRouteId(), title, departureDate, 10, 1, "OPEN");
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
                if (response.isSuccessful() && response.body() != null
                        && response.body().getCode() == 1) {
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
            btnSend.setEnabled(!sending);
            btnSend.setAlpha(sending ? 0.45f : 1f);
            btnSend.setContentDescription(sending ? "AI规划中" : getString(R.string.route_send_desc));
        }
        if (editMessage != null) {
            editMessage.setEnabled(!sending);
            if (sending) {
                editMessage.setHint("AI 正在规划路线…");
            } else {
                editMessage.setHint(editableRouteNodes.isEmpty()
                        ? R.string.route_ai_input_hint
                        : R.string.route_ai_optimize_hint);
            }
        }
    }

    private void scrollChatToBottom() {
        if (chatRecyclerView != null && adapter != null && adapter.getItemCount() > 0) {
            chatRecyclerView.scrollToPosition(adapter.getItemCount() - 1);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_AI_MODE, aiMode);
        if (mainRouteMapView != null) {
            mainRouteMapView.onSaveInstanceState(outState);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mainRouteMapView != null) {
            mainRouteMapView.onResume();
        }
        if (adapter != null) {
            adapter.onHostResume();
        }
    }

    @Override
    public void onPause() {
        if (mainRouteMapView != null) {
            mainRouteMapView.onPause();
        }
        if (adapter != null) {
            adapter.onHostPause();
        }
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        stopWaitingFeedback();
        if (walkRoutePlanner != null) {
            walkRoutePlanner.cancel();
        }
        if (customWalkPlanner != null) {
            customWalkPlanner.cancel();
        }
        if (adapter != null) {
            adapter.onHostDestroy();
        }
        if (mainRouteMapView != null) {
            mainRouteMapView.onDestroy();
        }
        mainRouteMapView = null;
        mainMap = null;
        chatRecyclerView = null;
        poiResultsRecyclerView = null;
        editMessage = null;
        editPlaceSearch = null;
        btnSend = null;
        btnPlaceSearch = null;
        btnRouteMode = null;
        super.onDestroyView();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mainRouteMapView != null) {
            mainRouteMapView.onLowMemory();
        }
        if (adapter != null) {
            adapter.onHostLowMemory();
        }
    }
}
