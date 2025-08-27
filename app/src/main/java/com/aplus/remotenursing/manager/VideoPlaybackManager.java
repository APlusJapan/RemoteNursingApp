package com.aplus.remotenursing.manager;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import com.aplus.remotenursing.common.ApiConfig;
import com.aplus.remotenursing.common.VideoPlayUtils;
import com.aplus.remotenursing.models.VideoTaskDetail;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 视频播放管理器 - 统一处理缓存、状态回传等功能
 */
public class VideoPlaybackManager {
    private static final String TAG = "VideoPlaybackManager";

    private static VideoPlaybackManager instance;

    // 核心组件
    private final Context context;
    private final VideoCacheManager cacheManager;
    private final VideoPlayHistoryManager playHistoryManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();
    private final OkHttpClient httpForUpdates = new OkHttpClient();
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    // 预加载管理
    private final Set<String> preloading = new HashSet<>();
    private final Map<String, Integer> id2Index = new HashMap<>();

    // 播放统计相关
    private long lastReadyStartMs = 0L;
    private boolean hasRecordedForThisItem = false;

    private VideoPlaybackManager(Context context) {
        this.context = context.getApplicationContext();
        this.cacheManager = VideoCacheManager.getInstance(context);
        this.playHistoryManager = VideoPlayHistoryManager.getInstance(context);
    }

    public static synchronized VideoPlaybackManager getInstance(Context context) {
        if (instance == null) {
            instance = new VideoPlaybackManager(context);
        }
        return instance;
    }

    // ===================== 缓存管理相关 =====================

    /**
     * 获取本地缓存路径
     */
    public String getLocalVideoPath(String videoId, String url) {
        if (cacheManager == null) return null;
        return cacheManager.getLocalVideoPath(videoId, url);
    }

    /**
     * 构建播放URI，优先使用本地缓存
     */
    public Uri buildPlayableUri(String videoUrl, String videoId) {
        if (TextUtils.isEmpty(videoId)) {
            videoId = VideoPlayUtils.videoIdFromUrl(videoUrl);
        }

        String localPath = getLocalVideoPath(videoId, videoUrl);
        if (localPath != null) {
            return Uri.fromFile(new File(localPath));
        }
        return Uri.parse(videoUrl);
    }

    /**
     * 带UI联动地下载当前视频
     */
    public void downloadCurrentVideoWithUi(@NonNull VideoTaskDetail item,
                                           @Nullable DownloadProgressCallback progressCallback) {
        if (cacheManager == null) return;

        final String url = item.getVideoURL();
        String vid = item.getVideoId();
        if (TextUtils.isEmpty(vid)) {
            vid = VideoPlayUtils.videoIdFromUrl(url);
        }
        final String videoId = vid;

        // 已有本地文件就不再下
        String local = cacheManager.getLocalVideoPath(videoId, url);
        if (local != null) {
            if (progressCallback != null) {
                mainHandler.post(() -> {
                    progressCallback.onHideProgress();
                    progressCallback.onStatusUpdate("已缓存");
                });
            }
            return;
        }

        // UI：显示进度条、按钮文案
        if (progressCallback != null) {
            mainHandler.post(() -> {
                progressCallback.onShowProgress();
                progressCallback.onStatusUpdate("缓存中…");
            });
        }

        cacheManager.downloadAndCacheVideo(videoId, url, new VideoCacheManager.DownloadCallback() {
            @Override public void onStart(String id) {
                if (progressCallback != null) {
                    mainHandler.post(() -> progressCallback.onProgressUpdate(0));
                }
            }
            @Override public void onProgress(String id, int progress) {
                if (progressCallback != null) {
                    mainHandler.post(() -> progressCallback.onProgressUpdate(progress));
                }
            }
            @Override public void onSuccess(String id, String localPath) {
                mainHandler.post(() -> {
                    if (progressCallback != null) {
                        progressCallback.onHideProgress();
                        progressCallback.onStatusUpdate("已缓存");
                        progressCallback.onCacheInfoUpdate();
                        progressCallback.onSwapToLocal(id2Index.get(id));
                    }
                });
            }
            @Override public void onError(String id, String error) {
                Log.w(TAG, "下载失败: " + error);

                // 仅在"目标被占用/重命名失败"时，采取跳转下一条策略
                boolean maybeInUse = error != null && (error.contains("重命名缓存文件失败")
                        || error.contains("占用") || error.contains("in use") || error.contains("rename"));

                if (maybeInUse && progressCallback != null) {
                    Integer idxObj = id2Index.get(id);
                    if (idxObj != null) {
                        int idx = idxObj;
                        mainHandler.post(() -> {
                            progressCallback.onShowShortToast("视频正在更新缓存，已临时播放下一条…");
                            progressCallback.onSwitchToNext(idx);
                        });

                        // 切走后改用智能退避重试 promote
                        scheduleTryPromoteWithBackoff(videoId, url, idx, progressCallback);
                    }
                }

                mainHandler.post(() -> {
                    if (progressCallback != null) {
                        progressCallback.onHideProgress();
                        progressCallback.onStatusUpdate("未缓存");
                        if (!maybeInUse) {
                            progressCallback.onShowShortToast("缓存失败，请稍后重试");
                        }
                    }
                });
            }
        });
    }

    /**
     * 智能重试 tryPromoteNow
     */
    private void scheduleTryPromoteWithBackoff(@NonNull String videoId,
                                               @NonNull String url,
                                               int indexIfKnown,
                                               @Nullable DownloadProgressCallback callback) {
        long[] delays = new long[]{600, 1500, 3000};
        scheduleTryPromoteWithBackoffInternal(videoId, url, indexIfKnown, delays, 0, callback);
    }

    private void scheduleTryPromoteWithBackoffInternal(@NonNull String videoId,
                                                       @NonNull String url,
                                                       int indexIfKnown,
                                                       @NonNull long[] delays,
                                                       int attempt,
                                                       @Nullable DownloadProgressCallback callback) {
        if (attempt >= delays.length) return;
        long delay = delays[attempt];
        mainHandler.postDelayed(() -> {
            // 若仍在预加载，跳过本次尝试，直接进入下一轮退避
            if (preloading.contains(videoId)) {
                Log.i(TAG, "promote skipped (still preloading): " + videoId + ", attempt=" + (attempt + 1));
                scheduleTryPromoteWithBackoffInternal(videoId, url, indexIfKnown, delays, attempt + 1, callback);
                return;
            }
            boolean promoted = (cacheManager != null) && cacheManager.tryPromoteNow(videoId, url);
            Log.i(TAG, "tryPromoteNow attempt#" + (attempt + 1) + " after " + delay + "ms: " + promoted);
            if (promoted) {
                if (indexIfKnown >= 0 && callback != null) {
                    callback.onCacheInfoUpdate();
                    callback.onSwapToLocal(indexIfKnown);
                }
            } else {
                scheduleTryPromoteWithBackoffInternal(videoId, url, indexIfKnown, delays, attempt + 1, callback);
            }
        }, delay);
    }

    /**
     * 后台缓存视频
     */
    public void cacheVideoInBackground(VideoTaskDetail item) {
        if (cacheManager == null || item == null) return;
        final String url = item.getVideoURL();
        String vid = item.getVideoId();
        if (TextUtils.isEmpty(vid)) {
            vid = VideoPlayUtils.videoIdFromUrl(url);
        }
        final String videoId = vid;
        if (!preloading.add(videoId)) return; // 已在预加载

        new Thread(() -> {
            cacheManager.downloadAndCacheVideo(videoId, url,
                    new VideoCacheManager.DownloadCallback() {
                        private int last = -10;
                        @Override public void onStart(String id) {}
                        @Override public void onProgress(String id, int progress) {
                            if (progress - last >= 10) {
                                Log.d(TAG, "后台下载进度: " + progress + "%");
                                last = progress;
                            }
                        }
                        @Override public void onSuccess(String id, String localPath) {
                            preloading.remove(id);
                            Log.d(TAG, "后台缓存完成: " + localPath);
                        }
                        @Override public void onError(String id, String error) {
                            preloading.remove(id);
                            Log.w(TAG, "预加载失败: " + error);
                        }
                    });
        }).start();
    }

    /**
     * 预加载所有视频
     */
    public void preloadAllVideos(List<VideoTaskDetail> videoList, Runnable onStart) {
        if (cacheManager == null || videoList == null) return;

        if (onStart != null) onStart.run();

        Log.d(TAG, "开始预加载所有视频");
        for (VideoTaskDetail v : videoList) {
            cacheVideoInBackground(v);
        }
    }

    /**
     * 清理缓存
     */
    public void clearCache() {
        if (cacheManager == null) return;
        Log.d(TAG, "清理缓存");
        cacheManager.clearCache();
    }

    /**
     * 获取缓存大小
     */
    public String getCacheSizeFormatted() {
        if (cacheManager == null) return "0 MB";
        long sizeInBytes = cacheManager.getCacheSize();
        if (sizeInBytes < 1024L * 1024L) {
            return String.format("%.1f KB", sizeInBytes / 1024.0);
        } else if (sizeInBytes < 1024L * 1024L * 1024L) {
            return String.format("%.1f MB", sizeInBytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", sizeInBytes / (1024.0 * 1024.0 * 1024.0));
        }
    }

    // ===================== 播放统计相关 =====================

    /**
     * 设置索引映射
     */
    public void setIndexMapping(Map<String, Integer> mapping) {
        this.id2Index.clear();
        this.id2Index.putAll(mapping);
    }

    /**
     * 重置当前播放项的统计状态
     */
    public void resetPlaybackStats() {
        lastReadyStartMs = SystemClock.elapsedRealtime();
        hasRecordedForThisItem = false;
    }

    /**
     * 检查是否播放足够时长（阈值判断）
     */
    public boolean playedEnoughToCount(ExoPlayer player) {
        if (player == null) return false;
        long pos = Math.max(0L, player.getCurrentPosition());
        long dur = Math.max(0L, player.getDuration());
        return pos >= 5000 || (dur > 0 && pos >= dur / 10);
    }

    /**
     * 尝试记录当前视频播放（带去重和阈值）
     */
    public void maybeRecordCurrentVideoPlay(ExoPlayer player,
                                            VideoTaskDetail currentItem,
                                            String videoSeriesId,
                                            String videoSeriesName,
                                            String scene) {
        if (player == null || currentItem == null || playHistoryManager == null) return;

        if (hasRecordedForThisItem) {
            Log.d(TAG, "已记录过本条，scene=" + scene);
            return;
        }

        if (!playedEnoughToCount(player)) {
            Log.d(TAG, "未达阈值，scene=" + scene);
            return;
        }

        String videoId = currentItem.getVideoId();
        if (TextUtils.isEmpty(videoId)) {
            videoId = VideoPlayUtils.videoIdFromUrl(currentItem.getVideoURL());
        }

        Log.d(TAG, ">>>>>>> 记录视频播放 (" + scene + ") <<<<<<<");
        Log.d(TAG, "  VideoID: " + videoId);
        Log.d(TAG, "  视频名: " + currentItem.getVideoName());
        Log.d(TAG, "  系列ID: " + videoSeriesId);
        Log.d(TAG, "  系列名: " + videoSeriesName);

        playHistoryManager.recordVideoPlay(
                videoId,
                videoSeriesId,
                currentItem.getVideoName(),
                videoSeriesName,
                currentItem.getVideoDuration()
        );

        hasRecordedForThisItem = true;
        playHistoryManager.logAllLocalData();
    }

    /**
     * 上传并清理播放记录
     */
    public void uploadAndClearRecords() {
        if (playHistoryManager != null) {
            playHistoryManager.uploadAndClearRecords();
        }
    }

    /**
     * 打印所有本地播放数据（调试用）
     */
    public void logAllLocalData() {
        if (playHistoryManager != null) {
            playHistoryManager.logAllLocalData();
        }
    }

    /**
     * 获取记录数量
     */
    public int getRecordCount() {
        return playHistoryManager != null ? playHistoryManager.getRecordCount() : 0;
    }

    // ===================== Q-Ack 旁路更新相关 =====================

    // 存储从后台获取的更新通知列表
    private List<UpdateNoticeItem> pendingUpdates = new ArrayList<>();

    /**
     * 一次性从后台获取所有待更新的视频列表并保存
     */
    public void fetchAndCachePendingUpdates(String seriesId, String userId, String projectId, String teamId,
                                            @Nullable Runnable onComplete) {
        String updatesUrl = ApiConfig.API_VIDEO_UPDATE_NOTICE
                + "?series_id=" + Uri.encode(seriesId)
                + "&user_id=" + Uri.encode(userId);

        if (!TextUtils.isEmpty(projectId)) {
            updatesUrl += "&project_id=" + Uri.encode(projectId);
        }
        if (!TextUtils.isEmpty(teamId)) {
            updatesUrl += "&team_id=" + Uri.encode(teamId);
        }

        Log.d(TAG, "获取待更新视频列表URL: " + updatesUrl);

        Request req = new Request.Builder().url(updatesUrl).get().build();
        httpForUpdates.newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.w(TAG, "获取待更新视频列表失败: " + e.getMessage());
                if (onComplete != null) {
                    mainHandler.post(onComplete);
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response resp = response) {
                    if (!resp.isSuccessful() || resp.body() == null) {
                        Log.w(TAG, "获取待更新视频列表返回非成功状态码: " + resp.code());
                        if (onComplete != null) {
                            mainHandler.post(onComplete);
                        }
                        return;
                    }

                    UpdateResponse ur = gson.fromJson(resp.body().charStream(), UpdateResponse.class);
                    if (ur == null || ur.notices == null) {
                        Log.d(TAG, "无待更新视频");
                        pendingUpdates.clear();
                    } else {
                        pendingUpdates.clear();
                        pendingUpdates.addAll(ur.notices);
                        Log.i(TAG, "获取到 " + pendingUpdates.size() + " 个待更新视频:");
                        for (UpdateNoticeItem item : pendingUpdates) {
                            Log.i(TAG, "  - Video ID: " + item.video_id +
                                    ", Notice ID: " + item.notice_id +
                                    ", URL: " + item.download_url);
                        }
                    }

                    if (onComplete != null) {
                        mainHandler.post(onComplete);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "解析待更新视频列表异常: " + e.getMessage());
                    if (onComplete != null) {
                        mainHandler.post(onComplete);
                    }
                }
            }
        });
    }

    /**
     * 处理待更新视频：将待播放视频列表与待更新列表对比，启动更新任务
     */
    public void processVideoUpdatesInBackground(List<VideoTaskDetail> videoList,
                                                String userId,
                                                @Nullable UpdateProgressCallback callback) {
        if (pendingUpdates.isEmpty() || videoList == null || videoList.isEmpty()) {
            Log.d(TAG, "无待更新视频或视频列表为空，跳过更新处理");
            return;
        }

        Log.d(TAG, "开始处理视频更新，待播放视频数: " + videoList.size() + ", 待更新视频数: " + pendingUpdates.size());

        // 对比视频列表，找出需要更新的视频
        for (VideoTaskDetail video : videoList) {
            String videoId = !TextUtils.isEmpty(video.getVideoId())
                    ? video.getVideoId()
                    : VideoPlayUtils.videoIdFromUrl(video.getVideoURL());

            // 在待更新列表中查找匹配的视频
            for (UpdateNoticeItem updateItem : pendingUpdates) {
                if (TextUtils.equals(videoId, updateItem.video_id)) {
                    Log.i(TAG, "找到需要更新的视频: " + videoId + " -> " + updateItem.download_url);

                    // 启动后台更新任务
                    startVideoUpdateTask(video, updateItem, userId, callback);
                    break;
                }
            }
        }
    }

    /**
     * 启动单个视频的更新任务
     */
    private void startVideoUpdateTask(VideoTaskDetail video,
                                      UpdateNoticeItem updateItem,
                                      String userId,
                                      @Nullable UpdateProgressCallback callback) {
        String videoId = !TextUtils.isEmpty(video.getVideoId())
                ? video.getVideoId()
                : VideoPlayUtils.videoIdFromUrl(video.getVideoURL());

        String forceUrl = updateItem.download_url;
        long noticeId = updateItem.notice_id;

        Log.i(TAG, "开始更新视频: " + videoId + " (Notice ID: " + noticeId + ")");

        // 检查是否正在播放该视频
        Integer idxObj = id2Index.get(videoId);
        int idx = (idxObj == null ? -1 : idxObj);
        boolean isCurrentlyPlaying = callback != null && callback.isCurrentlyPlaying(idx);

        if (isCurrentlyPlaying) {
            // 如果正在播放，先切换到下一条
            Log.i(TAG, "视频 " + videoId + " 正在播放，先切换到下一条");
            if (callback != null) {
                mainHandler.post(() -> {
                    callback.onShowUpdateMessage("视频 " + video.getVideoName() + " 有更新，已临时切到下一条，后台更新中…");
                    callback.onSwitchToNext(idx);
                });
            }

            // 延迟开始强制更新
            mainHandler.postDelayed(() ->
                            forceRefreshVideoCache(videoId, forceUrl, idx, noticeId, userId, callback),
                    700);
        } else {
            // 直接后台强制更新
            Log.i(TAG, "视频 " + videoId + " 未在播放，直接后台更新");
            forceRefreshVideoCache(videoId, forceUrl, idx, noticeId, userId, callback);
        }
    }

    /**
     * 提取的强制刷新逻辑（下载 → 尝试提升 → ACK → UI收尾）
     */
    private void forceRefreshVideoCache(String videoIdForCache,
                                        String forceUrl,
                                        int idx,
                                        long noticeId,
                                        @NonNull String userId,
                                        @Nullable UpdateProgressCallback callback) {
        if (cacheManager == null) return;

        Log.i(TAG, "强制刷新视频缓存: " + videoIdForCache + " from " + forceUrl);

        cacheManager.downloadAndCacheVideoForce(videoIdForCache, forceUrl,
                new VideoCacheManager.DownloadCallback() {
                    @Override public void onStart(String id) {
                        Log.d(TAG, "开始强制下载视频: " + id);
                    }

                    @Override public void onProgress(String id, int progress) {
                        if (progress % 20 == 0) { // 每20%打印一次，减少日志量
                            Log.d(TAG, "强制下载进度 " + id + ": " + progress + "%");
                        }
                    }

                    @Override public void onSuccess(String id, String localPath) {
                        Log.i(TAG, "强制下载完成: " + id + " -> " + localPath);

                        // 统一使用智能退避 promote，并在成功后调用callback
                        scheduleTryPromoteWithBackoff(videoIdForCache, forceUrl, idx,
                                callback != null ? callback.asDownloadProgressCallback() : null);

                        // 成功ACK回执
                        ackUpdate(noticeId, userId, videoIdForCache, true, null);
                    }

                    @Override public void onError(String id, String error) {
                        Log.w(TAG, "强制下载失败: " + id + " -> " + error);

                        // 失败ACK回执
                        ackUpdate(noticeId, userId, videoIdForCache, false, "download_failed: " + error);
                    }
                });
    }

    /**
     * 回执 ACK：POST /api/videos/updates/{noticeId}/ack
     */
    private void ackUpdate(long noticeId, String userId, String videoId, boolean success, @Nullable String failReason) {
        try {
            String ackUrl = ApiConfig.API_VIDEO_UPDATE_RECEIPT + noticeId + "/ack";
            String payload = "{\"user_id\":\""+userId+"\",\"video_id\":\""+videoId+"\","
                    + "\"status\":\""+(success?"success":"failed")+"\","
                    + "\"fail_reason\":\""+(failReason==null?"":failReason.replace("\"","'"))+"\"}";
            RequestBody body = RequestBody.create(payload, JSON);
            Request req = new Request.Builder().url(ackUrl).post(body).build();

            Log.d(TAG, "发送ACK: " + ackUrl + " -> " + payload);

            httpForUpdates.newCall(req).enqueue(new Callback() {
                @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.w(TAG, "ACK 上报失败: noticeId=" + noticeId + ", error=" + e.getMessage());
                }
                @Override public void onResponse(@NonNull Call call, @NonNull Response response) {
                    Log.d(TAG, "ACK 上报完成: noticeId=" + noticeId + ", code=" + response.code());
                    response.close();
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "ACK 构造/发送异常: noticeId=" + noticeId + ", error=" + t.getMessage());
        }
    }

    // ===================== 内部类 =====================

    // 更新接口返回的模型
    private static class UpdateNoticeItem {
        long notice_id;
        String video_id;
        String download_url;
        String memo;
    }

    private static class UpdateResponse {
        List<UpdateNoticeItem> notices;
    }

    // ===================== 回调接口 =====================

    /**
     * 下载进度回调接口
     */
    public interface DownloadProgressCallback {
        void onShowProgress();
        void onHideProgress();
        void onProgressUpdate(int progress);
        void onStatusUpdate(String status);
        void onCacheInfoUpdate();
        void onSwapToLocal(Integer index);
        void onSwitchToNext(int currentIndex);
        void onShowShortToast(String message);
    }

    /**
     * 更新进度回调接口
     */
    public interface UpdateProgressCallback {
        boolean isCurrentlyPlaying(int index);
        void onShowUpdateMessage(String message);
        void onSwitchToNext(int currentIndex);
        DownloadProgressCallback asDownloadProgressCallback();
    }
}