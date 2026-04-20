package com.example.Japp.Chat.adapter;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
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
        if (conversation == null || conversation.getUser_opposite() == null) {
            return;
        }

        List<String> messages = conversation.getMessages();

        // 设置用户名
        String userName = conversation.getUser_opposite().getUsername();
        holder.UserName.setText(userName != null ? userName : "未知用户");

        // 显示最后一条消息
        if (messages != null && !messages.isEmpty()) {
            String lastMessage = messages.get(messages.size() - 1);
            holder.LatestMessage.setText(lastMessage != null ? lastMessage : "");
        } else {
            holder.LatestMessage.setText("暂无消息");
        }

        // 显示时间（使用当前时间作为示例，实际应该从数据库获取）
        holder.txtTime.setText(TIME_FMT.format(new Date()));

        // 显示未读消息数
        int unreadCount = conversation.getUnRead_num();
        if (unreadCount > 0) {
            holder.UnreadMessage.setVisibility(View.VISIBLE);
            String unreadText = unreadCount > 99 ? "99+" : String.valueOf(unreadCount);
            holder.UnreadMessage.setText(unreadText);
        } else {
            holder.UnreadMessage.setVisibility(View.GONE);
        }

        // 设置点击事件
        holder.itemView.setOnClickListener(v -> {
            if (conversationOnClickListener != null) {
                conversationOnClickListener.onItemClick(position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return conversationList == null ? 0 : conversationList.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        ImageView avatar;
        TextView UserName, LatestMessage, UnreadMessage, txtTime;

        public Holder(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.avatar);
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