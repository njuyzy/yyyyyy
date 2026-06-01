package com.example.Japp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.content.res.ColorStateList;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProfileSetupActivity extends AppCompatActivity {

    public static final String EXTRA_NAME = "extra_name";
    public static final String EXTRA_PHONE = "extra_phone";
    public static final String EXTRA_PASSWORD = "extra_password";
    public static final String EXTRA_CODE = "extra_code";
    public static final String EXTRA_ROLE = "extra_role";

    private TextInputLayout tilProvince;
    private TextInputLayout tilCity;
    private MaterialAutoCompleteTextView etProvince;
    private MaterialAutoCompleteTextView etCity;
    private ChipGroup chipGroup;
    private MaterialButton btnSubmit;

    private String name;
    private String phone;
    private String password;
    private String code;
    private final List<Integer> selectedTagIds = new ArrayList<>();
    private final List<String> selectedTagNames = new ArrayList<>();

    private final Map<String, String[]> cityMap = buildCityMap();
    private final Map<Integer, Integer> chipIdToTagId = new LinkedHashMap<>();
    private static final int ROUTE_TAG_COUNT = 15;
    private static final int MAX_ROUTE_TAG_SELECTION = 3;
    private final Map<String, String> regionCodeMap = buildRegionCodeMap();
    private final Map<String, String> provinceCodeMap = buildProvinceCodeMap();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_setup);

        if (!readExtras()) {
            startActivity(new Intent(ProfileSetupActivity.this, signup.class));
            finish();
            return;
        }

        initializeViews();
        setupProvinceCitySelectors();
        setupTagChips();
        setupListeners();
    }

    private boolean readExtras() {
        Intent intent = getIntent();
        if (intent == null) {
            return false;
        }
        name = intent.getStringExtra(EXTRA_NAME);
        phone = intent.getStringExtra(EXTRA_PHONE);
        password = intent.getStringExtra(EXTRA_PASSWORD);
        code = intent.getStringExtra(EXTRA_CODE);
        return !TextUtils.isEmpty(name)
                && !TextUtils.isEmpty(phone)
                && !TextUtils.isEmpty(password)
                && !TextUtils.isEmpty(code);
    }

    private void initializeViews() {
        tilProvince = findViewById(R.id.tilProvince);
        tilCity = findViewById(R.id.tilCity);
        etProvince = findViewById(R.id.etProvince);
        etCity = findViewById(R.id.etCity);
        chipGroup = findViewById(R.id.chipGroupTags);
        btnSubmit = findViewById(R.id.btnSubmitProfile);
    }

    private void setupProvinceCitySelectors() {
        ArrayAdapter<String> provinceAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                new ArrayList<>(cityMap.keySet())
        );
        etProvince.setAdapter(provinceAdapter);
        etCity.setEnabled(false);
        etCity.setFocusable(false);

        etProvince.setOnItemClickListener((parent, view, position, id) -> {
            String province = (String) parent.getItemAtPosition(position);
            updateCityAdapter(province);
            clearError(tilProvince);
        });

        etCity.setOnItemClickListener((parent, view, position, id) -> clearError(tilCity));
    }

    private void updateCityAdapter(@NonNull String province) {
        String[] cities = cityMap.get(province);
        if (cities == null || cities.length == 0) {
            cities = new String[]{province};
        }
        ArrayAdapter<String> cityAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                cities
        );
        etCity.setAdapter(cityAdapter);
        etCity.setEnabled(true);
        etCity.setFocusable(true);
        etCity.setText("", false);
    }

    private void setupTagChips() {
        String[] tagNames = getResources().getStringArray(R.array.route_tag_names);
        int tagCount = Math.min(tagNames.length, ROUTE_TAG_COUNT);
        chipGroup.removeAllViews();
        chipIdToTagId.clear();

        for (int i = 0; i < tagCount; i++) {
            int tagId = i + 1;
            Chip chip = new Chip(this);
            chip.setText(tagNames[i]);
            chip.setCheckable(true);
            chip.setTextColor(ContextCompat.getColor(this, R.color.route_tag_chip_text));
            chip.setChipBackgroundColor(
                    ColorStateList.valueOf(ContextCompat.getColor(this, R.color.route_tag_chip_bg)));
            chip.setId(View.generateViewId());
            chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (!isChecked) {
                    return;
                }
                if (chipGroup.getCheckedChipIds().size() > MAX_ROUTE_TAG_SELECTION) {
                    chip.setChecked(false);
                    Toast.makeText(this, "最多选择 3 个偏好", Toast.LENGTH_SHORT).show();
                }
            });
            chipGroup.addView(chip);
            chipIdToTagId.put(chip.getId(), tagId);
        }
    }

    private void setupListeners() {
        btnSubmit.setOnClickListener(v -> {
            clearAllErrors();

            String province = etProvince.getText() != null ? etProvince.getText().toString().trim() : "";
            String city = etCity.getText() != null ? etCity.getText().toString().trim() : "";

            if (TextUtils.isEmpty(province)) {
                tilProvince.setError("请选择省份");
                tilProvince.requestFocus();
                return;
            }

            if (TextUtils.isEmpty(city)) {
                tilCity.setError("请选择城市");
                tilCity.requestFocus();
                return;
            }

            if (cityMap.containsKey(province) && !isCityInProvince(city, province)) {
                tilCity.setError("请选择对应省份的城市");
                tilCity.requestFocus();
                return;
            }

            selectedTagIds.clear();
            selectedTagNames.clear();
            for (int chipId : chipGroup.getCheckedChipIds()) {
                Integer tagId = chipIdToTagId.get(chipId);
                View chipView = chipGroup.findViewById(chipId);
                if (tagId != null && chipView instanceof Chip) {
                    selectedTagIds.add(tagId);
                    selectedTagNames.add(((Chip) chipView).getText().toString());
                }
            }

            if (selectedTagIds.isEmpty()) {
                Toast.makeText(this, "请选择至少一个路线偏好", Toast.LENGTH_SHORT).show();
                return;
            }
            if (selectedTagIds.size() > MAX_ROUTE_TAG_SELECTION) {
                Toast.makeText(this, "最多选择 3 个偏好", Toast.LENGTH_SHORT).show();
                return;
            }

            String regionCode = resolveRegionCode(province, city);
            saveLocalPreferences(province, city, regionCode);

            Intent intent = new Intent(ProfileSetupActivity.this, RoleSelectionActivity.class);
            intent.putExtra(EXTRA_NAME, name);
            intent.putExtra(EXTRA_PHONE, phone);
            intent.putExtra(EXTRA_PASSWORD, password);
            intent.putExtra(EXTRA_CODE, code);
            startActivity(intent);
            finish();
        });
    }

    private boolean isCityInProvince(@NonNull String city, @NonNull String province) {
        String[] cities = cityMap.get(province);
        if (cities == null) {
            return true;
        }
        for (String item : cities) {
            if (city.equals(item)) {
                return true;
            }
        }
        return false;
    }

    private void clearAllErrors() {
        clearError(tilProvince);
        clearError(tilCity);
    }

    private void clearError(@NonNull TextInputLayout layout) {
        if (layout.isErrorEnabled()) {
            layout.setError(null);
            layout.setErrorEnabled(false);
        }
    }

    private void saveLocalPreferences(String province, String city, String regionCode) {
        SharedPreferences preferences = getSharedPreferences("user_pref", MODE_PRIVATE);
        preferences.edit()
                .putString("region_province", province)
                .putString("region_city", city)
                .putString("region_code", regionCode)
                .putString("route_tag_names", TextUtils.join(",", selectedTagNames))
                .putString("route_tag_ids", TextUtils.join(",", selectedTagIds))
                .apply();
    }

    private String resolveRegionCode(@NonNull String province, @NonNull String city) {
        String key = province + "-" + city;
        String code = regionCodeMap.get(key);
        if (!TextUtils.isEmpty(code)) {
            return code;
        }
        String provinceCode = provinceCodeMap.get(province);
        if (!TextUtils.isEmpty(provinceCode)) {
            return provinceCode + "0000";
        }
        return key;
    }

    private Map<String, String[]> buildCityMap() {
        Map<String, String[]> map = new LinkedHashMap<>();
        map.put("北京", new String[]{"北京"});
        map.put("天津", new String[]{"天津"});
        map.put("上海", new String[]{"上海"});
        map.put("重庆", new String[]{"重庆"});
        map.put("河北", new String[]{"石家庄"});
        map.put("山西", new String[]{"太原"});
        map.put("辽宁", new String[]{"沈阳"});
        map.put("吉林", new String[]{"长春"});
        map.put("黑龙江", new String[]{"哈尔滨"});
        map.put("江苏", new String[]{"南京"});
        map.put("浙江", new String[]{"杭州"});
        map.put("安徽", new String[]{"合肥"});
        map.put("福建", new String[]{"福州"});
        map.put("江西", new String[]{"南昌"});
        map.put("山东", new String[]{"济南"});
        map.put("河南", new String[]{"郑州"});
        map.put("湖北", new String[]{"武汉"});
        map.put("湖南", new String[]{"长沙"});
        map.put("广东", new String[]{"广州"});
        map.put("广西", new String[]{"南宁"});
        map.put("海南", new String[]{"海口"});
        map.put("四川", new String[]{"成都"});
        map.put("贵州", new String[]{"贵阳"});
        map.put("云南", new String[]{"昆明"});
        map.put("陕西", new String[]{"西安"});
        map.put("甘肃", new String[]{"兰州"});
        map.put("青海", new String[]{"西宁"});
        map.put("内蒙古", new String[]{"呼和浩特"});
        map.put("宁夏", new String[]{"银川"});
        map.put("新疆", new String[]{"乌鲁木齐"});
        map.put("西藏", new String[]{"拉萨"});
        map.put("香港", new String[]{"香港"});
        map.put("澳门", new String[]{"澳门"});
        map.put("台湾", new String[]{"台北"});
        return map;
    }

    private Map<String, String> buildRegionCodeMap() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("北京-北京", "110100");
        map.put("天津-天津", "120100");
        map.put("上海-上海", "310100");
        map.put("重庆-重庆", "500100");
        map.put("河北-石家庄", "130100");
        map.put("山西-太原", "140100");
        map.put("辽宁-沈阳", "210100");
        map.put("吉林-长春", "220100");
        map.put("黑龙江-哈尔滨", "230100");
        map.put("江苏-南京", "320100");
        map.put("浙江-杭州", "330100");
        map.put("安徽-合肥", "340100");
        map.put("福建-福州", "350100");
        map.put("江西-南昌", "360100");
        map.put("山东-济南", "370100");
        map.put("河南-郑州", "410100");
        map.put("湖北-武汉", "420100");
        map.put("湖南-长沙", "430100");
        map.put("广东-广州", "440100");
        map.put("广西-南宁", "450100");
        map.put("海南-海口", "460100");
        map.put("四川-成都", "510100");
        map.put("贵州-贵阳", "520100");
        map.put("云南-昆明", "530100");
        map.put("陕西-西安", "610100");
        map.put("甘肃-兰州", "620100");
        map.put("青海-西宁", "630100");
        map.put("内蒙古-呼和浩特", "150100");
        map.put("宁夏-银川", "640100");
        map.put("新疆-乌鲁木齐", "650100");
        map.put("西藏-拉萨", "540100");
        map.put("香港-香港", "810100");
        map.put("澳门-澳门", "820100");
        map.put("台湾-台北", "710100");
        return map;
    }

    private Map<String, String> buildProvinceCodeMap() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("北京", "11");
        map.put("天津", "12");
        map.put("河北", "13");
        map.put("山西", "14");
        map.put("内蒙古", "15");
        map.put("辽宁", "21");
        map.put("吉林", "22");
        map.put("黑龙江", "23");
        map.put("上海", "31");
        map.put("江苏", "32");
        map.put("浙江", "33");
        map.put("安徽", "34");
        map.put("福建", "35");
        map.put("江西", "36");
        map.put("山东", "37");
        map.put("河南", "41");
        map.put("湖北", "42");
        map.put("湖南", "43");
        map.put("广东", "44");
        map.put("广西", "45");
        map.put("海南", "46");
        map.put("重庆", "50");
        map.put("四川", "51");
        map.put("贵州", "52");
        map.put("云南", "53");
        map.put("西藏", "54");
        map.put("陕西", "61");
        map.put("甘肃", "62");
        map.put("青海", "63");
        map.put("宁夏", "64");
        map.put("新疆", "65");
        map.put("台湾", "71");
        map.put("香港", "81");
        map.put("澳门", "82");
        return map;
    }
}
