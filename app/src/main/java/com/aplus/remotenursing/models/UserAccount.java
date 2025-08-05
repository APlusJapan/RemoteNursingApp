package com.aplus.remotenursing.models;

import com.google.gson.annotations.SerializedName;

public class UserAccount {
    @SerializedName(value = "userId", alternate = {"user_id"})
    private String userId;

    @SerializedName(value = "loginName", alternate = {"login_name"})
    private String loginName;
    @SerializedName(value = "nickName", alternate = {"nick_name"})
    private String nickName;
    public UserAccount() {}

    // ---- Getter & Setter (全部用驼峰风格，和字段保持一致) ----
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getLoginName() { return loginName; }
    public void setLoginName(String loginName) { this.loginName = loginName; }
    public String getNickName() { return nickName; }
    public void setNickName(String nickName) { this.nickName = nickName; }
}
