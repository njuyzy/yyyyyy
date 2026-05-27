package com.example.Japp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import com.example.Japp.data.User;
import com.example.Japp.leader.LeaderMainActivity;
import com.example.Japp.network.ApiClient;
import com.example.Japp.network.api.UserService;
import com.example.Japp.network.models.Account;
import com.example.Japp.network.models.AccountTagPref;
import com.example.Japp.network.models.LoginResponse;
import com.example.Japp.network.models.Result;
import com.example.Japp.network.models.requests.LoginRequest;
import com.example.Japp.network.models.requests.RegisterRequest;
import com.example.Japp.user.UserMainActivity;
import com.google.android.material.card.MaterialCardView;

import android.text.TextUtils;
import android.widget.Toast;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RoleSelectionActivity extends AppCompatActivity {

    public static final String EXTRA_ROLE = "extra_role";
    public static final String ROLE_SCOPE = "role_scope";
    public static final String ROLE_SCOPE_USER = "USER_ONLY";
    public static final String ROLE_SCOPE_LEADER = "LEADER_ONLY";
    public static final String ROLE_SCOPE_BOTH = "BOTH";

    private MaterialCardView cardUser, cardLeader, cardBoth;
    private TextView txtPhoneInfo;

    private String name;
    private String phone;
    private String password;
    private String code;
    private String regionCode;
    private final List<Integer> selectedTagIds = new ArrayList<>();
    private boolean isSubmitting;

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_role_selection);

        cardUser = findViewById(R.id.cardUser);
        cardLeader = findViewById(R.id.cardLeader);
        cardBoth = findViewById(R.id.cardBoth);
<<<<<<< Updated upstream
        txtPhoneInfo = findViewById(R.id.txtPhoneInfo);
=======
>>>>>>> Stashed changes

        if (!readExtras()) {
            startActivity(new Intent(RoleSelectionActivity.this, signup.class));
            finish();
            return;
        }

        if (txtPhoneInfo != null && phone != null) {
            txtPhoneInfo.setText("手机号：" + phone);
        }

        cardUser.setOnClickListener(v -> handleSelection("USER", ROLE_SCOPE_USER, "USER"));
        cardLeader.setOnClickListener(v -> handleSelection("LEADER", ROLE_SCOPE_LEADER, "LEADER"));
        cardBoth.setOnClickListener(v -> handleSelection("LEADER", ROLE_SCOPE_BOTH, "BOTH"));
    }

    private boolean readExtras() {
        Intent intent = getIntent();
        if (intent == null) {
            return false;
        }
        name = intent.getStringExtra(ProfileSetupActivity.EXTRA_NAME);
        phone = intent.getStringExtra(ProfileSetupActivity.EXTRA_PHONE);
        password = intent.getStringExtra(ProfileSetupActivity.EXTRA_PASSWORD);
        code = intent.getStringExtra(ProfileSetupActivity.EXTRA_CODE);

        SharedPreferences preferences = getSharedPreferences("user_pref", MODE_PRIVATE);
        regionCode = preferences.getString("region_code", "");
        selectedTagIds.clear();
        String tagIds = preferences.getString("route_tag_ids", "");
        if (!TextUtils.isEmpty(tagIds)) {
            for (String part : tagIds.split(",")) {
                if (TextUtils.isEmpty(part)) {
                    continue;
                }
                try {
                    selectedTagIds.add(Integer.parseInt(part.trim()));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        return !TextUtils.isEmpty(name)
                && !TextUtils.isEmpty(phone)
                && !TextUtils.isEmpty(password)
                && !TextUtils.isEmpty(code)
                && !TextUtils.isEmpty(regionCode);
    }

    private void handleSelection(String mode, String roleScope, String registerRole) {
        if (isSubmitting) {
            return;
        }
        isSubmitting = true;
        setCardsEnabled(false);

        SharedPreferences preferences = getSharedPreferences("user_pref", MODE_PRIVATE);
        preferences.edit()
                .putString("Mode", mode)
                .putString(ROLE_SCOPE, roleScope)
                .putString(EXTRA_ROLE, registerRole)
                .apply();

        performRegister(registerRole, roleScope, mode);
    }

    private void setCardsEnabled(boolean enabled) {
        cardUser.setEnabled(enabled);
        cardLeader.setEnabled(enabled);
        cardBoth.setEnabled(enabled);
        cardUser.setAlpha(enabled ? 1f : 0.6f);
        cardLeader.setAlpha(enabled ? 1f : 0.6f);
        cardBoth.setAlpha(enabled ? 1f : 0.6f);
    }

    private void resetSubmitState() {
        isSubmitting = false;
        setCardsEnabled(true);
    }

    private void performRegister(String registerRole, String roleScope, String mode) {
        UserService service = ApiClient.getClient().create(UserService.class);
        RegisterRequest request = new RegisterRequest(registerRole, name, phone, password, regionCode);
        Call<Result> call = service.register(request);

        call.enqueue(new Callback<Result>() {
            @Override
            public void onResponse(Call<Result> call, Response<Result> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getCode() == 1) {
                    loginAfterRegister(roleScope, registerRole, mode);
                } else {
                    resetSubmitState();
                    String msg = response.body() != null ? response.body().getMsg() : null;
                    Toast.makeText(RoleSelectionActivity.this,
                            TextUtils.isEmpty(msg) ? "注册失败，请重试" : msg,
                            Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Result> call, Throwable t) {
                resetSubmitState();
                if (t instanceof SocketTimeoutException) {
                    Toast.makeText(RoleSelectionActivity.this, "服务器响应超时，请稍后重试", Toast.LENGTH_SHORT).show();
                } else if (t instanceof IOException) {
                    Toast.makeText(RoleSelectionActivity.this, "网络连接超时，请检查网络", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(RoleSelectionActivity.this, "网络连接失败，请检查网络", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void loginAfterRegister(String roleScope, String registerRole, String mode) {
        UserService service = ApiClient.getClient().create(UserService.class);
        LoginRequest request = new LoginRequest(phone, password);
        Call<Result<LoginResponse>> call = service.login(request);

        call.enqueue(new Callback<Result<LoginResponse>>() {
            @Override
            public void onResponse(Call<Result<LoginResponse>> call, Response<Result<LoginResponse>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getCode() == 1) {
                    LoginResponse loginData = response.body().getData();
                    Account account = loginData != null ? loginData.getAccount() : null;
                    if (account != null) {
                        User user = new User(
                                account.getUsername(),
                                account.getPhone(),
                                password
                        );
                        user.setId(String.valueOf(account.getId()));

                        boolean autoLoginEnabled = getSharedPreferences("user_pref", MODE_PRIVATE)
                                .getBoolean("autoLogin", false);
                        String savedRegionCode = TextUtils.isEmpty(account.getRegionCode())
                                ? regionCode
                                : account.getRegionCode();

                        SharedPreferences sharedPreferences = getSharedPreferences("user_pref", MODE_PRIVATE);
                        sharedPreferences.edit()
                                .putString("user_inf", user.toString())
                                .putString("Mode", mode)
                                .putString(ROLE_SCOPE, roleScope)
                                .putString(EXTRA_ROLE, registerRole)
                                .putString("region_code", savedRegionCode)
                                .putInt("account_id", account.getId())
                                .putBoolean("is_logged_in", true)
                                .putBoolean("autoLogin", autoLoginEnabled)
                                .apply();

                        if (loginData.getToken() != null) {
                            ApiClient.saveToken(loginData.getToken());
                        }

                        updateTagPrefs(account.getId(), mode);
                        return;
                    }
                }

                resetSubmitState();
                Toast.makeText(RoleSelectionActivity.this, "登录失败，请重试", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(Call<Result<LoginResponse>> call, Throwable t) {
                resetSubmitState();
                Toast.makeText(RoleSelectionActivity.this, "网络连接失败，请检查网络", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateTagPrefs(int accountId, String mode) {
        if (selectedTagIds.isEmpty()) {
            jumpToMain(mode);
            return;
        }

        List<AccountTagPref> prefs = new ArrayList<>();
        for (Integer tagId : selectedTagIds) {
            AccountTagPref pref = new AccountTagPref();
            pref.setAccountId(accountId);
            pref.setTagId(tagId);
            prefs.add(pref);
        }

        UserService service = ApiClient.getClient().create(UserService.class);
        Call<Result> call = service.updateTagPrefs(accountId, prefs);

        call.enqueue(new Callback<Result>() {
            @Override
            public void onResponse(Call<Result> call, Response<Result> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getCode() != 1) {
                    Toast.makeText(RoleSelectionActivity.this, "偏好上传失败，请稍后重试", Toast.LENGTH_SHORT).show();
                }
                jumpToMain(mode);
            }

            @Override
            public void onFailure(Call<Result> call, Throwable t) {
                Toast.makeText(RoleSelectionActivity.this, "偏好上传失败，请稍后重试", Toast.LENGTH_SHORT).show();
                jumpToMain(mode);
            }
        });
    }

    private void jumpToMain(String mode) {
        Intent intent;
        if ("USER".equals(mode)) {
            intent = new Intent(RoleSelectionActivity.this, UserMainActivity.class);
        } else {
            intent = new Intent(RoleSelectionActivity.this, LeaderMainActivity.class);
        }
        startActivity(intent);
        finish();
    }
}
