package com.example.Japp.data;

import androidx.fragment.app.Fragment;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Conversation implements Serializable {
    public User getUser_me() {
        return user_me;
    }

    public void setUser_me(User user_me) {
        this.user_me = user_me;
    }

    private User user_me;
    public User getUser_opposite() {
        if(user_opposite==null)
            return new User();
        return user_opposite;
    }

    public void setUser_opposite(User user_opposite) {
        this.user_opposite = user_opposite;
    }

    private User user_opposite;

    private List<Message> messages;

    public List<Message> getMessages() {
        return messages;
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages;
    }

    public void addMessage(Message message) {
        if (messages == null) {
            messages = new ArrayList<>();
        }
        messages.add(message);
    }

    public int getUnRead_num() {
        return unRead_num;
    }

    public void resetUnRead_num() {
        unRead_num = 0;
    }

    private int unRead_num;


    public void setUnRead_num(int unRead_num) {
        this.unRead_num = unRead_num;
    }

    public Conversation(){
        messages=new ArrayList<>();
        unRead_num=0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Conversation that = (Conversation) o;
        return user_me.equals(that.user_me) &&
               user_opposite.equals(that.user_opposite);
    }

    @Override
    public int hashCode() {
        return user_me.hashCode() ^ user_opposite.hashCode();
    }

    public String getLatestMessage() {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        return messages.get(messages.size() - 1).getContent();
    }

    public long getLatestMessageTime() {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        return messages.get(messages.size() - 1).getTimestamp();
    }

    public String getLatestMessageFormattedTime() {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        return messages.get(messages.size() - 1).getFormattedTime();
    }
}
