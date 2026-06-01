package com.example.Japp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.Japp.data.User;
import com.example.Japp.network.ApiClient;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.io.File;
import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {

    private static final String PREF_NAME = "user_pref";
    private static final String KEY_AUTO_LOGIN = "autoLogin";
    private static final String KEY_NOTIFY = "notify_enabled";
    private static final String KEY_LOGGED_IN = "is_logged_in";
    private static final String KEY_USER_INFO = "user_inf";

    private SharedPreferences prefs;
    private TextView txtCacheSize;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings);

        View root = findViewById(R.id.main);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        bindProfileHeader();
        bindNavRow(R.id.rowAccountSecurity,
                R.drawable.baseline_account_circle_24,
                R.string.settings_account_security,
                R.string.settings_account_security_summary,
                this::showComingSoon);
        bindNavRow(R.id.rowChangePassword,
                R.drawable.baseline_settings_24,
                R.string.settings_change_password,
                R.string.settings_change_password_summary,
                this::showComingSoon);
        bindSwitchRow(R.id.rowNotification,
                R.drawable.baseline_message_24,
                R.string.settings_notification,
                R.string.settings_notification_summary,
                KEY_NOTIFY,
                true);
        bindSwitchRow(R.id.rowAutoLogin,
                R.drawable.baseline_add_circle_outline_24,
                R.string.settings_auto_login,
                R.string.settings_auto_login_summary,
                KEY_AUTO_LOGIN,
                prefs.getBoolean(KEY_AUTO_LOGIN, false));
        bindNavRow(R.id.rowPrivacy,
                R.drawable.baseline_settings_24,
                R.string.settings_privacy,
                R.string.settings_privacy_summary,
                this::showComingSoon);
        bindClearCacheRow();
        bindNavRow(R.id.rowAbout,
                R.drawable.baseline_route_24,
                R.string.settings_about,
                0,
                this::showAboutDialog);
        bindNavRow(R.id.rowUserAgreement,
                R.drawable.baseline_add_task_24,
                R.string.settings_user_agreement,
                0,
                this::showComingSoon);
        bindNavRow(R.id.rowPrivacyPolicy,
                R.drawable.baseline_group_add_24,
                R.string.settings_privacy_policy,
                0,
                this::showComingSoon);

        TextView txtVersion = findViewById(R.id.txtVersion);
        txtVersion.setText(getString(R.string.settings_version_format, getAppVersionName()));

        MaterialButton btnLogout = findViewById(R.id.btnLogout);
        btnLogout.setOnClickListener(v -> confirmLogout());
    }

    private void bindProfileHeader() {
        TextView txtProfileName = findViewById(R.id.txtProfileName);
        TextView txtProfilePhone = findViewById(R.id.txtProfilePhone);
        if (txtProfileName == null || txtProfilePhone == null) {
            return;
        }

        String userInfo = prefs.getString(KEY_USER_INFO, "");
        if (userInfo == null || userInfo.trim().isEmpty()) {
            txtProfileName.setText(R.string.settings_not_logged_in);
            txtProfilePhone.setText("");
            return;
        }

        try {
            User user = new User();
            txtProfileName.setText(user.getUsername(userInfo));
            txtProfilePhone.setText(maskPhone(user.getPhone(userInfo)));
        } catch (Exception ignored) {
            txtProfileName.setText(R.string.settings_not_logged_in);
            txtProfilePhone.setText("");
        }
    }

    private void bindNavRow(int rowId, int iconRes, int titleRes, int summaryRes, Runnable action) {
        View row = findViewById(rowId);
        if (row == null) {
            return;
        }
        ImageView icon = row.findViewById(R.id.ivIcon);
        TextView title = row.findViewById(R.id.txtTitle);
        TextView summary = row.findViewById(R.id.txtSummary);
        if (icon == null || title == null || summary == null) {
            return;
        }

        icon.setImageResource(iconRes);
        tintIcon(icon, iconRes);
        title.setText(titleRes);
        if (summaryRes != 0) {
            summary.setText(summaryRes);
            summary.setVisibility(View.VISIBLE);
        } else {
            summary.setVisibility(View.GONE);
        }
        row.setOnClickListener(v -> action.run());
    }

    private void bindSwitchRow(int rowId, int iconRes, int titleRes, int summaryRes,
                               String prefKey, boolean defaultValue) {
        View row = findViewById(rowId);
        if (row == null) {
            return;
        }
        ImageView icon = row.findViewById(R.id.ivIcon);
        TextView title = row.findViewById(R.id.txtTitle);
        TextView summary = row.findViewById(R.id.txtSummary);
        SwitchMaterial switchSetting = row.findViewById(R.id.switchSetting);
        if (icon == null || title == null || summary == null || switchSetting == null) {
            return;
        }

        icon.setImageResource(iconRes);
        tintIcon(icon, iconRes);
        title.setText(titleRes);
        summary.setText(summaryRes);
        summary.setVisibility(View.VISIBLE);
        switchSetting.setChecked(prefs.getBoolean(prefKey, defaultValue));
        switchSetting.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.edit().putBoolean(prefKey, isChecked).apply());
    }

    private void bindClearCacheRow() {
        View row = findViewById(R.id.rowClearCache);
        if (row == null) {
            return;
        }
        ImageView icon = row.findViewById(R.id.ivIcon);
        TextView title = row.findViewById(R.id.txtTitle);
        TextView summary = row.findViewById(R.id.txtSummary);
        txtCacheSize = row.findViewById(R.id.txtValue);
        if (icon == null || title == null || summary == null || txtCacheSize == null) {
            return;
        }

        icon.setImageResource(R.drawable.baseline_add_task_24);
        tintIcon(icon, R.drawable.baseline_add_task_24);
        title.setText(R.string.settings_clear_cache);
        summary.setVisibility(View.GONE);
        refreshCacheSize();

        row.setOnClickListener(v -> {
            clearAppCache();
            refreshCacheSize();
            Toast.makeText(this, R.string.settings_cache_cleared, Toast.LENGTH_SHORT).show();
        });
    }

    private void tintIcon(ImageView icon, int iconRes) {
        Drawable drawable = ContextCompat.getDrawable(this, iconRes);
        if (drawable != null) {
            drawable = drawable.mutate();
            drawable.setTint(ContextCompat.getColor(this, R.color.accent));
            icon.setImageDrawable(drawable);
        }
    }

    private void refreshCacheSize() {
        if (txtCacheSize == null) {
            return;
        }
        txtCacheSize.setVisibility(View.VISIBLE);
        txtCacheSize.setText(formatSize(getCacheBytes()));
    }

    private long getCacheBytes() {
        long size = dirSize(getCacheDir());
        File externalCache = getExternalCacheDir();
        if (externalCache != null) {
            size += dirSize(externalCache);
        }
        return size;
    }

    private long dirSize(File dir) {
        if (dir == null || !dir.exists()) {
            return 0L;
        }
        long size = 0L;
        File[] files = dir.listFiles();
        if (files == null) {
            return 0L;
        }
        for (File file : files) {
            size += file.isDirectory() ? dirSize(file) : file.length();
        }
        return size;
    }

    private void clearAppCache() {
        deleteDir(getCacheDir());
        File externalCache = getExternalCacheDir();
        if (externalCache != null) {
            deleteDir(externalCache);
        }
    }

    private void deleteDir(File dir) {
        if (dir == null || !dir.exists()) {
            return;
        }
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDir(file);
                } else {
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                }
            }
        }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0);
        }
        return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.isEmpty()) {
            return "";
        }
        if (phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private String getAppVersionName() {
        try {
            String version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            return version != null ? version : "1.0";
        } catch (Exception ignored) {
            return "1.0";
        }
    }

    private void showComingSoon() {
        Toast.makeText(this, R.string.settings_coming_soon, Toast.LENGTH_SHORT).show();
    }

    private void showAboutDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_about)
                .setMessage(getString(R.string.settings_about_message, getAppVersionName()))
                .setPositiveButton(R.string.settings_confirm, null)
                .show();
    }

    private void confirmLogout() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_logout_confirm_title)
                .setMessage(R.string.settings_logout_confirm_message)
                .setNegativeButton(R.string.settings_cancel, null)
                .setPositiveButton(R.string.settings_confirm, (dialog, which) -> logout())
                .show();
    }

    private void logout() {
        prefs.edit()
                .putBoolean(KEY_LOGGED_IN, false)
                .remove("account_id")
                .apply();
        ApiClient.clearToken();

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
