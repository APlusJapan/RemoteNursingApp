package com.aplus.remotenursing.models;

import com.google.gson.annotations.SerializedName;
import java.util.Date;

public class UserNotice {
    @SerializedName("id")
    private Integer id;

    @SerializedName("projectId")
    private String projectId;

    @SerializedName("teamId")
    private String teamId;

    @SerializedName("noticeId")
    private String noticeId;

    @SerializedName("noticeText")
    private String noticeText;

    @SerializedName("startTime")
    private Date startTime;

    @SerializedName("endTime")
    private Date endTime;

    @SerializedName("adminId")
    private String adminId;

    @SerializedName("isDeleted")
    private boolean isDeleted;

    @SerializedName("createTime")
    private Date createTime;

    @SerializedName("updatedTime")
    private Date updatedTime;

    public UserNotice() {}

    // Getters
    public Integer getId() { return id; }
    public String getProjectId() { return projectId; }
    public String getTeamId() { return teamId; }
    public String getNoticeId() { return noticeId; }
    public String getNoticeText() { return noticeText; }
    public Date getStartTime() { return startTime; }
    public Date getEndTime() { return endTime; }
    public String getAdminId() { return adminId; }
    public boolean isDeleted() { return isDeleted; }
    public Date getCreateTime() { return createTime; }
    public Date getUpdatedTime() { return updatedTime; }

    // Setters
    public void setId(Integer id) { this.id = id; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public void setTeamId(String teamId) { this.teamId = teamId; }
    public void setNoticeId(String noticeId) { this.noticeId = noticeId; }
    public void setNoticeText(String noticeText) { this.noticeText = noticeText; }
    public void setStartTime(Date startTime) { this.startTime = startTime; }
    public void setEndTime(Date endTime) { this.endTime = endTime; }
    public void setAdminId(String adminId) { this.adminId = adminId; }
    public void setDeleted(boolean deleted) { isDeleted = deleted; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public void setUpdatedTime(Date updatedTime) { this.updatedTime = updatedTime; }
}