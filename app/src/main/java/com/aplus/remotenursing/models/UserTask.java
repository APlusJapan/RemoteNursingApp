package com.aplus.remotenursing.models;

import com.google.gson.annotations.SerializedName;

public class UserTask {
    @SerializedName("userId")
    private String userId;
    @SerializedName("taskId")
    private String taskId;
    @SerializedName("taskType")
    private String taskType;
    @SerializedName("taskName")
    private String taskName;
    @SerializedName("taskOrder")
    private int taskOrder;
    @SerializedName("actionStatus")
    private String actionStatus;
    public UserTask() {}
    // ---- Getter & Setter
    public String getUserId() { return userId; }

    public void setUserId(String userId) { this.userId = userId; }
    public String getTaskId() { return taskId; }
    public String getTaskType() { return taskType; }
    public String getTaskName() { return taskName; }
    public int getTaskOrder() { return taskOrder; }
    public String getActionStatus() { return actionStatus; }
}