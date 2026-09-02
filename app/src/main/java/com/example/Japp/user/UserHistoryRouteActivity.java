package com.example.Japp.user;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.Japp.R;
import com.example.Japp.user.fragment.profile.historyRoute;
import com.example.Japp.util.DisplayCutoutAdapter;

public class UserHistoryRouteActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_history_route);
        DisplayCutoutAdapter.apply(this);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.historyRouteContainer, new historyRoute())
                    .commit();
        }
    }
}
