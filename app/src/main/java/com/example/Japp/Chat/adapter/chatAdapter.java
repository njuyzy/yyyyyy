package com.example.Japp.Chat.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.Japp.R;
import com.example.Japp.data.Message;

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
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    @Override
    public int getItemViewType(int position) {
        Message message = messages.get(position);
        return message.getSender().getId().equals(currentUserId) ? 1 : 0;
    }


    public void addMessage(Message message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }


    public void updateMessages(List<Message> newMessages) {
        messages.clear();
        messages.addAll(newMessages);
        notifyDataSetChanged();
    }


    public Message getLastMessage() {
        if (messages.isEmpty()) return null;
        return messages.get(messages.size() - 1);
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
}