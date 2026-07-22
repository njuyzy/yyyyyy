package com.example.Japp.network.models;

import com.google.gson.annotations.SerializedName;

public class ServerChatMessage {

    @SerializedName("id")
    private long id;

    @SerializedName("sessionId")
    private long sessionId;

    @SerializedName("senderAccountId")
    private int senderAccountId;

    @SerializedName("content")
    private String content;

    @SerializedName("msgType")
    private String msgType;

    @SerializedName("sentAt")
    private String sentAt;

    public long getId() {
        return id;
    }

    public long getSessionId() {
        return sessionId;
    }

    public int getSenderAccountId() {
        return senderAccountId;
    }

    public String getContent() {
        return content;
    }

    public String getMsgType() {
        return msgType;
    }

    public String getSentAt() {
        return sentAt;
    }
}
