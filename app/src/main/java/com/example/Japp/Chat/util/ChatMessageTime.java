package com.example.Japp.Chat.util;

import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.example.Japp.network.models.ServerChatMessage;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class ChatMessageTime {

    private static final String[] SERVER_PATTERNS = {
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss"
    };

    private ChatMessageTime() {}

    public static long timestamp(@Nullable ServerChatMessage message) {
        if (message == null) return 0L;
        if (message.getLocalTimestamp() > 0) return message.getLocalTimestamp();
        String sentAt = message.getSentAt();
        if (!TextUtils.isEmpty(sentAt)) {
            for (String pattern : SERVER_PATTERNS) {
                try {
                    Date parsed = new SimpleDateFormat(pattern, Locale.ROOT).parse(sentAt);
                    if (parsed != null) return parsed.getTime();
                } catch (Exception ignored) {
                    // Try the next server time format.
                }
            }
        }
        return Math.max(1L, message.getId());
    }
}
