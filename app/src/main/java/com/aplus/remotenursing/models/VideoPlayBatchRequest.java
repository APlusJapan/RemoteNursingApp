package com.aplus.remotenursing.models;

import java.util.List;

public class VideoPlayBatchRequest {
    private String userId;
    private List<VideoPlayRecord> records;

    // Constructors
    public VideoPlayBatchRequest() {}

    public VideoPlayBatchRequest(String userId, List<VideoPlayRecord> records) {
        this.userId = userId;
        this.records = records;
    }

    // Getters and Setters
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public List<VideoPlayRecord> getRecords() { return records; }
    public void setRecords(List<VideoPlayRecord> records) { this.records = records; }
}