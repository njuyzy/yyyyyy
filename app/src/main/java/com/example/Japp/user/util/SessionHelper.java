package com.example.Japp.user.util;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.example.Japp.MainActivity;
import com.example.Japp.network.ApiClient;

public final class SessionHelper {

    private static final String PREFS = "user_pref";

    private SessionHelper() {}

    public static int getAccountId(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt("account_id", -1);
    }

    public static boolean isLoggedIn(Context context) {
        int accountId = getAccountId(context);
        String token = ApiClient.getToken();
        return accountId > 0 && token != null && !token.trim().isEmpty();
    }

    public static void handleUnauthorized(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit()
                .putBoolean("is_logged_in", false)
                .remove("account_id")
                .apply();
        ApiClient.clearToken();

        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);
    }
}
