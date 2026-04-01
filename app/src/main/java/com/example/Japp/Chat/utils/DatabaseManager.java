package com.example.Japp.Chat.utils;

import android.content.Context;

import com.example.Japp.data.Conversation;
import com.example.Japp.data.Message;
import com.example.Japp.data.User;
import com.example.Japp.data.sqlite.DatabaseHelper;

import java.util.List;

public class DatabaseManager {
    private static DatabaseManager instance;
    private DatabaseHelper databaseHelper;
    private String currentUserId;

    private DatabaseManager(Context context) {
        databaseHelper = new DatabaseHelper(context);
        // In a real app, this would be retrieved from your authentication system
        currentUserId = "current_user"; // Default value
    }

    public static synchronized DatabaseManager getInstance(Context context) {
        if (instance == null) {
            instance = new DatabaseManager(context);
        }
        return instance;
    }

    public void setCurrentUserId(String userId) {
        this.currentUserId = userId;
    }

    // Save or update a conversation
    public void saveConversation(Conversation conversation) {
        if (conversation != null && databaseHelper != null) {
            databaseHelper.saveConversation(conversation);
        }
    }

    // Get a conversation by participant IDs
    public Conversation getConversation(String oppositeUserId) {
        if (databaseHelper != null && currentUserId != null) {
            return databaseHelper.getConversation(currentUserId, oppositeUserId);
        }
        return null;
    }

    // Get all conversations for the current user
    public List<Conversation> getAllConversations() {
        if (databaseHelper != null && currentUserId != null) {
            return databaseHelper.getAllConversations(currentUserId);
        }
        return null;
    }

    // Delete a conversation
    public void deleteConversation(String oppositeUserId) {
        if (databaseHelper != null && currentUserId != null) {
            databaseHelper.deleteConversation(currentUserId, oppositeUserId);
        }
    }

    // Save a single message to a conversation
    public void saveMessage(Message message, String oppositeUserId) {
        if (databaseHelper != null && currentUserId != null && message != null) {
            String conversationId = generateConversationId(currentUserId, oppositeUserId);
            databaseHelper.saveMessage(message, conversationId);
        }
    }

    // Get unread count for a conversation
    public int getUnreadCount(String oppositeUserId) {
        if (databaseHelper != null && currentUserId != null) {
            return databaseHelper.getUnreadCount(currentUserId, oppositeUserId);
        }
        return 0;
    }

    // Update unread count
    public void updateUnreadCount(String oppositeUserId, int count) {
        if (databaseHelper != null && currentUserId != null) {
            databaseHelper.updateUnreadCount(currentUserId, oppositeUserId, count);
        }
    }

    // Generate conversation ID
    private String generateConversationId(String userId1, String userId2) {
        if (userId1.compareTo(userId2) < 0) {
            return userId1 + "_" + userId2;
        } else {
            return userId2 + "_" + userId1;
        }
    }

    // Clear all data (for testing or reset)
    public void clearAllData() {
        if (databaseHelper != null) {
            // This would need to be implemented in DatabaseHelper
            // For now, we'll just recreate the database
            databaseHelper.close();
        }
    }
}