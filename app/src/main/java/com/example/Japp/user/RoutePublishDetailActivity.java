package com.example.Japp.user;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.TextUtils.TruncateAt;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.Japp.R;
import com.example.Japp.network.ApiClient;
import com.example.Japp.network.api.UserService;
import com.example.Japp.network.models.Result;
import com.example.Japp.network.models.RouteNode;
import com.example.Japp.network.models.requests.PublishRouteRequest;
import com.example.Japp.user.fragment.route.RouteMapDrawHelper;
import com.example.Japp.user.util.RoutePlanHelper;
import com.example.Japp.user.util.SessionHelper;
import com.example.Japp.util.DisplayCutoutAdapter;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.JsonElement;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RoutePublishDetailActivity extends AppCompatActivity {

    public static final String EXTRA_ROUTE_ID = "route_id";
    public static final String EXTRA_ROUTE_TITLE = "route_title";
    public static final String EXTRA_ROUTE_NODES = "route_nodes";
    public static final String EXTRA_CURRENT_LAT = "current_lat";
    public static final String EXTRA_CURRENT_LNG = "current_lng";

    private int routeId;
    private double currentLat;
    private double currentLng;
    private final List<RouteNode> routeNodes = new ArrayList<>();
    private final Calendar departure = Calendar.getInstance();

    private TextInputEditText editTitle;
    private TextInputEditText editRepresentativeCount;
    private TextInputEditText editMaxMembers;
    private TextInputEditText editDepartureDate;
    private AutoCompleteTextView editDepartureTime;
    private TextInputEditText editLeaderRequirements;
    private TextInputEditText editMemberRequirements;
    private LinearLayout routePreviewContainer;
    private TextView textRouteStartHint;
    private MaterialButton btnSubmit;
    private UserService service;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_route_publish_detail);
        DisplayCutoutAdapter.apply(this);

        routeId = getIntent().getIntExtra(EXTRA_ROUTE_ID, 0);
        Serializable nodesExtra = getIntent().getSerializableExtra(EXTRA_ROUTE_NODES);
        if (nodesExtra instanceof List<?>) {
            for (Object item : (List<?>) nodesExtra) {
                if (item instanceof RouteNode) {
                    routeNodes.add((RouteNode) item);
                }
            }
        }
        if (routeId <= 0 && routeNodes.isEmpty()) {
            Toast.makeText(this, "路线信息无效", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        currentLat = getIntent().getDoubleExtra(EXTRA_CURRENT_LAT, 0d);
        currentLng = getIntent().getDoubleExtra(EXTRA_CURRENT_LNG, 0d);
        service = ApiClient.getClient().create(UserService.class);

        bindViews();
        setupToolbar();
        setupDefaults();
        setupInteractions();
    }

    private void bindViews() {
        editTitle = findViewById(R.id.editTitle);
        editRepresentativeCount = findViewById(R.id.editRepresentativeCount);
        editMaxMembers = findViewById(R.id.editMaxMembers);
        editDepartureDate = findViewById(R.id.editDepartureDate);
        editDepartureTime = findViewById(R.id.editDepartureTime);
        editLeaderRequirements = findViewById(R.id.editLeaderRequirements);
        editMemberRequirements = findViewById(R.id.editMemberRequirements);
        routePreviewContainer = findViewById(R.id.routePreviewContainer);
        textRouteStartHint = findViewById(R.id.textRouteStartHint);
        btnSubmit = findViewById(R.id.btnSubmit);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupDefaults() {
        editTitle.setText(getIntent().getStringExtra(EXTRA_ROUTE_TITLE));
        departure.add(Calendar.DAY_OF_MONTH, 7);
        updateDateText();
        setupDepartureTimeOptions();
        renderRoutePreview();
    }

    private void setupInteractions() {
        editDepartureDate.setOnClickListener(v -> new DatePickerDialog(this,
                (view, year, month, day) -> {
                    departure.set(year, month, day);
                    updateDateText();
                }, departure.get(Calendar.YEAR), departure.get(Calendar.MONTH),
                departure.get(Calendar.DAY_OF_MONTH)).show());

        btnSubmit.setOnClickListener(v -> submit());
        editDepartureTime.setOnItemClickListener((parent, view, position, id) ->
                updateRouteStartHint());
    }

    private void updateDateText() {
        editDepartureDate.setText(String.format(Locale.getDefault(), "%04d-%02d-%02d",
                departure.get(Calendar.YEAR), departure.get(Calendar.MONTH) + 1,
                departure.get(Calendar.DAY_OF_MONTH)));
    }

    private void setupDepartureTimeOptions() {
        List<String> options = new ArrayList<>(48);
        for (int hour = 0; hour < 24; hour++) {
            options.add(String.format(Locale.getDefault(), "%02d:00", hour));
            options.add(String.format(Locale.getDefault(), "%02d:30", hour));
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, options);
        editDepartureTime.setAdapter(adapter);
        editDepartureTime.setText("09:00", false);
    }

    private void renderRoutePreview() {
        routePreviewContainer.removeAllViews();
        for (int index = 0; index < routeNodes.size(); index++) {
            if (index > 0) {
                TextView arrow = new TextView(this);
                arrow.setText("→");
                arrow.setTextColor(ContextCompat.getColor(this, R.color.route_text_secondary));
                arrow.setTextSize(18);
                arrow.setGravity(Gravity.CENTER);
                arrow.setPadding(dp(7), 0, dp(7), 0);
                routePreviewContainer.addView(arrow, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
            }

            RouteNode node = routeNodes.get(index);
            TextView stop = new TextView(this);
            stop.setText(routeNodeName(node, index));
            stop.setTextColor(ContextCompat.getColor(this, R.color.route_primary));
            stop.setTextSize(13);
            stop.setGravity(Gravity.CENTER);
            stop.setMaxLines(2);
            stop.setEllipsize(TruncateAt.END);
            stop.setMaxWidth(dp(150));
            stop.setMinHeight(dp(40));
            stop.setBackgroundResource(R.drawable.bg_route_preview_stop);
            routePreviewContainer.addView(stop, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        updateRouteStartHint();
    }

    private void updateRouteStartHint() {
        if (routeNodes.isEmpty()) {
            textRouteStartHint.setText("请按规定时间到达路线起点。");
            return;
        }
        String time = textOf(editDepartureTime);
        String firstName = routeNodeName(routeNodes.get(0), 0);
        textRouteStartHint.setText("请在 " + time + " 前到达「" + firstName
                + "」，该地点为本次路线起点。");
    }

    private String routeNodeName(RouteNode node, int index) {
        if (node != null && !TextUtils.isEmpty(node.getName())) {
            return node.getName().trim();
        }
        if (node != null && !TextUtils.isEmpty(node.getAddress())) {
            return node.getAddress().trim();
        }
        return "未命名地点";
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void submit() {
        if (!SessionHelper.isLoggedIn(this)) {
            SessionHelper.handleUnauthorized(this);
            return;
        }

        String title = textOf(editTitle);
        int representativeCount = positiveInt(editRepresentativeCount);
        int maxMembers = positiveInt(editMaxMembers);
        if (TextUtils.isEmpty(title)) {
            editTitle.setError("请填写拼单标题");
            return;
        }
        if (representativeCount <= 0) {
            editRepresentativeCount.setError("人数至少为 1");
            return;
        }
        if (maxMembers > 0 && maxMembers < representativeCount) {
            editMaxMembers.setError("总人数不能小于本组人数");
            return;
        }

        PublishForm form = new PublishForm(
                title,
                textOf(editDepartureDate),
                textOf(editDepartureTime),
                representativeCount,
                maxMembers,
                textOf(editLeaderRequirements),
                textOf(editMemberRequirements));
        btnSubmit.setEnabled(false);
        if (routeId <= 0) {
            createManualRouteAndPublish(form);
        } else {
            createProject(routeId, form);
        }
    }

    private void createManualRouteAndPublish(PublishForm form) {
        service.createManualRoute(routeNodes).enqueue(new Callback<Result<JsonElement>>() {
            @Override
            public void onResponse(Call<Result<JsonElement>> call,
                                   Response<Result<JsonElement>> response) {
                if (response.code() == 401) {
                    SessionHelper.handleUnauthorized(RoutePublishDetailActivity.this);
                    return;
                }
                Result<JsonElement> body = response.body();
                int savedRouteId = body != null && body.getCode() == 1
                        ? RoutePlanHelper.parseRouteId(body.getData()) : 0;
                if (!response.isSuccessful() || savedRouteId <= 0) {
                    btnSubmit.setEnabled(true);
                    String message = body != null ? body.getMsg() : "路线保存失败";
                    Toast.makeText(RoutePublishDetailActivity.this,
                            message, Toast.LENGTH_SHORT).show();
                    return;
                }
                routeId = savedRouteId;
                createProject(savedRouteId, form);
            }

            @Override
            public void onFailure(Call<Result<JsonElement>> call, Throwable t) {
                btnSubmit.setEnabled(true);
                Toast.makeText(RoutePublishDetailActivity.this,
                        "网络错误，路线保存失败", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void createProject(int savedRouteId, PublishForm form) {
        RouteNode firstNode = routeNodes.isEmpty() ? null : routeNodes.get(0);
        String startPoint = firstNode == null ? null : firstNode.getName();
        if (TextUtils.isEmpty(startPoint) && firstNode != null) {
            startPoint = firstNode.getAddress();
        }
        if (TextUtils.isEmpty(startPoint)) {
            startPoint = hasCurrentLocation() ? "当前位置" : null;
        }
        PublishRouteRequest request = new PublishRouteRequest(
                form.title,
                form.representativeCount,
                form.departureDate,
                form.departureTime,
                "MANUAL",
                startPoint,
                form.leaderRequirements,
                form.memberRequirements,
                form.maxMembers);

        service.publishRoute(savedRouteId, request).enqueue(new Callback<Result<JsonElement>>() {
            @Override
            public void onResponse(Call<Result<JsonElement>> call,
                                   Response<Result<JsonElement>> response) {
                btnSubmit.setEnabled(true);
                if (response.code() == 401) {
                    SessionHelper.handleUnauthorized(RoutePublishDetailActivity.this);
                    return;
                }
                if (response.isSuccessful() && response.body() != null
                        && response.body().getCode() == 1) {
                    saveLocalPublishDetails(request);
                    Toast.makeText(RoutePublishDetailActivity.this,
                            "发布成功，可在拼单页查看", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                } else {
                    String message = response.body() != null ? response.body().getMsg() : "发布失败";
                    Toast.makeText(RoutePublishDetailActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Result<JsonElement>> call, Throwable t) {
                btnSubmit.setEnabled(true);
                Toast.makeText(RoutePublishDetailActivity.this,
                        "网络错误，发布失败", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private static final class PublishForm {
        final String title;
        final String departureDate;
        final String departureTime;
        final int representativeCount;
        final int maxMembers;
        final String leaderRequirements;
        final String memberRequirements;

        PublishForm(String title, String departureDate, String departureTime,
                    int representativeCount, int maxMembers,
                    String leaderRequirements, String memberRequirements) {
            this.title = title;
            this.departureDate = departureDate;
            this.departureTime = departureTime;
            this.representativeCount = representativeCount;
            this.maxMembers = maxMembers;
            this.leaderRequirements = leaderRequirements;
            this.memberRequirements = memberRequirements;
        }
    }

    private void saveLocalPublishDetails(PublishRouteRequest request) {
        String prefix = "route_" + routeId + "_";
        getSharedPreferences("project_publish_details", MODE_PRIVATE).edit()
                .putInt(prefix + "representative_count", request.getRepresentedCount())
                .putString(prefix + "departure_time", request.getDepartureTime())
                .putString(prefix + "start_point", request.getStartPoint())
                .putString(prefix + "leader_requirements", request.getLeaderRequirements())
                .putString(prefix + "member_requirements", request.getParticipantRequirements())
                .apply();
    }

    private boolean hasCurrentLocation() {
        return currentLat != 0d && currentLng != 0d;
    }

    private int positiveInt(TextInputEditText input) {
        try {
            return Integer.parseInt(textOf(input));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String textOf(TextView input) {
        return input.getText() != null ? input.getText().toString().trim() : "";
    }
}
