package com.aplus.remotenursing.models;

public class VideoTask {
    private String videoSeriesId;
    private String videoSeriesName;
    private String videoSurfaceImage;
    private String videoDescription;

    // 必须要有空构造方法
    public VideoTask() {}

    public VideoTask(String id, String name, String img, String desc) {
        this.videoSeriesId = id;
        this.videoSeriesName = name;
        this.videoSurfaceImage = img;
        this.videoDescription = desc;
    }
    public String getVideoSeriesId() { return videoSeriesId; }
    public String getVideoSeriesName() { return videoSeriesName; }
    public String getVideoSurfaceImage() { return videoSurfaceImage; }
    public String getVideoDescription() { return videoDescription; }
}
