package com.example.Japp.Chat;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.Japp.Chat.adapter.chatAdapter;
import com.example.Japp.R;
import com.example.Japp.data.Conversation;
import com.example.Japp.data.Message;
import com.example.Japp.data.User;
import com.example.Japp.database.DatabaseManager;
import com.example.Japp.database.dao.ConversationDao;
import com.example.Japp.database.dao.MessageDao;
import com.example.Japp.database.dao.UserDao;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class chatActivity extends AppCompatActivity {

    private Conversation conversation;
    private RecyclerView recycler;
    private chatAdapter adapter;
    private EditText edtInput;
    private MaterialButton btnSend;
    private List<Message> messages;
    private User currentUser;

    private DatabaseManager dbManager;
    private MessageDao messageDao;
    private ConversationDao conversationDao;
    private UserDao userDao;
    private String conversationId;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        conversation = (Conversation) getIntent().getSerializableExtra("conversation_info");
        if (conversation == null) {
            finish();
            return;
        }

        initDatabase();
        initCurrentUser();

        conversationId = conversationDao.generateConversationId(
                currentUser.getId(),
                conversation.getUser_opposite().getId()
        );

        setOppositeName();
        initViews();
        setupRecyclerView();
        loadMessagesFromDatabase();

        btnSend.setOnClickListener(v -> sendMessage());

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void initDatabase() {
        dbManager = DatabaseManager.getInstance(this);
        messageDao = new MessageDao(dbManager);
        conversationDao = new ConversationDao(dbManager);
        userDao = new UserDao(dbManager);
    }

    private void initCurrentUser() {
        String userInfo = getSharedPreferences("user_pref", MODE_PRIVATE)
                .getString("user_inf", "");

        if (!TextUtils.isEmpty(userInfo)) {
            currentUser = new User();
            currentUser.setId(currentUser.getId(userInfo));
            currentUser.setName(currentUser.getUsername(userInfo));
            currentUser.setPhone(currentUser.getPhone(userInfo));
            currentUser.setPassword(currentUser.getPassword(userInfo));
        } else {
            currentUser = new User("我", "", "");
        }
    }

    private void initViews() {
        recycler = findViewById(R.id.recycler);
        edtInput = findViewById(R.id.edtInput);
        btnSend = findViewById(R.id.btnSend);
        messages = new ArrayList<>();
    }

    private void setupRecyclerView() {
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new chatAdapter(messages, currentUser.getId());
        recycler.setAdapter(adapter);
    }

    private void setOppositeName() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(conversation.getUser_opposite().getUsername());
    }

    private void loadMessagesFromDatabase() {
        executorService.execute(() -> {
            List<Message> dbMessages = messageDao.getMessagesByConversation(
                    conversationId,
                    currentUser,
                    conversation.getUser_opposite()
            );

            mainHandler.post(() -> {
                if (!dbMessages.isEmpty()) {
                    messages.clear();
                    messages.addAll(dbMessages);
                    adapter.notifyDataSetChanged();

                    // 更新Conversation的消息列表
                    updateConversationMessages();

                    if (!messages.isEmpty()) {
                        recycler.scrollToPosition(messages.size() - 1);
                    }

                    markMessagesAsRead();
                }
            });
        });
    }

    private void updateConversationMessages() {
        // 同步更新Conversation对象的消息列表
        conversation.getMessages().clear();
        for (Message msg : messages) {
            conversation.getMessages().add(msg.getContent());
        }
    }

    private void markMessagesAsRead() {
        executorService.execute(() -> {
            messageDao.markMessagesAsRead(conversationId, currentUser.getId());
            conversationDao.resetUnreadCount(conversationId);
        });
    }

    private void sendMessage() {
        String messageContent = edtInput.getText().toString().trim();
        if (messageContent.isEmpty()) {
            Toast.makeText(this, "请输入消息内容", Toast.LENGTH_SHORT).show();
            return;
        }

        // 创建新消息
        Message newMessage = new Message(currentUser, messageContent, System.currentTimeMillis());
        messages.add(newMessage);
        adapter.notifyItemInserted(messages.size() - 1);
        recycler.scrollToPosition(messages.size() - 1);
        edtInput.setText("");

        // 更新Conversation的消息列表
        updateConversationMessages();

        // 保存到数据库
        executorService.execute(() -> {
            try {
                // 保存消息
                messageDao.insertMessage(newMessage, conversationId);

                // 更新会话的最后一条消息
                conversationDao.updateLastMessage(conversationId, messageContent, newMessage.getTimestamp());

                // 更新会话信息（包括未读计数等）
                conversation.setUnRead_num(0);
                conversationDao.insertOrUpdateConversation(conversation, currentUser.getId());

                // 保存用户信息
                userDao.insertOrUpdateUser(currentUser);
                userDao.insertOrUpdateUser(conversation.getUser_opposite());
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // 模拟回复
        simulateReply();
    }

    private void simulateReply() {
        new Handler().postDelayed(() -> {
            User oppositeUser = conversation.getUser_opposite();
            String replyContent = getAutoReply(edtInput.getText().toString());
            Message replyMessage = new Message(oppositeUser, replyContent, System.currentTimeMillis());
            messages.add(replyMessage);
            adapter.notifyItemInserted(messages.size() - 1);
            recycler.scrollToPosition(messages.size() - 1);

            // 更新Conversation的消息列表
            updateConversationMessages();

            // 保存回复消息到数据库
            executorService.execute(() -> {
                try {
                    messageDao.insertMessage(replyMessage, conversationId);
                    conversationDao.updateLastMessage(conversationId, replyContent, replyMessage.getTimestamp());
                    conversationDao.insertOrUpdateConversation(conversation, currentUser.getId());
                    // 增加未读计数（对方发来的消息）
                    conversationDao.incrementUnreadCount(conversationId, currentUser.getId());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }, 1000);
    }

    private String getAutoReply(String userMessage) {
        if (userMessage.contains("你好") || userMessage.contains("hi") || userMessage.contains("hello")) {
            return "你好！有什么可以帮你的吗？";
        } else if (userMessage.contains("谢谢")) {
            return "不客气！";
        } else if (userMessage.contains("再见")) {
            return "再见，期待下次交流！";
        } else {
            return "收到，我会尽快回复你。";
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        String draft = edtInput.getText().toString().trim();
        if (!TextUtils.isEmpty(draft)) {
            executorService.execute(() -> conversationDao.saveDraft(conversationId, draft));
        }
        updateConversationMessages();
    }

    @Override
    protected void onResume() {
        super.onResume();
        executorService.execute(() -> {
            String draft = conversationDao.getDraft(conversationId);
            mainHandler.post(() -> {
                if (draft != null && !TextUtils.isEmpty(draft)) {
                    edtInput.setText(draft);
                    edtInput.setSelection(draft.length());
                }
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }
}