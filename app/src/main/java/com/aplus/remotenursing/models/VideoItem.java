package com.aplus.remotenursing.models;

public class VideoItem {
    private String vedioId;
    private String vedioName;
    private String vedioURL;
    private String vedioDuration;
    private String vedioDescription;
    private String vedioSurfaceImage;

    public VideoItem() {}

    public String getVedioId() { return vedioId; }
    public void setVedioId(String vedioId) { this.vedioId = vedioId; }

    public String getVedioName() { return vedioName; }
    public void setVedioName(String vedioName) { this.vedioName = vedioName; }

    public String getVedioURL() { return vedioURL; }
    public void setVedioURL(String vedioURL) { this.vedioURL = vedioURL; }

    public String getVedioDuration() { return vedioDuration; }
    public void setVedioDuration(String vedioDuration) { this.vedioDuration = vedioDuration; }

    public String getVedioDescription() { return vedioDescription; }
    public void setVedioDescription(String vedioDescription) { this.vedioDescription = vedioDescription; }

    public String getVedioSurfaceImage() { return vedioSurfaceImage; }
    public void setVedioSurfaceImage(String vedioSurfaceImage) { this.vedioSurfaceImage = vedioSurfaceImage; }
}
