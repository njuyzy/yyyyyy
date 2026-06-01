package com.example.Japp.network.models.requests;

public class UpdateUsernameRequest {
    private String username;

    public UpdateUsernameRequest(String username) {
        this.username = username;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
