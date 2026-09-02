package com.example.Japp.network.models.requests;

import com.google.gson.annotations.SerializedName;

/** 创建或恢复项目群；成员身份由后端根据 JWT 和项目关系确定。 */
public class CreateChatGroupRequest {

    @SerializedName("projectId")
    private final int projectId;

    public CreateChatGroupRequest(int projectId) {
        this.projectId = projectId;
    }

    public int getProjectId() {
        return projectId;
    }
}
