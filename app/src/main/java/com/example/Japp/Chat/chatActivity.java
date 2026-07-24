package com.example.Japp.Chat;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.Japp.Chat.adapter.ChatMemberAdapter;
import com.example.Japp.Chat.adapter.chatAdapter;
import com.example.Japp.R;
import com.example.Japp.data.Conversation;
import com.example.Japp.data.Message;
import com.example.Japp.data.User;
import com.example.Japp.network.ApiClient;
import com.example.Japp.network.api.UserService;
import com.example.Japp.network.models.ChatGroupMember;
import com.example.Japp.network.models.Result;
import com.example.Japp.network.models.ServerChatMessage;
import com.example.Japp.network.models.requests.SendChatMessageRequest;
import com.example.Japp.user.util.SessionHelper;
import com.example.Japp.util.DisplayCutoutAdapter;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class chatActivity extends AppCompatActivity {

    private static final String MEMBER_GREETING = "大家好，很高兴加入群聊，请多关照！";
    private static final String LEADER_GREETING = "大家好，我是本次领队，很高兴认识大家！";

    private Conversation conversation;
    private RecyclerView recycler;
    private chatAdapter adapter;
    private EditText edtInput;
    private MaterialButton btnSend;
    private final List<Message> messages = new ArrayList<>();
    private User currentUser;
    private UserService service;
    private int currentAccountId;
    private int loadGeneration;
    private final Map<Integer, User> membersByAccountId = new HashMap<>();
    private View memberPanel;
    private View memberScrim;
    private TextView txtMemberPanelTitle;
    private ChatMemberAdapter memberAdapter;
    private MaterialButton btnCancelTrip;
    private View cancelTripDivider;
    private boolean memberPanelOpen;
    private boolean automaticGreetingInFlight;
    private boolean automaticGreetingSent;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        DisplayCutoutAdapter.apply(this);

        conversation = (Conversation) getIntent().getSerializableExtra("conversation_info");
        if (conversation == null || conversation.getBackendSessionId() <= 0) {
            Toast.makeText(this, "会话信息无效", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

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
        btnSend.setOnClickListener(v -> sendMessage());
    }

    private void initViews() {
        recycler = findViewById(R.id.recycler);
        edtInput = findViewById(R.id.edtInput);
        btnSend = findViewById(R.id.btnSend);
        memberPanel = findViewById(R.id.memberPanel);
        memberScrim = findViewById(R.id.memberScrim);
        txtMemberPanelTitle = findViewById(R.id.txtMemberPanelTitle);
        btnCancelTrip = findViewById(R.id.btnCancelTrip);
        cancelTripDivider = findViewById(R.id.cancelTripDivider);
    }

    private void setupRecyclerView() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        recycler.setLayoutManager(layoutManager);
        adapter = new chatAdapter(messages, String.valueOf(currentAccountId));
        recycler.setAdapter(adapter);
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
        btnCancelTrip.setOnClickListener(v -> confirmCancelTrip());
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
            if (conversation.isGroup() && !conversation.getMemberNames().isEmpty()) {
                subtitleView.setText(String.join("、", conversation.getMemberNames()));
            } else {
                subtitleView.setText("项目沟通");
            }
        }

        TextView avatarView = findViewById(R.id.txtToolbarAvatar);
        if (avatarView != null) {
            avatarView.setText(initialOf(title));
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
        }
        boolean isPublisher = memberMe != null
                && ("PUBLISHER".equalsIgnoreCase(memberMe.getMemberRole())
                || "OWNER".equalsIgnoreCase(memberMe.getMemberRole()));
        int cancelVisibility = isPublisher && conversation.getProjectId() > 0
                ? View.VISIBLE : View.GONE;
        btnCancelTrip.setVisibility(cancelVisibility);
        cancelTripDivider.setVisibility(cancelVisibility);

        TextView subtitleView = findViewById(R.id.txtToolbarSubtitle);
        if (subtitleView != null && !membersByAccountId.isEmpty()) {
            subtitleView.setText(membersByAccountId.size() + "位成员");
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
        service.getChatMessages(conversation.getBackendSessionId())
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
                            Toast.makeText(chatActivity.this, "消息加载失败", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        messages.clear();
                        List<ServerChatMessage> serverMessages = response.body().getData();
                        if (serverMessages != null) {
                            for (ServerChatMessage serverMessage : serverMessages) {
                                messages.add(new Message(
                                        resolveSender(serverMessage.getSenderAccountId()),
                                        serverMessage.getContent(),
                                        System.currentTimeMillis()));
                            }
                        }
                        adapter.notifyDataSetChanged();
                        if (!messages.isEmpty()) {
                            recycler.scrollToPosition(messages.size() - 1);
                        }
                        maybeSendAutomaticGreeting(serverMessages);
                    }

                    @Override
                    public void onFailure(Call<Result<List<ServerChatMessage>>> call, Throwable t) {
                        Toast.makeText(chatActivity.this, "网络异常，消息加载失败", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void maybeSendAutomaticGreeting(List<ServerChatMessage> serverMessages) {
        if (automaticGreetingSent || automaticGreetingInFlight || currentUser == null) {
            return;
        }

        String role = currentUser.getMemberRole();
        if (TextUtils.isEmpty(role)) {
            return;
        }
        if ("PUBLISHER".equalsIgnoreCase(role) || "OWNER".equalsIgnoreCase(role)) {
            automaticGreetingSent = true;
            return;
        }

        if (serverMessages != null) {
            for (ServerChatMessage message : serverMessages) {
                if (message.getSenderAccountId() == currentAccountId
                        && (MEMBER_GREETING.equals(message.getContent())
                        || LEADER_GREETING.equals(message.getContent()))) {
                    automaticGreetingSent = true;
                    return;
                }
            }
        }

        String greeting = "LEADER".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role)
                ? LEADER_GREETING : MEMBER_GREETING;
        automaticGreetingInFlight = true;
        SendChatMessageRequest request = new SendChatMessageRequest(
                conversation.getBackendSessionId(), greeting);
        service.sendChatMessage(request).enqueue(new Callback<Result<Long>>() {
            @Override
            public void onResponse(Call<Result<Long>> call, Response<Result<Long>> response) {
                automaticGreetingInFlight = false;
                if (response.code() == 401) {
                    SessionHelper.handleUnauthorized(chatActivity.this);
                    return;
                }
                if (!response.isSuccessful() || response.body() == null
                        || response.body().getCode() != 1 || isFinishing()) {
                    return;
                }
                automaticGreetingSent = true;
                messages.add(new Message(currentUser, greeting, System.currentTimeMillis()));
                adapter.notifyItemInserted(messages.size() - 1);
                recycler.scrollToPosition(messages.size() - 1);
            }

            @Override
            public void onFailure(Call<Result<Long>> call, Throwable t) {
                automaticGreetingInFlight = false;
            }
        });
    }

    private void confirmCancelTrip() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("取消行程")
                .setMessage("取消后行程将结束，群聊也会被删除且无法继续发送消息。确定继续吗？")
                .setNegativeButton("暂不取消", null)
                .setPositiveButton("确认取消", (dialog, which) -> cancelTrip())
                .show();
    }

    private void cancelTrip() {
        btnCancelTrip.setEnabled(false);
        btnCancelTrip.setText("正在取消…");
        service.updateProjectStatus(conversation.getProjectId(), "CANCELLED")
                .enqueue(new Callback<Result>() {
                    @Override
                    public void onResponse(Call<Result> call, Response<Result> response) {
                        if (response.code() == 401) {
                            SessionHelper.handleUnauthorized(chatActivity.this);
                            return;
                        }
                        if (!response.isSuccessful() || response.body() == null
                                || response.body().getCode() != 1) {
                            restoreCancelButton();
                            String message = response.body() == null
                                    ? "取消行程失败" : response.body().getMsg();
                            Toast.makeText(chatActivity.this, message, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        Toast.makeText(chatActivity.this,
                                "行程已取消，群聊已删除", Toast.LENGTH_SHORT).show();
                        setResult(RESULT_OK);
                        finish();
                    }

                    @Override
                    public void onFailure(Call<Result> call, Throwable t) {
                        restoreCancelButton();
                        Toast.makeText(chatActivity.this,
                                "网络异常，取消失败", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void restoreCancelButton() {
        btnCancelTrip.setEnabled(true);
        btnCancelTrip.setText("取消行程并删除群聊");
    }

    private void sendMessage() {
        String content = edtInput.getText().toString().trim();
        if (content.isEmpty()) {
            Toast.makeText(this, "请输入消息内容", Toast.LENGTH_SHORT).show();
            return;
        }

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
                    Toast.makeText(chatActivity.this, message, Toast.LENGTH_SHORT).show();
                    return;
                }

                messages.add(new Message(currentUser, content, System.currentTimeMillis()));
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
            loadConversation();
        }
    }

}
