package com.example.Japp.network.models;

import com.google.gson.annotations.SerializedName;

public class LoginResponse {
    @SerializedName("account")
    private Account account;

    @SerializedName("token")
    private String token;

    @SerializedName("refreshToken")
    private String refreshToken;

    public Account getAccount() { return account; }
    public String getToken() { return token; }
    public String getRefreshToken() { return refreshToken; }
}
