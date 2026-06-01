package com.example.Japp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.Japp.network.ApiClient;

public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DURATION = 2000; // 2秒
    private static final int REQ_PERMISSIONS = 1001;
    private Handler handler = new Handler();
    private Runnable runnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.splash_screen);

        // 初始化 ApiClient（用于后续请求自动携带 token）
        ApiClient.init(this);

        // 初始化动画
        initializeAnimations();

        // 先申请运行时权限，再进入主页面
        checkAndRequestPermissions();
    }

    private void initializeAnimations() {
        // 加载动画
        Animation fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in);
        Animation slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up);

        // 获取控件
        ImageView logo = findViewById(R.id.logo);
        TextView versionInfo = findViewById(R.id.version_info);

        // 应用动画
        if (logo != null) {
            logo.startAnimation(fadeIn);
        }
        if (versionInfo != null) {
            versionInfo.startAnimation(fadeIn);
        }
    }

    private void startSplashTimer() {
        runnable = new Runnable() {
            @Override
            public void run() {
                // 跳转到主页面
                navigateToMainActivity();
            }
        };
        handler.postDelayed(runnable, SPLASH_DURATION);
    }

    private void checkAndRequestPermissions() {
        String[] required = getRequiredPermissions();
        if (required.length == 0) {
            startSplashTimer();
            return;
        }

        boolean allGranted = true;
        for (String permission : required) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (allGranted) {
            startSplashTimer();
        } else {
            ActivityCompat.requestPermissions(this, required, REQ_PERMISSIONS);
        }
    }

    private String[] getRequiredPermissions() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) { // Android 13以下才申请存储权限
            return new String[] {
                Manifest.permission.CAMERA,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            };
        }
        return new String[] {
            Manifest.permission.CAMERA,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        };
    }

    private void navigateToMainActivity() {
        try {
            Intent intent = new Intent(SplashActivity.this, MainActivity.class);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        } catch (Exception e) {
            e.printStackTrace();
            // 如果MainActivity启动失败，直接结束
            finish();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_PERMISSIONS) {
            return;
        }

        boolean allGranted = true;
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (!allGranted) {
            Toast.makeText(this, "部分权限未授予，功能可能受限", Toast.LENGTH_SHORT).show();
        }
        startSplashTimer();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 避免内存泄漏
        if (handler != null && runnable != null) {
            handler.removeCallbacks(runnable);
        }
    }
}