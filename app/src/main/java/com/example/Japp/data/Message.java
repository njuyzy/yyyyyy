package com.example.Japp.data;

import java.io.Serializable;
import java.util.Date;

public class Message implements Serializable {
    private User sender;
    private String content;
    private long timestamp;

    public Message() {
        this.sender = new User();
        this.content = "";
        this.timestamp = System.currentTimeMillis();
    }

    public Message(User sender, String content, long timestamp) {
        this.sender = sender;
        this.content = content;
        this.timestamp = timestamp;
    }

    public User getSender() {
        return sender;
    }

    public void setSender(User sender) {
        this.sender = sender;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}