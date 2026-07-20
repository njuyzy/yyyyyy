package com.example.Japp.user.fragment.route;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.amap.api.maps.MapsInitializer;
import com.amap.api.services.core.AMapException;
import com.amap.api.services.core.LatLonPoint;
import com.amap.api.services.core.PoiItem;
import com.amap.api.services.poisearch.IndoorData;
import com.amap.api.services.poisearch.Photo;
import com.amap.api.services.poisearch.PoiItemExtension;
import com.amap.api.services.poisearch.PoiResult;
import com.amap.api.services.poisearch.PoiSearch;
import com.example.Japp.R;
import com.example.Japp.util.InsetDividerDecoration;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 独立地点搜索页：展示距离，选中后加载高德 POI 扩展详情并返回路线页。 */
public class PlaceSearchActivity extends AppCompatActivity
        implements PoiSearch.OnPoiSearchListener {

    public static final String EXTRA_ORIGIN_LAT = "place_search_origin_lat";
    public static final String EXTRA_ORIGIN_LNG = "place_search_origin_lng";
    public static final String EXTRA_POI_ID = "selected_poi_id";
    public static final String EXTRA_NAME = "selected_place_name";
    public static final String EXTRA_ADDRESS = "selected_place_address";
    public static final String EXTRA_CITY = "selected_place_city";
    public static final String EXTRA_LAT = "selected_place_lat";
    public static final String EXTRA_LNG = "selected_place_lng";
    public static final String EXTRA_PHOTO_URL = "selected_place_photo_url";
    private static final LatLonPoint DEFAULT_ORIGIN = new LatLonPoint(32.0603, 118.7969);

    private MaterialToolbar toolbar;
    private EditText editSearch;
    private MaterialButton btnSearch;
    private MaterialButton btnAdd;
    private TextView txtStatus;
    private TextView txtDetailName;
    private TextView txtDetailSummary;
    private TextView txtDetailInfo;
    private TextView txtPhotoStatus;
    private ImageView imgPlacePhoto;
    private View photoPlaceholder;
    private RecyclerView resultsView;
    private View searchBar;
    private View detailView;
    private PoiSearchAdapter adapter;
    private PoiSearch poiSearch;
    private PoiItem selectedPoi;
    private LatLonPoint origin = DEFAULT_ORIGIN;
    private boolean usingDeviceOrigin;
    private int lastResultCount;
    private int photoRequestGeneration;
    private final ExecutorService photoExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MapsInitializer.updatePrivacyShow(getApplicationContext(), true, true);
        MapsInitializer.updatePrivacyAgree(getApplicationContext(), true);
        setContentView(R.layout.activity_place_search);

        readOrigin();
        bindViews();
        setupResults();
        setupActions();
    }

    private void readOrigin() {
        Intent intent = getIntent();
        if (intent == null || !intent.hasExtra(EXTRA_ORIGIN_LAT)
                || !intent.hasExtra(EXTRA_ORIGIN_LNG)) {
            return;
        }
        double lat = intent.getDoubleExtra(EXTRA_ORIGIN_LAT, 0d);
        double lng = intent.getDoubleExtra(EXTRA_ORIGIN_LNG, 0d);
        if (lat != 0d && lng != 0d) {
            origin = new LatLonPoint(lat, lng);
            usingDeviceOrigin = true;
        }
    }

    private void bindViews() {
        toolbar = findViewById(R.id.placeSearchToolbar);
        editSearch = findViewById(R.id.editFullPlaceSearch);
        btnSearch = findViewById(R.id.btnFullPlaceSearch);
        btnAdd = findViewById(R.id.btnAddSelectedPlace);
        txtStatus = findViewById(R.id.txtFullSearchStatus);
        txtDetailName = findViewById(R.id.txtPlaceDetailName);
        txtDetailSummary = findViewById(R.id.txtPlaceDetailSummary);
        txtDetailInfo = findViewById(R.id.txtPlaceDetailInfo);
        txtPhotoStatus = findViewById(R.id.txtPlacePhotoStatus);
        imgPlacePhoto = findViewById(R.id.imgPlacePhoto);
        photoPlaceholder = findViewById(R.id.placePhotoPlaceholder);
        resultsView = findViewById(R.id.fullPoiResultsRecyclerView);
        searchBar = findViewById(R.id.placeSearchBar);
        detailView = findViewById(R.id.placeDetailScrollView);
    }

    private void setupResults() {
        adapter = new PoiSearchAdapter(this::loadPlaceDetail);
        adapter.setOrigin(origin);
        resultsView.setLayoutManager(new LinearLayoutManager(this));
        resultsView.setAdapter(adapter);
        resultsView.addItemDecoration(new InsetDividerDecoration(this, 70, 16));
    }

    private void setupActions() {
        toolbar.setNavigationOnClickListener(v -> handleBack());
        btnSearch.setOnClickListener(v -> searchPlaces());
        editSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchPlaces();
                return true;
            }
            return false;
        });
        btnAdd.setOnClickListener(v -> returnSelectedPlace());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBack();
            }
        });
        editSearch.requestFocus();
    }

    private void searchPlaces() {
        String keyword = editSearch.getText().toString().trim();
        if (TextUtils.isEmpty(keyword)) {
            txtStatus.setText("请输入地点名称");
            return;
        }
        hideKeyboard();
        showResults();
        txtStatus.setText("正在搜索“" + keyword + "”…");
        btnSearch.setEnabled(false);
        adapter.submitItems(null);

        PoiSearch.Query query = new PoiSearch.Query(keyword, "", "");
        query.setPageSize(30);
        query.setPageNum(0);
        query.setExtensions(PoiSearch.EXTENSIONS_ALL);
        query.setLocation(origin);
        query.setDistanceSort(true);
        try {
            poiSearch = new PoiSearch(this, query);
            poiSearch.setLanguage(PoiSearch.CHINESE);
            poiSearch.setOnPoiSearchListener(this);
            poiSearch.searchPOIAsyn();
        } catch (AMapException e) {
            btnSearch.setEnabled(true);
            txtStatus.setText("搜索失败（错误码 " + e.getErrorCode() + "）");
        }
    }

    @Override
    public void onPoiSearched(PoiResult result, int errorCode) {
        btnSearch.setEnabled(true);
        List<PoiItem> items = result == null ? null : result.getPois();
        if (errorCode != AMapException.CODE_AMAP_SUCCESS) {
            adapter.submitItems(null);
            lastResultCount = 0;
            txtStatus.setText("搜索失败（错误码 " + errorCode + "）");
            return;
        }
        if (items == null || items.isEmpty()) {
            adapter.submitItems(null);
            lastResultCount = 0;
            txtStatus.setText("没有找到相关地点，换个关键词试试");
            return;
        }
        List<PoiItem> sorted = new ArrayList<>(items);
        sorted.sort(Comparator.comparingInt(item ->
                PoiSearchAdapter.distanceMeters(item, origin)));
        lastResultCount = sorted.size();
        adapter.submitItems(sorted);
        txtStatus.setText("找到 " + sorted.size() + " 个地点，已按"
                + (usingDeviceOrigin ? "当前位置" : "南京默认位置") + "由近到远排序");
    }

    private void loadPlaceDetail(@NonNull PoiItem item) {
        selectedPoi = item;
        hideKeyboard();
        if (poiSearch == null || TextUtils.isEmpty(item.getPoiId())) {
            showDetail(item);
            return;
        }
        txtStatus.setText("正在加载地点详情…");
        poiSearch.searchPOIIdAsyn(item.getPoiId());
    }

    @Override
    public void onPoiItemSearched(PoiItem item, int errorCode) {
        if (errorCode == AMapException.CODE_AMAP_SUCCESS && item != null) {
            showDetail(item);
        } else if (selectedPoi != null) {
            Toast.makeText(this, "扩展详情加载失败，已展示基础信息", Toast.LENGTH_SHORT).show();
            showDetail(selectedPoi);
        }
    }

    private void showDetail(@NonNull PoiItem item) {
        selectedPoi = item;
        resultsView.setVisibility(View.GONE);
        searchBar.setVisibility(View.GONE);
        txtStatus.setVisibility(View.GONE);
        detailView.setVisibility(View.VISIBLE);
        toolbar.setTitle("地点详情");

        String name = valueOr(item.getTitle(), "未命名地点");
        txtDetailName.setText(name);
        PoiItemExtension extension = item.getPoiExtension();
        String rating = extension == null ? null : extension.getmRating();
        StringBuilder summary = new StringBuilder(valueOr(item.getTypeDes(), "地点"));
        summary.append(" · ").append(PoiSearchAdapter.formatDistance(item, origin));
        if (!TextUtils.isEmpty(rating)) {
            summary.append(" · 评分 ").append(rating);
        }
        txtDetailSummary.setText(summary.toString());

        List<Photo> photos = item.getPhotos();
        loadAmapPhoto(photos);

        StringBuilder info = new StringBuilder();
        appendLine(info, "营业时间", extension == null ? null : extension.getOpentime(), true);
        appendLine(info, "地址", item.getSnippet(), true);
        appendLine(info, "电话", item.getTel(), true);
        appendLine(info, "地点分类", item.getTypeDes(), true);
        appendLine(info, "所在区域", joinArea(item), true);
        appendLine(info, "商圈", item.getBusinessArea(), false);
        appendLine(info, usingDeviceOrigin ? "距当前位置" : "距南京默认位置",
                PoiSearchAdapter.formatDistance(item, origin), true);
        appendLine(info, "高德图片", photos == null ? "0 张" : photos.size() + " 张", true);
        appendLine(info, "网站", item.getWebsite(), false);
        appendLine(info, "邮箱", item.getEmail(), false);
        appendLine(info, "邮编", item.getPostcode(), false);
        appendLine(info, "停车场类型", item.getParkingType(), false);
        appendLine(info, "方位", item.getDirection(), false);
        if (item.isIndoorMap()) {
            appendLine(info, "室内地图", "支持", true);
        }
        IndoorData indoor = item.getIndoorData();
        if (indoor != null) {
            appendLine(info, "所在楼层", valueOr(indoor.getFloorName(),
                    String.valueOf(indoor.getFloor())), false);
        }
        LatLonPoint point = item.getLatLonPoint();
        if (point != null) {
            appendLine(info, "坐标", point.getLongitude() + ", " + point.getLatitude(), true);
        }
        appendLine(info, "高德 POI ID", item.getPoiId(), false);
        txtDetailInfo.setText(info.toString());
    }

    private void loadAmapPhoto(@Nullable List<Photo> photos) {
        int requestId = ++photoRequestGeneration;
        imgPlacePhoto.setImageDrawable(null);
        imgPlacePhoto.setVisibility(View.GONE);
        photoPlaceholder.setVisibility(View.VISIBLE);

        String photoUrl = null;
        if (photos != null) {
            for (Photo photo : photos) {
                if (photo != null && !TextUtils.isEmpty(photo.getUrl())) {
                    photoUrl = photo.getUrl();
                    break;
                }
            }
        }
        if (TextUtils.isEmpty(photoUrl)) {
            txtPhotoStatus.setText("高德暂无地点图片");
            return;
        }

        txtPhotoStatus.setText("正在从高德加载图片…");
        final String url = photoUrl;
        photoExecutor.execute(() -> {
            Bitmap bitmap = downloadBitmap(url);
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed() || requestId != photoRequestGeneration) {
                    return;
                }
                if (bitmap == null) {
                    txtPhotoStatus.setText("高德图片加载失败");
                    return;
                }
                imgPlacePhoto.setImageBitmap(bitmap);
                imgPlacePhoto.setVisibility(View.VISIBLE);
                photoPlaceholder.setVisibility(View.GONE);
            });
        });
    }

    @Nullable
    private Bitmap downloadBitmap(@NonNull String urlValue) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(urlValue).openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(12000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "Japp-Android");
            connection.connect();
            if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) {
                return null;
            }
            try (InputStream input = connection.getInputStream()) {
                return BitmapFactory.decodeStream(input);
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void showResults() {
        detailView.setVisibility(View.GONE);
        searchBar.setVisibility(View.VISIBLE);
        resultsView.setVisibility(View.VISIBLE);
        txtStatus.setVisibility(View.VISIBLE);
        toolbar.setTitle("搜索地点");
    }

    private void handleBack() {
        if (detailView.getVisibility() == View.VISIBLE) {
            showResults();
            txtStatus.setText(lastResultCount > 0
                    ? "找到 " + lastResultCount + " 个地点"
                    : "输入名称搜索景点、学校或博物馆");
        } else {
            finish();
        }
    }

    private void returnSelectedPlace() {
        if (selectedPoi == null || selectedPoi.getLatLonPoint() == null) {
            Toast.makeText(this, "该地点缺少坐标，无法加入路线", Toast.LENGTH_SHORT).show();
            return;
        }
        LatLonPoint point = selectedPoi.getLatLonPoint();
        Intent data = new Intent();
        data.putExtra(EXTRA_POI_ID, selectedPoi.getPoiId());
        data.putExtra(EXTRA_NAME, valueOr(selectedPoi.getTitle(), "未命名地点"));
        data.putExtra(EXTRA_ADDRESS, selectedPoi.getSnippet());
        data.putExtra(EXTRA_CITY, selectedPoi.getCityName());
        data.putExtra(EXTRA_LAT, point.getLatitude());
        data.putExtra(EXTRA_LNG, point.getLongitude());
        data.putExtra(EXTRA_PHOTO_URL, firstPhotoUrl(selectedPoi.getPhotos()));
        setResult(Activity.RESULT_OK, data);
        finish();
    }

    @Nullable
    private static String firstPhotoUrl(@Nullable List<Photo> photos) {
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

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
    }

    private static void appendLine(StringBuilder builder, String label,
                                   @Nullable String value, boolean showUnavailable) {
        if (TextUtils.isEmpty(value) && !showUnavailable) {
            return;
        }
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(label).append("：")
                .append(TextUtils.isEmpty(value) ? "暂无信息" : value);
    }

    private static String joinArea(PoiItem item) {
        StringBuilder area = new StringBuilder();
        appendPart(area, item.getProvinceName());
        appendPart(area, item.getCityName());
        appendPart(area, item.getAdName());
        return area.toString();
    }

    private static void appendPart(StringBuilder builder, @Nullable String value) {
        if (TextUtils.isEmpty(value) || builder.toString().endsWith(value)) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(" · ");
        }
        builder.append(value);
    }

    private static String valueOr(@Nullable String value, String fallback) {
        return TextUtils.isEmpty(value) ? fallback : value;
    }

    @Override
    protected void onDestroy() {
        photoRequestGeneration++;
        photoExecutor.shutdownNow();
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
