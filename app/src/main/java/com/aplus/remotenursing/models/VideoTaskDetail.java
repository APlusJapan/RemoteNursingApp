package com.aplus.remotenursing.models;

public class VideoTaskDetail {
    private String videoId;
    private String videoName;
    private String videoURL;
    private String videoDuration;
    private String videoDescription;
    private String videoSurfaceImage;

    public VideoTaskDetail() {}

    public String getVideoId() { return videoId; }
    public void setVideoId(String videoId) { this.videoId = videoId; }

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
}
