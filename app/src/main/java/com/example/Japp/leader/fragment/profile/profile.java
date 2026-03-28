package com.example.Japp.leader.fragment.profile;

import static android.content.Context.MODE_PRIVATE;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.example.Japp.R;
import com.example.Japp.SettingsActivity;
import com.example.Japp.user.UserMainActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;

public class profile extends Fragment {

    private Button switchMode, check_review;
    private ImageView ivAvatar;
    private ImageButton Settings;
    private static final int SELECT_FILE = 1002;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.leader_fragment_profile, container, false);

        ivAvatar = view.findViewById(R.id.ivAvatar);
        switchMode = view.findViewById(R.id.btnSwitchRole);
        check_review = view.findViewById(R.id.btnViewReviews);
        Settings = view.findViewById(R.id.btnSettings);

        // 加载本地存储的图片
        loadImageFromLocal();

        // 主要修改：为头像图片添加点击事件
        ivAvatar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showImagePickerDialog();
            }
        });

        switchMode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchMode();
            }
        });

        check_review.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                review targetFragment = new review();
                FragmentTransaction transaction = getParentFragmentManager().beginTransaction();
                transaction.replace(R.id.container, targetFragment);
                transaction.addToBackStack(null);
                transaction.commit();
            }
        });

        Settings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(requireContext(), SettingsActivity.class));
            }
        });

        return view;
    }

    private void switchMode() {
        SharedPreferences sharedPreferences = requireContext().getSharedPreferences("user_pref", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        editor.putString("Mode","USER").apply();

        Toast.makeText(requireContext(),"已切换到用户模式",Toast.LENGTH_SHORT).show();

        startActivity(new Intent(requireContext(), UserMainActivity.class));
        requireActivity().finish();
    }

    // 显示图片选择对话框
    private void showImagePickerDialog() {
        String[] items = {"加载图片", "取消"};
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(requireContext());
        builder.setTitle("选择图片来源");
        builder.setItems(items, (dialog, which) -> {
            switch (which) {
                case 0:
                    // 下载图片
                    requestStoragePermission();
                    break;
                case 1:
                    // 取消
                    dialog.dismiss();
                    break;
            }
        });
        builder.show();
    }


    // 请求存储权限
    private void requestStoragePermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, SELECT_FILE);
        } else {
            openGallery();
        }
    }


    // 打开图片下载
    private void openGallery() {
        try {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            intent.setType("image/*");
            startActivityForResult(intent, SELECT_FILE);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "打开图库失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // 处理权限结果
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == SELECT_FILE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openGallery();
            } else {
                Toast.makeText(requireContext(), "没有授予存储权限", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // 处理回执结果
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == requireActivity().RESULT_OK) {
            if (requestCode == SELECT_FILE) {
                if (data != null) {
                    Uri selectedImageUri = data.getData();
                    if (selectedImageUri != null) {
                        try {
                            Bitmap bitmap = ImageUtils.loadBitmapFromUri(requireContext(), selectedImageUri);
                            if (bitmap != null) {
                                ivAvatar.setImageBitmap(bitmap);
                                saveImageToLocal(bitmap);
                                Toast.makeText(requireContext(), "图片更换成功", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(requireContext(), "无法加载图片", Toast.LENGTH_SHORT).show();
                            }
                        } catch (Exception e) {
                            Toast.makeText(requireContext(), "加载图片失败", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            }
        }
    }


    // 保存图片到本地
    private void saveImageToLocal(Bitmap bitmap) {
        try {
            String fileName = "user_avatar.jpg";
            if (ImageUtils.saveImageToInternalStorage(requireContext(), bitmap, fileName)) {
                Toast.makeText(requireContext(), "图片已保存", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(requireContext(), "保存图片失败", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(requireContext(), "保存图片失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // 加载本地图片
    private void loadImageFromLocal() {
        try {
            String fileName = "user_avatar.jpg";
            if (ImageUtils.isFileExists(requireContext(), fileName)) {
                Bitmap bitmap = ImageUtils.loadImageFromInternalStorage(requireContext(), fileName);
                if (bitmap != null) {
                    ivAvatar.setImageBitmap(bitmap);
                } else {
                    ivAvatar.setImageResource(R.drawable.ic_default_avatar);
                }
            } else {
                ivAvatar.setImageResource(R.drawable.ic_default_avatar);
            }
        } catch (Exception e) {
            ivAvatar.setImageResource(R.drawable.ic_default_avatar);
        }
    }

}