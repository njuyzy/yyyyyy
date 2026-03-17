package com.example.Japp;

import static com.example.Japp.signin.isValidPhone;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.Japp.data.User;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Objects;

public class signup extends AppCompatActivity {

    private TextInputEditText etName,etPhone,etPassword,etCode;

    private TextInputLayout tilName,tilPhone,tilPassword,tilCode;

    private Button getCode,Signup,cancel;

    private int countdown = 60;
    private Handler handler = new Handler();
    private Runnable countdownRunnable;

    private String savedCode;
    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        initialize();
        setupListeners();
    }

    private void initialize(){

        tilName=findViewById(R.id.usernameLayout);
        tilPhone=findViewById(R.id.phoneLayout);
        tilPassword=findViewById(R.id.passwordLayout);
        tilCode=findViewById(R.id.codeLayout);

        etName=findViewById(R.id.username);
        etPhone=findViewById(R.id.phone_num);
        etPassword=findViewById(R.id.password);
        etCode=findViewById(R.id.code);

        getCode=findViewById(R.id.get_code);
        Signup=findViewById(R.id.register);
        cancel=findViewById(R.id.cancel);
    }

    private void setupListeners(){
        getCode.setOnClickListener(new View.OnClickListener() {
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

        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        Signup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String name = Objects.requireNonNull(etName.getText()).toString().trim();
                String phone = Objects.requireNonNull(etPhone.getText()).toString().trim();
                String code = Objects.requireNonNull(etCode.getText()).toString().trim();
                String password=Objects.requireNonNull(etPassword.getText()).toString().trim();

                if (TextUtils.isEmpty(name)) {
                    tilPhone.setError("用户名不能为空");
                    Toast.makeText(signup.this, "用户名不能为空", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (TextUtils.isEmpty(phone)) {
                    tilPhone.setError("手机号不能为空");
                    Toast.makeText(signup.this, "手机号不能为空", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (TextUtils.isEmpty(code)) {
                    tilPhone.setError("验证码不能为空");
                    Toast.makeText(signup.this, "验证码不能为空", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (TextUtils.isEmpty(password)) {
                    tilPhone.setError("密码不能为空");
                    Toast.makeText(signup.this, "密码不能为空", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (!isValidPhone(phone)) {
                    tilPhone.setError("请输入正确的11位手机号");
                    Toast.makeText(signup.this, "请输入正确的手机号", Toast.LENGTH_SHORT).show();
                    return;
                }

                if(NameExists(name)) {
                    tilName.setError("用户名已存在");
                    Toast.makeText(signup.this, "用户名已存在", Toast.LENGTH_SHORT).show();
                    return;
                }

                if(name.length()>20){
                    Toast.makeText(signup.this, "用户名长度不能超过20", Toast.LENGTH_SHORT).show();
                    return;
                }

                if(password.length()>20){
                    Toast.makeText(signup.this, "密码长度不能超过20", Toast.LENGTH_SHORT).show();
                    return;
                }

                if(PhoneExists(phone)){
                    Toast.makeText(signup.this,"该手机号已注册",Toast.LENGTH_SHORT).show();
                    return;
                }

                performRegister(name,phone,password,code);
            }
        });
    }

    private void performRegister(String name,String phone,String password,String code){
        if(!code.equals(savedCode)){
            Toast.makeText(signup.this,"验证码错误",Toast.LENGTH_SHORT).show();
            return;
        }
        User user=new User(name,phone,password);
        Intent intent=new Intent(signup.this,RoleSelectionActivity.class);
        intent.putExtra("user",user);
        startActivity(intent);
        finish();
    }
    private boolean PhoneExists(String phone){
        //TODO:检查数据库中是否有相同phone
        return false;
    }
    private boolean NameExists(String name){
        //TODO:检查数据库中是否有相同name
        return false;
    }
    private void sendVerificationCode(String phone) {

        Toast.makeText(this, "验证码已发送", Toast.LENGTH_LONG).show();
        startCountdown();
        savedCode="111111";//TODO:发送验证码，用savedCode记录
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
