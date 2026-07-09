package com.example.Japp.Chat.adapter;

import android.annotation.SuppressLint;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.Japp.R;
import com.example.Japp.data.Conversation;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class conversationListAdapter extends RecyclerView.Adapter<conversationListAdapter.Holder> {

    private List<Conversation> conversationList;
    private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("HH:mm", Locale.getDefault());

    public void setListData(List<Conversation> conversationList) {
        this.conversationList = conversationList;
        notifyDataSetChanged();
    }

    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.fragment_conversation_card, parent, false);
        return new Holder(view);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        if (conversationList == null || position >= conversationList.size()) {
            return;
        }

        Conversation conversation = conversationList.get(position);
        if (conversation == null) {
            return;
        }

        List<String> messages = conversation.getMessages();
        String displayName = conversation.getDisplayName();
        holder.UserName.setText(displayName);

        if (holder.txtAvatar != null) {
            holder.txtAvatar.setText(initialOf(displayName));
        }
        if (holder.avatarContainer != null) {
            holder.avatarContainer.setBackgroundResource(
                    conversation.isGroup()
                            ? R.drawable.bg_chat_avatar_group
                            : R.drawable.bg_chat_avatar_peer
            );
        }

        if (messages != null && !messages.isEmpty()) {
            String lastMessage = messages.get(messages.size() - 1);
            holder.LatestMessage.setText(lastMessage != null ? lastMessage : "");
        } else if (conversation.isGroup()) {
            int memberCount = conversation.getMemberNames().size();
            holder.LatestMessage.setText(memberCount > 0
                    ? "群聊已创建，共 " + memberCount + " 人"
                    : "群聊已创建");
        } else {
            holder.LatestMessage.setText("暂无消息");
        }

        holder.txtTime.setText(TIME_FMT.format(new Date()));

        int unreadCount = conversation.getUnRead_num();
        if (unreadCount > 0) {
            holder.UnreadMessage.setVisibility(View.VISIBLE);
            String unreadText = unreadCount > 99 ? "99+" : String.valueOf(unreadCount);
            holder.UnreadMessage.setText(unreadText);
        } else {
            holder.UnreadMessage.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (conversationOnClickListener != null) {
                conversationOnClickListener.onItemClick(position);
            }
        });
    }

    private String initialOf(String name) {
        if (TextUtils.isEmpty(name)) {
            return "?";
        }
        return String.valueOf(name.charAt(0)).toUpperCase(Locale.getDefault());
    }

    @Override
    public int getItemCount() {
        return conversationList == null ? 0 : conversationList.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        View avatarContainer;
        TextView txtAvatar;
        TextView UserName, LatestMessage, UnreadMessage, txtTime;

        public Holder(@NonNull View itemView) {
            super(itemView);
            avatarContainer = itemView.findViewById(R.id.avatarContainer);
            txtAvatar = itemView.findViewById(R.id.txtAvatar);
            UserName = itemView.findViewById(R.id.txtName);
            LatestMessage = itemView.findViewById(R.id.txtLast);
            UnreadMessage = itemView.findViewById(R.id.txtUnread);
            txtTime = itemView.findViewById(R.id.txtTime);
        }
    }

    public void setConversationOnClickListener(ConversationOnClickListener listener) {
        this.conversationOnClickListener = listener;
    }

    private ConversationOnClickListener conversationOnClickListener;

    public interface ConversationOnClickListener {
        void onItemClick(int position);
    }
}
