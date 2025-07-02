package com.aplus.remotenursing.models;

public class VedioTask {
    private String vedioSeriesId;
    private String vedioSeriesName;
    private String vedioSurfaceImage;
    private String vedioDescription;

    // 必须要有空构造方法
    public VedioTask() {}

    public VedioTask(String id, String name, String img, String desc) {
        this.vedioSeriesId = id;
        this.vedioSeriesName = name;
        this.vedioSurfaceImage = img;
        this.vedioDescription = desc;
    }
    public String getVedioSeriesId() { return vedioSeriesId; }
    public String getVedioSeriesName() { return vedioSeriesName; }
    public String getVedioSurfaceImage() { return vedioSurfaceImage; }
    public String getVedioDescription() { return vedioDescription; }
}
