package com.example.Japp.network.models.requests;

public class RegisterRequest {
    private String role;
    private String username;
    private String phone;
    private String password;
    private String regionCode;

    public RegisterRequest(String role, String username, String phone, String password, String regionCode) {
        this.role = role;
        this.username = username;
        this.phone = phone;
        this.password = password;
        this.regionCode = regionCode;
    }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getRegionCode() { return regionCode; }
    public void setRegionCode(String regionCode) { this.regionCode = regionCode; }
}