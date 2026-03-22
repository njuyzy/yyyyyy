package com.example.Japp.Chat;

import android.os.Bundle;
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
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class chatActivity extends AppCompatActivity {

    private static final String CURRENT_USER_ID = "current_user_id";

    private Conversation conversation;
    private RecyclerView recycler;
    private chatAdapter adapter;
    private EditText edtInput;
    private MaterialButton btnSend;
    private List<Message> messages;
    private User currentUser;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

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
    }
}
