package com.example.Japp.network;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
    private static final String BASE_URL = "http://47.94.95.110:8080/";
    private static final int AI_ROUTE_READ_TIMEOUT_SECONDS = 300;
    public static volatile Retrofit retrofit;
    private static Context appContext;

    public static void init(Context context) {
        appContext = context.getApplicationContext();
    }

    public static String getToken() {
        if (appContext == null) return null;
        SharedPreferences prefs = appContext.getSharedPreferences("user_pref", Context.MODE_PRIVATE);
        return prefs.getString("token", null);
    }

    public static void saveToken(String token) {
        if (appContext == null) return;
        SharedPreferences prefs = appContext.getSharedPreferences("user_pref", Context.MODE_PRIVATE);
        prefs.edit().putString("token", token).apply();
    }

    public static void saveTokens(String token, String refreshToken) {
        if (appContext == null) return;
        SharedPreferences.Editor editor = appContext
                .getSharedPreferences("user_pref", Context.MODE_PRIVATE).edit();
        if (token == null) editor.remove("token");
        else editor.putString("token", token);
        if (refreshToken == null) editor.remove("refresh_token");
        else editor.putString("refresh_token", refreshToken);
        editor.apply();
    }

    public static String getRefreshToken() {
        if (appContext == null) return null;
        return appContext.getSharedPreferences("user_pref", Context.MODE_PRIVATE)
                .getString("refresh_token", null);
    }

    public static void clearToken() {
        if (appContext == null) return;
        SharedPreferences prefs = appContext.getSharedPreferences("user_pref", Context.MODE_PRIVATE);
        prefs.edit().remove("token").remove("refresh_token").apply();
    }

    public static Retrofit getClient() {
        if (retrofit == null) {
            synchronized (ApiClient.class) {
                if (retrofit == null) {
                    OkHttpClient client = new OkHttpClient.Builder()
                            .connectTimeout(30, TimeUnit.SECONDS)
                            .readTimeout(120, TimeUnit.SECONDS)
                            .writeTimeout(30, TimeUnit.SECONDS)
                            .addInterceptor(chain -> {
                                Request request = chain.request();
                                Request.Builder builder = request.newBuilder();
                                String token = getToken();
                                if (token != null && !token.trim().isEmpty()) {
                                    String normalized = token.trim();
                                    if (!normalized.regionMatches(true, 0, "Bearer ", 0, 7)) {
                                        normalized = "Bearer " + normalized;
                                    }
                                    builder.header("Authorization", normalized);
                                }
                                if (request.url().encodedPath().startsWith("/routes/ai/")) {
                                    return chain.withReadTimeout(
                                                    AI_ROUTE_READ_TIMEOUT_SECONDS,
                                                    TimeUnit.SECONDS)
                                            .proceed(builder.build());
                                }
                                return chain.proceed(builder.build());
                            })
                            .build();

                    retrofit = new Retrofit.Builder()
                            .baseUrl(BASE_URL)
                            .client(client)
                            .addConverterFactory(GsonConverterFactory.create())
                            .build();
                }
            }
        }
        return retrofit;
    }
}
