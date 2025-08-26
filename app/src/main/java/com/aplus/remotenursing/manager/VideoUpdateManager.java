package com.aplus.remotenursing.manager;


import android.content.Context;
import android.text.TextUtils;

import com.aplus.remotenursing.common.FileDownloadUtil;
import com.aplus.remotenursing.models.UpdateVideoNotice;
import com.aplus.remotenursing.models.UpdateVideoResponse;
import com.aplus.remotenursing.helper.ApiClientHelper;
import com.google.gson.Gson;

import java.io.File;
import java.io.IOException;

import okhttp3.*;

public class VideoUpdateManager {
    private static final Gson gson = new Gson();

    /** 缓存目录与文件名 */
    public static File cacheDir(Context ctx) {
        File dir = new File(ctx.getExternalFilesDir(null), "video_cache");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }
    public static File videoFile(Context ctx, String videoId) {
        return new File(cacheDir(ctx), videoId + ".mp4");
    }
    public static File tmpFile(Context ctx, String videoId) {
        return new File(cacheDir(ctx), videoId + ".tmp");
    }

    /** 1) 拉取“未ACK”的最新公告（没有则返回 null） */
    public static UpdateVideoNotice fetchLatestUnacked(String baseUrl, String seriesId, String videoId, String userId) {
        HttpUrl url = HttpUrl.parse(baseUrl + "/api/videos/updates")
                .newBuilder()
                .addQueryParameter("series_id", seriesId)
                .addQueryParameter("video_id", videoId)
                .addQueryParameter("user_id", userId)
                .build();
        Request req = new Request.Builder().url(url).get().build();
        try (Response resp = ApiClientHelper.get().newCall(req).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) return null;
            UpdateVideoResponse ur = gson.fromJson(resp.body().charStream(), UpdateVideoResponse.class);
            if (ur == null || ur.notices == null || ur.notices.isEmpty()) return null;
            return ur.notices.get(0); // 后端已按时间倒序 LIMIT 1
        } catch (IOException e) {
            return null;
        }
    }

    /** 2) 下载并替换（成功返回本地文件），同时校验 size 与 md5（如有） */
    public static File ensureUpdated(Context ctx, UpdateVideoNotice n) {
        File tmp = tmpFile(ctx, n.video_id);
        if (!FileDownloadUtil.downloadTo(n.download_url, tmp)) return null;

        if (n.file_size != null && n.file_size > 0 && tmp.length() != n.file_size) return null;
        if (!TextUtils.isEmpty(n.md5)) {
            String m = FileDownloadUtil.md5(tmp);
            if (!TextUtils.equals(m, n.md5)) return null;
        }
        File dst = videoFile(ctx, n.video_id);
        if (!FileDownloadUtil.atomicReplace(tmp, dst)) return null;
        return dst;
    }

    /** 3) ACK（成功/失败） */
    public static void ack(String baseUrl, long noticeId, String userId, String videoId, boolean success, String failReason) {
        HttpUrl url = HttpUrl.parse(baseUrl + "/api/videos/updates/" + noticeId + "/ack");
        String json = "{\"user_id\":\""+userId+"\",\"video_id\":\""+videoId+"\","
                + "\"status\":\""+(success?"success":"failed")+"\","
                + "\"fail_reason\":\""+(failReason==null?"":failReason.replace("\"","'"))+"\"}";
        RequestBody body = RequestBody.create(json, MediaType.parse("application/json; charset=utf-8"));
        Request req = new Request.Builder().url(url).post(body).build();
        try { ApiClientHelper.get().newCall(req).execute().close(); } catch (Exception ignore) {}
    }
}
