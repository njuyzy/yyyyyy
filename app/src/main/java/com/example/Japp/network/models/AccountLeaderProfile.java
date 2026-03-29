package com.example.Japp.network.models;

public class AccountLeaderProfile {
    private int accountId;
    private String intro;
    private Integer totalRating;
    private Integer ratingCount;

    // Getters and Setters
    public int getAccountId() { return accountId; }
    public void setAccountId(int accountId) { this.accountId = accountId; }

    public String getIntro() { return intro; }
    public void setIntro(String intro) { this.intro = intro; }

    public Integer getTotalRating() { return totalRating; }
    public void setTotalRating(Integer totalRating) { this.totalRating = totalRating; }

    public Integer getRatingCount() { return ratingCount; }
    public void setRatingCount(Integer ratingCount) { this.ratingCount = ratingCount; }
}