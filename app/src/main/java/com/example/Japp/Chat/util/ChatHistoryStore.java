package com.example.Japp.Chat.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.Japp.network.models.ChatGroupMember;
import com.example.Japp.network.models.ChatSession;
import com.example.Japp.network.models.ServerChatMessage;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 将已出现过的项目群聊保存在本地，退出行程后仍可查看历史消息。 */
public final class ChatHistoryStore {

    private static final String PREFS = "chat_history_store";
    private static final String KEY_PREFIX = "sessions_account_";
    private static final Gson GSON = new Gson();
    private static final Type LIST_TYPE = new TypeToken<List<CachedSession>>() {}.getType();
    private static final int MAX_SYSTEM_NOTICES = 100;

    private ChatHistoryStore() {}

    public static final class CachedSession {
        public long sessionId;
        public int projectId;
        public String title;
        public String status;
        public String currentUserRole;
        public int userAccountId;
        public Integer leaderAccountId;
        public String latestMessage;
        public String latestMessageAt;
        public boolean readOnly;
        public boolean forcedReadOnly;
        public String readOnlyReason;
        public long updatedAt;
        public List<ChatGroupMember> members = new ArrayList<>();
        public boolean memberSnapshotInitialized;
        public List<ServerChatMessage> messages = new ArrayList<>();
        public List<SystemNotice> systemNotices = new ArrayList<>();
        public long lastSystemNoticeReadAt;
    }

    public static final class SystemNotice {
        public long id;
        public long createdAt;
        public String eventKey;
        public String content;
    }

    @NonNull
    public static synchronized List<CachedSession> reconcile(
            @NonNull Context context, int accountId, @Nullable List<ChatSession> liveSessions) {
        List<CachedSession> cached = read(context, accountId);
        Map<Long, CachedSession> byId = new LinkedHashMap<>();
        for (CachedSession item : cached) {
            if (item != null && item.sessionId > 0) {
                ensureLists(item);
                byId.put(item.sessionId, item);
            }
        }

        Map<Long, Boolean> liveIds = new LinkedHashMap<>();
        if (liveSessions != null) {
            for (ChatSession session : liveSessions) {
                if (session == null || session.getId() <= 0) continue;
                long sessionId = session.getId();
                liveIds.put(sessionId, true);
                CachedSession item = byId.get(sessionId);
                if (item == null) {
                    item = new CachedSession();
                    item.sessionId = sessionId;
                    byId.put(sessionId, item);
                }
                item.projectId = session.getProjectId();
                item.title = session.getProjectTitle();
                item.status = session.getStatus();
                item.currentUserRole = session.getCurrentUserRole();
                item.userAccountId = session.getUserAccountId();
                item.leaderAccountId = session.getLeaderAccountId();
                item.latestMessage = session.getLatestMessage();
                item.latestMessageAt = session.getLatestMessageAt();
                item.updatedAt = System.currentTimeMillis();
                boolean serverReadOnly = isClosedStatus(item.status)
                        || isFormerRole(item.currentUserRole);
                item.readOnly = item.forcedReadOnly || serverReadOnly;
                if (serverReadOnly) {
                    item.readOnlyReason = closedReason(item.status, item.currentUserRole);
                } else if (!item.forcedReadOnly) {
                    item.readOnlyReason = null;
                }
                ensureLists(item);
            }
        }

        for (CachedSession item : byId.values()) {
            if (!liveIds.containsKey(item.sessionId)) {
                item.readOnly = true;
                if (TextUtils.isEmpty(item.readOnlyReason)) {
                    item.readOnlyReason = roleExitReason(item.currentUserRole);
                }
            }
        }
        List<CachedSession> result = new ArrayList<>(byId.values());
        write(context, accountId, result);
        return result;
    }

    @NonNull
    public static synchronized List<CachedSession> getAll(
            @NonNull Context context, int accountId) {
        return read(context, accountId);
    }

    @Nullable
    public static synchronized CachedSession find(
            @NonNull Context context, int accountId, long sessionId) {
        for (CachedSession item : read(context, accountId)) {
            if (item != null && item.sessionId == sessionId) {
                ensureLists(item);
                return item;
            }
        }
        return null;
    }

    public static synchronized void saveMembers(@NonNull Context context, int accountId,
                                                long sessionId,
                                                @Nullable List<ChatGroupMember> members) {
        List<CachedSession> sessions = read(context, accountId);
        CachedSession item = findOrCreate(sessions, sessionId);
        item.members = members == null ? new ArrayList<>() : new ArrayList<>(members);
        item.updatedAt = System.currentTimeMillis();
        write(context, accountId, sessions);
    }

    /**
     * 对比上次成员快照并生成当前账号有权看到的本地系统通知。
     * 首次观察会话时只建立基线，避免把已有成员全部当作刚加入。
     */
    public static synchronized void reconcileMembers(@NonNull Context context, int accountId,
                                                     long sessionId,
                                                     @Nullable List<ChatGroupMember> members) {
        List<CachedSession> sessions = read(context, accountId);
        CachedSession item = findOrCreate(sessions, sessionId);
        ensureLists(item);
        List<ChatGroupMember> current = members == null
                ? new ArrayList<>() : new ArrayList<>(members);
        long now = System.currentTimeMillis();
        if (!item.memberSnapshotInitialized) {
            item.members = current;
            item.memberSnapshotInitialized = true;
            item.updatedAt = now;
            write(context, accountId, sessions);
            return;
        }

        Map<Integer, ChatGroupMember> previousById = memberMap(item.members);
        Map<Integer, ChatGroupMember> currentById = memberMap(current);
        ChatGroupMember viewer = currentById.get(accountId);
        if (viewer == null) viewer = previousById.get(accountId);
        String viewerRole = viewer == null ? item.currentUserRole : viewer.getMemberRole();
        long eventTime = System.currentTimeMillis();
        int sequence = 0;

        for (ChatGroupMember member : current) {
            if (member == null) continue;
            ChatGroupMember old = previousById.get(member.getAccountId());
            if (old == null) {
                if (shouldReceiveNotice(accountId, viewerRole, member.getAccountId(),
                        null, member.getMemberRole())) {
                    addSystemNotice(item, "join:" + member.getAccountId() + ":" + eventTime,
                            joinText(member), eventTime + sequence++);
                }
                continue;
            }
            if (!sameRole(old.getMemberRole(), member.getMemberRole())
                    && shouldReceiveNotice(accountId, viewerRole, member.getAccountId(),
                    old.getMemberRole(), member.getMemberRole())) {
                addSystemNotice(item,
                        "role:" + member.getAccountId() + ":" + member.getMemberRole()
                                + ":" + eventTime,
                        roleChangeText(old, member), eventTime + sequence++);
            }
            if (old.getRepresentedCount() != member.getRepresentedCount()
                    && shouldReceiveNotice(accountId, viewerRole, member.getAccountId(),
                    old.getMemberRole(), member.getMemberRole())) {
                addSystemNotice(item,
                        "count:" + member.getAccountId() + ":"
                                + member.getRepresentedCount() + ":" + eventTime,
                        displayName(member) + "的参团人数由 "
                                + Math.max(0, old.getRepresentedCount()) + " 人调整为 "
                                + Math.max(0, member.getRepresentedCount()) + " 人",
                        eventTime + sequence++);
            }
        }

        for (ChatGroupMember old : item.members) {
            if (old == null || currentById.containsKey(old.getAccountId())) continue;
            if (shouldReceiveNotice(accountId, viewerRole, old.getAccountId(),
                    old.getMemberRole(), null)) {
                addSystemNotice(item, "leave:" + old.getAccountId() + ":" + eventTime,
                        leaveText(old), eventTime + sequence++);
            }
        }

        item.members = current;
        item.updatedAt = System.currentTimeMillis();
        write(context, accountId, sessions);
    }

    @NonNull
    public static synchronized List<ServerChatMessage> getSystemMessages(
            @NonNull Context context, int accountId, long sessionId) {
        CachedSession item = find(context, accountId, sessionId);
        List<ServerChatMessage> result = new ArrayList<>();
        if (item == null) return result;
        for (SystemNotice notice : item.systemNotices) {
            if (notice == null || TextUtils.isEmpty(notice.content)) continue;
            ServerChatMessage message = new ServerChatMessage(
                    notice.id, sessionId, -1, notice.content, "SYSTEM_NOTICE", null);
            message.setLocalTimestamp(notice.createdAt);
            result.add(message);
        }
        return result;
    }

    public static synchronized int countUnreadSystemNotices(
            @NonNull Context context, int accountId, long sessionId) {
        CachedSession item = find(context, accountId, sessionId);
        if (item == null) return 0;
        int count = 0;
        for (SystemNotice notice : item.systemNotices) {
            if (notice != null && notice.createdAt > item.lastSystemNoticeReadAt) count++;
        }
        return count;
    }

    public static synchronized void markSystemNoticesRead(
            @NonNull Context context, int accountId, long sessionId) {
        List<CachedSession> sessions = read(context, accountId);
        CachedSession item = findOrCreate(sessions, sessionId);
        item.lastSystemNoticeReadAt = System.currentTimeMillis();
        write(context, accountId, sessions);
    }

    @Nullable
    public static synchronized SystemNotice latestSystemNotice(
            @NonNull Context context, int accountId, long sessionId) {
        CachedSession item = find(context, accountId, sessionId);
        if (item == null || item.systemNotices.isEmpty()) return null;
        return item.systemNotices.get(item.systemNotices.size() - 1);
    }

    @NonNull
    public static synchronized List<ServerChatMessage> getAllSystemMessages(
            @NonNull Context context, int accountId) {
        List<ServerChatMessage> result = new ArrayList<>();
        for (CachedSession item : read(context, accountId)) {
            ensureLists(item);
            String title = TextUtils.isEmpty(item.title) ? "项目群聊" : item.title.trim();
            for (SystemNotice notice : item.systemNotices) {
                if (notice == null || TextUtils.isEmpty(notice.content)) continue;
                ServerChatMessage message = new ServerChatMessage(
                        notice.id, -1L, -1,
                        title + " · " + notice.content, "SYSTEM_NOTICE", null);
                message.setLocalTimestamp(notice.createdAt);
                result.add(message);
            }
        }
        result.sort(Comparator.comparingLong(ChatMessageTime::timestamp));
        return result;
    }

    public static synchronized int countAllUnreadSystemNotices(
            @NonNull Context context, int accountId) {
        int count = 0;
        for (CachedSession item : read(context, accountId)) {
            ensureLists(item);
            for (SystemNotice notice : item.systemNotices) {
                if (notice != null && notice.createdAt > item.lastSystemNoticeReadAt) count++;
            }
        }
        return count;
    }

    public static synchronized void markAllSystemNoticesRead(
            @NonNull Context context, int accountId) {
        List<CachedSession> sessions = read(context, accountId);
        long now = System.currentTimeMillis();
        for (CachedSession item : sessions) {
            ensureLists(item);
            item.lastSystemNoticeReadAt = now;
        }
        write(context, accountId, sessions);
    }

    @Nullable
    public static synchronized SystemNotice latestSystemNotice(
            @NonNull Context context, int accountId) {
        SystemNotice latest = null;
        for (CachedSession item : read(context, accountId)) {
            ensureLists(item);
            for (SystemNotice notice : item.systemNotices) {
                if (notice != null && (latest == null
                        || notice.createdAt > latest.createdAt)) {
                    latest = notice;
                }
            }
        }
        return latest;
    }

    public static synchronized void saveMessages(@NonNull Context context, int accountId,
                                                 long sessionId,
                                                 @Nullable List<ServerChatMessage> messages) {
        List<CachedSession> sessions = read(context, accountId);
        CachedSession item = findOrCreate(sessions, sessionId);
        item.messages = ChatMessagePaging.merge(item.messages, messages);
        item.updatedAt = System.currentTimeMillis();
        write(context, accountId, sessions);
    }

    public static synchronized void appendMessage(@NonNull Context context, int accountId,
                                                  long sessionId,
                                                  @NonNull ServerChatMessage message) {
        List<CachedSession> sessions = read(context, accountId);
        CachedSession item = findOrCreate(sessions, sessionId);
        ensureLists(item);
        item.messages = ChatMessagePaging.merge(item.messages,
                java.util.Collections.singletonList(message));
        item.updatedAt = System.currentTimeMillis();
        write(context, accountId, sessions);
    }

    public static synchronized void markProjectReadOnly(@NonNull Context context, int accountId,
                                                        int projectId,
                                                        @NonNull String reason) {
        List<CachedSession> sessions = read(context, accountId);
        for (CachedSession item : sessions) {
            if (item != null && item.projectId == projectId) {
                item.readOnly = true;
                item.forcedReadOnly = true;
                item.readOnlyReason = reason;
                item.updatedAt = System.currentTimeMillis();
            }
        }
        write(context, accountId, sessions);
    }

    public static synchronized void markSessionReadOnly(@NonNull Context context, int accountId,
                                                        long sessionId,
                                                        @NonNull String reason) {
        List<CachedSession> sessions = read(context, accountId);
        CachedSession item = findOrCreate(sessions, sessionId);
        item.readOnly = true;
        item.forcedReadOnly = true;
        item.readOnlyReason = reason;
        item.updatedAt = System.currentTimeMillis();
        write(context, accountId, sessions);
    }

    public static synchronized void markProjectActive(@NonNull Context context, int accountId,
                                                      int projectId) {
        List<CachedSession> sessions = read(context, accountId);
        for (CachedSession item : sessions) {
            if (item != null && item.projectId == projectId) {
                item.forcedReadOnly = false;
                item.readOnly = isClosedStatus(item.status)
                        || isFormerRole(item.currentUserRole);
                if (!item.readOnly) item.readOnlyReason = null;
                item.updatedAt = System.currentTimeMillis();
            }
        }
        write(context, accountId, sessions);
    }

    @NonNull
    public static String roleExitReason(@Nullable String role) {
        String normalized = normalize(role);
        if ("PUBLISHER".equals(normalized) || "OWNER".equals(normalized)) {
            return "行程已取消，聊天记录仅供查看";
        }
        if ("LEADER".equals(normalized) || "ADMIN".equals(normalized)) {
            return "你已放弃带队，聊天记录仅供查看";
        }
        return "你已退出行程，聊天记录仅供查看";
    }

    @NonNull
    public static String closedReason(@Nullable String status, @Nullable String role) {
        String normalized = normalize(status);
        if (normalized.contains("DONE") || normalized.contains("COMPLETED")) {
            return "行程已结束，聊天记录仅供查看";
        }
        if (normalized.contains("CANCEL") || normalized.contains("CLOSED")
                || normalized.contains("ARCHIVED")) {
            return "发布者已取消行程，所有成员仅可查看历史消息";
        }
        return roleExitReason(role);
    }

    private static boolean isClosedStatus(@Nullable String status) {
        String normalized = normalize(status);
        return normalized.contains("CANCEL") || normalized.contains("CLOSED")
                || normalized.contains("ARCHIVED") || normalized.contains("DONE")
                || normalized.contains("COMPLETED") || normalized.contains("INACTIVE");
    }

    private static boolean isFormerRole(@Nullable String role) {
        String normalized = normalize(role);
        return normalized.contains("LEFT") || normalized.contains("QUIT")
                || normalized.contains("FORMER") || normalized.contains("REMOVED");
    }

    @NonNull
    private static String normalize(@Nullable String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    @NonNull
    private static List<CachedSession> read(@NonNull Context context, int accountId) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_PREFIX + accountId, null);
        if (TextUtils.isEmpty(raw)) return new ArrayList<>();
        try {
            List<CachedSession> sessions = GSON.fromJson(raw, LIST_TYPE);
            if (sessions == null) return new ArrayList<>();
            for (CachedSession item : sessions) ensureLists(item);
            return sessions;
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    private static void write(@NonNull Context context, int accountId,
                              @NonNull List<CachedSession> sessions) {
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_PREFIX + accountId, GSON.toJson(sessions)).apply();
    }

    @NonNull
    private static CachedSession findOrCreate(@NonNull List<CachedSession> sessions,
                                              long sessionId) {
        for (CachedSession item : sessions) {
            if (item != null && item.sessionId == sessionId) {
                ensureLists(item);
                return item;
            }
        }
        CachedSession item = new CachedSession();
        item.sessionId = sessionId;
        sessions.add(item);
        return item;
    }

    private static void ensureLists(@Nullable CachedSession item) {
        if (item == null) return;
        if (item.members == null) item.members = new ArrayList<>();
        if (!item.members.isEmpty()) item.memberSnapshotInitialized = true;
        if (item.messages == null) item.messages = new ArrayList<>();
        if (item.systemNotices == null) item.systemNotices = new ArrayList<>();
    }

    @NonNull
    private static Map<Integer, ChatGroupMember> memberMap(
            @Nullable List<ChatGroupMember> members) {
        Map<Integer, ChatGroupMember> result = new LinkedHashMap<>();
        if (members != null) {
            for (ChatGroupMember member : members) {
                if (member != null) result.put(member.getAccountId(), member);
            }
        }
        return result;
    }

    private static boolean shouldReceiveNotice(int viewerId, @Nullable String viewerRole,
                                               int subjectId, @Nullable String oldRole,
                                               @Nullable String newRole) {
        if (viewerId == subjectId) return false;
        boolean privilegedSubject = isPrivilegedRole(oldRole) || isPrivilegedRole(newRole);
        return privilegedSubject || isPrivilegedRole(viewerRole);
    }

    private static boolean isPrivilegedRole(@Nullable String role) {
        String normalized = normalize(role);
        return "PUBLISHER".equals(normalized) || "OWNER".equals(normalized)
                || "LEADER".equals(normalized) || "ADMIN".equals(normalized);
    }

    private static boolean sameRole(@Nullable String left, @Nullable String right) {
        return normalize(left).equals(normalize(right));
    }

    private static void addSystemNotice(@NonNull CachedSession item, @NonNull String eventKey,
                                        @NonNull String content, long createdAt) {
        for (SystemNotice existing : item.systemNotices) {
            if (existing != null && eventKey.equals(existing.eventKey)) return;
        }
        SystemNotice notice = new SystemNotice();
        notice.createdAt = createdAt;
        notice.id = createdAt * 1000L + item.systemNotices.size() % 1000;
        notice.eventKey = eventKey;
        notice.content = content;
        item.systemNotices.add(notice);
        if (item.systemNotices.size() > MAX_SYSTEM_NOTICES) {
            item.systemNotices = new ArrayList<>(item.systemNotices.subList(
                    item.systemNotices.size() - MAX_SYSTEM_NOTICES,
                    item.systemNotices.size()));
        }
    }

    private static String joinText(ChatGroupMember member) {
        String name = displayName(member);
        String role = normalize(member.getMemberRole());
        if ("LEADER".equals(role) || "ADMIN".equals(role)) {
            return "领队 " + name + " 加入了群聊";
        }
        if ("PUBLISHER".equals(role) || "OWNER".equals(role)) {
            return "发布者 " + name + " 加入了群聊";
        }
        return name + "（代表" + Math.max(0, member.getRepresentedCount()) + "人）加入了行程";
    }

    private static String leaveText(ChatGroupMember member) {
        String name = displayName(member);
        String role = normalize(member.getMemberRole());
        if ("LEADER".equals(role) || "ADMIN".equals(role)) {
            return "领队 " + name + " 已退出群聊";
        }
        if ("PUBLISHER".equals(role) || "OWNER".equals(role)) {
            return "发布者 " + name + " 已退出群聊";
        }
        return name + " 已退出行程";
    }

    private static String roleChangeText(ChatGroupMember oldMember,
                                         ChatGroupMember newMember) {
        String name = displayName(newMember);
        if (isPrivilegedRole(newMember.getMemberRole())) {
            String role = normalize(newMember.getMemberRole());
            return name + ("LEADER".equals(role) || "ADMIN".equals(role)
                    ? " 已成为领队" : " 已成为发布者");
        }
        if (isPrivilegedRole(oldMember.getMemberRole())) {
            String role = normalize(oldMember.getMemberRole());
            return name + ("LEADER".equals(role) || "ADMIN".equals(role)
                    ? " 不再担任领队" : " 不再担任发布者");
        }
        return name + " 的群聊身份已更新";
    }

    private static String displayName(ChatGroupMember member) {
        return member == null || TextUtils.isEmpty(member.getUsername())
                ? "群成员" : member.getUsername().trim();
    }
}
