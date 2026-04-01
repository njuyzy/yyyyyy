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

import com.example.Japp.Chat.adapter.ConversationListAdapter;
import com.example.Japp.Chat.chatActivity;
import com.example.Japp.Chat.utils.ChatStorageHelper;
import com.example.Japp.R;
import com.example.Japp.data.Conversation;
import com.example.Japp.data.User;

import java.util.ArrayList;
import java.util.List;

public class ConversationList extends Fragment {

    private RecyclerView recycler;
    private ConversationListAdapter adapter;
    private List<Conversation> conversationList;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState){
        View view=inflater.inflate(R.layout.fragment_conversation,container,false);

        recycler= view.findViewById(R.id.recycler);
        conversationList=new ArrayList<>();

        // 加载会话列表
        loadConversations();

        adapter=new ConversationListAdapter();
        adapter.setListData(conversationList);
        adapter.setConversationOnClickListener(new ConversationListAdapter.ConversationOnClickListener() {
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

    private void loadConversations() {
        // 检查当前用户
        String userInfo = requireActivity().getSharedPreferences("user_pref", MODE_PRIVATE)
                .getString("user_inf", "");

        if (userInfo.isEmpty()) {
            // 如果没有用户信息，加载示例数据
            loadSampleConversations();
        } else {
            // 从 ChatStorageHelper 加载会话
            ChatStorageHelper storageHelper = new ChatStorageHelper(requireContext());
            String currentUserId = getCurrentUserId(userInfo);

            storageHelper.loadConversations(new ChatStorageHelper.LoadCallback() {
                @Override
                public void onLoadComplete(List<Conversation> conversations) {
                    // 过滤出当前用户参与的会话
                    conversationList.clear();
                    if (conversations != null) {
                        for (Conversation conv : conversations) {
                            // 检查会话是否包含当前用户
                            if (isConversationRelevant(conv, currentUserId)) {
                                conversationList.add(conv);
                            }
                        }
                    }

                    // 如果没有会话，加载示例
                    if (conversationList.isEmpty()) {
                        loadSampleConversations();
                    } else {
                        adapter.notifyDataSetChanged();
                    }
                }
            });
        }
    }

    private boolean isConversationRelevant(Conversation conv, String currentUserId) {
        // 检查会话是否与当前用户相关
        User userMe = conv.getUser_me();
        User userOpposite = conv.getUser_opposite();

        if (userMe != null && currentUserId.equals(userMe.getId())) {
            return true;
        }
        if (userOpposite != null && currentUserId.equals(userOpposite.getId())) {
            return true;
        }

        // 如果没有设置用户信息，默认显示
        return userMe == null && userOpposite == null;
    }

    private String getCurrentUserId(String userInfo) {
        if (userInfo != null && userInfo.contains("id:")) {
            return userInfo.split(" ")[0].split(":")[1];
        }
        return "unknown";
    }

    private void loadSampleConversations() {
        // 示例数据（用于演示）
        User me = new User();
        me.setId("current_user");
        me.setUsername("我");

        User[] sampleUsers = {
                new User("张三", "13800138001", ""),
                new User("李四", "13800138002", ""),
                new User("王五", "13800138003", ""),
                new User("赵六", "13800138004", ""),
                new User("钱七", "13800138005", "")
        };

        for (int i = 0; i < 5; i++) {
            Conversation conv = new Conversation();
            conv.setUser_me(me);
            conv.setUser_opposite(sampleUsers[i]);
            conversationList.add(conv);
        }
    }
}
