package com.example.Japp.Chat.utils;

import android.content.Context;
import android.os.AsyncTask;

import com.example.Japp.data.Conversation;
import com.example.Japp.data.sqlite.DatabaseHelper;

import java.util.ArrayList;
import java.util.List;

public class ChatStorageHelper {

    private DatabaseHelper databaseHelper;

    public ChatStorageHelper(Context context) {
        databaseHelper = new DatabaseHelper(context);
    }

    /**
     * 保存会话列表
     */
    public void saveConversations(List<Conversation> conversations) {
        // Run database operations on a background thread
        new android.os.AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                for (Conversation conversation : conversations) {
                    saveConversation(conversation);
                }
                return null;
            }
        }.execute();
    }

    /**
     * 加载会话列表
     */
    public void loadConversations(final LoadCallback callback) {
        if (databaseHelper == null) {
            if (callback != null) {
                callback.onLoadComplete(new ArrayList<>());
            }
            return;
        }

        // Run database operations on a background thread
        new AsyncTask<Void, Void, List<Conversation>>() {
            @Override
            protected List<Conversation> doInBackground(Void... voids) {
                return databaseHelper.getAllConversations(getCurrentUserId());
            }

            @Override
            protected void onPostExecute(List<Conversation> conversations) {
                if (callback != null) {
                    callback.onLoadComplete(conversations);
                }
            }
        }.execute();
    }

    public interface LoadCallback {
        void onLoadComplete(List<Conversation> conversations);
    }

    /**
     * 保存单个会话
     */
    public void saveConversation(Conversation conversation) {
        if (conversation == null || databaseHelper == null) return;

        // Run database operations on a background thread
        new android.os.AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                databaseHelper.saveConversation(conversation);
                return null;
            }
        }.execute();
    }

    /**
     * 根据对方用户ID获取会话
     */
    public void getConversationByOppositeId(final String oppositeId, final String currentUserId, final GetConversationCallback callback) {
        if (oppositeId == null || currentUserId == null || databaseHelper == null) {
            callback.onGetComplete(null);
            return;
        }

        // Run database operations on a background thread
        new android.os.AsyncTask<Void, Void, Conversation>() {
            @Override
            protected Conversation doInBackground(Void... voids) {
                return databaseHelper.getConversation(currentUserId, oppositeId);
            }

            @Override
            protected void onPostExecute(Conversation conversation) {
                callback.onGetComplete(conversation);
            }
        }.execute();
    }

    public interface GetConversationCallback {
        void onGetComplete(Conversation conversation);
    }

    /**
     * 获取当前用户ID
     */
    private String getCurrentUserId() {
        // This should be implemented to get the actual current user ID
        // For now, returning a default value
        return "current_user";
    }

    /**
     * 删除会话
     */
    public void deleteConversation(final String userId, final String oppositeId) {
        if (databaseHelper == null) return;

        // Run database operations on a background thread
        new android.os.AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                databaseHelper.deleteConversation(userId, oppositeId);
                return null;
            }
        }.execute();
    }

    /**
     * 获取未读消息数量
     */
    public void getUnreadCount(final String userId, final String oppositeId, final GetUnreadCountCallback callback) {
        if (databaseHelper == null) {
            callback.onGetComplete(0);
            return;
        }

        // Run database operations on a background thread
        new android.os.AsyncTask<Void, Void, Integer>() {
            @Override
            protected Integer doInBackground(Void... voids) {
                return databaseHelper.getUnreadCount(userId, oppositeId);
            }

            @Override
            protected void onPostExecute(Integer count) {
                callback.onGetComplete(count);
            }
        }.execute();
    }

    public interface GetUnreadCountCallback {
        void onGetComplete(int count);
    }

    /**
     * 更新未读消息数量
     */
    public void updateUnreadCount(final String userId, final String oppositeId, final int count) {
        if (databaseHelper == null) return;

        // Run database operations on a background thread
        new android.os.AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                databaseHelper.updateUnreadCount(userId, oppositeId, count);
                return null;
            }
        }.execute();
    }
}
