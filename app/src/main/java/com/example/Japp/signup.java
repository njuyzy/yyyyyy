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
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.Japp.data.User;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Objects;

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
                // 不需要操作
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

                // 发送验证码
                sendVerificationCode(phone);
            }
        });

        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
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

        User user = new User(name, phone, password);//user!=null
        //TODO:上传数据库

        //写入注册表
        SharedPreferences sharedPreferences=getSharedPreferences("user_pref",MODE_PRIVATE);
        sharedPreferences.edit()
                .putString("user_inf",user.toString())
                .putBoolean("is_logged_in",true)
                .apply();
        //返回登录
        Intent intent = new Intent(signup.this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    private boolean PhoneExists(String phone) {
        // TODO: 检查数据库中是否有相同phone
        return false;
    }

    private boolean NameExists(String name) {
        // TODO: 检查数据库中是否有相同name
        return false;
    }

    private void sendVerificationCode(String phone) {
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