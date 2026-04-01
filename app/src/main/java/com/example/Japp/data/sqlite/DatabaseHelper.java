package com.example.Japp.data.sqlite;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.example.Japp.data.Conversation;
import com.example.Japp.data.Message;
import com.example.Japp.data.User;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "chat_database";
    private static final int DATABASE_VERSION = 1;

    // Messages table
    private static final String TABLE_MESSAGES = "messages";
    private static final String KEY_ID = "id";
    private static final String KEY_CONTENT = "content";
    private static final String KEY_TYPE = "type";
    private static final String KEY_SENDER_ID = "sender_id";
    private static final String KEY_RECEIVER_ID = "receiver_id";
    private static final String KEY_TIMESTAMP = "timestamp";
    private static final String KEY_CONVERSATION_ID = "conversation_id";

    // Conversations table
    private static final String TABLE_CONVERSATIONS = "conversations";
    private static final String KEY_USER_ME_ID = "user_me_id";
    private static final String KEY_USER_OPPOSITE_ID = "user_opposite_id";
    private static final String KEY_UNREAD_COUNT = "unread_count";
    private static final String KEY_LATEST_MESSAGE = "latest_message";
    private static final String KEY_LATEST_MESSAGE_TIME = "latest_message_time";

    // Create tables SQL
    private static final String CREATE_TABLE_MESSAGES = "CREATE TABLE " + TABLE_MESSAGES + "("
            + KEY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
            + KEY_CONTENT + " TEXT,"
            + KEY_TYPE + " TEXT,"
            + KEY_SENDER_ID + " TEXT,"
            + KEY_RECEIVER_ID + " TEXT,"
            + KEY_TIMESTAMP + " LONG,"
            + KEY_CONVERSATION_ID + " TEXT"
            + ")";

    private static final String CREATE_TABLE_CONVERSATIONS = "CREATE TABLE " + TABLE_CONVERSATIONS + "("
            + KEY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
            + KEY_USER_ME_ID + " TEXT,"
            + KEY_USER_OPPOSITE_ID + " TEXT,"
            + KEY_UNREAD_COUNT + " INTEGER DEFAULT 0,"
            + KEY_LATEST_MESSAGE + " TEXT,"
            + KEY_LATEST_MESSAGE_TIME + " LONG,"
            + "UNIQUE(" + KEY_USER_ME_ID + "," + KEY_USER_OPPOSITE_ID + ")"
            + ")";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_MESSAGES);
        db.execSQL(CREATE_TABLE_CONVERSATIONS);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_MESSAGES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_CONVERSATIONS);
        onCreate(db);
    }

    // Save a conversation with all its messages
    public void saveConversation(Conversation conversation) {
        SQLiteDatabase db = this.getWritableDatabase();

        // Generate conversation ID
        String conversationId = getConversationId(conversation.getUser_me().getId(),
                conversation.getUser_opposite().getId());

        // Save or update conversation
        ContentValues convValues = new ContentValues();
        convValues.put(KEY_USER_ME_ID, conversation.getUser_me().getId());
        convValues.put(KEY_USER_OPPOSITE_ID, conversation.getUser_opposite().getId());

        if (conversation.getMessages() != null && !conversation.getMessages().isEmpty()) {
            Message latestMsg = conversation.getMessages().get(conversation.getMessages().size() - 1);
            convValues.put(KEY_LATEST_MESSAGE, latestMsg.getContent());
            convValues.put(KEY_LATEST_MESSAGE_TIME, latestMsg.getTimestamp());
        }

        // Check if conversation exists
        Cursor cursor = db.rawQuery("SELECT " + KEY_ID + " FROM " + TABLE_CONVERSATIONS
                + " WHERE " + KEY_USER_ME_ID + " = ? AND " + KEY_USER_OPPOSITE_ID + " = ?",
                new String[]{conversation.getUser_me().getId(), conversation.getUser_opposite().getId()});

        if (cursor.moveToFirst()) {
            // Update existing conversation
            db.update(TABLE_CONVERSATIONS, convValues, KEY_ID + " = ?",
                    new String[]{String.valueOf(cursor.getInt(0))});
        } else {
            // Insert new conversation
            db.insert(TABLE_CONVERSATIONS, null, convValues);
        }
        cursor.close();

        // Save messages
        if (conversation.getMessages() != null) {
            for (Message message : conversation.getMessages()) {
                saveMessage(message, conversationId);
            }
        }

        db.close();
    }

    // Save a single message
    public void saveMessage(Message message, String conversationId) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(KEY_CONTENT, message.getContent());
        values.put(KEY_TYPE, message.getType());
        values.put(KEY_SENDER_ID, message.getSenderId());
        values.put(KEY_RECEIVER_ID, message.getReceiverId());
        values.put(KEY_TIMESTAMP, message.getTimestamp());
        values.put(KEY_CONVERSATION_ID, conversationId);

        db.insert(TABLE_MESSAGES, null, values);
        db.close();
    }

    // Get conversation by user IDs
    public Conversation getConversation(String userId, String oppositeUserId) {
        SQLiteDatabase db = this.getReadableDatabase();

        String conversationId = getConversationId(userId, oppositeUserId);

        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_CONVERSATIONS
                + " WHERE " + KEY_USER_ME_ID + " = ? AND " + KEY_USER_OPPOSITE_ID + " = ?",
                new String[]{userId, oppositeUserId});

        Conversation conversation = null;

        if (cursor.moveToFirst()) {
            conversation = new Conversation();
            User me = new User();
            me.setId(userId);
            conversation.setUser_me(me);

            User opposite = new User();
            opposite.setId(oppositeUserId);
            conversation.setUser_opposite(opposite);

            // Load messages for this conversation
            List<Message> messages = getMessagesByConversationId(conversationId);
            conversation.setMessages(messages);
        }

        cursor.close();
        db.close();
        return conversation;
    }

    // Get all conversations
    public List<Conversation> getAllConversations(String userId) {
        List<Conversation> conversations = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_CONVERSATIONS
                + " WHERE " + KEY_USER_ME_ID + " = ? OR " + KEY_USER_OPPOSITE_ID + " = ?",
                new String[]{userId, userId});

        if (cursor.moveToFirst()) {
            do {
                @SuppressLint("Range") String meId = cursor.getString(cursor.getColumnIndex(KEY_USER_ME_ID));
                @SuppressLint("Range") String oppositeId = cursor.getString(cursor.getColumnIndex(KEY_USER_OPPOSITE_ID));

                // Determine which is the current user
                String currentUserId = meId.equals(userId) ? meId : oppositeId;
                String otherUserId = meId.equals(userId) ? oppositeId : meId;

                Conversation conversation = new Conversation();
                User me = new User();
                me.setId(currentUserId);
                conversation.setUser_me(me);

                User opposite = new User();
                opposite.setId(otherUserId);
                conversation.setUser_opposite(opposite);

                // Load messages
                String conversationId = getConversationId(currentUserId, otherUserId);
                List<Message> messages = getMessagesByConversationId(conversationId);
                conversation.setMessages(messages);

                conversations.add(conversation);
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
        return conversations;
    }

    // Get messages for a conversation
    @SuppressLint("Range")
    private List<Message> getMessagesByConversationId(String conversationId) {
        List<Message> messages = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_MESSAGES
                + " WHERE " + KEY_CONVERSATION_ID + " = ? ORDER BY " + KEY_TIMESTAMP,
                new String[]{conversationId});

        if (cursor.moveToFirst()) {
            do {
                Message message = new Message();
                message.setContent(cursor.getString(cursor.getColumnIndex(KEY_CONTENT)));
                message.setType(cursor.getString(cursor.getColumnIndex(KEY_TYPE)));
                message.setSenderId(cursor.getString(cursor.getColumnIndex(KEY_SENDER_ID)));
                message.setReceiverId(cursor.getString(cursor.getColumnIndex(KEY_RECEIVER_ID)));
                message.setTimestamp(cursor.getLong(cursor.getColumnIndex(KEY_TIMESTAMP)));

                messages.add(message);
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
        return messages;
    }

    // Delete conversation
    public void deleteConversation(String userId, String oppositeUserId) {
        SQLiteDatabase db = this.getWritableDatabase();

        String conversationId = getConversationId(userId, oppositeUserId);

        // Delete messages
        db.delete(TABLE_MESSAGES, KEY_CONVERSATION_ID + " = ?", new String[]{conversationId});

        // Delete conversation
        db.delete(TABLE_CONVERSATIONS, KEY_USER_ME_ID + " = ? AND " + KEY_USER_OPPOSITE_ID + " = ?",
                new String[]{userId, oppositeUserId});

        db.close();
    }

    // Generate conversation ID
    private String getConversationId(String userId, String oppositeUserId) {
        if (userId.compareTo(oppositeUserId) < 0) {
            return userId + "_" + oppositeUserId;
        } else {
            return oppositeUserId + "_" + userId;
        }
    }

    // Get unread count for conversation
    public int getUnreadCount(String userId, String oppositeUserId) {
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.rawQuery("SELECT " + KEY_UNREAD_COUNT + " FROM " + TABLE_CONVERSATIONS
                + " WHERE " + KEY_USER_ME_ID + " = ? AND " + KEY_USER_OPPOSITE_ID + " = ?",
                new String[]{userId, oppositeUserId});

        int count = 0;
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }

        cursor.close();
        db.close();
        return count;
    }

    // Update unread count
    public void updateUnreadCount(String userId, String oppositeUserId, int count) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(KEY_UNREAD_COUNT, count);

        db.update(TABLE_CONVERSATIONS, values,
                KEY_USER_ME_ID + " = ? AND " + KEY_USER_OPPOSITE_ID + " = ?",
                new String[]{userId, oppositeUserId});

        db.close();
    }
}