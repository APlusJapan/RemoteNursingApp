package com.aplus.remotenursing;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
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
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.aplus.remotenursing.adapters.VideoTaskDetailAdapter;
import com.aplus.remotenursing.models.VideoTaskDetail;
import com.aplus.remotenursing.common.ApiConfig;
import com.aplus.remotenursing.common.UserUtil;
import com.aplus.remotenursing.manager.VideoCacheManager;
import com.aplus.remotenursing.manager.PermissionManager;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class VideoTaskDetailFragment extends Fragment {

    private static final int REQ_FULLSCREEN = 1001;
    private static final int REQUEST_STORAGE_PERMISSION = 1002;

    // 原有组件
    private PlayerView playerView;
    private ExoPlayer player;
    private VideoTaskDetail currentItem;
    private List<VideoTaskDetail> videoList;
    private int currentVideoIndex = 0;
    private RecyclerView rvOther;
    private VideoTaskDetailAdapter adapter;
    private TextView tvSeriesTitle;
    private final Gson gson = new Gson();

    // 新增缓存相关组件
    private ProgressBar downloadProgressBar;
    private Button btnCacheStatus;
    private Button btnPreload;
    private Button btnClearCache;
    private TextView tvCacheInfo;
    private VideoCacheManager cacheManager;
    private boolean isUsingCache = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_video_task_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        String userId = UserUtil.loadUserId(requireContext());
        if (userId == null) {
            Toast.makeText(requireContext(), "请先登录", Toast.LENGTH_SHORT).show();
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new UserLoginFragment())
                    .commit();
            return;
        }

        String videoSeriesId = getArguments() != null ? getArguments().getString("videoSeriesId") : null;
        String videoSeriesName = getArguments() != null ? getArguments().getString("videoSeriesName") : "视频系列";

        // 初始化原有控件
        initOriginalViews(view, videoSeriesName);

        // 初始化缓存相关控件
        initCacheViews(view);

        // 检查权限并初始化缓存
        checkPermissionsAndInitCache();

        // 加载视频数据
        fetchVideoList(userId, videoSeriesId, videoList -> {
            if (videoList == null || videoList.isEmpty()) return;

            this.videoList = videoList;
            sortVideosByOrder();
            findAndSetCurrentVideo();

            // 初始化播放器
            initExoPlayer(view);

            // 播放当前视频
            playVideo(currentItem);

            // 设置适配器
            setupAdapter();

            // 预加载下一个视频
            preloadNextVideo();

            // 设置按钮事件
            setupButtonEvents(view, videoSeriesId);
        });
    }

    /**
     * 初始化原有视图组件
     */
    private void initOriginalViews(View view, String videoSeriesName) {
        rvOther = view.findViewById(R.id.rv_other_videos);
        rvOther.setLayoutManager(new LinearLayoutManager(requireContext()));

        tvSeriesTitle = view.findViewById(R.id.tv_more_series);
        if (tvSeriesTitle != null) {
            tvSeriesTitle.setText("更多 " + videoSeriesName + " 视频");
        }
    }

    /**
     * 初始化缓存相关视图组件
     */
    private void initCacheViews(View view) {
        downloadProgressBar = view.findViewById(R.id.progress_download);
        btnCacheStatus = view.findViewById(R.id.btn_cache_status);
        btnPreload = view.findViewById(R.id.btn_preload);
        btnClearCache = view.findViewById(R.id.btn_clear_cache);
        tvCacheInfo = view.findViewById(R.id.tv_cache_info);

        // 设置缓存按钮点击事件
        if (btnCacheStatus != null) {
            btnCacheStatus.setOnClickListener(v -> showCacheInfo());
        }
        if (btnPreload != null) {
            btnPreload.setOnClickListener(v -> preloadAllVideos());
        }
        if (btnClearCache != null) {
            btnClearCache.setOnClickListener(v -> confirmClearCache());
        }
    }

    /**
     * 检查权限并初始化缓存 - 添加调试日志
     */
    private void checkPermissionsAndInitCache() {
        boolean hasPermission = PermissionManager.hasStoragePermission(requireContext());

        // 添加调试日志
        Log.d("VideoCache", "存储权限检查: " + (hasPermission ? "已授权" : "未授权"));

        if (hasPermission) {
            initCacheManager();
        } else {
            Log.d("VideoCache", "请求存储权限");
            PermissionManager.requestStoragePermission(this);
        }
    }

    /**
     * 初始化缓存管理器 - 添加调试日志
     */
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

    /**
     * 初始化ExoPlayer
     */
    private void initExoPlayer(View view) {
        playerView = view.findViewById(R.id.player_view);
        player = new ExoPlayer.Builder(requireContext()).build();
        playerView.setPlayer(player);

        // 添加播放状态监听器，实现自动播放下一个视频
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_ENDED) {
                    playNextVideo();
                }
            }
        });
    }

    /**
     * 设置适配器
     */
    private void setupAdapter() {
        adapter = new VideoTaskDetailAdapter(videoList, item -> {
            currentVideoIndex = videoList.indexOf(item);
            currentItem = item;
            adapter.setCurrentPlayingItem(item);
            scrollToCurrentVideo();
            playVideo(item);
        });
        rvOther.setAdapter(adapter);
        adapter.setCurrentPlayingItem(currentItem);
        scrollToCurrentVideo();
    }

    /**
     * 设置按钮事件
     */
    private void setupButtonEvents(View view, String videoSeriesId) {
        // 全屏按钮点击事件
        view.findViewById(R.id.btn_fullscreen).setOnClickListener(v -> {
            if (player != null && currentItem != null) {
                long pos = player.getCurrentPosition();
                boolean playReady = player.getPlayWhenReady();
                player.pause();
                Intent it = new Intent(requireContext(), VideoFullscreenPlayerActivity.class)
                        .putExtra(VideoFullscreenPlayerActivity.EXTRA_URL, currentItem.getVideoURL())
                        .putExtra(VideoFullscreenPlayerActivity.EXTRA_START_POS, pos)
                        .putExtra(VideoFullscreenPlayerActivity.EXTRA_START_PLAYREADY, playReady)
                        .putExtra(VideoFullscreenPlayerActivity.EXTRA_VIDEO_SERIES_ID, videoSeriesId)
                        .putExtra(VideoFullscreenPlayerActivity.EXTRA_CURRENT_VIDEO_INDEX, currentVideoIndex);
                startActivityForResult(it, REQ_FULLSCREEN);
            }
        });

        // 返回按钮点击事件
        ImageButton backButton = view.findViewById(R.id.VideoDetailPage_btn_back);
        if (backButton != null) {
            backButton.setOnClickListener(v -> requireActivity().onBackPressed());
        }
    }

    /**
     * 播放视频（集成缓存功能）
     */
    private void playVideo(VideoTaskDetail item) {
        if (player == null || item == null || item.getVideoURL() == null) {
            Log.w("VideoCache", "播放视频失败: player、item或URL为null");
            return;
        }

        Log.d("VideoCache", "开始播放视频: " + item.getVideoName());

        // 更新缓存状态显示
        updateCacheStatus(item);

        if (isUsingCache && cacheManager != null) {
            playVideoWithCache(item);
        } else {
            Log.d("VideoCache", "缓存功能未启用，直接播放在线视频");
            playOnlineVideo(item);
        }
    }

    /**
     * 使用缓存播放视频
     */
    private void playVideoWithCache(VideoTaskDetail item) {
        String localPath = cacheManager.getLocalVideoPath(item.getVideoId(), item.getVideoURL());

        if (localPath != null) {
            Log.d("VideoCache", "使用本地缓存播放: " + localPath);
            playLocalVideo(localPath);
        } else {
            Log.d("VideoCache", "本地无缓存，开始下载");
            downloadAndPlayVideo(item);
        }
    }

    /**
     * 播放本地缓存视频
     */
    private void playLocalVideo(String localPath) {
        try {
            Uri videoUri = Uri.parse("file://" + localPath);
            player.setMediaItem(MediaItem.fromUri(videoUri));
            player.prepare();
            player.play();
            hideDownloadProgress();
            Log.d("VideoCache", "本地视频播放成功");
        } catch (Exception e) {
            Log.e("VideoCache", "播放本地视频失败: " + e.getMessage());
            Toast.makeText(requireContext(), "播放本地视频失败", Toast.LENGTH_SHORT).show();
            playOnlineVideo(currentItem);
        }
    }

    /**
     * 下载并播放视频
     */
    private void downloadAndPlayVideo(VideoTaskDetail item) {
        Log.d("VideoCache", "开始下载视频: " + item.getVideoId());
        showDownloadProgress();

        cacheManager.downloadAndCacheVideo(item.getVideoId(), item.getVideoURL(),
                new VideoCacheManager.DownloadCallback() {
                    @Override
                    public void onStart(String videoId) {
                        requireActivity().runOnUiThread(() -> {
                            Log.d("VideoCache", "下载开始: " + videoId);
                            Toast.makeText(requireContext(), "开始下载视频...", Toast.LENGTH_SHORT).show();
                        });
                    }

                    @Override
                    public void onProgress(String videoId, int progress) {
                        requireActivity().runOnUiThread(() -> {
                            Log.d("VideoCache", "下载进度: " + progress + "%");
                            updateDownloadProgress(progress);
                        });
                    }

                    @Override
                    public void onSuccess(String videoId, String localPath) {
                        requireActivity().runOnUiThread(() -> {
                            Log.d("VideoCache", "下载成功: " + localPath);
                            hideDownloadProgress();
                            playLocalVideo(localPath);
                            updateCacheStatus(item);
                            updateCacheInfo();
                        });
                    }

                    @Override
                    public void onError(String videoId, String error) {
                        requireActivity().runOnUiThread(() -> {
                            Log.e("VideoCache", "下载失败: " + error);
                            hideDownloadProgress();
                            Toast.makeText(requireContext(), "下载失败，播放在线视频", Toast.LENGTH_SHORT).show();
                            playOnlineVideo(item);
                        });
                    }
                });
    }

    /**
     * 播放在线视频
     */
    private void playOnlineVideo(VideoTaskDetail item) {
        Log.d("VideoCache", "播放在线视频: " + item.getVideoURL());
        player.setMediaItem(MediaItem.fromUri(item.getVideoURL()));
        player.prepare();
        player.play();
    }

    /**
     * 更新缓存状态显示 - 添加调试日志
     */
    private void updateCacheStatus(VideoTaskDetail item) {
        if (btnCacheStatus == null) {
            Log.w("VideoCache", "缓存状态按钮为null");
            return;
        }

        if (!isUsingCache) {
            Log.w("VideoCache", "缓存功能未启用");
            btnCacheStatus.setText("缓存未启用");
            return;
        }

        if (cacheManager == null) {
            Log.w("VideoCache", "缓存管理器为null");
            btnCacheStatus.setText("缓存不可用");
            return;
        }

        try {
            String videoId = item.getVideoId();
            String videoUrl = item.getVideoURL();

            Log.d("VideoCache", "检查缓存 - VideoID: " + videoId + ", URL: " + videoUrl);

            boolean isCached = cacheManager.getLocalVideoPath(videoId, videoUrl) != null;

            Log.d("VideoCache", "缓存状态: " + (isCached ? "已缓存" : "未缓存"));

            btnCacheStatus.setText(isCached ? "已缓存" : "未缓存");
            btnCacheStatus.setBackgroundColor(isCached ?
                    getResources().getColor(android.R.color.holo_green_light) :
                    getResources().getColor(android.R.color.darker_gray));
        } catch (Exception e) {
            Log.e("VideoCache", "更新缓存状态失败: " + e.getMessage());
            btnCacheStatus.setText("检查失败");
        }
    }

    /**
     * 预加载下一个视频
     */
    private void preloadNextVideo() {
        if (!isUsingCache || cacheManager == null || videoList == null) {
            Log.d("VideoCache", "预加载条件不满足");
            return;
        }

        int nextIndex = (currentVideoIndex + 1) % videoList.size();
        VideoTaskDetail nextVideo = videoList.get(nextIndex);

        String localPath = cacheManager.getLocalVideoPath(nextVideo.getVideoId(), nextVideo.getVideoURL());
        if (localPath == null) {
            Log.d("VideoCache", "开始预加载下一个视频: " + nextVideo.getVideoName());
            cacheManager.downloadAndCacheVideo(nextVideo.getVideoId(), nextVideo.getVideoURL(),
                    new VideoCacheManager.DownloadCallback() {
                        @Override
                        public void onStart(String videoId) {}
                        @Override
                        public void onProgress(String videoId, int progress) {}
                        @Override
                        public void onSuccess(String videoId, String localPath) {
                            requireActivity().runOnUiThread(() -> {
                                Log.d("VideoCache", "预加载成功: " + videoId);
                                updateCacheInfo();
                            });
                        }
                        @Override
                        public void onError(String videoId, String error) {
                            Log.w("VideoCache", "预加载失败: " + error);
                        }
                    });
        } else {
            Log.d("VideoCache", "下一个视频已缓存");
        }
    }

    /**
     * 预加载所有视频
     */
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
                    for (VideoTaskDetail video : videoList) {
                        cacheManager.downloadAndCacheVideo(video.getVideoId(), video.getVideoURL(),
                                new VideoCacheManager.DownloadCallback() {
                                    @Override
                                    public void onStart(String videoId) {}
                                    @Override
                                    public void onProgress(String videoId, int progress) {}
                                    @Override
                                    public void onSuccess(String videoId, String localPath) {
                                        requireActivity().runOnUiThread(() -> updateCacheInfo());
                                    }
                                    @Override
                                    public void onError(String videoId, String error) {}
                                });
                    }
                    Toast.makeText(requireContext(), "开始后台预加载", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 显示缓存信息
     */
    private void showCacheInfo() {
        if (currentItem == null) return;

        boolean isCached = isUsingCache && cacheManager != null &&
                cacheManager.getLocalVideoPath(currentItem.getVideoId(), currentItem.getVideoURL()) != null;

        String message = String.format(
                "视频: %s\n状态: %s\nVideoID: %s",
                currentItem.getVideoName(),
                isCached ? "已缓存" : "未缓存",
                currentItem.getVideoId()
        );

        new AlertDialog.Builder(requireContext())
                .setTitle("视频信息")
                .setMessage(message)
                .setPositiveButton("确定", null)
                .show();
    }

    /**
     * 确认清理缓存
     */
    private void confirmClearCache() {
        if (!isUsingCache || cacheManager == null) {
            return;
        }

        String cacheSize = getCacheSizeFormatted();

        new AlertDialog.Builder(requireContext())
                .setTitle("清理缓存")
                .setMessage("当前缓存大小: " + cacheSize + "\n\n确定要清理所有缓存吗？")
                .setPositiveButton("确定", (dialog, which) -> {
                    Log.d("VideoCache", "清理缓存");
                    cacheManager.clearCache();
                    updateCacheInfo();
                    if (currentItem != null) {
                        updateCacheStatus(currentItem);
                    }
                    Toast.makeText(requireContext(), "缓存已清理", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 更新缓存信息
     */
    private void updateCacheInfo() {
        if (tvCacheInfo != null && isUsingCache && cacheManager != null) {
            String cacheSize = getCacheSizeFormatted();
            tvCacheInfo.setText("缓存大小: " + cacheSize);
            Log.d("VideoCache", "缓存信息更新: " + cacheSize);
        }
    }

    /**
     * 获取格式化的缓存大小
     */
    private String getCacheSizeFormatted() {
        if (!isUsingCache || cacheManager == null) {
            return "0 MB";
        }

        long sizeInBytes = cacheManager.getCacheSize();
        if (sizeInBytes < 1024 * 1024) {
            return String.format("%.1f KB", sizeInBytes / 1024.0);
        } else if (sizeInBytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", sizeInBytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", sizeInBytes / (1024.0 * 1024.0 * 1024.0));
        }
    }

    /**
     * 显示下载进度
     */
    private void showDownloadProgress() {
        if (downloadProgressBar != null) {
            downloadProgressBar.setVisibility(View.VISIBLE);
            downloadProgressBar.setProgress(0);
        }
    }

    /**
     * 更新下载进度
     */
    private void updateDownloadProgress(int progress) {
        if (downloadProgressBar != null) {
            downloadProgressBar.setProgress(progress);
        }
    }

    /**
     * 隐藏下载进度
     */
    private void hideDownloadProgress() {
        if (downloadProgressBar != null) {
            downloadProgressBar.setVisibility(View.GONE);
        }
    }

    // ==================== 原有方法保持不变 ====================

    private void sortVideosByOrder() {
        if (videoList != null) {
            Collections.sort(videoList, new Comparator<VideoTaskDetail>() {
                @Override
                public int compare(VideoTaskDetail v1, VideoTaskDetail v2) {
                    Integer order1 = v1.getVideoOrder();
                    Integer order2 = v2.getVideoOrder();

                    if (order1 == null && order2 == null) return 0;
                    if (order1 == null) return 1;
                    if (order2 == null) return -1;

                    return order1.compareTo(order2);
                }
            });
        }
    }

    private void findAndSetCurrentVideo() {
        currentVideoIndex = 0;
        currentItem = videoList.get(0);

        for (int i = 0; i < videoList.size(); i++) {
            VideoTaskDetail video = videoList.get(i);
            if (video.isCurrentlyPlaying()) {
                currentVideoIndex = i;
                currentItem = video;
                break;
            }
        }
    }

    private void playNextVideo() {
        if (videoList != null && videoList.size() > 0) {
            if (currentVideoIndex >= videoList.size() - 1) {
                currentVideoIndex = 0;
            } else {
                currentVideoIndex++;
            }

            currentItem = videoList.get(currentVideoIndex);

            if (adapter != null) {
                adapter.setCurrentPlayingItem(currentItem);
            }

            scrollToCurrentVideo();
            playVideo(currentItem);
        }
    }

    private void scrollToCurrentVideo() {
        if (rvOther != null && currentVideoIndex >= 0) {
            rvOther.postDelayed(() -> {
                LinearLayoutManager layoutManager = (LinearLayoutManager) rvOther.getLayoutManager();
                if (layoutManager != null) {
                    layoutManager.scrollToPositionWithOffset(currentVideoIndex, 100);
                }
            }, 100);
        }
    }

    private interface VideoListCallback {
        void onResult(List<VideoTaskDetail> videoList);
    }

    private void fetchVideoList(String userId, String videoSeriesId, VideoListCallback callback) {
        OkHttpClient client = new OkHttpClient();
        String url = ApiConfig.API_VIDEO_DETAIL_BY_SERIES_ID + videoSeriesId;
        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> callback.onResult(null));
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String json = response.body().string();
                    List<VideoTaskDetail> videoList = gson.fromJson(json, new TypeToken<List<VideoTaskDetail>>(){}.getType());
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> callback.onResult(videoList));
                    }
                } else {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> callback.onResult(null));
                    }
                }
            }
        });
    }

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

    @Override
    public void onActivityResult(int req, int res, @Nullable Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_FULLSCREEN && res == Activity.RESULT_OK && data != null) {
            long pos = data.getLongExtra(VideoFullscreenPlayerActivity.EXTRA_END_POS, 0L);
            boolean playReady = data.getBooleanExtra(VideoFullscreenPlayerActivity.EXTRA_END_PLAYREADY, true);
            int returnedIndex = data.getIntExtra(VideoFullscreenPlayerActivity.EXTRA_CURRENT_VIDEO_INDEX, currentVideoIndex);

            if (returnedIndex != currentVideoIndex && videoList != null && returnedIndex < videoList.size()) {
                currentVideoIndex = returnedIndex;
                currentItem = videoList.get(currentVideoIndex);
                if (adapter != null) {
                    adapter.setCurrentPlayingItem(currentItem);
                }
                scrollToCurrentVideo();

                if (player != null) {
                    player.setMediaItem(MediaItem.fromUri(currentItem.getVideoURL()));
                    player.prepare();
                }
            }

            if (player != null) {
                player.seekTo(pos);
                player.setPlayWhenReady(playReady);
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (player != null) {
            player.release();
            player = null;
        }
    }
}