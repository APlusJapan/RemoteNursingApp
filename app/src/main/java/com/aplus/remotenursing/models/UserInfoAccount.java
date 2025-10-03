package com.aplus.remotenursing.models;

public class UserInfoAccount {
    public String userId;
    public String userName;
    public String gender;
    public String phone;
    public String createdTime;
    public String lastLoginTime;
    public String loginStatus;
    public String projectId;      // 新增
    public String projectName;
    public String teamId;          // 新增
    public String teamName;

    // 无参构造函数
    public UserInfoAccount() {}

    // 全参构造函数
    public UserInfoAccount(String userId, String userName, String gender, String phone,
                           String createdTime, String loginStatus, String projectName, String teamName) {
        this.userId = userId;
        this.userName = userName;
        this.gender = gender;
        this.phone = phone;
        this.createdTime = createdTime;
        this.loginStatus = loginStatus;
        this.projectName = projectName;
        this.teamName = teamName;
    }

    // Getter 方法
    public String getUserId() {
        return userId;
    }

    public String getUserName() {
        return userName;
    }

    public String getGender() {
        return gender;
    }

    public String getPhone() {
        return phone;
    }

    public String getCreatedTime() {
        return createdTime;
    }

    public String getLastLoginTime() {
        return lastLoginTime;
    }

    public String getLoginStatus() {
        return loginStatus;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getProjectName() {
        return projectName;
    }

    public String getTeamId() {
        return teamId;
    }

    public String getTeamName() {
        return teamName;
    }

    // Setter 方法
    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public void setCreatedTime(String createdTime) {
        this.createdTime = createdTime;
    }

    public void setLastLoginTime(String lastLoginTime) {
        this.lastLoginTime = lastLoginTime;
    }

    public void setLoginStatus(String loginStatus) {
        this.loginStatus = loginStatus;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public void setTeamId(String teamId) {
        this.teamId = teamId;
    }

    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }

    // 便于RecyclerView安全处理的辅助方法
    public String safe(String s) {
        return s == null ? "" : s;
    }
}