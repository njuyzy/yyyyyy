package com.example.Japp.user.fragment.profile;

import static android.content.Context.MODE_PRIVATE;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.example.Japp.R;
import com.example.Japp.RoleSelectionActivity;
import com.example.Japp.SettingsActivity;
import com.example.Japp.leader.LeaderMainActivity;

public class profile extends Fragment {

    private Button switchMode, changeImage, check_historyOrder;
    private ImageButton Settings;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.user_fragment_profile, container, false);

        switchMode = view.findViewById(R.id.btnSwitchRole);
        changeImage = view.findViewById(R.id.btnUploadAvatar);
        check_historyOrder = view.findViewById(R.id.btnViewHistory);
        Settings=view.findViewById(R.id.btnSettings);

        if (!canSwitchRole()) {
            switchMode.setVisibility(View.GONE);
        }

        switchMode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchMode();
            }
        });

        Settings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(requireContext(), SettingsActivity.class));
            }
        });
        return view;
    }

    private void switchMode() {
        if (!canSwitchRole()) {
            Toast.makeText(requireContext(), "当前身份不可切换", Toast.LENGTH_SHORT).show();
            return;
        }
        SharedPreferences sharedPreferences = requireContext().getSharedPreferences("user_pref", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        editor.putString("Mode","LEADER").apply();

        Toast.makeText(requireContext(),"已切换到领队模式",Toast.LENGTH_SHORT).show();

        startActivity(new Intent(requireContext(), LeaderMainActivity.class));
        requireActivity().finish();
    }

    private boolean canSwitchRole() {
        SharedPreferences sharedPreferences = requireContext().getSharedPreferences("user_pref", MODE_PRIVATE);
        String scope = sharedPreferences.getString(RoleSelectionActivity.ROLE_SCOPE, "");
        return RoleSelectionActivity.ROLE_SCOPE_BOTH.equals(scope);
    }
}