package com.example.Japp.network.models.requests;

public class RegisterRequest {
    private String role;
    private String username;
    private String phone;
    private String passwordHash;
    private String regionCode;

    public RegisterRequest(String role, String username, String phone, String passwordHash, String regionCode) {
        this.role = role;
        this.username = username;
        this.phone = phone;
        this.passwordHash = passwordHash;
        this.regionCode = regionCode;
    }

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
}