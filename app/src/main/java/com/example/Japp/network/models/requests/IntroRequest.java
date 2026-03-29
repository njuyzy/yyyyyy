package com.example.Japp.network.models.requests;

public class IntroRequest {
    private String intro;

    public IntroRequest(String intro) {
        this.intro = intro;
    }

    public String getIntro() { return intro; }
    public void setIntro(String intro) { this.intro = intro; }
}