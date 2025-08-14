package com.aplus.remotenursing.common;

import android.content.Context;
import android.net.Uri;

import com.aplus.remotenursing.VideoFullscreenPlayerActivity;
import com.aplus.remotenursing.manager.VideoCacheManager;
import com.google.android.exoplayer2.MediaItem;

import java.io.File;
import java.security.MessageDigest;

public final class VideoPlayUtils {

    private VideoPlayUtils() {}

    /** 用 URL 生成稳定的 videoId（与 Activity 里同一实现） */
    public static String videoIdFromUrl(String url) {
        if (url == null || url.isEmpty()) return "VID_UNKNOWN";
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] dig = md.digest(url.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder("VID_");
            for (byte b : dig) sb.append(String.format(java.util.Locale.US, "%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "VID_" + Math.abs(url.hashCode());
        }
    }

    /**
     * 本地优先构建带 tag 的 MediaItem：
     * - 命中缓存：setUri(file://...)
     * - 否则：setUri(http/https)，并把 {id,url} 放进 tag
     */
    public static MediaItem buildTaggedItem(Context ctx, String videoUrl) {
        String vid = videoIdFromUrl(videoUrl);
        String local = VideoCacheManager.getInstance(ctx).getLocalVideoPath(vid, videoUrl);
        Uri u = (local != null) ? Uri.fromFile(new File(local)) : Uri.parse(videoUrl);
        return new MediaItem.Builder()
                .setUri(u)
                .setMediaId(vid)
                .setTag(new VideoFullscreenPlayerActivity.VideoTag(vid, videoUrl))
                .build();
    }
}
