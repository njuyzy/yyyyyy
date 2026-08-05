package com.example.Japp.network.models.requests;

public class UpdateAccountRoleRequest {
    private final String role;

    public UpdateAccountRoleRequest(String role) {
        this.role = role;
    }

    public String getRole() {
        return role;
    }
}
