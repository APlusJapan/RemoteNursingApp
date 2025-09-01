package com.aplus.remotenursing;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

import com.aplus.remotenursing.common.InfoPopup;
import com.aplus.remotenursing.adapters.VideoTaskDetailAdapter;
import com.aplus.remotenursing.common.ApiConfig;
import com.aplus.remotenursing.models.UserAccount;
import com.aplus.remotenursing.common.UserUtils;
import com.aplus.remotenursing.common.VideoPlayUtils;
import com.aplus.remotenursing.manager.PermissionManager;
import com.aplus.remotenursing.manager.VideoPlaybackManager;
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
import java.util.List;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
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

    // 媒体列表（与播放器解耦）
    private final List<MediaItem> mediaItems = new ArrayList<>();
    private final Map<String, Integer> id2Index = new HashMap<>();

    // 延迟任务
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable preloadRunnable;

    private final Gson gson = new Gson();

    // 防止死循环的标志位
    private boolean isTransitioning = false;
    private boolean isPlayingNext = false;

    // 统一管理器
    private VideoPlaybackManager playbackManager;
    private boolean isUsingCache = false;

    // 用户和系列信息
    private String userIdArg;
    private String videoSeriesIdArg;

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
            InfoPopup.showError(requireContext(), "请先登录");
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new UserLoginFragment())
                    .commit();
            return;
        }
        this.userIdArg = userId;

        // 初始化统一管理器
        playbackManager = VideoPlaybackManager.getInstance(requireContext());

        // 应用启动时尝试上传之前的播放记录
        playbackManager.uploadAndClearRecords();

        String videoSeriesId = getArguments() != null ? getArguments().getString("videoSeriesId") : null;
        this.videoSeriesIdArg = videoSeriesId;
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

            // 7) 一次性获取待更新视频列表并处理更新
            fetchAndProcessVideoUpdates();
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
            // 通过统一管理器检查缓存功能
            isUsingCache = (playbackManager.getLocalVideoPath("test", "test") != null ||
                    playbackManager.getCacheSizeFormatted() != null);
            Log.d("VideoCache", "缓存管理器初始化成功");
            updateCacheInfo();
            isUsingCache = true;
            Log.d("VideoCache", "缓存功能已可用");
        } catch (Exception e) {
            Log.e("VideoCache", "缓存管理器初始化失败: " + e.getMessage());
            isUsingCache = false;
        }
    }

    /** 初始化或复用播放器 */
    private void initExoPlayer(View view) {
        playerView = view.findViewById(R.id.player_view);
        if (playerView == null) {
            Log.e(TAG, "PlayerView not found in layout!");
            return;
        }

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
                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    Log.d(TAG, "播放状态改变: " + playbackState +
                            ", 当前视频: " + (currentItem != null ? currentItem.getVideoName() : "null") +
                            ", VideoID: " + (currentItem != null ? currentItem.getVideoId() : "null"));

                    if (playbackState == Player.STATE_READY) {
                        // 视频开始播放
                        playbackManager.resetPlaybackStats();

                        Log.d(TAG, "视频准备就绪 - VideoID: " +
                                (currentItem != null ? currentItem.getVideoId() : "null") +
                                ", 视频名: " + (currentItem != null ? currentItem.getVideoName() : "null"));

                    } else if (playbackState == Player.STATE_ENDED) {
                        Log.d(TAG, "视频播放结束 - 准备记录");

                        // 结束时加阈值 + 去重
                        if (playbackManager.playedEnoughToCount(player)) {
                            playbackManager.maybeRecordCurrentVideoPlay(
                                    player, currentItem, videoSeriesIdArg,
                                    getArguments() != null ? getArguments().getString("videoSeriesName") : "",
                                    "ended"
                            );
                        } else {
                            Log.d(TAG, "忽略过早的 END（<5s 或 <10%），可能是替换 MediaItem 触发的");
                        }

                        // 只有"有效播放"才自动播下一条
                        if (playbackManager.playedEnoughToCount(player) && !isPlayingNext) {
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

                    // 在切换前记录上一个视频（阈值 + 去重）
                    if (currentItem != null && player != null) {
                        long position = player.getCurrentPosition();
                        if (position > 5000) { // 播放超过5秒才记录
                            Log.d(TAG, "切换前记录上一个视频，播放时长: " + (position/1000) + "秒");
                            playbackManager.maybeRecordCurrentVideoPlay(
                                    player, currentItem, videoSeriesIdArg,
                                    getArguments() != null ? getArguments().getString("videoSeriesName") : "",
                                    "transition"
                            );
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

                            // 重置统计状态
                            playbackManager.resetPlaybackStats();
                        }
                    }
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
                    InfoPopup.showError(requireContext(), "当前视频地址为空，稍后再试");
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
        Log.d(TAG, "buildMediaItems - 开始构建媒体项，视频数量: " + list.size());
        mediaItems.clear();
        id2Index.clear();
        for (int i = 0; i < list.size(); i++) {
            VideoTaskDetail d = list.get(i);
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
                    .setTag(d)
                    .build();
            mediaItems.add(mi);
            id2Index.put(vid, i);
        }

        Log.d(TAG, "buildMediaItems - 完成，创建了 " + mediaItems.size() + " 个媒体项");

        // 设置索引映射到管理器
        playbackManager.setIndexMapping(id2Index);
        Log.d(TAG, "buildMediaItems - 索引映射已设置到 VideoPlaybackManager");
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
        if (player == null || videoList == null || index < 0 || index >= videoList.size()) {
            Log.w(TAG, "(小屏模式)switchToVideoByIndex 参数无效: index=" + index + ", videoListSize=" + (videoList == null ? "null" : videoList.size()));
            return;
        }
        if (index == currentVideoIndex) {
            Log.d(TAG, "(小屏模式)switchToVideoByIndex 索引未变: " + index);
            return;
        }

        Log.i(TAG, "(小屏模式)switchToVideoByIndex - 从索引 " + currentVideoIndex + " 切换到 " + index);
        Log.i(TAG, "  切换前视频: " + (currentItem != null ? currentItem.getVideoName() : "null"));
        Log.i(TAG, "  切换后视频: " + videoList.get(index).getVideoName());

        // 切换前记录当前视频（阈值 + 去重 + 避免 END 后重复记）
        if (currentItem != null && player != null) {
            int state = player.getPlaybackState();
            long position = player.getCurrentPosition();

            if (state != Player.STATE_ENDED && position > 5000) { // >5s 才算有效
                Log.d(TAG, "手动切换前记录当前视频 - VideoID: " + currentItem.getVideoId() +
                        ", 视频名: " + currentItem.getVideoName() +
                        ", 播放位置: " + (position/1000) + "秒");

                playbackManager.maybeRecordCurrentVideoPlay(
                        player, currentItem, videoSeriesIdArg,
                        getArguments() != null ? getArguments().getString("videoSeriesName") : "",
                        "manual_switch"
                );

                // 查看记录状态
                playbackManager.logAllLocalData();
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
        playbackManager.resetPlaybackStats();

        // 延迟重置标志
        mainHandler.postDelayed(() -> {
            isTransitioning = false;
            Log.d(TAG, "切换完成，重置isTransitioning标志");
        }, 1500);

        // 后台缓存
        if (isUsingCache) {
            playbackManager.cacheVideoInBackground(currentItem);
        }

        // 切换到新视频后，也尝试旁路更新该新视频的缓存
        // checkAndRefreshVideoCacheInBackground(); // 旧逻辑已移除，改为一次性处理
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
        if (!isUsingCache || videoList == null || videoList.isEmpty()) return;
        int nextIndex = (currentVideoIndex + 1) % videoList.size();
        VideoTaskDetail nextVideo = videoList.get(nextIndex);

        String url = nextVideo.getVideoURL();
        String vid = nextVideo.getVideoId();
        if (TextUtils.isEmpty(vid)) {
            vid = VideoPlayUtils.videoIdFromUrl(url);
        }
        String localPath = playbackManager.getLocalVideoPath(vid, url);
        if (localPath != null) {
            Log.d("VideoCache", "下一个视频已缓存");
            return;
        }

        Log.d("VideoCache", "开始预加载下一个视频: " + nextVideo.getVideoName());
        playbackManager.cacheVideoInBackground(nextVideo);
    }

    // ---------- UI：缓存状态/按钮/进度条联动 ----------

    /** 带 UI 联动地下载当前视频 */
    private void downloadCurrentVideoWithUi(@NonNull VideoTaskDetail item) {
        if (!isUsingCache) return;

        playbackManager.downloadCurrentVideoWithUi(item, new VideoPlaybackManager.DownloadProgressCallback() {
            @Override
            public void onShowProgress() {
                showDownloadProgress();
            }

            @Override
            public void onHideProgress() {
                hideDownloadProgress();
            }

            @Override
            public void onProgressUpdate(int progress) {
                updateDownloadProgress(progress);
            }

            @Override
            public void onStatusUpdate(String status) {
                if (btnCacheStatus != null) btnCacheStatus.setText(status);
            }

            @Override
            public void onCacheInfoUpdate() {
                updateCacheInfo();
            }

            @Override
            public void onSwapToLocal(Integer index) {
                if (index != null) maybeSwapToLocal(index);
            }

            @Override
            public void onSwitchToNext(int currentIndex) {
                if (player != null && player.getCurrentMediaItemIndex() == currentIndex) {
                    int next = (currentIndex + 1) % (videoList == null ? 1 : videoList.size());
                    switchToVideoByIndex(next);
                }
            }

            @Override
            public void onShowShortToast(String message) {
                showShortToast(message);
            }
        });
    }

    private void updateCacheStatus(VideoTaskDetail item) {
        if (btnCacheStatus == null) return;
        if (!isUsingCache) {
            btnCacheStatus.setText("缓存未可用");
            return;
        }
        try {
            String url = item.getVideoURL();
            String vid = item.getVideoId();
            if (TextUtils.isEmpty(vid)) {
                vid = VideoPlayUtils.videoIdFromUrl(url);
            }
            Log.d("VideoCache", "检查缓存 - VID: " + vid + ", URL: " + url);
            boolean isCached = playbackManager.getLocalVideoPath(vid, url) != null;
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
        if (!isUsingCache || videoList == null) {
            InfoPopup.showError(requireContext(), "缓存功能不可用");
            return;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle("预加载确认")
                .setMessage(String.format("将预加载 %d 个视频，可能需要较长时间和消耗流量，确定继续吗？", videoList.size()))
                .setPositiveButton("确定", (dialog, which) -> {
                    playbackManager.preloadAllVideos(videoList, () -> {
                        InfoPopup.showSuccess(requireContext(), "开始后台预加载");
                    });
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
        boolean isCached = isUsingCache &&
                playbackManager.getLocalVideoPath(vid, currentItem.getVideoURL()) != null;
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
        if (!isUsingCache) return;
        String cacheSize = playbackManager.getCacheSizeFormatted();
        new AlertDialog.Builder(requireContext())
                .setTitle("清理缓存")
                .setMessage("当前缓存大小: " + cacheSize + "\n\n确定要清理所有缓存吗？")
                .setPositiveButton("确定", (dialog, which) -> {
                    Log.d("VideoCache", "清理缓存");
                    playbackManager.clearCache();
                    updateCacheInfo();
                    if (currentItem != null) updateCacheStatus(currentItem);
                    hideDownloadProgress();
                    InfoPopup.showSuccess(requireContext(), "缓存已清理");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void updateCacheInfo() {
        if (tvCacheInfo != null && isUsingCache) {
            String cacheSize = playbackManager.getCacheSizeFormatted();
            tvCacheInfo.setText("缓存大小: " + cacheSize);
            Log.d("VideoCache", "缓存信息更新: " + cacheSize);
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
        OkHttpClient client = new OkHttpClient();
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
            InfoPopup.showError(requireContext(), "需要存储权限才能缓存视频");
        }
    }

    // 若该 index 已有本地缓存，则把该 MediaItem 换成 file://
    private void maybeSwapToLocal(int index) {
        if (player == null || videoList == null || index < 0 || index >= videoList.size()) return;
        if (!isUsingCache) return;

        VideoTaskDetail d = videoList.get(index);
        String url = d.getVideoURL();
        String vid = d.getVideoId();
        if (TextUtils.isEmpty(vid)) {
            vid = VideoPlayUtils.videoIdFromUrl(url);
        }

        String local = playbackManager.getLocalVideoPath(vid, url);
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

            Log.d(TAG, "全屏返回 - returnedIndex: " + returnedIndex + ", 当前index: " + currentVideoIndex + ", pos: " + pos);

            // 更新小屏的当前视频信息
            if (videoList != null && returnedIndex >= 0 && returnedIndex < videoList.size()) {
                // 关键修复：先更新currentVideoIndex，再更新currentItem
                currentVideoIndex = returnedIndex;
                currentItem = videoList.get(currentVideoIndex);

                Log.d(TAG, "更新小屏状态 - 新index: " + currentVideoIndex + ", 视频名: " + currentItem.getVideoName());

                if (adapter != null) {
                    adapter.setCurrentPlayingItem(currentItem);
                }
                scrollToCurrentVideo();

                // 更新缓存状态显示
                updateCacheStatus(currentItem);
            }

            if (player != null) {
                // 确保播放器已准备
                ensurePlayerPreparedOnce(currentVideoIndex);

                // 关键修复：直接跳转到正确的索引位置
                Log.d(TAG, "播放器跳转到index: " + currentVideoIndex + ", position: " + pos);
                player.seekTo(currentVideoIndex, pos);
                player.setPlayWhenReady(playReady);

                // 重置播放统计（因为是从全屏返回，相当于重新开始）
                playbackManager.resetPlaybackStats();
            }

            // 回来后联动进度条
            if (currentItem != null && isUsingCache) {
                downloadCurrentVideoWithUi(currentItem);
            }

            Log.d(TAG, "全屏返回处理完成");
        }
    }

    // ---------- 生命周期管理方法 ----------

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "Fragment暂停");

        // Fragment暂停时记录当前视频（如果播放时间足够）
        if (player != null && currentItem != null) {
            long currentPosition = player.getCurrentPosition();
            long duration = player.getDuration();

            // 如果播放进度超过10%或5秒，记录一次
            if ((duration > 0 && currentPosition > duration * 0.1) || currentPosition > 5000) {
                Log.d(TAG, "Fragment暂停时记录 - VideoID: " + currentItem.getVideoId() +
                        ", 视频名: " + currentItem.getVideoName() +
                        ", 播放进度: " + (duration > 0 ? (currentPosition * 100 / duration) : 0) + "%");

                playbackManager.maybeRecordCurrentVideoPlay(
                        player, currentItem, videoSeriesIdArg,
                        getArguments() != null ? getArguments().getString("videoSeriesName") : "",
                        "pause"
                );

                // 查看记录
                playbackManager.logAllLocalData();
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
        if (currentItem != null && player != null) {
            // 获取播放位置
            long position = 0;
            try {
                position = player.getCurrentPosition();
            } catch (Exception e) {
                Log.e(TAG, "获取播放位置失败: " + e.getMessage());
            }

            // 如果播放超过5秒，记录
            if (position > 5000) {
                Log.d(TAG, "Fragment销毁前最后记录 - VideoID: " + currentItem.getVideoId() +
                        ", 视频名: " + currentItem.getVideoName() +
                        ", 播放位置: " + (position/1000) + "秒");

                playbackManager.maybeRecordCurrentVideoPlay(
                        player, currentItem, videoSeriesIdArg,
                        getArguments() != null ? getArguments().getString("videoSeriesName") : "",
                        "destroy"
                );
            }
        }

        // 最终调试输出
        if (playbackManager != null) {
            Log.d(TAG, "========== Fragment销毁，最终播放历史 ==========");
            playbackManager.logAllLocalData();
            int count = playbackManager.getRecordCount();
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

    // ===================== 一次性获取和处理视频更新 =====================

    /**
     * 获取并处理视频更新：一次性从后台获取待更新列表，然后与播放列表对比处理
     */
    private void fetchAndProcessVideoUpdates() {
        Log.d(TAG, "fetchAndProcessVideoUpdates() - 开始执行");
        Log.d(TAG, "参数检查 - isUsingCache: " + isUsingCache +
                ", videoSeriesIdArg: " + videoSeriesIdArg +
                ", videoList: " + (videoList != null ? videoList.size() + " items" : "null"));

        if (!isUsingCache || videoSeriesIdArg == null || videoList == null) {
            Log.w(TAG, "跳过视频更新处理：缓存未启用或参数缺失");
            Log.w(TAG, "  - isUsingCache: " + isUsingCache);
            Log.w(TAG, "  - videoSeriesIdArg: " + videoSeriesIdArg);
            Log.w(TAG, "  - videoList: " + (videoList == null ? "null" : "not null"));
            return;
        }

        // 获取用户账户信息
        try {
            Log.d(TAG, "尝试获取用户账户信息...");
            UserAccount userAccount = UserUtils.getUserAccount(requireContext());
            if (userAccount == null) {
                Log.w(TAG, "无法获取用户账户信息，跳过视频更新");
                return;
            }

            String userId = userAccount.getUserId();
            String projectId = userAccount.getProjectId();
            String teamId = userAccount.getTeamId();

            Log.i(TAG, "用户账户信息获取成功:");
            Log.i(TAG, "  - userId: " + userId);
            Log.i(TAG, "  - projectId: " + projectId);
            Log.i(TAG, "  - teamId: " + teamId);
            Log.i(TAG, "  - seriesId: " + videoSeriesIdArg);

            // 1. 一次性从后台获取待更新视频列表并保存
            Log.d(TAG, "开始调用 fetchAndCachePendingUpdates...");
            playbackManager.fetchAndCachePendingUpdates(videoSeriesIdArg, userId, projectId, teamId, () -> {
                // 2. 获取完成后，处理视频更新
                Log.d(TAG, "fetchAndCachePendingUpdates 完成回调执行");
                Log.d(TAG, "开始调用 processVideoUpdatesInBackground...");

                playbackManager.processVideoUpdatesInBackground(videoList, userId,
                        new VideoPlaybackManager.UpdateProgressCallback() {
                            @Override
                            public boolean isCurrentlyPlaying(int index) {
                                boolean playing = player != null && playerPrepared && player.getCurrentMediaItemIndex() == index;
                                Log.d(TAG, "isCurrentlyPlaying(" + index + "): " + playing);
                                return playing;
                            }

                            @Override
                            public void onShowUpdateMessage(String message) {
                                Log.d(TAG, "onShowUpdateMessage: " + message);
                                showShortToast(message);
                            }

                            @Override
                            public void onSwitchToNext(int currentIndex) {
                                Log.d(TAG, "onSwitchToNext from index: " + currentIndex);
                                int next = (currentIndex + 1) % videoList.size();
                                switchToVideoByIndex(next);
                            }

                            @Override
                            public VideoPlaybackManager.DownloadProgressCallback asDownloadProgressCallback() {
                                Log.d(TAG, "创建 DownloadProgressCallback");
                                return new VideoPlaybackManager.DownloadProgressCallback() {
                                    @Override public void onShowProgress() {
                                        Log.d(TAG, "DownloadProgressCallback.onShowProgress");
                                    }
                                    @Override public void onHideProgress() {
                                        Log.d(TAG, "DownloadProgressCallback.onHideProgress");
                                    }
                                    @Override public void onProgressUpdate(int progress) {
                                        Log.d(TAG, "DownloadProgressCallback.onProgressUpdate: " + progress);
                                    }
                                    @Override public void onStatusUpdate(String status) {
                                        Log.d(TAG, "DownloadProgressCallback.onStatusUpdate: " + status);
                                    }
                                    @Override public void onCacheInfoUpdate() {
                                        Log.d(TAG, "DownloadProgressCallback.onCacheInfoUpdate");
                                        mainHandler.post(() -> updateCacheInfo());
                                    }
                                    @Override public void onSwapToLocal(Integer index) {
                                        Log.d(TAG, "DownloadProgressCallback.onSwapToLocal: " + index);
                                        if (index != null) {
                                            mainHandler.post(() -> maybeSwapToLocal(index));
                                        }
                                    }
                                    @Override public void onSwitchToNext(int currentIndex) {
                                        Log.d(TAG, "DownloadProgressCallback.onSwitchToNext: " + currentIndex);
                                    }
                                    @Override public void onShowShortToast(String message) {
                                        Log.d(TAG, "DownloadProgressCallback.onShowShortToast: " + message);
                                    }
                                };
                            }
                        }
                );
            });

        } catch (Exception e) {
            Log.e(TAG, "处理视频更新异常: " + e.getMessage(), e);
        }
    }

    private void showShortToast(String msg) {
        try {
            InfoPopup.showError(requireContext(), msg);
        } catch (Throwable ignore) {}
    }
}