package com.example.Japp.user.fragment.joinTeam;

import com.example.Japp.network.models.Project;

public class TeamCardItem {
    private final Project project;
    private String ownerName = "";
    private String city = "";
    private String routeSummary = "";
    private String duration = "";

    public TeamCardItem(Project project) {
        this.project = project;
    }

    public Project getProject() {
        return project;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName != null ? ownerName : "";
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city != null ? city : "";
    }

    public String getRouteSummary() {
        return routeSummary;
    }

    public void setRouteSummary(String routeSummary) {
        this.routeSummary = routeSummary != null ? routeSummary : "";
    }

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration != null ? duration : "";
    }
}
