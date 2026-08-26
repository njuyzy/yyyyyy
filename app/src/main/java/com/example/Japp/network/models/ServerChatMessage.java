package com.example.Japp.network.models;

import com.google.gson.annotations.SerializedName;

public class ServerChatMessage {

    public ServerChatMessage() {}

    public ServerChatMessage(long id, long sessionId, int senderAccountId,
                             String content, String msgType, String sentAt) {
        this.id = id;
        this.sessionId = sessionId;
        this.senderAccountId = senderAccountId;
        this.content = content;
        this.msgType = msgType;
        this.sentAt = sentAt;
    }

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

    /** 仅用于本地系统通知和本机刚发送消息，不参与后端写入。 */
    private long localTimestamp;

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

    public long getLocalTimestamp() { return localTimestamp; }
    public void setLocalTimestamp(long localTimestamp) {
        this.localTimestamp = localTimestamp;
    }

    public boolean isSystemNotice() {
        return senderAccountId == -1 || "SYSTEM_NOTICE".equalsIgnoreCase(msgType);
    }
}
