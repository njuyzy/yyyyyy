package com.example.Japp.leader;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.Lifecycle;

import com.example.Japp.R;
import com.example.Japp.Chat.fragment.ConversationList;
import com.example.Japp.leader.fragment.order.orderList;
import com.example.Japp.leader.fragment.profile.profile;
import com.example.Japp.util.DisplayCutoutAdapter;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class LeaderMainActivity extends AppCompatActivity {

    private static final String STATE_POSITION = "leader_main_position";
    private static final String TAG_ORDERS = "leader_orders";
    private static final String TAG_MESSAGES = "leader_messages";
    private static final String TAG_PROFILE = "leader_profile";

    private orderList orderList;
    private profile mine;

    private ConversationList conversationList;
    int position;

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.leader_activity_main);
        DisplayCutoutAdapter.apply(this);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        BottomNavigationView bottomNavigationView = findViewById(R.id.LeaderBottomNav);
        restoreFragments();

        bottomNavigationView.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {
                if(menuItem.getItemId()==R.id.orders){
                    position=0;
                }
                else if(menuItem.getItemId()==R.id.messages){
                    position=1;
                }
                else{
                    position=2;
                }
                selectedFragment(position);
                return true;
            }
        });

        position = savedInstanceState != null ? savedInstanceState.getInt(STATE_POSITION, 0) : 0;
        selectedFragment(position);
    }

    private void restoreFragments() {
        for (Fragment fragment : getSupportFragmentManager().getFragments()) {
            if (fragment instanceof orderList) {
                orderList = (orderList) fragment;
            } else if (fragment instanceof ConversationList) {
                conversationList = (ConversationList) fragment;
            } else if (fragment instanceof profile) {
                mine = (profile) fragment;
            }
        }
    }

    private void selectedFragment(int position){
        FragmentTransaction fragmentTransaction=getSupportFragmentManager().beginTransaction();
        hideFragment(fragmentTransaction);

        if(position==0){

            if(orderList==null){
                orderList=new orderList();
                fragmentTransaction.add(R.id.container,orderList,TAG_ORDERS);
            } else {
                fragmentTransaction.show(orderList);
            }
            fragmentTransaction.setMaxLifecycle(orderList, Lifecycle.State.RESUMED);

        }
        else if(position==1){

            if(conversationList==null){
                conversationList=new ConversationList();
                fragmentTransaction.add(R.id.container,conversationList,TAG_MESSAGES);
            } else{
                fragmentTransaction.show(conversationList);
            }
            fragmentTransaction.setMaxLifecycle(conversationList, Lifecycle.State.RESUMED);
        }
        else {

            if(mine==null){
                mine=new profile();
                fragmentTransaction.add(R.id.container,mine,TAG_PROFILE);
            }else{
                fragmentTransaction.show(mine);
            }
            fragmentTransaction.setMaxLifecycle(mine, Lifecycle.State.RESUMED);
        }

        fragmentTransaction.commit();
    }

    private void hideFragment(FragmentTransaction fragmentTransaction){
        if(orderList!=null){
            fragmentTransaction.hide(orderList);
            fragmentTransaction.setMaxLifecycle(orderList, Lifecycle.State.STARTED);
        }
        if(conversationList!=null){
            fragmentTransaction.hide(conversationList);
            fragmentTransaction.setMaxLifecycle(conversationList, Lifecycle.State.STARTED);
        }
        if(mine!=null){
            fragmentTransaction.hide(mine);
            fragmentTransaction.setMaxLifecycle(mine, Lifecycle.State.STARTED);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putInt(STATE_POSITION, position);
        super.onSaveInstanceState(outState);
    }
}
