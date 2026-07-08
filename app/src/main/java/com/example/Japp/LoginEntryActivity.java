package com.example.Japp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

public class LoginEntryActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login_entry);

        MaterialButton btnPasswordLogin = findViewById(R.id.btnPasswordLogin);
        MaterialButton btnCodeLogin = findViewById(R.id.btnCodeLogin);
        TextView txtGoRegister = findViewById(R.id.txtGoRegister);

        btnPasswordLogin.setOnClickListener(v ->
                startActivity(new Intent(LoginEntryActivity.this, MainActivity.class)));
        btnCodeLogin.setOnClickListener(v ->
                startActivity(new Intent(LoginEntryActivity.this, CodeLoginActivity.class)));
        txtGoRegister.setOnClickListener(v ->
                startActivity(new Intent(LoginEntryActivity.this, signup.class)));
    }
}
