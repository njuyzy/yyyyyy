package com.example.Japp;

import static com.example.Japp.MainActivity.isValidPhone;

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
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.Japp.data.User;
import com.example.Japp.network.ApiClient;
import com.example.Japp.network.api.UserService;
import com.example.Japp.network.models.Account;
import com.example.Japp.network.models.Result;
import com.example.Japp.network.models.requests.LoginRequest;
import com.example.Japp.network.models.requests.RegisterRequest;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.Objects;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class signup extends AppCompatActivity {

    private TextInputEditText etName, etPhone, etPassword, etCode;
    private TextInputLayout tilName, tilPhone, tilPassword, tilCode;
    private Button getCode, Signup, cancel;
    private CheckBox autoLogin;
    private int countdown = 60;
    private Handler handler = new Handler();
    private Runnable countdownRunnable;

    private String savedCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        initialize();
        setupListeners();
        setupErrorClearListeners(); // 添加错误清除监听


    }
    private void validatePhoneFormat() {
        String phone = Objects.requireNonNull(etPhone.getText()).toString().trim();

        // 实时输入时：空值不提示，由点击注册/获取验证码时统一做”不能为空”校验
        if (TextUtils.isEmpty(phone)) {
            if (tilPhone.isErrorEnabled()) {
                tilPhone.setError(null);
                tilPhone.setErrorEnabled(false);
            }
            return;
        }

        // 去掉手机号格式校验
        if (tilPhone.isErrorEnabled()) {
            tilPhone.setError(null);
            tilPhone.setErrorEnabled(false);
        }
    }
    private void validateUsernameFormat() {
        String name = Objects.requireNonNull(etName.getText()).toString().trim();

        // 实时输入时：空值不提示，由提交注册时统一做“不能为空”校验
        if (TextUtils.isEmpty(name)) {
            if (tilName.isErrorEnabled()) {
                tilName.setError(null);
                tilName.setErrorEnabled(false);
            }
            return;
        }

        // 长度限制：1~20
        if (name.length() > 20) {
            tilName.setError("用户名长度不能超过20");
            return;
        }

        // 仅允许中文、英文、数字、下划线
        if (!name.matches("^[a-zA-Z0-9_\\u4e00-\\u9fa5]+$")) {
            tilName.setError("用户名仅支持中文、字母、数字和下划线");
            return;
        }

        // 校验通过，清除错误
        if (tilName.isErrorEnabled()) {
            tilName.setError(null);
            tilName.setErrorEnabled(false);
        }
    }
    private void initialize() {

        tilName = findViewById(R.id.usernameLayout);
        tilPhone = findViewById(R.id.phoneLayout);
        tilPassword = findViewById(R.id.passwordLayout);
        tilCode = findViewById(R.id.codeLayout);

        etName = findViewById(R.id.username);
        etPhone = findViewById(R.id.phone_num);
        etPassword = findViewById(R.id.password);
        etCode = findViewById(R.id.code);

        getCode = findViewById(R.id.get_code);
        Signup = findViewById(R.id.register);
        cancel = findViewById(R.id.cancel);

        autoLogin=findViewById(R.id.autoLogin);
    }

    /**
     * 设置错误清除监听器
     * 当用户开始输入时，清除对应字段的错误提示
     */
    private void setupErrorClearListeners() {
        // 用户名输入监听
        etName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // 不需要操作
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // 当用户输入时清除错误
                if (tilName.isErrorEnabled()) {
                    tilName.setError(null);
                    tilName.setErrorEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                // 实时验证用户名格式
                validateUsernameFormat();
            }
        });

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

        // 验证码输入监听
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
                // 验证码输入满6位自动跳转到注册
                if (s.length() == 6) {
                    Signup.callOnClick();
                }
            }
        });

        // 为每个输入框设置焦点变化监听，当获得焦点时也清除错误
        View.OnFocusChangeListener focusChangeListener = new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    // 根据获得焦点的视图清除对应的错误
                    if (v == etName && tilName.isErrorEnabled()) {
                        tilName.setError(null);
                        tilName.setErrorEnabled(false);
                    } else if (v == etPhone && tilPhone.isErrorEnabled()) {
                        tilPhone.setError(null);
                        tilPhone.setErrorEnabled(false);
                    } else if (v == etPassword && tilPassword.isErrorEnabled()) {
                        tilPassword.setError(null);
                        tilPassword.setErrorEnabled(false);
                    } else if (v == etCode && tilCode.isErrorEnabled()) {
                        tilCode.setError(null);
                        tilCode.setErrorEnabled(false);
                    }
                }
            }
        };

        etName.setOnFocusChangeListener(focusChangeListener);
        etPhone.setOnFocusChangeListener(focusChangeListener);
        etPassword.setOnFocusChangeListener(focusChangeListener);
        etCode.setOnFocusChangeListener(focusChangeListener);
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

        getCode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String phone = Objects.requireNonNull(etPhone.getText()).toString().trim();

                // 先清除之前的错误
                tilPhone.setError(null);
                tilPhone.setErrorEnabled(false);

                // 检查手机号是否为空
                if (TextUtils.isEmpty(phone)) {
                    tilPhone.setError("手机号不能为空");
                    tilPhone.requestFocus();
                    return;
                }

                // 检查手机号格式（简单验证：11位数字）
                if (!isValidPhone(phone)) {
                    tilPhone.setError("请输入正确的11位手机号");
                    tilPhone.requestFocus();
                    return;
                }

                // 添加获取验证码动画
                getCode.setEnabled(false);
                getCode.setText("发送中...");

                // 发送验证码
                sendVerificationCode(phone);
            }
        });

        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 添加取消按钮动画
                cancel.setEnabled(false);
                cancel.setText("取消中...");
                finish();
            }
        });

        Signup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 先清除所有错误
                clearAllErrors();

                String name = Objects.requireNonNull(etName.getText()).toString().trim();
                String phone = Objects.requireNonNull(etPhone.getText()).toString().trim();
                String code = Objects.requireNonNull(etCode.getText()).toString().trim();
                String password = Objects.requireNonNull(etPassword.getText()).toString().trim();

                // 验证用户名
                if (TextUtils.isEmpty(name)) {
                    tilName.setError("用户名不能为空");
                    tilName.requestFocus();
                    Toast.makeText(signup.this, "用户名不能为空", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (name.length() > 20) {
                    tilName.setError("用户名长度不能超过20");
                    tilName.requestFocus();
                    Toast.makeText(signup.this, "用户名长度不能超过20", Toast.LENGTH_SHORT).show();
                    return;
                }

                // 验证手机号
                if (TextUtils.isEmpty(phone)) {
                    tilPhone.setError("手机号不能为空");
                    tilPhone.requestFocus();
                    Toast.makeText(signup.this, "手机号不能为空", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (!isValidPhone(phone)) {
                    tilPhone.setError("请输入正确的11位手机号");
                    tilPhone.requestFocus();
                    Toast.makeText(signup.this, "请输入正确的手机号", Toast.LENGTH_SHORT).show();
                    return;
                }

                // 验证验证码
                if (TextUtils.isEmpty(code)) {
                    tilCode.setError("验证码不能为空");
                    tilCode.requestFocus();
                    Toast.makeText(signup.this, "验证码不能为空", Toast.LENGTH_SHORT).show();
                    return;
                }

                // 验证密码
                if (TextUtils.isEmpty(password)) {
                    tilPassword.setError("密码不能为空");
                    tilPassword.requestFocus();
                    Toast.makeText(signup.this, "密码不能为空", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (password.length() > 20) {
                    tilPassword.setError("密码长度不能超过20");
                    tilPassword.requestFocus();
                    Toast.makeText(signup.this, "密码长度不能超过20", Toast.LENGTH_SHORT).show();
                    return;
                }

                // 检查用户名是否已存在
                if (NameExists(name)) {
                    tilName.setError("用户名已存在");
                    tilName.requestFocus();
                    Toast.makeText(signup.this, "用户名已存在", Toast.LENGTH_SHORT).show();
                    return;
                }

                // 检查手机号是否已注册
                if (PhoneExists(phone)) {
                    tilPhone.setError("该手机号已注册");
                    tilPhone.requestFocus();
                    Toast.makeText(signup.this, "该手机号已注册", Toast.LENGTH_SHORT).show();
                    return;
                }

                // 添加注册按钮动画
                Signup.setEnabled(false);
                Signup.setText("注册中...");

                performRegister(name, phone, password, code);
            }
        });
    }

    /**
     * 清除所有输入框的错误提示
     */
    private void clearAllErrors() {
        if (tilName.isErrorEnabled()) {
            tilName.setError(null);
            tilName.setErrorEnabled(false);
        }
        if (tilPhone.isErrorEnabled()) {
            tilPhone.setError(null);
            tilPhone.setErrorEnabled(false);
        }
        if (tilPassword.isErrorEnabled()) {
            tilPassword.setError(null);
            tilPassword.setErrorEnabled(false);
        }
        if (tilCode.isErrorEnabled()) {
            tilCode.setError(null);
            tilCode.setErrorEnabled(false);
        }
    }

    private void performRegister(String name, String phone, String password, String code) {
        if (!code.equals(savedCode)) {
            tilCode.setError("验证码错误");
            tilCode.requestFocus();
            Toast.makeText(signup.this, "验证码错误", Toast.LENGTH_SHORT).show();
            return;
        }

        // 添加网络连接测试
        if (!isServerReachable()) {
            Toast.makeText(signup.this, "无法连接到服务器，请检查网络或稍后重试", Toast.LENGTH_LONG).show();
            Signup.setEnabled(true);
            Signup.setText("注册");
            return;
        }

        // 调用API注册
        UserService service = ApiClient.getClient().create(UserService.class);
        RegisterRequest request = new RegisterRequest("USER", name, phone, password, "210000");
        Call<Result> call = service.register(request);

        call.enqueue(new Callback<Result>() {
            @Override
            public void onResponse(Call<Result> call, Response<Result> response) {
                // 恢复按钮状态
                Signup.setEnabled(true);
                Signup.setText("注册");

                if (response.isSuccessful() && response.body() != null) {
                    if (response.body().getCode() == 1) {
                        // 注册成功，获取用户信息
                        loginAfterRegister(phone, password);
                    } else {
                        // 处理注册失败
                        if (response.body().getMsg() != null) {
                            Toast.makeText(signup.this, response.body().getMsg(), Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(signup.this, "注册失败，请重试", Toast.LENGTH_SHORT).show();
                        }
                        tilPhone.setError("注册失败");
                        tilPhone.requestFocus();
                    }
                } else {
                    Toast.makeText(signup.this, "网络错误，请重试", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Result> call, Throwable t) {
                // 恢复按钮状态
                Signup.setEnabled(true);
                Signup.setText("注册");

                // 提供更具体的错误信息
                if (t instanceof IOException) {
                    Toast.makeText(signup.this, "网络连接超时，请检查网络", Toast.LENGTH_SHORT).show();
                } else if (t instanceof SocketTimeoutException) {
                    Toast.makeText(signup.this, "服务器响应超时，请稍后重试", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(signup.this, "网络连接失败，请检查网络", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    // 添加网络连接测试方法
    private boolean isServerReachable() {
        try {
            URL url = new URL("https://10.6.86.86/login/ping");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000); // 5秒超时
            connection.setReadTimeout(5000);
            int responseCode = connection.getResponseCode();
            return responseCode == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private void loginAfterRegister(String phone, String password) {
        UserService service = ApiClient.getClient().create(UserService.class);
        LoginRequest request = new LoginRequest(phone, password); // 直接使用明文密码
        Call<Result<Account>> call = service.login(request);

        call.enqueue(new Callback<Result<Account>>() {
            @Override
            public void onResponse(Call<Result<Account>> call, Response<Result<Account>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    if (response.body().getCode() == 1) {
                        Account account = response.body().getData();
                        if (account != null) {
                            // 登录成功，保存用户信息
                            User user = new User(
                                    account.getUsername(),
                                    account.getPhone(),
                                    password // 保存明文密码
                            );
                            user.setId(String.valueOf(account.getId()));

                            // 写入注册表
                            SharedPreferences sharedPreferences = getSharedPreferences("user_pref", MODE_PRIVATE);
                            sharedPreferences.edit()
                                    .putString("user_inf", user.toString())
                                    .putString("Mode", account.getRole())
                                    .putBoolean("is_logged_in", true)
                                    .apply();

                            // 添加注册成功动画
                            Animation slideUp = AnimationUtils.loadAnimation(signup.this, R.anim.slide_up);
                            slideUp.setAnimationListener(new Animation.AnimationListener() {
                                @Override
                                public void onAnimationStart(Animation animation) {
                                }

                                @Override
                                public void onAnimationEnd(Animation animation) {
                                    Toast.makeText(signup.this, "注册成功！欢迎 " + user.getUsername(), Toast.LENGTH_SHORT).show();

                                    // 跳转到主页面
                                    Intent intent = new Intent(signup.this, MainActivity.class);
                                    startActivity(intent);
                                    finish();
                                }

                                @Override
                                public void onAnimationRepeat(Animation animation) {
                                }
                            });

                            // 应用动画到注册按钮
                            Signup.startAnimation(slideUp);
                        }
                    } else {
                        Toast.makeText(signup.this, "登录失败，请重试", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(signup.this, "网络错误，请重试", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Result<Account>> call, Throwable t) {
                Toast.makeText(signup.this, "网络连接失败，请检查网络", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private boolean PhoneExists(String phone) {
        // 实际应用中应该调用API检查手机号是否已注册
        // 这里简化实现，返回false
        return false;
    }

    private boolean NameExists(String name) {
        // 本地检查用户名是否已存在（简化实现）
        // 实际应用中应该调用API检查
        return false;
    }

    private void sendVerificationCode(String phone) {
        // 实际应用中应调用API发送验证码
        Toast.makeText(this, "验证码已发送", Toast.LENGTH_LONG).show();
        startCountdown();
        savedCode = "111111"; // TODO: 发送验证码，用savedCode记录
    }

    private void startCountdown() {
        getCode.setEnabled(false);

        countdownRunnable = new Runnable() {
            @Override
            public void run() {
                if (countdown > 0) {
                    getCode.setText(countdown + "秒后重试");
                    countdown--;
                    handler.postDelayed(this, 1000);
                } else {
                    // 倒计时结束，恢复按钮
                    getCode.setEnabled(true);
                    getCode.setText("获取验证码");
                    countdown = 60;
                }
            }
        };

        handler.post(countdownRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null && countdownRunnable != null) {
            handler.removeCallbacks(countdownRunnable);
        }
    }
}