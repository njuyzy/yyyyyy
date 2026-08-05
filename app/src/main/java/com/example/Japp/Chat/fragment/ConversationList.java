package com.example.Japp.Chat.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.Japp.Chat.adapter.conversationListAdapter;
import com.example.Japp.Chat.chatActivity;
import com.example.Japp.Chat.util.ChatUnreadManager;
import com.example.Japp.R;
import com.example.Japp.data.Conversation;
import com.example.Japp.data.User;
import com.example.Japp.network.ApiClient;
import com.example.Japp.network.api.UserService;
import com.example.Japp.network.models.Account;
import com.example.Japp.network.models.ChatSession;
import com.example.Japp.network.models.ChatGroupMember;
import com.example.Japp.network.models.Project;
import com.example.Japp.network.models.Result;
import com.example.Japp.network.models.ServerChatMessage;
import com.example.Japp.user.util.SessionHelper;
import com.example.Japp.user.util.ProjectUiHelper;
import com.example.Japp.util.InsetDividerDecoration;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ConversationList extends Fragment {

    public interface UnreadCountHost {
        void updateChatUnreadBadge(int unreadCount);
    }

    private RecyclerView recycler;
    private TextView emptyState;
    private conversationListAdapter adapter;
    private final List<Conversation> conversationList = new ArrayList<>();
    private UserService service;
    private int currentAccountId;
    private int pendingSessionChecks;
    private User currentUser;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_conversation, container, false);
        recycler = view.findViewById(R.id.recycler);
        emptyState = view.findViewById(R.id.txtEmptyState);

        adapter = new conversationListAdapter();
        adapter.setListData(conversationList);
        adapter.setConversationOnClickListener(position -> {
            if (position >= 0 && position < conversationList.size()) {
                openConversation(conversationList.get(position));
            }
        });

        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);
        recycler.addItemDecoration(new InsetDividerDecoration(requireContext(), 76, 16));

        service = ApiClient.getClient().create(UserService.class);
        initCurrentUser();
        return view;
    }

    private void initCurrentUser() {
        currentAccountId = SessionHelper.getAccountId(requireContext());
        currentUser = new User();
        currentUser.setId(String.valueOf(currentAccountId));
        currentUser.setName("我");

        if (currentAccountId > 0) {
            service.getAccount(currentAccountId).enqueue(new Callback<Result<Account>>() {
                @Override
                public void onResponse(Call<Result<Account>> call, Response<Result<Account>> response) {
                    if (response.isSuccessful() && response.body() != null
                            && response.body().getCode() == 1 && response.body().getData() != null) {
                        currentUser.setName(response.body().getData().getUsername());
                    }
                }

                @Override
                public void onFailure(Call<Result<Account>> call, Throwable t) {
                    // 名称加载失败不影响会话使用。
                }
            });
        }
    }

    private void loadConversations() {
        if (currentAccountId <= 0 || !SessionHelper.isLoggedIn(requireContext())) {
            showEmptyState("登录后查看项目会话");
            return;
        }

        service.getChatSessions().enqueue(new Callback<Result<List<ChatSession>>>() {
            @Override
            public void onResponse(Call<Result<List<ChatSession>>> call,
                                   Response<Result<List<ChatSession>>> response) {
                if (!isAdded()) {
                    return;
                }
                if (response.code() == 401) {
                    SessionHelper.handleUnauthorized(requireContext());
                    return;
                }
                if (!response.isSuccessful() || response.body() == null
                        || response.body().getCode() != 1) {
                    showEmptyState("会话加载失败，请稍后重试");
                    return;
                }

                List<ChatSession> sessions = response.body().getData();
                conversationList.clear();
                adapter.setListData(conversationList);
                if (sessions == null || sessions.isEmpty()) {
                    updateEmptyState();
                    return;
                }
                showEmptyState("正在加载项目会话…");
                for (ChatSession session : sessions) {
                    Conversation conversation = buildConversation(session);
                    conversationList.add(conversation);
                    if (session.getLatestMessage() != null
                            && !session.getLatestMessage().trim().isEmpty()) {
                        conversation.addMessage(session.getLatestMessage());
                    }
                    loadConversationDetails(conversation);
                }
                adapter.setListData(conversationList);
                updateEmptyState();
            }

            @Override
            public void onFailure(Call<Result<List<ChatSession>>> call, Throwable t) {
                if (isAdded()) {
                    showEmptyState("网络异常，会话加载失败");
                }
            }
        });
    }

    private void validateAndAddConversation(ChatSession session) {
        service.getProject(session.getProjectId()).enqueue(new Callback<Result<Project>>() {
            @Override
            public void onResponse(Call<Result<Project>> call, Response<Result<Project>> response) {
                if (!isAdded() || adapter == null || !response.isSuccessful()
                        || response.body() == null || response.body().getCode() != 1
                        || response.body().getData() == null) {
                    finishSessionValidation();
                    return;
                }

                Project project = response.body().getData();
                Integer assignedLeaderId = project.getLeaderAccountId();
                String status = ProjectUiHelper.normalizeStatus(project.getStatus());
                boolean isPublisherLeaderSession = project.getOwnerAccountId() == session.getUserAccountId()
                        && assignedLeaderId != null
                        && assignedLeaderId == session.getLeaderAccountId()
                        && !ProjectUiHelper.STATUS_DONE.equals(status)
                        && !ProjectUiHelper.STATUS_CANCELLED.equals(status);
                if (!isPublisherLeaderSession) {
                    finishSessionValidation();
                    return;
                }

                Conversation conversation = buildConversation(session);
                conversationList.add(conversation);
                adapter.setListData(conversationList);
                updateEmptyState();
                loadConversationDetails(conversation);
                finishSessionValidation();
            }

            @Override
            public void onFailure(Call<Result<Project>> call, Throwable t) {
                // 项目校验失败时不展示来源不明的旧会话。
                finishSessionValidation();
            }
        });
    }

    private void finishSessionValidation() {
        pendingSessionChecks = Math.max(0, pendingSessionChecks - 1);
        if (pendingSessionChecks == 0 && conversationList.isEmpty()) {
            updateEmptyState();
        }
    }

    private Conversation buildConversation(ChatSession session) {
        User peer = new User();
        peer.setId("0");
        peer.setName("项目群成员");

        Conversation conversation = new Conversation();
        conversation.setUser_me(currentUser);
        conversation.setUser_opposite(peer);
        conversation.setBackendSessionId(session.getId());
        conversation.setProjectId(session.getProjectId());
        conversation.setGroup(true);
        String title = session.getProjectTitle();
        conversation.setGroupName(title == null || title.trim().isEmpty()
                ? "项目群聊" : title);
        conversation.setUnRead_num(0);
        return conversation;
    }

    private void loadConversationDetails(Conversation conversation) {
        service.getChatMembers(conversation.getBackendSessionId())
                .enqueue(new Callback<Result<List<ChatGroupMember>>>() {
                    @Override
                    public void onResponse(Call<Result<List<ChatGroupMember>>> call,
                                           Response<Result<List<ChatGroupMember>>> response) {
                        if (!isAdded() || adapter == null || !response.isSuccessful()
                                || response.body() == null || response.body().getCode() != 1) {
                            return;
                        }
                        List<String> members = new ArrayList<>();
                        List<ChatGroupMember> data = response.body().getData();
                        if (data != null) {
                            for (ChatGroupMember member : data) {
                                String name = member.getUsername() == null
                                        ? "群成员" : member.getUsername();
                                String representation = member.getRepresentationText();
                                boolean isLeader = "LEADER".equalsIgnoreCase(member.getMemberRole())
                                        || "ADMIN".equalsIgnoreCase(member.getMemberRole());
                                members.add(isLeader || representation == null
                                        || representation.trim().isEmpty()
                                        ? name : name + "（" + representation + "）");
                            }
                        }
                        conversation.setMemberNames(members);
                        adapter.notifyDataSetChanged();
                    }

                    @Override
                    public void onFailure(Call<Result<List<ChatGroupMember>>> call, Throwable t) {
                        // 成员信息加载失败不影响聊天。
                    }
                });

        service.getChatMessages(conversation.getBackendSessionId())
                .enqueue(new Callback<Result<List<ServerChatMessage>>>() {
                    @Override
                    public void onResponse(Call<Result<List<ServerChatMessage>>> call,
                                           Response<Result<List<ServerChatMessage>>> response) {
                        if (!isAdded() || adapter == null || !response.isSuccessful()
                                || response.body() == null || response.body().getCode() != 1) {
                            return;
                        }
                        List<ServerChatMessage> messages = response.body().getData();
                        conversation.getMessages().clear();
                        if (messages != null && !messages.isEmpty()) {
                            ServerChatMessage last = messages.get(messages.size() - 1);
                            conversation.addMessage(last.getContent());
                        }
                        conversation.setLatestMessageId(
                                ChatUnreadManager.latestMessageId(messages));
                        conversation.setUnRead_num(ChatUnreadManager.calculateUnread(
                                requireContext(),
                                currentAccountId,
                                conversation.getBackendSessionId(),
                                messages));
                        sortConversations();
                        adapter.notifyDataSetChanged();
                        notifyUnreadCount();
                    }

                    @Override
                    public void onFailure(Call<Result<List<ServerChatMessage>>> call, Throwable t) {
                        // 列表仍可进入会话。
                    }
                });
    }

    private void openConversation(Conversation conversation) {
        if (conversation == null || conversation.getBackendSessionId() <= 0) {
            Toast.makeText(requireContext(), "会话信息无效", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(requireContext(), chatActivity.class);
        intent.putExtra("conversation_info", conversation);
        startActivity(intent);
    }

    private void sortConversations() {
        conversationList.sort((left, right) -> {
            boolean leftUnread = left.getUnRead_num() > 0;
            boolean rightUnread = right.getUnRead_num() > 0;
            if (leftUnread != rightUnread) {
                return leftUnread ? -1 : 1;
            }
            return Long.compare(right.getLatestMessageId(), left.getLatestMessageId());
        });
    }

    private void notifyUnreadCount() {
        int totalUnread = 0;
        for (Conversation conversation : conversationList) {
            totalUnread += conversation.getUnRead_num();
        }
        if (getActivity() instanceof UnreadCountHost) {
            ((UnreadCountHost) getActivity()).updateChatUnreadBadge(totalUnread);
        }
    }

    private void updateEmptyState() {
        if (emptyState == null || recycler == null) {
            return;
        }
        boolean empty = conversationList.isEmpty();
        emptyState.setText("发布、加入拼单或领队接单后，将自动出现项目群聊");
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        recycler.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (empty) {
            notifyUnreadCount();
        }
    }

    private void showEmptyState(String message) {
        conversationList.clear();
        if (adapter != null) {
            adapter.setListData(conversationList);
        }
        if (emptyState != null) {
            emptyState.setText(message);
            emptyState.setVisibility(View.VISIBLE);
        }
        if (recycler != null) {
            recycler.setVisibility(View.GONE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        loadConversations();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        recycler = null;
        emptyState = null;
        adapter = null;
    }
}
