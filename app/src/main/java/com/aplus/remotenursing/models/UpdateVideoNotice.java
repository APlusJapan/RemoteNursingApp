package com.aplus.remotenursing.models;

public class UpdateVideoNotice {
    public long notice_id;
    public String video_id;
    public String download_url;
    public Long file_size; // 可能为 null
    public String md5;     // 可能为 null
    public String memo;
}
