package com.aplus.remotenursing;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.aplus.remotenursing.common.ApiConfig;
import com.aplus.remotenursing.common.UserUtils;
import com.aplus.remotenursing.common.VideoPlayUtils;
import com.aplus.remotenursing.manager.VideoCacheManager;
import com.aplus.remotenursing.manager.VideoPlayHistoryManager;
import com.aplus.remotenursing.models.VideoTaskDetail;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.IOException;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 全屏播放器（对齐 VideoTaskDetailFragment 的统计/上传策略）
 */
public class VideoFullscreenPlayerActivity extends AppCompatActivity {

    private static final String TAG = "VideoFS";

    public static final String EXTRA_URL             = "video_url";
    public static final String EXTRA_START_POS       = "video_start_pos";
    public static final String EXTRA_START_PLAYREADY = "video_start_playready";
    public static final String EXTRA_END_POS         = "video_end_pos";
    public static final String EXTRA_END_PLAYREADY   = "video_end_playready";

    public static final String EXTRA_VIDEO_SERIES_ID     = "video_series_id";
    public static final String EXTRA_CURRENT_VIDEO_INDEX = "current_video_index";
    public static final String EXTRA_VIDEO_ID            = "video_id"; // 首条显式ID

    private PlayerView playerView;
    private ExoPlayer  player;
    private TextView   btnExitFs;

    private List<VideoTaskDetail> videoList;
    private int currentVideoIndex = 0;
    private String videoSeriesId;

    private final Gson gson = new Gson();
    private VideoCacheManager cacheManager;

    // 首条 intent 参数
    private String initialUrlFromIntent;
    private String initialVideoIdFromIntent;

    // 播放历史
    private VideoPlayHistoryManager playHistoryManager;
    private long    lastReadyStartMs = 0L;          // 本条 READY 的时间点（用于阈值/调试）
    private long    lastVideoStartWall = 0L;        // 当前条开始播放的 wall time
    private boolean hasRecordedForThisItem = false; // 本条是否已按阈值成功上报过

    // 便于拼接 recordVideoPlay 的字段（尽量从列表取，不行再兜底）
    private String  currentVideoId;
    private String  currentVideoTitle;
    private String  currentVideoDuration; // 形如 "00:10:23" 或你后台习惯的格式（直接透传）
    private String  currentSeriesId;      // = videoSeriesId
    private String  currentSeriesName;    // 若模型没给，传空串即可

    private boolean keepScreenOnApplied;

    private final OkHttpClient http = new OkHttpClient();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        cacheManager = VideoCacheManager.getInstance(getApplicationContext());
        playHistoryManager = VideoPlayHistoryManager.getInstance(getApplicationContext());
        Log.d(TAG, "onCreate: cacheManager/init ok");

        // 沉浸式
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_video_fullscreen_player);
        applyFullscreen();

        playerView = findViewById(R.id.fs_player_view);
        btnExitFs  = findViewById(R.id.btn_exit_fullscreen);

        // 取参数
        Intent it = getIntent();
        initialVideoIdFromIntent = it.getStringExtra(EXTRA_VIDEO_ID);
        String url = it.getStringExtra(EXTRA_URL);
        initialUrlFromIntent = url;
        long   pos = it.getLongExtra(EXTRA_START_POS, 0L);
        boolean playWhenReady = it.getBooleanExtra(EXTRA_START_PLAYREADY, true);

        videoSeriesId = it.getStringExtra(EXTRA_VIDEO_SERIES_ID);
        currentSeriesId = videoSeriesId; // 直接带给历史管理器
        currentVideoIndex = it.getIntExtra(EXTRA_CURRENT_VIDEO_INDEX, 0);

        if (TextUtils.isEmpty(url)) {
            Log.e(TAG, "首条URL为空，直接 finish");
            finish();
            return;
        }

        // 初始化播放器
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);

        // 统一back出口
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                exitWithResultAndFinish();
            }
        });

        // 监听
        player.addListener(new Player.Listener() {

            @Override public void onIsPlayingChanged(boolean isPlaying) {
                Log.d(TAG, "onIsPlayingChanged=" + isPlaying);
                setKeepScreenOn(isPlaying);
                if (isPlaying) {
                    // 首次开始这条
                    if (lastVideoStartWall == 0L) {
                        lastVideoStartWall = System.currentTimeMillis();
                        Log.d(TAG, "开始计时本条播放 wallTime=" + lastVideoStartWall);
                    }
                }
            }

            @Override public void onPlaybackStateChanged(int state) {
                Log.d(TAG, "onPlaybackStateChanged state=" + state);
                if (state == Player.STATE_READY) {
                    lastReadyStartMs = SystemClock.elapsedRealtime();
                    // 每次进入 READY 视为“新一条/切回一条”的开始
                    lastVideoStartWall = System.currentTimeMillis();
                    hasRecordedForThisItem = false;
                    logPlayingUri(player.getCurrentMediaItemIndex());
                } else if (state == Player.STATE_ENDED) {
                    Log.d(TAG, "STATE_ENDED 触发");
                    // 结束时满足阈值即记一次（带去重）
                    maybeRecordCurrentByThreshold("ended");
                    // 自动下一条（保持与你小屏一致）
                    playNextVideo();
                    setKeepScreenOn(false);
                }
            }

            @Override public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
                Log.d(TAG, "onMediaItemTransition reason=" + reason);
                // 切换前的那条：若已播放足够，也记录一次
                maybeRecordCurrentByThreshold("transition");
                // 新条：尝试命中本地缓存
                int cur = player.getCurrentMediaItemIndex();
                if (cur != C.INDEX_UNSET) {
                    maybeSwapToLocal(cur);
                }
                // 重置跨回调标志
                hasRecordedForThisItem = false;
                lastReadyStartMs = SystemClock.elapsedRealtime();
                lastVideoStartWall = System.currentTimeMillis();

                // 同步当前条的描述字段（用于 recordVideoPlay）
                syncCurrentMetaFromListOrTag();
            }
        });

        // 拉列表并开始
        if (!TextUtils.isEmpty(videoSeriesId)) {
            loadVideoListAndPlay(url, pos, playWhenReady);
        } else {
            playVideo(url, /*explicitId*/ null, pos, playWhenReady);
        }

        btnExitFs.setOnClickListener(v -> exitWithResultAndFinish());
    }

    // -------- 全屏/沉浸式 --------
    private void applyFullscreen() {
        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        );
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyFullscreen();
    }

    private void setKeepScreenOn(boolean on) {
        if (on) {
            if (!keepScreenOnApplied) {
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                keepScreenOnApplied = true;
                Log.d(TAG, "FLAG_KEEP_SCREEN_ON 开启");
            }
        } else if (keepScreenOnApplied) {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            keepScreenOnApplied = false;
            Log.d(TAG, "FLAG_KEEP_SCREEN_ON 关闭");
        }
    }

    // -------- 列表加载 / 播放 --------
    private void loadVideoListAndPlay(String initialUrl, long pos, boolean playWhenReady) {
        String url = ApiConfig.API_VIDEO_DETAIL_BY_SERIES_ID + videoSeriesId;
        Request request = new Request.Builder().url(url).build();
        Log.d(TAG, "拉取系列列表: " + url);

        http.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                Log.e(TAG, "拉取列表失败: " + e.getMessage());
                runOnUiThread(() -> playVideo(initialUrl, /*explicitId*/ null, pos, playWhenReady));
            }
            @Override public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    Log.e(TAG, "拉取列表返回非200");
                    runOnUiThread(() -> playVideo(initialUrl, /*explicitId*/ null, pos, playWhenReady));
                    return;
                }
                String json = response.body().string();
                List<VideoTaskDetail> list =
                        gson.fromJson(json, new TypeToken<List<VideoTaskDetail>>(){}.getType());
                runOnUiThread(() -> {
                    videoList = list;
                    Log.d(TAG, "列表条数: " + (videoList == null ? 0 : videoList.size()));
                    playVideo(initialUrl, /*explicitId*/ null, pos, playWhenReady);
                });
            }
        });
    }

    private void playVideo(String videoUrl, long pos, boolean playWhenReady) {
        playVideo(videoUrl, /*explicitId*/ null, pos, playWhenReady);
    }

    private void playVideo(String videoUrl, @Nullable String explicitVideoId, long pos, boolean playWhenReady) {
        if (player == null || TextUtils.isEmpty(videoUrl)) return;

        // 选 videoId：显式 → 首条匹配 → 由URL推断
        String videoId = !TextUtils.isEmpty(explicitVideoId)
                ? explicitVideoId
                : ((!TextUtils.isEmpty(initialVideoIdFromIntent) && TextUtils.equals(videoUrl, initialUrlFromIntent))
                ? initialVideoIdFromIntent
                : VideoPlayUtils.videoIdFromUrl(videoUrl));

        // 同步当前元数据（用于 recordVideoPlay）
        currentVideoId = videoId;
        currentSeriesId = videoSeriesId;
        currentSeriesName = ""; // 模型不一定有系列名字段，先置空
        currentVideoTitle = null;
        currentVideoDuration = null;

        if (videoList != null) {
            for (int i = 0; i < videoList.size(); i++) {
                VideoTaskDetail v = videoList.get(i);
                if (v == null) continue;
                String id = !TextUtils.isEmpty(v.getVideoId()) ? v.getVideoId() : VideoPlayUtils.videoIdFromUrl(v.getVideoURL());
                if (TextUtils.equals(id, videoId) || TextUtils.equals(v.getVideoURL(), videoUrl)) {
                    currentVideoTitle    = safeGetName(v);
                    currentVideoDuration = safeGetDuration(v);
                    currentVideoIndex    = i;
                    Log.d(TAG, "匹配到列表项 index=" + i + ", title=" + currentVideoTitle + ", duration=" + currentVideoDuration);
                    break;
                }
            }
        }

        // 本地优先
        Uri playable = resolvePlayableUri(videoUrl, videoId);

        MediaItem item = new MediaItem.Builder()
                .setUri(playable)
                .setMediaId(videoId)
                .setTag(new VideoTag(videoId, videoUrl))
                .build();

        player.setMediaItem(item);
        maybeSwapToLocal(0);
        player.prepare();
        player.seekTo(pos);
        player.setPlayWhenReady(playWhenReady);

        // 如果是网络资源，后台缓存当前 + 预取下一条
        if (isNetworkUri(playable)) {
            VideoCacheManager.getInstance(this).downloadAndCacheVideo(
                    videoId, videoUrl, new VideoCacheManager.DownloadCallback() {
                        @Override public void onStart(String id) { Log.d("VideoCache", "开始缓存当前: " + id); }
                        @Override public void onProgress(String id, int progress) { /* 降噪 */ }
                        @Override public void onSuccess(String id, String localPath) {
                            Log.d("VideoCache", "当前缓存完成: " + id + " -> " + localPath);
                            int cur = player.getCurrentMediaItemIndex();
                            if (cur != C.INDEX_UNSET) maybeSwapToLocal(cur);
                        }
                        @Override public void onError(String id, String error) {
                            Log.w("VideoCache", "当前缓存失败: " + error);
                        }
                    }
            );
        }

        prefetchNextInSeries();

        // 新条开始：复位统计标志/时间
        hasRecordedForThisItem = false;
        lastReadyStartMs = SystemClock.elapsedRealtime();
        lastVideoStartWall = System.currentTimeMillis();

        Log.d(TAG, "开始播放: id=" + videoId + ", url=" + videoUrl + ", pos=" + pos + ", play=" + playWhenReady);
    }

    private void prefetchNextInSeries() {
        if (videoList == null || videoList.isEmpty()) return;
        int nextIndex = (currentVideoIndex >= videoList.size() - 1) ? 0 : currentVideoIndex + 1;
        VideoTaskDetail next = videoList.get(nextIndex);
        if (next == null || TextUtils.isEmpty(next.getVideoURL())) return;

        String nextUrl = next.getVideoURL();
        String nextId  = !TextUtils.isEmpty(next.getVideoId()) ? next.getVideoId() : VideoPlayUtils.videoIdFromUrl(nextUrl);

        Log.d("VideoCache", "预取下一条: index=" + nextIndex + ", id=" + nextId);
        VideoCacheManager.getInstance(this).downloadAndCacheVideo(nextId, nextUrl, new VideoCacheManager.DownloadCallback() {
            @Override public void onStart(String id) {}
            @Override public void onProgress(String id, int progress) {}
            @Override public void onSuccess(String id, String localPath) { Log.d("VideoCache", "下一条缓存完成: " + localPath); }
            @Override public void onError(String id, String error) { Log.w("VideoCache", "下一条缓存失败: " + error); }
        });
    }

    private void playNextVideo() {
        if (videoList == null || videoList.isEmpty()) return;
        int next = (currentVideoIndex >= videoList.size() - 1) ? 0 : (currentVideoIndex + 1);
        VideoTaskDetail v = videoList.get(next);
        if (v != null && !TextUtils.isEmpty(v.getVideoURL())) {
            currentVideoIndex = next;
            playVideo(v.getVideoURL(), v.getVideoId(), 0L, true);
        }
    }

    // -------- 统计：阈值 + 多时机去重（与小屏一致） --------

    /** >5s 或 >10% 才算一次有效观看 */
    private boolean playedEnoughToCount() {
        if (player == null) return false;
        long pos = Math.max(0L, player.getCurrentPosition());
        long dur = Math.max(0L, player.getDuration());
        boolean ok = pos >= 5000 || (dur > 0 && pos >= (dur / 10));
        Log.d(TAG, "playedEnough? pos=" + pos + ", dur=" + dur + " => " + ok);
        return ok;
    }

    /** 在 END/切换/退出/暂停 等时机调用：满足阈值才记一次；同一条只记一次 */
    private void maybeRecordCurrentByThreshold(String scene) {
        if (player == null) return;
        if (hasRecordedForThisItem) {
            Log.d(TAG, "maybeRecordCurrentByThreshold - 已记录过本条，scene=" + scene);
            return;
        }
        if (!playedEnoughToCount()) {
            Log.d(TAG, "maybeRecordCurrentByThreshold - 未达阈值，scene=" + scene);
            return;
        }

        syncCurrentMetaFromListOrTag();

        String uid = UserUtils.loadUserId(this);
        if (TextUtils.isEmpty(uid)) {
            Log.w(TAG, "未登录，跳过统计 scene=" + scene);
            return;
        }

        String vid = currentVideoId;
        if (TextUtils.isEmpty(vid)) {
            // 尝试兜底从当前 MediaItem 取
            MediaItem mi = player.getCurrentMediaItem();
            if (mi != null) {
                if (!TextUtils.isEmpty(mi.mediaId)) vid = mi.mediaId;
                if (TextUtils.isEmpty(vid) && mi.localConfiguration != null) {
                    Uri u = mi.localConfiguration.uri;
                    if (u != null) vid = VideoPlayUtils.videoIdFromUrl(u.toString());
                }
            }
        }
        if (TextUtils.isEmpty(vid)) {
            Log.w(TAG, "找不到当前 videoId，放弃记录 scene=" + scene);
            return;
        }

        String videoName     = (currentVideoTitle == null ? "" : currentVideoTitle);
        String videoDuration = (currentVideoDuration == null ? "" : currentVideoDuration);
        String seriesId      = (currentSeriesId == null ? "" : currentSeriesId);
        String seriesName    = (currentSeriesName == null ? "" : currentSeriesName);

        Log.d(TAG, ">>>>>>> 记录视频播放 (" + scene + ") <<<<<<<");
        Log.d(TAG, "  videoId=" + vid);
        Log.d(TAG, "  videoName=" + videoName);
        Log.d(TAG, "  seriesId=" + seriesId);
        Log.d(TAG, "  seriesName=" + seriesName);
        Log.d(TAG, "  duration=" + videoDuration);

        try {
            playHistoryManager.recordVideoPlay(
                    vid,
                    seriesId,
                    videoName,
                    seriesName,
                    videoDuration
            );
            hasRecordedForThisItem = true;
            Log.d(TAG, "记录完成，打印本地记录快照：");
            playHistoryManager.logAllLocalData();
        } catch (Throwable t) {
            Log.e(TAG, "记录异常: " + t.getMessage());
        }
    }

    /** 同步当前条的 meta 信息（从列表或 mediaItem.tag） */
    private void syncCurrentMetaFromListOrTag() {
        try {
            int curIdx = player.getCurrentMediaItemIndex();
            MediaItem mi = player.getCurrentMediaItem();
            String vid = null, url = null;

            if (mi != null) {
                if (!TextUtils.isEmpty(mi.mediaId)) vid = mi.mediaId;
                if (mi.localConfiguration != null) {
                    Uri u = mi.localConfiguration.uri;
                    url = u == null ? null : u.toString();
                }
                Object tag = (mi.localConfiguration != null) ? mi.localConfiguration.tag : null;
                if (tag instanceof VideoTag) {
                    vid = ((VideoTag) tag).id;
                    url = ((VideoTag) tag).url;
                } else if (tag instanceof VideoTaskDetail) {
                    VideoTaskDetail d = (VideoTaskDetail) tag;
                    vid = !TextUtils.isEmpty(d.getVideoId()) ? d.getVideoId() : VideoPlayUtils.videoIdFromUrl(d.getVideoURL());
                    url = d.getVideoURL();
                    currentVideoTitle    = safeGetName(d);
                    currentVideoDuration = safeGetDuration(d);
                }
            }
            if (TextUtils.isEmpty(vid) && !TextUtils.isEmpty(url)) vid = VideoPlayUtils.videoIdFromUrl(url);

            // 从列表对齐标题/时长
            if (videoList != null && curIdx >= 0 && curIdx < videoList.size()) {
                VideoTaskDetail d = videoList.get(curIdx);
                if (d != null) {
                    if (TextUtils.isEmpty(vid)) {
                        String idFromList = !TextUtils.isEmpty(d.getVideoId()) ? d.getVideoId() : VideoPlayUtils.videoIdFromUrl(d.getVideoURL());
                        vid = idFromList;
                    }
                    currentVideoTitle    = safeGetName(d);
                    currentVideoDuration = safeGetDuration(d);
                    currentVideoIndex    = curIdx;
                }
            }

            currentVideoId = vid;
            currentSeriesId = videoSeriesId; // 已在 onCreate 拿到
        } catch (Throwable ignore) {}
    }

    private String safeGetName(VideoTaskDetail v) {
        try { return v.getVideoName(); } catch (Throwable t) { return ""; }
    }
    private String safeGetDuration(VideoTaskDetail v) {
        try { return v.getVideoDuration(); } catch (Throwable t) { return ""; }
    }

    // -------- 缓存：命中/替换/预取 --------
    private Uri resolvePlayableUri(String videoUrl, String videoId) {
        try {
            String local = VideoCacheManager.getInstance(this).getLocalVideoPath(videoId, videoUrl);
            if (!TextUtils.isEmpty(local)) {
                Log.d("VideoCache", "命中本地缓存: " + local);
                return Uri.fromFile(new File(local));
            }
        } catch (Throwable ignore) {}
        return Uri.parse(videoUrl);
    }

    private boolean isNetworkUri(Uri uri) {
        if (uri == null) return false;
        String s = uri.getScheme();
        return "http".equalsIgnoreCase(s) || "https".equalsIgnoreCase(s);
    }

    private void maybeSwapToLocal(int index) {
        if (player == null || index < 0 || index >= player.getMediaItemCount()) return;

        MediaItem cur = player.getMediaItemAt(index);
        if (cur == null || cur.localConfiguration == null) return;

        Uri uri = cur.localConfiguration.uri;
        if (uri == null) return;
        if ("file".equalsIgnoreCase(uri.getScheme())) return;

        String wantVideoId;
        String wantUrl = uri.toString();
        Object tagObj = cur.localConfiguration.tag;
        if (tagObj instanceof VideoTag) {
            wantVideoId = ((VideoTag) tagObj).id;
            wantUrl     = ((VideoTag) tagObj).url;
        } else {
            wantVideoId = VideoPlayUtils.videoIdFromUrl(wantUrl);
        }

        String localPath = VideoCacheManager.getInstance(getApplicationContext())
                .getLocalVideoPath(wantVideoId, wantUrl);
        if (TextUtils.isEmpty(localPath)) {
            Log.d("VideoCache", "暂未缓存，无需替换 index=" + index);
            return;
        }

        Uri localUri = Uri.fromFile(new File(localPath));
        MediaItem localItem = cur.buildUpon().setUri(localUri).build();

        boolean isCurrent = index == player.getCurrentMediaItemIndex();
        long pos = isCurrent ? Math.max(0, player.getCurrentPosition()) : 0L;
        boolean playReady = player.getPlayWhenReady();

        player.removeMediaItem(index);
        player.addMediaItem(index, localItem);

        if (isCurrent) {
            player.seekTo(index, pos);
            player.setPlayWhenReady(playReady);
        }

        Log.i("VideoCache", "切换为本地播放 index=" + index + " -> " + localUri);
    }

    private void logPlayingUri(int index) {
        try {
            MediaItem mi = player.getMediaItemAt(index);
            Uri u = (mi != null && mi.localConfiguration != null) ? mi.localConfiguration.uri : null;
            Log.d("VideoCache", "当前播放URI: " + (u == null ? "null" : u.toString()));
        } catch (Throwable ignore) {}
    }

    // -------- 退出/生命周期 --------
    private void exitWithResultAndFinish() {
        // 退出前：若已达到阈值且尚未记录，补一次
        maybeRecordCurrentByThreshold("exit");
        setKeepScreenOn(false);

        long curPos = player != null ? player.getCurrentPosition() : 0L;
        boolean playWhenReady = player != null && player.getPlayWhenReady();

        Intent data = new Intent()
                .putExtra(EXTRA_END_POS, curPos)
                .putExtra(EXTRA_END_PLAYREADY, playWhenReady)
                .putExtra(EXTRA_CURRENT_VIDEO_INDEX, currentVideoIndex);

        Log.d(TAG, "exitWithResult: pos=" + curPos + ", play=" + playWhenReady + ", index=" + currentVideoIndex);
        setResult(RESULT_OK, data);
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyFullscreen();
        // 与小屏对齐：进入前台时上传“到昨天为止”的记录
        try {
            Log.d(TAG, "onResume -> uploadAndClearRecords()");
            playHistoryManager.uploadAndClearRecords();
        } catch (Throwable t) {
            Log.w(TAG, "uploadAndClearRecords 异常: " + t.getMessage());
        }
    }

    @Override
    protected void onPause() {
        // 暂停场景也尝试补记一次（比如用户直接切应用/锁屏）
        maybeRecordCurrentByThreshold("pause");
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        setKeepScreenOn(false);
        // 销毁前兜底再尝试一次
        maybeRecordCurrentByThreshold("destroy");
        if (player != null) {
            player.release();
            player = null;
        }
    }

    // -------- 附属类型 --------
    public static final class VideoTag {
        public final String id;
        public final String url;
        public VideoTag(String id, String url) { this.id = id; this.url = url; }
    }
}
