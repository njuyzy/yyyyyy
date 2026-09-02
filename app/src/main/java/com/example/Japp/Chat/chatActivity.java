package com.example.Japp.Chat;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.Japp.Chat.adapter.ChatMemberAdapter;
import com.example.Japp.Chat.adapter.chatAdapter;
import com.example.Japp.Chat.util.ChatUnreadManager;
import com.example.Japp.Chat.util.ChatHistoryStore;
import com.example.Japp.Chat.util.ChatMessagePaging;
import com.example.Japp.Chat.util.ChatMessageTime;
import com.example.Japp.R;
import com.example.Japp.data.Conversation;
import com.example.Japp.data.Message;
import com.example.Japp.data.User;
import com.example.Japp.network.ApiClient;
import com.example.Japp.network.api.UserService;
import com.example.Japp.network.models.ChatGroupMember;
import com.example.Japp.network.models.Project;
import com.example.Japp.network.models.Result;
import com.example.Japp.network.models.ServerChatMessage;
import com.example.Japp.network.models.requests.SendChatMessageRequest;
import com.example.Japp.user.util.SessionHelper;
import com.example.Japp.user.util.ProjectUiHelper;
import com.example.Japp.user.TeamDetailActivity;
import com.example.Japp.leader.orderDetailActivity;
import com.google.android.material.button.MaterialButton;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class chatActivity extends AppCompatActivity {

    public static final long SYSTEM_NOTIFICATION_SESSION_ID = -1L;

    private Conversation conversation;
    private RecyclerView recycler;
    private chatAdapter adapter;
    private EditText edtInput;
    private MaterialButton btnSend;
    private final List<Message> messages = new ArrayList<>();
    private final List<ServerChatMessage> displayedServerMessages = new ArrayList<>();
    private User currentUser;
    private UserService service;
    private int currentAccountId;
    private int loadGeneration;
    private final Map<Integer, User> membersByAccountId = new HashMap<>();
    private View memberPanel;
    private View memberScrim;
    private TextView txtMemberPanelTitle;
    private ChatMemberAdapter memberAdapter;
    private MaterialButton btnViewTrip;
    private TextView txtReadOnlyNotice;
    private boolean memberPanelOpen;
    private boolean chatReadOnly;
    private boolean loadingOlderMessages;
    private boolean hasMoreOlderMessages = true;
    private int nextMessagePage = 2;
    private boolean systemConversation;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        conversation = (Conversation) getIntent().getSerializableExtra("conversation_info");
        if (conversation == null || conversation.getBackendSessionId() == 0) {
            Toast.makeText(this, "会话信息无效", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        systemConversation = conversation.getBackendSessionId()
                == SYSTEM_NOTIFICATION_SESSION_ID;

        currentAccountId = SessionHelper.getAccountId(this);
        if (currentAccountId <= 0) {
            SessionHelper.handleUnauthorized(this);
            return;
        }

        currentUser = conversation.getUser_me();
        if (currentUser == null) {
            currentUser = new User();
            currentUser.setName("我");
        }
        currentUser.setId(String.valueOf(currentAccountId));
        service = ApiClient.getClient().create(UserService.class);

        initViews();
        setupToolbar();
        setupRecyclerView();
        setupMemberPanel();
        if (systemConversation) {
            restoreSystemNotifications();
        } else {
            restoreCachedHistory();
        }
        applyReadOnlyState();
        btnSend.setOnClickListener(v -> sendMessage());
        setupInputBehavior();
    }

    private void initViews() {
        recycler = findViewById(R.id.recycler);
        edtInput = findViewById(R.id.edtInput);
        btnSend = findViewById(R.id.btnSend);
        memberPanel = findViewById(R.id.memberPanel);
        memberScrim = findViewById(R.id.memberScrim);
        txtMemberPanelTitle = findViewById(R.id.txtMemberPanelTitle);
        btnViewTrip = findViewById(R.id.btnViewTrip);
        txtReadOnlyNotice = findViewById(R.id.txtReadOnlyNotice);
    }

    private void setupInputBehavior() {
        edtInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_SEND) {
                return false;
            }
            if (btnSend.isEnabled()) {
                sendMessage();
            }
            return true;
        });
    }

    private void setupRecyclerView() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        recycler.setLayoutManager(layoutManager);
        adapter = new chatAdapter(messages, String.valueOf(currentAccountId));
        recycler.setAdapter(adapter);
        recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (dy >= 0 || loadingOlderMessages || !hasMoreOlderMessages) return;
                int firstVisible = layoutManager.findFirstVisibleItemPosition();
                if (firstVisible != RecyclerView.NO_POSITION
                        && firstVisible <= ChatMessagePaging.PREFETCH_DISTANCE) {
                    loadOlderMessages();
                }
            }
        });
    }

    private void setupMemberPanel() {
        RecyclerView memberRecycler = findViewById(R.id.memberRecycler);
        memberAdapter = new ChatMemberAdapter();
        memberRecycler.setLayoutManager(new LinearLayoutManager(this));
        memberRecycler.setAdapter(memberAdapter);

        View btnMembers = findViewById(R.id.btnMembers);
        View btnCloseMembers = findViewById(R.id.btnCloseMembers);
        btnMembers.setOnClickListener(v -> showMemberPanel());
        btnCloseMembers.setOnClickListener(v -> hideMemberPanel());
        memberScrim.setOnClickListener(v -> hideMemberPanel());
        btnViewTrip.setOnClickListener(v -> openTripDetails());
        if (systemConversation) {
            btnMembers.setVisibility(View.GONE);
            btnViewTrip.setVisibility(View.GONE);
        }
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (memberPanelOpen) {
                    hideMemberPanel();
                    return;
                }
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
            }
        });
    }

    private void setupToolbar() {
        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        String title = conversation.getDisplayName();
        TextView titleView = findViewById(R.id.txtToolbarTitle);
        if (titleView != null) {
            titleView.setText(title);
        }

        TextView subtitleView = findViewById(R.id.txtToolbarSubtitle);
        if (subtitleView != null) {
            if (systemConversation) {
                subtitleView.setText("人员变更通知");
            } else if (conversation.isGroup() && !conversation.getMemberNames().isEmpty()) {
                subtitleView.setText(String.join("、", conversation.getMemberNames()));
            } else {
                subtitleView.setText("项目沟通");
            }
        }

        TextView avatarView = findViewById(R.id.txtToolbarAvatar);
        if (avatarView != null) {
            avatarView.setText(initialOf(title));
        }
        View avatarContainer = findViewById(R.id.toolbarAvatarContainer);
        if (systemConversation && avatarContainer != null) {
            avatarContainer.setBackgroundResource(R.drawable.bg_chat_avatar_system);
        }
    }

    private String initialOf(String name) {
        if (TextUtils.isEmpty(name)) {
            return "?";
        }
        return String.valueOf(name.charAt(0)).toUpperCase(Locale.getDefault());
    }

    private void showMemberPanel() {
        if (memberPanelOpen) {
            return;
        }
        memberPanelOpen = true;
        memberScrim.setAlpha(0f);
        memberScrim.setVisibility(View.VISIBLE);
        memberPanel.setVisibility(View.VISIBLE);
        memberPanel.post(() -> {
            memberPanel.setTranslationX(memberPanel.getWidth());
            memberPanel.animate()
                    .translationX(0f)
                    .setDuration(220)
                    .start();
            memberScrim.animate()
                    .alpha(1f)
                    .setDuration(180)
                    .start();
        });
    }

    private void hideMemberPanel() {
        if (!memberPanelOpen) {
            return;
        }
        memberPanelOpen = false;
        memberPanel.animate()
                .translationX(memberPanel.getWidth())
                .setDuration(200)
                .withEndAction(() -> {
                    memberPanel.setVisibility(View.GONE);
                    memberPanel.setTranslationX(0f);
                })
                .start();
        memberScrim.animate()
                .alpha(0f)
                .setDuration(160)
                .withEndAction(() -> memberScrim.setVisibility(View.GONE))
                .start();
    }

    private void loadConversation() {
        restoreCachedHistory();
        refreshProjectChatState();
        int generation = ++loadGeneration;
        service.getChatMembers(conversation.getBackendSessionId())
                .enqueue(new Callback<Result<List<ChatGroupMember>>>() {
                    @Override
                    public void onResponse(Call<Result<List<ChatGroupMember>>> call,
                                           Response<Result<List<ChatGroupMember>>> response) {
                        if (generation != loadGeneration || isFinishing()) {
                            return;
                        }
                        if (response.code() == 401) {
                            SessionHelper.handleUnauthorized(chatActivity.this);
                            return;
                        }
                        if (response.isSuccessful() && response.body() != null
                                && response.body().getCode() == 1) {
                            bindMembers(response.body().getData());
                        } else if (response.code() == 403 || response.code() == 404) {
                            setChatReadOnly(ChatHistoryStore.roleExitReason(
                                    conversation.getCurrentUserRole()));
                        }
                        loadMessages(generation);
                    }

                    @Override
                    public void onFailure(Call<Result<List<ChatGroupMember>>> call, Throwable t) {
                        if (generation == loadGeneration && !isFinishing()) {
                            loadMessages(generation);
                        }
                    }
                });
    }

    private void bindMembers(List<ChatGroupMember> members) {
        ChatHistoryStore.reconcileMembers(this, currentAccountId,
                conversation.getBackendSessionId(), members);
        membersByAccountId.clear();
        if (members != null) {
            for (ChatGroupMember member : members) {
                User user = new User();
                user.setId(String.valueOf(member.getAccountId()));
                user.setName(TextUtils.isEmpty(member.getUsername())
                        ? "群成员" : member.getUsername());
                user.setAvatarUrl(member.getAvatarUrl());
                user.setMemberRole(member.getMemberRole());
                user.setRepresentedCount(member.getRepresentedCount());
                membersByAccountId.put(member.getAccountId(), user);
            }
        }

        User memberMe = membersByAccountId.get(currentAccountId);
        if (memberMe != null) {
            currentUser = memberMe;
            conversation.setCurrentUserRole(memberMe.getMemberRole());
        } else if (members != null) {
            setChatReadOnly(ChatHistoryStore.roleExitReason(
                    conversation.getCurrentUserRole()));
        }
        btnViewTrip.setVisibility(conversation.getProjectId() > 0
                ? View.VISIBLE : View.GONE);

        TextView subtitleView = findViewById(R.id.txtToolbarSubtitle);
        if (subtitleView != null && !membersByAccountId.isEmpty()) {
            subtitleView.setText((chatReadOnly ? "只读 · " : "")
                    + membersByAccountId.size() + "位成员");
        }
        if (memberAdapter != null) {
            memberAdapter.setMembers(members);
        }
        if (txtMemberPanelTitle != null) {
            int count = members == null ? 0 : members.size();
            txtMemberPanelTitle.setText("群成员（" + count + "）");
        }
    }

    private User resolveSender(int accountId) {
        if (accountId == -1) {
            User system = new User();
            system.setId("-1");
            system.setName("系统通知");
            system.setMemberRole("SYSTEM");
            return system;
        }
        User sender = membersByAccountId.get(accountId);
        if (sender != null) {
            return sender;
        }
        if (accountId == currentAccountId) {
            return currentUser;
        }
        User fallback = new User();
        fallback.setId(String.valueOf(accountId));
        fallback.setName("群成员");
        return fallback;
    }

    private void loadMessages(int generation) {
        service.getChatMessagesPage(conversation.getBackendSessionId(), 1,
                        ChatMessagePaging.PAGE_SIZE)
                .enqueue(new Callback<Result<List<ServerChatMessage>>>() {
                    @Override
                    public void onResponse(Call<Result<List<ServerChatMessage>>> call,
                                           Response<Result<List<ServerChatMessage>>> response) {
                        if (generation != loadGeneration || isFinishing()) {
                            return;
                        }
                        if (response.code() == 401) {
                            SessionHelper.handleUnauthorized(chatActivity.this);
                            return;
                        }
                        if (!response.isSuccessful() || response.body() == null
                                || response.body().getCode() != 1) {
                            if (response.code() == 403 || response.code() == 404) {
                                setChatReadOnly(ChatHistoryStore.roleExitReason(
                                        conversation.getCurrentUserRole()));
                            }
                            if (messages.isEmpty()) {
                                Toast.makeText(chatActivity.this,
                                        "消息加载失败", Toast.LENGTH_SHORT).show();
                            }
                            return;
                        }

                        List<ServerChatMessage> serverMessages = response.body().getData();
                        List<ServerChatMessage> latestPage =
                                ChatMessagePaging.pageBefore(serverMessages, null);
                        nextMessagePage = 2;
                        bindLatestPage(latestPage);
                        hasMoreOlderMessages = ChatMessagePaging.mayHaveOlder(
                                serverMessages, latestPage);
                        ChatHistoryStore.saveMessages(chatActivity.this, currentAccountId,
                                conversation.getBackendSessionId(), serverMessages);
                        ChatUnreadManager.markSessionRead(
                                chatActivity.this,
                                currentAccountId,
                                conversation.getBackendSessionId(),
                                latestPage);
                    }

                    @Override
                    public void onFailure(Call<Result<List<ServerChatMessage>>> call, Throwable t) {
                        if (messages.isEmpty()) {
                            Toast.makeText(chatActivity.this,
                                    "网络异常，消息加载失败", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void loadOlderMessages() {
        if (loadingOlderMessages || !hasMoreOlderMessages
                || displayedServerMessages.isEmpty()) return;
        long beforeId = oldestBackendMessageId();
        if (beforeId <= 0) {
            hasMoreOlderMessages = false;
            return;
        }

        loadingOlderMessages = true;
        int generation = loadGeneration;
        int requestedPage = nextMessagePage;
        service.getChatMessagesPage(conversation.getBackendSessionId(), requestedPage,
                        ChatMessagePaging.PAGE_SIZE)
                .enqueue(new Callback<Result<List<ServerChatMessage>>>() {
                    @Override
                    public void onResponse(Call<Result<List<ServerChatMessage>>> call,
                                           Response<Result<List<ServerChatMessage>>> response) {
                        loadingOlderMessages = false;
                        if (generation != loadGeneration || isFinishing()) return;
                        if (response.code() == 401) {
                            SessionHelper.handleUnauthorized(chatActivity.this);
                            return;
                        }
                        if (!response.isSuccessful() || response.body() == null
                                || response.body().getCode() != 1) {
                            loadOlderMessagesFromCache(beforeId);
                            return;
                        }

                        List<ServerChatMessage> serverMessages = response.body().getData();
                        List<ServerChatMessage> olderPage =
                                ChatMessagePaging.pageBefore(serverMessages, null);
                        if (serverMessages != null && !serverMessages.isEmpty()) {
                            nextMessagePage = requestedPage + 1;
                        }
                        if (olderPage.isEmpty()) {
                            olderPage = cachedPageBefore(beforeId);
                        }
                        int inserted = prependOlderPage(olderPage);
                        hasMoreOlderMessages = inserted > 0
                                && (serverMessages != null
                                && serverMessages.size() >= ChatMessagePaging.PAGE_SIZE
                                || hasCachedMessagesBefore(olderPage.get(0).getId()));
                        ChatHistoryStore.saveMessages(chatActivity.this, currentAccountId,
                                conversation.getBackendSessionId(), serverMessages);
                    }

                    @Override
                    public void onFailure(Call<Result<List<ServerChatMessage>>> call, Throwable t) {
                        loadingOlderMessages = false;
                        if (generation == loadGeneration && !isFinishing()) {
                            loadOlderMessagesFromCache(beforeId);
                        }
                    }
                });
    }

    private void bindLatestPage(List<ServerChatMessage> latestPage) {
        List<ServerChatMessage> combined = new ArrayList<>(latestPage);
        displayedServerMessages.clear();
        displayedServerMessages.addAll(combined);
        messages.clear();
        for (ServerChatMessage serverMessage : combined) {
            messages.add(toUiMessage(serverMessage));
        }
        adapter.notifyDataSetChanged();
        if (!messages.isEmpty()) recycler.scrollToPosition(messages.size() - 1);
    }

    private int prependOlderPage(List<ServerChatMessage> olderPage) {
        if (olderPage == null || olderPage.isEmpty()) return 0;
        Map<Long, Boolean> displayedIds = new HashMap<>();
        for (ServerChatMessage message : displayedServerMessages) {
            displayedIds.put(message.getId(), true);
        }
        List<ServerChatMessage> newMessages = new ArrayList<>();
        for (ServerChatMessage message : olderPage) {
            if (message != null && !displayedIds.containsKey(message.getId())) {
                newMessages.add(message);
            }
        }
        if (newMessages.isEmpty()) return 0;

        LinearLayoutManager layoutManager = (LinearLayoutManager) recycler.getLayoutManager();
        int firstVisible = layoutManager == null
                ? RecyclerView.NO_POSITION : layoutManager.findFirstVisibleItemPosition();
        View anchor = firstVisible == RecyclerView.NO_POSITION || layoutManager == null
                ? null : layoutManager.findViewByPosition(firstVisible);
        int anchorTop = anchor == null ? 0 : anchor.getTop();

        displayedServerMessages.addAll(0, newMessages);
        List<Message> uiMessages = new ArrayList<>();
        for (ServerChatMessage message : newMessages) uiMessages.add(toUiMessage(message));
        messages.addAll(0, uiMessages);
        adapter.notifyItemRangeInserted(0, newMessages.size());
        if (layoutManager != null && firstVisible != RecyclerView.NO_POSITION) {
            layoutManager.scrollToPositionWithOffset(
                    firstVisible + newMessages.size(), anchorTop);
        }
        return newMessages.size();
    }

    private void loadOlderMessagesFromCache(long beforeId) {
        List<ServerChatMessage> cached = cachedPageBefore(beforeId);
        if (cached.isEmpty()) {
            hasMoreOlderMessages = false;
            return;
        }
        int inserted = prependOlderPage(cached);
        hasMoreOlderMessages = inserted > 0
                && hasCachedMessagesBefore(cached.get(0).getId());
    }

    private List<ServerChatMessage> cachedPageBefore(long beforeId) {
        ChatHistoryStore.CachedSession cached = ChatHistoryStore.find(
                this, currentAccountId, conversation.getBackendSessionId());
        return ChatMessagePaging.pageBefore(
                cached == null ? null : cached.messages, beforeId);
    }

    private boolean hasCachedMessagesBefore(long beforeId) {
        ChatHistoryStore.CachedSession cached = ChatHistoryStore.find(
                this, currentAccountId, conversation.getBackendSessionId());
        if (cached == null || cached.messages == null) return false;
        for (ServerChatMessage message : cached.messages) {
            if (message != null && message.getId() < beforeId) return true;
        }
        return false;
    }

    private Message toUiMessage(ServerChatMessage message) {
        return new Message(resolveSender(message.getSenderAccountId()),
                message.getContent(), ChatMessageTime.timestamp(message));
    }

    private long oldestBackendMessageId() {
        for (ServerChatMessage message : displayedServerMessages) {
            if (message != null && !message.isSystemNotice() && message.getId() > 0) {
                return message.getId();
            }
        }
        return 0L;
    }

    private void openTripDetails() {
        if (conversation.getProjectId() <= 0) {
            return;
        }
        btnViewTrip.setEnabled(false);
        btnViewTrip.setText("正在加载…");
        service.getProject(conversation.getProjectId())
                .enqueue(new Callback<Result<Project>>() {
                    @Override
                    public void onResponse(Call<Result<Project>> call,
                                           Response<Result<Project>> response) {
                        restoreViewTripButton();
                        if (response.code() == 401) {
                            SessionHelper.handleUnauthorized(chatActivity.this);
                            return;
                        }
                        if (!response.isSuccessful() || response.body() == null
                                || response.body().getCode() != 1
                                || response.body().getData() == null) {
                            Toast.makeText(chatActivity.this,
                                    "行程详情加载失败", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        Project project = response.body().getData();
                        String role = currentUser == null
                                ? null : currentUser.getMemberRole();
                        Class<?> target = "LEADER".equalsIgnoreCase(role)
                                || "ADMIN".equalsIgnoreCase(role)
                                ? orderDetailActivity.class : TeamDetailActivity.class;
                        Intent intent = new Intent(chatActivity.this, target);
                        intent.putExtra(TeamDetailActivity.EXTRA_PROJECT_JSON,
                                new Gson().toJson(project));
                        startActivity(intent);
                    }

                    @Override
                    public void onFailure(Call<Result<Project>> call, Throwable t) {
                        restoreViewTripButton();
                        Toast.makeText(chatActivity.this,
                                "网络异常，加载失败", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void restoreViewTripButton() {
        btnViewTrip.setEnabled(true);
        btnViewTrip.setText("查看行程详情");
    }

    private void sendMessage() {
        if (chatReadOnly || conversation.isReadOnly()) {
            Toast.makeText(this, readOnlyReason(), Toast.LENGTH_SHORT).show();
            return;
        }
        String content = edtInput.getText().toString().trim();
        if (content.isEmpty()) {
            Toast.makeText(this, "请输入消息内容", Toast.LENGTH_SHORT).show();
            return;
        }

        if (conversation.getProjectId() > 0) {
            btnSend.setEnabled(false);
            service.getProject(conversation.getProjectId()).enqueue(new Callback<Result<Project>>() {
                @Override
                public void onResponse(Call<Result<Project>> call, Response<Result<Project>> response) {
                    if (!response.isSuccessful() || response.body() == null
                            || response.body().getCode() != 1 || response.body().getData() == null) {
                        btnSend.setEnabled(true);
                        Toast.makeText(chatActivity.this,
                                "暂时无法确认行程状态，请稍后再试", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String status = ProjectUiHelper.normalizeStatus(response.body().getData().getStatus());
                    if (ProjectUiHelper.STATUS_CANCELLED.equals(status)
                            || ProjectUiHelper.STATUS_DONE.equals(status)) {
                        setChatReadOnly(ProjectUiHelper.STATUS_CANCELLED.equals(status)
                                ? "发布者已取消行程，所有成员仅可查看历史消息"
                                : "行程已结束，聊天记录仅供查看");
                        return;
                    }
                    sendMessageNow(content);
                }

                @Override
                public void onFailure(Call<Result<Project>> call, Throwable t) {
                    btnSend.setEnabled(true);
                    Toast.makeText(chatActivity.this,
                            "网络异常，暂时无法发送", Toast.LENGTH_SHORT).show();
                }
            });
            return;
        }
        sendMessageNow(content);
    }

    private void sendMessageNow(String content) {
        btnSend.setEnabled(false);
        SendChatMessageRequest request = new SendChatMessageRequest(
                conversation.getBackendSessionId(), content);
        service.sendChatMessage(request).enqueue(new Callback<Result<Long>>() {
            @Override
            public void onResponse(Call<Result<Long>> call, Response<Result<Long>> response) {
                btnSend.setEnabled(true);
                if (response.code() == 401) {
                    SessionHelper.handleUnauthorized(chatActivity.this);
                    return;
                }
                if (!response.isSuccessful() || response.body() == null
                        || response.body().getCode() != 1) {
                    String message = response.body() != null ? response.body().getMsg() : "发送失败";
                    if (!TextUtils.isEmpty(message)
                            && (message.contains("退出") || message.contains("关闭")
                            || message.contains("成员") || message.contains("取消"))) {
                        setChatReadOnly("当前行程群聊已关闭，聊天记录仅供查看");
                    }
                    Toast.makeText(chatActivity.this, message, Toast.LENGTH_SHORT).show();
                    return;
                }

                Long messageId = response.body().getData();
                if (messageId != null) {
                    ChatUnreadManager.markSessionRead(
                            chatActivity.this,
                            currentAccountId,
                            conversation.getBackendSessionId(),
                            messageId);
                }
                ServerChatMessage sentMessage = new ServerChatMessage(messageId == null
                        ? System.currentTimeMillis() : messageId,
                        conversation.getBackendSessionId(), currentAccountId,
                        content, "TEXT", null);
                sentMessage.setLocalTimestamp(System.currentTimeMillis());
                displayedServerMessages.add(sentMessage);
                messages.add(new Message(currentUser, content, System.currentTimeMillis()));
                ChatHistoryStore.appendMessage(chatActivity.this, currentAccountId,
                        conversation.getBackendSessionId(), sentMessage);
                adapter.notifyItemInserted(messages.size() - 1);
                recycler.scrollToPosition(messages.size() - 1);
                edtInput.setText("");
            }

            @Override
            public void onFailure(Call<Result<Long>> call, Throwable t) {
                btnSend.setEnabled(true);
                Toast.makeText(chatActivity.this, "网络异常，发送失败", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (service != null && conversation != null) {
            if (systemConversation) {
                restoreSystemNotifications();
            } else {
                loadConversation();
            }
        }
    }

    private void restoreSystemNotifications() {
        if (adapter == null || currentAccountId <= 0) return;
        List<ServerChatMessage> localMessages = ChatHistoryStore.getAllSystemMessages(
                this, currentAccountId);
        displayedServerMessages.clear();
        displayedServerMessages.addAll(localMessages);
        messages.clear();
        for (ServerChatMessage message : localMessages) {
            messages.add(toUiMessage(message));
        }
        if (messages.isEmpty()) {
            messages.add(new Message(resolveSender(-1),
                    "群聊人员变化会显示在这里", System.currentTimeMillis()));
        }
        adapter.notifyDataSetChanged();
        hasMoreOlderMessages = false;
        if (!messages.isEmpty()) recycler.scrollToPosition(messages.size() - 1);
        ChatHistoryStore.markAllSystemNoticesRead(this, currentAccountId);
    }

    private void restoreCachedHistory() {
        if (conversation == null || currentAccountId <= 0 || adapter == null) return;
        ChatHistoryStore.CachedSession cached = ChatHistoryStore.find(
                this, currentAccountId, conversation.getBackendSessionId());
        if (cached == null) return;
        conversation.setChatStatus(cached.status);
        conversation.setCurrentUserRole(cached.currentUserRole);
        conversation.setReadOnly(cached.readOnly);
        conversation.setReadOnlyReason(cached.readOnlyReason);
        chatReadOnly = cached.readOnly;
        if (cached.members != null && !cached.members.isEmpty()) {
            bindCachedMembers(cached.members);
        }
        if (cached.messages != null && !cached.messages.isEmpty() && messages.isEmpty()) {
            bindCachedMessages(cached.messages);
        }
        applyReadOnlyState();
    }

    private void bindCachedMembers(List<ChatGroupMember> members) {
        membersByAccountId.clear();
        for (ChatGroupMember member : members) {
            User user = new User();
            user.setId(String.valueOf(member.getAccountId()));
            user.setName(TextUtils.isEmpty(member.getUsername()) ? "群成员" : member.getUsername());
            user.setAvatarUrl(member.getAvatarUrl());
            user.setMemberRole(member.getMemberRole());
            user.setRepresentedCount(member.getRepresentedCount());
            membersByAccountId.put(member.getAccountId(), user);
        }
        User memberMe = membersByAccountId.get(currentAccountId);
        if (memberMe != null) currentUser = memberMe;
        if (memberAdapter != null) memberAdapter.setMembers(members);
        if (txtMemberPanelTitle != null) {
            txtMemberPanelTitle.setText("群成员（" + members.size() + "）");
        }
    }

    private void bindCachedMessages(List<ServerChatMessage> cachedMessages) {
        List<ServerChatMessage> latestPage =
                ChatMessagePaging.pageBefore(cachedMessages, null);
        bindLatestPage(latestPage);
        nextMessagePage = 2;
        hasMoreOlderMessages = cachedMessages.size() > latestPage.size();
    }

    private void refreshProjectChatState() {
        if (conversation.getProjectId() <= 0) return;
        service.getProject(conversation.getProjectId()).enqueue(new Callback<Result<Project>>() {
            @Override
            public void onResponse(Call<Result<Project>> call, Response<Result<Project>> response) {
                if (!response.isSuccessful() || response.body() == null
                        || response.body().getCode() != 1 || response.body().getData() == null
                        || isFinishing()) return;
                String status = ProjectUiHelper.normalizeStatus(response.body().getData().getStatus());
                if (ProjectUiHelper.STATUS_CANCELLED.equals(status)) {
                    setChatReadOnly("发布者已取消行程，所有成员仅可查看历史消息");
                } else if (ProjectUiHelper.STATUS_DONE.equals(status)) {
                    setChatReadOnly("行程已结束，聊天记录仅供查看");
                }
            }

            @Override
            public void onFailure(Call<Result<Project>> call, Throwable t) {
                // 使用会话状态和本地记录兜底。
            }
        });
    }

    private void setChatReadOnly(String reason) {
        chatReadOnly = true;
        conversation.setReadOnly(true);
        conversation.setReadOnlyReason(reason);
        ChatHistoryStore.markSessionReadOnly(this, currentAccountId,
                conversation.getBackendSessionId(), reason);
        applyReadOnlyState();
    }

    private String readOnlyReason() {
        return TextUtils.isEmpty(conversation.getReadOnlyReason())
                ? "聊天记录仅供查看" : conversation.getReadOnlyReason();
    }

    private void applyReadOnlyState() {
        chatReadOnly = chatReadOnly || (conversation != null && conversation.isReadOnly());
        if (txtReadOnlyNotice != null) {
            txtReadOnlyNotice.setText(readOnlyReason());
            txtReadOnlyNotice.setVisibility(chatReadOnly ? View.VISIBLE : View.GONE);
        }
        if (edtInput != null) {
            edtInput.setVisibility(chatReadOnly ? View.GONE : View.VISIBLE);
            edtInput.setEnabled(!chatReadOnly);
        }
        if (btnSend != null) {
            btnSend.setVisibility(chatReadOnly ? View.GONE : View.VISIBLE);
            btnSend.setEnabled(!chatReadOnly);
        }
        TextView subtitle = findViewById(R.id.txtToolbarSubtitle);
        if (chatReadOnly && subtitle != null
                && !subtitle.getText().toString().startsWith("只读")) {
            subtitle.setText("只读 · " + subtitle.getText());
        }
    }

}
