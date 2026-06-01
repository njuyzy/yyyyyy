package com.example.Japp.leader.fragment.profile;

import static android.content.Context.MODE_PRIVATE;

import android.Manifest;
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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.example.Japp.R;
import com.example.Japp.PersonalInfoActivity;
import com.example.Japp.RoleSelectionActivity;
import com.example.Japp.leader.LeaderHistoryRouteActivity;
import com.example.Japp.SettingsActivity;
import com.example.Japp.MainActivity;
import com.example.Japp.user.UserMainActivity;
import com.example.Japp.network.ApiClient;
import com.example.Japp.network.api.UserService;
import com.example.Japp.network.models.Account;
import com.example.Japp.network.models.Result;

import java.io.File;
import java.io.InputStream;
import java.net.URL;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class profile extends Fragment {

    private static final int REQUEST_PERSONAL_INFO = 1003;

    private Button switchMode, check_review, btnPersonalInfo, btnHistoryRoute;
    private Button btnLogout;
    private ImageView ivAvatar;
    private ImageButton Settings;
    private TextView txtName;
    private TextView txtStats;
    private static final int SELECT_FILE = 1002;
    private static final String AVATAR_UPLOAD_URL = "http://10.6.86.86/upload";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.leader_fragment_profile, container, false);

        ivAvatar = view.findViewById(R.id.ivAvatar);
        switchMode = view.findViewById(R.id.btnViewReviews);
        check_review = view.findViewById(R.id.btnSwitchRole);
        btnPersonalInfo = view.findViewById(R.id.btnPersonalInfo);
        btnHistoryRoute = view.findViewById(R.id.btnHistoryRoute);
        btnLogout = view.findViewById(R.id.btnLogout);
        Settings = view.findViewById(R.id.btnSettings);
        txtName = view.findViewById(R.id.txtName);
        txtStats = view.findViewById(R.id.txtStats);

        // 加载本地存储的图片
        loadImageFromLocal();
        bindProfileInfo();

        // 主要修改：为头像图片添加点击事件
        ivAvatar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showImagePickerDialog();
            }
        });

        if (!canSwitchRole()) {
            switchMode.setVisibility(View.GONE);
        } else {
            switchMode.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    switchMode();
                }
            });
        }

        check_review.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                review targetFragment = new review();
                FragmentTransaction transaction = getParentFragmentManager().beginTransaction();
                transaction.replace(R.id.container, targetFragment);
                transaction.addToBackStack(null);
                transaction.commit();
            }
        });

        btnPersonalInfo.setOnClickListener(v ->
                startActivityForResult(new Intent(requireContext(), PersonalInfoActivity.class), REQUEST_PERSONAL_INFO));

        btnHistoryRoute.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), LeaderHistoryRouteActivity.class)));

        Settings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(requireContext(), SettingsActivity.class));
            }
        });

        btnLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                logout();
            }
        });

        return view;
    }

    private void bindProfileInfo() {
        SharedPreferences prefs = requireContext().getSharedPreferences("user_pref", MODE_PRIVATE);
        String userInfo = prefs.getString("user_inf", "");
        String regionCode = prefs.getString("region_code", "");

        String username = "未登录用户";
        if (userInfo != null && userInfo.contains(" Username:") && userInfo.contains(" phoneNumber:")) {
            int start = userInfo.indexOf(" Username:") + " Username:".length();
            int end = userInfo.indexOf(" phoneNumber:");
            if (start >= 0 && end > start) {
                username = userInfo.substring(start, end).trim();
            }
        }

        txtName.setText(username);
        String regionName = regionCodeToName(regionCode);
        txtStats.setText(regionName);
        if ("所在地区".equals(regionName)) {
            fetchRegionFromServer();
        }
    }

    private String regionCodeToName(String code) {
        if (code == null) return "所在地区";
        String trimmed = code.trim();
        if (trimmed.isEmpty()) return "所在地区";
        if (!trimmed.matches("\\d+")) return trimmed;
        if (trimmed.length() >= 6) {
            String adcode = trimmed.substring(0, 6);
            if (adcode.endsWith("0000")) {
                String province = provinceCodeToName(adcode.substring(0, 2));
                return province != null ? province : trimmed;
            }
            if (adcode.endsWith("00")) {
                String city = cityCodeToName(adcode.substring(0, 4));
                if (city != null) return city;
                String province = provinceCodeToName(adcode.substring(0, 2));
                return province != null ? province : trimmed;
            }
        }
        if (trimmed.length() < 4) return trimmed;
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
            case "71": return "台湾省";
            case "81": return "香港特别行政区";
            case "82": return "澳门特别行政区";
            default: return null;
        }
    }

    private void fetchRegionFromServer() {
        if (!isAdded()) return;
        SharedPreferences prefs = requireContext().getSharedPreferences("user_pref", MODE_PRIVATE);
        int accountId = prefs.getInt("account_id", -1);
        if (accountId <= 0) return;

        UserService service = ApiClient.getClient().create(UserService.class);
        service.getAccount(accountId).enqueue(new Callback<Result<Account>>() {
            @Override
            public void onResponse(Call<Result<Account>> call, Response<Result<Account>> response) {
                if (!isAdded()) return;
                if (response.isSuccessful() && response.body() != null && response.body().getCode() == 1) {
                    Account account = response.body().getData();
                    if (account != null) {
                        String regionCode = account.getRegionCode();
                        if (regionCode != null && !regionCode.trim().isEmpty()) {
                            prefs.edit().putString("region_code", regionCode).apply();
                            txtStats.setText(regionCodeToName(regionCode));
                            return;
                        }
                    }
                }
                Toast.makeText(requireContext(), "未获取到地区信息", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(Call<Result<Account>> call, Throwable t) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), "获取地区失败，请检查网络", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void switchMode() {
        if (!canSwitchRole()) {
            Toast.makeText(requireContext(), "当前身份不可切换", Toast.LENGTH_SHORT).show();
            return;
        }
        SharedPreferences sharedPreferences = requireContext().getSharedPreferences("user_pref", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        editor.putString("Mode","USER").apply();

        Toast.makeText(requireContext(),"已切换到用户模式",Toast.LENGTH_SHORT).show();

        startActivity(new Intent(requireContext(), UserMainActivity.class));
        requireActivity().finish();
    }

    private boolean canSwitchRole() {
        SharedPreferences sharedPreferences = requireContext().getSharedPreferences("user_pref", MODE_PRIVATE);
        String scope = sharedPreferences.getString(RoleSelectionActivity.ROLE_SCOPE, "");
        return RoleSelectionActivity.ROLE_SCOPE_BOTH.equals(scope);
    }

    private void logout() {
        SharedPreferences prefs = requireContext().getSharedPreferences("user_pref", MODE_PRIVATE);
        prefs.edit()
                .putBoolean("is_logged_in", false)
                .remove("account_id")
                .remove("user_inf")
                .remove("region_code")
                .remove("avatar_url")
                .remove("Mode")
                .apply();
        ApiClient.clearToken();

        Toast.makeText(requireContext(), "已退出登录", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(requireContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    // 显示图片选择对话框
    private void showImagePickerDialog() {
        String[] items = {"加载图片", "取消"};
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(requireContext());
        builder.setTitle("选择图片来源");
        builder.setItems(items, (dialog, which) -> {
            switch (which) {
                case 0:
                    // 下载图片
                    requestStoragePermission();
                    break;
                case 1:
                    // 取消
                    dialog.dismiss();
                    break;
            }
        });
        builder.show();
    }


    // 请求存储权限
    private void requestStoragePermission() {
        String[] permissions = getImagePermissions();
        if (ContextCompat.checkSelfPermission(requireContext(), permissions[0]) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), permissions, SELECT_FILE);
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


    // 打开图片下载
    private void openGallery() {
        try {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            intent.setType("image/*");
            startActivityForResult(intent, SELECT_FILE);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "打开图库失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // 处理权限结果
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == SELECT_FILE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openGallery();
            } else {
                Toast.makeText(requireContext(), "没有授予存储权限", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // 处理回执结果
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PERSONAL_INFO && resultCode == android.app.Activity.RESULT_OK) {
            bindProfileInfo();
            loadImageFromLocal();
            return;
        }
        if (resultCode == requireActivity().RESULT_OK) {
            if (requestCode == SELECT_FILE) {
                if (data != null) {
                    Uri selectedImageUri = data.getData();
                    if (selectedImageUri != null) {
                        try {
                            Bitmap bitmap = ImageUtils.loadBitmapFromUri(requireContext(), selectedImageUri);
                            if (bitmap != null) {
                                applyAvatarBitmap(bitmap, true);
                                uploadAvatar(bitmap);
                                Toast.makeText(requireContext(), "图片更换成功", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(requireContext(), "无法加载图片", Toast.LENGTH_SHORT).show();
                            }
                        } catch (Exception e) {
                            Toast.makeText(requireContext(), "加载图片失败", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            }
        }
    }


    // 保存图片到本地
    private void saveImageToLocal(Bitmap bitmap) {
        try {
            String fileName = "user_avatar.jpg";
            if (ImageUtils.saveImageToInternalStorage(requireContext(), bitmap, fileName)) {
                Toast.makeText(requireContext(), "图片已保存", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(requireContext(), "保存图片失败", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(requireContext(), "保存图片失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void applyAvatarBitmap(Bitmap bitmap, boolean saveLocal) {
        Bitmap circular = cropToCircle(bitmap);
        Bitmap displayBitmap = circular != null ? circular : bitmap;
        ivAvatar.setImageBitmap(displayBitmap);
        if (saveLocal) {
            saveImageToLocalSilently(displayBitmap);
        }
    }

    private Bitmap cropToCircle(Bitmap source) {
        if (source == null) return null;
        int size = Math.min(source.getWidth(), source.getHeight());
        if (size <= 0) return null;
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

    private boolean saveImageToLocalSilently(Bitmap bitmap) {
        try {
            String fileName = "user_avatar.jpg";
            return ImageUtils.saveImageToInternalStorage(requireContext(), bitmap, fileName);
        } catch (Exception e) {
            return false;
        }
    }

    private void uploadAvatar(Bitmap bitmap) {
        if (!isAdded()) return;
        File tempFile = new File(requireContext().getCacheDir(), "avatar_upload_" + System.currentTimeMillis() + ".jpg");
        if (!ImageUtils.saveBitmapToFile(requireContext(), bitmap, tempFile)) {
            Toast.makeText(requireContext(), "头像保存失败，无法上传", Toast.LENGTH_SHORT).show();
            return;
        }

        RequestBody requestBody = RequestBody.create(MediaType.parse("image/*"), tempFile);
        MultipartBody.Part imagePart = MultipartBody.Part.createFormData("image", tempFile.getName(), requestBody);
        UserService service = ApiClient.getClient().create(UserService.class);
        service.uploadAvatar(AVATAR_UPLOAD_URL, imagePart).enqueue(new Callback<Result<String>>() {
            @Override
            public void onResponse(Call<Result<String>> call, Response<Result<String>> response) {
                if (!isAdded()) return;
                Result<String> result = response.body();
                if (response.isSuccessful() && result != null && result.getCode() == 1) {
                    String url = result.getData();
                    if (!TextUtils.isEmpty(url)) {
                        SharedPreferences prefs = requireContext().getSharedPreferences("user_pref", MODE_PRIVATE);
                        prefs.edit().putString("avatar_url", url).apply();
                        Toast.makeText(requireContext(), "头像上传成功", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(), "头像上传失败：返回地址为空", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    String reason = result != null ? result.getMsg() : null;
                    String message = TextUtils.isEmpty(reason)
                            ? "头像上传失败，请稍后重试"
                            : "头像上传失败：" + reason;
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                }
                tempFile.delete();
            }

            @Override
            public void onFailure(Call<Result<String>> call, Throwable t) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), "头像上传失败，请检查网络", Toast.LENGTH_SHORT).show();
                tempFile.delete();
            }
        });
    }

    private void loadAvatarFromUrl(String url) {
        new Thread(() -> {
            try (InputStream inputStream = new URL(url).openStream()) {
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                if (bitmap != null && isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        applyAvatarBitmap(bitmap, true);
                    });
                }
            } catch (Exception e) {
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> ivAvatar.setImageResource(R.drawable.ic_default_avatar));
                }
            }
        }).start();
    }

    // 加载本地图片
    private void loadImageFromLocal() {
        try {
            String fileName = "user_avatar.jpg";
            if (ImageUtils.isFileExists(requireContext(), fileName)) {
                Bitmap bitmap = ImageUtils.loadImageFromInternalStorage(requireContext(), fileName);
                if (bitmap != null) {
                    applyAvatarBitmap(bitmap, false);
                    return;
                }
            }
            SharedPreferences prefs = requireContext().getSharedPreferences("user_pref", MODE_PRIVATE);
            String avatarUrl = prefs.getString("avatar_url", "");
            if (!TextUtils.isEmpty(avatarUrl)) {
                loadAvatarFromUrl(avatarUrl);
            } else {
                ivAvatar.setImageResource(R.drawable.ic_default_avatar);
            }
        } catch (Exception e) {
            ivAvatar.setImageResource(R.drawable.ic_default_avatar);
        }
    }

}