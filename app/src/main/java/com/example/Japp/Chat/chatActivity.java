package com.example.Japp.Chat;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.Japp.Chat.adapter.chatAdapter;
import com.example.Japp.R;
import com.example.Japp.data.Conversation;
import com.example.Japp.data.Message;
import com.example.Japp.data.User;
import com.example.Japp.network.ApiClient;
import com.example.Japp.network.api.UserService;
import com.example.Japp.network.models.Result;
import com.example.Japp.network.models.ServerChatMessage;
import com.example.Japp.network.models.requests.SendChatMessageRequest;
import com.example.Japp.user.util.SessionHelper;
import com.example.Japp.util.DisplayCutoutAdapter;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class chatActivity extends AppCompatActivity {

    private Conversation conversation;
    private RecyclerView recycler;
    private chatAdapter adapter;
    private EditText edtInput;
    private MaterialButton btnSend;
    private final List<Message> messages = new ArrayList<>();
    private User currentUser;
    private UserService service;
    private int currentAccountId;

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
        btnSend.setOnClickListener(v -> sendMessage());
    }

    private void initViews() {
        recycler = findViewById(R.id.recycler);
        edtInput = findViewById(R.id.edtInput);
        btnSend = findViewById(R.id.btnSend);
    }

    private void setupRecyclerView() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        recycler.setLayoutManager(layoutManager);
        adapter = new chatAdapter(messages, String.valueOf(currentAccountId));
        recycler.setAdapter(adapter);
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

    private void loadMessages() {
        service.getChatMessages(conversation.getBackendSessionId())
                .enqueue(new Callback<Result<List<ServerChatMessage>>>() {
                    @Override
                    public void onResponse(Call<Result<List<ServerChatMessage>>> call,
                                           Response<Result<List<ServerChatMessage>>> response) {
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
                                User sender = serverMessage.getSenderAccountId() == currentAccountId
                                        ? currentUser
                                        : conversation.getUser_opposite();
                                messages.add(new Message(sender, serverMessage.getContent(),
                                        System.currentTimeMillis()));
                            }
                        }
                        adapter.notifyDataSetChanged();
                        if (!messages.isEmpty()) {
                            recycler.scrollToPosition(messages.size() - 1);
                        }
                    }

                    @Override
                    public void onFailure(Call<Result<List<ServerChatMessage>>> call, Throwable t) {
                        Toast.makeText(chatActivity.this, "网络异常，消息加载失败", Toast.LENGTH_SHORT).show();
                    }
                });
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
            loadMessages();
        }
    }
}
