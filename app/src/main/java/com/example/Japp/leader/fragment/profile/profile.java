package com.example.Japp.leader.fragment.profile;

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
import androidx.fragment.app.FragmentTransaction;

import com.example.Japp.R;
import com.example.Japp.SettingsActivity;
import com.example.Japp.user.UserMainActivity;

public class profile extends Fragment {

    private Button switchMode, changeImage, check_review;

    private ImageButton Settings;
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.leader_fragment_profile, container, false);

        switchMode = view.findViewById(R.id.btnSwitchRole);
        changeImage = view.findViewById(R.id.btnUploadAvatar);
        check_review = view.findViewById(R.id.btnViewReviews);
        Settings=view.findViewById(R.id.btnSettings);

        switchMode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchMode();
            }
        });

        check_review.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                review targetFragment = new review();
                FragmentTransaction transaction = getParentFragmentManager().beginTransaction();
                transaction.replace(R.id.container, targetFragment);
                transaction.addToBackStack(null);
                transaction.commit();
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
        SharedPreferences sharedPreferences = requireContext().getSharedPreferences("user_pref", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        editor.putString("Mode","USER").apply();

        Toast.makeText(requireContext(),"已切换到用户模式",Toast.LENGTH_SHORT).show();

        startActivity(new Intent(requireContext(), UserMainActivity.class));
        requireActivity().finish();
    }
}