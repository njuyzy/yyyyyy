package com.example.Japp;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import com.example.Japp.database.DatabaseManager;
import com.example.Japp.util.DisplayCutoutAdapter;

public class JappApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // 初始化数据库
        DatabaseManager.getInstance(this);
        com.example.Japp.network.ApiClient.init(this);
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                DisplayCutoutAdapter.apply(activity);
            }

            @Override
            public void onActivityStarted(Activity activity) {
            }

            @Override
            public void onActivityResumed(Activity activity) {
            }

            @Override
            public void onActivityPaused(Activity activity) {
            }

            @Override
            public void onActivityStopped(Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
            }
        });
    }

    @Override
    public void onTerminate() {
        super.onTerminate();

        // 应用退出时关闭数据库
        DatabaseManager.getInstance(this).closeDatabase();
    }
}