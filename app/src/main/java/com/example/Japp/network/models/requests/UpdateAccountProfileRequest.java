package com.example.Japp.network.models.requests;

public class UpdateAccountProfileRequest {
    private String username;
    private String regionCode;
    private String avatarUrl;

    public UpdateAccountProfileRequest() {
    }

    public UpdateAccountProfileRequest(String username, String regionCode, String avatarUrl) {
        this.username = username;
        this.regionCode = regionCode;
        this.avatarUrl = avatarUrl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRegionCode() {
        return regionCode;
    }

    public void setRegionCode(String regionCode) {
        this.regionCode = regionCode;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }
}
