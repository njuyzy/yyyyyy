package com.example.Japp.Chat.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.util.LruCache;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.InputStream;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ChatAvatarLoader {

    private static final String SERVER_BASE_URL = "http://47.94.95.110:8080/";
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(3);
    private static final LruCache<String, Bitmap> CACHE = new LruCache<>(32);

    private ChatAvatarLoader() {
    }

    public static void bind(ImageView imageView, TextView fallback,
                            String avatarUrl, String displayName) {
        String initial = !TextUtils.isEmpty(displayName)
                ? String.valueOf(displayName.charAt(0)).toUpperCase(Locale.getDefault())
                : "?";
        fallback.setText(initial);
        fallback.setVisibility(View.VISIBLE);

        imageView.setTag(null);
        imageView.setImageDrawable(null);
        imageView.setVisibility(View.GONE);

        String normalizedUrl = normalizeUrl(avatarUrl);
        if (TextUtils.isEmpty(normalizedUrl)) {
            return;
        }
        imageView.setTag(normalizedUrl);
        Bitmap cached = CACHE.get(normalizedUrl);
        if (cached != null) {
            show(imageView, fallback, normalizedUrl, cached);
            return;
        }
        EXECUTOR.execute(() -> load(imageView, fallback, normalizedUrl));
    }

    private static String normalizeUrl(String avatarUrl) {
        if (TextUtils.isEmpty(avatarUrl)) {
            return null;
        }
        String normalized = avatarUrl.trim();
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            return normalized;
        }
        return SERVER_BASE_URL
                + (normalized.startsWith("/") ? normalized.substring(1) : normalized);
    }

    private static void load(ImageView imageView, TextView fallback, String avatarUrl) {
        try (InputStream inputStream = new URL(avatarUrl).openStream()) {
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            if (bitmap != null) {
                CACHE.put(avatarUrl, bitmap);
                imageView.post(() -> show(imageView, fallback, avatarUrl, bitmap));
            }
        } catch (Exception ignored) {
            // Keep the initials fallback when the remote avatar is unavailable.
        }
    }

    private static void show(ImageView imageView, TextView fallback,
                             String avatarUrl, Bitmap bitmap) {
        if (!avatarUrl.equals(imageView.getTag())) {
            return;
        }
        imageView.setImageBitmap(bitmap);
        imageView.setVisibility(View.VISIBLE);
        fallback.setVisibility(View.GONE);
    }
}
