package com.example.Japp.Chat.fragment;

import static android.content.Context.MODE_PRIVATE;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.Japp.Chat.adapter.conversationListAdapter;
import com.example.Japp.Chat.chatActivity;
import com.example.Japp.R;
import com.example.Japp.data.Conversation;
import com.example.Japp.data.Message;
import com.example.Japp.data.User;
import com.example.Japp.database.DatabaseManager;
import com.example.Japp.database.dao.ConversationDao;
import com.example.Japp.database.dao.MessageDao;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ConversationList extends Fragment {

    private RecyclerView recycler;
    private conversationListAdapter adapter;
    private List<Conversation> conversationList;

    private DatabaseManager dbManager;
    private ConversationDao conversationDao;
    private MessageDao messageDao;
    private String currentUserId;
    private User currentUser;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_conversation, container, false);

        recycler = view.findViewById(R.id.recycler);
        conversationList = new ArrayList<>();

        adapter = new conversationListAdapter();
        adapter.setListData(conversationList);

        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);

        initDatabase();
        initCurrentUser();
        setupClickListener();

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        loadConversationsFromDatabase();
    }

    private void initDatabase() {
        dbManager = DatabaseManager.getInstance(requireContext());
        conversationDao = new ConversationDao(dbManager);
        messageDao = new MessageDao(dbManager);
    }

    private void initCurrentUser() {
        String userInfo = requireActivity().getSharedPreferences("user_pref", MODE_PRIVATE)
                .getString("user_inf", "");

        currentUser = new User();
        if (!userInfo.isEmpty()) {
            currentUser.setId(currentUser.getId(userInfo));
            currentUser.setName(currentUser.getUsername(userInfo));
            currentUser.setPhone(currentUser.getPhone(userInfo));
        } else {
            currentUser.setId("default_user");
            currentUser.setName("我");
        }
        currentUserId = currentUser.getId();
    }

    private void setupClickListener() {
        adapter.setConversationOnClickListener(position -> {
            Conversation conv = conversationList.get(position);
            if (conv != null) {
                Intent intent = new Intent(requireContext(), chatActivity.class);
                intent.putExtra("conversation_info", conv);
                startActivity(intent);
            }
        });
    }

    private void loadConversationsFromDatabase() {
        executorService.execute(() -> {
            try {
                // 从数据库获取会话列表
                List<Conversation> dbConversations = conversationDao.getConversationsByUser(currentUserId);

                List<Conversation> loadedConversations = new ArrayList<>();

                if (dbConversations != null && !dbConversations.isEmpty()) {
                    for (Conversation conv : dbConversations) {
                        String conversationId = conversationDao.generateConversationId(
                                currentUserId,
                                conv.getUser_opposite().getId()
                        );

                        // 从数据库加载该会话的所有消息
                        List<Message> messages = messageDao.getMessagesByConversation(
                                conversationId,
                                currentUser,
                                conv.getUser_opposite()
                        );

                        // 清空并重新填充消息列表
                        conv.getMessages().clear();
                        for (Message msg : messages) {
                            conv.getMessages().add(msg.getContent());
                        }

                        loadedConversations.add(conv);
                    }
                }

                final List<Conversation> finalConversations = loadedConversations;
                mainHandler.post(() -> {
                    conversationList.clear();
                    if (!finalConversations.isEmpty()) {
                        conversationList.addAll(finalConversations);
                        Toast.makeText(requireContext(), "加载了 " + finalConversations.size() + " 个会话", Toast.LENGTH_SHORT).show();
                    } else {
                        loadSampleData();
                        Toast.makeText(requireContext(), "暂无会话，使用示例数据", Toast.LENGTH_SHORT).show();
                    }

                    if (adapter != null) {
                        adapter.setListData(conversationList);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> {
                    loadSampleData();
                    if (adapter != null) {
                        adapter.setListData(conversationList);
                    }
                });
            }
        });
    }

    private void loadSampleData() {
        conversationList.clear();

        Conversation conversation1 = new Conversation();
        conversation1.setUser_opposite(new User("张三", "13800000001", ""));
        conversation1.addMessage("你好，我想咨询一下路线");
        conversation1.setUnRead_num(2);
        conversationList.add(conversation1);

        Conversation conversation2 = new Conversation();
        conversation2.setUser_opposite(new User("李四", "13800000002", ""));
        conversation2.addMessage("什么时候出发？");
        conversation2.setUnRead_num(0);
        conversationList.add(conversation2);

        Conversation conversation3 = new Conversation();
        conversation3.setUser_opposite(new User("王五", "13800000003", ""));
        conversation3.addMessage("谢谢你的帮助");
        conversation3.setUnRead_num(1);
        conversationList.add(conversation3);
    }

    @Override
    public void onResume() {
        super.onResume();
        loadConversationsFromDatabase();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        recycler = null;
        adapter = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }
}