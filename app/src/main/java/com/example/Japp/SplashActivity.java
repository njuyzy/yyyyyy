package com.example.Japp;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.Japp.network.ApiClient;

public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DURATION = 2000; // 2秒
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

        // 启动延迟跳转
        startSplashTimer();
    }

    private void initializeAnimations() {
        // 加载动画
        Animation fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in);
        Animation slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up);

        // 获取控件
        ImageView logo = findViewById(R.id.logo);
        TextView appName = findViewById(R.id.app_name);
        TextView versionInfo = findViewById(R.id.version_info);

        // 应用动画
        if (logo != null) {
            logo.startAnimation(fadeIn);
        }
        if (appName != null) {
            appName.startAnimation(slideUp);
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
    protected void onDestroy() {
        super.onDestroy();
        // 避免内存泄漏
        if (handler != null && runnable != null) {
            handler.removeCallbacks(runnable);
        }
    }
}