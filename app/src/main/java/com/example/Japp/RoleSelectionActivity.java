package com.example.Japp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.example.Japp.data.User.Mode;
import com.example.Japp.leader.LeaderMainActivity;
import com.example.Japp.user.UserMainActivity;

public class RoleSelectionActivity extends AppCompatActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_role_selection);


        CardView leader_mode=findViewById(R.id.cardLeader);
        CardView user_mode=findViewById(R.id.cardUser);

        leader_mode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(RoleSelectionActivity.this, LeaderMainActivity.class));
                //TODO:更改用户身份
                finish();
            }
        });

        user_mode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(RoleSelectionActivity.this, UserMainActivity.class));
                //TODO：更改用户身份
                finish();
            }
        });
    }
}
