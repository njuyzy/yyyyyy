package com.example.Japp.data;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Message implements Serializable {
    public static final String TYPE_SENT = "sent";
    public static final String TYPE_RECEIVED = "received";

    private String content;
    private String type;
    private String senderId;
    private String receiverId;
    private long timestamp;
    private String formattedTime;

    public Message() {
        this.timestamp = System.currentTimeMillis();
        this.formattedTime = formatTime(this.timestamp);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Message message = (Message) o;
        return timestamp == message.timestamp &&
               senderId.equals(message.senderId) &&
               receiverId.equals(message.receiverId) &&
               content.equals(message.content);
    }

    @Override
    public int hashCode() {
        return (int) (timestamp ^ (timestamp >>> 32));
    }

    public Message(String content, String type, String senderId, String receiverId) {
        this.content = content;
        this.type = type;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.timestamp = System.currentTimeMillis();
        this.formattedTime = formatTime(this.timestamp);
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getReceiverId() {
        return receiverId;
    }

    public void setReceiverId(String receiverId) {
        this.receiverId = receiverId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
        this.formattedTime = formatTime(timestamp);
    }

    public String getFormattedTime() {
        return formattedTime;
    }

    private String formatTime(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    public boolean isSent() {
        return TYPE_SENT.equals(type);
    }
}