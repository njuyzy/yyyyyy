package com.example.Japp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.example.Japp.leader.LeaderMainActivity;
import com.example.Japp.user.UserMainActivity;

public class RoleSelectionActivity extends AppCompatActivity {

    CardView cardUser,cardLeader;
    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_role_selection);

        cardUser=findViewById(R.id.cardUser);
        cardLeader=findViewById(R.id.cardLeader);

        cardUser.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getSharedPreferences("user_pref",MODE_PRIVATE).edit()
                        .putString("Mode","USER").apply();
                startActivity(new Intent(RoleSelectionActivity.this, UserMainActivity.class));
                finish();
            }
        });

        cardLeader.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getSharedPreferences("user_pref",MODE_PRIVATE).edit()
                        .putString("Mode","LEADER").apply();
                startActivity(new Intent(RoleSelectionActivity.this, LeaderMainActivity.class));
                finish();
            }
        });
    }
}
