package com.example.Japp.Chat.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.Japp.network.models.ServerChatMessage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 聊天消息的前端游标分页规则。 */
public final class ChatMessagePaging {

    public static final int PAGE_SIZE = 30;
    public static final int PREFETCH_DISTANCE = 20;

    private ChatMessagePaging() {}

    /**
     * 兼容分页接口和旧版全量接口：筛出 beforeId 之前、最接近游标的 30 条，
     * 并统一为时间正序。
     */
    @NonNull
    public static List<ServerChatMessage> pageBefore(
            @Nullable List<ServerChatMessage> source, @Nullable Long beforeId) {
        List<ServerChatMessage> candidates = new ArrayList<>();
        if (source != null) {
            for (ServerChatMessage message : source) {
                if (message == null) continue;
                if (beforeId == null || message.getId() < beforeId) {
                    candidates.add(message);
                }
            }
        }
        candidates.sort(Comparator.comparingLong(ServerChatMessage::getId));
        int from = Math.max(0, candidates.size() - PAGE_SIZE);
        return new ArrayList<>(candidates.subList(from, candidates.size()));
    }

    /** 当前响应之后是否可能还有更早消息。 */
    public static boolean mayHaveOlder(@Nullable List<ServerChatMessage> response,
                                       @NonNull List<ServerChatMessage> page) {
        if (page.isEmpty()) return false;
        long pageOldestId = page.get(0).getId();
        if (response != null) {
            for (ServerChatMessage message : response) {
                if (message != null && message.getId() < pageOldestId) {
                    return true;
                }
            }
        }
        // 分页接口一次返回满页时，还需再请求一次才能确认到达历史开头。
        return page.size() == PAGE_SIZE;
    }

    @NonNull
    public static List<ServerChatMessage> merge(
            @Nullable List<ServerChatMessage> existing,
            @Nullable List<ServerChatMessage> incoming) {
        Map<String, ServerChatMessage> byKey = new LinkedHashMap<>();
        putAll(byKey, existing);
        putAll(byKey, incoming);
        List<ServerChatMessage> merged = new ArrayList<>(byKey.values());
        merged.sort(Comparator.comparingLong(ServerChatMessage::getId));
        return merged;
    }

    private static void putAll(@NonNull Map<String, ServerChatMessage> target,
                               @Nullable List<ServerChatMessage> messages) {
        if (messages == null) return;
        for (ServerChatMessage message : messages) {
            if (message == null) continue;
            String key = message.getId() > 0
                    ? "id:" + message.getId()
                    : "fallback:" + message.getSenderAccountId() + ":"
                    + message.getSentAt() + ":" + message.getContent();
            target.put(key, message);
        }
    }
}
