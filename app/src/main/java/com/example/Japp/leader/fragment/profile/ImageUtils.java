package com.example.Japp.leader.fragment.profile;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class ImageUtils {

    // 保存图片到本地
    public static boolean saveImageToInternalStorage(Context context, Bitmap bitmap, String fileName) {
        try {
            FileOutputStream outputStream = context.openFileOutput(fileName, Context.MODE_PRIVATE);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream);
            outputStream.close();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    // 从本地加载图片
    public static Bitmap loadImageFromInternalStorage(Context context, String fileName) {
        try {
            File file = new File(context.getFilesDir(), fileName);
            if (file.exists()) {
                return BitmapFactory.decodeFile(file.getAbsolutePath());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // 从Uri加载图片
    public static Bitmap loadBitmapFromUri(Context context, Uri uri) {
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(uri);
            return BitmapFactory.decodeStream(inputStream);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // 保存图片到指定路径
    public static boolean saveBitmapToFile(Context context, Bitmap bitmap, File file) {
        try {
            FileOutputStream outputStream = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream);
            outputStream.close();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    // 检查文件是否存在
    public static boolean isFileExists(Context context, String fileName) {
        File file = new File(context.getFilesDir(), fileName);
        return file.exists();
    }

    // 检查是否有存储权限
    public static boolean hasStoragePermission(Context context) {
        return android.os.Environment.MEDIA_MOUNTED.equals(android.os.Environment.getExternalStorageState());
    }
}