package com.example.Japp.user;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentTransaction;

import com.example.Japp.R;
import com.example.Japp.Chat.fragment.ConversationList;
import com.example.Japp.user.fragment.joinTeam.TeamList;
import com.example.Japp.user.fragment.profile.profile;
import com.example.Japp.user.fragment.route.routeDesign;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class UserMainActivity extends AppCompatActivity {

    private routeDesign routeDesign;
    private TeamList teamList;
    private profile mine;

    private ConversationList conversationList;
    int position;

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.user_activity_main);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        BottomNavigationView bottomNavigationView = findViewById(R.id.UserBottomNav);

        bottomNavigationView.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {
                if(menuItem.getItemId()==R.id.route){
                    position=0;
                }
                else if(menuItem.getItemId()==R.id.share){
                    position=1;
                }
                else if(menuItem.getItemId()==R.id.messages){
                    position=2;
                }
                else{
                    position=3;
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

            if(routeDesign==null){
                routeDesign=new routeDesign();
                fragmentTransaction.add(R.id.container,routeDesign);
            } else {
                fragmentTransaction.show(routeDesign);
            }

        }
        else if(position==1){

            if(teamList==null){
                teamList=new TeamList();
                fragmentTransaction.add(R.id.container,teamList);
            }else{
                fragmentTransaction.show(teamList);
            }

        }
        else if(position==2){

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
        if(teamList!=null){
            fragmentTransaction.hide(teamList);
        }
        if(routeDesign!=null){
            fragmentTransaction.hide(routeDesign);
        }
        if(conversationList!=null){
            fragmentTransaction.hide(conversationList);
        }
        if(mine!=null){
            fragmentTransaction.hide(mine);
        }
    }
}
