package com.example.Japp.Chat.adapter;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.Japp.R;
import com.example.Japp.data.Conversation;

import java.util.ArrayList;
import java.util.List;

public class ConversationListAdapter extends RecyclerView.Adapter<ConversationListAdapter.Holder> {

    private List<Conversation> conversationList;

    public void setListData(List<Conversation> conversationList){
        this.conversationList = conversationList != null ? conversationList : new ArrayList<>();
        notifyDataSetChanged();
    }
    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view= LayoutInflater.from(parent.getContext()).inflate(R.layout.fragment_conversation_card,parent,false);


        return new Holder(view);
    }

    @SuppressLint({"SetTextI18n", "RecyclerView"})
    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        try {
            if (conversationList == null || position >= conversationList.size()) {
                return;
            }

            Conversation conversation = conversationList.get(position);
            holder.bind(conversation);

            // 设置点击监听器
            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (conversationOnClickListener != null) {
                        conversationOnClickListener.onItemClick(position);
                    }
                }
            });
        } catch (Exception e) {
            android.util.Log.e("ConversationListAdapter", "Error binding view: " + e.getMessage());
        }
    }

    @Override
    public int getItemCount() {
        return conversationList != null ? conversationList.size() : 0;
    }

    static class Holder extends RecyclerView.ViewHolder{
        TextView UserName,LatestMessage,Time,UnreadMessage;
        public Holder (@NonNull View itemView){
            super(itemView);

            UserName=itemView.findViewById(R.id.txtName);
            LatestMessage=itemView.findViewById(R.id.txtLast);
            Time=itemView.findViewById(R.id.txtTime);
            UnreadMessage=itemView.findViewById(R.id.txtUnread);
        }

        void bind(Conversation conversation) {
            try {
                if (conversation.getUser_opposite() != null) {
                    UserName.setText(conversation.getUser_opposite().getUsername());
                } else {
                    UserName.setText("未知联系人");
                }

                String latestMsg = conversation.getLatestMessage();
                if (latestMsg != null && !latestMsg.isEmpty()) {
                    LatestMessage.setText(latestMsg);
                } else {
                    LatestMessage.setText("暂无消息");
                }

                long time = conversation.getLatestMessageTime();
                if (time > 0) {
                    Time.setText(conversation.getLatestMessageFormattedTime());
                } else {
                    Time.setText("");
                }

                int unreadCount = conversation.getUnRead_num();
                if (unreadCount > 0) {
                    UnreadMessage.setVisibility(View.VISIBLE);
                    UnreadMessage.setText(String.valueOf(unreadCount));
                } else {
                    UnreadMessage.setVisibility(View.GONE);
                }
            } catch (Exception e) {
                android.util.Log.e("ConversationListAdapter", "Error binding holder: " + e.getMessage());
            }
        }
    }

    public void setConversationOnClickListener(ConversationOnClickListener conversationOnClickListener) {
        this.conversationOnClickListener = conversationOnClickListener;
    }

    private ConversationOnClickListener conversationOnClickListener;
    public interface ConversationOnClickListener{
        void onItemClick(int position);
    }

    // 添加重置未读消息的方法
    public void resetUnreadCount(int position) {
        try {
            if (conversationList != null && position >= 0 && position < conversationList.size()) {
                Conversation conversation = conversationList.get(position);
                conversation.resetUnRead_num();
                notifyItemChanged(position);
            }
        } catch (Exception e) {
            android.util.Log.e("ConversationListAdapter", "Error resetting unread count: " + e.getMessage());
        }
    }

    // 更新指定位置的会话
    public void updateConversation(int position, Conversation newConversation) {
        try {
            if (conversationList != null && position >= 0 && position < conversationList.size()) {
                conversationList.set(position, newConversation);
                notifyItemChanged(position);
            }
        } catch (Exception e) {
            android.util.Log.e("ConversationListAdapter", "Error updating conversation: " + e.getMessage());
        }
    }

    // 删除指定位置的会话
    public void removeConversation(int position) {
        try {
            if (conversationList != null && position >= 0 && position < conversationList.size()) {
                conversationList.remove(position);
                notifyItemRemoved(position);
                notifyItemRangeChanged(position, conversationList.size());
            }
        } catch (Exception e) {
            android.util.Log.e("ConversationListAdapter", "Error removing conversation: " + e.getMessage());
        }
    }
}
