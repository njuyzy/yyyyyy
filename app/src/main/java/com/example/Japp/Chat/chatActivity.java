package com.example.Japp.Chat;

import android.content.SharedPreferences;
import android.os.Bundle;
<<<<<<< HEAD
import android.os.Handler;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
=======
import android.widget.EditText;
>>>>>>> 8e17abf98766200ef08a42fca1e64b4600ad7f30
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.Japp.Chat.adapter.chatAdapter;
import com.example.Japp.Chat.utils.ChatStorageHelper;
import com.example.Japp.R;
import com.example.Japp.data.Conversation;
import com.example.Japp.data.Message;
import com.example.Japp.data.User;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

import java.util.ArrayList;
import java.util.List;

public class chatActivity extends AppCompatActivity {

    private static final String CURRENT_USER_ID = "current_user_id";

    private Conversation conversation;
    private RecyclerView recycler;
    private chatAdapter adapter;
    private EditText edtInput;
<<<<<<< HEAD
    private ImageButton btnSend;
    private SharedPreferences sharedPreferences;
    private String currentUserId;
    private ChatStorageHelper storageHelper;
=======
    private MaterialButton btnSend;
    private List<Message> messages;
    private User currentUser;
>>>>>>> 8e17abf98766200ef08a42fca1e64b4600ad7f30

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

<<<<<<< HEAD
        try {
            // 初始化 SharedPreferences
            sharedPreferences = getSharedPreferences("user_pref", MODE_PRIVATE);
            currentUserId = getCurrentUserId();
            storageHelper = new ChatStorageHelper(this);

            // 获取会话信息
            conversation = (Conversation) getIntent().getSerializableExtra("conversation_info");
            if (conversation == null) {
                Toast.makeText(this, "会话信息错误", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            // 确保会话有当前用户信息
            if (conversation.getUser_me() == null) {
                User currentUser = new User();
                currentUser.setId(currentUserId);
                conversation.setUser_me(currentUser);
            }

            // 初始化控件
            initViews();

            // 设置标题
            setOppositeName();

            // 加载消息
            loadMessages();

            // 设置监听器
            setupListeners();
        } catch (Exception e) {
            Toast.makeText(this, "应用启动失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void initViews() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });

        recycler = findViewById(R.id.recycler);
        edtInput = findViewById(R.id.edtInput);
        btnSend = findViewById(R.id.btnSend);

        // 设置 RecyclerView
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        recycler.setLayoutManager(layoutManager);

        adapter = new chatAdapter();
        adapter.setCurrentUserId(getCurrentUserId());
        recycler.setAdapter(adapter);

        // 设置初始消息列表
        if (conversation.getMessages() != null) {
            adapter.setMessages(conversation.getMessages());
        }
    }

    private void setOppositeName() {
        try {
            MaterialToolbar toolbar = findViewById(R.id.toolbar);
            User oppositeUser = conversation.getUser_opposite();
            if (oppositeUser != null && oppositeUser.getUsername() != null) {
                toolbar.setTitle(oppositeUser.getUsername());
            } else {
                toolbar.setTitle("未知联系人");
            }
        } catch (Exception e) {
            android.util.Log.e("ChatActivity", "Error setting toolbar title: " + e.getMessage());
        }
    }

    private String getCurrentUserId() {
        if (currentUserId != null && currentUserId.contains("id:")) {
            return currentUserId.split(" ")[0].split(":")[1];
        }
        return "current_user";
    }

    private void loadMessages() {
        try {
            // 从SQLite数据库加载消息
            String oppositeId = conversation != null ? conversation.getUser_opposite() != null ? conversation.getUser_opposite().getId() : null : null;
            if (oppositeId == null) {
                Toast.makeText(this, "无法加载消息: 联系人信息缺失", Toast.LENGTH_SHORT).show();
                // 添加一些测试消息
                addTestMessages();
                return;
            }

            storageHelper.getConversationByOppositeId(oppositeId, currentUserId, new ChatStorageHelper.GetConversationCallback() {
                @Override
                public void onGetComplete(Conversation savedConversation) {
                    if (savedConversation != null && savedConversation.getMessages() != null && !savedConversation.getMessages().isEmpty()) {
                        conversation = savedConversation;
                        adapter.setMessages(conversation.getMessages());
                    } else {
                        // 如果没有保存的消息，添加测试消息
                        addTestMessages();
                    }
                    scrollToBottom();
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "加载消息失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            android.util.Log.e("ChatActivity", "Error loading messages: " + e.getMessage());
            // 添加测试消息作为后备
            addTestMessages();
        }
    }

    private void setupListeners() {
        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendMessage();
            }
        });

        // 监听输入框，控制发送按钮状态
        edtInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                btnSend.setEnabled(s.length() > 0);
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        // 初始禁用发送按钮
        btnSend.setEnabled(false);
    }

    private void sendMessage() {
        try {
            String content = edtInput.getText().toString().trim();
            if (content.isEmpty()) {
                return;
            }

            // 获取对方用户ID
            String receiverId = conversation != null && conversation.getUser_opposite() != null ? conversation.getUser_opposite().getId() : null;
            if (receiverId == null) {
                Toast.makeText(this, "无法发送消息: 联系人信息缺失", Toast.LENGTH_SHORT).show();
                return;
            }

            // 创建消息对象
            Message message = new Message(content, Message.TYPE_SENT, currentUserId, receiverId);

            // 添加到会话对象
            if (conversation != null) {
                conversation.addMessage(message);
            }

            // 更新适配器
            adapter.addMessage(message);

            // 保存会话到持久化存储
            if (storageHelper != null) {
                storageHelper.saveConversation(conversation);
            }

            // 清空输入框
            edtInput.setText("");

            // 滚动到底部
            scrollToBottom();

            // 模拟收到回复（实际项目中应该从服务器获取）
            simulateReply(content);
        } catch (Exception e) {
            Toast.makeText(this, "发送消息失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            android.util.Log.e("ChatActivity", "Error sending message: " + e.getMessage());
        }
    }

    private void scrollToBottom() {
        recycler.post(new Runnable() {
            @Override
            public void run() {
                if (adapter.getItemCount() > 0) {
                    recycler.smoothScrollToPosition(adapter.getItemCount() - 1);
                }
            }
        });
    }

    // 模拟对方回复（实际项目应该从服务器接收消息）
    private void simulateReply(String sentMessage) {
        try {
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    String replyText = generateAutoReply(sentMessage);
                    String receiverId = currentUserId;

                    if (conversation != null && conversation.getUser_opposite() != null) {
                        Message replyMessage = new Message(replyText, Message.TYPE_RECEIVED,
                                conversation.getUser_opposite().getId(), receiverId);

                        adapter.addMessage(replyMessage);
                        if (conversation != null) {
                            conversation.addMessage(replyMessage);
                        }
                        if (storageHelper != null) {
                            storageHelper.saveConversation(conversation);
                        }
                        scrollToBottom();
                    }
                }
            }, 1500);
        } catch (Exception e) {
            android.util.Log.e("ChatActivity", "Error simulating reply: " + e.getMessage());
        }
    }

    private String generateAutoReply(String message) {
        // 简单的自动回复逻辑
        switch (message.toLowerCase()) {
            case "你好":
            case "hi":
            case "hello":
                return "你好！有什么可以帮助你的吗？";
            case "在吗":
                return "我在，请说！";
            case "谢谢":
                return "不客气！";
            default:
                return "收到你的消息：" + message;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            // 保存会话数据
            if (conversation != null && storageHelper != null) {
                storageHelper.saveConversation(conversation);
            }
        } catch (Exception e) {
            android.util.Log.e("ChatActivity", "Error saving conversation on pause: " + e.getMessage());
        }
    }

    /**
     * 添加测试消息
     */
    private void addTestMessages() {
        if (conversation == null) {
            return;
        }

        // 确保消息列表存在
        if (conversation.getMessages() == null) {
            conversation.setMessages(new ArrayList<>());
        }

        // 清除现有消息并添加测试消息
        conversation.getMessages().clear();

        // 添加一些测试消息
        Message testMsg1 = new Message("你好！这是一条测试消息。", Message.TYPE_SENT, currentUserId,
                conversation.getUser_opposite() != null ? conversation.getUser_opposite().getId() : "test_user");
        Message testMsg2 = new Message("您好！这是自动回复的测试消息。", Message.TYPE_RECEIVED,
                conversation.getUser_opposite() != null ? conversation.getUser_opposite().getId() : "test_user", currentUserId);
        Message testMsg3 = new Message("测试聊天功能是否正常工作？", Message.TYPE_SENT, currentUserId,
                conversation.getUser_opposite() != null ? conversation.getUser_opposite().getId() : "test_user");
        Message testMsg4 = new Message("聊天功能工作正常！消息气泡颜色和布局应该都显示正确。", Message.TYPE_RECEIVED,
                conversation.getUser_opposite() != null ? conversation.getUser_opposite().getId() : "test_user", currentUserId);

        conversation.getMessages().add(testMsg1);
        conversation.getMessages().add(testMsg2);
        conversation.getMessages().add(testMsg3);
        conversation.getMessages().add(testMsg4);

        // 更新适配器
        adapter.setMessages(conversation.getMessages());

        // 保存到数据库
        if (storageHelper != null) {
            storageHelper.saveConversation(conversation);
        }
=======
        conversation = (Conversation) getIntent().getSerializableExtra("conversation_info");
        if (conversation == null) {
            finish();
            return;
        }

        // 当前用户（name, phone, password），id由ID.Generate_id()自动生成
        // 为了让adapter能识别"自己"，我们用setName固定一个标识
        currentUser = new User("我", "", "");

        setOppositeName();

        recycler = findViewById(R.id.recycler);
        edtInput = findViewById(R.id.edtInput);
        btnSend = findViewById(R.id.btnSend);

        // 将 Conversation 中的 List<String> 转换为 List<Message>
        messages = new ArrayList<>();
        List<String> rawMessages = conversation.getMessages();
        if (rawMessages != null) {
            User opposite = conversation.getUser_opposite();
            for (String text : rawMessages) {
                messages.add(new Message(opposite, text, System.currentTimeMillis()));
            }
        }

        // 设置RecyclerView
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new chatAdapter(messages, currentUser.getId());
        recycler.setAdapter(adapter);
        if (!messages.isEmpty()) {
            recycler.scrollToPosition(messages.size() - 1);
        }

        btnSend.setOnClickListener(v -> sendMessage());

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setOppositeName() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(conversation.getUser_opposite().getUsername());
    }

    private void sendMessage() {
        String messageContent = edtInput.getText().toString().trim();
        if (messageContent.isEmpty()) {
            Toast.makeText(this, "请输入消息内容", Toast.LENGTH_SHORT).show();
            return;
        }

        Message newMessage = new Message(currentUser, messageContent, System.currentTimeMillis());
        messages.add(newMessage);
        adapter.notifyItemInserted(messages.size() - 1);
        recycler.scrollToPosition(messages.size() - 1);
        edtInput.setText("");

        simulateReply();
    }

    private void simulateReply() {
        new android.os.Handler().postDelayed(() -> {
            User oppositeUser = conversation.getUser_opposite();
            Message replyMessage = new Message(oppositeUser, "收到", System.currentTimeMillis());
            messages.add(replyMessage);
            adapter.notifyItemInserted(messages.size() - 1);
            recycler.scrollToPosition(messages.size() - 1);
        }, 1000);
>>>>>>> 8e17abf98766200ef08a42fca1e64b4600ad7f30
    }
}

