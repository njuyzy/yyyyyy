package com.example.Japp.Chat.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.Japp.R;
import com.example.Japp.data.Message;

import java.util.ArrayList;
import java.util.List;

public class chatAdapter extends RecyclerView.Adapter<chatAdapter.MessageViewHolder> {

    private List<Message> messageList = new ArrayList<>();
    private String currentUserId;

    public void setCurrentUserId(String currentUserId) {
        this.currentUserId = currentUserId;
    }

    public void setMessages(List<Message> messages) {
        this.messageList.clear();
        if (messages != null) {
            this.messageList.addAll(messages);
        }
        notifyDataSetChanged();
    }

    public void addMessage(Message message) {
        messageList.add(message);
        notifyItemInserted(messageList.size() - 1);
        notifyItemRangeChanged(messageList.size() - 1, 1);
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat_message, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        Message message = messageList.get(position);

        if (message.isSent()) {
            // 显示发送的消息
            holder.sentMessageLayout.setVisibility(View.VISIBLE);
            holder.receivedMessageLayout.setVisibility(View.GONE);

            holder.tvSentMessage.setText(message.getContent());
            holder.tvSentTime.setText(message.getFormattedTime());
        } else {
            // 显示接收的消息
            holder.sentMessageLayout.setVisibility(View.GONE);
            holder.receivedMessageLayout.setVisibility(View.VISIBLE);

            holder.tvReceivedMessage.setText(message.getContent());
            holder.tvReceivedTime.setText(message.getFormattedTime());
        }
    }

    @Override
    public int getItemCount() {
        return messageList.size();
    }

    public void clearMessages() {
        messageList.clear();
        notifyDataSetChanged();
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        LinearLayout sentMessageLayout;
        LinearLayout receivedMessageLayout;
        TextView tvSentMessage;
        TextView tvSentTime;
        TextView tvReceivedMessage;
        TextView tvReceivedTime;

        public MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            sentMessageLayout = itemView.findViewById(R.id.sentMessageLayout);
            receivedMessageLayout = itemView.findViewById(R.id.receivedMessageLayout);
            tvSentMessage = itemView.findViewById(R.id.tvSentMessage);
            tvSentTime = itemView.findViewById(R.id.tvSentTime);
            tvReceivedMessage = itemView.findViewById(R.id.tvReceivedMessage);
            tvReceivedTime = itemView.findViewById(R.id.tvReceivedTime);
        }
    }
}
