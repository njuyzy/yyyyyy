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
    public List<String> getMessages() {
        return messages;
    }

    private List<String> messages;
    public int getUnRead_num() {
        return unRead_num;
    }

    public void resetUnRead_num() {
        unRead_num = 0;
    }

    private int unRead_num;

    public Conversation(){
        messages=new ArrayList<>();
        unRead_num=1;
        //样例
        messages.add("你好！");
    }



}
