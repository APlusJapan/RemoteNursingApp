package com.aplus.remotenursing.models;

import com.google.gson.annotations.SerializedName;

public class UserTask {
    @SerializedName(value = "task_id", alternate = {"taskId"})
    private String task_id;
    @SerializedName(value = "task_type", alternate = {"taskType"})
    private String task_type;
    @SerializedName(value = "task_name", alternate = {"taskName"})
    private String task_name;
    @SerializedName(value = "task_order", alternate = {"taskOrder"})
    private int task_order;

    public UserTask() {}

    public String getTask_id() { return task_id; }
    public String getTask_type() { return task_type; }
    public String getTask_name() { return task_name; }
    public int getTask_order() { return task_order; }
}