package com.example.Japp.Chat.fragment;

import static android.content.Context.MODE_PRIVATE;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
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
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

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
        ImageButton btnAdd = view.findViewById(R.id.add);
        conversationList = new ArrayList<>();

        adapter = new conversationListAdapter();
        adapter.setListData(conversationList);

        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);

        initDatabase();
        initCurrentUser();
        setupClickListener();

        if (btnAdd != null) {
            btnAdd.setOnClickListener(v -> showChatActionsSheet());
        }

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
                openConversation(conv);
            }
        });
    }

    private void showChatActionsSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View content = LayoutInflater.from(requireContext()).inflate(R.layout.popup_chat_actions, null);
        dialog.setContentView(content);

        content.findViewById(R.id.btnAddFriend).setOnClickListener(v -> {
            dialog.dismiss();
            showAddFriendSheet();
        });
        content.findViewById(R.id.btnCreateGroup).setOnClickListener(v -> {
            dialog.dismiss();
            showCreateGroupSheet();
        });
        content.findViewById(R.id.btnJoinGroup).setOnClickListener(v -> {
            dialog.dismiss();
            showJoinGroupSheet();
        });
        dialog.show();
    }

    private void showAddFriendSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View content = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_friend, null);
        dialog.setContentView(content);

        TextInputEditText etName = content.findViewById(R.id.etFriendName);
        TextInputEditText etPhone = content.findViewById(R.id.etFriendPhone);

        content.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());
        content.findViewById(R.id.btnConfirm).setOnClickListener(v -> {
            String name = etName.getText() != null ? etName.getText().toString().trim() : "";
            String phone = etPhone.getText() != null ? etPhone.getText().toString().trim() : "";
            if (TextUtils.isEmpty(name)) {
                Toast.makeText(requireContext(), "请输入好友昵称", Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            addFriend(name, phone);
        });
        dialog.show();
    }

    private void showCreateGroupSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View content = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_create_group, null);
        dialog.setContentView(content);

        TextInputEditText etGroupName = content.findViewById(R.id.etGroupName);
        TextInputEditText etMembers = content.findViewById(R.id.etGroupMembers);

        content.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());
        content.findViewById(R.id.btnConfirm).setOnClickListener(v -> {
            String groupName = etGroupName.getText() != null ? etGroupName.getText().toString().trim() : "";
            String membersRaw = etMembers.getText() != null ? etMembers.getText().toString().trim() : "";
            if (TextUtils.isEmpty(groupName)) {
                Toast.makeText(requireContext(), "请输入群名称", Toast.LENGTH_SHORT).show();
                return;
            }
            List<String> members = parseMemberNames(membersRaw);
            dialog.dismiss();
            createGroup(groupName, members);
        });
        dialog.show();
    }

    private void showJoinGroupSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View content = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_join_group, null);
        dialog.setContentView(content);

        TextInputEditText etGroupId = content.findViewById(R.id.etGroupId);
        TextInputEditText etGroupName = content.findViewById(R.id.etGroupName);

        content.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());
        content.findViewById(R.id.btnConfirm).setOnClickListener(v -> {
            String groupCode = etGroupId.getText() != null ? etGroupId.getText().toString().trim() : "";
            String groupName = etGroupName.getText() != null ? etGroupName.getText().toString().trim() : "";
            if (TextUtils.isEmpty(groupCode)) {
                Toast.makeText(requireContext(), "请输入群号", Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            joinGroup(groupCode, groupName);
        });
        dialog.show();
    }

    private List<String> parseMemberNames(String raw) {
        if (TextUtils.isEmpty(raw)) {
            return new ArrayList<>();
        }
        String normalized = raw.replace("，", ",").replace("、", ",");
        return Arrays.stream(normalized.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    private void addFriend(String name, String phone) {
        User friend = new User();
        friend.setName(name);
        if (!TextUtils.isEmpty(phone)) {
            friend.setPhone(phone);
            friend.setId("friend_" + phone);
        } else {
            friend.setId("friend_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        }

        Conversation existing = conversationDao.getConversationByUsers(currentUserId, friend.getId());
        if (existing != null) {
            existing.setUser_me(currentUser);
            Toast.makeText(requireContext(), "已是好友，进入聊天", Toast.LENGTH_SHORT).show();
            openConversation(existing);
            return;
        }

        String welcome = "你们已成为好友，开始聊天吧";
        Conversation conversation = new Conversation();
        conversation.setUser_me(currentUser);
        conversation.setUser_opposite(friend);
        conversation.setUnRead_num(0);
        conversation.addMessage(welcome);

        executorService.execute(() -> {
            conversationDao.insertOrUpdateConversation(conversation, currentUserId);
            String conversationId = conversationDao.generateConversationId(currentUserId, friend.getId());
            messageDao.insertMessage(new Message(currentUser, welcome, System.currentTimeMillis()), conversationId);
            mainHandler.post(() -> {
                conversationList.add(0, conversation);
                if (adapter != null) {
                    adapter.setListData(conversationList);
                }
                Toast.makeText(requireContext(), "已添加好友：" + name, Toast.LENGTH_SHORT).show();
                openConversation(conversation);
            });
        });
    }

    private String generateGroupCode() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder code = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            int idx = (int) (Math.random() * alphabet.length());
            code.append(alphabet.charAt(idx));
        }
        return code.toString();
    }

    private String normalizeGroupId(String raw) {
        String code = raw.trim().toUpperCase().replace(" ", "");
        if (code.startsWith("GROUP_")) {
            return "group_" + code.substring("GROUP_".length());
        }
        if (code.startsWith("group_")) {
            return code;
        }
        return "group_" + code;
    }

    private String extractGroupCode(String groupId) {
        if (groupId != null && groupId.startsWith("group_")) {
            return groupId.substring("group_".length());
        }
        return groupId != null ? groupId : "";
    }

    private void createGroup(String groupName, List<String> members) {
        String groupCode = generateGroupCode();
        User groupPeer = new User();
        groupPeer.setId("group_" + groupCode);
        groupPeer.setName(groupName);

        Conversation conversation = new Conversation();
        conversation.setUser_me(currentUser);
        conversation.setUser_opposite(groupPeer);
        conversation.setGroup(true);
        conversation.setGroupName(groupName);
        conversation.setMemberNames(members);
        conversation.setUnRead_num(0);

        String welcome;
        if (members.isEmpty()) {
            welcome = "群聊「" + groupName + "」已创建，群号：" + groupCode;
        } else {
            welcome = "群聊「" + groupName + "」已创建，群号：" + groupCode
                    + "，成员：" + TextUtils.join("、", members);
        }
        conversation.addMessage(welcome);

        executorService.execute(() -> {
            conversationDao.insertOrUpdateConversation(conversation, currentUserId);
            String conversationId = conversationDao.generateConversationId(currentUserId, groupPeer.getId());
            messageDao.insertMessage(new Message(currentUser, welcome, System.currentTimeMillis()), conversationId);
            mainHandler.post(() -> {
                conversationList.add(0, conversation);
                if (adapter != null) {
                    adapter.setListData(conversationList);
                }
                Toast.makeText(requireContext(), "群聊已创建，群号：" + groupCode, Toast.LENGTH_LONG).show();
                openConversation(conversation);
            });
        });
    }

    private void joinGroup(String rawGroupCode, String optionalName) {
        String groupId = normalizeGroupId(rawGroupCode);
        String groupCode = extractGroupCode(groupId);

        Conversation existing = conversationDao.getConversationByUsers(currentUserId, groupId);
        if (existing != null) {
            existing.setUser_me(currentUser);
            existing.setGroup(true);
            if (existing.getUser_opposite() != null) {
                existing.setGroupName(existing.getUser_opposite().getUsername());
            }
            Toast.makeText(requireContext(), "你已在该群中", Toast.LENGTH_SHORT).show();
            openConversation(existing);
            return;
        }

        String displayName = !TextUtils.isEmpty(optionalName) ? optionalName : ("群聊 " + groupCode);
        User groupPeer = new User();
        groupPeer.setId(groupId);
        groupPeer.setName(displayName);

        String myName = currentUser.getUsername() != null ? currentUser.getUsername() : "我";
        String welcome = myName + " 加入了群聊「" + displayName + "」";

        Conversation conversation = new Conversation();
        conversation.setUser_me(currentUser);
        conversation.setUser_opposite(groupPeer);
        conversation.setGroup(true);
        conversation.setGroupName(displayName);
        conversation.setUnRead_num(0);
        conversation.addMessage(welcome);

        executorService.execute(() -> {
            conversationDao.insertOrUpdateConversation(conversation, currentUserId);
            String conversationId = conversationDao.generateConversationId(currentUserId, groupId);
            messageDao.insertMessage(new Message(currentUser, welcome, System.currentTimeMillis()), conversationId);
            mainHandler.post(() -> {
                conversationList.add(0, conversation);
                if (adapter != null) {
                    adapter.setListData(conversationList);
                }
                Toast.makeText(requireContext(), "已加入群聊", Toast.LENGTH_SHORT).show();
                openConversation(conversation);
            });
        });
    }

    private void openConversation(Conversation conversation) {
        Intent intent = new Intent(requireContext(), chatActivity.class);
        intent.putExtra("conversation_info", conversation);
        startActivity(intent);
    }

    private void loadConversationsFromDatabase() {
        executorService.execute(() -> {
            try {
                List<Conversation> dbConversations = conversationDao.getConversationsByUser(currentUserId);
                List<Conversation> loadedConversations = new ArrayList<>();

                if (dbConversations != null && !dbConversations.isEmpty()) {
                    for (Conversation conv : dbConversations) {
                        String conversationId = conversationDao.generateConversationId(
                                currentUserId,
                                conv.getUser_opposite().getId()
                        );

                        List<Message> messages = messageDao.getMessagesByConversation(
                                conversationId,
                                currentUser,
                                conv.getUser_opposite()
                        );

                        conv.getMessages().clear();
                        for (Message msg : messages) {
                            conv.getMessages().add(msg.getContent());
                        }

                        // 从本地 ID 前缀恢复群聊标记
                        if (conv.getUser_opposite() != null
                                && conv.getUser_opposite().getId() != null
                                && conv.getUser_opposite().getId().startsWith("group_")) {
                            conv.setGroup(true);
                            conv.setGroupName(conv.getUser_opposite().getUsername());
                        }

                        loadedConversations.add(conv);
                    }
                }

                if (loadedConversations.isEmpty()) {
                    Conversation sample = createAndPersistSampleConversation();
                    if (sample != null) {
                        loadedConversations.add(sample);
                    }
                }

                final List<Conversation> finalConversations = loadedConversations;
                mainHandler.post(() -> {
                    conversationList.clear();
                    conversationList.addAll(finalConversations);
                    if (adapter != null) {
                        adapter.setListData(conversationList);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> {
                    conversationList.clear();
                    if (adapter != null) {
                        adapter.setListData(conversationList);
                    }
                });
            }
        });
    }

    /** 首次进入时写入一条稳定示例会话，避免发消息后其它内存示例消失 */
    private Conversation createAndPersistSampleConversation() {
        User peer = new User();
        peer.setId("friend_sample_zhangsan");
        peer.setName("张三");
        peer.setPhone("13800000001");

        String welcome = "你好，我想咨询一下路线";
        Conversation conversation = new Conversation();
        conversation.setUser_me(currentUser);
        conversation.setUser_opposite(peer);
        conversation.setUnRead_num(0);
        conversation.addMessage(welcome);

        try {
            conversationDao.insertOrUpdateConversation(conversation, currentUserId);
            String conversationId = conversationDao.generateConversationId(currentUserId, peer.getId());
            messageDao.insertMessage(new Message(peer, welcome, System.currentTimeMillis()), conversationId);
            return conversation;
        } catch (Exception e) {
            e.printStackTrace();
            return conversation;
        }
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
