package com.example.Japp.database.dao;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.Japp.database.DatabaseManager;
import com.example.Japp.data.Message;
import com.example.Japp.data.User;

import java.util.ArrayList;
import java.util.List;

public class MessageDao {

    private final DatabaseManager dbManager;

    public MessageDao(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    private String generateMessageId(String senderId, long timestamp) {
        return senderId + "_" + timestamp;
    }

    // 插入消息
    public long insertMessage(Message message, String conversationId) {
        SQLiteDatabase db = dbManager.getWritableDb();
        ContentValues values = new ContentValues();

        values.put(DatabaseManager.COL_MESSAGE_ID, generateMessageId(message.getSender().getId(), message.getTimestamp()));
        values.put(DatabaseManager.COL_CONVERSATION_ID, conversationId);
        values.put(DatabaseManager.COL_SENDER_ID, message.getSender().getId());
        values.put(DatabaseManager.COL_SENDER_NAME, message.getSender().getUsername());
        values.put(DatabaseManager.COL_CONTENT, message.getContent());
        values.put(DatabaseManager.COL_TIMESTAMP, message.getTimestamp());
        values.put(DatabaseManager.COL_IS_READ, 0);
        values.put(DatabaseManager.COL_IS_SENT, 1);
        values.put(DatabaseManager.COL_MESSAGE_TYPE, 0);

        return db.insert(DatabaseManager.TABLE_MESSAGES, null, values);
        // 不要关闭数据库
    }

    // 批量插入消息
    public void insertMessages(List<Message> messages, String conversationId) {
        SQLiteDatabase db = dbManager.getWritableDb();
        db.beginTransaction();

        try {
            for (Message message : messages) {
                ContentValues values = new ContentValues();
                values.put(DatabaseManager.COL_MESSAGE_ID, generateMessageId(message.getSender().getId(), message.getTimestamp()));
                values.put(DatabaseManager.COL_CONVERSATION_ID, conversationId);
                values.put(DatabaseManager.COL_SENDER_ID, message.getSender().getId());
                values.put(DatabaseManager.COL_SENDER_NAME, message.getSender().getUsername());
                values.put(DatabaseManager.COL_CONTENT, message.getContent());
                values.put(DatabaseManager.COL_TIMESTAMP, message.getTimestamp());
                values.put(DatabaseManager.COL_IS_READ, 0);
                values.put(DatabaseManager.COL_IS_SENT, 1);
                values.put(DatabaseManager.COL_MESSAGE_TYPE, 0);

                db.insert(DatabaseManager.TABLE_MESSAGES, null, values);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    // 获取会话的所有消息
    public List<Message> getMessagesByConversation(String conversationId, User currentUser, User oppositeUser) {
        SQLiteDatabase db = dbManager.getReadableDb();
        List<Message> messages = new ArrayList<>();

        Cursor cursor = db.query(
                DatabaseManager.TABLE_MESSAGES,
                null,
                DatabaseManager.COL_CONVERSATION_ID + " = ?",
                new String[]{conversationId},
                null, null,
                DatabaseManager.COL_TIMESTAMP + " ASC"
        );

        while (cursor.moveToNext()) {
            String senderId = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseManager.COL_SENDER_ID));
            String senderName = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseManager.COL_SENDER_NAME));
            String content = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseManager.COL_CONTENT));
            long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseManager.COL_TIMESTAMP));

            User sender;
            if (senderId.equals(currentUser.getId())) {
                sender = currentUser;
            } else {
                sender = oppositeUser;
                sender.setName(senderName);
            }

            Message message = new Message(sender, content, timestamp);
            messages.add(message);
        }

        cursor.close();
        return messages;
    }

    // 分页获取消息
    public List<Message> getMessagesPaged(String conversationId, User currentUser, User oppositeUser, int limit, int offset) {
        SQLiteDatabase db = dbManager.getReadableDb();
        List<Message> messages = new ArrayList<>();

        String query = "SELECT * FROM " + DatabaseManager.TABLE_MESSAGES +
                " WHERE " + DatabaseManager.COL_CONVERSATION_ID + " = ?" +
                " ORDER BY " + DatabaseManager.COL_TIMESTAMP + " DESC" +
                " LIMIT ? OFFSET ?";

        Cursor cursor = db.rawQuery(query, new String[]{conversationId, String.valueOf(limit), String.valueOf(offset)});

        while (cursor.moveToNext()) {
            String senderId = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseManager.COL_SENDER_ID));
            String senderName = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseManager.COL_SENDER_NAME));
            String content = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseManager.COL_CONTENT));
            long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseManager.COL_TIMESTAMP));

            User sender;
            if (senderId.equals(currentUser.getId())) {
                sender = currentUser;
            } else {
                sender = oppositeUser;
                sender.setName(senderName);
            }

            Message message = new Message(sender, content, timestamp);
            messages.add(0, message);
        }

        cursor.close();
        return messages;
    }

    // 标记消息为已读
    public int markMessagesAsRead(String conversationId, String currentUserId) {
        SQLiteDatabase db = dbManager.getWritableDb();
        ContentValues values = new ContentValues();
        values.put(DatabaseManager.COL_IS_READ, 1);

        return db.update(
                DatabaseManager.TABLE_MESSAGES,
                values,
                DatabaseManager.COL_CONVERSATION_ID + " = ? AND " +
                        DatabaseManager.COL_SENDER_ID + " != ? AND " +
                        DatabaseManager.COL_IS_READ + " = 0",
                new String[]{conversationId, currentUserId}
        );
    }

    // 获取未读消息总数
    public int getTotalUnreadCount(String currentUserId) {
        SQLiteDatabase db = dbManager.getReadableDb();

        String query = "SELECT COUNT(*) FROM " + DatabaseManager.TABLE_MESSAGES +
                " WHERE " + DatabaseManager.COL_SENDER_ID + " != ?" +
                " AND " + DatabaseManager.COL_IS_READ + " = 0";

        Cursor cursor = db.rawQuery(query, new String[]{currentUserId});
        int count = 0;
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }

        cursor.close();
        return count;
    }

    // 删除会话的所有消息
    public int deleteMessagesByConversation(String conversationId) {
        SQLiteDatabase db = dbManager.getWritableDb();
        return db.delete(
                DatabaseManager.TABLE_MESSAGES,
                DatabaseManager.COL_CONVERSATION_ID + " = ?",
                new String[]{conversationId}
        );
    }
}