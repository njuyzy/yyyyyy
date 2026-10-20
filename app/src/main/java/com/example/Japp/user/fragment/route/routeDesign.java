package com.example.Japp.user.fragment.route;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.DragEvent;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.MapView;
import com.amap.api.maps.MapsInitializer;
import com.amap.api.maps.model.BitmapDescriptor;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.MyLocationStyle;
import com.amap.api.maps.model.PolylineOptions;
import com.amap.api.services.core.AMapException;
import com.amap.api.services.core.LatLonPoint;
import com.amap.api.services.core.PoiItem;
import com.amap.api.services.poisearch.Photo;
import com.amap.api.services.poisearch.PoiResult;
import com.amap.api.services.poisearch.PoiSearch;
import com.example.Japp.R;
import com.example.Japp.leader.LeaderWalkRoutePlanner;
import com.example.Japp.network.ApiClient;
import com.example.Japp.network.api.UserService;
import com.example.Japp.network.models.Result;
import com.example.Japp.network.models.RouteNode;
import com.example.Japp.network.models.RouteAgent;
import com.example.Japp.user.util.RouteAgentHelper;
import com.example.Japp.user.RoutePublishDetailActivity;
import com.example.Japp.user.util.ProjectUiHelper;
import com.example.Japp.user.util.RoutePlanHelper;
import com.example.Japp.user.util.SessionHelper;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.JsonElement;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** 地图常驻的研学路线页，自定义地点与 AI 对话在同一张底卡中协作。 */
public class routeDesign extends Fragment {

    private static final String TAG = "RouteDesign";
    private static final String STATE_AI_ROUTE_MEMORY_ID = "ai_route_memory_id";
    private static final LatLng DEFAULT_MAP_CENTER = new LatLng(32.0603, 118.7969);

    private MapView mainRouteMapView;
    private AMap mainMap;
    private MaterialCardView customRoutePanel;
    private MaterialCardView routeStopsCard;
    private View chatArea;
    private BottomSheetBehavior<MaterialCardView> routeSheetBehavior;
    private View welcomePanel;
    private Button btnTogglePlaceSearch;
    private Button btnSaveRoute;
    private Button btnPublishRoute;
    private Button btnSend;
    private EditText editMessage;
    private TextView txtRouteStopCount;
    private TextView txtCurrentLocationStatus;
    private Chip chipRouteContext;
    private Chip chipNanjing;
    private Chip chipBeijing;
    private Chip chipSuzhou;
    private Chip chipShanghai;
    private Chip chipNewConversation;
    private LinearLayout routeStopsContainer;
    private NestedScrollView routeStopsScrollView;
    private View currentLocationRow;
    private View btnMyLocation;
    private RecyclerView chatRecyclerView;

    private RouteChatAdapter adapter;
    private UserService service;
    private RouteAgentCoordinator agent;
    private Call<Result<JsonElement>> poiSyncCall;
    private LeaderWalkRoutePlanner customWalkPlanner;

    private final List<RouteNode> editableRouteNodes = new ArrayList<>();
    private LatLng currentLocation;
    private boolean locationCameraCentered;
    private int mapRouteRevision;
    private String aiRouteMemoryId;
    private int publishableRouteId;
    private String publishableRouteSummary;
    private BitmapDescriptor myLocationDescriptor;
    private final Map<String, Bitmap> markerPhotoCache = new ConcurrentHashMap<>();
    private final Set<String> markerPhotoRequests = ConcurrentHashMap.newKeySet();
    private final Set<String> markerPoiDetailRequests = ConcurrentHashMap.newKeySet();
    private final Map<String, PoiSearch> markerPoiSearches = new ConcurrentHashMap<>();
    private final ExecutorService markerPhotoExecutor = Executors.newFixedThreadPool(2);

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
                addPoiToRoute(
                        item,
                        data.getStringExtra(PlaceSearchActivity.EXTRA_PHOTO_URL),
                        data.getStringExtra(PlaceSearchActivity.EXTRA_ADCODE),
                        data.getStringExtra(PlaceSearchActivity.EXTRA_CITYCODE));
            });

    private final Handler waitingHandler = new Handler(Looper.getMainLooper());
    private int waitingStatusPosition = -1;
    private boolean routeRequestInFlight;


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            aiRouteMemoryId = savedInstanceState.getString(STATE_AI_ROUTE_MEMORY_ID);
        }
        MapsInitializer.updatePrivacyShow(requireContext(), true, true);
        MapsInitializer.updatePrivacyAgree(requireContext(), true);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.user_fragment_route_design, container, false);
        service = ApiClient.getClient().create(UserService.class);

        bindViews(root);
        setupRouteBottomSheet(root);
        setupMainMap(savedInstanceState);
        setupCustomRouteEditor();
        setupAiRouteAssistant(root, savedInstanceState);

        boolean restoredDraft = restoreLocalDraft();
        adapter.setDataChangedListener(this::persistLocalDraft);
        setupAgent();
        renderEditableStops();
        if (restoredDraft && !editableRouteNodes.isEmpty()) {
            updateMapFromEditableRoute(true);
        } else {
            showDefaultMapIfNeeded();
        }
        requestOrEnableLocation();
        return root;
    }

    private void bindViews(View root) {
        mainRouteMapView = root.findViewById(R.id.mainRouteMapView);
        customRoutePanel = root.findViewById(R.id.customRoutePanel);
        routeStopsCard = root.findViewById(R.id.routeStopsCard);
        chatArea = root.findViewById(R.id.chatArea);
        btnTogglePlaceSearch = root.findViewById(R.id.btnTogglePlaceSearch);
        btnSaveRoute = root.findViewById(R.id.btnSaveRoute);
        btnSaveRoute.setEnabled(false);
        btnPublishRoute = root.findViewById(R.id.btnPublishRoute);
        btnPublishRoute.setEnabled(false);
        txtRouteStopCount = root.findViewById(R.id.txtRouteStopCount);
        txtCurrentLocationStatus = root.findViewById(R.id.txtCurrentLocationStatus);
        routeStopsContainer = root.findViewById(R.id.routeStopsContainer);
        routeStopsScrollView = root.findViewById(R.id.routeStopsScrollView);
        currentLocationRow = root.findViewById(R.id.currentLocationRow);
        btnMyLocation = root.findViewById(R.id.btnMyLocation);
        chatRecyclerView = root.findViewById(R.id.chatRecyclerView);
        editMessage = root.findViewById(R.id.editMessage);
        btnSend = root.findViewById(R.id.btnSend);
        welcomePanel = root.findViewById(R.id.welcomePanel);
        chipRouteContext = root.findViewById(R.id.chipRouteContext);
        chipNanjing = root.findViewById(R.id.chipNanjing);
        chipBeijing = root.findViewById(R.id.chipBeijing);
        chipSuzhou = root.findViewById(R.id.chipSuzhou);
        chipShanghai = root.findViewById(R.id.chipShanghai);
        chipNewConversation = root.findViewById(R.id.chipNewConversation);
        setupRouteStopDragScrolling();
    }

    private void setupRouteBottomSheet(@NonNull View root) {
        routeSheetBehavior = BottomSheetBehavior.from(customRoutePanel);
        routeSheetBehavior.setHideable(false);
        routeSheetBehavior.setDraggable(true);
        routeSheetBehavior.setFitToContents(false);
        routeSheetBehavior.setExpandedOffset(0);
        routeSheetBehavior.setHalfExpandedRatio(0.58f);
        routeSheetBehavior.setPeekHeight(dpToPx(370));

        routeSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    updateSheetContentHeight(1f);
                    customRoutePanel.setRadius(0f);
                } else if (newState == BottomSheetBehavior.STATE_HALF_EXPANDED) {
                    customRoutePanel.setRadius(dpToPx(20));
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
        setupChatScrollTouchGuard();
    }

    private void setupChatScrollTouchGuard() {
        if (chatRecyclerView == null) {
            return;
        }
        chatRecyclerView.setOnTouchListener((view, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                view.getParent().requestDisallowInterceptTouchEvent(true);
                if (routeSheetBehavior != null) {
                    routeSheetBehavior.setDraggable(false);
                }
            } else if (action == MotionEvent.ACTION_UP
                    || action == MotionEvent.ACTION_CANCEL) {
                view.getParent().requestDisallowInterceptTouchEvent(false);
                if (routeSheetBehavior != null) {
                    routeSheetBehavior.setDraggable(true);
                }
            }
            return false;
        });
    }

    private void updateSheetContentHeight(float expansion) {
        if (customRoutePanel == null || routeStopsCard == null || chatArea == null
                || customRoutePanel.getHeight() <= 0) {
            return;
        }
        int collapsedStopsHeight = dpToPx(96);
        int collapsedChatHeight = dpToPx(76);
        int fixedContentHeight = dpToPx(215);
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
        mainMap.setInfoWindowAdapter(new AMap.InfoWindowAdapter() {
            @Override
            public View getInfoWindow(Marker marker) {
                return createRouteMarkerInfoWindow(marker);
            }

            @Override
            public View getInfoContents(Marker marker) {
                return null;
            }
        });
        mainMap.setOnMarkerClickListener(marker -> {
            marker.showInfoWindow();
            return true;
        });

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

    @NonNull
    private BitmapDescriptor getMyLocationDescriptor() {
        if (myLocationDescriptor != null) {
            return myLocationDescriptor;
        }
        int size = dp(46);
        float center = size / 2f;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Path direction = new Path();
        direction.moveTo(center, dp(9));
        direction.lineTo(center - dp(5), dp(17));
        direction.lineTo(center + dp(5), dp(17));
        direction.close();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(Color.WHITE);
        canvas.drawPath(direction, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF1677FF);
        canvas.drawPath(direction, paint);

        paint.setColor(Color.WHITE);
        canvas.drawCircle(center, center + dp(3), dp(11), paint);
        paint.setColor(0xFF1677FF);
        canvas.drawCircle(center, center + dp(3), dp(8), paint);
        myLocationDescriptor = BitmapDescriptorFactory.fromBitmap(bitmap);
        return myLocationDescriptor;
    }

    @Nullable
    private View createRouteMarkerInfoWindow(@NonNull Marker marker) {
        Object object = marker.getObject();
        if (!(object instanceof RouteNode)) {
            return null;
        }
        RouteNode node = (RouteNode) object;
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(7), dp(7), dp(9), dp(7));
        card.setElevation(dp(6));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dp(12));
        background.setStroke(dp(1), 0xFFE1E7F0);
        card.setBackground(background);

        ImageView image = new ImageView(requireContext());
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setPadding(dp(9), dp(8), dp(9), dp(8));
        image.setBackgroundColor(0xFFEEF3FA);
        image.setImageResource(R.drawable.ic_route_photo_placeholder);
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        card.addView(image, imageParams);

        LinearLayout textGroup = new LinearLayout(requireContext());
        textGroup.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(dp(164),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        textParams.setMarginStart(dp(8));

        TextView name = new TextView(requireContext());
        name.setText(TextUtils.isEmpty(node.getName()) ? marker.getTitle() : node.getName());
        name.setTextColor(Color.BLACK);
        name.setTextSize(13f);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        textGroup.addView(name);

        TextView meta = new TextView(requireContext());
        meta.setText(buildMarkerMeta(node));
        meta.setTextColor(ContextCompat.getColor(
                requireContext(), R.color.route_primary));
        meta.setTextSize(10f);
        meta.setSingleLine(true);
        meta.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        metaParams.setMargins(0, dp(2), 0, 0);
        textGroup.addView(meta, metaParams);

        TextView address = new TextView(requireContext());
        address.setText(TextUtils.isEmpty(node.getAddress()) ? "路线地点" : node.getAddress());
        address.setTextColor(ContextCompat.getColor(
                requireContext(), R.color.route_text_secondary));
        address.setTextSize(10f);
        address.setMaxLines(1);
        address.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams addressParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        addressParams.setMargins(0, dp(2), 0, 0);
        textGroup.addView(address, addressParams);
        card.addView(textGroup, textParams);

        String photoUrl = node.getPhotoUrl();
        if (!TextUtils.isEmpty(photoUrl)) {
            Bitmap cached = markerPhotoCache.get(photoUrl);
            if (cached != null) {
                image.setPadding(0, 0, 0, 0);
                image.setImageBitmap(cached);
            } else {
                requestMarkerPhoto(photoUrl, marker);
            }
        } else {
            requestMarkerPoiDetail(node, marker);
        }
        return card;
    }

    @NonNull
    private String buildMarkerMeta(@NonNull RouteNode node) {
        StringBuilder meta = new StringBuilder();
        if (node.getVisitOrder() > 0) {
            meta.append("第 ").append(node.getVisitOrder()).append(" 站");
        }
        if (node.getRecommendedDuration() > 0) {
            if (meta.length() > 0) {
                meta.append(" · ");
            }
            meta.append("建议 ").append(node.getRecommendedDuration()).append(" 分钟");
        }
        if (!TextUtils.isEmpty(node.getVisitTime())) {
            if (meta.length() > 0) {
                meta.append(" · ");
            }
            meta.append(node.getVisitTime());
        }
        return meta.length() > 0 ? meta.toString() : "路线地点";
    }

    private void requestMarkerPoiDetail(@NonNull RouteNode node, @NonNull Marker marker) {
        String poiId = node.getPoiId();
        if (TextUtils.isEmpty(poiId) || !markerPoiDetailRequests.add(poiId)) {
            return;
        }
        try {
            PoiSearch.Query query = new PoiSearch.Query(safeNodeName(node), "", "");
            query.setExtensions(PoiSearch.EXTENSIONS_ALL);
            PoiSearch search = new PoiSearch(requireContext(), query);
            markerPoiSearches.put(poiId, search);
            search.setLanguage(PoiSearch.CHINESE);
            search.setOnPoiSearchListener(new PoiSearch.OnPoiSearchListener() {
                @Override
                public void onPoiSearched(PoiResult result, int errorCode) {
                }

                @Override
                public void onPoiItemSearched(PoiItem item, int errorCode) {
                    markerPoiDetailRequests.remove(poiId);
                    markerPoiSearches.remove(poiId);
                    if (!isAdded() || errorCode != AMapException.CODE_AMAP_SUCCESS
                            || item == null) {
                        return;
                    }
                    if (TextUtils.isEmpty(node.getAddress())
                            && !TextUtils.isEmpty(item.getSnippet())) {
                        node.setAddress(item.getSnippet());
                    }
                    String detailPhotoUrl = firstPhotoUrl(item.getPhotos());
                    if (!TextUtils.isEmpty(detailPhotoUrl)) {
                        node.setPhotoUrl(detailPhotoUrl);
                        requestMarkerPhoto(detailPhotoUrl, marker);
                    } else {
                        refreshMarkerInfoWindow(marker);
                    }
                }
            });
            search.searchPOIIdAsyn(poiId);
        } catch (AMapException e) {
            markerPoiDetailRequests.remove(poiId);
            markerPoiSearches.remove(poiId);
        }
    }

    @Nullable
    private String firstPhotoUrl(@Nullable List<Photo> photos) {
        if (photos == null) {
            return null;
        }
        for (Photo photo : photos) {
            if (photo != null && !TextUtils.isEmpty(photo.getUrl())) {
                return photo.getUrl();
            }
        }
        return null;
    }

    private void refreshMarkerInfoWindow(@NonNull Marker marker) {
        if (!isAdded() || mainMap == null) {
            return;
        }
        try {
            if (marker.isInfoWindowShown()) {
                marker.hideInfoWindow();
                marker.showInfoWindow();
            }
        } catch (RuntimeException ignored) {
            // 路线重绘后旧 Marker 已失效。
        }
    }

    private void requestMarkerPhoto(@NonNull String photoUrl, @NonNull Marker marker) {
        if (!markerPhotoRequests.add(photoUrl)) {
            return;
        }
        markerPhotoExecutor.execute(() -> {
            Bitmap bitmap = downloadMarkerPhoto(photoUrl);
            markerPhotoRequests.remove(photoUrl);
            if (bitmap == null) {
                return;
            }
            markerPhotoCache.put(photoUrl, bitmap);
            waitingHandler.post(() -> {
                refreshMarkerInfoWindow(marker);
            });
        });
    }

    @Nullable
    private Bitmap downloadMarkerPhoto(@NonNull String photoUrl) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(photoUrl).openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(10000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "Japp-Android");
            connection.connect();
            if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) {
                return null;
            }
            try (InputStream input = connection.getInputStream()) {
                Bitmap source = BitmapFactory.decodeStream(input);
                if (source == null) {
                    return null;
                }
                int targetWidth = dp(120);
                int targetHeight = dp(90);
                float scale = Math.min(
                        targetWidth / (float) source.getWidth(),
                        targetHeight / (float) source.getHeight());
                if (scale >= 1f) {
                    return source;
                }
                Bitmap thumbnail = Bitmap.createScaledBitmap(
                        source,
                        Math.max(1, Math.round(source.getWidth() * scale)),
                        Math.max(1, Math.round(source.getHeight() * scale)),
                        true);
                if (thumbnail != source) {
                    source.recycle();
                }
                return thumbnail;
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
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
                .myLocationIcon(getMyLocationDescriptor())
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
            txtCurrentLocationStatus.setText("");
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
        btnSaveRoute.setOnClickListener(v -> saveCurrentRoute());
        btnPublishRoute.setOnClickListener(v -> openPublishDetails());
    }

    private void setupAiRouteAssistant(View root, @Nullable Bundle savedInstanceState) {
        adapter = new RouteChatAdapter();
        adapter.setRecyclerView(chatRecyclerView);
        adapter.setMapCreateBundle(savedInstanceState);
        adapter.setListener(new RouteChatAdapter.RouteChatListener() {
            @Override
            public void onMapClick(RouteChatItem item) {
                // 地图已经常驻在主界面，不再打开卡片内地图。
            }

            @Override
            public void onRetry(RouteChatItem item) {
                retryLastRouteRequest();
            }
            @Override public void onAcceptPlan(RouteChatItem item) { confirmAgentPlan(item); }
            @Override public void onCancelPlan(RouteChatItem item) {
                if (agent != null && agent.state.busy) agent.cancel();
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
        editMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateSendButtonState(true);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        setupSuggestionChips(root);
        updateSendButtonState(false);
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

    private void addPoiToRoute(@NonNull PoiItem poi,
                               @Nullable String photoUrl,
                               @Nullable String adcode,
                               @Nullable String citycode) {
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
        node.setAdcode(adcode);
        node.setCitycode(citycode);
        node.setRecommendedDuration(60);
        node.setLocation(point.getLongitude() + "," + point.getLatitude());
        node.setPhotoUrl(photoUrl);
        editableRouteNodes.add(node);
        invalidatePublishableRoute();
        syncAttractionSilently(node.getPoiId());

        renderEditableStops();
        updateMapFromEditableRoute(true);
        persistLocalDraft();
    }

    private void syncAttractionSilently(@Nullable String poiId) {
        if (TextUtils.isEmpty(poiId) || service == null
                || !SessionHelper.isLoggedIn(requireContext())) {
            return;
        }
        service.syncAttraction(poiId).enqueue(new Callback<Result<JsonElement>>() {
            @Override
            public void onResponse(Call<Result<JsonElement>> call,
                                   Response<Result<JsonElement>> response) {
                Result<JsonElement> result = response.body();
                if (!response.isSuccessful() || result == null || result.getCode() != 1) {
                    Log.w(TAG, "POI sync skipped: poiId=" + poiId
                            + ", http=" + response.code()
                            + ", message=" + (result == null ? "" : result.getMsg()));
                }
            }

            @Override
            public void onFailure(Call<Result<JsonElement>> call, Throwable t) {
                Log.w(TAG, "POI sync failed: poiId=" + poiId, t);
            }
        });
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
        if (btnSaveRoute != null) {
            btnSaveRoute.setEnabled(!editableRouteNodes.isEmpty());
        }
        if (routeStopsContainer == null || txtRouteStopCount == null) {
            return;
        }
        routeStopsContainer.removeAllViews();
        txtRouteStopCount.setText(getString(R.string.route_stop_count_format, editableRouteNodes.size()));
        updateAssistantContext();

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

            ImageView dragHandle = new ImageView(requireContext());
            dragHandle.setImageResource(R.drawable.ic_route_drag_handle);
            dragHandle.setPadding(dp(6), dp(8), dp(6), dp(8));
            dragHandle.setContentDescription("拖动调整" + node.getName() + "的顺序");
            dragHandle.setOnLongClickListener(v -> startRouteStopDrag(v, position));
            row.addView(dragHandle, new LinearLayout.LayoutParams(dp(36), dp(42)));

            row.setOnDragListener((target, event) ->
                    handleRouteStopDrop(row, position, event));

            LatLng point = RouteMapDrawHelper.parseLocation(node.getLocation());
            if (point != null) {
                row.setOnClickListener(v -> mainMap.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(point, 16f)));
            }
            routeStopsContainer.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
        }
    }

    private boolean startRouteStopDrag(@NonNull View handle, int position) {
        if (position < 0 || position >= editableRouteNodes.size()
                || editableRouteNodes.size() < 2) {
            return false;
        }
        handle.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        RouteStopDragPayload payload = new RouteStopDragPayload(position, handle);
        boolean started = handle.startDragAndDrop(
                ClipData.newPlainText("route-stop", String.valueOf(position)),
                new View.DragShadowBuilder(handle),
                payload,
                0);
        if (started) {
            handle.setAlpha(0.35f);
        }
        return started;
    }

    private boolean handleRouteStopDrop(@NonNull View row, int targetPosition,
                                        @NonNull DragEvent event) {
        Object state = event.getLocalState();
        if (!(state instanceof RouteStopDragPayload)) {
            return false;
        }
        RouteStopDragPayload payload = (RouteStopDragPayload) state;
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                return true;
            case DragEvent.ACTION_DRAG_ENTERED:
                row.setBackgroundColor(ContextCompat.getColor(
                        requireContext(), R.color.route_primary_soft));
                return true;
            case DragEvent.ACTION_DRAG_EXITED:
                row.setBackgroundColor(Color.TRANSPARENT);
                return true;
            case DragEvent.ACTION_DROP:
                row.setBackgroundColor(Color.TRANSPARENT);
                moveRouteStop(payload.sourcePosition, targetPosition);
                return true;
            case DragEvent.ACTION_DRAG_ENDED:
                row.setBackgroundColor(Color.TRANSPARENT);
                payload.sourceView.setAlpha(1f);
                return true;
            default:
                return true;
        }
    }

    private void setupRouteStopDragScrolling() {
        if (routeStopsContainer == null || routeStopsScrollView == null) {
            return;
        }
        routeStopsContainer.setOnDragListener((view, event) -> {
            if (!(event.getLocalState() instanceof RouteStopDragPayload)) {
                return false;
            }
            if (event.getAction() == DragEvent.ACTION_DRAG_LOCATION) {
                float visibleY = event.getY() - routeStopsScrollView.getScrollY();
                int edge = dp(30);
                if (visibleY < edge) {
                    routeStopsScrollView.scrollBy(0, -dp(16));
                } else if (visibleY > routeStopsScrollView.getHeight() - edge) {
                    routeStopsScrollView.scrollBy(0, dp(16));
                }
            } else if (event.getAction() == DragEvent.ACTION_DRAG_ENDED) {
                ((RouteStopDragPayload) event.getLocalState()).sourceView.setAlpha(1f);
            }
            return true;
        });
    }

    private void moveRouteStop(int fromPosition, int toPosition) {
        if (fromPosition < 0 || fromPosition >= editableRouteNodes.size()
                || toPosition < 0 || toPosition >= editableRouteNodes.size()
                || fromPosition == toPosition) {
            return;
        }
        RouteNode moved = editableRouteNodes.remove(fromPosition);
        editableRouteNodes.add(toPosition, moved);
        renumberRouteNodes();
        invalidatePublishableRoute();
        renderEditableStops();
        updateMapFromEditableRoute(true);
        persistLocalDraft();
    }

    private static final class RouteStopDragPayload {
        final int sourcePosition;
        final View sourceView;

        RouteStopDragPayload(int sourcePosition, @NonNull View sourceView) {
            this.sourcePosition = sourcePosition;
            this.sourceView = sourceView;
        }
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
        invalidatePublishableRoute();
        renderEditableStops();
        updateMapFromEditableRoute(true);
        persistLocalDraft();
    }

    private void renumberRouteNodes() {
        for (int i = 0; i < editableRouteNodes.size(); i++) {
            editableRouteNodes.get(i).setVisitOrder(i + 1);
        }
    }

    private void invalidatePublishableRoute() {
        publishableRouteId = 0;
        publishableRouteSummary = null;
        if (btnPublishRoute != null) {
            btnPublishRoute.setEnabled(!editableRouteNodes.isEmpty());
        }
    }

    private void updateMapFromEditableRoute(boolean planRoad) {
        mapRouteRevision++;
        int revision = mapRouteRevision;
        if (mainMap == null) {
            return;
        }
        if (drawAppliedAgentPlan()) return;
        if (editableRouteNodes.isEmpty()) {
            mainMap.clear();
            showDefaultMapIfNeeded();
            return;
        }
        List<RouteNode> planningNodes = buildCustomPlanningNodes();
        List<LatLng> points = RouteMapDrawHelper.extractPointsFromNodes(planningNodes);
        if (points.size() == 1) {
            RouteMapDrawHelper.drawRouteWithNodes(
                    mainMap, Collections.emptyList(), editableRouteNodes);
            return;
        }

        // 道路规划返回前仅展示地点标记，禁止用站点坐标直接连线。
        RouteMapDrawHelper.drawRouteWithNodes(
                mainMap, Collections.emptyList(), editableRouteNodes);
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
                // 已显示站点，等待真实道路结果。
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
                RouteMapDrawHelper.drawRouteWithNodes(
                        mainMap, roadPolyline, editableRouteNodes);
            }

            @Override
            public void onPlanningFailed(@NonNull String message) {
                // 保留站点标记，不绘制不准确的直连线。
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

    private void setupSuggestionChips(View root) {
        bindSuggestionChip(chipNanjing, 0);
        bindSuggestionChip(chipBeijing, 1);
        bindSuggestionChip(chipSuzhou, 2);
        bindSuggestionChip(chipShanghai, 3);
        if (chipNewConversation != null) {
            chipNewConversation.setOnClickListener(v -> confirmNewAssistantConversation());
        }
        updateAssistantContext();
    }

    private void confirmNewAssistantConversation() {
        if (routeRequestInFlight) {
            Toast.makeText(requireContext(), "请等待当前规划完成后再新建对话", Toast.LENGTH_SHORT).show();
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("新建路线助手对话")
                .setMessage("将清空助手消息并重置对话记忆，当前路线地点会继续保留。")
                .setNegativeButton("取消", null)
                .setPositiveButton("新建", (dialog, which) -> {
                    if (adapter != null) {
                        adapter.clearItems();
                    }
                    aiRouteMemoryId = null;
                    if (agent != null) agent.newConversation();
                    waitingStatusPosition = -1;
                    updateWelcomeVisibility();
                    persistLocalDraft();
                })
                .show();
    }

    private void bindSuggestionChip(@Nullable Chip chip, int actionIndex) {
        if (chip == null) {
            return;
        }
        chip.setOnClickListener(v -> {
            if (editMessage == null) {
                return;
            }
            String prompt = buildQuickPrompt(actionIndex);
            editMessage.setText(prompt);
            editMessage.setSelection(editMessage.getText() != null ? editMessage.getText().length() : 0);
            editMessage.requestFocus();
            editMessage.setAlpha(0.55f);
            editMessage.animate().alpha(1f).setDuration(180).start();
        });
    }

    private void updateAssistantContext() {
        boolean hasRoute = !editableRouteNodes.isEmpty();
        if (chipRouteContext != null) {
            chipRouteContext.setText(hasRoute
                    ? "基于当前 " + editableRouteNodes.size() + " 个地点"
                    : getString(R.string.route_ai_context_new));
        }
        if (chipNanjing != null) chipNanjing.setText(hasRoute ? "优化顺序" : "南京历史研学");
        if (chipBeijing != null) chipBeijing.setText(hasRoute ? "减少步行" : "亲子自然探索");
        if (chipSuzhou != null) chipSuzhou.setText(hasRoute ? "增加午餐点" : "博物馆半日游");
        if (chipShanghai != null) chipShanghai.setText(hasRoute ? "控制在 4 小时" : "预算友好路线");
        if (editMessage != null && !routeRequestInFlight) {
            editMessage.setHint(hasRoute
                    ? R.string.route_ai_optimize_hint
                    : R.string.route_ai_input_hint);
        }
    }

    @NonNull
    private String buildQuickPrompt(int actionIndex) {
        if (editableRouteNodes.isEmpty()) {
            switch (actionIndex) {
                case 0:
                    return "帮我规划一条南京历史文化主题的一日研学路线";
                case 1:
                    return "帮我规划一条适合亲子参与的自然探索路线";
                case 2:
                    return "帮我规划一条半日博物馆研学路线";
                default:
                    return "帮我规划一条交通方便、预算友好的研学路线";
            }
        }
        switch (actionIndex) {
            case 0:
                return "保留现有全部地点，帮我优化游览顺序";
            case 1:
                return "保留现有全部地点，尽量减少步行距离";
            case 2:
                return "保留现有全部地点，新增一个合适的午餐地点";
            default:
                return "保留现有全部地点，将总行程控制在 4 小时左右";
        }
    }

    private void retryLastRouteRequest() {
        if (routeRequestInFlight || adapter == null || editMessage == null) {
            return;
        }
        if (agent != null && "transport_error".equals(agent.state.status)) {
            beginAgentFeedback("正在恢复原规划请求…");
            if (agent.retry()) return;
            setSending(false);
        }
        List<RouteChatItem> items = adapter.getItems();
        for (int i = items.size() - 1; i >= 0; i--) {
            RouteChatItem item = items.get(i);
            if (item.getType() == RouteChatItem.TYPE_USER && !TextUtils.isEmpty(item.getText())) {
                editMessage.setText(item.getText());
                editMessage.setSelection(editMessage.length());
                sendRouteRequest();
                return;
            }
        }
        Toast.makeText(requireContext(), "没有可重试的路线需求", Toast.LENGTH_SHORT).show();
    }

    private void updateWelcomeVisibility() {
        if (welcomePanel == null || adapter == null) {
            return;
        }
        welcomePanel.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
    }

    private void sendRouteRequest() {
        if (editMessage == null || routeRequestInFlight || agent == null) return;
        if (!SessionHelper.isLoggedIn(requireContext())) {
            SessionHelper.handleUnauthorized(requireContext());
            return;
        }
        String text = editMessage.getText().toString().trim();
        if ("transport_error".equals(agent.state.status) && agent.state.request != null) {
            if (!text.equals(agent.state.request.message)) {
                Toast.makeText(requireContext(), "上一轮状态尚未确认，请先点击重试恢复进度或新建对话", Toast.LENGTH_LONG).show();
                return;
            }
            beginAgentFeedback("正在恢复原规划请求…");
            agent.retry();
            return;
        }
        try {
            RouteAgent.Request request = RouteAgentHelper.request(
                    SessionHelper.getAccountId(requireContext()), getOrCreateAiRouteMemoryId(),
                    text, agent.state.history, agent.state.constraints, editableRouteNodes);
            if (currentLocation != null && hasLocationPermission()
                    && currentLocation.longitude >= 73 && currentLocation.longitude <= 136
                    && currentLocation.latitude >= 3 && currentLocation.latitude <= 54) {
                request.origin = new RouteAgent.Origin();
                request.origin.longitude = currentLocation.longitude;
                request.origin.latitude = currentLocation.latitude;
            }
            adapter.addItem(RouteChatItem.user(text));
            editMessage.setText("");
            updateWelcomeVisibility();
            revealAssistantConversation();
            beginAgentFeedback("正在提交规划需求…");
            agent.start(request, RouteAgentHelper.snapshot(editableRouteNodes));
        } catch (IllegalArgumentException exception) {
            Toast.makeText(requireContext(), exception.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String getOrCreateAiRouteMemoryId() {
        if (agent.state.memoryId == null) agent.state.memoryId = UUID.randomUUID().toString();
        aiRouteMemoryId = agent.state.memoryId;
        return aiRouteMemoryId;
    }

    private void beginAgentFeedback(String text) {
        setSending(true);
        if (waitingStatusPosition < 0 || waitingStatusPosition >= adapter.getItemCount()
                || adapter.getItems().get(waitingStatusPosition).getType() != RouteChatItem.TYPE_ASSISTANT_PROGRESS) {
            adapter.addItem(RouteChatItem.agentProgress(text));
            waitingStatusPosition = adapter.getLastItemPosition();
        } else adapter.updateItemText(waitingStatusPosition, text);
        scrollChatToBottom();
    }

    private void replaceAgentFeedback(RouteChatItem item) {
        setSending(false);
        if (waitingStatusPosition >= 0 && waitingStatusPosition < adapter.getItemCount())
            adapter.replaceItem(waitingStatusPosition, item);
        else adapter.addItem(item);
        waitingStatusPosition = -1;
        persistLocalDraft();
        scrollChatToBottom();
    }

    private void setupAgent() {
        agent = new RouteAgentCoordinator(requireContext(),
                SessionHelper.getAccountId(requireContext()), service);
        if (agent.state.busy) {
            for (int i = 0; i < adapter.getItemCount(); i++) {
                if (adapter.getItems().get(i).getType() == RouteChatItem.TYPE_ASSISTANT_PROGRESS
                        || adapter.getItems().get(i).getText().startsWith("上次路线规划因应用关闭")) {
                    waitingStatusPosition = i;
                    adapter.replaceItem(i, RouteChatItem.agentProgress("正在恢复规划进度…"));
                }
            }
        }
        agent.attach(new RouteAgentCoordinator.Listener() {
            @Override public void onProgress(String text, boolean cancelling) {
                beginAgentFeedback(text);
            }
            @Override public void onResult(String runId, RouteAgent.Result result) {
                showAgentResult(runId, result);
            }
            @Override public void onError(String message, boolean unauthorized) {
                if (agent.state.request != null) restoreFailedRequest(agent.state.request.message);
                showPlanError(message);
                if (unauthorized) SessionHelper.handleUnauthorized(requireContext());
            }
            @Override public void onCancelled() {
                replaceAgentFeedback(RouteChatItem.assistantMessage("本轮规划已取消，当前路线已保留。"));
            }
        });
        if (!agent.state.busy && agent.state.result != null && !agent.state.applied
                && agent.state.runId != null) {
            boolean shown = false;
            for (RouteChatItem item : adapter.getItems())
                if (agent.state.runId.equals(item.getAgentRunId())) shown = true;
            if (!shown) showAgentResult(agent.state.runId, agent.state.result);
        }
    }

    private void showAgentResult(String runId, RouteAgent.Result result) {
        if ("plan".equals(result.kind)) {
            if (result.nodes == null || result.nodes.isEmpty() || !result.requiresAcceptance) {
                showPlanError("规划结果不完整，请重新规划");
                return;
            }
            replaceAgentFeedback(RouteChatItem.agentPlan(RouteAgentHelper.describe(result), runId));
        } else {
            RouteChatItem message = RouteChatItem.assistantMessage(RouteAgentHelper.describe(result));
            message.setAgentRunId(runId);
            replaceAgentFeedback(message);
        }
    }

    private void confirmAgentPlan(RouteChatItem item) {
        if (agent == null || routeRequestInFlight || agent.state.applied || agent.state.result == null
                || item.getAgentRunId() == null || !item.getAgentRunId().equals(agent.state.runId)) {
            Toast.makeText(requireContext(), "该方案已应用或已被新方案替代", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!RouteAgentHelper.snapshot(editableRouteNodes).equals(agent.state.routeSnapshot)) {
            Toast.makeText(requireContext(), "当前路线已修改，请基于最新地点重新规划", Toast.LENGTH_LONG).show();
            return;
        }
        RouteAgent.Result result = agent.state.result;
        new MaterialAlertDialogBuilder(requireContext()).setTitle("确认应用路线方案")
                .setMessage(RouteAgentHelper.describe(result))
                .setNegativeButton("暂不应用", null)
                .setPositiveButton("应用方案", (dialog, which) -> {
                    boolean relative = false;
                    for (RouteNode node : result.nodes) {
                        if (node.getVisitTime() != null && node.getVisitTime().startsWith("第")) relative = true;
                    }
                    if (relative) {
                        java.time.LocalDate today = java.time.LocalDate.now();
                        new android.app.DatePickerDialog(requireContext(), (picker, year, month, day) ->
                                applyAgentPlan(result, java.time.LocalDate.of(year, month + 1, day)),
                                today.getYear(), today.getMonthValue() - 1, today.getDayOfMonth())
                                .show();
                    } else applyAgentPlan(result, null);
                }).show();
    }

    private void applyAgentPlan(RouteAgent.Result result, @Nullable java.time.LocalDate firstDay) {
        if (routeRequestInFlight || agent == null || agent.state.result != result
                || !RouteAgentHelper.snapshot(editableRouteNodes).equals(agent.state.routeSnapshot)) {
            Toast.makeText(requireContext(), "路线或方案已变更，请重新确认", Toast.LENGTH_LONG).show();
            return;
        }
        List<RouteNode> nodes = copyRouteNodes(result.nodes);
        try {
            RouteAgentHelper.request(SessionHelper.getAccountId(requireContext()), getOrCreateAiRouteMemoryId(),
                    "确认方案", Collections.emptyList(), null, nodes);
            for (RouteNode node : nodes)
                node.setVisitTime(RouteAgentHelper.visitTimeToIso(node.getVisitTime(), firstDay));
        } catch (IllegalArgumentException exception) {
            Toast.makeText(requireContext(), exception.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        setSending(true);
        syncAgentPoi(nodes, 0, result);
    }

    private boolean drawAppliedAgentPlan() {
        if (agent == null || !agent.state.applied || agent.state.result == null
                || !RouteAgentHelper.snapshot(editableRouteNodes).equals(agent.state.appliedSnapshot)) return false;
        if (customWalkPlanner != null) customWalkPlanner.cancel();
        RouteMapDrawHelper.drawRouteWithNodes(mainMap, Collections.emptyList(), editableRouteNodes);
        if (agent.state.result.legs != null) for (RouteAgent.Leg leg : agent.state.result.legs) {
            if (leg.polyline == null || leg.polyline.size() < 2) continue;
            List<LatLng> segment = new ArrayList<>();
            for (List<Double> point : leg.polyline) {
                if (point != null && point.size() == 2 && point.get(0) != null && point.get(1) != null)
                    segment.add(new LatLng(point.get(1), point.get(0)));
            }
            if (segment.size() >= 2) mainMap.addPolyline(new PolylineOptions().addAll(segment)
                    .width(10f).color(ContextCompat.getColor(requireContext(), R.color.route_primary)));
        }
        return true;
    }

    private void syncAgentPoi(List<RouteNode> nodes, int index, RouteAgent.Result result) {
        if (!isAdded() || getView() == null) return;
        if (agent == null || agent.state.result != result
                || !RouteAgentHelper.snapshot(editableRouteNodes).equals(agent.state.routeSnapshot)) {
            setSending(false);
            Toast.makeText(requireContext(), "当前路线已修改，请重新规划", Toast.LENGTH_LONG).show();
            return;
        }
        if (index == nodes.size()) {
            // Only change the local route after every POI is available on the server.
            editableRouteNodes.clear();
            editableRouteNodes.addAll(nodes);
            renumberRouteNodes();
            publishableRouteId = 0; // Agent runId is NEVER a persisted routeId.
            publishableRouteSummary = result.message;
            agent.markApplied(RouteAgentHelper.snapshot(editableRouteNodes));
            setSending(false);
            renderEditableStops();
            updateMapFromEditableRoute(true);
            for (int i = 0; i < adapter.getItemCount(); i++) {
                RouteChatItem candidate = adapter.getItems().get(i);
                if (agent.state.runId.equals(candidate.getAgentRunId())) {
                    adapter.replaceItem(i, RouteChatItem.assistantMessage(
                            "路线方案已应用\n\n" + RouteAgentHelper.describe(result)
                                    .replace("候选路线 · 等待确认\n\n", "")
                                    .replace("\n\n确认后才会替换当前路线。", "")));
                }
            }
            persistLocalDraft(true);
            routeStopsCard.setAlpha(0.55f);
            routeStopsCard.animate().alpha(1f).setDuration(200).start();
            return;
        }
        poiSyncCall = service.syncAttraction(nodes.get(index).getPoiId());
        poiSyncCall.enqueue(new Callback<Result<JsonElement>>() {
            @Override public void onResponse(Call<Result<JsonElement>> call, Response<Result<JsonElement>> response) {
                if (!isAdded() || getView() == null || call.isCanceled()) return;
                Result<JsonElement> body = response.body();
                if (!response.isSuccessful() || body == null || body.getCode() != 1) {
                    setSending(false);
                    Toast.makeText(requireContext(), "地点同步失败：" + RoutePlanHelper.readErrorMessage(response, body),
                            Toast.LENGTH_LONG).show();
                    if (response.code() == 401) SessionHelper.handleUnauthorized(requireContext());
                    return;
                }
                syncAgentPoi(nodes, index + 1, result);
            }
            @Override public void onFailure(Call<Result<JsonElement>> call, Throwable error) {
                if (!isAdded() || getView() == null || call.isCanceled()) return;
                setSending(false);
                Toast.makeText(requireContext(), "地点同步失败，请再次应用方案", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void restoreFailedRequest(@NonNull String text) {
        if (editMessage == null || TextUtils.isEmpty(text)) {
            return;
        }
        editMessage.setText(text);
        editMessage.setSelection(text.length());
    }


    private boolean restoreLocalDraft() {
        RouteDraftStore.Draft draft = RouteDraftStore.load(requireContext());
        if (draft == null) {
            return false;
        }
        editableRouteNodes.clear();
        editableRouteNodes.addAll(draft.routeNodes);
        renumberRouteNodes();
        adapter.replaceAllItems(draft.chatItems);
        if (TextUtils.isEmpty(aiRouteMemoryId)) {
            aiRouteMemoryId = draft.memoryId;
        }
        publishableRouteId = draft.publishableRouteId;
        publishableRouteSummary = draft.publishableRouteSummary;
        waitingStatusPosition = -1;
        if (btnPublishRoute != null) {
            btnPublishRoute.setEnabled(!editableRouteNodes.isEmpty());
        }
        updateWelcomeVisibility();
        if (!draft.chatItems.isEmpty() && chatRecyclerView != null) {
            chatRecyclerView.post(this::scrollChatToBottom);
        }
        return !editableRouteNodes.isEmpty() || !draft.chatItems.isEmpty();
    }

    private void persistLocalDraft() {
        persistLocalDraft(false);
    }

    private void persistLocalDraft(boolean synchronous) {
        if (!isAdded()) {
            return;
        }
        List<RouteChatItem> chatItems = adapter != null
                ? adapter.getItems() : Collections.emptyList();
        RouteDraftStore.save(requireContext(), editableRouteNodes, chatItems,
                aiRouteMemoryId, publishableRouteId, publishableRouteSummary,
                routeRequestInFlight, waitingStatusPosition, synchronous);
    }


    @NonNull
    private List<RouteNode> copyRouteNodes(@NonNull List<RouteNode> source) {
        List<RouteNode> copies = new ArrayList<>(source.size());
        for (RouteNode node : source) {
            copies.add(copyRouteNode(node));
        }
        return copies;
    }

    @NonNull
    private RouteNode copyRouteNode(@NonNull RouteNode source) {
        RouteNode copy = new RouteNode();
        copy.setRouteId(source.getRouteId());
        copy.setVisitOrder(source.getVisitOrder());
        copy.setPoiId(source.getPoiId());
        copy.setName(source.getName());
        copy.setAddress(source.getAddress());
        copy.setParentPoiId(source.getParentPoiId());
        copy.setVisitTime(source.getVisitTime());
        copy.setCityname(source.getCityname());
        copy.setCitycode(source.getCitycode());
        copy.setAdcode(source.getAdcode());
        copy.setAdname(source.getAdname());
        copy.setPcode(source.getPcode());
        copy.setPname(source.getPname());
        copy.setType(source.getType());
        copy.setTypecode(source.getTypecode());
        copy.setRecommendedDuration(source.getRecommendedDuration());
        copy.setNotes(source.getNotes());
        copy.setLocation(source.getLocation());
        copy.setDistance(source.getDistance());
        copy.setOpentimeToday(source.getOpentimeToday());
        copy.setOpentimeWeek(source.getOpentimeWeek());
        copy.setTel(source.getTel());
        copy.setAttractionCreatedAt(source.getAttractionCreatedAt());
        copy.setAttractionUpdatedAt(source.getAttractionUpdatedAt());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setPhotoUrl(source.getPhotoUrl());
        return copy;
    }

    @NonNull
    private static String safeNodeName(@NonNull RouteNode node) {
        return TextUtils.isEmpty(node.getName()) ? "未命名地点" : node.getName().trim();
    }


    private void openPublishDetails() {
        if (editableRouteNodes.isEmpty()) {
            Toast.makeText(requireContext(), "当前路线暂不可发布", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!SessionHelper.isLoggedIn(requireContext())) {
            Toast.makeText(requireContext(), "请先登录", Toast.LENGTH_SHORT).show();
            SessionHelper.handleUnauthorized(requireContext());
            return;
        }

        launchPublishDetails();
    }

    private void saveCurrentRoute() {
        if (editableRouteNodes.isEmpty()) {
            Toast.makeText(requireContext(), "请先添加路线地点", Toast.LENGTH_SHORT).show();
            return;
        }
        renumberRouteNodes();
        SavedRouteStore.save(requireContext(), copyRouteNodes(editableRouteNodes));
        persistLocalDraft(true);
        btnSaveRoute.animate()
                .scaleX(0.94f)
                .scaleY(0.94f)
                .setDuration(90)
                .withEndAction(() -> btnSaveRoute.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(120)
                        .start())
                .start();
        Toast.makeText(requireContext(), "路线已保存到历史路线", Toast.LENGTH_SHORT).show();
    }

    private void loadRequestedSavedRoute() {
        SavedRouteStore.SavedRoute saved = SavedRouteStore.consumePending(requireContext());
        if (saved == null || adapter == null) return;
        editableRouteNodes.clear();
        editableRouteNodes.addAll(saved.getNodes());
        renumberRouteNodes();
        publishableRouteId = 0;
        publishableRouteSummary = null;
        aiRouteMemoryId = null;
        if (agent != null && !agent.state.busy) agent.newConversation();
        adapter.replaceAllItems(Collections.singletonList(
                RouteChatItem.assistantStatus("已载入保存路线，可继续调整或让路线助手优化。")));
        renderEditableStops();
        updateMapFromEditableRoute(true);
        persistLocalDraft(true);
        Toast.makeText(requireContext(), "已载入“" + saved.getTitle() + "”",
                Toast.LENGTH_SHORT).show();
    }

    private void launchPublishDetails() {

        String summary = !TextUtils.isEmpty(publishableRouteSummary)
                ? publishableRouteSummary : "研学路线";
        String title = summary.length() > 20
                ? summary.substring(0, 20) + "…"
                : summary;
        if (title.trim().isEmpty()) {
            title = "研学拼单";
        }
        Intent intent = new Intent(requireContext(), RoutePublishDetailActivity.class);
        intent.putExtra(RoutePublishDetailActivity.EXTRA_ROUTE_ID, publishableRouteId);
        intent.putExtra(RoutePublishDetailActivity.EXTRA_ROUTE_TITLE, title);
        intent.putExtra(RoutePublishDetailActivity.EXTRA_ROUTE_NODES,
                new ArrayList<>(copyRouteNodes(editableRouteNodes)));
        if (currentLocation != null) {
            intent.putExtra(RoutePublishDetailActivity.EXTRA_CURRENT_LAT, currentLocation.latitude);
            intent.putExtra(RoutePublishDetailActivity.EXTRA_CURRENT_LNG, currentLocation.longitude);
        }
        startActivity(intent);
    }

    private void showPlanError(String message) {
        setSending(false);
        if (!isAdded() || adapter == null) {
            return;
        }
        String display = TextUtils.isEmpty(message) ? "路线规划失败" : message;
        Toast.makeText(requireContext(), display, Toast.LENGTH_SHORT).show();
        RouteChatItem item = RouteChatItem.assistantStatus(
                "规划失败：" + display + "\n\n当前路线已保留，可稍后重试或调整需求。");
        if (waitingStatusPosition >= 0 && waitingStatusPosition < adapter.getItemCount()) {
            adapter.replaceItem(waitingStatusPosition, item);
        } else {
            adapter.addItem(item);
        }
        waitingStatusPosition = -1;
        scrollChatToBottom();
    }

    private void setSending(boolean sending) {
        routeRequestInFlight = sending;
        updateSendButtonState(true);
        if (btnSend != null) {
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

    private void updateSendButtonState(boolean animate) {
        if (btnSend == null) {
            return;
        }
        boolean hasText = editMessage != null
                && !TextUtils.isEmpty(editMessage.getText().toString().trim());
        boolean enabled = !routeRequestInFlight && hasText;
        btnSend.setEnabled(enabled);
        float alpha = enabled ? 1f : 0.42f;
        float scale = enabled ? 1f : 0.88f;
        if (animate) {
            btnSend.animate().alpha(alpha).scaleX(scale).scaleY(scale)
                    .setDuration(140).start();
        } else {
            btnSend.setAlpha(alpha);
            btnSend.setScaleX(scale);
            btnSend.setScaleY(scale);
        }
    }

    private void scrollChatToBottom() {
        if (chatRecyclerView != null && adapter != null && adapter.getItemCount() > 0) {
            chatRecyclerView.scrollToPosition(adapter.getItemCount() - 1);
        }
    }

    private void revealAssistantConversation() {
        if (routeSheetBehavior == null
                || routeSheetBehavior.getState() != BottomSheetBehavior.STATE_COLLAPSED) {
            return;
        }
        customRoutePanel.post(() -> {
            if (routeSheetBehavior != null) {
                routeSheetBehavior.setState(BottomSheetBehavior.STATE_HALF_EXPANDED);
            }
        });
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (!TextUtils.isEmpty(aiRouteMemoryId)) {
            outState.putString(STATE_AI_ROUTE_MEMORY_ID, aiRouteMemoryId);
        }
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
        loadRequestedSavedRoute();
    }

    @Override
    public void onPause() {
        persistLocalDraft(true);
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
        persistLocalDraft(true);
        if (agent != null) agent.detach();
        if (poiSyncCall != null) poiSyncCall.cancel();
        routeRequestInFlight = false;
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
        chipRouteContext = null;
        chipNanjing = null;
        chipBeijing = null;
        chipSuzhou = null;
        chipShanghai = null;
        chipNewConversation = null;
        btnTogglePlaceSearch = null;
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        markerPhotoExecutor.shutdownNow();
        markerPhotoRequests.clear();
        markerPoiDetailRequests.clear();
        markerPoiSearches.clear();
        for (Bitmap bitmap : markerPhotoCache.values()) {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
        markerPhotoCache.clear();
        if (myLocationDescriptor != null) {
            myLocationDescriptor.recycle();
            myLocationDescriptor = null;
        }
        super.onDestroy();
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
