package com.example.Japp;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.Japp.data.User;
import com.example.Japp.leader.LeaderMainActivity;
import com.example.Japp.user.UserMainActivity;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

public class signin extends AppCompatActivity {

    private TextInputLayout tilPhone, tilVerCode;
    private Button btnGetCode, btnLogin;
    private TextInputEditText etPhone, etVerCode;

    // 倒计时相关
    private int countdown = 60;
    private Handler handler = new Handler();
    private Runnable countdownRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_signin);

        initialize();

        // 设置按钮点击事件
        setupListeners();
    }

    private void initialize() {
        tilPhone = findViewById(R.id.tilPhone);
        tilVerCode = findViewById(R.id.tilCode);

        etPhone = findViewById(R.id.etPhone);
        etVerCode = findViewById(R.id.etCode);

        btnGetCode = findViewById(R.id.btnGetCode);
        btnLogin = findViewById(R.id.btnLogin);
    }


    private void setupListeners() {
        // 获取验证码按钮
        btnGetCode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String phone = Objects.requireNonNull(etPhone.getText()).toString().trim();

                // 检查手机号是否为空
                if (TextUtils.isEmpty(phone)) {
                    tilPhone.setError("手机号不能为空");
                    return;
                }

                // 检查手机号格式（简单验证：11位数字）
                if (!isValidPhone(phone)) {
                    tilPhone.setError("请输入正确的11位手机号");
                    return;
                }

                // 清除错误提示
                tilPhone.setError(null);

                // 发送验证码
                sendVerificationCode(phone);
            }
        });

        // 登录按钮
        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 获取输入内容
                String phone = Objects.requireNonNull(etPhone.getText()).toString().trim();
                String code = Objects.requireNonNull(etVerCode.getText()).toString().trim();

                // 检查各项是否为空
                if (TextUtils.isEmpty(phone)) {
                    tilPhone.setError("手机号不能为空");
                    Toast.makeText(signin.this, "手机号不能为空", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (TextUtils.isEmpty(code)) {
                    tilVerCode.setError("验证码不能为空");
                    Toast.makeText(signin.this, "验证码不能为空", Toast.LENGTH_SHORT).show();
                    return;
                }

                // 检查手机号格式
                if (!isValidPhone(phone)) {
                    tilPhone.setError("请输入正确的11位手机号");
                    Toast.makeText(signin.this, "请输入正确的手机号", Toast.LENGTH_SHORT).show();
                    return;
                }

                // 执行登录验证
                performLogin(phone, code);
            }
        });
    }

    private void sendVerificationCode(String phone) {

        Toast.makeText(this, "验证码已发送", Toast.LENGTH_LONG).show();

        startCountdown();
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

    /**
     * 执行登录验证
     */
    private void performLogin(String phone, String code) {

                // 在后端查找用户
                User user = findUserByPhone(phone);

                if (user == null) {
                    // 用户不存在
                    tilPhone.setError("用户不存在");
                    Toast.makeText(signin.this, "用户不存在", Toast.LENGTH_SHORT).show();
                    resetLoginButton();
                    return;
                }

                if (!TextUtils.isEmpty(code)) {
                    String savedCode = "111111";//TODO:发送验证码

                    if (savedCode == null) {
                        tilVerCode.setError("请先获取验证码");
                        Toast.makeText(signin.this, "请先获取验证码", Toast.LENGTH_SHORT).show();
                    }
                    else if (!savedCode.equals(code)) {
                        tilVerCode.setError("验证码不正确");
                        Toast.makeText(signin.this, "验证码不正确", Toast.LENGTH_SHORT).show();
                    } else {
                        // 验证码正确，登录成功
                        loginSuccess(user);
                    }
                }
                resetLoginButton();
    }

    /**
     * 登录成功处理
     */
    private void loginSuccess(User user) {
        Toast.makeText(this, "登录成功！欢迎 " + user.getUsername(), Toast.LENGTH_LONG).show();

        // 保存登录状态（使用SharedPreferences）
        getSharedPreferences("user_prefs", MODE_PRIVATE)
                .edit()
                .putBoolean("is_logged_in", true)
                .putString("user_id", user.getId())
                .putString("username", user.getUsername())
                .putString("phone", user.getPhone())
                .apply();

        if(user.getMode()==User.Mode.NULL){
            Intent intent=new Intent(signin.this,RoleSelectionActivity.class);
            intent.putExtra("user",user);
            startActivity(intent);
            finish();
        }
        else if(user.getMode()==User.Mode.USER){
            startActivity(new Intent(signin.this, UserMainActivity.class));
            finish();
        }
        else {
            startActivity(new Intent(signin.this, LeaderMainActivity.class));
            finish();
        }
    }

    /**
     * 重置登录按钮
     */
    private void resetLoginButton() {
        btnLogin.setEnabled(true);
        btnLogin.setText("登录");
    }

    /**
     * 根据手机号查找用户（模拟后端查询）
     */
    private User findUserByPhone(String phone) {
        // TODO:根据号码查找用户
        return new User();
    }

    /**
     * 验证手机号格式
     */
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