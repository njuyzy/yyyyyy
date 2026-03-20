package com.example.Japp.Chat.fragment;

import static android.content.Context.MODE_PRIVATE;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.Japp.Chat.adapter.conversationListAdapter;
import com.example.Japp.Chat.chatActivity;
import com.example.Japp.R;
import com.example.Japp.data.Conversation;
import com.example.Japp.data.User;

import java.util.ArrayList;
import java.util.List;

public class ConversationList extends Fragment {

    private RecyclerView recycler;
    private conversationListAdapter adapter;
    private List<Conversation> conversationList;
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState){
        View view=inflater.inflate(R.layout.fragment_conversation,container,false);

        recycler= view.findViewById(R.id.recycler);
        conversationList=new ArrayList<>();

        if(requireActivity().getSharedPreferences("user_pref",MODE_PRIVATE).getString("Mode","USER").equals("USER")){
            //从本地数据库读取用户模式聊天记录
        }
        else ;//领队模式聊天记录

        //样例
        Conversation conversation=new Conversation();
        conversation.setUser_opposite(new User("yzy","111","111"));
        conversationList.add(new Conversation());
        conversationList.add(conversation);
        conversationList.add(new Conversation());
        conversationList.add(new Conversation());
        conversationList.add(new Conversation());
        //

        adapter=new conversationListAdapter();
        adapter.setListData(conversationList);
        adapter.setConversationOnClickListener(new conversationListAdapter.ConversationOnClickListener() {
            @Override
            public void onItemClick(int position) {
                Intent intent=new Intent(requireContext(), chatActivity.class);
                intent.putExtra("conversation_info",conversationList.get(position));
                startActivity(intent);
                conversationList.get(position).resetUnRead_num();
                adapter.notifyItemChanged(position);
            }
        });

        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);
        return view;
    }
}
