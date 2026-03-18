package com.example.Japp;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.Japp.data.User;
import com.example.Japp.leader.LeaderMainActivity;
import com.example.Japp.user.UserMainActivity;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    private TextInputLayout tilPhone, tilPassword, tilVerCode;
    private Button btnGetCode, btnLogin;
    private TextInputEditText etPhone, etPassword, etVerCode;

    // 倒计时相关
    private int countdown = 60;
    private Handler handler = new Handler();
    private Runnable countdownRunnable;
    private TextView btnRegister;
    private CheckBox autoLogin;
    private String savedCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        initialize();
        //如果用户设置自动登录，直接跳转
        if(getSharedPreferences("user_pref",MODE_PRIVATE).getBoolean("autoLogin",false)
            &&getSharedPreferences("user_pref",MODE_PRIVATE).getBoolean("is_logged_in",false)) {
            Jump();
        }
        setupListeners();
        setupErrorClearListeners();
    }

    private void initialize() {
        tilPhone = findViewById(R.id.tilPhone);
        tilPassword = findViewById(R.id.tilPassword);
        tilVerCode = findViewById(R.id.tilCode);

        etPhone = findViewById(R.id.etPhone);
        etPassword = findViewById(R.id.etPassword);
        etVerCode = findViewById(R.id.etCode);

        btnGetCode = findViewById(R.id.btnGetCode);
        btnLogin = findViewById(R.id.btnLogin);
        btnRegister = findViewById(R.id.btnRegister);

        autoLogin=findViewById(R.id.autoLogin);
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
        etVerCode.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (tilVerCode.isErrorEnabled()) {
                    tilVerCode.setError(null);
                    tilVerCode.setErrorEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        // 焦点变化监听
        View.OnFocusChangeListener focusChangeListener = new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    if (v == etPhone && tilPhone.isErrorEnabled()) {
                        tilPhone.setError(null);
                        tilPhone.setErrorEnabled(false);
                    } else if (v == etPassword && tilPassword.isErrorEnabled()) {
                        tilPassword.setError(null);
                        tilPassword.setErrorEnabled(false);
                    } else if (v == etVerCode && tilVerCode.isErrorEnabled()) {
                        tilVerCode.setError(null);
                        tilVerCode.setErrorEnabled(false);
                    }
                }
            }
        };

        etPhone.setOnFocusChangeListener(focusChangeListener);
        etPassword.setOnFocusChangeListener(focusChangeListener);
        etVerCode.setOnFocusChangeListener(focusChangeListener);
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
        if (tilVerCode.isErrorEnabled()) {
            tilVerCode.setError(null);
            tilVerCode.setErrorEnabled(false);
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

        btnRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, signup.class);
                startActivity(intent);
            }
        });

        btnGetCode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                tilPhone.setError(null);
                tilPhone.setErrorEnabled(false);

                String phone = Objects.requireNonNull(etPhone.getText()).toString().trim();

                if (TextUtils.isEmpty(phone)) {
                    tilPhone.setError("手机号不能为空");
                    tilPhone.requestFocus();
                    return;
                }

                if (!isValidPhone(phone)) {
                    tilPhone.setError("请输入正确的11位手机号");
                    tilPhone.requestFocus();
                    return;
                }

                if (findUserByPhone(phone) == null) {
                    tilPhone.setError("手机号未注册");
                    tilPhone.requestFocus();
                    Toast.makeText(MainActivity.this, "该手机号尚未注册", Toast.LENGTH_SHORT).show();
                    return;
                }

                sendVerificationCode(phone);
            }
        });

        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearAllErrors();

                String phone = Objects.requireNonNull(etPhone.getText()).toString().trim();
                String password = Objects.requireNonNull(etPassword.getText()).toString().trim();
                String code = Objects.requireNonNull(etVerCode.getText()).toString().trim();

                if (TextUtils.isEmpty(phone)) {
                    tilPhone.setError("手机号不能为空");
                    tilPhone.requestFocus();
                    Toast.makeText(MainActivity.this, "手机号不能为空", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (!isValidPhone(phone)) {
                    tilPhone.setError("请输入正确的11位手机号");
                    tilPhone.requestFocus();
                    Toast.makeText(MainActivity.this, "请输入正确的手机号", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (TextUtils.isEmpty(password)) {
                    tilPassword.setError("密码不能为空");
                    tilPassword.requestFocus();
                    Toast.makeText(MainActivity.this, "密码不能为空", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (TextUtils.isEmpty(code)) {
                    tilVerCode.setError("验证码不能为空");
                    tilVerCode.requestFocus();
                    Toast.makeText(MainActivity.this, "验证码不能为空", Toast.LENGTH_SHORT).show();
                    return;
                }

                performLogin(phone, password, code);
            }
        });
    }

    private void sendVerificationCode(String phone) {
        Toast.makeText(this, "验证码已发送到 " + phone, Toast.LENGTH_LONG).show();
        startCountdown();
        savedCode = "111111"; // TODO: 发送验证码，记录在savedCode
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
                    // 倒计时结束，恢复按钮
                    btnGetCode.setEnabled(true);
                    btnGetCode.setText("获取验证码");
                    countdown = 60;
                }
            }
        };

        handler.post(countdownRunnable);
    }

    private void performLogin(String phone, String password, String code) {

        User user = findUserByPhone(phone);

        if (user == null) {

            tilPhone.setError("用户不存在");
            tilPhone.requestFocus();
            Toast.makeText(MainActivity.this, "用户不存在", Toast.LENGTH_SHORT).show();
            return;
        }

        if (savedCode == null) {
            tilVerCode.setError("请先获取验证码");
            tilVerCode.requestFocus();
            Toast.makeText(MainActivity.this, "请先获取验证码", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!savedCode.equals(code)) {
            tilVerCode.setError("验证码错误");
            tilVerCode.requestFocus();
            Toast.makeText(MainActivity.this, "验证码错误", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!user.getPassword(user.toString()).equals(password)) {
            tilPassword.setError("密码错误");
            tilPassword.requestFocus();
            Toast.makeText(MainActivity.this, "密码错误", Toast.LENGTH_SHORT).show();
            return;
        }
        loginSuccess(user);
    }

    private void Jump(){
        if (getSharedPreferences("user_pref",MODE_PRIVATE).getString("Mode","USER").equals("USER")) {
            startActivity(new Intent(MainActivity.this, UserMainActivity.class));
            //finish();
        } else {
            startActivity(new Intent(MainActivity.this, LeaderMainActivity.class));
            //finish();
        }
    }
    private void loginSuccess(User user) {
        Toast.makeText(this, "登录成功！欢迎 " + user.getUsername(user.toString()), Toast.LENGTH_LONG).show();

        //更新注册表
        getSharedPreferences("user_pref", MODE_PRIVATE)
                .edit()
                .putBoolean("is_logged_in", true)
                .putString("user_inf",user.toString())
                .apply();

        Jump();

    }

    private User findUserByPhone(String phone) {
        // TODO: 根据号码查找用户
        return new User();
    }

    public static boolean isValidPhone(String phone) {
        return phone != null && phone.length() == 11 && phone.matches("^1[3-9]\\d{9}$");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null && countdownRunnable != null) {
            handler.removeCallbacks(countdownRunnable);
        }
    }
}