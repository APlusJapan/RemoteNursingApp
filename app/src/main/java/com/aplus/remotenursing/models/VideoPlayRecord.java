package com.aplus.remotenursing.models;

import java.util.Date;

public class VideoPlayRecord {
    private String userId;
    private String videoId;
    private String videoSeriesId;
    private String videoName;
    private String videoSeriesName;
    private String playDate; // 改为 String 格式：YYYY-MM-DD
    private int playTime; // 播放时长（秒）
    private String adminId; // 新增：管理员ID

    // Constructors
    public VideoPlayRecord() {}

    public VideoPlayRecord(String userId, String videoId, String videoSeriesId,
                           String videoName, String videoSeriesName, String playDate, int playTime, String adminId) {
        this.userId = userId;
        this.videoId = videoId;
        this.videoSeriesId = videoSeriesId;
        this.videoName = videoName;
        this.videoSeriesName = videoSeriesName;
        this.playDate = playDate;
        this.playTime = playTime;
        this.adminId = adminId;
    }

    // Getters and Setters
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getVideoId() { return videoId; }
    public void setVideoId(String videoId) { this.videoId = videoId; }

    public String getVideoSeriesId() { return videoSeriesId; }
    public void setVideoSeriesId(String videoSeriesId) { this.videoSeriesId = videoSeriesId; }

    public String getVideoName() { return videoName; }
    public void setVideoName(String videoName) { this.videoName = videoName; }

    public String getVideoSeriesName() { return videoSeriesName; }
    public void setVideoSeriesName(String videoSeriesName) { this.videoSeriesName = videoSeriesName; }

    public String getPlayDate() { return playDate; }
    public void setPlayDate(String playDate) { this.playDate = playDate; }

    public int getPlayTime() { return playTime; }
    public void setPlayTime(int playTime) { this.playTime = playTime; }

    public String getAdminId() { return adminId; }
    public void setAdminId(String adminId) { this.adminId = adminId; }
}
