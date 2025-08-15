package com.aplus.remotenursing.models;

public class VideoTaskDetail {
    private String videoId;
    private String videoName;
    private String videoSeriesId;
    private String videoURL;
    private String videoDuration;
    private String videoDescription;
    private String videoSurfaceImage;

    // 新增字段
    private Integer videoOrder;    // 视频顺序
    private String fouceNow;       // 现在播放标志

    public VideoTaskDetail() {}

    // 原有字段的getter和setter
    public String getVideoId() { return videoId; }
    public void setVideoId(String videoId) { this.videoId = videoId; }
    public String getVideoSeriesId() {
        return videoSeriesId;
    }

    public void setVideoSeriesId(String videoSeriesId) {
        this.videoSeriesId = videoSeriesId;
    }
    public String getVideoName() { return videoName; }
    public void setVideoName(String videoName) { this.videoName = videoName; }

    public String getVideoURL() { return videoURL; }
    public void setVideoURL(String videoURL) { this.videoURL = videoURL; }

    public String getVideoDuration() { return videoDuration; }
    public void setVideoDuration(String videoDuration) { this.videoDuration = videoDuration; }

    public String getVideoDescription() { return videoDescription; }
    public void setVideoDescription(String videoDescription) { this.videoDescription = videoDescription; }

    public String getVideoSurfaceImage() { return videoSurfaceImage; }
    public void setVideoSurfaceImage(String videoSurfaceImage) { this.videoSurfaceImage = videoSurfaceImage; }

    // 新增字段的getter和setter
    public Integer getVideoOrder() { return videoOrder; }
    public void setVideoOrder(Integer videoOrder) { this.videoOrder = videoOrder; }

    public String getFouceNow() { return fouceNow; }
    public void setFouceNow(String fouceNow) { this.fouceNow = fouceNow; }

    // 便利方法：判断是否为当前播放视频
    public boolean isCurrentlyPlaying() {
        return "1".equals(fouceNow);
    }
}