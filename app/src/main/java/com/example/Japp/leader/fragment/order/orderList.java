package com.example.Japp.leader.fragment.order;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.Japp.R;
import com.example.Japp.leader.adapter.TagGridAdapter;
import com.example.Japp.leader.orderDetailActivity;
import com.example.Japp.network.ApiClient;
import com.example.Japp.network.api.UserService;
import com.example.Japp.network.models.Account;
import com.example.Japp.network.models.Project;
import com.example.Japp.network.models.Region;
import com.example.Japp.network.models.Result;
import com.example.Japp.network.models.RouteNode;
import com.example.Japp.user.fragment.joinTeam.TeamCardItem;
import com.example.Japp.user.fragment.joinTeam.TeamListAdapter;
import com.example.Japp.user.util.ProjectUiHelper;
import com.example.Japp.user.util.SessionHelper;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class orderList extends Fragment {

    private RecyclerView recycler;
    private SwipeRefreshLayout swipeRefresh;
    private TextView txtEmpty;
    private ImageButton btnFilter;
    private TeamListAdapter adapter;
    private UserService service;
    private final List<TeamCardItem> teamItems = new ArrayList<>();

    private RecyclerView tagGrid;
    private TextView txtCurrentCity;
    private ImageButton btnSearch;
    private TagGridAdapter tagAdapter;

    private String filterKeyword;
    private String filterRegionCode;
    private String filterRegionLabel;
    private final Set<String> filterTags = new HashSet<>();
    private String filterStatus;
    private String filterStatusLabel;
    private String filterDateFrom;
    private String filterDateTo;
    private Boolean filterHasLeader;
    private Boolean filterOnlyAvailable;

    private List<Region> provinces = new ArrayList<>();
    private List<Region> cities = new ArrayList<>();

    private final ActivityResultLauncher<Intent> detailLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == android.app.Activity.RESULT_OK) {
                    loadOrders();
                }
            });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.leader_fragment_order_list, container, false);

        recycler = view.findViewById(R.id.recycler);
        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        txtEmpty = view.findViewById(R.id.txtEmpty);
        btnFilter = view.findViewById(R.id.btnFilter);
        tagGrid = view.findViewById(R.id.tagGrid);
        txtCurrentCity = view.findViewById(R.id.txtCurrentCity);
        btnSearch = view.findViewById(R.id.btnSearch);

        service = ApiClient.getClient().create(UserService.class);
        adapter = new TeamListAdapter();
        adapter.setOnTeamClickListener(item -> {
            Intent intent = new Intent(requireContext(), orderDetailActivity.class);
            intent.putExtra(orderDetailActivity.EXTRA_PROJECT_JSON, new Gson().toJson(item.getProject()));
            detailLauncher.launch(intent);
        });

        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);

        if (swipeRefresh != null) {
            swipeRefresh.setOnRefreshListener(this::loadOrders);
        }
        if (btnFilter != null) {
            btnFilter.setOnClickListener(v -> showFilterSheet());
        }

        // 初始化标签网格
        setupTagGrid();

        // 设置当前城市
        loadCurrentCity();

        // 搜索按钮
        if (btnSearch != null) {
            btnSearch.setOnClickListener(v -> {
                // TODO: 跳转到搜索界面
                Toast.makeText(requireContext(), "搜索功能开发中", Toast.LENGTH_SHORT).show();
            });
        }

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadOrders();
    }

    private void showFilterSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View content = LayoutInflater.from(requireContext()).inflate(R.layout.popup_filter_menu, null);
        dialog.setContentView(content);
        setupFilterSheet(content, dialog);
        dialog.show();
    }

    private void setupTagGrid() {
        if (tagGrid == null) return;
        tagAdapter = new TagGridAdapter();
        tagGrid.setLayoutManager(new GridLayoutManager(requireContext(), 2,
                GridLayoutManager.HORIZONTAL, false));
        tagGrid.setAdapter(tagAdapter);

        String[] tagNames = getResources().getStringArray(R.array.route_tag_names);
        tagAdapter.setTags(java.util.Arrays.asList(tagNames));

        tagAdapter.setOnTagSelectedChangeListener(selectedTags -> {
            filterTags.clear();
            if (selectedTags != null) {
                filterTags.addAll(selectedTags);
            }
            loadOrders();
        });
    }

    private void loadCurrentCity() {
        if (txtCurrentCity == null) return;
        SharedPreferences prefs = requireContext().getSharedPreferences("user_pref", android.content.Context.MODE_PRIVATE);
        String regionCode = prefs.getString("region_code", "");
        String city = ProjectUiHelper.regionAdcodeToCity(regionCode);
        if (city.isEmpty() || city.equals(regionCode)) {
            txtCurrentCity.setText("全国");
        } else {
            txtCurrentCity.setText(city);
        }
    }

    private void setupFilterSheet(View content, BottomSheetDialog dialog) {
        TextInputEditText etKeyword = content.findViewById(R.id.etKeyword);
        Spinner spinnerProvince = content.findViewById(R.id.spinnerProvince);
        Spinner spinnerCity = content.findViewById(R.id.spinnerCity);
        ChipGroup chipGroupTags = content.findViewById(R.id.chipGroupTags);
        Spinner spinnerStatus = content.findViewById(R.id.spinnerStatus);
        Spinner spinnerHasLeader = content.findViewById(R.id.spinnerHasLeader);
        MaterialCheckBox cbOnlyAvailable = content.findViewById(R.id.cbOnlyAvailable);
        TextInputEditText etDateFrom = content.findViewById(R.id.etDateFrom);
        TextInputEditText etDateTo = content.findViewById(R.id.etDateTo);
        View btnReset = content.findViewById(R.id.btnReset);
        View btnConfirm = content.findViewById(R.id.btnConfirm);

        if (etKeyword != null && filterKeyword != null) {
            etKeyword.setText(filterKeyword);
        }
        if (etDateFrom != null && filterDateFrom != null) {
            etDateFrom.setText(filterDateFrom);
        }
        if (etDateTo != null && filterDateTo != null) {
            etDateTo.setText(filterDateTo);
        }

        String[] tagNames = getResources().getStringArray(R.array.route_tag_names);
        if (chipGroupTags != null) {
            for (String tag : tagNames) {
                Chip chip = new Chip(requireContext());
                chip.setText(tag);
                chip.setCheckable(true);
                if (filterTags.contains(tag)) {
                    chip.setChecked(true);
                }
                chipGroupTags.addView(chip);
            }
        }

        String[] statusLabels = getResources().getStringArray(R.array.order_filter_status_labels);
        String[] statusValues = getResources().getStringArray(R.array.order_filter_status_values);
        ArrayAdapter<String> statusAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, statusLabels);
        statusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerStatus.setAdapter(statusAdapter);
        if (filterStatus != null) {
            for (int i = 0; i < statusValues.length; i++) {
                if (filterStatus.equals(statusValues[i])) {
                    spinnerStatus.setSelection(i);
                    break;
                }
            }
        }

        String[] hasLeaderLabels = getResources().getStringArray(R.array.order_filter_has_leader_labels);
        ArrayAdapter<String> hasLeaderAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, hasLeaderLabels);
        hasLeaderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerHasLeader.setAdapter(hasLeaderAdapter);
        if (filterHasLeader == null) {
            spinnerHasLeader.setSelection(0);
        } else if (Boolean.FALSE.equals(filterHasLeader)) {
            spinnerHasLeader.setSelection(1);
        } else {
            spinnerHasLeader.setSelection(2);
        }

        if (cbOnlyAvailable != null) {
            cbOnlyAvailable.setChecked(Boolean.TRUE.equals(filterOnlyAvailable));
        }

        loadProvinceSpinner(spinnerProvince, spinnerCity);

        etDateFrom.setOnClickListener(v -> showDatePicker(date -> etDateFrom.setText(date)));
        etDateTo.setOnClickListener(v -> showDatePicker(date -> etDateTo.setText(date)));

        btnReset.setOnClickListener(v -> {
            resetFilters();
            loadOrders();
            dialog.dismiss();
        });

        btnConfirm.setOnClickListener(v -> {
            filterKeyword = etKeyword != null && etKeyword.getText() != null
                    ? etKeyword.getText().toString().trim() : "";
            if (filterKeyword.isEmpty()) {
                filterKeyword = null;
            }

            filterTags.clear();
            filterTags.addAll(getSelectedTags(chipGroupTags));

            int statusIndex = spinnerStatus.getSelectedItemPosition();
            if (statusIndex >= 0 && statusIndex < statusValues.length) {
                String value = statusValues[statusIndex];
                filterStatus = TextUtils.isEmpty(value) ? null : value;
                filterStatusLabel = statusLabels[statusIndex];
                if (TextUtils.isEmpty(value)) {
                    filterStatusLabel = null;
                }
            }

            filterDateFrom = etDateFrom.getText() != null ? etDateFrom.getText().toString().trim() : "";
            filterDateTo = etDateTo.getText() != null ? etDateTo.getText().toString().trim() : "";
            if (filterDateFrom.isEmpty()) {
                filterDateFrom = null;
            }
            if (filterDateTo.isEmpty()) {
                filterDateTo = null;
            }

            resolveRegionFromSpinners(spinnerProvince, spinnerCity);

            int hasLeaderIndex = spinnerHasLeader.getSelectedItemPosition();
            if (hasLeaderIndex == 1) {
                filterHasLeader = false;
            } else if (hasLeaderIndex == 2) {
                filterHasLeader = true;
            } else {
                filterHasLeader = null;
            }

            filterOnlyAvailable = cbOnlyAvailable != null && cbOnlyAvailable.isChecked()
                    ? Boolean.TRUE : null;

            loadOrders();
            dialog.dismiss();
        });
    }

    private void loadProvinceSpinner(Spinner spinnerProvince, Spinner spinnerCity) {
        List<String> provinceNames = new ArrayList<>();
        provinceNames.add(getString(R.string.order_filter_no_region));

        service.getProvinces().enqueue(new Callback<Result<List<Region>>>() {
            @Override
            public void onResponse(@NonNull Call<Result<List<Region>>> call,
                                   @NonNull Response<Result<List<Region>>> response) {
                if (!isAdded()) {
                    return;
                }
                if (!response.isSuccessful() || response.body() == null || response.body().getCode() != 1) {
                    return;
                }
                provinces = response.body().getData() != null ? response.body().getData() : new ArrayList<>();
                provinceNames.clear();
                provinceNames.add(getString(R.string.order_filter_no_region));
                for (Region region : provinces) {
                    provinceNames.add(region.getName());
                }
                ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                        android.R.layout.simple_spinner_item, provinceNames);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerProvince.setAdapter(adapter);

                if (filterRegionCode != null && !provinces.isEmpty()) {
                    restoreProvinceSelection(spinnerProvince, spinnerCity);
                }

                spinnerProvince.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        if (position == 0) {
                            cities.clear();
                            bindCitySpinner(spinnerCity, new ArrayList<>(), false);
                        } else {
                            loadCitySpinner(spinnerCity, provinces.get(position - 1).getAdcode());
                        }
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                    }
                });
            }

            @Override
            public void onFailure(@NonNull Call<Result<List<Region>>> call, @NonNull Throwable t) {
            }
        });
    }

    private void restoreProvinceSelection(Spinner spinnerProvince, Spinner spinnerCity) {
        for (int i = 0; i < provinces.size(); i++) {
            Region province = provinces.get(i);
            if (filterRegionCode.startsWith(province.getAdcode().substring(0, 2))) {
                spinnerProvince.setSelection(i + 1);
                loadCitySpinner(spinnerCity, province.getAdcode(), () -> {
                    for (int j = 0; j < cities.size(); j++) {
                        if (filterRegionCode.equals(cities.get(j).getAdcode())) {
                            spinnerCity.setSelection(j + 1);
                            break;
                        }
                    }
                });
                break;
            }
        }
    }

    private void loadCitySpinner(Spinner spinnerCity, String provinceAdcode) {
        loadCitySpinner(spinnerCity, provinceAdcode, null);
    }

    private void loadCitySpinner(Spinner spinnerCity, String provinceAdcode, @Nullable Runnable onLoaded) {
        service.getRegionChildren(provinceAdcode).enqueue(new Callback<Result<List<Region>>>() {
            @Override
            public void onResponse(@NonNull Call<Result<List<Region>>> call,
                                   @NonNull Response<Result<List<Region>>> response) {
                if (!isAdded()) {
                    return;
                }
                cities = response.isSuccessful() && response.body() != null && response.body().getCode() == 1
                        && response.body().getData() != null
                        ? response.body().getData() : new ArrayList<>();
                bindCitySpinner(spinnerCity, cities, true);
                if (onLoaded != null) {
                    onLoaded.run();
                }
            }

            @Override
            public void onFailure(@NonNull Call<Result<List<Region>>> call, @NonNull Throwable t) {
                if (isAdded()) {
                    bindCitySpinner(spinnerCity, new ArrayList<>(), false);
                }
            }
        });
    }

    private void bindCitySpinner(Spinner spinnerCity, List<Region> cityList, boolean enabled) {
        List<String> names = new ArrayList<>();
        names.add(getString(R.string.order_filter_no_city));
        for (Region city : cityList) {
            names.add(city.getName());
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCity.setAdapter(adapter);
        spinnerCity.setEnabled(enabled && !cityList.isEmpty());
    }

    private void resolveRegionFromSpinners(Spinner spinnerProvince, Spinner spinnerCity) {
        int provinceIndex = spinnerProvince.getSelectedItemPosition();
        if (provinceIndex <= 0 || provinces.isEmpty()) {
            filterRegionCode = null;
            filterRegionLabel = null;
            return;
        }

        Region province = provinces.get(provinceIndex - 1);
        int cityIndex = spinnerCity.getSelectedItemPosition();
        if (cityIndex <= 0 || cities.isEmpty()) {
            filterRegionCode = province.getAdcode();
            filterRegionLabel = province.getName();
            return;
        }

        Region city = cities.get(cityIndex - 1);
        filterRegionCode = city.getAdcode();
        filterRegionLabel = province.getName() + " · " + city.getName();
    }

    @NonNull
    private Set<String> getSelectedTags(@Nullable ChipGroup chipGroupTags) {
        Set<String> selected = new HashSet<>();
        if (chipGroupTags == null) {
            return selected;
        }
        for (int i = 0; i < chipGroupTags.getChildCount(); i++) {
            View child = chipGroupTags.getChildAt(i);
            if (child instanceof Chip) {
                Chip chip = (Chip) child;
                if (chip.isChecked() && chip.getText() != null) {
                    String tag = chip.getText().toString().trim();
                    if (!tag.isEmpty()) {
                        selected.add(tag);
                    }
                }
            }
        }
        return selected;
    }

    private void showDatePicker(DateCallback callback) {
        Calendar calendar = Calendar.getInstance();
        new DatePickerDialog(requireContext(),
                (view, year, month, dayOfMonth) -> {
                    String date = String.format(Locale.getDefault(), "%04d-%02d-%02d",
                            year, month + 1, dayOfMonth);
                    callback.onDateSelected(date);
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private interface DateCallback {
        void onDateSelected(String date);
    }

    private void resetFilters() {
        filterKeyword = null;
        filterRegionCode = null;
        filterRegionLabel = null;
        filterTags.clear();
        filterStatus = null;
        filterStatusLabel = null;
        filterDateFrom = null;
        filterDateTo = null;
        filterHasLeader = null;
        filterOnlyAvailable = null;
        cities.clear();
        if (tagAdapter != null) {
            tagAdapter.clearSelection();
        }
    }

    private ProjectUiHelper.ProjectFilterCriteria buildFilterCriteria() {
        ProjectUiHelper.ProjectFilterCriteria criteria = new ProjectUiHelper.ProjectFilterCriteria();
        criteria.keyword = filterKeyword;
        criteria.regionAdcode = filterRegionCode;
        criteria.tags = new HashSet<>(filterTags);
        criteria.status = filterStatus;
        criteria.dateFrom = filterDateFrom;
        criteria.dateTo = filterDateTo;
        criteria.hasLeader = filterHasLeader;
        criteria.joinableOnly = Boolean.TRUE.equals(filterOnlyAvailable) ? Boolean.TRUE : null;
        return criteria;
    }

    private void loadOrders() {
        if (!SessionHelper.isLoggedIn(requireContext())) {
            stopRefreshing();
            showEmpty("请先登录后查看可接路线");
            adapter.setItems(new ArrayList<>());
            return;
        }

        int accountId = SessionHelper.getAccountId(requireContext());
        if (swipeRefresh != null) {
            swipeRefresh.setRefreshing(true);
        }

        // 拉取较宽结果集，再按「任一条件命中」做本地 OR 筛选
        service.getProjects(accountId, 1, 100)
                .enqueue(new Callback<Result<List<Project>>>() {
                    @Override
                    public void onResponse(@NonNull Call<Result<List<Project>>> call,
                                           @NonNull Response<Result<List<Project>>> response) {
                        if (!isAdded()) {
                            return;
                        }
                        stopRefreshing();
                        if (response.code() == 401) {
                            verifySessionAfterLeaderUnauthorized();
                            return;
                        }
                        if (!response.isSuccessful() || response.body() == null || response.body().getCode() != 1) {
                            showEmpty("加载失败，下拉刷新重试");
                            adapter.setItems(new ArrayList<>());
                            return;
                        }
                        List<Project> projects = response.body().getData();
                        if (projects == null) {
                            projects = new ArrayList<>();
                        }
                        List<Project> filtered = ProjectUiHelper.filterProjectsByAnyMatch(
                                projects, buildFilterCriteria());
                        if (filtered.isEmpty()) {
                            showEmpty("暂无符合条件的路线");
                            adapter.setItems(new ArrayList<>());
                            return;
                        }
                        enrichProjects(filtered);
                    }

                    @Override
                    public void onFailure(@NonNull Call<Result<List<Project>>> call, @NonNull Throwable t) {
                        if (!isAdded()) {
                            return;
                        }
                        stopRefreshing();
                        showEmpty("网络错误，下拉刷新重试");
                        adapter.setItems(new ArrayList<>());
                    }
                });
    }

    private void verifySessionAfterLeaderUnauthorized() {
        int accountId = SessionHelper.getAccountId(requireContext());
        service.getAccount(accountId).enqueue(new Callback<Result<Account>>() {
            @Override
            public void onResponse(@NonNull Call<Result<Account>> call,
                                   @NonNull Response<Result<Account>> response) {
                if (!isAdded()) {
                    return;
                }
                if (response.isSuccessful() && response.body() != null
                        && response.body().getCode() == 1) {
                    Toast.makeText(requireContext(),
                            "当前账号暂无领队端访问权限，已返回用户端",
                            Toast.LENGTH_LONG).show();
                    SessionHelper.returnToUserMode(requireActivity());
                    return;
                }
                if (response.code() == 401) {
                    Toast.makeText(requireContext(),
                            "登录已失效，请重新登录", Toast.LENGTH_SHORT).show();
                    SessionHelper.handleUnauthorized(requireContext());
                    return;
                }
                showEmpty("暂时无法验证领队权限，请稍后重试");
                adapter.setItems(new ArrayList<>());
            }

            @Override
            public void onFailure(@NonNull Call<Result<Account>> call, @NonNull Throwable t) {
                if (!isAdded()) {
                    return;
                }
                showEmpty("网络异常，暂时无法验证领队权限");
                adapter.setItems(new ArrayList<>());
            }
        });
    }

    private void enrichProjects(List<Project> projects) {
        ProjectUiHelper.sortProjectsByStatus(projects);
        teamItems.clear();
        List<TeamCardItem> temp = new ArrayList<>();
        for (Project project : projects) {
            TeamCardItem item = new TeamCardItem(project);
            item.setCity(ProjectUiHelper.regionAdcodeToCity(project.getRegionAdcode()));
            temp.add(item);
            teamItems.add(item);
        }
        adapter.setItems(temp);
        txtEmpty.setVisibility(View.GONE);
        recycler.setVisibility(View.VISIBLE);

        AtomicInteger done = new AtomicInteger(0);
        int total = projects.size();
        for (int i = 0; i < projects.size(); i++) {
            final int index = i;
            Project project = projects.get(i);
            TeamCardItem item = temp.get(index);

            service.getAccount(project.getOwnerAccountId()).enqueue(new Callback<Result<Account>>() {
                @Override
                public void onResponse(@NonNull Call<Result<Account>> call, @NonNull Response<Result<Account>> response) {
                    if (response.isSuccessful() && response.body() != null && response.body().getCode() == 1) {
                        Account account = response.body().getData();
                        if (account != null) {
                            item.setOwnerName(account.getUsername());
                        }
                    }
                    fetchRoute(item, project.getRouteId(), done, total);
                }

                @Override
                public void onFailure(@NonNull Call<Result<Account>> call, @NonNull Throwable t) {
                    fetchRoute(item, project.getRouteId(), done, total);
                }
            });
        }
    }

    private void fetchRoute(TeamCardItem item, int routeId, AtomicInteger done, int total) {
        service.getRouteNodes(routeId).enqueue(new Callback<Result<List<RouteNode>>>() {
            @Override
            public void onResponse(@NonNull Call<Result<List<RouteNode>>> call,
                                   @NonNull Response<Result<List<RouteNode>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getCode() == 1) {
                    List<RouteNode> nodes = response.body().getData();
                    item.setRouteSummary(ProjectUiHelper.buildRouteSummary(nodes));
                    item.setDuration(ProjectUiHelper.formatDuration(ProjectUiHelper.sumDurationMinutes(nodes)));
                }
                checkRefresh(done, total);
            }

            @Override
            public void onFailure(@NonNull Call<Result<List<RouteNode>>> call, @NonNull Throwable t) {
                checkRefresh(done, total);
            }
        });
    }

    private void checkRefresh(AtomicInteger done, int total) {
        if (done.incrementAndGet() == total && isAdded()) {
            requireActivity().runOnUiThread(() -> {
                teamItems.sort((left, right) -> ProjectUiHelper.compareProjectsByStatus(
                        left.getProject(), right.getProject()));
                adapter.setItems(new ArrayList<>(teamItems));
            });
        }
    }

    private void stopRefreshing() {
        if (swipeRefresh != null) {
            swipeRefresh.setRefreshing(false);
        }
    }

    private void showEmpty(String message) {
        txtEmpty.setVisibility(View.VISIBLE);
        txtEmpty.setText(message);
        recycler.setVisibility(View.GONE);
    }
}
