package com.aplus.remotenursing.models;

import java.io.Serializable;

public class Project implements Serializable {
    private Long id;
    private Long adminId;
    private String projectId;
    private String projectName;
    private String description;
    private Integer status;
    private Boolean defaultFlg;
    private Boolean isDeleted;
    private String createTime;
    private String updatedTime;

    // 默认构造函数
    public Project() {}

    // 便于下拉列表使用的构造函数
    public Project(String projectId, String projectName) {
        this.projectId = projectId;
        this.projectName = projectName;
    }

    // Getter 和 Setter 方法
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getAdminId() { return adminId; }
    public void setAdminId(Long adminId) { this.adminId = adminId; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }

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
        if (projectName != null && !projectName.isEmpty()) {
            return projectName;
        }
        return projectId != null ? projectId : "未知课题";
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
        Project project = (Project) obj;
        return projectId != null ? projectId.equals(project.projectId) : project.projectId == null;
    }

    @Override
    public int hashCode() {
        return projectId != null ? projectId.hashCode() : 0;
    }
}