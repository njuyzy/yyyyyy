package com.example.Japp.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseManager extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "japp_chat.db";
    private static final int DATABASE_VERSION = 1;

    private static DatabaseManager instance;
    private SQLiteDatabase readableDatabase;
    private SQLiteDatabase writableDatabase;

    // 表名
    public static final String TABLE_MESSAGES = "messages";
    public static final String TABLE_CONVERSATIONS = "conversations";
    public static final String TABLE_USERS = "users";

    // 消息表字段
    public static final String COL_ID = "_id";
    public static final String COL_MESSAGE_ID = "message_id";
    public static final String COL_CONVERSATION_ID = "conversation_id";
    public static final String COL_SENDER_ID = "sender_id";
    public static final String COL_SENDER_NAME = "sender_name";
    public static final String COL_CONTENT = "content";
    public static final String COL_TIMESTAMP = "timestamp";
    public static final String COL_IS_READ = "is_read";
    public static final String COL_IS_SENT = "is_sent";
    public static final String COL_MESSAGE_TYPE = "message_type";
    public static final String COL_ATTACHMENT_URL = "attachment_url";

    // 会话表字段
    public static final String COL_CONVERSATION_ID_CONV = "conversation_id";
    public static final String COL_USER_ID = "user_id";
    public static final String COL_OPPOSITE_USER_ID = "opposite_user_id";
    public static final String COL_OPPOSITE_USER_NAME = "opposite_user_name";
    public static final String COL_OPPOSITE_AVATAR = "opposite_avatar";
    public static final String COL_LAST_MESSAGE = "last_message";
    public static final String COL_LAST_MESSAGE_TIME = "last_message_time";
    public static final String COL_UNREAD_COUNT = "unread_count";
    public static final String COL_IS_TOP = "is_top";
    public static final String COL_DRAFT_CONTENT = "draft_content";
    public static final String COL_IS_DELETED = "is_deleted";
    public static final String COL_CREATED_AT = "created_at";
    public static final String COL_UPDATED_AT = "updated_at";

    // 用户表字段
    public static final String COL_USER_ID_USER = "user_id";
    public static final String COL_USERNAME = "username";
    public static final String COL_PHONE = "phone";
    public static final String COL_AVATAR_URL = "avatar_url";
    public static final String COL_ROLE = "role";
    public static final String COL_LAST_ACTIVE_TIME = "last_active_time";
    public static final String COL_IS_ONLINE = "is_online";

    // 创建消息表的SQL语句
    private static final String CREATE_TABLE_MESSAGES =
            "CREATE TABLE " + TABLE_MESSAGES + " (" +
                    COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_MESSAGE_ID + " TEXT UNIQUE, " +
                    COL_CONVERSATION_ID + " TEXT, " +
                    COL_SENDER_ID + " TEXT, " +
                    COL_SENDER_NAME + " TEXT, " +
                    COL_CONTENT + " TEXT, " +
                    COL_TIMESTAMP + " INTEGER, " +
                    COL_IS_READ + " INTEGER DEFAULT 0, " +
                    COL_IS_SENT + " INTEGER DEFAULT 1, " +
                    COL_MESSAGE_TYPE + " INTEGER DEFAULT 0, " +
                    COL_ATTACHMENT_URL + " TEXT" +
                    ")";

    // 创建会话表的SQL语句
    private static final String CREATE_TABLE_CONVERSATIONS =
            "CREATE TABLE " + TABLE_CONVERSATIONS + " (" +
                    COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_CONVERSATION_ID_CONV + " TEXT UNIQUE, " +
                    COL_USER_ID + " TEXT, " +
                    COL_OPPOSITE_USER_ID + " TEXT, " +
                    COL_OPPOSITE_USER_NAME + " TEXT, " +
                    COL_OPPOSITE_AVATAR + " TEXT, " +
                    COL_LAST_MESSAGE + " TEXT, " +
                    COL_LAST_MESSAGE_TIME + " INTEGER, " +
                    COL_UNREAD_COUNT + " INTEGER DEFAULT 0, " +
                    COL_IS_TOP + " INTEGER DEFAULT 0, " +
                    COL_DRAFT_CONTENT + " TEXT, " +
                    COL_IS_DELETED + " INTEGER DEFAULT 0, " +
                    COL_CREATED_AT + " INTEGER, " +
                    COL_UPDATED_AT + " INTEGER" +
                    ")";

    // 创建用户表的SQL语句
    private static final String CREATE_TABLE_USERS =
            "CREATE TABLE " + TABLE_USERS + " (" +
                    COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_USER_ID_USER + " TEXT UNIQUE, " +
                    COL_USERNAME + " TEXT, " +
                    COL_PHONE + " TEXT UNIQUE, " +
                    COL_AVATAR_URL + " TEXT, " +
                    COL_ROLE + " TEXT, " +
                    COL_LAST_ACTIVE_TIME + " INTEGER, " +
                    COL_IS_ONLINE + " INTEGER DEFAULT 0" +
                    ")";

    // 创建索引
    private static final String CREATE_INDEX_MESSAGES_CONV =
            "CREATE INDEX idx_messages_conversation ON " + TABLE_MESSAGES + "(" + COL_CONVERSATION_ID + ")";
    private static final String CREATE_INDEX_MESSAGES_TIMESTAMP =
            "CREATE INDEX idx_messages_timestamp ON " + TABLE_MESSAGES + "(" + COL_TIMESTAMP + ")";
    private static final String CREATE_INDEX_CONVERSATIONS_USER =
            "CREATE INDEX idx_conversations_user ON " + TABLE_CONVERSATIONS + "(" + COL_USER_ID + ")";
    private static final String CREATE_INDEX_CONVERSATIONS_OPPOSITE =
            "CREATE INDEX idx_conversations_opposite ON " + TABLE_CONVERSATIONS + "(" + COL_OPPOSITE_USER_ID + ")";

    private DatabaseManager(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    public static synchronized DatabaseManager getInstance(Context context) {
        if (instance == null) {
            instance = new DatabaseManager(context.getApplicationContext());
        }
        return instance;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_MESSAGES);
        db.execSQL(CREATE_TABLE_CONVERSATIONS);
        db.execSQL(CREATE_TABLE_USERS);
        db.execSQL(CREATE_INDEX_MESSAGES_CONV);
        db.execSQL(CREATE_INDEX_MESSAGES_TIMESTAMP);
        db.execSQL(CREATE_INDEX_CONVERSATIONS_USER);
        db.execSQL(CREATE_INDEX_CONVERSATIONS_OPPOSITE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_MESSAGES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_CONVERSATIONS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USERS);
        onCreate(db);
    }

    public synchronized SQLiteDatabase getReadableDb() {
        if (readableDatabase == null || !readableDatabase.isOpen()) {
            readableDatabase = super.getReadableDatabase();
        }
        return readableDatabase;
    }

    // 获取可写数据库（不自动关闭）
    public synchronized SQLiteDatabase getWritableDb() {
        if (writableDatabase == null || !writableDatabase.isOpen()) {
            writableDatabase = super.getWritableDatabase();
        }
        return writableDatabase;
    }

    // 关闭数据库连接（在应用退出时调用）
    public synchronized void closeDatabase() {
        if (readableDatabase != null && readableDatabase.isOpen()) {
            readableDatabase.close();
        }
        if (writableDatabase != null && writableDatabase.isOpen()) {
            writableDatabase.close();
        }
    }
}