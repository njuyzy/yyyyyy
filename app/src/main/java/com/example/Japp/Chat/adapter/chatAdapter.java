package com.example.Japp.Chat.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
<<<<<<< HEAD
import android.widget.LinearLayout;
=======
>>>>>>> 8e17abf98766200ef08a42fca1e64b4600ad7f30
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.Japp.R;
import com.example.Japp.data.Message;

<<<<<<< HEAD
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
=======
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class chatAdapter extends RecyclerView.Adapter<chatAdapter.ViewHolder> {

    private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("HH:mm", Locale.getDefault());

    private final List<Message> messages;
    private final String currentUserId;

    public chatAdapter(List<Message> messages, String currentUserId) {
        this.messages = messages;
        this.currentUserId = currentUserId;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutId = (viewType == 1) ? R.layout.item_chat_right : R.layout.item_chat_left;
        View view = LayoutInflater.from(parent.getContext()).inflate(layoutId, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Message message = messages.get(position);
        String name = message.getSender().getUsername();

        holder.txtMessage.setText(message.getContent());
        holder.txtTime.setText(TIME_FMT.format(new Date(message.getTimestamp())));

        if (holder.txtName != null) {
            holder.txtName.setText(name);
        }

        if (holder.txtAvatar != null) {
            String initial = (name != null && !name.isEmpty())
                    ? String.valueOf(name.charAt(0)).toUpperCase(Locale.getDefault())
                    : "?";
            holder.txtAvatar.setText(initial);
>>>>>>> 8e17abf98766200ef08a42fca1e64b4600ad7f30
        }
    }

    @Override
    public int getItemCount() {
<<<<<<< HEAD
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
=======
        return messages.size();
    }

    @Override
    public int getItemViewType(int position) {
        Message message = messages.get(position);
        return message.getSender().getId().equals(currentUserId) ? 1 : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtName;
        TextView txtMessage;
        TextView txtTime;
        TextView txtAvatar;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            txtName    = itemView.findViewById(R.id.txtName);
            txtMessage = itemView.findViewById(R.id.txtMessage);
            txtTime    = itemView.findViewById(R.id.txtTime);
            txtAvatar  = itemView.findViewById(R.id.txtAvatar);
        }
    }

    public void updateMessages(List<Message> newMessages) {
        this.messages.clear();
        this.messages.addAll(newMessages);
        notifyDataSetChanged();
    }
>>>>>>> 8e17abf98766200ef08a42fca1e64b4600ad7f30
}
