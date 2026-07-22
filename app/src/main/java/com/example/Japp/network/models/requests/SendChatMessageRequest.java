package com.example.Japp.network.models.requests;

import com.google.gson.annotations.SerializedName;

public class SendChatMessageRequest {

    @SerializedName("sessionId")
    private final long sessionId;

    @SerializedName("content")
    private final String content;

    @SerializedName("msgType")
    private final String msgType;

    public SendChatMessageRequest(long sessionId, String content) {
        this.sessionId = sessionId;
        this.content = content;
        this.msgType = "TEXT";
    }

    public long getSessionId() {
        return sessionId;
    }

    public String getContent() {
        return content;
    }

    public String getMsgType() {
        return msgType;
    }
}
