package com.example.Japp.user.fragment.route;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
import com.amap.api.maps.model.MyLocationStyle;
import com.amap.api.services.core.LatLonPoint;
import com.amap.api.services.core.PoiItem;
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
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.card.MaterialCardView;
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

/** 地图常驻的研学路线页，自定义地点与 AI 对话在同一张底卡中协作。 */
public class routeDesign extends Fragment {

    private static final String TAG = "RouteDesign";
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
    private MaterialCardView customRoutePanel;
    private MaterialCardView routeStopsCard;
    private View chatArea;
    private BottomSheetBehavior<MaterialCardView> routeSheetBehavior;
    private View welcomePanel;
    private Button btnTogglePlaceSearch;
    private Button btnSend;
    private EditText editMessage;
    private TextView txtRouteStopCount;
    private TextView txtAiRouteContext;
    private TextView txtCurrentLocationStatus;
    private LinearLayout routeStopsContainer;
    private View currentLocationRow;
    private View btnMyLocation;
    private RecyclerView chatRecyclerView;

    private RouteChatAdapter adapter;
    private UserService service;
    private LeaderWalkRoutePlanner walkRoutePlanner;
    private LeaderWalkRoutePlanner customWalkPlanner;

    private final List<RouteNode> editableRouteNodes = new ArrayList<>();
    private LatLng currentLocation;
    private boolean locationCameraCentered;
    private int mapRouteRevision;

    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (!isAdded()) {
                    return;
                }
                if (hasLocationPermission()) {
                    enableMyLocation(true);
                } else if (txtCurrentLocationStatus != null) {
                    txtCurrentLocationStatus.setText(R.string.route_location_permission_needed);
                }
            });

    private final ActivityResultLauncher<Intent> placeSearchLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (!isAdded() || result.getResultCode() != Activity.RESULT_OK
                        || result.getData() == null) {
                    return;
                }
                Intent data = result.getData();
                double lat = data.getDoubleExtra(PlaceSearchActivity.EXTRA_LAT, 0d);
                double lng = data.getDoubleExtra(PlaceSearchActivity.EXTRA_LNG, 0d);
                if (lat == 0d || lng == 0d) {
                    Toast.makeText(requireContext(), "该地点缺少坐标，无法加入路线", Toast.LENGTH_SHORT).show();
                    return;
                }
                PoiItem item = new PoiItem(
                        data.getStringExtra(PlaceSearchActivity.EXTRA_POI_ID),
                        new LatLonPoint(lat, lng),
                        data.getStringExtra(PlaceSearchActivity.EXTRA_NAME),
                        data.getStringExtra(PlaceSearchActivity.EXTRA_ADDRESS));
                item.setCityName(data.getStringExtra(PlaceSearchActivity.EXTRA_CITY));
                addPoiToRoute(item);
            });

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
        setupRouteBottomSheet(root);
        setupMainMap(savedInstanceState);
        setupCustomRouteEditor();
        setupAiRouteAssistant(root, savedInstanceState);

        renderEditableStops();
        showDefaultMapIfNeeded();
        requestOrEnableLocation();
        return root;
    }

    private void bindViews(View root) {
        mainRouteMapView = root.findViewById(R.id.mainRouteMapView);
        customRoutePanel = root.findViewById(R.id.customRoutePanel);
        routeStopsCard = root.findViewById(R.id.routeStopsCard);
        chatArea = root.findViewById(R.id.chatArea);
        btnTogglePlaceSearch = root.findViewById(R.id.btnTogglePlaceSearch);
        txtRouteStopCount = root.findViewById(R.id.txtRouteStopCount);
        txtAiRouteContext = root.findViewById(R.id.txtAiRouteContext);
        txtCurrentLocationStatus = root.findViewById(R.id.txtCurrentLocationStatus);
        routeStopsContainer = root.findViewById(R.id.routeStopsContainer);
        currentLocationRow = root.findViewById(R.id.currentLocationRow);
        btnMyLocation = root.findViewById(R.id.btnMyLocation);
        chatRecyclerView = root.findViewById(R.id.chatRecyclerView);
        editMessage = root.findViewById(R.id.editMessage);
        btnSend = root.findViewById(R.id.btnSend);
        welcomePanel = root.findViewById(R.id.welcomePanel);
    }

    private void setupRouteBottomSheet(@NonNull View root) {
        routeSheetBehavior = BottomSheetBehavior.from(customRoutePanel);
        routeSheetBehavior.setHideable(false);
        routeSheetBehavior.setDraggable(true);
        routeSheetBehavior.setFitToContents(false);
        routeSheetBehavior.setExpandedOffset(0);
        routeSheetBehavior.setPeekHeight(dpToPx(312));

        routeSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    updateSheetContentHeight(1f);
                    customRoutePanel.setRadius(0f);
                } else if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    updateSheetContentHeight(0f);
                    customRoutePanel.setRadius(dpToPx(20));
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                updateSheetContentHeight(Math.max(0f, Math.min(1f, slideOffset)));
            }
        });

        View handle = root.findViewById(R.id.routeSheetHandle);
        handle.setOnClickListener(v -> {
            int targetState = routeSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED
                    ? BottomSheetBehavior.STATE_COLLAPSED
                    : BottomSheetBehavior.STATE_EXPANDED;
            routeSheetBehavior.setState(targetState);
        });
        customRoutePanel.post(() -> {
            if (routeSheetBehavior != null) {
                routeSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                updateSheetContentHeight(0f);
            }
        });
    }

    private void updateSheetContentHeight(float expansion) {
        if (customRoutePanel == null || routeStopsCard == null || chatArea == null
                || customRoutePanel.getHeight() <= 0) {
            return;
        }
        int collapsedStopsHeight = dpToPx(96);
        int collapsedChatHeight = dpToPx(62);
        int fixedContentHeight = dpToPx(140);
        int expandedSectionHeight = Math.max(collapsedStopsHeight,
                (customRoutePanel.getHeight() - fixedContentHeight) / 2);

        int stopsHeight = collapsedStopsHeight
                + Math.round((expandedSectionHeight - collapsedStopsHeight) * expansion);
        int chatHeight = collapsedChatHeight
                + Math.round((expandedSectionHeight - collapsedChatHeight) * expansion);
        setViewHeight(routeStopsCard, stopsHeight);
        setViewHeight(chatArea, chatHeight);
    }

    private void setViewHeight(@NonNull View view, int targetHeight) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params.height != targetHeight) {
            params.height = targetHeight;
            view.setLayoutParams(params);
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
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
        mainMap.setMapType(AMap.MAP_TYPE_NORMAL);
        mainMap.setOnMapLoadedListener(() -> {
            if (editableRouteNodes.isEmpty() && !locationCameraCentered) {
                showDefaultMapIfNeeded();
            }
        });
        mainMap.setOnMyLocationChangeListener(this::onMyLocationChanged);

        if (currentLocationRow != null) {
            currentLocationRow.setOnClickListener(v -> centerOnMyLocation());
        }
        if (btnMyLocation != null) {
            btnMyLocation.setOnClickListener(v -> centerOnMyLocation());
        }
    }

    private void requestOrEnableLocation() {
        if (hasLocationPermission()) {
            enableMyLocation(false);
            return;
        }
        if (txtCurrentLocationStatus != null) {
            txtCurrentLocationStatus.setText(R.string.route_location_permission_needed);
        }
        locationPermissionLauncher.launch(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        });
    }

    private boolean hasLocationPermission() {
        if (!isAdded()) {
            return false;
        }
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void enableMyLocation(boolean centerWhenReady) {
        if (mainMap == null || !hasLocationPermission()) {
            return;
        }
        locationCameraCentered = !centerWhenReady && locationCameraCentered;
        MyLocationStyle style = new MyLocationStyle()
                .myLocationType(MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE_NO_CENTER)
                .interval(2000L)
                .strokeColor(Color.WHITE)
                .strokeWidth(2f)
                .radiusFillColor(0x221677FF);
        mainMap.setMyLocationStyle(style);
        try {
            mainMap.setMyLocationEnabled(true);
            if (txtCurrentLocationStatus != null) {
                txtCurrentLocationStatus.setText(R.string.route_location_locating);
            }
        } catch (SecurityException e) {
            Log.w(TAG, "Location permission was revoked while enabling map location", e);
        }
    }

    private void onMyLocationChanged(@Nullable Location location) {
        if (!isAdded() || location == null
                || location.getLatitude() == 0d || location.getLongitude() == 0d) {
            return;
        }
        LatLng candidate = new LatLng(location.getLatitude(), location.getLongitude());
        if (!isInsideAmapServiceRegion(candidate)) {
            currentLocation = null;
            locationCameraCentered = false;
            if (txtCurrentLocationStatus != null) {
                txtCurrentLocationStatus.setText(R.string.route_location_outside_service);
            }
            showDefaultMapIfNeeded();
            return;
        }
        boolean firstFix = currentLocation == null;
        currentLocation = candidate;
        if (txtCurrentLocationStatus != null) {
            txtCurrentLocationStatus.setText(R.string.route_location_ready);
        }
        if (firstFix && !editableRouteNodes.isEmpty()) {
            updateMapFromEditableRoute(true);
        } else if (!locationCameraCentered && editableRouteNodes.isEmpty()) {
            centerOnMyLocation();
        }
    }

    /** 高德国内底图的稳定覆盖范围；避免模拟器默认境外坐标把镜头带到空白区域。 */
    private boolean isInsideAmapServiceRegion(@NonNull LatLng point) {
        return point.latitude >= 3.0 && point.latitude <= 54.0
                && point.longitude >= 73.0 && point.longitude <= 136.0;
    }

    private void centerOnMyLocation() {
        if (!hasLocationPermission()) {
            requestOrEnableLocation();
            return;
        }
        if (currentLocation == null || !isInsideAmapServiceRegion(currentLocation)) {
            enableMyLocation(true);
            showDefaultMapIfNeeded();
            Toast.makeText(requireContext(), "正在获取可用的当前位置…", Toast.LENGTH_SHORT).show();
            return;
        }
        locationCameraCentered = true;
        mainMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 16f));
    }

    private void showDefaultMapIfNeeded() {
        if (mainMap != null && editableRouteNodes.isEmpty()) {
            if (currentLocation != null) {
                mainMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 16f));
            } else {
                mainMap.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_MAP_CENTER, 11f));
            }
        }
    }

    private void setupCustomRouteEditor() {
        btnTogglePlaceSearch.setOnClickListener(v -> launchPlaceSearch());
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

    private void launchPlaceSearch() {
        Intent intent = new Intent(requireContext(), PlaceSearchActivity.class);
        if (currentLocation != null && isInsideAmapServiceRegion(currentLocation)) {
            intent.putExtra(PlaceSearchActivity.EXTRA_ORIGIN_LAT, currentLocation.latitude);
            intent.putExtra(PlaceSearchActivity.EXTRA_ORIGIN_LNG, currentLocation.longitude);
        }
        placeSearchLauncher.launch(intent);
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
            TextView addDestination = new TextView(requireContext());
            addDestination.setText(R.string.route_empty_stops);
            addDestination.setTextColor(ContextCompat.getColor(
                    requireContext(), R.color.route_text_secondary));
            addDestination.setTextSize(12f);
            addDestination.setGravity(Gravity.CENTER_VERTICAL);
            addDestination.setPadding(dp(4), 0, dp(4), 0);
            routeStopsContainer.addView(addDestination, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));
            updateAiContextLabel();
            return;
        }

        for (int i = 0; i < editableRouteNodes.size(); i++) {
            final int position = i;
            RouteNode node = editableRouteNodes.get(i);
            if (i > 0) {
                View divider = new View(requireContext());
                divider.setBackgroundColor(ContextCompat.getColor(
                        requireContext(), R.color.route_card_stroke));
                LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
                dividerParams.setMarginStart(dp(29));
                routeStopsContainer.addView(divider, dividerParams);
            }

            LinearLayout row = new LinearLayout(requireContext());
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(dp(4), 0, dp(2), 0);

            TextView number = new TextView(requireContext());
            number.setGravity(Gravity.CENTER);
            number.setText(String.valueOf(i + 1));
            number.setTextColor(ContextCompat.getColor(requireContext(), R.color.route_primary));
            number.setTextSize(10f);
            number.setBackgroundResource(R.drawable.bg_route_stop_number);
            row.addView(number, new LinearLayout.LayoutParams(dp(20), dp(20)));

            TextView name = new TextView(requireContext());
            name.setText(node.getName());
            name.setTextColor(ContextCompat.getColor(requireContext(), R.color.route_text_primary));
            name.setTextSize(14f);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            nameParams.setMarginStart(dp(12));
            row.addView(name, nameParams);

            TextView remove = new TextView(requireContext());
            remove.setText("×");
            remove.setTextColor(ContextCompat.getColor(requireContext(), R.color.route_text_secondary));
            remove.setTextSize(22f);
            remove.setGravity(Gravity.CENTER);
            remove.setContentDescription("移除" + node.getName());
            remove.setOnClickListener(v -> removeRouteStop(position));
            row.addView(remove, new LinearLayout.LayoutParams(dp(36), dp(42)));

            LatLng point = RouteMapDrawHelper.parseLocation(node.getLocation());
            if (point != null) {
                row.setOnClickListener(v -> mainMap.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(point, 16f)));
            }
            routeStopsContainer.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
        }
        updateAiContextLabel();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
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
        if (mainMap == null) {
            return;
        }
        if (editableRouteNodes.isEmpty()) {
            mainMap.clear();
            showDefaultMapIfNeeded();
            return;
        }
        List<RouteNode> planningNodes = buildCustomPlanningNodes();
        List<LatLng> points = RouteMapDrawHelper.extractPointsFromNodes(planningNodes);
        boolean hasCurrentOrigin = currentLocation != null;
        if (points.size() == 1) {
            mainMap.clear();
            mainMap.addMarker(new MarkerOptions()
                    .position(points.get(0))
                    .title(editableRouteNodes.get(0).getName()));
            mainMap.animateCamera(CameraUpdateFactory.newLatLngZoom(points.get(0), 15f));
            return;
        }

        RouteMapDrawHelper.drawRoute(mainMap, points, points, !hasCurrentOrigin);
        if (!planRoad) {
            return;
        }
        if (customWalkPlanner != null) {
            customWalkPlanner.cancel();
        }
        customWalkPlanner = new LeaderWalkRoutePlanner(requireContext());
        List<RouteNode> snapshot = new ArrayList<>(planningNodes);
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
                RouteMapDrawHelper.drawRoute(
                        mainMap, roadPolyline, points, !hasCurrentOrigin);
            }

            @Override
            public void onPlanningFailed(@NonNull String message) {
                // 保留已经绘制的站点直连线。
            }
        });
    }

    @NonNull
    private List<RouteNode> buildCustomPlanningNodes() {
        List<RouteNode> nodes = new ArrayList<>();
        if (currentLocation != null) {
            RouteNode origin = new RouteNode();
            origin.setVisitOrder(0);
            origin.setName(getString(R.string.route_my_location));
            origin.setRecommendedDuration(0);
            origin.setLocation(currentLocation.longitude + "," + currentLocation.latitude);
            nodes.add(origin);
        }
        nodes.addAll(editableRouteNodes);
        return nodes;
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
            if (mainMap != null) {
                mainMap.setMyLocationEnabled(false);
                mainMap.setOnMyLocationChangeListener(null);
            }
            mainRouteMapView.onDestroy();
        }
        mainRouteMapView = null;
        mainMap = null;
        routeSheetBehavior = null;
        customRoutePanel = null;
        routeStopsCard = null;
        chatArea = null;
        chatRecyclerView = null;
        editMessage = null;
        txtCurrentLocationStatus = null;
        currentLocationRow = null;
        btnMyLocation = null;
        btnSend = null;
        btnTogglePlaceSearch = null;
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
