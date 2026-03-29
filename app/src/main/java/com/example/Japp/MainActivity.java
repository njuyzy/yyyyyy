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
import com.example.Japp.network.models.Result;
import com.example.Japp.network.models.requests.LoginRequest;
import com.example.Japp.user.UserMainActivity;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Objects;
import android.util.Log;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

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

        // 添加启动动画


        // 如果用户设置自动登录，直接跳转
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
                // 验证码输入满6位自动跳转到登录
                if (s.length() == 6) {
                    btnLogin.callOnClick();
                }
            }
        });

        // 为每个输入框设置焦点变化监听，当获得焦点时也清除错误
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

                // 添加登录按钮点击动画
                btnLogin.setEnabled(false);
                btnLogin.setText("登录中...");

                performLogin(phone, password, code);
            }
        });
    }

    private void sendVerificationCode(String phone) {
        // 添加获取验证码动画
        btnGetCode.setEnabled(false);
        btnGetCode.setText("发送中...");

        // 模拟网络请求
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, "验证码已发送到 " + phone, Toast.LENGTH_LONG).show();
                startCountdown();
                savedCode = "111111"; // TODO: 发送验证码，记录在savedCode

                // 恢复按钮状态
                btnGetCode.setEnabled(true);
                btnGetCode.setText("获取验证码");
            }
        }, 1000);
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

        // 调用API登录
        UserService service = ApiClient.getClient().create(UserService.class);
        LoginRequest request = new LoginRequest(phone, password);
        Call<Result<Account>> call = service.login(request);

        call.enqueue(new Callback<Result<Account>>() {
            @Override
            public void onResponse(Call<Result<Account>> call, Response<Result<Account>> response) {
                // 恢复按钮状态
                btnLogin.setEnabled(true);
                btnLogin.setText("登录");

                if (response.isSuccessful() && response.body() != null) {
                    if (response.body().getCode() == 1) {
                        Account account = response.body().getData();
                        if (account != null) {
                            // 登录成功，保存用户信息
                            User user = new User(
                                    account.getUsername(),
                                    account.getPhone(),
                                    account.getPasswordHash()
                            );
                            user.setId(String.valueOf(account.getId()));

                            // 写入注册表
                            SharedPreferences sharedPreferences = getSharedPreferences("user_pref", MODE_PRIVATE);
                            sharedPreferences.edit()
                                    .putString("user_inf", user.toString())
                                    .putString("Mode", account.getRole())
                                    .putBoolean("is_logged_in", true)
                                    .apply();

                            loginSuccess(user);
                        }
                    } else {
                        // 处理登录失败
                        if (response.body().getMsg() != null) {
                            Toast.makeText(MainActivity.this, response.body().getMsg(), Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(MainActivity.this, "登录失败，请重试", Toast.LENGTH_SHORT).show();
                        }

                        // 验证码验证
                        if (savedCode == null) {
                            tilVerCode.setError("请先获取验证码");
                            tilVerCode.requestFocus();
                            return;
                        }

                        if (!savedCode.equals(code)) {
                            tilVerCode.setError("验证码错误");
                            tilVerCode.requestFocus();
                            return;
                        }

                        tilPassword.setError("用户名或密码错误");
                        tilPassword.requestFocus();
                    }
                } else {
                    Toast.makeText(MainActivity.this, "网络错误，请重试", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Result<Account>> call, Throwable t) {
                // 恢复按钮状态
                btnLogin.setEnabled(true);
                btnLogin.setText("登录");

                Toast.makeText(MainActivity.this, "网络连接失败，请检查网络", Toast.LENGTH_SHORT).show();
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

                //更新注册表
                getSharedPreferences("user_pref", MODE_PRIVATE)
                        .edit()
                        .putBoolean("is_logged_in", true)
                        .putString("user_inf",user.toString())
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
        // 从服务器获取用户（使用固定ID 1，假设该用户存在）
        UserService service = ApiClient.getClient().create(UserService.class);
        Call<Result<Account>> call = service.getAccount(1);

        try {
            Response<Result<Account>> response = call.execute();
            if (response.isSuccessful() && response.body() != null) {
                if (response.body().getCode() == 1 && response.body().getData() != null) {
                    Account account = response.body().getData();
                    // 对比手机号
                    if (account.getPhone().equals(phone)) {
                        // 如果手机号匹配，返回用户
                        return new User(
                                account.getUsername(),
                                account.getPhone(),
                                account.getPasswordHash()
                        );
                    }
                }
            }
        } catch (Exception e) {
            // 处理异常
            Log.e("MainActivity", "Error finding user by phone", e);
        }

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


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null && countdownRunnable != null) {
            handler.removeCallbacks(countdownRunnable);
        }
    }
}