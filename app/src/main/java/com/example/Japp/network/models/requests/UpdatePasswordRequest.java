package com.example.Japp.network.models.requests;

public class UpdatePasswordRequest {
    private final String oldPassword;
    private final String newPassword;
    private final String confirmPassword;

    public UpdatePasswordRequest(String oldPassword, String newPassword, String confirmPassword) {
        this.oldPassword = oldPassword;
        this.newPassword = newPassword;
        this.confirmPassword = confirmPassword;
    }

    public String getOldPassword() {
        return oldPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }
}
