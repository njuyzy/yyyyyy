package com.example.Japp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.Japp.data.User;
import com.example.Japp.leader.fragment.profile.ImageUtils;
import com.example.Japp.network.ApiClient;
import com.example.Japp.network.api.UserService;
import com.example.Japp.network.models.Account;
import com.example.Japp.network.models.AccountTagPref;
import com.example.Japp.network.models.Result;
import com.example.Japp.network.models.requests.UpdateAccountRoleRequest;
import com.example.Japp.network.models.requests.UpdatePasswordRequest;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SettingsActivity extends AppCompatActivity {

    private static final String PREF_NAME = "user_pref";
    private static final String KEY_AUTO_LOGIN = "autoLogin";
    private static final String KEY_NOTIFY = "notify_enabled";
    private static final String KEY_LOGGED_IN = "is_logged_in";
    private static final String KEY_USER_INFO = "user_inf";

    private SharedPreferences prefs;
    private TextView txtCacheSize;
    private UserService service;
    private int accountId = -1;
    private String accountRole = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings);

        View root = findViewById(R.id.main);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
            return insets;
        });

        prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        service = ApiClient.getClient().create(UserService.class);
        accountId = prefs.getInt("account_id", -1);
        accountRole = prefs.getString(RoleSelectionActivity.EXTRA_ROLE, "");

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        bindProfileHeader();
        bindNavRow(R.id.rowAccountSecurity,
                R.drawable.baseline_account_circle_24,
                R.string.settings_account_security,
                R.string.settings_account_security_summary,
                () -> startActivity(new Intent(this, PersonalInfoActivity.class)));
        bindNavRow(R.id.rowInterestPreferences,
                R.drawable.baseline_route_24,
                R.string.settings_interest_preferences,
                R.string.settings_interest_preferences_summary,
                this::loadInterestPreferences);
        bindNavRow(R.id.rowAccountRole,
                R.drawable.baseline_account_circle_24,
                R.string.settings_account_role,
                R.string.settings_account_role_summary,
                this::showRoleDialog);
        bindNavRow(R.id.rowChangePassword,
                R.drawable.baseline_settings_24,
                R.string.settings_change_password,
                R.string.settings_change_password_summary,
                this::showChangePasswordDialog);
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

        View content = findViewById(android.R.id.content);
        content.setAlpha(0f);
        content.setTranslationY(12f * getResources().getDisplayMetrics().density);
        content.animate().alpha(1f).translationY(0f).setDuration(220L).start();
        loadRemoteAccount();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (prefs != null) {
            bindProfileHeader();
        }
    }

    private void bindProfileHeader() {
        TextView txtProfileName = findViewById(R.id.txtProfileName);
        TextView txtProfilePhone = findViewById(R.id.txtProfilePhone);
        ImageView avatar = findViewById(R.id.ivProfileAvatar);
        if (txtProfileName == null || txtProfilePhone == null) {
            return;
        }
        if (avatar != null && ImageUtils.isFileExists(this, "user_avatar.jpg")) {
            Bitmap bitmap = ImageUtils.loadImageFromInternalStorage(this, "user_avatar.jpg");
            if (bitmap != null) {
                avatar.setImageBitmap(bitmap);
            }
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

    private void loadRemoteAccount() {
        if (accountId <= 0) {
            return;
        }
        service.getAccount(accountId).enqueue(new Callback<Result<Account>>() {
            @Override
            public void onResponse(@NonNull Call<Result<Account>> call,
                                   @NonNull Response<Result<Account>> response) {
                Result<Account> result = response.body();
                if (!response.isSuccessful() || result == null || result.getCode() != 1
                        || result.getData() == null) {
                    return;
                }
                Account account = result.getData();
                accountRole = account.getRole();
                prefs.edit()
                        .putString(RoleSelectionActivity.EXTRA_ROLE, accountRole)
                        .putString("region_code", account.getRegionCode())
                        .apply();
                TextView name = findViewById(R.id.txtProfileName);
                TextView phone = findViewById(R.id.txtProfilePhone);
                name.setText(account.getUsername());
                phone.setText(maskPhone(account.getPhone()));
                updateRowSummary(R.id.rowAccountRole, roleLabel(accountRole));
            }

            @Override
            public void onFailure(@NonNull Call<Result<Account>> call, @NonNull Throwable t) {
            }
        });
    }

    private void updateRowSummary(int rowId, String value) {
        View row = findViewById(rowId);
        if (row == null) {
            return;
        }
        TextView summary = row.findViewById(R.id.txtSummary);
        if (summary != null && !TextUtils.isEmpty(value)) {
            summary.setText(value);
            summary.setVisibility(View.VISIBLE);
        }
    }

    private void showChangePasswordDialog() {
        if (!ensureLoggedIn()) {
            return;
        }
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_change_password, null);
        TextInputLayout oldLayout = content.findViewById(R.id.tilOldPassword);
        TextInputLayout newLayout = content.findViewById(R.id.tilNewPassword);
        TextInputLayout confirmLayout = content.findViewById(R.id.tilConfirmPassword);
        TextInputEditText oldInput = content.findViewById(R.id.etOldPassword);
        TextInputEditText newInput = content.findViewById(R.id.etNewPassword);
        TextInputEditText confirmInput = content.findViewById(R.id.etConfirmPassword);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_change_password)
                .setView(content)
                .setNegativeButton(R.string.settings_cancel, null)
                .setPositiveButton(R.string.settings_confirm, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    oldLayout.setError(null);
                    newLayout.setError(null);
                    confirmLayout.setError(null);
                    String oldPassword = textOf(oldInput);
                    String newPassword = textOf(newInput);
                    String confirmPassword = textOf(confirmInput);
                    if (TextUtils.isEmpty(oldPassword)) {
                        oldLayout.setError(getString(R.string.settings_old_password));
                        return;
                    }
                    if (newPassword.length() < 6) {
                        newLayout.setError(getString(R.string.settings_password_length_error));
                        return;
                    }
                    if (!newPassword.equals(confirmPassword)) {
                        confirmLayout.setError(getString(R.string.settings_password_mismatch));
                        return;
                    }
                    Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                    positive.setEnabled(false);
                    service.updatePassword(accountId,
                                    new UpdatePasswordRequest(oldPassword, newPassword, confirmPassword))
                            .enqueue(new Callback<Result>() {
                                @Override
                                public void onResponse(@NonNull Call<Result> call,
                                                       @NonNull Response<Result> response) {
                                    positive.setEnabled(true);
                                    Result result = response.body();
                                    if (response.isSuccessful() && result != null && result.getCode() == 1) {
                                        dialog.dismiss();
                                        Toast.makeText(SettingsActivity.this,
                                                R.string.settings_password_changed,
                                                Toast.LENGTH_SHORT).show();
                                    } else {
                                        newLayout.setError(readServerMessage(response));
                                    }
                                }

                                @Override
                                public void onFailure(@NonNull Call<Result> call, @NonNull Throwable t) {
                                    positive.setEnabled(true);
                                    newLayout.setError(getString(R.string.settings_request_failed));
                                }
                            });
                }));
        dialog.show();
    }

    private void loadInterestPreferences() {
        if (!ensureLoggedIn()) {
            return;
        }
        Toast.makeText(this, R.string.settings_preferences_loading, Toast.LENGTH_SHORT).show();
        service.getTagPrefs(accountId).enqueue(new Callback<Result<List<AccountTagPref>>>() {
            @Override
            public void onResponse(@NonNull Call<Result<List<AccountTagPref>>> call,
                                   @NonNull Response<Result<List<AccountTagPref>>> response) {
                Result<List<AccountTagPref>> result = response.body();
                if (!response.isSuccessful() || result == null || result.getCode() != 1) {
                    showRequestError(response);
                    return;
                }
                showInterestPreferencesDialog(result.getData());
            }

            @Override
            public void onFailure(@NonNull Call<Result<List<AccountTagPref>>> call,
                                  @NonNull Throwable t) {
                Toast.makeText(SettingsActivity.this,
                        R.string.settings_request_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showInterestPreferencesDialog(List<AccountTagPref> preferences) {
        String[] names = getResources().getStringArray(R.array.route_tag_names);
        boolean[] selected = new boolean[names.length];
        if (preferences != null) {
            for (AccountTagPref preference : preferences) {
                int index = preference.getTagId() - 1;
                if (index >= 0 && index < selected.length) {
                    selected[index] = true;
                }
            }
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_interest_preferences)
                .setMultiChoiceItems(names, selected, (target, which, isChecked) ->
                        selected[which] = isChecked)
                .setNegativeButton(R.string.settings_cancel, null)
                .setPositiveButton(R.string.settings_confirm, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    int count = 0;
                    for (boolean checked : selected) {
                        if (checked) {
                            count++;
                        }
                    }
                    if (count < 1 || count > 3) {
                        Toast.makeText(this, R.string.settings_preferences_required,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    saveInterestPreferences(dialog, selected);
                }));
        dialog.show();
    }

    private void saveInterestPreferences(AlertDialog dialog, boolean[] selected) {
        List<AccountTagPref> preferences = new ArrayList<>();
        for (int i = 0; i < selected.length; i++) {
            if (!selected[i]) {
                continue;
            }
            AccountTagPref preference = new AccountTagPref();
            preference.setAccountId(accountId);
            preference.setTagId(i + 1);
            preferences.add(preference);
        }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        service.updateTagPrefs(accountId, preferences).enqueue(new Callback<Result>() {
            @Override
            public void onResponse(@NonNull Call<Result> call, @NonNull Response<Result> response) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                Result result = response.body();
                if (response.isSuccessful() && result != null && result.getCode() == 1) {
                    StringBuilder ids = new StringBuilder();
                    for (AccountTagPref preference : preferences) {
                        if (ids.length() > 0) {
                            ids.append(',');
                        }
                        ids.append(preference.getTagId());
                    }
                    prefs.edit().putString("route_tag_ids", ids.toString()).apply();
                    dialog.dismiss();
                    Toast.makeText(SettingsActivity.this,
                            R.string.settings_preferences_saved, Toast.LENGTH_SHORT).show();
                } else {
                    showRequestError(response);
                }
            }

            @Override
            public void onFailure(@NonNull Call<Result> call, @NonNull Throwable t) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                Toast.makeText(SettingsActivity.this,
                        R.string.settings_request_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showRoleDialog() {
        if (!ensureLoggedIn()) {
            return;
        }
        String[] labels = {"普通用户", "领队", "普通用户 + 领队"};
        String[] values = {"USER", "LEADER", "BOTH"};
        int selected = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equalsIgnoreCase(accountRole)) {
                selected = i;
                break;
            }
        }
        final int[] choice = {selected};
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_account_role)
                .setSingleChoiceItems(labels, selected, (target, which) -> choice[0] = which)
                .setNegativeButton(R.string.settings_cancel, null)
                .setPositiveButton(R.string.settings_confirm, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String selectedRole = values[choice[0]];
                    if (selectedRole.equalsIgnoreCase(accountRole)) {
                        dialog.dismiss();
                        return;
                    }
                    updateRole(dialog, selectedRole);
                }));
        dialog.show();
    }

    private void updateRole(AlertDialog dialog, String role) {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        service.updateAccountRole(accountId, new UpdateAccountRoleRequest(role))
                .enqueue(new Callback<Result<JsonElement>>() {
                    @Override
                    public void onResponse(@NonNull Call<Result<JsonElement>> call,
                                           @NonNull Response<Result<JsonElement>> response) {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                        Result<JsonElement> result = response.body();
                        if (!response.isSuccessful() || result == null || result.getCode() != 1) {
                            showRequestError(response);
                            return;
                        }
                        accountRole = role;
                        persistRoleResult(role, result.getData());
                        updateRowSummary(R.id.rowAccountRole, roleLabel(role));
                        dialog.dismiss();
                        Toast.makeText(SettingsActivity.this,
                                R.string.settings_role_changed, Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onFailure(@NonNull Call<Result<JsonElement>> call,
                                          @NonNull Throwable t) {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                        Toast.makeText(SettingsActivity.this,
                                R.string.settings_request_failed, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void persistRoleResult(String role, JsonElement data) {
        String mode = prefs.getString("Mode", "USER");
        String scope;
        if ("LEADER".equals(role)) {
            scope = RoleSelectionActivity.ROLE_SCOPE_LEADER;
            mode = "LEADER";
        } else if ("BOTH".equals(role)) {
            scope = RoleSelectionActivity.ROLE_SCOPE_BOTH;
        } else {
            scope = RoleSelectionActivity.ROLE_SCOPE_USER;
            mode = "USER";
        }
        SharedPreferences.Editor editor = prefs.edit()
                .putString(RoleSelectionActivity.EXTRA_ROLE, role)
                .putString(RoleSelectionActivity.ROLE_SCOPE, scope)
                .putString("Mode", mode);
        if (data != null && data.isJsonObject()) {
            JsonObject object = data.getAsJsonObject();
            if (object.has("token") && !object.get("token").isJsonNull()) {
                ApiClient.saveToken(object.get("token").getAsString());
            }
            if (object.has("refreshToken") && !object.get("refreshToken").isJsonNull()) {
                editor.putString("refresh_token", object.get("refreshToken").getAsString());
            }
        }
        editor.apply();
    }

    private boolean ensureLoggedIn() {
        if (accountId > 0) {
            return true;
        }
        Toast.makeText(this, R.string.settings_not_logged_in, Toast.LENGTH_SHORT).show();
        return false;
    }

    private String roleLabel(String role) {
        if ("LEADER".equalsIgnoreCase(role)) {
            return "领队";
        }
        if ("BOTH".equalsIgnoreCase(role)) {
            return "普通用户 + 领队";
        }
        if ("USER".equalsIgnoreCase(role)) {
            return "普通用户";
        }
        return getString(R.string.settings_account_role_summary);
    }

    private String textOf(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private void showRequestError(Response<?> response) {
        Toast.makeText(this, readServerMessage(response), Toast.LENGTH_SHORT).show();
    }

    private String readServerMessage(Response<?> response) {
        if (response != null && response.body() instanceof Result) {
            String message = ((Result<?>) response.body()).getMsg();
            if (!TextUtils.isEmpty(message)) {
                return message;
            }
        }
        if (response != null && response.errorBody() != null) {
            try {
                JsonElement element = JsonParser.parseString(response.errorBody().string());
                if (element.isJsonObject() && element.getAsJsonObject().has("msg")) {
                    return element.getAsJsonObject().get("msg").getAsString();
                }
            } catch (Exception ignored) {
            }
        }
        return getString(R.string.settings_request_failed);
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
