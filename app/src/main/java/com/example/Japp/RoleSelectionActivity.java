package com.example.Japp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.example.Japp.data.User;
import com.example.Japp.data.User.Mode;
import com.example.Japp.leader.LeaderMainActivity;
import com.example.Japp.user.UserMainActivity;

public class RoleSelectionActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_role_selection);

        User user=(User)getIntent().getSerializableExtra("user");

        CardView leader_mode=findViewById(R.id.cardLeader);
        CardView user_mode=findViewById(R.id.cardUser);

        leader_mode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                user.setMode(Mode.LEADER);
                //TODO:更新用户数据

                startActivity(new Intent(RoleSelectionActivity.this, LeaderMainActivity.class));
                finish();
            }
        });

        user_mode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                user.setMode(Mode.USER);
                //TODO:更新用户数据

                startActivity(new Intent(RoleSelectionActivity.this, UserMainActivity.class));
                finish();
            }
        });
    }
}
