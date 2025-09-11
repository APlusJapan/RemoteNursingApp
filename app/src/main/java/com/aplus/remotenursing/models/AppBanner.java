// models/AppBanner.java
package com.aplus.remotenursing.models;

import com.google.gson.annotations.SerializedName;
import org.json.JSONException;
import org.json.JSONObject;

public class AppBanner {
    private Long id;

    @SerializedName("projectId")  // 从之前日志看应该是驼峰格式
    private String projectId;

    @SerializedName("teamId")
    private String teamId;

    private String title;

    @SerializedName("imageUrl")  // 修改：改为驼峰格式
    private String imageUrl;


    @SerializedName("actionType")  // 修改：改为驼峰格式
    private int actionType;

    @SerializedName("actionData")  // 修改：改为驼峰格式
    private String actionData;

    @SerializedName("displayOrder")  // 修改：改为驼峰格式
    private int displayOrder;

    @SerializedName("startTime")  // 修改：改为驼峰格式
    private String startTime;

    @SerializedName("endTime")  // 修改：改为驼峰格式
    private String endTime;

    @SerializedName("targetUserType")  // 修改：改为驼峰格式
    private String targetUserType;

    @SerializedName("clickCount")  // 修改：改为驼峰格式
    private int clickCount;

    @SerializedName("viewCount")  // 修改：改为驼峰格式
    private int viewCount;

    private int status;

    @SerializedName("adminId")  // 修改：改为驼峰格式
    private String adminId;

    @SerializedName("isDeleted")  // 修改：改为驼峰格式
    private boolean isDeleted;  // 修改：改为boolean类型，因为JSON返回的是true/false

    @SerializedName("createTime")  // 修改：改为驼峰格式
    private String createTime;

    @SerializedName("updatedTime")  // 修改：改为驼峰格式
    private String updatedTime;

    @SerializedName("active")  // 新增：直接映射active字段
    private boolean active;

    // 构造函数
    public AppBanner() {}

    // Getter和Setter方法
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getTeamId() {
        return teamId;
    }

    public void setTeamId(String teamId) {
        this.teamId = teamId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public int getActionType() {
        return actionType;
    }

    public void setActionType(int actionType) {
        this.actionType = actionType;
    }

    public String getActionData() {
        return actionData;
    }

    public void setActionData(String actionData) {
        this.actionData = actionData;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public String getTargetUserType() {
        return targetUserType;
    }

    public void setTargetUserType(String targetUserType) {
        this.targetUserType = targetUserType;
    }

    public int getClickCount() {
        return clickCount;
    }

    public void setClickCount(int clickCount) {
        this.clickCount = clickCount;
    }

    public int getViewCount() {
        return viewCount;
    }

    public void setViewCount(int viewCount) {
        this.viewCount = viewCount;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getAdminId() {
        return adminId;
    }

    public void setAdminId(String adminId) {
        this.adminId = adminId;
    }

    public boolean getIsDeleted() {  // 修改：改为boolean
        return isDeleted;
    }

    public void setIsDeleted(boolean isDeleted) {  // 修改：改为boolean
        this.isDeleted = isDeleted;
    }

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    public String getUpdatedTime() {
        return updatedTime;
    }

    public void setUpdatedTime(String updatedTime) {
        this.updatedTime = updatedTime;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    // 辅助方法：解析actionData
    public JSONObject getActionDataJson() {
        try {
            return new JSONObject(actionData != null ? actionData : "{}");
        } catch (JSONException e) {
            return new JSONObject();
        }
    }
}