package com.example.Japp.network.models;

import com.google.gson.annotations.SerializedName;

public class Account {
    @SerializedName("id")
    private int id;

    @SerializedName("role")
    private String role;

    @SerializedName("username")
    private String username;

    @SerializedName("phone")
    private String phone;

    @SerializedName("passwordHash")
    private String passwordHash;

    @SerializedName(value = "regionCode", alternate = {"region_code", "region", "regionName", "regionAdcode", "adcode"})
    private String regionCode;

    @SerializedName("avatarUrl")
    private String avatarUrl;

    @SerializedName("intro")
    private String intro;

    @SerializedName("status")
    private int status;

    @SerializedName("createdAt")
    private String createdAt;

    @SerializedName("updatedAt")
    private String updatedAt;

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getRegionCode() { return regionCode; }
    public void setRegionCode(String regionCode) { this.regionCode = regionCode; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public String getIntro() { return intro; }
    public void setIntro(String intro) { this.intro = intro; }

    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
