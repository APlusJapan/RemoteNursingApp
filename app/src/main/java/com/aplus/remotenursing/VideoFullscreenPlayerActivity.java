package com.aplus.remotenursing;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import com.aplus.remotenursing.manager.VideoPlaybackManager;
import com.aplus.remotenursing.models.VideoTaskDetail;
import com.aplus.remotenursing.models.UserAccount;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 全屏播放器（使用VideoPlaybackManager统一管理）
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
    public static final String EXTRA_VIDEO_ID            = "video_id"; // 首条显示ID

    private PlayerView playerView;
    private ExoPlayer  player;
    private TextView   btnExitFs;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private List<VideoTaskDetail> videoList;
    private int currentVideoIndex = 0;
    private String videoSeriesId;
    private String videoSeriesName;

    private final Gson gson = new Gson();

    // 首条 intent 参数
    private String initialUrlFromIntent;
    private String initialVideoIdFromIntent;

    // 统一管理器
    private VideoPlaybackManager playbackManager;
    private final Map<String, Integer> id2Index = new HashMap<>();

    // 播放列表管理
    private final List<MediaItem> mediaItems = new ArrayList<>();
    private boolean playerPrepared = false;

    private boolean keepScreenOnApplied;
    private final OkHttpClient http = new OkHttpClient();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 初始化统一管理器
        playbackManager = VideoPlaybackManager.getInstance(getApplicationContext());
        Log.d(TAG, "onCreate: VideoPlaybackManager initialized");

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
        currentVideoIndex = it.getIntExtra(EXTRA_CURRENT_VIDEO_INDEX, 0);

        Log.i(TAG, "onCreate 初始参数:");
        Log.i(TAG, "  - initialVideoId: " + initialVideoIdFromIntent);
        Log.i(TAG, "  - initialUrl: " + url);
        Log.i(TAG, "  - videoSeriesId: " + videoSeriesId);
        Log.i(TAG, "  - currentVideoIndex: " + currentVideoIndex);

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
            }

            @Override public void onPlaybackStateChanged(int state) {
                String stateName = "";
                switch (state) {
                    case Player.STATE_IDLE: stateName = "IDLE"; break;
                    case Player.STATE_BUFFERING: stateName = "BUFFERING"; break;
                    case Player.STATE_READY: stateName = "READY"; break;
                    case Player.STATE_ENDED: stateName = "ENDED"; break;
                }

                Log.d(TAG, "onPlaybackStateChanged: " + stateName + " (state=" + state + ")");
                Log.d(TAG, "  - currentVideoIndex: " + currentVideoIndex);
                Log.d(TAG, "  - playerCurrentIndex: " + player.getCurrentMediaItemIndex());

                if (state == Player.STATE_READY) {
                    playbackManager.resetPlaybackStats();
                    logPlayingUri(player.getCurrentMediaItemIndex());

                    // 播放列表模式下确保索引同步
                    int playerIndex = player.getCurrentMediaItemIndex();
                    if (playerPrepared && playerIndex >= 0 && playerIndex != currentVideoIndex) {
                        Log.w(TAG, "STATE_READY 时索引不同步！currentVideoIndex=" + currentVideoIndex + ", playerIndex=" + playerIndex + " - 同步索引");
                        currentVideoIndex = playerIndex;
                    }
                } else if (state == Player.STATE_ENDED) {
                    Log.d(TAG, "STATE_ENDED 触发");
                    // 结束时记录
                    maybeRecordCurrentByThreshold("ended");
                    // 自动下一条
                    playNextVideoInPlaylist();
                    setKeepScreenOn(false);
                }
            }

            @Override public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
                String reasonName = "";
                switch (reason) {
                    case Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT: reasonName = "REPEAT"; break;
                    case Player.MEDIA_ITEM_TRANSITION_REASON_AUTO: reasonName = "AUTO"; break;
                    case Player.MEDIA_ITEM_TRANSITION_REASON_SEEK: reasonName = "SEEK"; break;
                    case Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED: reasonName = "PLAYLIST_CHANGED"; break;
                }

                Log.d(TAG, "onMediaItemTransition: " + reasonName + " (reason=" + reason + ")");
                Log.d(TAG, "  - 切换前 currentVideoIndex: " + currentVideoIndex);

                // 切换前记录
                maybeRecordCurrentByThreshold("transition");

                // 播放列表模式下的索引更新
                int cur = player.getCurrentMediaItemIndex();
                Log.d(TAG, "  - 切换后 playerCurrentIndex: " + cur);

                if (cur != C.INDEX_UNSET && playerPrepared) {
                    // 关键修复：只在非PLAYLIST_CHANGED时更新索引
                    if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                        currentVideoIndex = cur;
                        Log.d(TAG, "  - 更新后 currentVideoIndex: " + currentVideoIndex);
                    } else {
                        Log.d(TAG, "  - PLAYLIST_CHANGED事件，保持 currentVideoIndex: " + currentVideoIndex);
                    }

                    maybeSwapToLocal(cur);
                }

                playbackManager.resetPlaybackStats();
                syncCurrentMetaFromListOrTag();
            }
        });

        // 拉列表并开始
        if (!TextUtils.isEmpty(videoSeriesId)) {
            loadVideoListAndPlay(url, pos, playWhenReady);
        } else {
            playVideoSingle(url, null, pos, playWhenReady);
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
                runOnUiThread(() -> playVideoSingle(initialUrl, null, pos, playWhenReady));
            }
            @Override public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    Log.e(TAG, "拉取列表返回非200");
                    runOnUiThread(() -> playVideoSingle(initialUrl, null, pos, playWhenReady));
                    return;
                }
                String json = response.body().string();
                List<VideoTaskDetail> list =
                        gson.fromJson(json, new TypeToken<List<VideoTaskDetail>>(){}.getType());
                runOnUiThread(() -> {
                    videoList = list;
                    Log.d(TAG, "列表条数: " + (videoList == null ? 0 : videoList.size()));

                    // 建立索引映射并设置到管理器
                    buildIndexMapping();
                    playbackManager.setIndexMapping(id2Index);

                    // 根据 initialVideoIdFromIntent 找到正确的起始索引
                    if (!TextUtils.isEmpty(initialVideoIdFromIntent)) {
                        Integer foundIndex = id2Index.get(initialVideoIdFromIntent);
                        if (foundIndex != null) {
                            currentVideoIndex = foundIndex;
                            Log.i(TAG, "根据 videoId 找到起始索引: " + currentVideoIndex + " (id=" + initialVideoIdFromIntent + ")");
                        }
                    }

                    // 构建播放列表
                    buildMediaItems();

                    // 处理视频更新
                    processVideoUpdates();

                    // 使用播放列表模式
                    playVideoWithPlaylist(pos, playWhenReady);
                });
            }
        });
    }

    private void buildIndexMapping() {
        id2Index.clear();
        if (videoList != null) {
            for (int i = 0; i < videoList.size(); i++) {
                VideoTaskDetail d = videoList.get(i);
                String vid = !TextUtils.isEmpty(d.getVideoId())
                        ? d.getVideoId()
                        : VideoPlayUtils.videoIdFromUrl(d.getVideoURL());
                id2Index.put(vid, i);
            }
        }
    }

    // 构建MediaItems列表
    private void buildMediaItems() {
        mediaItems.clear();
        if (videoList == null) return;

        Log.d(TAG, "buildMediaItems - 开始构建媒体项，视频数量: " + videoList.size());

        for (int i = 0; i < videoList.size(); i++) {
            VideoTaskDetail d = videoList.get(i);
            String url = d.getVideoURL();
            String vid = d.getVideoId();
            if (TextUtils.isEmpty(vid)) {
                vid = VideoPlayUtils.videoIdFromUrl(url);
            }

            Log.d(TAG, "处理视频 [" + i + "]: " + d.getVideoName());
            Log.d(TAG, "  - VideoID: " + vid);
            Log.d(TAG, "  - 原始URL: " + url);

            Uri uri = playbackManager.buildPlayableUri(url, vid);

            // 检查是否使用了缓存
            String scheme = uri.getScheme();
            boolean isLocalFile = "file".equalsIgnoreCase(scheme);
            Log.d(TAG, "  - 播放URI: " + uri.toString());
            Log.d(TAG, "  - 使用缓存: " + (isLocalFile ? "是" : "否"));

            MediaItem mi = new MediaItem.Builder()
                    .setUri(uri)
                    .setMediaId(vid)
                    .setTag(new VideoTag(vid, url))
                    .build();
            mediaItems.add(mi);
        }

        Log.d(TAG, "buildMediaItems - 完成，创建了 " + mediaItems.size() + " 个媒体项");
    }

    // 使用播放列表模式播放
    private void playVideoWithPlaylist(long pos, boolean playWhenReady) {
        if (player == null || mediaItems.isEmpty()) return;

        Log.d(TAG, "使用播放列表模式，起始索引: " + currentVideoIndex);

        // 设置播放列表
        player.setMediaItems(mediaItems, currentVideoIndex, pos);
        player.prepare();
        playerPrepared = true;
        player.setPlayWhenReady(playWhenReady);

        // 尝试替换为本地缓存
        maybeSwapToLocal(currentVideoIndex);

        // 后台缓存当前视频
        if (videoList != null && currentVideoIndex < videoList.size()) {
            VideoTaskDetail current = videoList.get(currentVideoIndex);
            if (current != null) {
                playbackManager.cacheVideoInBackground(current);
            }
        }

        // 预取下一条
        prefetchNextInSeries();

        // 重置统计标志
        playbackManager.resetPlaybackStats();

        Log.d(TAG, "播放列表设置完成");
    }

    // 保留原有的单视频播放方法（用于fallback）
    private void playVideoSingle(String videoUrl, @Nullable String explicitVideoId, long pos, boolean playWhenReady) {
        if (player == null || TextUtils.isEmpty(videoUrl)) return;

        // 选 videoId：显式 → 首条匹配 → 由URL推断
        String videoId = !TextUtils.isEmpty(explicitVideoId)
                ? explicitVideoId
                : ((!TextUtils.isEmpty(initialVideoIdFromIntent) && TextUtils.equals(videoUrl, initialUrlFromIntent))
                ? initialVideoIdFromIntent
                : VideoPlayUtils.videoIdFromUrl(videoUrl));

        // 本地优先
        Uri playable = playbackManager.buildPlayableUri(videoUrl, videoId);

        // 记录播放信息
        logVideoPlayInfo(videoId, videoUrl, playable);

        MediaItem item = new MediaItem.Builder()
                .setUri(playable)
                .setMediaId(videoId)
                .setTag(new VideoTag(videoId, videoUrl))
                .build();

        player.setMediaItem(item);
        player.prepare();
        playerPrepared = true;
        player.seekTo(pos);
        player.setPlayWhenReady(playWhenReady);

        // 如果是网络资源，后台缓存
        if (isNetworkUri(playable)) {
            playbackManager.cacheVideoInBackground(findVideoDetailById(videoId));
        }

        prefetchNextInSeries();
        playbackManager.resetPlaybackStats();

        Log.d(TAG, "单视频播放: id=" + videoId + ", url=" + videoUrl + ", pos=" + pos + ", play=" + playWhenReady);
    }

    // 兼容原有的playVideo方法调用
    private void playVideo(String videoUrl, @Nullable String explicitVideoId, long pos, boolean playWhenReady) {
        // 如果是播放列表模式，使用播放列表
        if (playerPrepared && !mediaItems.isEmpty()) {
            // 播放列表已准备，直接seek到位置
            player.seekTo(pos);
            player.setPlayWhenReady(playWhenReady);
        } else {
            // 单视频模式
            playVideoSingle(videoUrl, explicitVideoId, pos, playWhenReady);
        }
    }

    private void processVideoUpdates() {
        // 获取用户账户信息并处理视频更新
        try {
            UserAccount userAccount = UserUtils.getUserAccount(this);
            if (userAccount == null || videoSeriesId == null || videoList == null) {
                Log.d(TAG, "跳过视频更新：用户信息或视频列表缺失");
                return;
            }

            String userId = userAccount.getUserId();
            String projectId = userAccount.getProjectId();
            String teamId = userAccount.getTeamId();

            Log.d(TAG, "全屏播放器开始获取视频更新列表 - userId: " + userId +
                    ", projectId: " + projectId +
                    ", teamId: " + teamId +
                    ", seriesId: " + videoSeriesId);

            // 一次性从后台获取待更新视频列表并处理
            playbackManager.fetchAndCachePendingUpdates(videoSeriesId, userId, projectId, teamId, () -> {
                Log.d(TAG, "全屏播放器：待更新视频列表获取完成，开始处理更新");

                playbackManager.processVideoUpdatesInBackground(videoList, userId,
                        new VideoPlaybackManager.UpdateProgressCallback() {
                            @Override
                            public boolean isCurrentlyPlaying(int index) {
                                return player != null && player.getCurrentMediaItemIndex() == index;
                            }

                            @Override
                            public void onShowUpdateMessage(String message) {
                                runOnUiThread(() -> {
                                    // 可以显示Toast或其他提示
                                    Log.d(TAG, "更新提示: " + message);
                                });
                            }

                            @Override
                            public void onSwitchToNext(int currentIndex) {
                                runOnUiThread(() -> {
                                    int next = (currentIndex + 1) % videoList.size();
                                    switchToVideoByIndex(next);
                                });
                            }

                            @Override
                            public VideoPlaybackManager.DownloadProgressCallback asDownloadProgressCallback() {
                                return new VideoPlaybackManager.DownloadProgressCallback() {
                                    @Override public void onShowProgress() {}
                                    @Override public void onHideProgress() {}
                                    @Override public void onProgressUpdate(int progress) {}
                                    @Override public void onStatusUpdate(String status) {}
                                    @Override public void onCacheInfoUpdate() {}
                                    @Override public void onSwapToLocal(Integer index) {
                                        if (index != null) {
                                            runOnUiThread(() -> maybeSwapToLocal(index));
                                        }
                                    }
                                    @Override public void onSwitchToNext(int currentIndex) {}
                                    @Override public void onShowShortToast(String message) {}
                                };
                            }
                        }
                );
            });

        } catch (Exception e) {
            Log.e(TAG, "处理视频更新异常: " + e.getMessage());
        }
    }

    private void logVideoPlayInfo(String videoId, String videoUrl, Uri playable) {
        String scheme = playable.getScheme();
        boolean isLocalFile = "file".equalsIgnoreCase(scheme);
        Log.d(TAG, "播放视频信息:");
        Log.d(TAG, "  - VideoID: " + videoId);
        Log.d(TAG, "  - 原始URL: " + videoUrl);
        Log.d(TAG, "  - 播放URI: " + playable.toString());
        Log.d(TAG, "  - 使用缓存: " + (isLocalFile ? "是" : "否"));
    }

    private VideoTaskDetail findVideoDetailById(String videoId) {
        if (videoList == null) return null;
        for (VideoTaskDetail v : videoList) {
            String id = !TextUtils.isEmpty(v.getVideoId()) ? v.getVideoId() : VideoPlayUtils.videoIdFromUrl(v.getVideoURL());
            if (TextUtils.equals(id, videoId)) {
                return v;
            }
        }
        return null;
    }

    private void prefetchNextInSeries() {
        if (videoList == null || videoList.isEmpty()) return;
        int nextIndex = (currentVideoIndex >= videoList.size() - 1) ? 0 : currentVideoIndex + 1;
        VideoTaskDetail next = videoList.get(nextIndex);
        if (next == null || TextUtils.isEmpty(next.getVideoURL())) return;

        Log.d(TAG, "预取下一条: index=" + nextIndex + ", name=" + next.getVideoName());
        playbackManager.cacheVideoInBackground(next);
    }

    // 播放列表中播放下一个视频方法
    private void playNextVideoInPlaylist() {
        if (!playerPrepared || videoList == null || videoList.isEmpty()) return;

        int next = (currentVideoIndex >= videoList.size() - 1) ? 0 : (currentVideoIndex + 1);
        Log.d(TAG, "playNextVideoInPlaylist: " + currentVideoIndex + " -> " + next);

        VideoTaskDetail v = videoList.get(next);
        if (v != null && !TextUtils.isEmpty(v.getVideoURL())) {
            // 更新索引
            currentVideoIndex = next;
            Log.i(TAG, "播放列表中播放下一个: " + v.getVideoName() + " (index=" + currentVideoIndex + ")");

            // 直接跳转到对应索引
            player.seekTo(currentVideoIndex, 0L);
            player.setPlayWhenReady(true);
        }
    }

    // 切换到指定索引方法
    private void switchToVideoByIndex(int index) {
        if (!playerPrepared || videoList == null || index < 0 || index >= videoList.size()){
            Log.w(TAG, "switchToVideoByIndex 参数无效: index=" + index + ", playerPrepared=" + playerPrepared);
            return;
        }

        VideoTaskDetail v = videoList.get(index);
        if (index == currentVideoIndex) {
            Log.d(TAG, "switchToVideoByIndex 索引未变: " + index);
            return;
        }

        Log.i(TAG, "switchToVideoByIndex: " + currentVideoIndex + " -> " + index);
        Log.i(TAG, "切换到视频: " + v.getVideoName() + " (ID: " + v.getVideoId() + ")");

        if (v != null && !TextUtils.isEmpty(v.getVideoURL())) {
            // 更新索引
            currentVideoIndex = index;

            // 直接跳转到对应索引
            player.seekTo(currentVideoIndex, 0L);
            player.setPlayWhenReady(true);
        }
    }

    // -------- 统计：阈值 + 多时机去重（与小屏一致） --------

    /** 统一阈值：>5s 或 >10% 才算一次有效观看 */
    private boolean playedEnoughToCount() {
        return playbackManager.playedEnoughToCount(player);
    }

    /** 在 END/切换/退出/暂停 等时机调用：满足阈值才记一次；同一条只记一次 */
    private void maybeRecordCurrentByThreshold(String scene) {
        if (player == null) return;

        VideoTaskDetail currentItem = getCurrentVideoDetail();
        if (currentItem == null) return;

        playbackManager.maybeRecordCurrentVideoPlay(
                player, currentItem, videoSeriesId, videoSeriesName, scene
        );
    }

    private VideoTaskDetail getCurrentVideoDetail() {
        if (videoList == null) return null;

        try {
            int curIdx = player.getCurrentMediaItemIndex();
            if (curIdx >= 0 && curIdx < videoList.size()) {
                return videoList.get(curIdx);
            }
        } catch (Exception e) {
            Log.w(TAG, "获取当前视频详情失败: " + e.getMessage());
        }

        return null;
    }

    /** 同步当前条的 meta 信息（从列表或 mediaItem.tag） */
    private void syncCurrentMetaFromListOrTag() {
        try {
            int curIdx = player.getCurrentMediaItemIndex();

            if (videoList != null && curIdx >= 0 && curIdx < videoList.size()) {
                VideoTaskDetail d = videoList.get(curIdx);
                if (d != null) {
                    // 确保索引同步
                    if (currentVideoIndex != curIdx && playerPrepared) {
                        Log.w(TAG, "syncCurrentMetaFromListOrTag 索引不同步！currentVideoIndex=" + currentVideoIndex + ", playerIndex=" + curIdx);
                        currentVideoIndex = curIdx;
                    }

                    Log.d(TAG, "同步视频信息: index=" + curIdx + ", name=" + d.getVideoName() + ", id=" + d.getVideoId());
                }
            }
        } catch (Throwable e) {
            Log.e(TAG, "syncCurrentMetaFromListOrTag 异常: " + e.getMessage());
        }
    }

    // -------- 缓存：命中/替换/预取 --------
    private Uri resolvePlayableUri(String videoUrl, String videoId) {
        return playbackManager.buildPlayableUri(videoUrl, videoId);
    }

    private boolean isNetworkUri(Uri uri) {
        if (uri == null) return false;
        String s = uri.getScheme();
        return "http".equalsIgnoreCase(s) || "https".equalsIgnoreCase(s);
    }

    private void maybeSwapToLocal(int index) {
        if (player == null || videoList == null || index < 0 || index >= videoList.size()) return;

        VideoTaskDetail d = videoList.get(index);
        String url = d.getVideoURL();
        String vid = !TextUtils.isEmpty(d.getVideoId()) ? d.getVideoId() : VideoPlayUtils.videoIdFromUrl(url);

        String localPath = playbackManager.getLocalVideoPath(vid, url);
        if (TextUtils.isEmpty(localPath)) {
            Log.d(TAG, "暂未缓存，无需替换 index=" + index);
            return;
        }

        MediaItem cur = null;
        try {
            if (player.getMediaItemCount() > index) {
                cur = player.getMediaItemAt(index);
            }
        } catch (Throwable ignore) {}

        if (cur == null || cur.localConfiguration == null) return;

        Uri uri = cur.localConfiguration.uri;
        if (uri == null) return;
        if ("file".equalsIgnoreCase(uri.getScheme())) return; // 已经是本地

        Uri localUri = Uri.parse("file://" + localPath);
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

        Log.i(TAG, "切换为本地播放 index=" + index + " -> " + localUri);
    }

    private void logPlayingUri(int index) {
        try {
            MediaItem mi = player.getMediaItemAt(index);
            Uri u = (mi != null && mi.localConfiguration != null) ? mi.localConfiguration.uri : null;
            Log.d(TAG, "当前播放URI: " + (u == null ? "null" : u.toString()));
        } catch (Throwable ignore) {}
    }

    // -------- 退出/生命周期 --------
    private void exitWithResultAndFinish() {
        // 退出前：若已达到阈值且尚未记录，补一次
        maybeRecordCurrentByThreshold("exit");
        setKeepScreenOn(false);

        long curPos = player != null ? player.getCurrentPosition() : 0L;
        boolean playWhenReady = player != null && player.getPlayWhenReady();

        // 获取当前播放的视频信息用于调试
        String currentVideoName = "unknown";
        String currentVideoId = "unknown";
        if (videoList != null && currentVideoIndex >= 0 && currentVideoIndex < videoList.size()) {
            VideoTaskDetail current = videoList.get(currentVideoIndex);
            if (current != null) {
                currentVideoName = current.getVideoName();
                currentVideoId = current.getVideoId();
            }
        }

        Log.i(TAG, "exitWithResultAndFinish 详细信息:");
        Log.i(TAG, "  - currentVideoIndex: " + currentVideoIndex);
        Log.i(TAG, "  - currentVideoName: " + currentVideoName);
        Log.i(TAG, "  - currentVideoId: " + currentVideoId);
        Log.i(TAG, "  - playerCurrentIndex: " + (player != null ? player.getCurrentMediaItemIndex() : "null"));
        Log.i(TAG, "  - position: " + curPos);
        Log.i(TAG, "  - playWhenReady: " + playWhenReady);

        Intent data = new Intent()
                .putExtra(EXTRA_END_POS, curPos)
                .putExtra(EXTRA_END_PLAYREADY, playWhenReady)
                .putExtra(EXTRA_CURRENT_VIDEO_INDEX, currentVideoIndex);

        setResult(RESULT_OK, data);
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyFullscreen();
        // 与小屏对齐：进入前台时上传"到昨天为止"的记录
        try {
            Log.d(TAG, "onResume -> uploadAndClearRecords()");
            playbackManager.uploadAndClearRecords();
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