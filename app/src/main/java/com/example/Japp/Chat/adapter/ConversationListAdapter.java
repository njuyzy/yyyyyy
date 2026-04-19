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

import java.util.List;

public class conversationListAdapter extends RecyclerView.Adapter<conversationListAdapter.Holder> {

    List<Conversation> conversationList;


    public void setListData(List<Conversation> conversationList){
        this.conversationList=conversationList;
        notifyDataSetChanged();
    }
    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view= LayoutInflater.from(parent.getContext()).inflate(R.layout.fragment_conversation_card,parent,false);


        return new Holder(view);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull Holder holder, @SuppressLint("RecyclerView") int position) {

        Conversation conversation=conversationList.get(position);
        List<String> messages=conversation.getMessages();
        holder.UserName.setText(conversation.getUser_opposite().getUsername());
        holder.LatestMessage.setText(messages.get(messages.size()-1));


        if(conversation.getUnRead_num()>0){
            holder.UnreadMessage.setVisibility(View.VISIBLE);
            holder.UnreadMessage.setText(conversation.getUnRead_num()+"");
        }
        //如果没有未读消息，隐藏控件
        else
            holder.UnreadMessage.setVisibility(View.GONE);
        //
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(conversationOnClickListener!=null){
                    conversationOnClickListener.onItemClick(position);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return conversationList.size();
    }

    static class Holder extends RecyclerView.ViewHolder{
        ImageView avatar;
        TextView UserName, LatestMessage, UnreadMessage;
        public Holder (@NonNull View itemView){
            super(itemView);

            avatar = itemView.findViewById(R.id.avatar);
            UserName = itemView.findViewById(R.id.txtName);
            LatestMessage = itemView.findViewById(R.id.txtLast);
            UnreadMessage = itemView.findViewById(R.id.txtUnread);
        }
    }

    public void setConversationOnClickListener(ConversationOnClickListener conversationOnClickListener) {
        this.conversationOnClickListener = conversationOnClickListener;
    }

    private ConversationOnClickListener conversationOnClickListener;
    public interface ConversationOnClickListener{
        void onItemClick(int position);
    }
}
