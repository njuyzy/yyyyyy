package com.example.Japp.database.dao;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.Japp.database.DatabaseManager;
import com.example.Japp.data.User;

import java.util.ArrayList;
import java.util.List;

public class UserDao {

    private final DatabaseManager dbManager;

    public UserDao(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    // 保存用户信息
    public long insertOrUpdateUser(User user) {
        SQLiteDatabase db = dbManager.getWritableDb();
        ContentValues values = new ContentValues();

        values.put(DatabaseManager.COL_USER_ID_USER, user.getId());
        values.put(DatabaseManager.COL_USERNAME, user.getUsername());
        values.put(DatabaseManager.COL_PHONE, user.getPhone());
        values.put(DatabaseManager.COL_LAST_ACTIVE_TIME, System.currentTimeMillis());

        return db.insertWithOnConflict(DatabaseManager.TABLE_USERS, null, values,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    // 获取用户信息
    public User getUserById(String userId) {
        SQLiteDatabase db = dbManager.getReadableDb();
        User user = null;

        Cursor cursor = db.query(
                DatabaseManager.TABLE_USERS,
                null,
                DatabaseManager.COL_USER_ID_USER + " = ?",
                new String[]{userId},
                null, null, null
        );

        if (cursor.moveToFirst()) {
            user = new User();
            user.setId(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseManager.COL_USER_ID_USER)));
            user.setName(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseManager.COL_USERNAME)));
            user.setPhone(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseManager.COL_PHONE)));
        }

        cursor.close();
        return user;
    }
}