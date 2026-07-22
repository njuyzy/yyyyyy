package com.example.Japp.data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Conversation implements Serializable {
    private User user_me;
    private User user_opposite;
    private List<String> messages;
    private int unRead_num;
    private boolean isGroup;
    private String groupName;
    private List<String> memberNames;
    private long backendSessionId;
    private int projectId;

    public long getBackendSessionId() {
        return backendSessionId;
    }

    public void setBackendSessionId(long backendSessionId) {
        this.backendSessionId = backendSessionId;
    }

    public int getProjectId() {
        return projectId;
    }

    public void setProjectId(int projectId) {
        this.projectId = projectId;
    }

    public boolean isGroup() {
        return isGroup;
    }

    public void setGroup(boolean group) {
        isGroup = group;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public List<String> getMemberNames() {
        if (memberNames == null) {
            memberNames = new ArrayList<>();
        }
        return memberNames;
    }

    public void setMemberNames(List<String> memberNames) {
        this.memberNames = memberNames;
    }

    /** 列表展示名称：群聊用群名，私聊用对方昵称 */
    public String getDisplayName() {
        if (isGroup && groupName != null && !groupName.isEmpty()) {
            return groupName;
        }
        if (user_opposite != null && user_opposite.getUsername() != null) {
            return user_opposite.getUsername();
        }
        return "未知用户";
    }

    public User getUser_me() {
        return user_me;
    }

    public void setUser_me(User user_me) {
        this.user_me = user_me;
    }

    public User getUser_opposite() {
        if (user_opposite == null)
            return new User();
        return user_opposite;
    }

    public void setUser_opposite(User user_opposite) {
        this.user_opposite = user_opposite;
    }

    public List<String> getMessages() {
        if (messages == null) {
            messages = new ArrayList<>();
        }
        return messages;
    }

    public void setMessages(List<String> messages) {
        this.messages = messages;
    }

    // 添加新消息
    public void addMessage(String message) {
        if (messages == null) {
            messages = new ArrayList<>();
        }
        messages.add(message);
    }

    // 获取最后一条消息
    public String getLastMessage() {
        if (messages != null && !messages.isEmpty()) {
            return messages.get(messages.size() - 1);
        }
        return "";
    }

    public int getUnRead_num() {
        return unRead_num;
    }

    public void resetUnRead_num() {
        unRead_num = 0;
    }

    public void setUnRead_num(int unRead_num) {
        this.unRead_num = unRead_num;
    }

    public void incrementUnread() {
        this.unRead_num++;
    }

    public Conversation() {
        messages = new ArrayList<>();
        unRead_num = 0;
    }
}
