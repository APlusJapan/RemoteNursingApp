
package com.aplus.remotenursing;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.aplus.remotenursing.adapters.VideoTaskDetailAdapter;
import com.aplus.remotenursing.common.ApiConfig;
import com.aplus.remotenursing.common.UserUtils;
import com.aplus.remotenursing.common.VideoPlayUtils;
import com.aplus.remotenursing.manager.PermissionManager;
import com.aplus.remotenursing.manager.VideoCacheManager;
import com.aplus.remotenursing.manager.VideoPlayHistoryManager;
import com.aplus.remotenursing.models.VideoTaskDetail;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;            // [ADD]
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;         // [ADD]
import okhttp3.Response;

public class VideoTaskDetailFragment extends Fragment {

    private static final int REQ_FULLSCREEN = 1001;
    private static final String TAG = "VideoTaskDetailFragment";

    // 播放相关
    private PlayerView playerView;
    private ExoPlayer player;
    private boolean playerPrepared = false;

    // 数据与列表
    private List<VideoTaskDetail> videoList;
    private VideoTaskDetail currentItem;
    private int currentVideoIndex = 0;

    // RecyclerView
    private RecyclerView rvOther;
    private VideoTaskDetailAdapter adapter;

    // UI
    private TextView tvSeriesTitle;
    private ProgressBar downloadProgressBar;
    private Button btnCacheStatus, btnPreload, btnClearCache;
    private TextView tvCacheInfo;

    // 缓存
    private VideoCacheManager cacheManager;
    private boolean isUsingCache = false;

    // 媒体列表（与播放器解耦）
    private final List<MediaItem> mediaItems = new ArrayList<>();
    private final Map<String, Integer> id2Index = new HashMap<>();

    // 预加载与延迟任务
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable preloadRunnable;
    private final Set<String> preloading = new HashSet<>();

    // 播放历史追踪器
    private VideoPlayHistoryManager playHistoryManager;
    private final Gson gson = new Gson();

    // 防止死循环的标志位
    private boolean isTransitioning = false;
    private boolean isPlayingNext = false;

    // ====== [ADD] END阈值 + 去重用跨回调标志 ======
    private long lastReadyStartMs = 0L;             // 本条进入 READY 的时刻（用于过滤伪 END，可留作调试）
    private boolean hasRecordedForThisItem = false; // 本条是否已记过（跨回调/手动切换去重）
    // ============================================

    // ====== [ADD] Q-Ack 旁路更新相关 ======
    private String userIdArg;                  // 当前用户
    private String videoSeriesIdArg;           // 当前视频系列ID（从参数传入）
    private final OkHttpClient httpForUpdates = new OkHttpClient(); // 统一复用
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    // —— 更新接口返回的模型（最小字段）——
    private static class UpdateNotice {
        long notice_id;
        String video_id;
        String download_url;
        Long file_size;
        String md5;
        String memo;
    }
    private static class UpdateResponseX {
        List<UpdateNotice> notices;
    }
    // ====================================

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_video_task_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        String userId = UserUtils.loadUserId(requireContext());
        if (userId == null) {
            Toast.makeText(requireContext(), "请先登录", Toast.LENGTH_SHORT).show();
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new UserLoginFragment())
                    .commit();
            return;
        }
        this.userIdArg = userId; // [ADD] 记录到字段，后台更新用

        // 初始化播放历史追踪器
        playHistoryManager = VideoPlayHistoryManager.getInstance(getContext());

        // 应用启动时尝试上传之前的播放记录
        playHistoryManager.uploadAndClearRecords();

        String videoSeriesId = getArguments() != null ? getArguments().getString("videoSeriesId") : null;
        this.videoSeriesIdArg = videoSeriesId; // [ADD] 记录到字段（后台更新用）
        String videoSeriesName = getArguments() != null ? getArguments().getString("videoSeriesName") : "视频系列";

        // 1) 初始化 UI
        initOriginalViews(view, videoSeriesName);
        initCacheViews(view);

        // 2) 权限与缓存
        checkPermissionsAndInitCache();

        // 3) 拉取数据
        fetchVideoList(userId, videoSeriesId, list -> {
            if (list == null || list.isEmpty()) return;

            this.videoList = list;
            sortVideosByOrder();
            findAndSetCurrentVideo();

            // 构建 MediaItems（优先 file:// 本地缓存）
            buildMediaItems(videoList);

            // 刷新适配器数据
            if (adapter != null) {
                adapter.setData(videoList);
                adapter.setCurrentPlayingItem(currentItem);
                scrollToCurrentVideo();
            }

            // 4) 初始化（或复用）播放器
            initExoPlayer(view);

            // 5) 只在首次准备：把整列 mediaItems 喂进去并开始播放
            ensurePlayerPreparedOnce(currentVideoIndex);

            // 进度条联动：首次进入时就检查并联动当前视频的缓存状态/下载进度
            if (currentItem != null) {
                updateCacheStatus(currentItem);
                if (isUsingCache) {
                    downloadCurrentVideoWithUi(currentItem);
                }
            }

            // 6) 延迟预加载（纯后台，不触碰 player）
            schedulePreloadNextVideo();

            // 7) [ADD] 旁路：后台检查是否有未ACK的更新 → 静默刷新缓存 → ACK
            checkAndRefreshVideoCacheInBackground();
        });
    }

    /** 初始化原有视图组件（含 RecyclerView） */
    private void initOriginalViews(View view, String videoSeriesName) {
        rvOther = view.findViewById(R.id.rv_other_videos);
        rvOther.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new VideoTaskDetailAdapter(new ArrayList<>(), item -> {
            // 防止重复点击导致的死循环
            if (isTransitioning) {
                Log.d(TAG, "正在切换中，忽略点击");
                return;
            }

            // 列表点击：通过索引切换，不重新 prepare
            int newIndex = videoList.indexOf(item);
            if (newIndex < 0 || newIndex == currentVideoIndex) return;

            Log.d(TAG, "列表点击切换到索引: " + newIndex);
            switchToVideoByIndex(newIndex);
        });
        rvOther.setAdapter(adapter);

        tvSeriesTitle = view.findViewById(R.id.tv_more_series);
        if (tvSeriesTitle != null) {
            tvSeriesTitle.setText("更多 " + videoSeriesName + " 视频");
        }
    }

    private void logPlayingUri(int index) {
        try {
            MediaItem mi = player.getMediaItemAt(index);
            Uri u = (mi != null && mi.localConfiguration != null) ? mi.localConfiguration.uri : null;
            Log.d("VideoCache", "当前播放URI: " + (u == null ? "null" : u.toString()));
        } catch (Throwable ignore) {}
    }

    /** 初始化缓存相关视图组件（含进度条属性） */
    private void initCacheViews(View view) {
        downloadProgressBar = view.findViewById(R.id.progress_download);
        btnCacheStatus = view.findViewById(R.id.btn_cache_status);
        btnPreload = view.findViewById(R.id.btn_preload);
        btnClearCache = view.findViewById(R.id.btn_clear_cache);
        tvCacheInfo = view.findViewById(R.id.tv_cache_info);

        if (downloadProgressBar != null) {
            downloadProgressBar.setMax(100);
            downloadProgressBar.setIndeterminate(false);
            downloadProgressBar.setVisibility(View.GONE);
            downloadProgressBar.setProgress(0);
        }

        if (btnCacheStatus != null) btnCacheStatus.setOnClickListener(v -> showCacheInfo());
        if (btnPreload != null) btnPreload.setOnClickListener(v -> preloadAllVideos());
        if (btnClearCache != null) btnClearCache.setOnClickListener(v -> confirmClearCache());
    }

    /** 权限与缓存初始化 */
    private void checkPermissionsAndInitCache() {
        boolean hasPermission = PermissionManager.hasStoragePermission(requireContext());
        Log.d("VideoCache", "存储权限检查: " + (hasPermission ? "已授权" : "未授权"));
        if (hasPermission) {
            initCacheManager();
        } else {
            Log.d("VideoCache", "请求存储权限");
            PermissionManager.requestStoragePermission(this);
        }
    }

    private void initCacheManager() {
        try {
            cacheManager = VideoCacheManager.getInstance(requireContext());
            Log.d("VideoCache", "缓存管理器初始化成功");
            updateCacheInfo();
            isUsingCache = true;
            Log.d("VideoCache", "缓存功能已启用");
        } catch (Exception e) {
            Log.e("VideoCache", "缓存管理器初始化失败: " + e.getMessage());
            isUsingCache = false;
        }
    }

    /** 初始化或复用播放器 */
    private void initExoPlayer(View view) {
        playerView = view.findViewById(R.id.player_view);
        if (player == null) {
            player = new ExoPlayer.Builder(requireContext())
                    .setHandleAudioBecomingNoisy(true)
                    .build();
            try {
                player.setAudioAttributes(
                        new AudioAttributes.Builder()
                                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                                .setUsage(C.USAGE_MEDIA)
                                .build(),
                        true
                );
            } catch (Exception e) {
                Log.w("VideoPlayer", "设置音频属性失败: " + e.getMessage());
            }

            player.addListener(new Player.Listener() {
                // 记录跟踪
                private String lastRecordedVideoId = null;
                private long lastVideoStartTime = 0;
                private boolean hasRecordedCurrent = false; // 标记当前视频是否已记录

                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    Log.d(TAG, "播放状态改变: " + playbackState +
                            ", 当前视频: " + (currentItem != null ? currentItem.getVideoName() : "null") +
                            ", VideoID: " + (currentItem != null ? currentItem.getVideoId() : "null"));

                    if (playbackState == Player.STATE_READY) {
                        // 视频开始播放
                        lastVideoStartTime = System.currentTimeMillis();
                        hasRecordedCurrent = false; // 重置记录标志

                        // [ADD] 记录 READY 时刻 & 跨回调去重复位
                        lastReadyStartMs = SystemClock.elapsedRealtime();
                        hasRecordedForThisItem = false;

                        Log.d(TAG, "视频准备就绪 - VideoID: " +
                                (currentItem != null ? currentItem.getVideoId() : "null") +
                                ", 视频名: " + (currentItem != null ? currentItem.getVideoName() : "null"));

                    } else if (playbackState == Player.STATE_ENDED) {
                        Log.d(TAG, "视频播放结束 - 准备记录");

                        // [ADD] 结束时加阈值 + 去重
                        if (!hasRecordedCurrent && playedEnoughToCount()) {
                            recordCurrentVideoPlay();                // 内部会把 hasRecordedCurrent 置位
                            hasRecordedForThisItem = true;           // 跨回调去重
                        } else {
                            Log.d(TAG, "忽略过早的 END（<5s 或 <10%），可能是替换 MediaItem 触发的");
                        }

                        // [ADD] 只有“有效播放”才自动播下一条
                        if (playedEnoughToCount() && !isPlayingNext) {
                            mainHandler.postDelayed(() -> {
                                if (!isPlayingNext) {
                                    playNextVideo();
                                }
                            }, 500);
                        }
                    }
                }

                @Override
                public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
                    Log.d(TAG, "媒体项切换: reason=" + reason +
                            ", 从 " + (currentItem != null ? currentItem.getVideoName() : "null") +
                            " 切换");

                    // [ADD] 在切换前记录上一个视频（阈值 + 去重）
                    if (currentItem != null && !hasRecordedCurrent && lastVideoStartTime > 0) {
                        long playDuration = System.currentTimeMillis() - lastVideoStartTime;
                        if (playDuration > 5000) { // 播放超过5秒才记录
                            Log.d(TAG, "切换前记录上一个视频，播放时长: " + (playDuration/1000) + "秒");
                            recordCurrentVideoPlay();
                            hasRecordedForThisItem = true; // 跨回调去重
                        }
                    }

                    int cur = player.getCurrentMediaItemIndex();
                    if (cur != C.INDEX_UNSET && !isTransitioning) {
                        maybeSwapToLocal(cur);

                        // 更新当前项
                        if (videoList != null && cur < videoList.size()) {
                            currentVideoIndex = cur;
                            currentItem = videoList.get(cur);

                            Log.d(TAG, "切换到新视频 - Index: " + cur +
                                    ", VideoID: " + currentItem.getVideoId() +
                                    ", 视频名: " + currentItem.getVideoName());

                            if (adapter != null) {
                                adapter.setCurrentPlayingItem(currentItem);
                            }

                            // 重置记录状态
                            hasRecordedCurrent = false;
                            lastVideoStartTime = System.currentTimeMillis();
                        }
                    }
                }

                // 记录当前视频播放
                private void recordCurrentVideoPlay() {
                    if (playHistoryManager == null || currentItem == null) {
                        Log.d(TAG, "无法记录：playHistoryManager=" + playHistoryManager +
                                ", currentItem=" + currentItem);
                        return;
                    }

                    String videoId = currentItem.getVideoId();
                    if (TextUtils.isEmpty(videoId)) {
                        videoId = VideoPlayUtils.videoIdFromUrl(currentItem.getVideoURL());
                    }

                    // 检查是否已记录过这个视频（避免重复）
                    if (videoId.equals(lastRecordedVideoId) && hasRecordedCurrent) {
                        Log.d(TAG, "视频 " + videoId + " 在本次播放中已记录，跳过");
                        return;
                    }

                    String videoSeriesId = getArguments() != null ?
                            getArguments().getString("videoSeriesId") : "";
                    String videoSeriesName = getArguments() != null ?
                            getArguments().getString("videoSeriesName") : "";
                    String videoDuration = currentItem.getVideoDuration();

                    Log.d(TAG, ">>>>>>> 记录视频播放 <<<<<<<");
                    Log.d(TAG, "  VideoID: " + videoId);
                    Log.d(TAG, "  视频名: " + currentItem.getVideoName());
                    Log.d(TAG, "  系列ID: " + videoSeriesId);
                    Log.d(TAG, "  系列名: " + videoSeriesName);
                    Log.d(TAG, "  时长: " + videoDuration);

                    playHistoryManager.recordVideoPlay(
                            videoId,
                            videoSeriesId,
                            currentItem.getVideoName(),
                            videoSeriesName,
                            videoDuration
                    );

                    lastRecordedVideoId = videoId;
                    hasRecordedCurrent = true;
                    hasRecordedForThisItem = true; // [ADD] 跨回调去重

                    // 立即打印所有记录
                    Log.d(TAG, "===== 记录后的播放历史 =====");
                    playHistoryManager.logAllLocalData();
                }

                @Override
                public void onIsPlayingChanged(boolean isPlaying) {
                    Log.d(TAG, "播放状态: " + (isPlaying ? "播放中" : "暂停") +
                            ", 当前视频: " + (currentItem != null ? currentItem.getVideoName() : "null"));
                }
            });
        }
        playerView.setPlayer(player);

        // 全屏按钮
        View fs = view.findViewById(R.id.btn_fullscreen);
        if (fs != null) {
            fs.setOnClickListener(v -> {
                if (player == null) return;

                long pos = Math.max(0L, player.getCurrentPosition());
                boolean playReady = player.getPlayWhenReady();

                // 获取当前播放的URL和videoId
                String url = null;
                String vid = null;

                MediaItem mi = player.getCurrentMediaItem();
                if (mi != null && mi.localConfiguration != null) {
                    Object tag = mi.localConfiguration.tag;
                    if (tag instanceof VideoTaskDetail) {
                        VideoTaskDetail d = (VideoTaskDetail) tag;
                        url = d.getVideoURL();
                        vid = d.getVideoId();
                    } else if (tag instanceof VideoFullscreenPlayerActivity.VideoTag) {
                        VideoFullscreenPlayerActivity.VideoTag t = (VideoFullscreenPlayerActivity.VideoTag) tag;
                        url = t.url;
                        vid = t.id;
                    }

                    if (TextUtils.isEmpty(url)) {
                        Uri u = mi.localConfiguration.uri;
                        if (u != null) url = u.toString();
                    }
                }

                if (TextUtils.isEmpty(url) && currentItem != null) {
                    url = currentItem.getVideoURL();
                    vid = currentItem.getVideoId();
                }

                if (TextUtils.isEmpty(url)) {
                    Toast.makeText(requireContext(), "当前视频地址为空，稍后再试", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (TextUtils.isEmpty(vid)) {
                    vid = VideoPlayUtils.videoIdFromUrl(url);
                }

                // 暂停播放避免冲突
                try {
                    player.pause();
                } catch (Throwable ignore) {}

                Intent it = new Intent(requireContext(), VideoFullscreenPlayerActivity.class)
                        .putExtra(VideoFullscreenPlayerActivity.EXTRA_URL, url)
                        .putExtra(VideoFullscreenPlayerActivity.EXTRA_VIDEO_ID, vid)
                        .putExtra(VideoFullscreenPlayerActivity.EXTRA_START_POS, pos)
                        .putExtra(VideoFullscreenPlayerActivity.EXTRA_START_PLAYREADY, playReady)
                        .putExtra(VideoFullscreenPlayerActivity.EXTRA_VIDEO_SERIES_ID,
                                getArguments() != null ? getArguments().getString("videoSeriesId") : null)
                        .putExtra(VideoFullscreenPlayerActivity.EXTRA_CURRENT_VIDEO_INDEX, currentVideoIndex);

                startActivityForResult(it, REQ_FULLSCREEN);
            });
        }

        ImageButton backButton = view.findViewById(R.id.VideoDetailPage_btn_back);
        if (backButton != null) backButton.setOnClickListener(v -> requireActivity().onBackPressed());
    }

    // ---------- 播放列表构建 & 切换 ----------

    /** 优先使用本地缓存路径构建 MediaItems */
    private void buildMediaItems(List<VideoTaskDetail> list) {
        mediaItems.clear();
        id2Index.clear();
        for (int i = 0; i < list.size(); i++) {
            VideoTaskDetail d = list.get(i);
            String url = d.getVideoURL();
            String vid = d.getVideoId();
            if (TextUtils.isEmpty(vid)) {
                vid = VideoPlayUtils.videoIdFromUrl(url);
            }

            String local = (isUsingCache && cacheManager != null)
                    ? cacheManager.getLocalVideoPath(vid, url)
                    : null;

            Uri uri = (local != null) ? Uri.fromFile(new File(local)) : Uri.parse(url);
            MediaItem mi = new MediaItem.Builder()
                    .setUri(uri)
                    .setMediaId(vid)
                    .setTag(d)
                    .build();
            mediaItems.add(mi);
            id2Index.put(vid, i);
        }
    }

    /** 首次准备：一次性把列表喂给 player */
    private void ensurePlayerPreparedOnce(int startIndex) {
        if (player == null) return;
        if (!playerPrepared) {
            player.setMediaItems(mediaItems, startIndex, C.TIME_UNSET);
            player.prepare();
            playerPrepared = true;
        }
        player.setPlayWhenReady(true);
    }

    /** 通过索引切换视频（防死循环版本） */
    private void switchToVideoByIndex(int index) {
        if (player == null || videoList == null || index < 0 || index >= videoList.size()) return;
        if (index == currentVideoIndex) return;

        Log.d(TAG, "switchToVideoByIndex - 从索引 " + currentVideoIndex + " 切换到 " + index);

        // [MODIFY] 切换前记录当前视频（阈值 + 去重 + 避免 END 后重复记）
        if (currentItem != null && player != null && playHistoryManager != null) {
            int state = player.getPlaybackState();
            long position = player.getCurrentPosition();

            if (state != Player.STATE_ENDED && !hasRecordedForThisItem && position > 5000) { // >5s 才算有效
                String videoId = currentItem.getVideoId();
                if (TextUtils.isEmpty(videoId)) {
                    videoId = VideoPlayUtils.videoIdFromUrl(currentItem.getVideoURL());
                }

                Log.d(TAG, "手动切换前记录当前视频 - VideoID: " + videoId +
                        ", 视频名: " + currentItem.getVideoName() +
                        ", 播放位置: " + (position/1000) + "秒");

                String videoSeriesId = getArguments() != null ?
                        getArguments().getString("videoSeriesId") : "";
                String videoSeriesName = getArguments() != null ?
                        getArguments().getString("videoSeriesName") : "";

                playHistoryManager.recordVideoPlay(
                        videoId,
                        videoSeriesId,
                        currentItem.getVideoName(),
                        videoSeriesName,
                        currentItem.getVideoDuration()
                );

                // 查看记录状态
                playHistoryManager.logAllLocalData();

                // [ADD] 跨回调去重置位，避免后续 ENDED/其它位置再次记录
                hasRecordedForThisItem = true;
            }
        }

        // 设置切换标志
        isTransitioning = true;
        isPlayingNext = false;

        currentVideoIndex = index;
        currentItem = videoList.get(index);

        Log.d(TAG, "切换到新视频 - Index: " + index +
                ", VideoID: " + currentItem.getVideoId() +
                ", 视频名: " + currentItem.getVideoName());

        // 更新UI
        if (adapter != null) {
            adapter.setCurrentPlayingItem(currentItem);
        }
        scrollToCurrentVideo();

        // 更新缓存状态
        updateCacheStatus(currentItem);
        if (isUsingCache) {
            downloadCurrentVideoWithUi(currentItem);
        }

        // 切换播放器
        ensurePlayerPreparedOnce(index);
        if (player.getCurrentMediaItemIndex() != index) {
            Log.d(TAG, "播放器seekTo索引: " + index);
            player.seekTo(index, 0L);
        }
        player.setPlayWhenReady(true);

        // 新条目开始播放时，复位跨回调去重标志
        hasRecordedForThisItem = false;
        lastReadyStartMs = SystemClock.elapsedRealtime();

        // 延迟重置标志
        mainHandler.postDelayed(() -> {
            isTransitioning = false;
            Log.d(TAG, "切换完成，重置isTransitioning标志");
        }, 1500);

        // 后台缓存
        if (isUsingCache) {
            cacheVideoInBackground(currentItem);
        }

        // [ADD] 切换到新视频后，也尝试旁路更新该新视频的缓存
        checkAndRefreshVideoCacheInBackground();
    }

    // [ADD] 统一阈值：>5s 或 >10% 才算一次有效观看
    private boolean playedEnoughToCount() {
        if (player == null) return false;
        long pos = Math.max(0L, player.getCurrentPosition());
        long dur = Math.max(0L, player.getDuration());
        return pos >= 5000 || (dur > 0 && pos >= dur / 10);
    }

    /** 播放下一个视频（防死循环版本） */
    private void playNextVideo() {
        if (isPlayingNext) {
            Log.d(TAG, "playNextVideo - 已在播放下一个，返回");
            return;
        }

        if (videoList == null || videoList.isEmpty()) {
            Log.d(TAG, "playNextVideo - 视频列表为空");
            return;
        }

        Log.d(TAG, "playNextVideo - 开始播放下一个视频");
        isPlayingNext = true;

        // 计算下一个索引
        int nextIndex;
        if (currentVideoIndex >= videoList.size() - 1) {
            nextIndex = 0; // 循环到第一个
        } else {
            nextIndex = currentVideoIndex + 1;
        }

        Log.d(TAG, "playNextVideo - 下一个索引: " + nextIndex);

        // 延迟执行切换，避免与STATE_ENDED重复触发
        mainHandler.postDelayed(() -> {
            switchToVideoByIndex(nextIndex);
            // 延迟重置标志
            mainHandler.postDelayed(() -> {
                isPlayingNext = false;
                Log.d(TAG, "playNextVideo - 重置isPlayingNext标志");
            }, 1000);
        }, 200);
    }

    // ---------- 预加载（纯后台，不碰 player） ----------

    private void schedulePreloadNextVideo() {
        cancelPreload();
        preloadRunnable = this::preloadNextVideoInBackground;
        mainHandler.postDelayed(preloadRunnable, 3000);
    }

    private void cancelPreload() {
        if (preloadRunnable != null) {
            mainHandler.removeCallbacks(preloadRunnable);
            preloadRunnable = null;
        }
    }

    private void preloadNextVideoInBackground() {
        if (!isUsingCache || cacheManager == null || videoList == null || videoList.isEmpty()) return;
        int nextIndex = (currentVideoIndex + 1) % videoList.size();
        VideoTaskDetail nextVideo = videoList.get(nextIndex);

        String url = nextVideo.getVideoURL();
        String vid = nextVideo.getVideoId();
        if (TextUtils.isEmpty(vid)) {
            vid = VideoPlayUtils.videoIdFromUrl(url);
        }
        String localPath = cacheManager.getLocalVideoPath(vid, url);
        if (localPath != null) {
            Log.d("VideoCache", "下一个视频已缓存");
            return;
        }

        Log.d("VideoCache", "开始预加载下一个视频: " + nextVideo.getVideoName());
        cacheVideoInBackground(nextVideo);
    }

    private void cacheVideoInBackground(VideoTaskDetail item) {
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
                                Log.d("VideoCache", "后台下载进度: " + progress + "%");
                                last = progress;
                            }
                        }
                        @Override public void onSuccess(String id, String localPath) {
                            preloading.remove(id);
                            mainHandler.post(() -> {
                                updateCacheInfo();
                                Integer idx = id2Index.get(id);
                                if (idx != null) maybeSwapToLocal(idx);
                            });
                        }
                        @Override public void onError(String id, String error) {
                            preloading.remove(id);
                            Log.w("VideoCache", "预加载失败: " + error);
                        }
                    });
        }).start();
    }

    // ---------- UI：缓存状态/按钮/进度条联动 ----------

    /** 带 UI 联动地下载当前视频（若是当前正在播放且要覆盖，则先跳到下一条，再促成升级） */
    private void downloadCurrentVideoWithUi(@NonNull VideoTaskDetail item) {
        if (!isUsingCache || cacheManager == null) return;

        final String url = item.getVideoURL();
        String vid = item.getVideoId();
        if (TextUtils.isEmpty(vid)) {
            vid = VideoPlayUtils.videoIdFromUrl(url);
        }
        final String videoId = vid;

        // 已有本地文件就不再下
        String local = cacheManager.getLocalVideoPath(videoId, url);
        if (local != null) {
            hideDownloadProgress();
            if (btnCacheStatus != null) btnCacheStatus.setText("已缓存");
            Integer idx = id2Index.get(videoId);
            if (idx != null) maybeSwapToLocal(idx);
            return;
        }

        // UI：显示进度条、按钮文案
        showDownloadProgress();
        if (btnCacheStatus != null) btnCacheStatus.setText("缓存中…");

        cacheManager.downloadAndCacheVideo(videoId, url, new VideoCacheManager.DownloadCallback() {
            @Override public void onStart(String id) {
                mainHandler.post(() -> updateDownloadProgress(0));
            }
            @Override public void onProgress(String id, int progress) {
                mainHandler.post(() -> updateDownloadProgress(progress));
            }
            @Override public void onSuccess(String id, String localPath) {
                mainHandler.post(() -> {
                    hideDownloadProgress();
                    if (btnCacheStatus != null) btnCacheStatus.setText("已缓存");
                    updateCacheInfo();
                    Integer idx = id2Index.get(id);
                    if (idx != null) maybeSwapToLocal(idx);
                });
            }
            @Override public void onError(String id, String error) {
                Log.w("VideoCache", "下载失败: " + error);

                // 仅在“目标被占用/重命名失败”时，采取跳转下一条策略
                boolean maybeInUse = error != null && (error.contains("重命名缓存文件失败")
                        || error.contains("占用") || error.contains("in use") || error.contains("rename"));

                if (maybeInUse) {
                    Integer idxObj = id2Index.get(id);
                    if (idxObj != null) {
                        // ★ 关键：任何 player.* 调用切回主线程
                        withPlayerOnMain(() -> {
                            if (player == null) return;
                            int idx = idxObj;
                            boolean isCurrent = (player.getCurrentMediaItemIndex() == idx);
                            if (isCurrent) {
                                int next = (idx + 1) % (videoList == null ? 1 : videoList.size());
                                mainHandler.post(() -> showShortToast("视频正在更新缓存，已临时播放下一条…"));
                                switchToVideoByIndex(next);

                                // 切走后改用智能退避重试 promote
                                scheduleTryPromoteWithBackoff(videoId, url, idx);
                            }
                        });
                    }
                }

                mainHandler.post(() -> {
                    hideDownloadProgress();
                    if (btnCacheStatus != null) btnCacheStatus.setText("未缓存");
                    if (!maybeInUse) {
                        Toast.makeText(requireContext(), "缓存失败，请稍后重试", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }
    /** 智能重试 tryPromoteNow：仅在该 videoId 不在 preloading 集合时尝试；
     *  没成功即按 600ms / 1500ms / 3000ms 做退避重试；成功则 maybeSwapToLocal(idx)。
     */
    private void scheduleTryPromoteWithBackoff(@NonNull String videoId,
                                               @NonNull String url,
                                               int indexIfKnown) {
        long[] delays = new long[]{600, 1500, 3000};
        scheduleTryPromoteWithBackoffInternal(videoId, url, indexIfKnown, delays, 0);
    }

    private void scheduleTryPromoteWithBackoffInternal(@NonNull String videoId,
                                                       @NonNull String url,
                                                       int indexIfKnown,
                                                       @NonNull long[] delays,
                                                       int attempt) {
        if (attempt >= delays.length) return;
        long delay = delays[attempt];
        mainHandler.postDelayed(() -> {
            // 若仍在预加载，跳过本次尝试，直接进入下一轮退避
            if (preloading.contains(videoId)) {
                Log.i("VideoCache", "promote skipped (still preloading): " + videoId + ", attempt=" + (attempt + 1));
                scheduleTryPromoteWithBackoffInternal(videoId, url, indexIfKnown, delays, attempt + 1);
                return;
            }
            boolean promoted = (cacheManager != null) && cacheManager.tryPromoteNow(videoId, url);
            Log.i("VideoCache", "tryPromoteNow attempt#" + (attempt + 1) + " after " + delay + "ms: " + promoted);
            if (promoted) {
                if (indexIfKnown >= 0) {
                    updateCacheInfo();
                    maybeSwapToLocal(indexIfKnown);
                }
            } else {
                scheduleTryPromoteWithBackoffInternal(videoId, url, indexIfKnown, delays, attempt + 1);
            }
        }, delay);
    }

    private void updateCacheStatus(VideoTaskDetail item) {
        if (btnCacheStatus == null) return;
        if (!isUsingCache || cacheManager == null) {
            btnCacheStatus.setText("缓存未启用");
            return;
        }
        try {
            String url = item.getVideoURL();
            String vid = item.getVideoId();
            if (TextUtils.isEmpty(vid)) {
                vid = VideoPlayUtils.videoIdFromUrl(url);
            }
            Log.d("VideoCache", "检查缓存 - VID: " + vid + ", URL: " + url);
            boolean isCached = cacheManager.getLocalVideoPath(vid, url) != null;
            Log.d("VideoCache", "缓存状态: " + (isCached ? "已缓存" : "未缓存"));
            btnCacheStatus.setText(isCached ? "已缓存" : "未缓存");
            btnCacheStatus.setBackgroundColor(isCached
                    ? getResources().getColor(android.R.color.holo_green_light)
                    : getResources().getColor(android.R.color.darker_gray));
        } catch (Exception e) {
            Log.e("VideoCache", "更新缓存状态失败: " + e.getMessage());
            btnCacheStatus.setText("检查失败");
        }
    }

    private void preloadAllVideos() {
        if (!isUsingCache || cacheManager == null || videoList == null) {
            Toast.makeText(requireContext(), "缓存功能不可用", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle("预加载确认")
                .setMessage(String.format("将预加载 %d 个视频，可能需要较长时间和消耗流量，确定继续吗？", videoList.size()))
                .setPositiveButton("确定", (dialog, which) -> {
                    Log.d("VideoCache", "开始预加载所有视频");
                    for (VideoTaskDetail v : videoList) {
                        cacheVideoInBackground(v);
                    }
                    Toast.makeText(requireContext(), "开始后台预加载", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showCacheInfo() {
        if (currentItem == null) return;
        String vid = currentItem.getVideoId();
        if (TextUtils.isEmpty(vid)) {
            vid = VideoPlayUtils.videoIdFromUrl(currentItem.getVideoURL());
        }
        boolean isCached = isUsingCache && cacheManager != null &&
                cacheManager.getLocalVideoPath(vid, currentItem.getVideoURL()) != null;
        String message = String.format(
                "视频: %s\n状态: %s\nVideoID: %s",
                currentItem.getVideoName(),
                isCached ? "已缓存" : "未缓存",
                vid
        );
        new AlertDialog.Builder(requireContext())
                .setTitle("视频信息")
                .setMessage(message)
                .setPositiveButton("确定", null)
                .show();
    }

    private void confirmClearCache() {
        if (!isUsingCache || cacheManager == null) return;
        String cacheSize = getCacheSizeFormatted();
        new AlertDialog.Builder(requireContext())
                .setTitle("清理缓存")
                .setMessage("当前缓存大小: " + cacheSize + "\n\n确定要清理所有缓存吗？")
                .setPositiveButton("确定", (dialog, which) -> {
                    Log.d("VideoCache", "清理缓存");
                    cacheManager.clearCache();
                    updateCacheInfo();
                    if (currentItem != null) updateCacheStatus(currentItem);
                    hideDownloadProgress();
                    Toast.makeText(requireContext(), "缓存已清理", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void updateCacheInfo() {
        if (tvCacheInfo != null && isUsingCache && cacheManager != null) {
            String cacheSize = getCacheSizeFormatted();
            tvCacheInfo.setText("缓存大小: " + cacheSize);
            Log.d("VideoCache", "缓存信息更新: " + cacheSize);
        }
    }

    private String getCacheSizeFormatted() {
        if (!isUsingCache || cacheManager == null) return "0 MB";
        long sizeInBytes = cacheManager.getCacheSize();
        if (sizeInBytes < 1024L * 1024L) {
            return String.format("%.1f KB", sizeInBytes / 1024.0);
        } else if (sizeInBytes < 1024L * 1024L * 1024L) {
            return String.format("%.1f MB", sizeInBytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", sizeInBytes / (1024.0 * 1024.0 * 1024.0));
        }
    }

    private void showDownloadProgress() {
        if (downloadProgressBar != null) {
            downloadProgressBar.setVisibility(View.VISIBLE);
            downloadProgressBar.setProgress(0);
        }
    }

    private void updateDownloadProgress(int progress) {
        if (downloadProgressBar != null) {
            downloadProgressBar.setProgress(progress);
        }
    }

    private void hideDownloadProgress() {
        if (downloadProgressBar != null) {
            downloadProgressBar.setVisibility(View.GONE);
        }
    }

    // ---------- 排序、定位、列表滚动 ----------

    private void sortVideosByOrder() {
        if (videoList != null) {
            Collections.sort(videoList, new Comparator<VideoTaskDetail>() {
                @Override
                public int compare(VideoTaskDetail v1, VideoTaskDetail v2) {
                    Integer o1 = v1.getVideoOrder();
                    Integer o2 = v2.getVideoOrder();
                    if (o1 == null && o2 == null) return 0;
                    if (o1 == null) return 1;
                    if (o2 == null) return -1;
                    return o1.compareTo(o2);
                }
            });
        }
    }

    private void findAndSetCurrentVideo() {
        currentVideoIndex = 0;
        currentItem = videoList.get(0);
        for (int i = 0; i < videoList.size(); i++) {
            VideoTaskDetail v = videoList.get(i);
            if (v.isCurrentlyPlaying()) {
                currentVideoIndex = i;
                currentItem = v;
                break;
            }
        }
    }

    private void scrollToCurrentVideo() {
        if (rvOther != null && currentVideoIndex >= 0) {
            rvOther.post(() -> {
                LinearLayoutManager lm = (LinearLayoutManager) rvOther.getLayoutManager();
                if (lm != null) lm.scrollToPositionWithOffset(currentVideoIndex, 100);
            });
        }
    }

    // ---------- 网络：获取列表 ----------

    private interface VideoListCallback {
        void onResult(List<VideoTaskDetail> videoList);
    }

    private void fetchVideoList(String userId, String videoSeriesId, VideoListCallback callback) {
        OkHttpClient client = httpForUpdates; // 复用
        String url = ApiConfig.API_VIDEO_DETAIL_BY_SERIES_ID + videoSeriesId;
        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                Activity act = getActivity();
                if (act != null) act.runOnUiThread(() -> callback.onResult(null));
            }
            @Override public void onResponse(Call call, Response response) {
                try (Response resp = response) { // 确保关闭
                    Activity act = getActivity();
                    if (!resp.isSuccessful() || resp.body() == null) {
                        if (act != null) act.runOnUiThread(() -> callback.onResult(null));
                        return;
                    }
                    String json = resp.body().string();
                    List<VideoTaskDetail> list =
                            gson.fromJson(json, new TypeToken<List<VideoTaskDetail>>(){}.getType());
                    if (act != null) act.runOnUiThread(() -> callback.onResult(list));
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                    Activity act = getActivity();
                    if (act != null) act.runOnUiThread(() -> callback.onResult(null));
                }
            }
        });
    }

    // ---------- 权限回调 ----------

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (PermissionManager.onRequestPermissionsResult(requestCode, permissions, grantResults)) {
            Log.d("VideoCache", "权限授予成功");
            initCacheManager();
        } else {
            Log.d("VideoCache", "权限授予失败");
            Toast.makeText(requireContext(), "需要存储权限才能缓存视频", Toast.LENGTH_LONG).show();
        }
    }

    // 若该 index 已有本地缓存，则把该 MediaItem 换成 file://
    private void maybeSwapToLocal(int index) {
        if (player == null || videoList == null || index < 0 || index >= videoList.size()) return;
        if (!isUsingCache || cacheManager == null) return;

        VideoTaskDetail d = videoList.get(index);
        String url = d.getVideoURL();
        String vid = d.getVideoId();
        if (TextUtils.isEmpty(vid)) {
            vid = VideoPlayUtils.videoIdFromUrl(url);
        }

        String local = cacheManager.getLocalVideoPath(vid, url);
        if (local == null) return;

        Uri want = Uri.fromFile(new File(local));

        // 取出当前 player 中该位置的 MediaItem 的 uri
        MediaItem cur = null;
        try {
            if (player.getMediaItemCount() > index) {
                cur = player.getMediaItemAt(index);
            }
        } catch (Throwable ignore) {}

        Uri have = (cur != null && cur.localConfiguration != null) ? cur.localConfiguration.uri : null;

        // 已经是本地就不动
        if (have != null && want.equals(have)) return;

        MediaItem newItem = new MediaItem.Builder()
                .setUri(want)
                .setMediaId(vid)
                .setTag(d)
                .build();

        if (playerPrepared) {
            // 播放器已准备：用 remove+add 的方式原位替换
            int curIdx = player.getCurrentMediaItemIndex();
            long position = 0L;
            boolean restore = (curIdx == index);
            boolean wasPlaying = player.getPlayWhenReady();
            if (restore) position = player.getCurrentPosition();

            if (player.getMediaItemCount() > index) {
                player.removeMediaItem(index);
            }
            player.addMediaItem(index, newItem);

            if (restore) {
                player.seekTo(index, position);
                player.setPlayWhenReady(wasPlaying);
            }
        } else {
            // 还没 prepare：改掉我们维护的 mediaItems
            if (index >= 0 && index < mediaItems.size()) {
                mediaItems.set(index, newItem);
            }
        }

        Log.i("VideoCache", "切换为本地播放: " + d.getVideoName() + " -> " + local);
    }

    // ---------- 全屏返回 ----------

    @Override
    public void onActivityResult(int req, int res, @Nullable Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_FULLSCREEN && res == Activity.RESULT_OK && data != null) {
            long pos = data.getLongExtra(VideoFullscreenPlayerActivity.EXTRA_END_POS, 0L);
            boolean playReady = data.getBooleanExtra(VideoFullscreenPlayerActivity.EXTRA_END_PLAYREADY, true);
            int returnedIndex = data.getIntExtra(VideoFullscreenPlayerActivity.EXTRA_CURRENT_VIDEO_INDEX, currentVideoIndex);

            if (videoList != null && returnedIndex >= 0 && returnedIndex < videoList.size()) {
                currentVideoIndex = returnedIndex;
                currentItem = videoList.get(currentVideoIndex);
                if (adapter != null) adapter.setCurrentPlayingItem(currentItem);
                scrollToCurrentVideo();
            }

            if (player != null) {
                ensurePlayerPreparedOnce(currentVideoIndex);
                if (player.getCurrentMediaItemIndex() != currentVideoIndex) {
                    player.seekTo(currentVideoIndex, pos);
                } else {
                    player.seekTo(pos);
                }
                player.setPlayWhenReady(playReady);
            }

            // 回来后也联动一次进度条
            if (currentItem != null && isUsingCache) {
                updateCacheStatus(currentItem);
                downloadCurrentVideoWithUi(currentItem);
            }
        }
    }

    // ---------- 生命周期管理方法 ----------

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "Fragment暂停");

        // Fragment暂停时记录当前视频（如果播放时间足够）
        if (player != null && currentItem != null && playHistoryManager != null) {
            long currentPosition = player.getCurrentPosition();
            long duration = player.getDuration();

            // 如果播放进度超过10%或5秒，记录一次
            if ((duration > 0 && currentPosition > duration * 0.1) || currentPosition > 5000) {
                String videoId = currentItem.getVideoId();
                if (TextUtils.isEmpty(videoId)) {
                    videoId = VideoPlayUtils.videoIdFromUrl(currentItem.getVideoURL());
                }

                Log.d(TAG, "Fragment暂停时记录 - VideoID: " + videoId +
                        ", 视频名: " + currentItem.getVideoName() +
                        ", 播放进度: " + (duration > 0 ? (currentPosition * 100 / duration) : 0) + "%");

                String videoSeriesId = getArguments() != null ?
                        getArguments().getString("videoSeriesId") : "";
                String videoSeriesName = getArguments() != null ?
                        getArguments().getString("videoSeriesName") : "";

                playHistoryManager.recordVideoPlay(
                        videoId,
                        videoSeriesId,
                        currentItem.getVideoName(),
                        videoSeriesName,
                        currentItem.getVideoDuration()
                );

                // 查看记录
                playHistoryManager.logAllLocalData();
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "Fragment恢复");
    }

    @Override
    public void onDestroyView() {
        Log.d(TAG, "Fragment销毁");

        // Fragment销毁前，确保记录当前视频
        if (currentItem != null && playHistoryManager != null && player != null) {
            // 获取播放位置
            long position = 0;
            try {
                position = player.getCurrentPosition();
            } catch (Exception e) {
                Log.e(TAG, "获取播放位置失败: " + e.getMessage());
            }

            // 如果播放超过5秒，记录
            if (position > 5000) {
                String videoId = currentItem.getVideoId();
                if (TextUtils.isEmpty(videoId)) {
                    videoId = VideoPlayUtils.videoIdFromUrl(currentItem.getVideoURL());
                }

                Log.d(TAG, "Fragment销毁前最后记录 - VideoID: " + videoId +
                        ", 视频名: " + currentItem.getVideoName() +
                        ", 播放位置: " + (position/1000) + "秒");

                String videoSeriesId = getArguments() != null ?
                        getArguments().getString("videoSeriesId") : "";
                String videoSeriesName = getArguments() != null ?
                        getArguments().getString("videoSeriesName") : "";

                playHistoryManager.recordVideoPlay(
                        videoId,
                        videoSeriesId,
                        currentItem.getVideoName(),
                        videoSeriesName,
                        currentItem.getVideoDuration()
                );
            }
        }

        // 最终调试输出
        if (playHistoryManager != null) {
            Log.d(TAG, "========== Fragment销毁，最终播放历史 ==========");
            playHistoryManager.logAllLocalData();
            int count = playHistoryManager.getRecordCount();
            Log.d(TAG, "总记录数: " + count);
            Log.d(TAG, "==============================================");
        }

        // 清理资源
        super.onDestroyView();
        cancelPreload();
        if (player != null) {
            player.release();
            player = null;
            playerPrepared = false;
        }

        // 重置标志
        isTransitioning = false;
        isPlayingNext = false;
    }

    // ===================== [ADD] ExoPlayer 线程封装工具 =====================
    private Handler playerHandler() {
        if (player != null && player.getApplicationLooper() != null) {
            return new Handler(player.getApplicationLooper());
        }
        return mainHandler; // 兜底：主线程
    }

    private void withPlayerOnMain(@NonNull Runnable r) {
        Handler h = playerHandler();
        if (Looper.myLooper() == h.getLooper()) {
            r.run();
        } else {
            h.post(r);
        }
    }

    private boolean canTouchUiOrPlayer() {
        return isAdded()
                && getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)
                && player != null;
    }
    // =====================================================================

    // ===================== [ADD] Q-Ack 旁路更新实现 =====================

    /** 旁路：后台检查“未ACK”的更新 → 如有则静默下载覆盖 → ACK 回执
     *  若正好是“当前播放的视频”，则先切到下一条释放占用，再进行覆盖，并尝试立即提升 .staging
     */
    private void checkAndRefreshVideoCacheInBackground() {
        if (!isUsingCache || cacheManager == null) return;
        if (videoSeriesIdArg == null || currentItem == null) return;

        final String curUrl = currentItem.getVideoURL();
        final String curVid = !TextUtils.isEmpty(currentItem.getVideoId())
                ? currentItem.getVideoId()
                : VideoPlayUtils.videoIdFromUrl(curUrl);

        final String seriesId = videoSeriesIdArg;
        final String userId = userIdArg;

        final String updatesUrl = ApiConfig.API_VIDEO_UPDATES_RECEIPT
                + "?series_id=" + Uri.encode(seriesId)
                + "&video_id="  + Uri.encode(curVid)
                + "&user_id="   + Uri.encode(userId);

        Request req = new Request.Builder().url(updatesUrl).get().build();
        httpForUpdates.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.w(TAG, "检查更新失败: " + e.getMessage());
            }

            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response resp = response) { // 确保关闭
                    if (!resp.isSuccessful() || resp.body() == null) {
                        Log.w(TAG, "检查更新返回非成功状态码: " + resp.code());
                        return;
                    }
                    UpdateResponseX ur = gson.fromJson(resp.body().charStream(), UpdateResponseX.class);
                    if (ur == null || ur.notices == null || ur.notices.isEmpty()) {
                        Log.d(TAG, "无未ACK更新");
                        return;
                    }
                    final UpdateNotice n = ur.notices.get(0);
                    final String forceUrl = n.download_url;
                    final String videoIdForCache = curVid;
                    Log.i(TAG, "检测到未ACK更新: notice_id=" + n.notice_id
                            + ", targetVideoId=" + videoIdForCache
                            + ", will refresh in background");
                    Integer idxObj = id2Index.get(videoIdForCache);
                    final int idx = (idxObj == null ? -1 : idxObj);

                    // ★★★ 关键：所有 player.* 操作切回到 ExoPlayer 的应用线程/主线程
                    withPlayerOnMain(() -> {
                        if (!canTouchUiOrPlayer()) {
                            // Fragment/Player 暂不可用，直接后台强刷
                            forceRefreshVideoCache(videoIdForCache, forceUrl, idx, n, userId);
                            return;
                        }

                        boolean isCurrent = (idx >= 0 && playerPrepared && player.getCurrentMediaItemIndex() == idx);
                        if (isCurrent) {
                            int next = (idx + 1) % (videoList == null ? 1 : videoList.size());
                            showShortToast("检测到此视频有更新，已临时切到下一条，后台更新中…");
                            switchToVideoByIndex(next);

                            // 切走后稍等再开始强制刷新
                            mainHandler.postDelayed(() ->
                                            forceRefreshVideoCache(videoIdForCache, forceUrl, idx, n, userId),
                                    700
                            );
                        } else {
                            // 当前未播放该条，直接后台强刷
                            forceRefreshVideoCache(videoIdForCache, forceUrl, idx, n, userId);
                        }
                    });
                }
            }
        });
    }

    // 提取的强制刷新逻辑（下载 → 尝试提升 → ACK → UI小收尾）
    private void forceRefreshVideoCache(String videoIdForCache,
                                        String forceUrl,
                                        int idx,
                                        @NonNull UpdateNotice n,
                                        @NonNull String userId) {
        if (!isUsingCache || cacheManager == null) return;

        cacheManager.downloadAndCacheVideoForce(videoIdForCache, forceUrl,
                new VideoCacheManager.DownloadCallback() {
                    @Override public void onStart(String id) {}

                    @Override public void onProgress(String id, int progress) {}

                    @Override public void onSuccess(String id, String localPath) {
                        Log.i(TAG, "旁路更新完成(可能已落到 .staging)");
                        // 统一使用智能退避 promote，并在成功后 maybeSwapToLocal(idx)
                        scheduleTryPromoteWithBackoff(videoIdForCache, forceUrl, idx);

                        // 成功与否都回 ACK（服务端可据 status 判定）
                        ackUpdate(ApiConfig.API_VIDEO_UPDATES_RECEIPT, n.notice_id, userId, videoIdForCache, true, null);
                    }

                    @Override public void onError(String id, String error) {
                        Log.w(TAG, "旁路更新下载失败: " + error);
                        ackUpdate(ApiConfig.API_VIDEO_UPDATES_RECEIPT, n.notice_id, userId, videoIdForCache, false, "download_or_verify_failed");
                    }
                });
    }

    /** 回执 ACK：POST /api/videos/updates/{noticeId}/ack */
    private void ackUpdate(String apiRoot, long noticeId, String userId, String videoId, boolean success, @Nullable String failReason) {
        try {
            String ackUrl = apiRoot + "/" + noticeId + "/ack";
            String payload = "{\"user_id\":\""+userId+"\",\"video_id\":\""+videoId+"\","
                    + "\"status\":\""+(success?"success":"failed")+"\","
                    + "\"fail_reason\":\""+(failReason==null?"":failReason.replace("\"","'"))+"\"}";
            RequestBody body = RequestBody.create(payload, JSON);
            Request req = new Request.Builder().url(ackUrl).post(body).build();
            httpForUpdates.newCall(req).enqueue(new Callback() {
                @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.w(TAG, "ACK 上报失败: " + e.getMessage());
                }
                @Override public void onResponse(@NonNull Call call, @NonNull Response response) {
                    Log.d(TAG, "ACK 上报完成，code=" + response.code());
                    response.close();
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "ACK 构造/发送异常: " + t.getMessage());
        }
    }

    private void showShortToast(String msg) {
        try {
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
        } catch (Throwable ignore) {}
    }

    // =================== [END] Q-Ack 旁路更新实现 ===================
}
