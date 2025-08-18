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

import java.io.File;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.aplus.remotenursing.adapters.VideoTaskDetailAdapter;
import com.aplus.remotenursing.common.ApiConfig;
import com.aplus.remotenursing.common.UserUtils;
import com.aplus.remotenursing.common.VideoPlayUtils;
import com.aplus.remotenursing.manager.VideoPlayTimeManager;
import com.aplus.remotenursing.manager.VideoPlayHistoryManager;
import com.aplus.remotenursing.manager.PermissionManager;
import com.aplus.remotenursing.manager.VideoCacheManager;
import com.aplus.remotenursing.models.VideoTaskDetail;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

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

    // 播放时长追踪器
    private VideoPlayTimeManager playTimeManager;
    private VideoPlayHistoryManager playHistoryManager;
    private final Gson gson = new Gson();

    // 防止死循环的标志位
    private boolean isTransitioning = false;
    private boolean isPlayingNext = false;

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

        // 初始化播放时长追踪器
        playTimeManager = new VideoPlayTimeManager(getContext());
        playHistoryManager = VideoPlayHistoryManager.getInstance(getContext());

        String videoSeriesId = getArguments() != null ? getArguments().getString("videoSeriesId") : null;
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
                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    Log.d(TAG, "播放状态改变: " + playbackState + ", isTransitioning: " + isTransitioning);

                    if (playbackState == Player.STATE_ENDED) {
                        // 防止重复触发
                        if (isPlayingNext) {
                            Log.d(TAG, "已在播放下一个，忽略STATE_ENDED");
                            return;
                        }

                        // 播放结束时结束记录（这里不受isTransitioning影响）
                        if (playTimeManager != null) {
                            Log.d(TAG, "视频播放结束，结束播放时长记录");
                            playTimeManager.endSession();
                        }

                        // 延迟播放下一个，避免立即重复触发
                        mainHandler.postDelayed(() -> {
                            if (!isPlayingNext) {
                                playNextVideo();
                            }
                        }, 500);

                    } else if (playbackState == Player.STATE_READY) {
                        // 播放准备完成时开始记录（只在真正开始新视频时记录，避免切换过程中重复记录）
                        if (playTimeManager != null && currentItem != null) {
                            String videoId = currentItem.getVideoId();
                            if (TextUtils.isEmpty(videoId)) {
                                videoId = VideoPlayUtils.videoIdFromUrl(currentItem.getVideoURL());
                            }
                            String videoSeriesId = getArguments() != null ? getArguments().getString("videoSeriesId") : "";
                            String videoSeriesName = getArguments() != null ? getArguments().getString("videoSeriesName") : "";

                            Log.d(TAG, "视频准备就绪，开始播放时长记录 - VideoID: " + videoId);
                            playTimeManager.startSession(
                                    videoId,
                                    videoSeriesId,
                                    currentItem.getVideoName(),
                                    videoSeriesName
                            );
                        }
                    }
                }

                @Override
                public void onIsPlayingChanged(boolean isPlaying) {
                    // 播放状态改变时的记录不受isTransitioning影响，确保时长统计准确
                    if (playTimeManager != null) {
                        if (isPlaying) {
                            Log.d(TAG, "开始播放，恢复播放时长记录");
                            playTimeManager.resumeSession();
                        } else {
                            Log.d(TAG, "暂停播放，暂停播放时长记录");
                            playTimeManager.pauseSession();
                        }
                    }
                }

                @Override
                public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
                    Log.d(TAG, "媒体项切换: reason=" + reason + ", isTransitioning=" + isTransitioning);

                    int cur = player.getCurrentMediaItemIndex();
                    if (cur != C.INDEX_UNSET && !isTransitioning) {
                        maybeSwapToLocal(cur);

                        // 更新当前项
                        if (videoList != null && cur < videoList.size() && cur != currentVideoIndex) {
                            Log.d(TAG, "更新当前播放索引: " + currentVideoIndex + " -> " + cur);
                            currentVideoIndex = cur;
                            currentItem = videoList.get(cur);
                            if (adapter != null) {
                                adapter.setCurrentPlayingItem(currentItem);
                            }
                        }
                    }
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
        if (index == currentVideoIndex) return; // 相同索引不处理

        Log.d(TAG, "switchToVideoByIndex - 从索引 " + currentVideoIndex + " 切换到 " + index);

        // 设置切换标志，防止监听器重复触发
        isTransitioning = true;
        isPlayingNext = false;

        // 结束当前播放时长记录（确保当前视频的播放时长被正确记录）
        if (playTimeManager != null) {
            Log.d(TAG, "切换视频前，结束当前视频的播放时长记录");
            playTimeManager.endSession();
        }

        currentVideoIndex = index;
        currentItem = videoList.get(index);

        // 更新适配器
        if (adapter != null) {
            adapter.setCurrentPlayingItem(currentItem);
        }
        scrollToCurrentVideo();

        // 更新缓存状态
        updateCacheStatus(currentItem);
        if (isUsingCache) {
            downloadCurrentVideoWithUi(currentItem);
        }

        // 切换播放器到目标项
        ensurePlayerPreparedOnce(index);
        if (player.getCurrentMediaItemIndex() != index) {
            Log.d(TAG, "播放器切换到索引: " + index);
            player.seekTo(index, 0L);
        }

        player.setPlayWhenReady(true);

        // 延迟重置切换标志（给播放器足够时间完成切换）
        mainHandler.postDelayed(() -> {
            isTransitioning = false;
            Log.d(TAG, "切换完成，重置isTransitioning标志");

            // 切换完成后，开始新视频的播放时长记录
            if (playTimeManager != null && currentItem != null && player != null && player.isPlaying()) {
                String videoId = currentItem.getVideoId();
                if (TextUtils.isEmpty(videoId)) {
                    videoId = VideoPlayUtils.videoIdFromUrl(currentItem.getVideoURL());
                }
                String videoSeriesId = getArguments() != null ? getArguments().getString("videoSeriesId") : "";
                String videoSeriesName = getArguments() != null ? getArguments().getString("videoSeriesName") : "";

                Log.d(TAG, "切换完成后，开始新视频的播放时长记录 - VideoID: " + videoId);
                playTimeManager.startSession(
                        videoId,
                        videoSeriesId,
                        currentItem.getVideoName(),
                        videoSeriesName
                );
            }
        }, 1500); // 增加延迟时间，确保播放器完全切换完成

        // 纯后台缓存下一个
        if (isUsingCache) {
            cacheVideoInBackground(currentItem);
        }
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

    /** 带 UI 联动地下载当前视频 */
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
                mainHandler.post(() -> {
                    hideDownloadProgress();
                    if (btnCacheStatus != null) btnCacheStatus.setText("未缓存");
                    Toast.makeText(requireContext(), "缓存失败，请稍后重试", Toast.LENGTH_SHORT).show();
                });
            }
        });
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
        OkHttpClient client = new OkHttpClient();
        String url = ApiConfig.API_VIDEO_DETAIL_BY_SERIES_ID + videoSeriesId;
        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                if (getActivity() != null) getActivity().runOnUiThread(() -> callback.onResult(null));
            }
            @Override public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String json = response.body().string();
                    List<VideoTaskDetail> list =
                            gson.fromJson(json, new TypeToken<List<VideoTaskDetail>>(){}.getType());
                    if (getActivity() != null) getActivity().runOnUiThread(() -> callback.onResult(list));
                } else {
                    if (getActivity() != null) getActivity().runOnUiThread(() -> callback.onResult(null));
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

    // 生命周期管理方法：
    @Override
    public void onPause() {
        super.onPause();
        // Fragment暂停时暂停记录
        if (playTimeManager != null) {
            Log.d(TAG, "Fragment暂停，暂停播放时长记录");
            playTimeManager.pauseSession();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Fragment恢复时恢复记录（如果播放器正在播放）
        if (playTimeManager != null && player != null && player.isPlaying()) {
            Log.d(TAG, "Fragment恢复，恢复播放时长记录");
            playTimeManager.resumeSession();
        }
    }

    @Override
    public void onDestroyView() {
        // 销毁时结束记录
        if (playTimeManager != null) {
            Log.d(TAG, "Fragment销毁，结束播放时长记录");
            playTimeManager.endSession();
        }

        // 调试 - 打印所有本地数据
        if (playHistoryManager != null) {
            Log.d(TAG, "Fragment销毁，打印本地播放历史数据");
            playHistoryManager.logAllLocalData();
        }

        super.onDestroyView();
        cancelPreload();
        if (player != null) {
            player.release();
            player = null;
            playerPrepared = false;
        }

        // 重置所有标志位
        isTransitioning = false;
        isPlayingNext = false;
    }
}