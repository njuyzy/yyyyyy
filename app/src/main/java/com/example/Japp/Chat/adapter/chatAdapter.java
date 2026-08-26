package com.example.Japp.Chat.adapter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.Japp.R;
import com.example.Japp.Chat.util.ChatAvatarLoader;
import com.example.Japp.data.Message;
import com.example.Japp.data.User;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class chatAdapter extends RecyclerView.Adapter<chatAdapter.ViewHolder> {

    private static final SimpleDateFormat TIME_FMT =
            new SimpleDateFormat("HH:mm", Locale.getDefault());

    private final List<Message> messages;
    private final String currentUserId;

    public chatAdapter(List<Message> messages, String currentUserId) {
        this.messages = messages;
        this.currentUserId = currentUserId;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutId = viewType == 1 ? R.layout.item_chat_right : R.layout.item_chat_left;
        View view = LayoutInflater.from(parent.getContext()).inflate(layoutId, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Message message = messages.get(position);
        User sender = message.getSender();
        String name = sender == null ? null : sender.getUsername();
        if (TextUtils.isEmpty(name)) {
            name = "群成员";
        }

        holder.txtMessage.setText(message.getContent());
        holder.txtTime.setText(TIME_FMT.format(new Date(message.getTimestamp())));
        holder.txtName.setText(formatName(name, sender));
        holder.txtName.setVisibility(View.VISIBLE);

        bindRoleBadge(holder, sender == null ? null : sender.getMemberRole());
        bindAvatar(holder, sender, name);
    }

    private void bindRoleBadge(ViewHolder holder, String memberRole) {
        if ("PUBLISHER".equalsIgnoreCase(memberRole)
                || "OWNER".equalsIgnoreCase(memberRole)) {
            holder.txtRoleBadge.setText("群主");
            holder.txtRoleBadge.setBackgroundResource(R.drawable.bg_chat_role_owner);
            holder.txtRoleBadge.setVisibility(View.VISIBLE);
        } else if ("LEADER".equalsIgnoreCase(memberRole)
                || "ADMIN".equalsIgnoreCase(memberRole)) {
            holder.txtRoleBadge.setText("领队");
            holder.txtRoleBadge.setBackgroundResource(R.drawable.bg_chat_role_leader);
            holder.txtRoleBadge.setVisibility(View.VISIBLE);
        } else {
            holder.txtRoleBadge.setVisibility(View.GONE);
        }
    }

    private void bindAvatar(ViewHolder holder, User sender, String name) {
        boolean system = sender != null
                && "SYSTEM".equalsIgnoreCase(sender.getMemberRole());
        boolean self = sender != null && currentUserId.equals(sender.getId());
        holder.avatarContainer.setBackgroundResource(system
                ? R.drawable.bg_chat_avatar_system
                : self ? R.drawable.bg_chat_avatar_self : R.drawable.bg_chat_avatar_peer);
        holder.txtName.setTextColor(ContextCompat.getColor(holder.itemView.getContext(),
                system ? R.color.chat_name_system : R.color.chat_name_other));
        ChatAvatarLoader.bind(
                holder.imgAvatar,
                holder.txtAvatar,
                system || sender == null ? null : sender.getAvatarUrl(),
                name);
    }

    private static String formatName(String name, User sender) {
        String role = sender == null ? null : sender.getMemberRole();
        if ("LEADER".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role)) {
            return name;
        }
        Integer representedCount = sender == null ? null : sender.getRepresentedCount();
        if (representedCount == null) {
            return name;
        }
        return name + "（" + Math.max(0, representedCount) + "人）";
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    @Override
    public int getItemViewType(int position) {
        Message message = messages.get(position);
        User sender = message.getSender();
        return sender != null && currentUserId.equals(sender.getId()) ? 1 : 0;
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
        if (messages.isEmpty()) {
            return null;
        }
        return messages.get(messages.size() - 1);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView txtName;
        final TextView txtMessage;
        final TextView txtTime;
        final TextView txtAvatar;
        final TextView txtRoleBadge;
        final ImageView imgAvatar;
        final View avatarContainer;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            txtName = itemView.findViewById(R.id.txtName);
            txtMessage = itemView.findViewById(R.id.txtMessage);
            txtTime = itemView.findViewById(R.id.txtTime);
            txtAvatar = itemView.findViewById(R.id.txtAvatar);
            txtRoleBadge = itemView.findViewById(R.id.txtRoleBadge);
            imgAvatar = itemView.findViewById(R.id.imgAvatar);
            avatarContainer = itemView.findViewById(R.id.avatarContainer);
        }
    }
}
