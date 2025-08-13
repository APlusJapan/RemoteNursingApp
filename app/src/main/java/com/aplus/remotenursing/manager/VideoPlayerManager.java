package com.aplus.remotenursing.manager;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.Log;
import android.widget.VideoView;
import android.widget.ProgressBar;
import android.widget.Toast;

/**
 * 视频播放助手类
 * 集成缓存功能，自动处理本地缓存和远程下载
 */
public class VideoPlayerManager {
    private static final String TAG = "VideoPlayerManager";

    private Context context;
    private VideoCacheManager cacheManager;
    private VideoView videoView;
    private ProgressBar downloadProgressBar;

    public VideoPlayerManager(Context context, VideoView videoView, ProgressBar downloadProgressBar) {
        this.context = context;
        this.videoView = videoView;
        this.downloadProgressBar = downloadProgressBar;
        this.cacheManager = VideoCacheManager.getInstance(context);
    }

    /**
     * 播放视频（自动处理缓存）
     * @param videoId 视频ID
     * @param videoUrl 视频URL
     */
    public void playVideo(String videoId, String videoUrl) {
        Log.d(TAG, "准备播放视频: " + videoId);

        // 首先检查本地缓存
        String localPath = cacheManager.getLocalVideoPath(videoId, videoUrl);

        if (localPath != null) {
            // 使用本地缓存播放
            Log.d(TAG, "使用本地缓存播放: " + localPath);
            playLocalVideo(localPath);
        } else {
            // 需要下载视频
            Log.d(TAG, "本地无缓存，开始下载: " + videoId);
            downloadAndPlayVideo(videoId, videoUrl);
        }
    }

    /**
     * 播放本地视频
     */
    private void playLocalVideo(String localPath) {
        try {
            Uri videoUri = Uri.parse("file://" + localPath);
            videoView.setVideoURI(videoUri);

            videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    Log.d(TAG, "本地视频准备完成，开始播放");
                    hideDownloadProgress();
                    videoView.start();
                }
            });

            videoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    Log.e(TAG, "本地视频播放错误: what=" + what + ", extra=" + extra);
                    showError("本地视频播放失败");
                    return true;
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "播放本地视频异常", e);
            showError("播放本地视频失败");
        }
    }

    /**
     * 下载并播放视频
     */
    private void downloadAndPlayVideo(String videoId, String videoUrl) {
        showDownloadProgress();

        cacheManager.downloadAndCacheVideo(videoId, videoUrl, new VideoCacheManager.DownloadCallback() {
            @Override
            public void onStart(String videoId) {
                Log.d(TAG, "开始下载视频: " + videoId);
                showMessage("开始下载视频...");
            }

            @Override
            public void onProgress(String videoId, int progress) {
                Log.d(TAG, "下载进度: " + videoId + " - " + progress + "%");
                updateDownloadProgress(progress);
            }

            @Override
            public void onSuccess(String videoId, String localPath) {
                Log.d(TAG, "视频下载成功: " + videoId);
                hideDownloadProgress();
                showMessage("下载完成，开始播放");

                // 下载完成后播放本地视频
                playLocalVideo(localPath);
            }

            @Override
            public void onError(String videoId, String error) {
                Log.e(TAG, "视频下载失败: " + videoId + " - " + error);
                hideDownloadProgress();
                showError("视频下载失败: " + error);

                // 下载失败时，尝试直接播放在线视频
                playOnlineVideoAsFallback(videoUrl);
            }
        });
    }

    /**
     * 备用方案：直接播放在线视频
     */
    private void playOnlineVideoAsFallback(String videoUrl) {
        Log.d(TAG, "尝试直接播放在线视频: " + videoUrl);
        showMessage("正在加载在线视频...");

        try {
            Uri videoUri = Uri.parse(videoUrl);
            videoView.setVideoURI(videoUri);

            videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    Log.d(TAG, "在线视频准备完成，开始播放");
                    videoView.start();
                }
            });

            videoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    Log.e(TAG, "在线视频播放错误: what=" + what + ", extra=" + extra);
                    showError("视频播放失败，请检查网络连接");
                    return true;
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "播放在线视频异常", e);
            showError("无法播放视频");
        }
    }

    /**
     * 显示下载进度
     */
    private void showDownloadProgress() {
        if (downloadProgressBar != null) {
            downloadProgressBar.setVisibility(android.view.View.VISIBLE);
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
            downloadProgressBar.setVisibility(android.view.View.GONE);
        }
    }

    /**
     * 显示消息
     */
    private void showMessage(String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    /**
     * 显示错误消息
     */
    private void showError(String error) {
        Toast.makeText(context, error, Toast.LENGTH_LONG).show();
    }

    /**
     * 预加载视频（后台下载，不播放）
     */
    public void preloadVideo(String videoId, String videoUrl) {
        String localPath = cacheManager.getLocalVideoPath(videoId, videoUrl);
        if (localPath == null) {
            // 后台下载，不显示进度
            cacheManager.downloadAndCacheVideo(videoId, videoUrl, new VideoCacheManager.DownloadCallback() {
                @Override
                public void onStart(String videoId) {
                    Log.d(TAG, "开始预加载视频: " + videoId);
                }

                @Override
                public void onProgress(String videoId, int progress) {
                    // 预加载时不显示进度
                }

                @Override
                public void onSuccess(String videoId, String localPath) {
                    Log.d(TAG, "视频预加载完成: " + videoId);
                }

                @Override
                public void onError(String videoId, String error) {
                    Log.w(TAG, "视频预加载失败: " + videoId + " - " + error);
                }
            });
        }
    }

    /**
     * 获取缓存状态
     */
    public boolean isVideoCached(String videoId, String videoUrl) {
        return cacheManager.getLocalVideoPath(videoId, videoUrl) != null;
    }

    /**
     * 清理缓存
     */
    public void clearCache() {
        cacheManager.clearCache();
        showMessage("缓存已清理");
    }

    /**
     * 获取缓存大小（格式化显示）
     */
    public String getCacheSizeFormatted() {
        long sizeInBytes = cacheManager.getCacheSize();
        if (sizeInBytes < 1024) {
            return sizeInBytes + " B";
        } else if (sizeInBytes < 1024 * 1024) {
            return String.format("%.1f KB", sizeInBytes / 1024.0);
        } else if (sizeInBytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", sizeInBytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", sizeInBytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
}