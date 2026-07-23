package com.example.Japp.network.models;

import com.google.gson.annotations.SerializedName;

public class ChatGroupMember {
    @SerializedName("accountId")
    private int accountId;

    @SerializedName("username")
    private String username;

    @SerializedName("avatarUrl")
    private String avatarUrl;

    @SerializedName("memberRole")
    private String memberRole;

    @SerializedName("representedCount")
    private int representedCount;

    @SerializedName("representationText")
    private String representationText;

    public int getAccountId() { return accountId; }
    public String getUsername() { return username; }
    public String getAvatarUrl() { return avatarUrl; }
    public String getMemberRole() { return memberRole; }
    public int getRepresentedCount() { return representedCount; }
    public String getRepresentationText() { return representationText; }
}
