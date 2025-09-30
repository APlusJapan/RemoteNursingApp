package com.aplus.remotenursing.models;

import java.io.Serializable;

public class Team implements Serializable {
    private Long id;
    private String projectId;
    private String teamId;
    private String teamName;
    private String description;
    private Integer status;
    private Boolean defaultFlg;
    private Boolean isDeleted;
    private String createTime;
    private String updatedTime;

    // 默认构造函数
    public Team() {}

    // 便于下拉列表使用的构造函数
    public Team(String teamId, String teamName) {
        this.teamId = teamId;
        this.teamName = teamName;
    }

    // Getter 和 Setter 方法
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getTeamId() { return teamId; }
    public void setTeamId(String teamId) { this.teamId = teamId; }

    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public Boolean getDefaultFlg() { return defaultFlg; }
    public void setDefaultFlg(Boolean defaultFlg) { this.defaultFlg = defaultFlg; }

    public Boolean getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Boolean isDeleted) { this.isDeleted = isDeleted; }

    public String getCreateTime() { return createTime; }
    public void setCreateTime(String createTime) { this.createTime = createTime; }

    public String getUpdatedTime() { return updatedTime; }
    public void setUpdatedTime(String updatedTime) { this.updatedTime = updatedTime; }

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