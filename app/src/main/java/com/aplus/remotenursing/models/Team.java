package com.aplus.remotenursing.models;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

public class Team implements Serializable {
    @SerializedName("id")
    private Long id;

    @SerializedName(value = "projectId", alternate = {"project_id"})
    private String projectId;

    @SerializedName(value = "teamId", alternate = {"team_id"})
    private String teamId;

    @SerializedName(value = "teamName", alternate = {"team_name"})
    private String teamName;

    @SerializedName("description")
    private String description;

    @SerializedName("status")
    private Integer status;

    // 关键：支持后端的 default_flg 字段
    @SerializedName(value = "defaultFlg", alternate = {"default_flg"})
    private Boolean defaultFlg;

    @SerializedName(value = "isDeleted", alternate = {"is_deleted"})
    private Boolean isDeleted;

    @SerializedName(value = "createTime", alternate = {"create_time"})
    private String createTime;

    @SerializedName(value = "updatedTime", alternate = {"updated_time"})
    private String updatedTime;

    // 默认构造函数
    public Team() {}

    // 便于下拉列表使用的构造函数
    public Team(String teamId, String teamName) {
        this.teamId = teamId;
        this.teamName = teamName;
        this.defaultFlg = false;
    }

    // Getter 和 Setter 方法
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

    public String getTeamName() {
        return teamName;
    }

    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Boolean getDefaultFlg() {
        return defaultFlg;
    }

    public void setDefaultFlg(Boolean defaultFlg) {
        this.defaultFlg = defaultFlg;
    }

    // 添加便捷方法：判断是否为默认分组
    public boolean isDefaultFlag() {
        return defaultFlg != null && defaultFlg;
    }

    public Boolean getIsDeleted() {
        return isDeleted;
    }

    public void setIsDeleted(Boolean isDeleted) {
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

    // 用于Spinner显示的方法
    public String getDisplayName() {
        if (teamName != null && !teamName.isEmpty()) {
            return teamName;
        }
        return teamId != null ? teamId : "未知分组";
    }

    // 重写toString方法，用于Spinner显示
    @Override
    public String toString() {
        return getDisplayName();
    }

    // 重写equals和hashCode，便于比较
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Team team = (Team) obj;
        return teamId != null ? teamId.equals(team.teamId) : team.teamId == null;
    }

    @Override
    public int hashCode() {
        return teamId != null ? teamId.hashCode() : 0;
    }
}