package com.example.Japp.leader.fragment.order;

import android.animation.ValueAnimator;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

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
import com.example.Japp.network.models.ProjectPage;
import com.example.Japp.network.models.Region;
import com.example.Japp.network.models.Result;
import com.example.Japp.network.models.RouteNode;
import com.example.Japp.user.fragment.joinTeam.TeamCardItem;
import com.example.Japp.user.fragment.joinTeam.TeamListAdapter;
import com.example.Japp.user.util.ProjectUiHelper;
import com.example.Japp.user.util.SessionHelper;
import com.example.Japp.util.InsetDividerDecoration;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class orderList extends Fragment {

    private static final String TAG = "LeaderOrderListApi";
    private static final int AVAILABLE_PAGE_SIZE = 15;
    private static final int MY_SERVING_PAGE_SIZE = 3;
    private static final int MAX_MY_SERVING_PAGES = 5;
    private static final int MAX_CONSECUTIVE_EMPTY_PAGES = 3;

    private RecyclerView recycler;
    private RecyclerView myPendingRecycler;
    private SwipeRefreshLayout swipeRefresh;
    private TextView txtEmpty;
    private TextView btnMoreMine;
    private TextView txtMyPendingCount;
    private View myPendingSection;
    private View myPendingHeader;
    private View myPendingContent;
    private ImageView imgMyPendingExpand;
    private View availableSection;
    private View inlineSearchContainer;
    private View searchDismissOverlay;
    private View toolbarTitle;
    private ImageButton btnSearch;
    private ImageButton btnFilter;
    private TextInputEditText editInlineSearch;
    private TeamListAdapter adapter;
    private TeamListAdapter myPendingAdapter;
    private UserService service;
    private final List<TeamCardItem> teamItems = new ArrayList<>();
    private final List<TeamCardItem> myPendingItems = new ArrayList<>();
    private final Set<Integer> teamProjectIds = new HashSet<>();
    private final Set<Integer> myPendingProjectIds = new HashSet<>();
    private final Map<Integer, RoutePresentation> routeCache = new HashMap<>();
    private int availablePage;
    private int myPendingPage;
    private int loadGeneration;
    private boolean loadingAvailable;
    private boolean loadingMyPending;
    @Nullable
    private Call<?> pendingAvailableCall;
    @Nullable
    private Call<?> pendingMyPendingCall;
    private boolean hasMoreAvailable = true;
    private boolean hasMoreMyPending = true;
    private int myPendingSectionHeight;
    private int availableOverlayOffset;
    private boolean tagFiltersVisible;
    private boolean myPendingExpanded;
    private int consecutiveEmptyAvailablePages;
    private int consecutiveEmptyMyPendingPages;
    private ValueAnimator tagGridAnimator;

    private RecyclerView tagGrid;
    private TextView txtCurrentCity;
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

    private List<Region> provinces = new ArrayList<>();
    private List<Region> cities = new ArrayList<>();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.leader_fragment_order_list, container, false);

        recycler = view.findViewById(R.id.recycler);
        myPendingRecycler = view.findViewById(R.id.myPendingRecycler);
        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        txtEmpty = view.findViewById(R.id.txtEmpty);
        btnMoreMine = view.findViewById(R.id.btnMoreMine);
        txtMyPendingCount = view.findViewById(R.id.txtMyPendingCount);
        myPendingSection = view.findViewById(R.id.myPendingSection);
        myPendingHeader = view.findViewById(R.id.myPendingHeader);
        myPendingContent = view.findViewById(R.id.myPendingContent);
        imgMyPendingExpand = view.findViewById(R.id.imgMyPendingExpand);
        availableSection = view.findViewById(R.id.availableSection);
        inlineSearchContainer = view.findViewById(R.id.inlineSearchContainer);
        searchDismissOverlay = view.findViewById(R.id.searchDismissOverlay);
        toolbarTitle = view.findViewById(R.id.toolbarTitle);
        btnSearch = view.findViewById(R.id.btnSearch);
        btnFilter = view.findViewById(R.id.btnFilter);
        editInlineSearch = view.findViewById(R.id.editInlineSearch);
        tagGrid = view.findViewById(R.id.tagGrid);
        txtCurrentCity = view.findViewById(R.id.txtCurrentCity);

        service = ApiClient.getClient().create(UserService.class);
        adapter = new TeamListAdapter();
        myPendingAdapter = new TeamListAdapter();
        adapter.setFavoriteEnabled(true);
        myPendingAdapter.setFavoriteEnabled(true);
        TeamListAdapter.OnTeamClickListener clickListener = item -> {
            Intent intent = new Intent(requireContext(), orderDetailActivity.class);
            intent.putExtra(orderDetailActivity.EXTRA_PROJECT_JSON, new Gson().toJson(item.getProject()));
            startActivity(intent);
        };
        adapter.setOnTeamClickListener(clickListener);
        myPendingAdapter.setOnTeamClickListener(clickListener);

        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);
        recycler.addItemDecoration(new InsetDividerDecoration(requireContext(), 14, 16));
        myPendingRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        myPendingRecycler.setAdapter(myPendingAdapter);
        myPendingRecycler.addItemDecoration(new InsetDividerDecoration(requireContext(), 14, 16));
        recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                updateAvailableOverlayForScroll(dy);
                if (dy <= 0 || loadingAvailable || !hasMoreAvailable) {
                    return;
                }
                LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (manager != null
                        && manager.findLastVisibleItemPosition() >= adapter.getItemCount() - 3) {
                    loadAvailablePage(true, loadGeneration);
                }
            }
        });

        if (swipeRefresh != null) {
            swipeRefresh.setOnRefreshListener(this::loadOrders);
            swipeRefresh.setOnChildScrollUpCallback((parent, child) ->
                    availableOverlayOffset > 0 || recycler.canScrollVertically(-1));
        }
        if (btnSearch != null) {
            btnSearch.setOnClickListener(v -> {
                if (inlineSearchContainer.getVisibility() == View.VISIBLE) {
                    hideInlineSearch(true);
                } else {
                    showInlineSearch();
                }
            });
        }
        btnFilter.setOnClickListener(v -> toggleTagFilters());
        searchDismissOverlay.setOnClickListener(v -> hideInlineSearch(true));
        editInlineSearch.setOnEditorActionListener((v, actionId, event) -> {
            hideInlineSearch(true);
            return true;
        });
        if (btnMoreMine != null) {
            btnMoreMine.setOnClickListener(v -> loadMyServingPage(true, loadGeneration));
        }
        myPendingHeader.setOnClickListener(v -> toggleMyPendingSection());
        txtCurrentCity.setOnClickListener(v -> showCityPicker());

        setupTagGrid();
        restoreCitySelection();

        return view;
    }

    private void toggleMyPendingSection() {
        myPendingExpanded = !myPendingExpanded;
        myPendingContent.setVisibility(myPendingExpanded ? View.VISIBLE : View.GONE);
        imgMyPendingExpand.animate()
                .rotation(myPendingExpanded ? 90f : 0f)
                .setDuration(160L)
                .start();
        imgMyPendingExpand.setContentDescription(
                myPendingExpanded
                        ? getString(R.string.leader_order_my_serving_collapse)
                        : getString(R.string.leader_order_my_serving_expand));
        availableOverlayOffset = 0;
        syncAvailableOverlayPosition();
    }

    private void toggleTagFilters() {
        if (tagGridAnimator != null) {
            tagGridAnimator.cancel();
        }
        int targetHeight = Math.round(180f * getResources().getDisplayMetrics().density);
        int startHeight = tagGrid.getVisibility() == View.VISIBLE
                ? tagGrid.getHeight() : 0;
        tagFiltersVisible = !tagFiltersVisible;

        if (tagFiltersVisible) {
            ViewGroup.LayoutParams params = tagGrid.getLayoutParams();
            params.height = 0;
            tagGrid.setLayoutParams(params);
            tagGrid.setVisibility(View.VISIBLE);
            tagGrid.setAlpha(0f);
            tagGrid.setTranslationY(-18f);
            tagGridAnimator = ValueAnimator.ofInt(0, targetHeight);
        } else {
            startHeight = Math.max(0, startHeight);
            tagGridAnimator = ValueAnimator.ofInt(startHeight, 0);
        }
        btnFilter.setRotation(0f);

        tagGridAnimator.setDuration(tagFiltersVisible ? 210L : 180L);
        tagGridAnimator.addUpdateListener(animation -> {
            int height = (int) animation.getAnimatedValue();
            ViewGroup.LayoutParams params = tagGrid.getLayoutParams();
            params.height = height;
            tagGrid.setLayoutParams(params);
            float progress = targetHeight == 0 ? 1f : height / (float) targetHeight;
            tagGrid.setAlpha(progress);
            tagGrid.setTranslationY(-18f * (1f - progress));
        });
        tagGridAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (!tagFiltersVisible) {
                    tagGrid.setVisibility(View.GONE);
                }
                tagGrid.setAlpha(1f);
                tagGrid.setTranslationY(0f);
            }
        });
        tagGridAnimator.start();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadOrders();
    }

    @Override
    public void onDestroyView() {
        loadGeneration++;
        cancelPendingCalls();
        if (tagGridAnimator != null) {
            tagGridAnimator.cancel();
            tagGridAnimator = null;
        }
        super.onDestroyView();
    }

    private void showInlineSearch() {
        if (inlineSearchContainer.getVisibility() == View.VISIBLE) {
            editInlineSearch.requestFocus();
            return;
        }
        editInlineSearch.setText(filterKeyword == null ? "" : filterKeyword);
        editInlineSearch.setSelection(editInlineSearch.length());
        searchDismissOverlay.setVisibility(View.VISIBLE);
        toolbarTitle.animate().cancel();
        txtCurrentCity.animate().cancel();
        toolbarTitle.animate()
                .alpha(0f)
                .translationY(-18f)
                .translationX(-24f)
                .setDuration(170L)
                .start();
        txtCurrentCity.animate()
                .alpha(0f)
                .translationY(-18f)
                .translationX(-18f)
                .setDuration(170L)
                .start();
        btnFilter.animate().cancel();
        btnFilter.animate()
                .alpha(0f)
                .translationY(-18f)
                .setDuration(150L)
                .start();
        inlineSearchContainer.setVisibility(View.VISIBLE);
        inlineSearchContainer.setAlpha(0f);
        inlineSearchContainer.setScaleX(0.28f);
        inlineSearchContainer.setTranslationX(54f);
        inlineSearchContainer.animate()
                .alpha(1f)
                .scaleX(1f)
                .translationX(0f)
                .setDuration(190L)
                .start();
        editInlineSearch.postDelayed(() -> {
            if (!isAdded() || inlineSearchContainer.getVisibility() != View.VISIBLE) {
                return;
            }
            editInlineSearch.requestFocus();
            InputMethodManager inputMethodManager = (InputMethodManager)
                    requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            inputMethodManager.showSoftInput(editInlineSearch, InputMethodManager.SHOW_IMPLICIT);
        }, 120L);
    }

    private void hideInlineSearch(boolean applyKeyword) {
        if (inlineSearchContainer.getVisibility() != View.VISIBLE) {
            return;
        }
        String nextKeyword = editInlineSearch.getText() == null
                ? "" : editInlineSearch.getText().toString().trim();
        String normalizedKeyword = nextKeyword.isEmpty() ? null : nextKeyword;
        boolean keywordChanged = !TextUtils.equals(filterKeyword, normalizedKeyword);
        if (applyKeyword) {
            filterKeyword = normalizedKeyword;
            clearLegacySearchFilters();
        }
        searchDismissOverlay.setVisibility(View.GONE);
        editInlineSearch.clearFocus();
        toolbarTitle.animate().cancel();
        txtCurrentCity.animate().cancel();
        toolbarTitle.animate()
                .alpha(1f)
                .translationY(0f)
                .translationX(0f)
                .setStartDelay(40L)
                .setDuration(180L)
                .start();
        txtCurrentCity.animate()
                .alpha(1f)
                .translationY(0f)
                .translationX(0f)
                .setStartDelay(40L)
                .setDuration(180L)
                .start();
        btnFilter.animate().cancel();
        btnFilter.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(40L)
                .setDuration(170L)
                .start();
        InputMethodManager inputMethodManager = (InputMethodManager)
                requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(editInlineSearch.getWindowToken(), 0);
        inlineSearchContainer.animate()
                .alpha(0f)
                .scaleX(0.3f)
                .translationX(54f)
                .setDuration(150L)
                .withEndAction(() -> {
                    inlineSearchContainer.setVisibility(View.GONE);
                    inlineSearchContainer.setAlpha(1f);
                    inlineSearchContainer.setScaleX(1f);
                    inlineSearchContainer.setTranslationX(0f);
                })
                .start();
        if (applyKeyword && keywordChanged) {
            loadOrders();
        }
    }

    private void clearLegacySearchFilters() {
        filterStatus = null;
        filterStatusLabel = null;
        filterDateFrom = null;
        filterDateTo = null;
        filterHasLeader = null;
    }

    private void setupTagGrid() {
        if (tagGrid == null) {
            return;
        }
        tagAdapter = new TagGridAdapter();
        tagAdapter.setSingleSelection(true);
        tagGrid.setLayoutManager(new GridLayoutManager(requireContext(), 3,
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

    private void restoreCitySelection() {
        if (txtCurrentCity == null) {
            return;
        }
        SharedPreferences prefs = requireContext().getSharedPreferences("leader_pref",
                android.content.Context.MODE_PRIVATE);
        if (prefs.contains("leader_order_region_code")) {
            filterRegionCode = prefs.getString("leader_order_region_code", "");
            filterRegionLabel = prefs.getString("leader_order_region_label", "");
        } else {
            filterRegionCode = prefs.getString("region_code", "");
            filterRegionLabel = ProjectUiHelper.regionAdcodeToCity(filterRegionCode);
            if (TextUtils.equals(filterRegionCode, filterRegionLabel)) {
                filterRegionCode = null;
                filterRegionLabel = null;
            }
        }
        if (TextUtils.isEmpty(filterRegionCode)) {
            filterRegionCode = null;
        }
        updateCurrentCityLabel();
    }

    private void updateCurrentCityLabel() {
        String city = filterRegionLabel;
        if (!TextUtils.isEmpty(city)) {
            int separator = city.lastIndexOf(" · ");
            if (separator >= 0 && separator + 3 < city.length()) {
                city = city.substring(separator + 3);
            }
        }
        if (TextUtils.isEmpty(city) && !TextUtils.isEmpty(filterRegionCode)) {
            city = ProjectUiHelper.regionAdcodeToCity(filterRegionCode);
        }
        if (TextUtils.isEmpty(city) || city.equals(filterRegionCode)) {
            txtCurrentCity.setText("全国");
        } else {
            txtCurrentCity.setText(city);
        }
    }

    private void showCityPicker() {
        View content = LayoutInflater.from(requireContext())
                .inflate(R.layout.popup_city_picker, null);
        ListView listProvince = content.findViewById(R.id.listProvince);
        PopupWindow popup = new PopupWindow(content, dpToPx(168), dpToPx(356), true);
        popup.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setElevation(dpToPx(8));
        PopupWindow[] cityPopupHolder = new PopupWindow[1];
        popup.setOnDismissListener(() -> {
            if (cityPopupHolder[0] != null && cityPopupHolder[0].isShowing()) {
                cityPopupHolder[0].dismiss();
            }
        });

        loadCityPickerProvinces(listProvince, popup, cityPopupHolder);
        popup.showAsDropDown(txtCurrentCity, 0, dpToPx(2));
    }

    private void loadCityPickerProvinces(ListView listProvince,
                                         PopupWindow provincePopup,
                                         PopupWindow[] cityPopupHolder) {
        service.getProvinces().enqueue(new Callback<Result<List<Region>>>() {
            @Override
            public void onResponse(@NonNull Call<Result<List<Region>>> call,
                                   @NonNull Response<Result<List<Region>>> response) {
                if (!isAdded() || !response.isSuccessful() || response.body() == null
                        || response.body().getCode() != 1) {
                    return;
                }
                provinces = response.body().getData() == null
                        ? new ArrayList<>() : response.body().getData();
                List<String> names = new ArrayList<>();
                names.add("全国");
                for (Region province : provinces) {
                    names.add(province.getName());
                }
                ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                        android.R.layout.simple_list_item_activated_1, names);
                listProvince.setAdapter(adapter);
                listProvince.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

                int initialPosition = 0;
                if (!TextUtils.isEmpty(filterRegionCode)) {
                    for (int i = 0; i < provinces.size(); i++) {
                        Region province = provinces.get(i);
                        if (filterRegionCode.startsWith(province.getAdcode().substring(0, 2))) {
                            initialPosition = i + 1;
                            break;
                        }
                    }
                }
                listProvince.setItemChecked(initialPosition, true);
                listProvince.setSelection(initialPosition);

                listProvince.setOnItemClickListener((parent, view, position, id) -> {
                    if (position == 0) {
                        applyCitySelection(null, null);
                        provincePopup.dismiss();
                        return;
                    }
                    Region province = provinces.get(position - 1);
                    listProvince.setOnTouchListener((lockedView, event) -> true);
                    showCitySubmenu(view, province, listProvince,
                            provincePopup, cityPopupHolder);
                });
            }

            @Override
            public void onFailure(@NonNull Call<Result<List<Region>>> call,
                                  @NonNull Throwable t) {
                if (isAdded()) {
                    Toast.makeText(requireContext(), "城市列表加载失败", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void showCitySubmenu(View selectedProvinceView,
                                 Region province,
                                 ListView listProvince,
                                 PopupWindow provincePopup,
                                 PopupWindow[] cityPopupHolder) {
        if (cityPopupHolder[0] != null && cityPopupHolder[0].isShowing()) {
            cityPopupHolder[0].dismiss();
        }
        View cityContent = LayoutInflater.from(requireContext())
                .inflate(R.layout.popup_city_submenu, null);
        ListView listCity = cityContent.findViewById(R.id.listCity);
        PopupWindow cityPopup = new PopupWindow(
                cityContent, dpToPx(168), dpToPx(356), false);
        cityPopup.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        cityPopup.setOutsideTouchable(false);
        cityPopup.setElevation(dpToPx(9));
        cityPopup.setOnDismissListener(() -> {
            listProvince.setOnTouchListener(null);
            if (provincePopup.isShowing()) {
                provincePopup.dismiss();
            }
        });
        cityPopupHolder[0] = cityPopup;
        cityPopup.showAsDropDown(selectedProvinceView,
                selectedProvinceView.getWidth(), -selectedProvinceView.getHeight());
        loadCityPickerCities(listCity, province, cityPopup, provincePopup);
    }

    private void loadCityPickerCities(ListView listCity,
                                      Region province,
                                      PopupWindow cityPopup,
                                      PopupWindow provincePopup) {
        listCity.setTag(province.getAdcode());
        service.getRegionChildren(province.getAdcode())
                .enqueue(new Callback<Result<List<Region>>>() {
            @Override
            public void onResponse(@NonNull Call<Result<List<Region>>> call,
                                   @NonNull Response<Result<List<Region>>> response) {
                if (!isAdded() || !cityPopup.isShowing()
                        || !TextUtils.equals(province.getAdcode(),
                                String.valueOf(listCity.getTag()))) {
                    return;
                }
                boolean successful = response.isSuccessful() && response.body() != null
                        && response.body().getCode() == 1;
                cities = successful && response.body().getData() != null
                        ? response.body().getData() : new ArrayList<>();
                if (!successful) {
                    Toast.makeText(requireContext(),
                            "城市列表加载失败", Toast.LENGTH_SHORT).show();
                } else if (cities.isEmpty()) {
                    Toast.makeText(requireContext(),
                            "暂无城市数据", Toast.LENGTH_SHORT).show();
                }
                bindCityPickerList(
                        listCity, province, cities, cityPopup, provincePopup);
            }

            @Override
            public void onFailure(@NonNull Call<Result<List<Region>>> call,
                                  @NonNull Throwable t) {
                if (isAdded() && cityPopup.isShowing()
                        && TextUtils.equals(province.getAdcode(),
                                String.valueOf(listCity.getTag()))) {
                    Toast.makeText(requireContext(),
                            "城市列表加载失败", Toast.LENGTH_SHORT).show();
                    bindCityPickerList(listCity, province, new ArrayList<>(),
                            cityPopup, provincePopup);
                }
            }
        });
    }

    private void bindCityPickerList(ListView listCity,
                                    Region province,
                                    List<Region> cityList,
                                    PopupWindow cityPopup,
                                    PopupWindow provincePopup) {
        List<String> names = new ArrayList<>();
        names.add("全省");
        for (Region city : cityList) {
            names.add(city.getName());
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_list_item_activated_1, names);
        listCity.setAdapter(adapter);
        listCity.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

        int initialPosition = 0;
        if (!TextUtils.isEmpty(filterRegionCode)) {
            for (int i = 0; i < cityList.size(); i++) {
                if (filterRegionCode.equals(cityList.get(i).getAdcode())) {
                    initialPosition = i + 1;
                    break;
                }
            }
        }
        listCity.setItemChecked(initialPosition, true);
        listCity.setSelection(initialPosition);
        listCity.setOnItemClickListener((parent, view, position, id) -> {
            applyCitySelection(province,
                    position == 0 ? null : cityList.get(position - 1));
            cityPopup.dismiss();
            provincePopup.dismiss();
        });
    }

    private void applyCitySelection(@Nullable Region province, @Nullable Region city) {
        if (province == null) {
            filterRegionCode = null;
            filterRegionLabel = null;
        } else if (city == null) {
            filterRegionCode = province.getAdcode();
            filterRegionLabel = province.getName();
        } else {
            filterRegionCode = city.getAdcode();
            filterRegionLabel = province.getName() + " · " + city.getName();
        }
        saveCitySelection();
        updateCurrentCityLabel();
        loadOrders();
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void saveCitySelection() {
        requireContext().getSharedPreferences("leader_pref", android.content.Context.MODE_PRIVATE)
                .edit()
                .putString("leader_order_region_code",
                        filterRegionCode == null ? "" : filterRegionCode)
                .putString("leader_order_region_label",
                        filterRegionLabel == null ? "" : filterRegionLabel)
                .apply();
    }

    private void showFilterSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View content = LayoutInflater.from(requireContext()).inflate(R.layout.popup_filter_menu, null);
        dialog.setContentView(content);
        setupFilterSheet(content, dialog);
        dialog.show();
    }

    private void setupFilterSheet(View content, BottomSheetDialog dialog) {
        TextInputEditText etKeyword = content.findViewById(R.id.etKeyword);
        Spinner spinnerProvince = content.findViewById(R.id.spinnerProvince);
        Spinner spinnerCity = content.findViewById(R.id.spinnerCity);
        ChipGroup chipGroupTags = content.findViewById(R.id.chipGroupTags);
        Spinner spinnerStatus = content.findViewById(R.id.spinnerStatus);
        Spinner spinnerHasLeader = content.findViewById(R.id.spinnerHasLeader);
        View cbOnlyAvailable = content.findViewById(R.id.cbOnlyAvailable);
        TextInputEditText etDateFrom = content.findViewById(R.id.etDateFrom);
        TextInputEditText etDateTo = content.findViewById(R.id.etDateTo);
        View btnReset = content.findViewById(R.id.btnReset);
        View btnConfirm = content.findViewById(R.id.btnConfirm);

        // 领队端始终只看可接路线，隐藏 cbOnlyAvailable 开关。
        if (cbOnlyAvailable != null) {
            cbOnlyAvailable.setVisibility(View.GONE);
        }

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
            chipGroupTags.setSingleSelection(true);
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
            if (tagAdapter != null) {
                tagAdapter.setSelectedTags(filterTags);
            }

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
            public void onFailure(@NonNull Call<Result<List<Region>>> call, Throwable t) {
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
            public void onFailure(@NonNull Call<Result<List<Region>>> call, Throwable t) {
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
        cities.clear();
        if (tagAdapter != null) {
            tagAdapter.clearSelection();
        }
    }

    private boolean hasActiveFilters() {
        return !TextUtils.isEmpty(filterKeyword)
                || !TextUtils.isEmpty(filterRegionCode)
                || !filterTags.isEmpty()
                || !TextUtils.isEmpty(filterStatus)
                || !TextUtils.isEmpty(filterDateFrom)
                || !TextUtils.isEmpty(filterDateTo)
                || filterHasLeader != null;
    }

    private void loadOrders() {
        if (!SessionHelper.isLoggedIn(requireContext())) {
            stopRefreshing();
            showEmpty("请先登录后查看可接路线");
            adapter.setItems(new ArrayList<>());
            myPendingAdapter.setItems(new ArrayList<>());
            myPendingSection.setVisibility(View.GONE);
            return;
        }

        cancelPendingCalls();
        int generation = ++loadGeneration;
        availablePage = 0;
        myPendingPage = 0;
        hasMoreAvailable = true;
        hasMoreMyPending = true;
        availableOverlayOffset = 0;
        loadingAvailable = false;
        loadingMyPending = false;
        consecutiveEmptyAvailablePages = 0;
        consecutiveEmptyMyPendingPages = 0;
        teamItems.clear();
        myPendingItems.clear();
        teamProjectIds.clear();
        myPendingProjectIds.clear();
        adapter.setItems(new ArrayList<>());
        myPendingAdapter.setItems(new ArrayList<>());
        myPendingSection.setVisibility(View.GONE);
        btnMoreMine.setVisibility(View.GONE);
        syncAvailableOverlayPosition();
        if (swipeRefresh != null) {
            swipeRefresh.setRefreshing(true);
        }
        loadMyServingPage(false, generation);
        loadAvailablePage(false, generation);
    }

    private void cancelPendingCalls() {
        if (pendingAvailableCall != null) {
            pendingAvailableCall.cancel();
            pendingAvailableCall = null;
        }
        if (pendingMyPendingCall != null) {
            pendingMyPendingCall.cancel();
            pendingMyPendingCall = null;
        }
        loadingAvailable = false;
        loadingMyPending = false;
    }

    private void loadAvailablePage(boolean append, int generation) {
        if (loadingAvailable || (append && !hasMoreAvailable) || generation != loadGeneration) {
            return;
        }
        int requestedPage = append ? availablePage + 1 : 1;
        loadingAvailable = true;

        if (hasActiveFilters()) {
            loadFilteredAvailablePage(append, requestedPage, generation);
        } else {
            loadDedicatedAvailablePage(append, requestedPage, generation);
        }
    }

    /** README: GET /projects/available?pageNum=&pageSize=，由后端保证项目当前可接。 */
    private void loadDedicatedAvailablePage(boolean append, int requestedPage, int generation) {
        Call<Result<ProjectPage>> request = service.getAvailableProjects(
                requestedPage, AVAILABLE_PAGE_SIZE);
        pendingAvailableCall = request;
        request.enqueue(new Callback<Result<ProjectPage>>() {
                    @Override
                    public void onResponse(@NonNull Call<Result<ProjectPage>> call,
                                           @NonNull Response<Result<ProjectPage>> response) {
                        if (!isAdded() || generation != loadGeneration) {
                            return;
                        }
                        if (pendingAvailableCall == call) {
                            pendingAvailableCall = null;
                        }
                        loadingAvailable = false;
                        if (response.code() == 401) {
                            stopRefreshing();
                            verifySessionAfterLeaderUnauthorized();
                            return;
                        }
                        if (!response.isSuccessful() || response.body() == null
                                || response.body().getCode() != 1
                                || response.body().getData() == null) {
                            handleAvailableLoadFailure(append, "加载失败，下拉刷新重试");
                            return;
                        }

                        ProjectPage page = response.body().getData();
                        List<Project> projects = page.getItems();
                        if (projects == null) {
                            projects = new ArrayList<>();
                        }
                        availablePage = page.getPageNum() > 0 ? page.getPageNum() : requestedPage;
                        hasMoreAvailable = page.getPages() > 0
                                ? availablePage < page.getPages()
                                : projects.size() >= AVAILABLE_PAGE_SIZE;
                        appendProjects(projects, teamItems, teamProjectIds, adapter);
                        consecutiveEmptyAvailablePages = 0;
                        stopRefreshing();
                        updateAvailableEmptyState();
                    }

                    @Override
                    public void onFailure(@NonNull Call<Result<ProjectPage>> call,
                                          @NonNull Throwable t) {
                        if (!isAdded() || generation != loadGeneration || call.isCanceled()) {
                            return;
                        }
                        if (pendingAvailableCall == call) {
                            pendingAvailableCall = null;
                        }
                        loadingAvailable = false;
                        Log.e(TAG, "available projects request failed", t);
                        handleAvailableLoadFailure(append, "网络错误，下拉刷新重试");
                    }
                });
    }

    /** 有搜索/地区/标签等条件时保留通用复合筛选接口，并继续按页加载。 */
    private void loadFilteredAvailablePage(boolean append, int requestedPage, int generation) {
        int accountId = SessionHelper.getAccountId(requireContext());
        Call<Result<List<Project>>> request = service.filterProjects(
                accountId,
                requestedPage,
                AVAILABLE_PAGE_SIZE,
                filterKeyword,
                backendRegionCode(),
                selectedFilterTag(),
                filterStatus,
                filterDateFrom,
                filterDateTo,
                null,
                null,
                false,
                true);
        pendingAvailableCall = request;
        request.enqueue(new Callback<Result<List<Project>>>() {
                    @Override
                    public void onResponse(@NonNull Call<Result<List<Project>>> call,
                                           @NonNull Response<Result<List<Project>>> response) {
                        if (!isAdded() || generation != loadGeneration) {
                            return;
                        }
                        if (pendingAvailableCall == call) {
                            pendingAvailableCall = null;
                        }
                        loadingAvailable = false;
                        if (response.code() == 401) {
                            stopRefreshing();
                            verifySessionAfterLeaderUnauthorized();
                            return;
                        }
                        if (!response.isSuccessful() || response.body() == null || response.body().getCode() != 1) {
                            stopRefreshing();
                            if (teamItems.isEmpty()) {
                                showEmpty("加载失败，下拉刷新重试");
                            } else {
                                Toast.makeText(requireContext(), "加载下一页失败", Toast.LENGTH_SHORT).show();
                            }
                            return;
                        }
                        List<Project> projects = response.body().getData();
                        if (projects == null) {
                            projects = new ArrayList<>();
                        }
                        availablePage = requestedPage;
                        hasMoreAvailable = projects.size() >= AVAILABLE_PAGE_SIZE;
                        List<Project> filtered = filterProvinceLocallyIfNeeded(projects);
                        // 过滤掉不可接单的路线（例如已有人接）。
                        List<Project> acceptList = new ArrayList<>();
                        for (Project project : filtered) {
                            if (ProjectUiHelper.isJoinable(project)
                                    && !ProjectUiHelper.hasAssignedLeader(project.getLeaderAccountId())) {
                                acceptList.add(project);
                            }
                        }
                        int added = appendProjects(acceptList, teamItems, teamProjectIds, adapter);
                        updateAvailableEmptyState();

                        if (added > 0) {
                            consecutiveEmptyAvailablePages = 0;
                        } else {
                            consecutiveEmptyAvailablePages++;
                            // 连续多页均无命中，避免无谓请求；超过上限即停止。
                            if (consecutiveEmptyAvailablePages >= MAX_CONSECUTIVE_EMPTY_PAGES) {
                                hasMoreAvailable = false;
                                stopRefreshing();
                                updateAvailableEmptyState();
                                return;
                            }
                        }
                        if (!hasMoreAvailable) {
                            stopRefreshing();
                            updateAvailableEmptyState();
                            return;
                        }
                        // 当前页可能全部都不命中过滤条件，继续取下一页。
                        if (added == 0) {
                            loadAvailablePage(true, generation);
                        } else {
                            stopRefreshing();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<Result<List<Project>>> call, @NonNull Throwable t) {
                        if (!isAdded() || generation != loadGeneration || call.isCanceled()) {
                            return;
                        }
                        if (pendingAvailableCall == call) {
                            pendingAvailableCall = null;
                        }
                        loadingAvailable = false;
                        stopRefreshing();
                        Log.e(TAG, "available orders request failed", t);
                        if (teamItems.isEmpty()) {
                            showEmpty("网络错误，下拉刷新重试");
                        } else {
                            Toast.makeText(requireContext(), "加载下一页失败", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void handleAvailableLoadFailure(boolean append, @NonNull String emptyMessage) {
        stopRefreshing();
        if (append && !teamItems.isEmpty()) {
            Toast.makeText(requireContext(), "加载下一页失败，请稍后重试", Toast.LENGTH_SHORT).show();
        } else {
            showEmpty(emptyMessage);
        }
    }

    private void loadMyServingPage(boolean append, int generation) {
        if (loadingMyPending || (append && !hasMoreMyPending) || generation != loadGeneration) {
            return;
        }
        int accountId = SessionHelper.getAccountId(requireContext());
        int requestedPage = append ? myPendingPage + 1 : 1;
        if (append && requestedPage > MAX_MY_SERVING_PAGES) {
            hasMoreMyPending = false;
            updateMyPendingSection();
            return;
        }
        loadingMyPending = true;
        btnMoreMine.setEnabled(false);

        // 领队"我正在服务的路线"：leaderAccountId 为当前账号的订单。
        Call<Result<List<Project>>> request = service.filterProjects(
                accountId,
                requestedPage,
                MY_SERVING_PAGE_SIZE,
                filterKeyword,
                backendRegionCode(),
                selectedFilterTag(),
                filterStatus,
                filterDateFrom,
                filterDateTo,
                null,
                accountId,
                null,
                null);
        pendingMyPendingCall = request;
        request.enqueue(new Callback<Result<List<Project>>>() {
                    @Override
                    public void onResponse(@NonNull Call<Result<List<Project>>> call,
                                           @NonNull Response<Result<List<Project>>> response) {
                        if (!isAdded() || generation != loadGeneration) {
                            return;
                        }
                        if (pendingMyPendingCall == call) {
                            pendingMyPendingCall = null;
                        }
                        loadingMyPending = false;
                        btnMoreMine.setEnabled(true);
                        if (response.code() == 401) {
                            stopRefreshing();
                            verifySessionAfterLeaderUnauthorized();
                            updateMyPendingSection();
                            return;
                        }
                        if (!response.isSuccessful() || response.body() == null
                                || response.body().getCode() != 1) {
                            if (append) {
                                Toast.makeText(requireContext(), "加载我正在服务的路线失败",
                                        Toast.LENGTH_SHORT).show();
                            }
                            updateMyPendingSection();
                            return;
                        }
                        List<Project> projects = response.body().getData();
                        if (projects == null) {
                            projects = new ArrayList<>();
                        }
                        myPendingPage = requestedPage;
                        hasMoreMyPending = projects.size() >= MY_SERVING_PAGE_SIZE
                                && requestedPage < MAX_MY_SERVING_PAGES;
                        projects = filterProvinceLocallyIfNeeded(projects);
                        int added = appendProjects(projects, myPendingItems,
                                myPendingProjectIds, myPendingAdapter);
                        updateMyPendingSection();
                        if (added > 0) {
                            consecutiveEmptyMyPendingPages = 0;
                        } else {
                            consecutiveEmptyMyPendingPages++;
                            if (consecutiveEmptyMyPendingPages >= MAX_CONSECUTIVE_EMPTY_PAGES) {
                                hasMoreMyPending = false;
                                updateMyPendingSection();
                                return;
                            }
                        }
                        if (!hasMoreMyPending) {
                            updateMyPendingSection();
                            return;
                        }
                        if (added == 0) {
                            loadMyServingPage(true, generation);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<Result<List<Project>>> call,
                                          @NonNull Throwable t) {
                        if (!isAdded() || generation != loadGeneration || call.isCanceled()) {
                            return;
                        }
                        if (pendingMyPendingCall == call) {
                            pendingMyPendingCall = null;
                        }
                        loadingMyPending = false;
                        btnMoreMine.setEnabled(true);
                        if (append) {
                            Toast.makeText(requireContext(), "加载我正在服务的路线失败",
                                    Toast.LENGTH_SHORT).show();
                        }
                        updateMyPendingSection();
                    }
                });
    }

    @Nullable
    private String selectedFilterTag() {
        return filterTags.isEmpty() ? null : filterTags.iterator().next();
    }

    @Nullable
    private String backendRegionCode() {
        return isProvinceRegionCode(filterRegionCode) ? null : filterRegionCode;
    }

    private List<Project> filterProvinceLocallyIfNeeded(List<Project> projects) {
        if (!isProvinceRegionCode(filterRegionCode)) {
            return projects;
        }
        List<Project> matched = new ArrayList<>();
        String prefix = filterRegionCode.substring(0, 2);
        for (Project project : projects) {
            if (project != null && !TextUtils.isEmpty(project.getRegionAdcode())
                    && project.getRegionAdcode().startsWith(prefix)) {
                matched.add(project);
            }
        }
        return matched;
    }

    private boolean isProvinceRegionCode(@Nullable String regionCode) {
        return !TextUtils.isEmpty(regionCode)
                && regionCode.length() >= 6
                && regionCode.endsWith("0000");
    }

    private int appendProjects(List<Project> projects,
                               List<TeamCardItem> target,
                               Set<Integer> targetIds,
                               TeamListAdapter targetAdapter) {
        int added = 0;
        List<TeamCardItem> newItems = new ArrayList<>();
        for (Project project : projects) {
            if (project == null || !targetIds.add(project.getId())) {
                continue;
            }
            TeamCardItem item = new TeamCardItem(project);
            item.setCity(ProjectUiHelper.regionAdcodeToCity(project.getRegionAdcode()));
            target.add(item);
            newItems.add(item);
            added++;
        }
        target.sort((left, right) -> ProjectUiHelper.compareProjectsByStatus(
                left.getProject(), right.getProject()));
        targetAdapter.setItems(new ArrayList<>(target));
        for (TeamCardItem item : newItems) {
            enrichRoute(item, target, targetAdapter);
        }
        return added;
    }

    private void enrichRoute(TeamCardItem item,
                             List<TeamCardItem> target,
                             TeamListAdapter targetAdapter) {
        int routeId = item.getProject().getRouteId();
        if (routeId <= 0) {
            return;
        }
        RoutePresentation cached = routeCache.get(routeId);
        if (cached != null) {
            item.setRouteSummary(cached.summary);
            item.setDuration(cached.duration);
            targetAdapter.setItems(new ArrayList<>(target));
            return;
        }
        service.getRouteNodes(routeId).enqueue(new Callback<Result<List<RouteNode>>>() {
            @Override
            public void onResponse(@NonNull Call<Result<List<RouteNode>>> call,
                                   @NonNull Response<Result<List<RouteNode>>> response) {
                if (!isAdded() || !response.isSuccessful() || response.body() == null
                        || response.body().getCode() != 1) {
                    return;
                }
                List<RouteNode> nodes = response.body().getData();
                RoutePresentation presentation = new RoutePresentation(
                        ProjectUiHelper.buildRouteSummary(nodes),
                        ProjectUiHelper.formatDuration(ProjectUiHelper.sumDurationMinutes(nodes)));
                routeCache.put(routeId, presentation);
                item.setRouteSummary(presentation.summary);
                item.setDuration(presentation.duration);
                targetAdapter.setItems(new ArrayList<>(target));
            }

            @Override
            public void onFailure(@NonNull Call<Result<List<RouteNode>>> call, @NonNull Throwable t) {
                Log.w(TAG, "route summary request failed, routeId=" + routeId, t);
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

    private void updateAvailableEmptyState() {
        if (teamItems.isEmpty()) {
            if (hasActiveFilters()) {
                showEmpty("暂无符合条件的可接订单");
            } else if (!hasMoreAvailable) {
                showEmpty("暂无其他可接订单");
            } else {
                showEmpty("正在加载可接订单…");
            }
        } else {
            txtEmpty.setVisibility(View.GONE);
            recycler.setVisibility(View.VISIBLE);
        }
    }

    private void updateMyPendingSection() {
        boolean visible = !myPendingItems.isEmpty();
        myPendingSection.setVisibility(visible ? View.VISIBLE : View.GONE);
        btnMoreMine.setVisibility(visible && hasMoreMyPending ? View.VISIBLE : View.GONE);
        myPendingContent.setVisibility(
                visible && myPendingExpanded ? View.VISIBLE : View.GONE);
        imgMyPendingExpand.setRotation(myPendingExpanded ? 90f : 0f);
        imgMyPendingExpand.setContentDescription(
                myPendingExpanded
                        ? getString(R.string.leader_order_my_serving_collapse)
                        : getString(R.string.leader_order_my_serving_expand));
        txtMyPendingCount.setText(myPendingItems.size() + " 条");
        syncAvailableOverlayPosition();
    }

    private void updateAvailableOverlayForScroll(int dy) {
        if (dy == 0 || myPendingSectionHeight <= 0 || availableSection == null) {
            return;
        }
        int nextOffset = Math.max(0,
                Math.min(myPendingSectionHeight, availableOverlayOffset + dy));
        if (nextOffset == availableOverlayOffset) {
            return;
        }
        availableOverlayOffset = nextOffset;
        availableSection.setTranslationY(myPendingSectionHeight - availableOverlayOffset);
    }

    private void syncAvailableOverlayPosition() {
        if (myPendingSection == null || availableSection == null) {
            return;
        }
        myPendingSection.post(() -> {
            if (!isAdded()) {
                return;
            }
            myPendingSectionHeight = myPendingSection.getVisibility() == View.VISIBLE
                    ? myPendingSection.getHeight() : 0;
            availableOverlayOffset = Math.min(availableOverlayOffset, myPendingSectionHeight);
            availableSection.setTranslationY(myPendingSectionHeight - availableOverlayOffset);
        });
    }

    private static final class RoutePresentation {
        final String summary;
        final String duration;

        RoutePresentation(String summary, String duration) {
            this.summary = summary;
            this.duration = duration;
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
