package com.example.Japp;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Shader;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.Japp.data.User;
import com.example.Japp.leader.fragment.profile.ImageUtils;
import com.example.Japp.network.ApiClient;
import com.example.Japp.network.api.UserService;
import com.example.Japp.network.models.Account;
import com.example.Japp.network.models.AccountLeaderProfile;
import com.example.Japp.network.models.Result;
import com.example.Japp.network.models.requests.IntroRequest;
import com.example.Japp.network.models.requests.UpdateUsernameRequest;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.Calendar;
import java.util.Locale;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PersonalInfoActivity extends AppCompatActivity {

    private static final int SELECT_FILE = 2001;
    private static final String AVATAR_UPLOAD_URL = "http://10.6.86.86/upload";
    private static final String PREF_GENDER = "personal_gender";
    private static final String PREF_BIRTHDAY = "personal_birthday";
    private static final String PREF_SIGNATURE = "personal_signature";

    private ImageView ivAvatar;
    private TextInputEditText etName;
    private TextView txtGender;
    private TextView txtRegion;
    private TextView txtBirthday;
    private TextView txtPhone;
    private TextInputEditText etSignature;
    private MaterialButton btnSave;

    private int accountId = -1;
    private String selectedGender = "";
    private String selectedBirthday = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_personal_info);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.toolbar), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), systemBars.top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });

        bindViews();
        setupToolbar();
        setupListeners();
        loadProfileData();
    }

    private void bindViews() {
        ivAvatar = findViewById(R.id.ivAvatar);
        etName = findViewById(R.id.etName);
        txtGender = findViewById(R.id.txtGender);
        txtRegion = findViewById(R.id.txtRegion);
        txtBirthday = findViewById(R.id.txtBirthday);
        txtPhone = findViewById(R.id.txtPhone);
        etSignature = findViewById(R.id.etSignature);
        btnSave = findViewById(R.id.btnSave);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupListeners() {
        findViewById(R.id.rowAvatar).setOnClickListener(v -> requestImagePermissionAndPick());
        findViewById(R.id.rowGender).setOnClickListener(v -> showGenderDialog());
        findViewById(R.id.rowBirthday).setOnClickListener(v -> showBirthdayPicker());
        btnSave.setOnClickListener(v -> saveProfile());
    }

    private void loadProfileData() {
        SharedPreferences prefs = getSharedPreferences("user_pref", MODE_PRIVATE);
        accountId = prefs.getInt("account_id", -1);

        etName.setText(parseUsername(prefs.getString("user_inf", "")));
        txtPhone.setText(parsePhone(prefs.getString("user_inf", "")));
        txtRegion.setText(resolveRegionDisplay(prefs));

        selectedGender = prefs.getString(PREF_GENDER, "");
        selectedBirthday = prefs.getString(PREF_BIRTHDAY, "");
        updateGenderText();
        updateBirthdayText();

        String signature = prefs.getString(PREF_SIGNATURE, "");
        if (!TextUtils.isEmpty(signature)) {
            etSignature.setText(signature);
        }

        loadAvatarFromLocal();
        fetchRemoteProfile();
    }

    private void fetchRemoteProfile() {
        if (accountId <= 0) {
            return;
        }

        UserService service = ApiClient.getClient().create(UserService.class);
        service.getAccount(accountId).enqueue(new Callback<Result<Account>>() {
            @Override
            public void onResponse(@NonNull Call<Result<Account>> call, @NonNull Response<Result<Account>> response) {
                if (!response.isSuccessful() || response.body() == null || response.body().getCode() != 1) {
                    return;
                }
                Account account = response.body().getData();
                if (account == null) {
                    return;
                }
                if (!TextUtils.isEmpty(account.getUsername())) {
                    etName.setText(account.getUsername());
                }
                if (!TextUtils.isEmpty(account.getPhone())) {
                    txtPhone.setText(account.getPhone());
                }
                syncRegionFromAccount(account);
                if (!TextUtils.isEmpty(account.getAvatarUrl())) {
                    getSharedPreferences("user_pref", MODE_PRIVATE)
                            .edit()
                            .putString("avatar_url", account.getAvatarUrl())
                            .apply();
                    loadAvatarFromUrl(account.getAvatarUrl());
                }
            }

            @Override
            public void onFailure(@NonNull Call<Result<Account>> call, @NonNull Throwable t) {
            }
        });

        service.getLeaderProfile(accountId).enqueue(new Callback<Result<AccountLeaderProfile>>() {
            @Override
            public void onResponse(@NonNull Call<Result<AccountLeaderProfile>> call,
                                   @NonNull Response<Result<AccountLeaderProfile>> response) {
                if (!response.isSuccessful() || response.body() == null || response.body().getCode() != 1) {
                    return;
                }
                AccountLeaderProfile profile = response.body().getData();
                if (profile == null || TextUtils.isEmpty(profile.getIntro())) {
                    return;
                }
                SharedPreferences prefs = getSharedPreferences("user_pref", MODE_PRIVATE);
                if (TextUtils.isEmpty(prefs.getString(PREF_SIGNATURE, ""))) {
                    etSignature.setText(profile.getIntro());
                }
            }

            @Override
            public void onFailure(@NonNull Call<Result<AccountLeaderProfile>> call, @NonNull Throwable t) {
            }
        });
    }

    private void showGenderDialog() {
        final String[] options = {"男", "女", "保密"};
        new AlertDialog.Builder(this)
                .setTitle("选择性别")
                .setItems(options, (dialog, which) -> {
                    selectedGender = options[which];
                    updateGenderText();
                })
                .show();
    }

    private void updateGenderText() {
        if (TextUtils.isEmpty(selectedGender)) {
            txtGender.setText("");
            txtGender.setHint("请选择");
        } else {
            txtGender.setText(selectedGender);
        }
    }

    private void showBirthdayPicker() {
        Calendar calendar = Calendar.getInstance();
        if (!TextUtils.isEmpty(selectedBirthday)) {
            String[] parts = selectedBirthday.split("-");
            if (parts.length == 3) {
                try {
                    calendar.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, Integer.parseInt(parts[2]));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    selectedBirthday = String.format(Locale.CHINA, "%d-%02d-%02d", year, month + 1, dayOfMonth);
                    updateBirthdayText();
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );
        dialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        dialog.show();
    }

    private void updateBirthdayText() {
        if (TextUtils.isEmpty(selectedBirthday)) {
            txtBirthday.setText("");
            txtBirthday.setHint("请选择");
        } else {
            txtBirthday.setText(selectedBirthday);
        }
    }

    private void saveProfile() {
        String name = etName.getText() != null ? etName.getText().toString().trim() : "";
        String signature = etSignature.getText() != null ? etSignature.getText().toString().trim() : "";

        if (TextUtils.isEmpty(name)) {
            Toast.makeText(this, "姓名不能为空", Toast.LENGTH_SHORT).show();
            etName.requestFocus();
            return;
        }

        if (name.length() > 20) {
            Toast.makeText(this, "姓名长度不能超过20", Toast.LENGTH_SHORT).show();
            etName.requestFocus();
            return;
        }

        if (!name.matches("^[a-zA-Z0-9_\\u4e00-\\u9fa5]+$")) {
            Toast.makeText(this, "姓名仅支持中文、字母、数字和下划线", Toast.LENGTH_SHORT).show();
            etName.requestFocus();
            return;
        }

        SharedPreferences prefs = getSharedPreferences("user_pref", MODE_PRIVATE);
        prefs.edit()
                .putString(PREF_GENDER, selectedGender)
                .putString(PREF_BIRTHDAY, selectedBirthday)
                .putString(PREF_SIGNATURE, signature)
                .apply();

        btnSave.setEnabled(false);
        btnSave.setText("保存中...");

        if (accountId <= 0) {
            updateLocalUsername(name);
            finishSaving(true);
            return;
        }

        UserService service = ApiClient.getClient().create(UserService.class);
        service.updateUsername(accountId, new UpdateUsernameRequest(name)).enqueue(new Callback<Result>() {
            @Override
            public void onResponse(@NonNull Call<Result> call, @NonNull Response<Result> response) {
                if (!response.isSuccessful() || response.body() == null || response.body().getCode() != 1) {
                    resetSaveButton();
                    String msg = response.body() != null ? response.body().getMsg() : null;
                    Toast.makeText(PersonalInfoActivity.this,
                            TextUtils.isEmpty(msg) ? "姓名保存失败，请重试" : msg,
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                updateLocalUsername(name);
                uploadIntro(service, signature, name);
            }

            @Override
            public void onFailure(@NonNull Call<Result> call, @NonNull Throwable t) {
                resetSaveButton();
                Toast.makeText(PersonalInfoActivity.this, "网络连接失败，请检查网络", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void uploadIntro(UserService service, String signature, String name) {
        service.updateIntro(accountId, new IntroRequest(signature)).enqueue(new Callback<Result>() {
            @Override
            public void onResponse(@NonNull Call<Result> call, @NonNull Response<Result> response) {
                boolean introSuccess = response.isSuccessful()
                        && response.body() != null
                        && response.body().getCode() == 1;
                if (!introSuccess) {
                    Toast.makeText(PersonalInfoActivity.this, "姓名已保存，个性签名同步失败", Toast.LENGTH_SHORT).show();
                }
                finishSaving(true);
            }

            @Override
            public void onFailure(@NonNull Call<Result> call, @NonNull Throwable t) {
                Toast.makeText(PersonalInfoActivity.this, "姓名已保存，个性签名同步失败", Toast.LENGTH_SHORT).show();
                finishSaving(true);
            }
        });
    }

    private void resetSaveButton() {
        btnSave.setEnabled(true);
        btnSave.setText("保存");
    }

    private void finishSaving(boolean success) {
        resetSaveButton();
        if (!success) {
            return;
        }
        Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }

    private void updateLocalUsername(String newName) {
        SharedPreferences prefs = getSharedPreferences("user_pref", MODE_PRIVATE);
        String userInfo = prefs.getString("user_inf", "");
        if (TextUtils.isEmpty(userInfo)) {
            return;
        }

        String id = parseId(userInfo);
        String phone = parsePhone(userInfo);
        String password = parsePassword(userInfo);

        User user = new User(newName, phone, password);
        if (!TextUtils.isEmpty(id)) {
            user.setId(id);
        }
        prefs.edit().putString("user_inf", user.toString()).apply();
    }

    private String parseId(String userInfo) {
        if (TextUtils.isEmpty(userInfo) || !userInfo.startsWith("id:")) {
            return "";
        }
        int end = userInfo.indexOf(" Username:");
        if (end > 3) {
            return userInfo.substring(3, end).trim();
        }
        return "";
    }

    private String parsePassword(String userInfo) {
        if (TextUtils.isEmpty(userInfo) || !userInfo.contains(" password:")) {
            return "";
        }
        int start = userInfo.indexOf(" password:") + " password:".length();
        return userInfo.substring(start).trim();
    }

    private String parseUsername(String userInfo) {
        if (TextUtils.isEmpty(userInfo) || !userInfo.contains(" Username:") || !userInfo.contains(" phoneNumber:")) {
            return "未登录用户";
        }
        int start = userInfo.indexOf(" Username:") + " Username:".length();
        int end = userInfo.indexOf(" phoneNumber:");
        if (start >= 0 && end > start) {
            return userInfo.substring(start, end).trim();
        }
        return "未登录用户";
    }

    private String parsePhone(String userInfo) {
        if (TextUtils.isEmpty(userInfo) || !userInfo.contains(" phoneNumber:")) {
            return "";
        }
        int start = userInfo.indexOf(" phoneNumber:") + " phoneNumber:".length();
        int end = userInfo.indexOf(" password:");
        if (end > start) {
            return userInfo.substring(start, end).trim();
        }
        return userInfo.substring(start).trim();
    }

    private String resolveRegionDisplay(SharedPreferences prefs) {
        String province = prefs.getString("region_province", "");
        String city = prefs.getString("region_city", "");
        if (!TextUtils.isEmpty(province) && !TextUtils.isEmpty(city)) {
            return province + " " + city;
        }
        String regionCode = prefs.getString("region_code", "");
        if (!TextUtils.isEmpty(regionCode)) {
            return regionCodeToName(regionCode);
        }
        return "未设置";
    }

    private String regionCodeToName(String code) {
        if (code == null) {
            return "未设置";
        }
        String trimmed = code.trim();
        if (trimmed.isEmpty()) {
            return "未设置";
        }
        if (!trimmed.matches("\\d+")) {
            return trimmed;
        }
        if (trimmed.length() >= 6) {
            String adcode = trimmed.substring(0, 6);
            if (adcode.endsWith("0000")) {
                String province = provinceCodeToName(adcode.substring(0, 2));
                return province != null ? province : trimmed;
            }
            if (adcode.endsWith("00")) {
                String city = cityCodeToName(adcode.substring(0, 4));
                if (city != null) {
                    return city;
                }
                String province = provinceCodeToName(adcode.substring(0, 2));
                return province != null ? province : trimmed;
            }
        }
        if (trimmed.length() < 4) {
            return trimmed;
        }
        String city = cityCodeToName(trimmed.substring(0, 4));
        return city != null ? city : trimmed;
    }

    private String cityCodeToName(String codePrefix) {
        switch (codePrefix) {
            case "1101": return "北京市";
            case "1201": return "天津市";
            case "2101": return "沈阳市";
            case "2201": return "长春市";
            case "2301": return "哈尔滨市";
            case "3101": return "上海市";
            case "3201": return "南京市";
            case "3301": return "杭州市";
            case "3501": return "福州市";
            case "3601": return "南昌市";
            case "3701": return "济南市";
            case "4101": return "郑州市";
            case "4201": return "武汉市";
            case "4301": return "长沙市";
            case "4401": return "广州市";
            case "4403": return "深圳市";
            case "4501": return "南宁市";
            case "5001": return "重庆市";
            case "5101": return "成都市";
            case "5201": return "贵阳市";
            case "5301": return "昆明市";
            case "6101": return "西安市";
            default: return null;
        }
    }

    private String provinceCodeToName(String provinceCode) {
        switch (provinceCode) {
            case "11": return "北京市";
            case "12": return "天津市";
            case "13": return "河北省";
            case "14": return "山西省";
            case "15": return "内蒙古自治区";
            case "21": return "辽宁省";
            case "22": return "吉林省";
            case "23": return "黑龙江省";
            case "31": return "上海市";
            case "32": return "江苏省";
            case "33": return "浙江省";
            case "34": return "安徽省";
            case "35": return "福建省";
            case "36": return "江西省";
            case "37": return "山东省";
            case "41": return "河南省";
            case "42": return "湖北省";
            case "43": return "湖南省";
            case "44": return "广东省";
            case "45": return "广西壮族自治区";
            case "46": return "海南省";
            case "50": return "重庆市";
            case "51": return "四川省";
            case "52": return "贵州省";
            case "53": return "云南省";
            case "54": return "西藏自治区";
            case "61": return "陕西省";
            case "62": return "甘肃省";
            case "63": return "青海省";
            case "64": return "宁夏回族自治区";
            case "65": return "新疆维吾尔自治区";
            default: return null;
        }
    }

    private void requestImagePermissionAndPick() {
        String[] permissions = getImagePermissions();
        if (ContextCompat.checkSelfPermission(this, permissions[0]) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, permissions, SELECT_FILE);
        } else {
            openGallery();
        }
    }

    private String[] getImagePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return new String[]{Manifest.permission.READ_MEDIA_IMAGES};
        }
        return new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
    }

    private void openGallery() {
        try {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            intent.setType("image/*");
            startActivityForResult(intent, SELECT_FILE);
        } catch (Exception e) {
            Toast.makeText(this, "打开图库失败", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == SELECT_FILE && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openGallery();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || requestCode != SELECT_FILE || data == null) {
            return;
        }
        Uri selectedImageUri = data.getData();
        if (selectedImageUri == null) {
            return;
        }
        Bitmap bitmap = ImageUtils.loadBitmapFromUri(this, selectedImageUri);
        if (bitmap == null) {
            Toast.makeText(this, "无法加载图片", Toast.LENGTH_SHORT).show();
            return;
        }
        applyAvatarBitmap(bitmap, true);
        uploadAvatar(bitmap);
    }

    private void applyAvatarBitmap(Bitmap bitmap, boolean saveLocal) {
        Bitmap circular = cropToCircle(bitmap);
        Bitmap displayBitmap = circular != null ? circular : bitmap;
        ivAvatar.setImageBitmap(displayBitmap);
        if (saveLocal) {
            ImageUtils.saveImageToInternalStorage(this, displayBitmap, "user_avatar.jpg");
        }
    }

    private Bitmap cropToCircle(Bitmap source) {
        if (source == null) {
            return null;
        }
        int size = Math.min(source.getWidth(), source.getHeight());
        if (size <= 0) {
            return null;
        }
        int x = (source.getWidth() - size) / 2;
        int y = (source.getHeight() - size) / 2;
        Bitmap squared = Bitmap.createBitmap(source, x, y, size, size);
        Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new BitmapShader(squared, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        float radius = size / 2f;
        canvas.drawCircle(radius, radius, radius, paint);
        return output;
    }

    private void uploadAvatar(Bitmap bitmap) {
        File tempFile = new File(getCacheDir(), "avatar_upload_" + System.currentTimeMillis() + ".jpg");
        if (!ImageUtils.saveBitmapToFile(this, bitmap, tempFile)) {
            Toast.makeText(this, "头像保存失败", Toast.LENGTH_SHORT).show();
            return;
        }

        RequestBody requestBody = RequestBody.create(MediaType.parse("image/*"), tempFile);
        MultipartBody.Part imagePart = MultipartBody.Part.createFormData("image", tempFile.getName(), requestBody);
        UserService service = ApiClient.getClient().create(UserService.class);
        service.uploadAvatar(AVATAR_UPLOAD_URL, imagePart).enqueue(new Callback<Result<String>>() {
            @Override
            public void onResponse(@NonNull Call<Result<String>> call, @NonNull Response<Result<String>> response) {
                tempFile.delete();
                Result<String> result = response.body();
                if (response.isSuccessful() && result != null && result.getCode() == 1) {
                    String url = result.getData();
                    if (!TextUtils.isEmpty(url)) {
                        getSharedPreferences("user_pref", MODE_PRIVATE)
                                .edit()
                                .putString("avatar_url", url)
                                .apply();
                        Toast.makeText(PersonalInfoActivity.this, "头像已更新", Toast.LENGTH_SHORT).show();
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<Result<String>> call, @NonNull Throwable t) {
                tempFile.delete();
                Toast.makeText(PersonalInfoActivity.this, "头像上传失败", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadAvatarFromLocal() {
        if (ImageUtils.isFileExists(this, "user_avatar.jpg")) {
            Bitmap bitmap = ImageUtils.loadImageFromInternalStorage(this, "user_avatar.jpg");
            if (bitmap != null) {
                applyAvatarBitmap(bitmap, false);
                return;
            }
        }
        String avatarUrl = getSharedPreferences("user_pref", MODE_PRIVATE).getString("avatar_url", "");
        if (!TextUtils.isEmpty(avatarUrl)) {
            loadAvatarFromUrl(avatarUrl);
        }
    }

    private void loadAvatarFromUrl(String url) {
        new Thread(() -> {
            try (InputStream inputStream = new URL(url).openStream()) {
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                if (bitmap != null) {
                    runOnUiThread(() -> applyAvatarBitmap(bitmap, true));
                }
            } catch (Exception ignored) {
            }
        }).start();
    }

    private void syncRegionFromAccount(Account account) {
        if (account == null || TextUtils.isEmpty(account.getRegionCode())) {
            return;
        }
        SharedPreferences prefs = getSharedPreferences("user_pref", MODE_PRIVATE);
        prefs.edit()
                .putString("region_code", account.getRegionCode())
                .remove("region_province")
                .remove("region_city")
                .apply();
        txtRegion.setText(resolveRegionDisplay(prefs));
    }
}
