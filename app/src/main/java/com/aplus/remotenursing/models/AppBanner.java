// models/AppBanner.java
package com.aplus.remotenursing.models;

import com.google.gson.annotations.SerializedName;
import org.json.JSONException;
import org.json.JSONObject;

public class AppBanner {
    private Long id;

    @SerializedName("project_id")
    private String projectId;

    @SerializedName("team_id")
    private String teamId;

    private String title;

    @SerializedName("image_url")
    private String imageUrl;

    @SerializedName("banner_type")
    private int bannerType;

    @SerializedName("action_type")
    private int actionType;

    @SerializedName("action_data")
    private String actionData;

    @SerializedName("display_order")
    private int displayOrder;

    @SerializedName("start_time")
    private String startTime;

    @SerializedName("end_time")
    private String endTime;

    @SerializedName("target_user_type")
    private String targetUserType;

    @SerializedName("click_count")
    private int clickCount;

    @SerializedName("view_count")
    private int viewCount;

    private int status;

    @SerializedName("admin_id")
    private String adminId;

    @SerializedName("is_deleted")
    private int isDeleted;

    @SerializedName("create_time")
    private String createTime;

    @SerializedName("updated_time")
    private String updatedTime;

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

    public int getBannerType() {
        return bannerType;
    }

    public void setBannerType(int bannerType) {
        this.bannerType = bannerType;
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

    public int getIsDeleted() {
        return isDeleted;
    }

    public void setIsDeleted(int isDeleted) {
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

    // 辅助方法：解析actionData
    public JSONObject getActionDataJson() {
        try {
            return new JSONObject(actionData != null ? actionData : "{}");
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    // 辅助方法：检查Banner是否活跃（简单版本，不考虑时间）
    public boolean isActive() {
        return status == 1 && isDeleted == 0;
    }
}