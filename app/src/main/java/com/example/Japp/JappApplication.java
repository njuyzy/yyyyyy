package com.example.Japp;

import android.app.Application;

import com.example.Japp.database.DatabaseManager;

public class JappApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // 初始化数据库
        DatabaseManager.getInstance(this);
        com.example.Japp.network.ApiClient.init(this);
    }

    @Override
    public void onTerminate() {
        super.onTerminate();

        // 应用退出时关闭数据库
        DatabaseManager.getInstance(this).closeDatabase();
    }
}