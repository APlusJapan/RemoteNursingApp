package com.aplus.remotenursing.models;

public class UserInfoAccount {
    public String userName;
    public String gender;
    public String phone;
    public String createdTime;
    public String loginStatus;
    public String projectName;
    public String teamName;

    // 便于RecyclerView安全处理的辅助方法
    public String safe(String s) { return s == null ? "" : s; }

}
