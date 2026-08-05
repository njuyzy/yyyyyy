package com.example.Japp.Chat.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.Japp.network.api.UserService;
import com.example.Japp.network.models.ChatSession;
import com.example.Japp.network.models.Result;
import com.example.Japp.network.models.ServerChatMessage;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public final class ChatUnreadManager {

    private static final String PREFS_NAME = "chat_unread_state";
    private static final String KEY_PREFIX = "last_read_";
    private static final String CACHE_COUNT_PREFIX = "cached_count_";
    private static final String CACHE_LATEST_PREFIX = "cached_latest_";

    private ChatUnreadManager() {
    }

    public interface RefreshCallback {
        void onRefreshed(Map<Long, Integer> unreadBySession, int totalUnread);

        default void onFailure() {
        }
    }

    public static int calculateUnread(Context context, int accountId, long sessionId,
                                      List<ServerChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            initializeIfNeeded(context, accountId, sessionId, 0L);
            return 0;
        }

        long latestId = latestMessageId(messages);
        SharedPreferences preferences = preferences(context);
        String key = key(accountId, sessionId);
        if (!preferences.contains(key)) {
            preferences.edit().putLong(key, latestId).apply();
            return 0;
        }

        long lastReadId = preferences.getLong(key, 0L);
        int unread = 0;
        for (ServerChatMessage message : messages) {
            if (message.getId() > lastReadId
                    && message.getSenderAccountId() != accountId) {
                unread++;
            }
        }
        preferences.edit().putInt(countKey(accountId, sessionId), unread).apply();
        return unread;
    }

    public static void markSessionRead(Context context, int accountId, long sessionId,
                                       List<ServerChatMessage> messages) {
        markSessionRead(context, accountId, sessionId, latestMessageId(messages));
    }

    public static void markSessionRead(Context context, int accountId,
                                       long sessionId, long latestMessageId) {
        if (accountId <= 0 || sessionId <= 0 || latestMessageId < 0) {
            return;
        }
        preferences(context).edit()
                .putLong(key(accountId, sessionId), latestMessageId)
                .putInt(countKey(accountId, sessionId), 0)
                .apply();
    }

    public static void refresh(Context context, UserService service, int accountId,
                               RefreshCallback callback) {
        if (accountId <= 0) {
            callback.onRefreshed(Collections.emptyMap(), 0);
            return;
        }
        Context appContext = context.getApplicationContext();
        service.getChatSessions().enqueue(new Callback<Result<List<ChatSession>>>() {
            @Override
            public void onResponse(Call<Result<List<ChatSession>>> call,
                                   Response<Result<List<ChatSession>>> response) {
                if (!response.isSuccessful() || response.body() == null
                        || response.body().getCode() != 1) {
                    callback.onFailure();
                    return;
                }
                if (response.body().getData() == null
                        || response.body().getData().isEmpty()) {
                    callback.onRefreshed(Collections.emptyMap(), 0);
                    return;
                }

                List<ChatSession> sessions = response.body().getData();
                Map<Long, Integer> unreadBySession = new HashMap<>();
                AtomicInteger pending = new AtomicInteger(sessions.size());
                AtomicInteger total = new AtomicInteger(0);
                AtomicBoolean failed = new AtomicBoolean(false);
                for (ChatSession session : sessions) {
                    long sessionId = session.getId();
                    Integer cachedUnread = cachedUnreadIfCurrent(
                            appContext, accountId, session);
                    if (cachedUnread != null) {
                        unreadBySession.put(sessionId, cachedUnread);
                        total.addAndGet(cachedUnread);
                        finishOne(pending, unreadBySession, total, failed, callback);
                        continue;
                    }
                    service.getChatMessages(sessionId)
                            .enqueue(new Callback<Result<List<ServerChatMessage>>>() {
                                @Override
                                public void onResponse(
                                        Call<Result<List<ServerChatMessage>>> call,
                                        Response<Result<List<ServerChatMessage>>> response) {
                                    int unread = 0;
                                    if (response.isSuccessful() && response.body() != null
                                            && response.body().getCode() == 1) {
                                        unread = calculateUnread(
                                                appContext,
                                                accountId,
                                                sessionId,
                                                response.body().getData());
                                        cacheLatestMarker(
                                                appContext,
                                                accountId,
                                                session,
                                                unread);
                                    } else {
                                        failed.set(true);
                                    }
                                    unreadBySession.put(sessionId, unread);
                                    total.addAndGet(unread);
                                    finishOne(pending, unreadBySession, total, failed, callback);
                                }

                                @Override
                                public void onFailure(
                                    Call<Result<List<ServerChatMessage>>> call,
                                        Throwable t) {
                                    failed.set(true);
                                    unreadBySession.put(sessionId, 0);
                                    finishOne(pending, unreadBySession, total, failed, callback);
                                }
                            });
                }
            }

            @Override
            public void onFailure(Call<Result<List<ChatSession>>> call, Throwable t) {
                callback.onFailure();
            }
        });
    }

    public static long latestMessageId(List<ServerChatMessage> messages) {
        long latestId = 0L;
        if (messages != null) {
            for (ServerChatMessage message : messages) {
                latestId = Math.max(latestId, message.getId());
            }
        }
        return latestId;
    }

    private static void initializeIfNeeded(Context context, int accountId,
                                           long sessionId, long latestId) {
        SharedPreferences preferences = preferences(context);
        String key = key(accountId, sessionId);
        if (!preferences.contains(key)) {
            preferences.edit().putLong(key, latestId).apply();
        }
    }

    private static void finishOne(AtomicInteger pending,
                                  Map<Long, Integer> unreadBySession,
                                  AtomicInteger total,
                                  AtomicBoolean failed,
                                  RefreshCallback callback) {
        if (pending.decrementAndGet() == 0) {
            if (failed.get()) {
                callback.onFailure();
            } else {
                callback.onRefreshed(new HashMap<>(unreadBySession), total.get());
            }
        }
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static Integer cachedUnreadIfCurrent(Context context, int accountId,
                                                 ChatSession session) {
        SharedPreferences preferences = preferences(context);
        String latestKey = latestKey(accountId, session.getId());
        String countKey = countKey(accountId, session.getId());
        if (!preferences.contains(latestKey) || !preferences.contains(countKey)) {
            return null;
        }
        String cachedLatest = preferences.getString(latestKey, "");
        String currentLatest = marker(session);
        return currentLatest.equals(cachedLatest)
                ? preferences.getInt(countKey, 0) : null;
    }

    private static void cacheLatestMarker(Context context, int accountId,
                                          ChatSession session, int unread) {
        preferences(context).edit()
                .putString(latestKey(accountId, session.getId()), marker(session))
                .putInt(countKey(accountId, session.getId()), unread)
                .apply();
    }

    private static String marker(ChatSession session) {
        String sentAt = session.getLatestMessageAt() == null
                ? "" : session.getLatestMessageAt();
        String content = session.getLatestMessage() == null
                ? "" : session.getLatestMessage();
        return sentAt + "\u0001" + content;
    }

    private static String key(int accountId, long sessionId) {
        return KEY_PREFIX + accountId + "_" + sessionId;
    }

    private static String countKey(int accountId, long sessionId) {
        return CACHE_COUNT_PREFIX + accountId + "_" + sessionId;
    }

    private static String latestKey(int accountId, long sessionId) {
        return CACHE_LATEST_PREFIX + accountId + "_" + sessionId;
    }
}
