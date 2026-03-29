package com.example.Japp.network.models.requests;

public class LoginRequest {
    private String phone;
    private String passwordHash;

    public LoginRequest(String phone, String passwordHash) {
        this.phone = phone;
        this.passwordHash = passwordHash;
    }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
}