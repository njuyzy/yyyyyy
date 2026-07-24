package com.example.Japp.Chat.adapter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.Japp.Chat.util.ChatAvatarLoader;
import com.example.Japp.R;
import com.example.Japp.network.models.ChatGroupMember;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ChatMemberAdapter extends RecyclerView.Adapter<ChatMemberAdapter.Holder> {

    private final List<ChatGroupMember> members = new ArrayList<>();

    public void setMembers(List<ChatGroupMember> values) {
        members.clear();
        if (values != null) {
            members.addAll(values);
            members.sort(Comparator
                    .comparingInt((ChatGroupMember member) ->
                            roleOrder(member.getMemberRole()))
                    .thenComparing(member -> safeName(member.getUsername()),
                            String.CASE_INSENSITIVE_ORDER));
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat_member, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        ChatGroupMember member = members.get(position);
        String name = safeName(member.getUsername());
        holder.txtName.setText(isLeader(member.getMemberRole())
                ? name
                : name + "（" + Math.max(0, member.getRepresentedCount()) + "人）");
        bindRole(holder.txtRoleBadge, member.getMemberRole());
        ChatAvatarLoader.bind(
                holder.imgAvatar,
                holder.txtAvatar,
                member.getAvatarUrl(),
                name);
    }

    private static void bindRole(TextView badge, String role) {
        if ("PUBLISHER".equalsIgnoreCase(role)
                || "OWNER".equalsIgnoreCase(role)) {
            badge.setText("群主");
            badge.setBackgroundResource(R.drawable.bg_chat_role_owner);
            badge.setVisibility(View.VISIBLE);
        } else if ("LEADER".equalsIgnoreCase(role)
                || "ADMIN".equalsIgnoreCase(role)) {
            badge.setText("领队");
            badge.setBackgroundResource(R.drawable.bg_chat_role_leader);
            badge.setVisibility(View.VISIBLE);
        } else {
            badge.setVisibility(View.GONE);
        }
    }

    private static int roleOrder(String role) {
        if ("PUBLISHER".equalsIgnoreCase(role)
                || "OWNER".equalsIgnoreCase(role)) {
            return 0;
        }
        if ("LEADER".equalsIgnoreCase(role)
                || "ADMIN".equalsIgnoreCase(role)) {
            return 1;
        }
        return 2;
    }

    private static boolean isLeader(String role) {
        return "LEADER".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role);
    }

    private static String safeName(String username) {
        return TextUtils.isEmpty(username) ? "群成员" : username;
    }

    @Override
    public int getItemCount() {
        return members.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView txtName;
        final TextView txtAvatar;
        final TextView txtRoleBadge;
        final ImageView imgAvatar;

        Holder(@NonNull View itemView) {
            super(itemView);
            txtName = itemView.findViewById(R.id.txtMemberName);
            txtAvatar = itemView.findViewById(R.id.txtMemberAvatar);
            txtRoleBadge = itemView.findViewById(R.id.txtMemberRoleBadge);
            imgAvatar = itemView.findViewById(R.id.imgMemberAvatar);
        }
    }
}
