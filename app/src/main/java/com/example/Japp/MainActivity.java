package com.example.Japp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.Japp.data.User;
import com.example.Japp.leader.LeaderMainActivity;
import com.example.Japp.network.ApiClient;
import com.example.Japp.network.api.UserService;
import com.example.Japp.network.models.Account;
import com.example.Japp.network.models.LoginResponse;
import com.example.Japp.network.models.Result;
import com.example.Japp.network.models.requests.LoginRequest;
import com.example.Japp.user.UserMainActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Objects;

import android.util.Log;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private TextInputLayout tilPhone, tilPassword;
    private Button btnLogin;
    private TextInputEditText etPhone, etPassword;
    private TextView btnRegister;
    private ImageButton btnBack;
    private CheckBox autoLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        initialize();

        // 同步自动登录复选框状态，交由用户自主选择
        boolean autoLoginEnabled = getSharedPreferences("user_pref", MODE_PRIVATE)
                .getBoolean("autoLogin", false);
        autoLogin.setChecked(autoLoginEnabled);

        // 仅在用户勾选自动登录且存在登录态时自动跳转
        if (autoLoginEnabled
                && getSharedPreferences("user_pref", MODE_PRIVATE).getBoolean("is_logged_in", false)) {
            Jump();
        }
        setupListeners();
        setupErrorClearListeners();
    }

    private void initialize() {
        tilPhone = findViewById(R.id.tilPhone);
        tilPassword = findViewById(R.id.tilPassword);

        etPhone = findViewById(R.id.etPhone);
        etPassword = findViewById(R.id.etPassword);

        btnLogin = findViewById(R.id.btnLogin);
        btnRegister = findViewById(R.id.btnRegister);
        btnBack = findViewById(R.id.btnBack);

        autoLogin=findViewById(R.id.autoLogin);
        View autoLoginRow = findViewById(R.id.autoLoginRow);
        autoLoginRow.setOnClickListener(v -> autoLogin.setChecked(!autoLogin.isChecked()));
    }

    private void setupErrorClearListeners() {
        // 手机号输入监听
        etPhone.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (tilPhone.isErrorEnabled()) {
                    tilPhone.setError(null);
                    tilPhone.setErrorEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                // 实时验证手机号格式
                validatePhoneFormat();
            }
        });

        // 密码输入监听
        etPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (tilPassword.isErrorEnabled()) {
                    tilPassword.setError(null);
                    tilPassword.setErrorEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        // 为每个输入框设置焦点变化监听，当获得焦点时也清除对应的错误
        View.OnFocusChangeListener focusChangeListener = new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    // 根据获得焦点的视图清除对应的错误
                    if (v == etPhone && tilPhone.isErrorEnabled()) {
                        tilPhone.setError(null);
                        tilPhone.setErrorEnabled(false);
                    } else if (v == etPassword && tilPassword.isErrorEnabled()) {
                        tilPassword.setError(null);
                        tilPassword.setErrorEnabled(false);
                    }
                }
            }
        };

        etPhone.setOnFocusChangeListener(focusChangeListener);
        etPassword.setOnFocusChangeListener(focusChangeListener);
    }

    private void clearAllErrors() {
        if (tilPhone.isErrorEnabled()) {
            tilPhone.setError(null);
            tilPhone.setErrorEnabled(false);
        }
        if (tilPassword.isErrorEnabled()) {
            tilPassword.setError(null);
            tilPassword.setErrorEnabled(false);
        }
    }

    private void setupListeners() {

        autoLogin.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
            @Override
            public void onCheckedChanged(@NonNull CompoundButton buttonView, boolean isChecked){
                if(isChecked){
                    getSharedPreferences("user_pref",MODE_PRIVATE).edit()
                            .putBoolean("autoLogin",true).apply();
                }
                else{
                    getSharedPreferences("user_pref",MODE_PRIVATE).edit()
                            .putBoolean("autoLogin",false).apply();
                }
            }
        });

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, LoginEntryActivity.class);
                startActivity(intent);
                finish();
            }
        });

        btnRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, signup.class);
                startActivity(intent);
            }
        });

        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearAllErrors();

                String phone = Objects.requireNonNull(etPhone.getText()).toString().trim();
                String password = Objects.requireNonNull(etPassword.getText()).toString().trim();

                if (TextUtils.isEmpty(phone)) {
                    tilPhone.setError("手机号不能为空");
                    tilPhone.requestFocus();
                    Toast.makeText(MainActivity.this, "手机号不能为空", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (TextUtils.isEmpty(password)) {
                    tilPassword.setError("密码不能为空");
                    tilPassword.requestFocus();
                    Toast.makeText(MainActivity.this, "密码不能为空", Toast.LENGTH_SHORT).show();
                    return;
                }

                // 添加登录按钮点击动画
                btnLogin.setEnabled(false);
                btnLogin.setText("登录中...");

                performLogin(phone, password);
            }
        });
    }

    private void performLogin(String phone, String password) {

        // 调用API登录
        UserService service = ApiClient.getClient().create(UserService.class);
        LoginRequest request = new LoginRequest(phone, password);
        Call<Result<LoginResponse>> call = service.login(request);

        call.enqueue(new Callback<Result<LoginResponse>>() {
            @Override
            public void onResponse(Call<Result<LoginResponse>> call, Response<Result<LoginResponse>> response) {
                // 恢复按钮状态
                btnLogin.setEnabled(true);
                btnLogin.setText("登录");

                if (response.isSuccessful() && response.body() != null) {
                    if (response.body().getCode() == 1) {
                        LoginResponse loginData = response.body().getData();
                        Account account = loginData != null ? loginData.getAccount() : null;
                        if (account != null) {
                            // 登录成功，保存用户信息
                            User user = new User(
                                    account.getUsername(),
                                    account.getPhone(),
                                    password // 保存明文密码
                            );
                            user.setId(String.valueOf(account.getId()));

                            String roleScope = RoleSelectionActivity.ROLE_SCOPE_USER;
                            String mode = "USER";
                            String role = account.getRole();
                            if ("LEADER".equalsIgnoreCase(role)) {
                                roleScope = RoleSelectionActivity.ROLE_SCOPE_LEADER;
                                mode = "LEADER";
                            } else if ("BOTH".equalsIgnoreCase(role)) {
                                roleScope = RoleSelectionActivity.ROLE_SCOPE_BOTH;
                                mode = "LEADER";
                            }

                            // 写入注册表
                            SharedPreferences sharedPreferences = getSharedPreferences("user_pref", MODE_PRIVATE);
                            sharedPreferences.edit()
                                    .putString("user_inf", user.toString())
                                    .putString("Mode", mode)
                                    .putString(RoleSelectionActivity.ROLE_SCOPE, roleScope)
                                    .putString(RoleSelectionActivity.EXTRA_ROLE, role)
                                    .putString("region_code", account.getRegionCode())
                                    .putInt("account_id", account.getId())
                                    .putBoolean("is_logged_in", true)
                                    .apply();

                            // 保存 token
                            if (loginData.getToken() != null) {
                                ApiClient.saveToken(loginData.getToken());
                            }

                            loginSuccess(user);
                        }
                    } else {
                        // 处理登录失败
                        if (response.body().getMsg() != null) {
                            Toast.makeText(MainActivity.this, response.body().getMsg(), Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(MainActivity.this, "登录失败，请重试", Toast.LENGTH_SHORT).show();
                        }

                        tilPassword.setError("用户名或密码错误");
                        tilPassword.requestFocus();
                    }
                } else {
                    Toast.makeText(MainActivity.this, "网络错误，请重试", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Result<LoginResponse>> call, Throwable t) {
                // 恢复按钮状态
                btnLogin.setEnabled(true);
                btnLogin.setText("登录");

                String reason = t != null ? t.getMessage() : "unknown";
                Log.e("MainActivity", "login failed: " + reason, t);

                if (t instanceof SocketTimeoutException) {
                    Toast.makeText(MainActivity.this, "服务器响应超时，请稍后重试", Toast.LENGTH_SHORT).show();
                } else if (t instanceof IOException) {
                    Toast.makeText(MainActivity.this, "网络连接超时，请检查网络", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "网络连接失败，请检查网络（" + reason + "）", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void Jump(){

        if(getSharedPreferences("user_pref",MODE_PRIVATE).getString("Mode","USER").equals("USER")){
            startActivity(new Intent(MainActivity.this, UserMainActivity.class));
        }
        else {
            startActivity(new Intent(MainActivity.this, LeaderMainActivity.class));
        }
        finish();
    }
    private void loginSuccess(User user) {
        // 添加登录成功动画
        Animation slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up);
        slideUp.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                Toast.makeText(MainActivity.this, "登录成功！欢迎 " + user.getUsername(), Toast.LENGTH_LONG).show();

                // 更新登录态并持久化用户当前的自动登录选择
                boolean autoLoginEnabled = autoLogin.isChecked();
                getSharedPreferences("user_pref", MODE_PRIVATE)
                        .edit()
                        .putBoolean("is_logged_in", true)
                        .putBoolean("autoLogin", autoLoginEnabled)
                        .putString("user_inf", user.toString())
                        .apply();

                Jump();
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });

        // 应用动画到登录按钮
        btnLogin.startAnimation(slideUp);
    }

    private User findUserByPhone(String phone) {
        // 实际应用中应该调用API检查手机号是否已注册
        // 这里简化实现，返回false
        return null;
    }

    public static boolean isValidPhone(String phone) {
        return phone != null && phone.length() == 11 && phone.matches("^1[3-9]\\d{9}$");
    }

    private void validatePhoneFormat() {
        String phone = Objects.requireNonNull(etPhone.getText()).toString().trim();

        if (TextUtils.isEmpty(phone)) {
            if (tilPhone.isErrorEnabled()) {
                tilPhone.setError(null);
                tilPhone.setErrorEnabled(false);
            }
            return;
        }

        if (tilPhone.isErrorEnabled()) {
            tilPhone.setError(null);
            tilPhone.setErrorEnabled(false);
        }
    }

}