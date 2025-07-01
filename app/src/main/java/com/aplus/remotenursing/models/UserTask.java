package com.aplus.remotenursing.models;

public class UserTask {
    private String task_id;
    private String task_type;
    private String task_name;
    private int task_order;

    public UserTask() {}

    public String getTask_id() { return task_id; }
    public String getTask_type() { return task_type; }
    public String getTask_name() { return task_name; }
    public int getTask_order() { return task_order; }
}