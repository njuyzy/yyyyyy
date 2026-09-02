package com.example.Japp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
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

import android.util.Log;

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

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CodeLoginActivity extends AppCompatActivity {

    private TextInputLayout tilPhone, tilCode;
    private TextInputEditText etPhone, etCode;
    private Button btnLogin, btnGetCode;
    private TextView btnRegister;
    private ImageButton btnBack;
    private CheckBox autoLogin;

    private int countdown = 60;
    private Handler handler = new Handler();
    private Runnable countdownRunnable;
    private String savedCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_code_login);

        initialize();

        boolean autoLoginEnabled = getSharedPreferences("user_pref", MODE_PRIVATE)
                .getBoolean("autoLogin", false);
        autoLogin.setChecked(autoLoginEnabled);

        if (autoLoginEnabled
                && getSharedPreferences("user_pref", MODE_PRIVATE).getBoolean("is_logged_in", false)) {
            Jump();
        }

        setupListeners();
        setupErrorClearListeners();
    }

    private void initialize() {
        tilPhone = findViewById(R.id.tilPhone);
        tilCode = findViewById(R.id.tilCode);

        etPhone = findViewById(R.id.etPhone);
        etCode = findViewById(R.id.etCode);

        btnLogin = findViewById(R.id.btnLogin);
        btnGetCode = findViewById(R.id.btnGetCode);
        btnRegister = findViewById(R.id.btnRegister);
        btnBack = findViewById(R.id.btnBack);

        autoLogin = findViewById(R.id.autoLogin);
        View autoLoginRow = findViewById(R.id.autoLoginRow);
        autoLoginRow.setOnClickListener(v -> autoLogin.setChecked(!autoLogin.isChecked()));
    }

    private void setupErrorClearListeners() {
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
                validatePhoneFormat();
            }
        });

        etCode.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (tilCode.isErrorEnabled()) {
                    tilCode.setError(null);
                    tilCode.setErrorEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        View.OnFocusChangeListener focusChangeListener = new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    if (v == etPhone && tilPhone.isErrorEnabled()) {
                        tilPhone.setError(null);
                        tilPhone.setErrorEnabled(false);
                    } else if (v == etCode && tilCode.isErrorEnabled()) {
                        tilCode.setError(null);
                        tilCode.setErrorEnabled(false);
                    }
                }
            }
        };

        etPhone.setOnFocusChangeListener(focusChangeListener);
        etCode.setOnFocusChangeListener(focusChangeListener);
    }

    private void clearAllErrors() {
        if (tilPhone.isErrorEnabled()) {
            tilPhone.setError(null);
            tilPhone.setErrorEnabled(false);
        }
        if (tilCode.isErrorEnabled()) {
            tilCode.setError(null);
            tilCode.setErrorEnabled(false);
        }
    }

    private void setupListeners() {
        autoLogin.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(@NonNull CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    getSharedPreferences("user_pref", MODE_PRIVATE).edit()
                            .putBoolean("autoLogin", true).apply();
                } else {
                    getSharedPreferences("user_pref", MODE_PRIVATE).edit()
                            .putBoolean("autoLogin", false).apply();
                }
            }
        });

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(CodeLoginActivity.this, LoginEntryActivity.class);
                startActivity(intent);
                finish();
            }
        });

        btnRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(CodeLoginActivity.this, signup.class);
                startActivity(intent);
            }
        });

        btnGetCode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String phone = Objects.requireNonNull(etPhone.getText()).toString().trim();

                tilPhone.setError(null);
                tilPhone.setErrorEnabled(false);

                if (TextUtils.isEmpty(phone)) {
                    tilPhone.setError("手机号不能为空");
                    tilPhone.requestFocus();
                    return;
                }

                if (!MainActivity.isValidPhone(phone)) {
                    tilPhone.setError("请输入正确的11位手机号");
                    tilPhone.requestFocus();
                    return;
                }

                btnGetCode.setEnabled(false);
                btnGetCode.setText("发送中...");
                sendVerificationCode(phone);
            }
        });

        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearAllErrors();

                String phone = Objects.requireNonNull(etPhone.getText()).toString().trim();
                String code = Objects.requireNonNull(etCode.getText()).toString().trim();

                if (TextUtils.isEmpty(phone)) {
                    tilPhone.setError("手机号不能为空");
                    tilPhone.requestFocus();
                    Toast.makeText(CodeLoginActivity.this, "手机号不能为空", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (!MainActivity.isValidPhone(phone)) {
                    tilPhone.setError("请输入正确的11位手机号");
                    tilPhone.requestFocus();
                    Toast.makeText(CodeLoginActivity.this, "请输入正确的手机号", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (TextUtils.isEmpty(code)) {
                    tilCode.setError("验证码不能为空");
                    tilCode.requestFocus();
                    Toast.makeText(CodeLoginActivity.this, "验证码不能为空", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (TextUtils.isEmpty(savedCode) || !code.equals(savedCode)) {
                    tilCode.setError("验证码错误");
                    tilCode.requestFocus();
                    Toast.makeText(CodeLoginActivity.this, "验证码错误", Toast.LENGTH_SHORT).show();
                    return;
                }

                btnLogin.setEnabled(false);
                btnLogin.setText("登录中...");
                performLogin(phone, code);
            }
        });
    }

    private void sendVerificationCode(String phone) {
        Toast.makeText(this, "验证码已发送", Toast.LENGTH_LONG).show();
        startCountdown();
        savedCode = "111111";
    }

    private void startCountdown() {
        btnGetCode.setEnabled(false);

        countdownRunnable = new Runnable() {
            @Override
            public void run() {
                if (countdown > 0) {
                    btnGetCode.setText(countdown + "秒后重试");
                    countdown--;
                    handler.postDelayed(this, 1000);
                } else {
                    btnGetCode.setEnabled(true);
                    btnGetCode.setText("获取验证码");
                    countdown = 60;
                }
            }
        };

        handler.post(countdownRunnable);
    }

    private void performLogin(String phone, String password) {
        UserService service = ApiClient.getClient().create(UserService.class);
        LoginRequest request = new LoginRequest(phone, password);
        Call<Result<LoginResponse>> call = service.login(request);

        call.enqueue(new Callback<Result<LoginResponse>>() {
            @Override
            public void onResponse(Call<Result<LoginResponse>> call, Response<Result<LoginResponse>> response) {
                btnLogin.setEnabled(true);
                btnLogin.setText("登录");

                if (response.isSuccessful() && response.body() != null) {
                    if (response.body().getCode() == 1) {
                        LoginResponse loginData = response.body().getData();
                        Account account = loginData != null ? loginData.getAccount() : null;
                        if (account != null) {
                            User user = new User(
                                    account.getUsername(),
                                    account.getPhone(),
                                    password
                            );
                            user.setId(String.valueOf(account.getId()));

                            SharedPreferences sharedPreferences = getSharedPreferences("user_pref", MODE_PRIVATE);
                            sharedPreferences.edit()
                                    .putString("user_inf", user.toString())
                                    .putString("Mode", account.getRole())
                                    .putString("region_code", account.getRegionCode())
                                    .putInt("account_id", account.getId())
                                    .putBoolean("is_logged_in", true)
                                    .apply();

                            ApiClient.saveTokens(loginData.getToken(),
                                    loginData.getRefreshToken());

                            loginSuccess(user);
                        }
                    } else {
                        if (response.body().getMsg() != null) {
                            Toast.makeText(CodeLoginActivity.this, response.body().getMsg(), Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(CodeLoginActivity.this, "登录失败，请重试", Toast.LENGTH_SHORT).show();
                        }

                        tilCode.setError("验证码错误");
                        tilCode.requestFocus();
                    }
                } else {
                    Toast.makeText(CodeLoginActivity.this, "网络错误，请重试", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Result<LoginResponse>> call, Throwable t) {
                btnLogin.setEnabled(true);
                btnLogin.setText("登录");

                String reason = t != null ? t.getMessage() : "unknown";
                Log.e("CodeLoginActivity", "login failed: " + reason, t);

                if (t instanceof SocketTimeoutException) {
                    Toast.makeText(CodeLoginActivity.this, "服务器响应超时，请稍后重试", Toast.LENGTH_SHORT).show();
                } else if (t instanceof IOException) {
                    Toast.makeText(CodeLoginActivity.this, "网络连接超时，请检查网络", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(CodeLoginActivity.this, "网络连接失败，请检查网络（" + reason + "）", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void Jump() {
        if (getSharedPreferences("user_pref", MODE_PRIVATE).getString("Mode", "USER").equals("USER")) {
            startActivity(new Intent(CodeLoginActivity.this, UserMainActivity.class));
        } else {
            startActivity(new Intent(CodeLoginActivity.this, LeaderMainActivity.class));
        }
        finish();
    }

    private void loginSuccess(User user) {
        Animation slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up);
        slideUp.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                Toast.makeText(CodeLoginActivity.this, "登录成功！欢迎 " + user.getUsername(), Toast.LENGTH_LONG).show();

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

        btnLogin.startAnimation(slideUp);
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null && countdownRunnable != null) {
            handler.removeCallbacks(countdownRunnable);
        }
    }
}
