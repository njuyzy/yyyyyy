package com.example.Japp.Chat.utils;

import android.content.Context;

import com.example.Japp.data.Conversation;
import com.example.Japp.data.Message;
import com.example.Japp.data.User;
import com.example.Japp.data.sqlite.DatabaseHelper;

import java.util.ArrayList;
import java.util.List;

public class DatabaseInitializer {
    private DatabaseHelper databaseHelper;
    private final Context context;

    public DatabaseInitializer(Context context) {
        this.context = context.getApplicationContext();
        databaseHelper = new DatabaseHelper(this.context);
    }

    // Initialize with sample conversations
    public void initializeSampleData(String currentUserId) {
        try {
            // Clear existing data
            clearAllData();

            // Create sample users
            User user1 = new User();
            user1.setId(currentUserId);
            user1.setUsername("我");

            User user2 = new User();
            user2.setId("user_001");
            user2.setUsername("张三");

            User user3 = new User();
            user3.setId("user_002");
            user3.setUsername("李四");

            User user4 = new User();
            user4.setId("user_003");
            user4.setUsername("王五");

            // Create conversation 1: with 张三
            Conversation conv1 = new Conversation();
            conv1.setUser_me(user1);
            conv1.setUser_opposite(user2);

            List<Message> messages1 = new ArrayList<>();
            messages1.add(new Message("你好，张三！", Message.TYPE_SENT, currentUserId, "user_001"));
            messages1.add(new Message("你好！最近怎么样？", Message.TYPE_RECEIVED, "user_001", currentUserId));
            messages1.add(new Message("挺好的，你呢？", Message.TYPE_SENT, currentUserId, "user_001"));
            conv1.setMessages(messages1);

            // Create conversation 2: with 李四
            Conversation conv2 = new Conversation();
            conv2.setUser_me(user1);
            conv2.setUser_opposite(user3);

            List<Message> messages2 = new ArrayList<>();
            messages2.add(new Message("明天有空吗？", Message.TYPE_RECEIVED, "user_002", currentUserId));
            messages2.add(new Message("有空，什么事？", Message.TYPE_SENT, currentUserId, "user_002"));
            conv2.setMessages(messages2);

            // Create conversation 3: with 王五
            Conversation conv3 = new Conversation();
            conv3.setUser_me(user1);
            conv3.setUser_opposite(user4);

            List<Message> messages3 = new ArrayList<>();
            messages3.add(new Message("项目进展如何？", Message.TYPE_SENT, currentUserId, "user_003"));
            messages3.add(new Message("进展顺利，预计下周完成", Message.TYPE_RECEIVED, "user_003", currentUserId));
            conv3.setMessages(messages3);

            // Save conversations to database
            databaseHelper.saveConversation(conv1);
            databaseHelper.saveConversation(conv2);
            databaseHelper.saveConversation(conv3);
        } catch (Exception e) {
            android.util.Log.e("DatabaseInitializer", "Error initializing sample data", e);
        }
    }

    // Clear all data
    private void clearAllData() {
        // This would typically involve dropping tables and recreating them
        // For simplicity, we'll just recreate the database helper
        databaseHelper.close();

        // Reinitialize database helper after closing
        databaseHelper = new DatabaseHelper(context);
    }

    // Check if database has data
    public boolean hasData(String currentUserId) {
        List<Conversation> conversations = databaseHelper.getAllConversations(currentUserId);
        return conversations != null && !conversations.isEmpty();
    }
}