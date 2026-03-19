package com.example.Japp.leader;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentTransaction;

import com.example.Japp.R;
import com.example.Japp.leader.fragment.chat.ConversationList;
import com.example.Japp.leader.fragment.order.orderList;
import com.example.Japp.leader.fragment.profile.profile;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class LeaderMainActivity extends AppCompatActivity {

    private orderList orderList;
    private profile mine;

    private ConversationList conversationList;
    int position;

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.leader_activity_main);

        BottomNavigationView bottomNavigationView = findViewById(R.id.LeaderBottomNav);

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

        selectedFragment(0);
    }

    private void selectedFragment(int position){
        FragmentTransaction fragmentTransaction=getSupportFragmentManager().beginTransaction();
        hideFragment(fragmentTransaction);

        if(position==0){

            if(orderList==null){
                orderList=new orderList();
                fragmentTransaction.add(R.id.container,orderList);
            } else {
                fragmentTransaction.show(orderList);
            }

        }
        else if(position==1){

            if(conversationList==null){
                conversationList=new ConversationList();
                fragmentTransaction.add(R.id.container,conversationList);
            } else{
                fragmentTransaction.show(conversationList);
            }
        }
        else {

            if(mine==null){
                mine=new profile();
                fragmentTransaction.add(R.id.container,mine);
            }else{
                fragmentTransaction.show(mine);
            }
        }

        fragmentTransaction.commit();
    }

    private void hideFragment(FragmentTransaction fragmentTransaction){
        if(orderList!=null){
            fragmentTransaction.hide(orderList);
        }
        if(conversationList!=null){
            fragmentTransaction.hide(conversationList);
        }
        if(mine!=null){
            fragmentTransaction.hide(mine);
        }
    }
}
