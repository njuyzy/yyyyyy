package com.example.Japp.Chat;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.example.Japp.Chat.adapter.chatAdapter;
import com.example.Japp.R;
import com.example.Japp.data.Conversation;
import com.google.android.material.appbar.MaterialToolbar;

public class chatActivity extends AppCompatActivity {

    private Conversation conversation;
    private RecyclerView recycler;
    private chatAdapter adapter;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        conversation=(Conversation) getIntent().getSerializableExtra("conversation_info");
        setOppositeName();
        recycler=findViewById(R.id.recycler);

    }
    private void setOppositeName(){
        MaterialToolbar title=findViewById(R.id.toolbar);
        title.setTitle(conversation.getUser_opposite().getUsername());
    }
}
