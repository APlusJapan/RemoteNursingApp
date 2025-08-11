package com.aplus.remotenursing.models;

public class VideoTaskDetail {
    private String videoId;
    private String videoName;
    private String videoURL;
    private String videoDuration;
    private String videoDescription;
    private String videoSurfaceImage;

    public VideoTaskDetail() {}

    public String getvideoId() { return videoId; }
    public void setvideoId(String videoId) { this.videoId = videoId; }

    public String getvideoName() { return videoName; }
    public void setvideoName(String videoName) { this.videoName = videoName; }

    public String getvideoURL() { return videoURL; }
    public void setvideoURL(String videoURL) { this.videoURL = videoURL; }

    public String getvideoDuration() { return videoDuration; }
    public void setvideoDuration(String videoDuration) { this.videoDuration = videoDuration; }

    public String getvideoDescription() { return videoDescription; }
    public void setvideoDescription(String videoDescription) { this.videoDescription = videoDescription; }

    public String getvideoSurfaceImage() { return videoSurfaceImage; }
    public void setvideoSurfaceImage(String videoSurfaceImage) { this.videoSurfaceImage = videoSurfaceImage; }
}
