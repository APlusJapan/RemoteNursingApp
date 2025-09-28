package com.aplus.remotenursing.models;

import com.google.gson.annotations.SerializedName;

public class UserAccount {
    @SerializedName(value = "userId", alternate = {"user_id"})
    private String userId;
    @SerializedName(value = "userType", alternate = {"user_type"})
    private String userType;
    @SerializedName(value = "loginName", alternate = {"login_name"})
    private String loginName;

    @SerializedName(value = "nickName", alternate = {"nick_name"})
    private String nickName;
    @SerializedName(value = "deviceId", alternate = {"device_id"})
    private String deviceId;
    @SerializedName(value = "adminId", alternate = {"admin_id"})
    private String adminId;
    @SerializedName(value = "teamId", alternate = {"team_id"})
    private String teamId;
    @SerializedName(value = "projectId", alternate = {"project_id"})
    private String projectId;
    public UserAccount() {}

    // ---- Getter & Setter (全部用驼峰风格，和字段保持一致) ----
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUserType() { return userType; }
    public void setUserType(String userType) { this.userType = userType; }
    public String getLoginName() { return loginName; }
    public void setLoginName(String loginName) { this.loginName = loginName; }

    public String getNickName() { return nickName; }
    public void setNickName(String nickName) { this.nickName = nickName; }

    public String getAdminId() { return adminId; }
    public void setAdminId(String adminId) { this.adminId = adminId; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getTeamId() { return teamId; }
    public void setTeamId(String teamId) { this.teamId = teamId; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
}