package com.example.Japp.Chat.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.Japp.Chat.adapter.conversationListAdapter;
import com.example.Japp.Chat.chatActivity;
import com.example.Japp.Chat.util.ChatUnreadManager;
import com.example.Japp.Chat.util.ChatHistoryStore;
import com.example.Japp.Chat.util.ChatMessagePaging;
import com.example.Japp.R;
import com.example.Japp.data.Conversation;
import com.example.Japp.data.User;
import com.example.Japp.network.ApiClient;
import com.example.Japp.network.api.UserService;
import com.example.Japp.network.models.Account;
import com.example.Japp.network.models.ChatSession;
import com.example.Japp.network.models.ChatGroupMember;
import com.example.Japp.network.models.Project;
import com.example.Japp.network.models.Result;
import com.example.Japp.network.models.ServerChatMessage;
import com.example.Japp.user.util.SessionHelper;
import com.example.Japp.user.util.ProjectUiHelper;
import com.example.Japp.util.InsetDividerDecoration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ConversationList extends Fragment {

    public interface UnreadCountHost {
        void updateChatUnreadBadge(int unreadCount);
    }

    private RecyclerView recycler;
    private TextView emptyState;
    private conversationListAdapter adapter;
    private final List<Conversation> conversationList = new ArrayList<>();
    private UserService service;
    private int currentAccountId;
    private int pendingSessionChecks;
    private User currentUser;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_conversation, container, false);
        recycler = view.findViewById(R.id.recycler);
        emptyState = view.findViewById(R.id.txtEmptyState);

        adapter = new conversationListAdapter();
        adapter.setListData(conversationList);
        adapter.setConversationOnClickListener(position -> {
            if (position >= 0 && position < conversationList.size()) {
                openConversation(conversationList.get(position));
            }
        });

        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);
        recycler.addItemDecoration(new InsetDividerDecoration(requireContext(), 76, 16));

        service = ApiClient.getClient().create(UserService.class);
        initCurrentUser();
        return view;
    }

    private void initCurrentUser() {
        currentAccountId = SessionHelper.getAccountId(requireContext());
        currentUser = new User();
        currentUser.setId(String.valueOf(currentAccountId));
        currentUser.setName("我");

        if (currentAccountId > 0) {
            service.getAccount(currentAccountId).enqueue(new Callback<Result<Account>>() {
                @Override
                public void onResponse(Call<Result<Account>> call, Response<Result<Account>> response) {
                    if (response.isSuccessful() && response.body() != null
                            && response.body().getCode() == 1 && response.body().getData() != null) {
                        currentUser.setName(response.body().getData().getUsername());
                    }
                }

                @Override
                public void onFailure(Call<Result<Account>> call, Throwable t) {
                    // 名称加载失败不影响会话使用。
                }
            });
        }
    }

    private void loadConversations() {
        if (currentAccountId <= 0 || !SessionHelper.isLoggedIn(requireContext())) {
            showEmptyState("登录后查看项目会话");
            return;
        }

        service.getChatSessions().enqueue(new Callback<Result<List<ChatSession>>>() {
            @Override
            public void onResponse(Call<Result<List<ChatSession>>> call,
                                   Response<Result<List<ChatSession>>> response) {
                if (!isAdded()) {
                    return;
                }
                if (response.code() == 401) {
                    SessionHelper.handleUnauthorized(requireContext());
                    return;
                }
                if (!response.isSuccessful() || response.body() == null
                        || response.body().getCode() != 1) {
                    showCachedConversations("会话加载失败，请稍后重试");
                    return;
                }

                List<ChatSession> sessions = response.body().getData();
                conversationList.clear();
                conversationList.add(buildSystemConversation());
                adapter.setListData(conversationList);
                List<ChatHistoryStore.CachedSession> cachedSessions =
                        ChatHistoryStore.reconcile(requireContext(), currentAccountId, sessions);
                Set<Long> liveSessionIds = new HashSet<>();
                if (sessions != null) {
                    for (ChatSession session : sessions) {
                        if (session != null) liveSessionIds.add((long) session.getId());
                    }
                }
                for (ChatHistoryStore.CachedSession cached : cachedSessions) {
                    Conversation conversation = buildConversation(cached);
                    conversationList.add(conversation);
                    applyCachedDetails(conversation, cached);
                    if (liveSessionIds.contains(cached.sessionId)) {
                        loadConversationDetails(conversation);
                    }
                }
                refreshSystemConversation();
                adapter.setListData(conversationList);
                updateEmptyState();
            }

            @Override
            public void onFailure(Call<Result<List<ChatSession>>> call, Throwable t) {
                if (isAdded()) {
                    showCachedConversations("网络异常，会话加载失败");
                }
            }
        });
    }

    private void validateAndAddConversation(ChatSession session) {
        service.getProject(session.getProjectId()).enqueue(new Callback<Result<Project>>() {
            @Override
            public void onResponse(Call<Result<Project>> call, Response<Result<Project>> response) {
                if (!isAdded() || adapter == null || !response.isSuccessful()
                        || response.body() == null || response.body().getCode() != 1
                        || response.body().getData() == null) {
                    finishSessionValidation();
                    return;
                }

                Project project = response.body().getData();
                Integer assignedLeaderId = project.getLeaderAccountId();
                String status = ProjectUiHelper.normalizeStatus(project.getStatus());
                boolean isPublisherLeaderSession = project.getOwnerAccountId() == session.getUserAccountId()
                        && assignedLeaderId != null
                        && assignedLeaderId == session.getLeaderAccountId()
                        && !ProjectUiHelper.STATUS_DONE.equals(status)
                        && !ProjectUiHelper.STATUS_CANCELLED.equals(status);
                if (!isPublisherLeaderSession) {
                    finishSessionValidation();
                    return;
                }

                Conversation conversation = buildConversation(session);
                conversationList.add(conversation);
                adapter.setListData(conversationList);
                updateEmptyState();
                loadConversationDetails(conversation);
                finishSessionValidation();
            }

            @Override
            public void onFailure(Call<Result<Project>> call, Throwable t) {
                // 项目校验失败时不展示来源不明的旧会话。
                finishSessionValidation();
            }
        });
    }

    private void finishSessionValidation() {
        pendingSessionChecks = Math.max(0, pendingSessionChecks - 1);
        if (pendingSessionChecks == 0 && conversationList.isEmpty()) {
            updateEmptyState();
        }
    }

    private Conversation buildConversation(ChatSession session) {
        User peer = new User();
        peer.setId("0");
        peer.setName("项目群成员");

        Conversation conversation = new Conversation();
        conversation.setUser_me(currentUser);
        conversation.setUser_opposite(peer);
        conversation.setBackendSessionId(session.getId());
        conversation.setProjectId(session.getProjectId());
        conversation.setChatStatus(session.getStatus());
        conversation.setCurrentUserRole(session.getCurrentUserRole());
        conversation.setGroup(true);
        String title = session.getProjectTitle();
        conversation.setGroupName(title == null || title.trim().isEmpty()
                ? "项目群聊" : title);
        conversation.setUnRead_num(0);
        return conversation;
    }

    private Conversation buildConversation(ChatHistoryStore.CachedSession cached) {
        User peer = new User();
        peer.setId("0");
        peer.setName("项目群成员");

        Conversation conversation = new Conversation();
        conversation.setUser_me(currentUser);
        conversation.setUser_opposite(peer);
        conversation.setBackendSessionId(cached.sessionId);
        conversation.setProjectId(cached.projectId);
        conversation.setChatStatus(cached.status);
        conversation.setCurrentUserRole(cached.currentUserRole);
        conversation.setReadOnly(cached.readOnly);
        conversation.setReadOnlyReason(cached.readOnlyReason);
        conversation.setGroup(true);
        conversation.setGroupName(cached.title == null || cached.title.trim().isEmpty()
                ? "项目群聊" : cached.title);
        conversation.setUnRead_num(0);
        return conversation;
    }

    private Conversation buildSystemConversation() {
        User system = new User();
        system.setId("-1");
        system.setName("系统通知");
        system.setMemberRole("SYSTEM");

        Conversation conversation = new Conversation();
        conversation.setUser_me(currentUser);
        conversation.setUser_opposite(system);
        conversation.setBackendSessionId(chatActivity.SYSTEM_NOTIFICATION_SESSION_ID);
        conversation.setReadOnly(true);
        conversation.setReadOnlyReason("系统通知仅供查看");
        applySystemConversationDetails(conversation);
        return conversation;
    }

    private void applyCachedDetails(Conversation conversation,
                                    ChatHistoryStore.CachedSession cached) {
        List<String> names = new ArrayList<>();
        if (cached.members != null) {
            for (ChatGroupMember member : cached.members) {
                String name = member.getUsername() == null ? "群成员" : member.getUsername();
                String representation = member.getRepresentationText();
                boolean leader = "LEADER".equalsIgnoreCase(member.getMemberRole())
                        || "ADMIN".equalsIgnoreCase(member.getMemberRole());
                names.add(leader || representation == null || representation.trim().isEmpty()
                        ? name : name + "（" + representation + "）");
            }
        }
        conversation.setMemberNames(names);
        applyLatestPreview(conversation, cached.messages);
        if (conversation.getMessages().isEmpty()
                && cached.latestMessage != null && !cached.latestMessage.trim().isEmpty()) {
            conversation.addMessage(cached.latestMessage);
        }
    }

    private void loadConversationDetails(Conversation conversation) {
        service.getChatMembers(conversation.getBackendSessionId())
                .enqueue(new Callback<Result<List<ChatGroupMember>>>() {
                    @Override
                    public void onResponse(Call<Result<List<ChatGroupMember>>> call,
                                           Response<Result<List<ChatGroupMember>>> response) {
                        if (!isAdded() || adapter == null || !response.isSuccessful()
                                || response.body() == null || response.body().getCode() != 1) {
                            return;
                        }
                        List<String> members = new ArrayList<>();
                        List<ChatGroupMember> data = response.body().getData();
                        if (data != null) {
                            for (ChatGroupMember member : data) {
                                String name = member.getUsername() == null
                                        ? "群成员" : member.getUsername();
                                String representation = member.getRepresentationText();
                                boolean isLeader = "LEADER".equalsIgnoreCase(member.getMemberRole())
                                        || "ADMIN".equalsIgnoreCase(member.getMemberRole());
                                members.add(isLeader || representation == null
                                        || representation.trim().isEmpty()
                                        ? name : name + "（" + representation + "）");
                            }
                        }
                        ChatHistoryStore.reconcileMembers(requireContext(), currentAccountId,
                                conversation.getBackendSessionId(), data);
                        conversation.setMemberNames(members);
                        ChatHistoryStore.CachedSession refreshed = ChatHistoryStore.find(
                                requireContext(), currentAccountId,
                                conversation.getBackendSessionId());
                        if (refreshed != null) {
                            applyLatestPreview(conversation, refreshed.messages);
                            conversation.setUnRead_num(ChatUnreadManager.calculateUnread(
                                    requireContext(), currentAccountId,
                                    conversation.getBackendSessionId(), refreshed.messages));
                            sortConversations();
                            notifyUnreadCount();
                        }
                        refreshSystemConversation();
                        adapter.notifyDataSetChanged();
                    }

                    @Override
                    public void onFailure(Call<Result<List<ChatGroupMember>>> call, Throwable t) {
                        // 成员信息加载失败不影响聊天。
                    }
                });

        service.getChatMessagesPage(conversation.getBackendSessionId(), null,
                        ChatMessagePaging.PAGE_SIZE)
                .enqueue(new Callback<Result<List<ServerChatMessage>>>() {
                    @Override
                    public void onResponse(Call<Result<List<ServerChatMessage>>> call,
                                           Response<Result<List<ServerChatMessage>>> response) {
                        if (!isAdded() || adapter == null || !response.isSuccessful()
                                || response.body() == null || response.body().getCode() != 1) {
                            return;
                        }
                        List<ServerChatMessage> serverMessages = response.body().getData();
                        List<ServerChatMessage> messages =
                                ChatMessagePaging.pageBefore(serverMessages, null);
                        ChatHistoryStore.saveMessages(requireContext(), currentAccountId,
                                conversation.getBackendSessionId(), serverMessages);
                        applyLatestPreview(conversation, messages);
                        conversation.setUnRead_num(ChatUnreadManager.calculateUnread(
                                requireContext(),
                                currentAccountId,
                                conversation.getBackendSessionId(),
                                messages));
                        sortConversations();
                        adapter.notifyDataSetChanged();
                        notifyUnreadCount();
                    }

                    @Override
                    public void onFailure(Call<Result<List<ServerChatMessage>>> call, Throwable t) {
                        // 列表仍可进入会话。
                    }
                });
    }

    private void applyLatestPreview(@NonNull Conversation conversation,
                                    @Nullable List<ServerChatMessage> backendMessages) {
        conversation.getMessages().clear();
        ServerChatMessage backendLast = null;
        if (backendMessages != null && !backendMessages.isEmpty()) {
            backendLast = backendMessages.get(backendMessages.size() - 1);
        }
        if (backendLast != null) {
            conversation.addMessage(backendLast.getContent());
            conversation.setLatestMessageId(backendLast.getId());
        } else {
            conversation.setLatestMessageId(0L);
        }
    }

    private void refreshSystemConversation() {
        if (!isAdded()) return;
        for (Conversation conversation : conversationList) {
            if (conversation.getBackendSessionId()
                    == chatActivity.SYSTEM_NOTIFICATION_SESSION_ID) {
                applySystemConversationDetails(conversation);
                sortConversations();
                notifyUnreadCount();
                return;
            }
        }
    }

    private void applySystemConversationDetails(@NonNull Conversation conversation) {
        conversation.getMessages().clear();
        List<ServerChatMessage> notices = ChatHistoryStore.getAllSystemMessages(
                requireContext(), currentAccountId);
        if (!notices.isEmpty()) {
            ServerChatMessage last = notices.get(notices.size() - 1);
            conversation.addMessage(last.getContent());
            conversation.setLatestMessageId(last.getId());
        } else {
            conversation.addMessage("群聊人员变化会显示在这里");
            conversation.setLatestMessageId(Long.MAX_VALUE);
        }
        conversation.setUnRead_num(ChatHistoryStore.countAllUnreadSystemNotices(
                requireContext(), currentAccountId));
    }

    private void openConversation(Conversation conversation) {
        if (conversation == null || conversation.getBackendSessionId() == 0) {
            Toast.makeText(requireContext(), "会话信息无效", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(requireContext(), chatActivity.class);
        intent.putExtra("conversation_info", conversation);
        startActivity(intent);
    }

    private void sortConversations() {
        conversationList.sort((left, right) -> {
            boolean leftSystem = left.getBackendSessionId()
                    == chatActivity.SYSTEM_NOTIFICATION_SESSION_ID;
            boolean rightSystem = right.getBackendSessionId()
                    == chatActivity.SYSTEM_NOTIFICATION_SESSION_ID;
            if (leftSystem != rightSystem) return leftSystem ? -1 : 1;
            boolean leftUnread = left.getUnRead_num() > 0;
            boolean rightUnread = right.getUnRead_num() > 0;
            if (leftUnread != rightUnread) {
                return leftUnread ? -1 : 1;
            }
            return Long.compare(right.getLatestMessageId(), left.getLatestMessageId());
        });
    }

    private void notifyUnreadCount() {
        int totalUnread = 0;
        for (Conversation conversation : conversationList) {
            totalUnread += conversation.getUnRead_num();
        }
        if (getActivity() instanceof UnreadCountHost) {
            ((UnreadCountHost) getActivity()).updateChatUnreadBadge(totalUnread);
        }
    }

    private void updateEmptyState() {
        if (emptyState == null || recycler == null) {
            return;
        }
        boolean empty = conversationList.isEmpty();
        emptyState.setText("发布、加入拼单或领队接单后，将自动出现项目群聊");
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        recycler.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (empty) {
            notifyUnreadCount();
        }
    }

    private void showEmptyState(String message) {
        conversationList.clear();
        if (adapter != null) {
            adapter.setListData(conversationList);
        }
        if (emptyState != null) {
            emptyState.setText(message);
            emptyState.setVisibility(View.VISIBLE);
        }
        if (recycler != null) {
            recycler.setVisibility(View.GONE);
        }
    }

    private void showCachedConversations(String emptyMessage) {
        if (!isAdded() || adapter == null) return;
        List<ChatHistoryStore.CachedSession> cached =
                ChatHistoryStore.getAll(requireContext(), currentAccountId);
        conversationList.clear();
        conversationList.add(buildSystemConversation());
        for (ChatHistoryStore.CachedSession item : cached) {
            Conversation conversation = buildConversation(item);
            applyCachedDetails(conversation, item);
            conversationList.add(conversation);
        }
        sortConversations();
        adapter.setListData(conversationList);
        updateEmptyState();
        if (!cached.isEmpty()) {
            Toast.makeText(requireContext(), "当前展示本地聊天记录", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        loadConversations();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        recycler = null;
        emptyState = null;
        adapter = null;
    }
}
