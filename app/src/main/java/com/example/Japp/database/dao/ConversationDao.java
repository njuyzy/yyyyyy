package com.example.Japp.database.dao;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.Japp.database.DatabaseManager;
import com.example.Japp.data.Conversation;
import com.example.Japp.data.User;

import java.util.ArrayList;
import java.util.List;

public class ConversationDao {

    private final DatabaseManager dbManager;

    public ConversationDao(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    // 生成会话ID
    public String generateConversationId(String userId, String oppositeUserId) {
        if (userId.compareTo(oppositeUserId) < 0) {
            return userId + "_" + oppositeUserId;
        } else {
            return oppositeUserId + "_" + userId;
        }
    }

    // 插入或更新会话
    public long insertOrUpdateConversation(Conversation conversation, String userId) {
        SQLiteDatabase db = dbManager.getWritableDb();
        ContentValues values = new ContentValues();

        String conversationId = generateConversationId(userId, conversation.getUser_opposite().getId());

        values.put(DatabaseManager.COL_CONVERSATION_ID_CONV, conversationId);
        values.put(DatabaseManager.COL_USER_ID, userId);
        values.put(DatabaseManager.COL_OPPOSITE_USER_ID, conversation.getUser_opposite().getId());
        values.put(DatabaseManager.COL_OPPOSITE_USER_NAME, conversation.getUser_opposite().getUsername());

        List<String> messages = conversation.getMessages();
        if (messages != null && !messages.isEmpty()) {
            values.put(DatabaseManager.COL_LAST_MESSAGE, messages.get(messages.size() - 1));
            values.put(DatabaseManager.COL_LAST_MESSAGE_TIME, System.currentTimeMillis());
        }

        values.put(DatabaseManager.COL_UNREAD_COUNT, conversation.getUnRead_num());
        values.put(DatabaseManager.COL_UPDATED_AT, System.currentTimeMillis());

        // 检查会话是否存在
        Cursor cursor = db.query(
                DatabaseManager.TABLE_CONVERSATIONS,
                new String[]{DatabaseManager.COL_ID},
                DatabaseManager.COL_CONVERSATION_ID_CONV + " = ? AND " + DatabaseManager.COL_USER_ID + " = ?",
                new String[]{conversationId, userId},
                null, null, null
        );

        long id;
        if (cursor.moveToFirst()) {
            long existingId = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseManager.COL_ID));
            values.put(DatabaseManager.COL_CREATED_AT, System.currentTimeMillis());
            id = db.update(DatabaseManager.TABLE_CONVERSATIONS, values,
                    DatabaseManager.COL_ID + " = ?",
                    new String[]{String.valueOf(existingId)});
            if (id == 0) id = existingId;
        } else {
            values.put(DatabaseManager.COL_CREATED_AT, System.currentTimeMillis());
            id = db.insert(DatabaseManager.TABLE_CONVERSATIONS, null, values);
        }

        cursor.close();
        return id;
    }

    // 获取用户的所有会话
    public List<Conversation> getConversationsByUser(String userId) {
        SQLiteDatabase db = dbManager.getReadableDb();
        List<Conversation> conversations = new ArrayList<>();

        Cursor cursor = db.query(
                DatabaseManager.TABLE_CONVERSATIONS,
                null,
                DatabaseManager.COL_USER_ID + " = ? AND " + DatabaseManager.COL_IS_DELETED + " = 0",
                new String[]{userId},
                null, null,
                DatabaseManager.COL_IS_TOP + " DESC, " + DatabaseManager.COL_LAST_MESSAGE_TIME + " DESC"
        );

        while (cursor.moveToNext()) {
            Conversation conversation = new Conversation();

            String oppositeUserId = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseManager.COL_OPPOSITE_USER_ID));
            String oppositeUserName = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseManager.COL_OPPOSITE_USER_NAME));

            User oppositeUser = new User();
            oppositeUser.setId(oppositeUserId);
            oppositeUser.setName(oppositeUserName);

            conversation.setUser_opposite(oppositeUser);
            conversation.setUnRead_num(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseManager.COL_UNREAD_COUNT)));

            String lastMessage = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseManager.COL_LAST_MESSAGE));
            if (lastMessage != null && !lastMessage.isEmpty()) {
                conversation.addMessage(lastMessage);
            }

            if (oppositeUserId != null && oppositeUserId.startsWith("group_")) {
                conversation.setGroup(true);
                conversation.setGroupName(oppositeUserName);
            }

            conversations.add(conversation);
        }

        cursor.close();
        return conversations;
    }

    // 获取两个用户之间的会话
    public Conversation getConversationByUsers(String userId, String oppositeUserId) {
        SQLiteDatabase db = dbManager.getReadableDb();
        String conversationId = generateConversationId(userId, oppositeUserId);
        Conversation conversation = null;

        Cursor cursor = db.query(
                DatabaseManager.TABLE_CONVERSATIONS,
                null,
                DatabaseManager.COL_CONVERSATION_ID_CONV + " = ? AND " + DatabaseManager.COL_USER_ID + " = ?",
                new String[]{conversationId, userId},
                null, null, null
        );

        if (cursor.moveToFirst()) {
            conversation = new Conversation();

            String oppositeId = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseManager.COL_OPPOSITE_USER_ID));
            String oppositeName = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseManager.COL_OPPOSITE_USER_NAME));

            User oppositeUser = new User();
            oppositeUser.setId(oppositeId);
            oppositeUser.setName(oppositeName);

            conversation.setUser_opposite(oppositeUser);
            conversation.setUnRead_num(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseManager.COL_UNREAD_COUNT)));
        }

        cursor.close();
        return conversation;
    }

    // 增加未读消息数
    public void incrementUnreadCount(String conversationId, String userId) {
        SQLiteDatabase db = dbManager.getWritableDb();
        String query = "UPDATE " + DatabaseManager.TABLE_CONVERSATIONS +
                " SET " + DatabaseManager.COL_UNREAD_COUNT + " = " + DatabaseManager.COL_UNREAD_COUNT + " + 1" +
                " WHERE " + DatabaseManager.COL_CONVERSATION_ID_CONV + " = ?" +
                " AND " + DatabaseManager.COL_USER_ID + " = ?";

        db.execSQL(query, new Object[]{conversationId, userId});
    }

    // 重置未读消息数
    public void resetUnreadCount(String conversationId) {
        SQLiteDatabase db = dbManager.getWritableDb();
        ContentValues values = new ContentValues();
        values.put(DatabaseManager.COL_UNREAD_COUNT, 0);

        db.update(
                DatabaseManager.TABLE_CONVERSATIONS,
                values,
                DatabaseManager.COL_CONVERSATION_ID_CONV + " = ?",
                new String[]{conversationId}
        );
    }

    // 更新最后一条消息
    public void updateLastMessage(String conversationId, String lastMessage, long lastMessageTime) {
        SQLiteDatabase db = dbManager.getWritableDb();
        ContentValues values = new ContentValues();
        values.put(DatabaseManager.COL_LAST_MESSAGE, lastMessage);
        values.put(DatabaseManager.COL_LAST_MESSAGE_TIME, lastMessageTime);
        values.put(DatabaseManager.COL_UPDATED_AT, System.currentTimeMillis());

        db.update(
                DatabaseManager.TABLE_CONVERSATIONS,
                values,
                DatabaseManager.COL_CONVERSATION_ID_CONV + " = ?",
                new String[]{conversationId}
        );
    }

    // 保存草稿
    public void saveDraft(String conversationId, String draftContent) {
        SQLiteDatabase db = dbManager.getWritableDb();
        ContentValues values = new ContentValues();
        values.put(DatabaseManager.COL_DRAFT_CONTENT, draftContent);

        db.update(
                DatabaseManager.TABLE_CONVERSATIONS,
                values,
                DatabaseManager.COL_CONVERSATION_ID_CONV + " = ?",
                new String[]{conversationId}
        );
    }

    // 获取草稿
    public String getDraft(String conversationId) {
        SQLiteDatabase db = dbManager.getReadableDb();
        String draft = null;

        Cursor cursor = db.query(
                DatabaseManager.TABLE_CONVERSATIONS,
                new String[]{DatabaseManager.COL_DRAFT_CONTENT},
                DatabaseManager.COL_CONVERSATION_ID_CONV + " = ?",
                new String[]{conversationId},
                null, null, null
        );

        if (cursor.moveToFirst()) {
            draft = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseManager.COL_DRAFT_CONTENT));
        }

        cursor.close();
        return draft;
    }

}