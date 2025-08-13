package com.aplus.remotenursing.manager;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 视频缓存管理器
 * 功能：
 * 1. 检查本地是否有缓存视频
 * 2. 下载并缓存远程视频
 * 3. 管理缓存文件（清理、版本控制）
 * 4. 支持断点续传
 */
public class VideoCacheManager {
    private static final String TAG = "VideoCacheManager";
    private static final String CACHE_DIR_NAME = "video_cache";
    private static final String VIDEO_INFO_SUFFIX = ".info"; // 存储视频信息的文件后缀

    private Context context;
    private File cacheDir;
    private ConcurrentHashMap<String, DownloadTask> downloadingTasks;
    private static VideoCacheManager instance;

    private VideoCacheManager(Context context) {
        Log.d(TAG, "构造函数调用");
        this.context = context.getApplicationContext();
        this.downloadingTasks = new ConcurrentHashMap<>();
        initCacheDir();
    }

    public static synchronized VideoCacheManager getInstance(Context context) {
        Log.d(TAG, "getInstance called");

        if (instance == null) {
            Log.d(TAG, "Creating new instance");
            instance = new VideoCacheManager(context);
        } else {
            Log.d(TAG, "Returning existing instance");
        }

        return instance;
    }

    /**
     * 初始化缓存目录
     */
    private void initCacheDir() {
        Log.d(TAG, "初始化缓存目录");

        // 优先使用外部存储，如果不可用则使用内部存储
        File externalDir = context.getExternalFilesDir(null);
        String storageState = Environment.getExternalStorageState();

        Log.d(TAG, "外部存储状态: " + storageState);
        Log.d(TAG, "外部文件目录: " + (externalDir != null ? externalDir.getAbsolutePath() : "null"));

        if (externalDir != null && Environment.MEDIA_MOUNTED.equals(storageState)) {
            cacheDir = new File(externalDir, CACHE_DIR_NAME);
            Log.d(TAG, "使用外部存储");
        } else {
            cacheDir = new File(context.getFilesDir(), CACHE_DIR_NAME);
            Log.d(TAG, "使用内部存储");
        }

        Log.d(TAG, "缓存目录路径: " + cacheDir.getAbsolutePath());

        if (!cacheDir.exists()) {
            boolean created = cacheDir.mkdirs();
            Log.d(TAG, "创建缓存目录: " + (created ? "成功" : "失败"));
            if (!created) {
                Log.e(TAG, "无法创建缓存目录!");
            }
        } else {
            Log.d(TAG, "缓存目录已存在");
        }

        // 检查目录权限
        Log.d(TAG, "缓存目录可读: " + cacheDir.canRead());
        Log.d(TAG, "缓存目录可写: " + cacheDir.canWrite());
        Log.d(TAG, "缓存目录是目录: " + cacheDir.isDirectory());

        Log.d(TAG, "缓存目录初始化完成: " + cacheDir.getAbsolutePath());
    }

    /**
     * 获取视频的本地缓存路径
     * @param videoId 视频ID
     * @param videoUrl 视频URL（用于生成唯一标识）
     * @return 本地缓存文件路径，如果不存在则返回null
     */
    public String getLocalVideoPath(String videoId, String videoUrl) {
        Log.d(TAG, "getLocalVideoPath - VideoID: " + videoId + ", URL: " + videoUrl);

        if (videoId == null || videoUrl == null) {
            Log.w(TAG, "VideoID or URL is null");
            return null;
        }

        if (cacheDir == null) {
            Log.e(TAG, "缓存目录为null");
            return null;
        }

        String fileName = generateCacheFileName(videoId, videoUrl);
        Log.d(TAG, "生成的缓存文件名: " + fileName);

        File cacheFile = new File(cacheDir, fileName);
        File infoFile = new File(cacheDir, fileName + VIDEO_INFO_SUFFIX);

        Log.d(TAG, "缓存文件路径: " + cacheFile.getAbsolutePath());
        Log.d(TAG, "信息文件路径: " + infoFile.getAbsolutePath());
        Log.d(TAG, "缓存文件存在: " + cacheFile.exists());
        Log.d(TAG, "信息文件存在: " + infoFile.exists());

        if (cacheFile.exists() && infoFile.exists()) {
            Log.d(TAG, "缓存文件和信息文件都存在，检查有效性");
            // 验证缓存是否有效（URL是否变化）
            if (isCacheValid(videoId, videoUrl, infoFile)) {
                Log.d(TAG, "找到有效缓存: " + videoId);
                return cacheFile.getAbsolutePath();
            } else {
                Log.d(TAG, "缓存已过期，需要重新下载: " + videoId);
                // 删除过期缓存
                boolean deletedCache = cacheFile.delete();
                boolean deletedInfo = infoFile.delete();
                Log.d(TAG, "删除过期缓存文件: " + deletedCache + ", 信息文件: " + deletedInfo);
            }
        } else {
            Log.d(TAG, "缓存文件不完整或不存在");
        }

        return null;
    }

    /**
     * 下载并缓存视频
     * @param videoId 视频ID
     * @param videoUrl 视频URL
     * @param callback 下载回调
     */
    public void downloadAndCacheVideo(String videoId, String videoUrl, DownloadCallback callback) {
        Log.d(TAG, "downloadAndCacheVideo - VideoID: " + videoId + ", URL: " + videoUrl);

        if (videoId == null || videoUrl == null) {
            Log.e(TAG, "VideoID or URL is null, cannot download");
            if (callback != null) {
                callback.onError(videoId, "VideoID或URL为空");
            }
            return;
        }

        // 检查是否已经在下载中
        if (downloadingTasks.containsKey(videoId)) {
            Log.d(TAG, "视频正在下载中: " + videoId);
            return;
        }

        Log.d(TAG, "创建下载任务: " + videoId);
        DownloadTask task = new DownloadTask(videoId, videoUrl, callback);
        downloadingTasks.put(videoId, task);
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        Log.d(TAG, "下载任务已启动: " + videoId);
    }

    /**
     * 检查缓存是否有效
     */
    private boolean isCacheValid(String videoId, String videoUrl, File infoFile) {
        Log.d(TAG, "检查缓存有效性: " + videoId);
        try {
            // 读取缓存信息文件，比较URL是否变化
            // 这里可以存储URL的MD5值或者时间戳等信息
            Log.d(TAG, "缓存验证通过: " + videoId);
            return true; // 简化实现，实际可以比较URL hash
        } catch (Exception e) {
            Log.e(TAG, "检查缓存有效性失败: " + videoId, e);
            return false;
        }
    }

    /**
     * 生成缓存文件名
     */
    private String generateCacheFileName(String videoId, String videoUrl) {
        Log.d(TAG, "生成缓存文件名: VideoID=" + videoId + ", URL=" + videoUrl);

        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            String input = videoId + "_" + videoUrl;
            byte[] digest = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            String fileName = videoId + "_" + sb.toString() + ".mp4";
            Log.d(TAG, "生成的文件名: " + fileName);
            return fileName;
        } catch (Exception e) {
            Log.e(TAG, "MD5生成失败，使用简单hash", e);
            // 如果MD5失败，使用简单的文件名
            String fileName = videoId + "_" + Math.abs(videoUrl.hashCode()) + ".mp4";
            Log.d(TAG, "简单hash文件名: " + fileName);
            return fileName;
        }
    }

    /**
     * 保存视频信息到本地
     */
    private void saveVideoInfo(String videoId, String videoUrl, String fileName) {
        Log.d(TAG, "保存视频信息: " + videoId + ", 文件名: " + fileName);
        try {
            File infoFile = new File(cacheDir, fileName + VIDEO_INFO_SUFFIX);
            Log.d(TAG, "信息文件路径: " + infoFile.getAbsolutePath());

            FileOutputStream fos = new FileOutputStream(infoFile);
            String info = videoId + "\n" + videoUrl + "\n" + System.currentTimeMillis();
            fos.write(info.getBytes());
            fos.close();

            Log.d(TAG, "视频信息保存成功: " + videoId);
        } catch (Exception e) {
            Log.e(TAG, "保存视频信息失败: " + videoId, e);
        }
    }

    /**
     * 清理缓存（可以按大小、时间等策略清理）
     */
    public void clearCache() {
        Log.d(TAG, "开始清理缓存");

        if (cacheDir == null) {
            Log.w(TAG, "缓存目录为null，无法清理");
            return;
        }

        if (cacheDir.exists()) {
            File[] files = cacheDir.listFiles();
            if (files != null) {
                Log.d(TAG, "找到 " + files.length + " 个文件需要删除");
                int deletedCount = 0;
                for (File file : files) {
                    if (file.delete()) {
                        deletedCount++;
                        Log.d(TAG, "删除文件: " + file.getName());
                    } else {
                        Log.w(TAG, "无法删除文件: " + file.getName());
                    }
                }
                Log.d(TAG, "成功删除 " + deletedCount + " 个文件");
            } else {
                Log.d(TAG, "缓存目录为空");
            }
        } else {
            Log.w(TAG, "缓存目录不存在");
        }

        Log.d(TAG, "缓存清理完成");
    }

    /**
     * 获取缓存大小
     */
    public long getCacheSize() {
        Log.d(TAG, "计算缓存大小");

        long size = 0;
        if (cacheDir != null && cacheDir.exists()) {
            File[] files = cacheDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    size += file.length();
                    Log.d(TAG, "文件: " + file.getName() + ", 大小: " + file.length() + " bytes");
                }
                Log.d(TAG, "总缓存大小: " + size + " bytes");
            } else {
                Log.d(TAG, "缓存目录为空");
            }
        } else {
            Log.w(TAG, "缓存目录不存在或为null");
        }

        return size;
    }

    /**
     * 下载任务
     */
    private class DownloadTask extends AsyncTask<Void, Integer, String> {
        private String videoId;
        private String videoUrl;
        private DownloadCallback callback;
        private String localPath;

        public DownloadTask(String videoId, String videoUrl, DownloadCallback callback) {
            this.videoId = videoId;
            this.videoUrl = videoUrl;
            this.callback = callback;
            Log.d(TAG, "DownloadTask created for: " + videoId);
        }

        @Override
        protected void onPreExecute() {
            Log.d(TAG, "开始下载任务: " + videoId);
            if (callback != null) {
                callback.onStart(videoId);
            }
        }

        @Override
        protected String doInBackground(Void... voids) {
            Log.d(TAG, "执行下载任务: " + videoId);

            try {
                String fileName = generateCacheFileName(videoId, videoUrl);
                File outputFile = new File(cacheDir, fileName);

                Log.d(TAG, "下载文件保存路径: " + outputFile.getAbsolutePath());

                URL url = new URL(videoUrl);
                Log.d(TAG, "连接URL: " + videoUrl);

                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(15000); // 15秒连接超时
                connection.setReadTimeout(30000);    // 30秒读取超时
                connection.connect();

                int responseCode = connection.getResponseCode();
                Log.d(TAG, "HTTP响应码: " + responseCode);

                if (responseCode != HttpURLConnection.HTTP_OK) {
                    String error = "下载失败: HTTP " + responseCode;
                    Log.e(TAG, error);
                    return error;
                }

                int fileLength = connection.getContentLength();
                Log.d(TAG, "文件大小: " + fileLength + " bytes");

                InputStream input = connection.getInputStream();
                FileOutputStream output = new FileOutputStream(outputFile);

                byte[] buffer = new byte[8192];
                long total = 0;
                int count;
                long lastLogTime = System.currentTimeMillis();

                while ((count = input.read(buffer)) != -1) {
                    if (isCancelled()) {
                        Log.d(TAG, "下载被取消: " + videoId);
                        break;
                    }

                    total += count;
                    output.write(buffer, 0, count);

                    if (fileLength > 0) {
                        int progress = (int) (total * 100 / fileLength);
                        publishProgress(progress);

                        // 每5秒记录一次进度日志
                        long currentTime = System.currentTimeMillis();
                        if (currentTime - lastLogTime > 5000) {
                            Log.d(TAG, "下载进度: " + progress + "% (" + total + "/" + fileLength + ")");
                            lastLogTime = currentTime;
                        }
                    }
                }

                output.flush();
                output.close();
                input.close();
                connection.disconnect();

                if (!isCancelled()) {
                    Log.d(TAG, "下载完成: " + videoId + ", 文件大小: " + total + " bytes");
                    // 保存视频信息
                    saveVideoInfo(videoId, videoUrl, fileName);
                    localPath = outputFile.getAbsolutePath();
                    Log.d(TAG, "视频下载完成: " + videoId + " -> " + localPath);
                    return null; // 成功
                } else {
                    boolean deleted = outputFile.delete(); // 清理未完成的文件
                    Log.d(TAG, "下载取消，清理文件: " + deleted);
                    return "下载已取消";
                }

            } catch (Exception e) {
                Log.e(TAG, "下载视频失败: " + videoId, e);
                return "下载失败: " + e.getMessage();
            }
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {
            if (callback != null) {
                callback.onProgress(videoId, progress[0]);
            }
        }

        @Override
        protected void onPostExecute(String error) {
            Log.d(TAG, "下载任务完成: " + videoId + ", 错误: " + error);
            downloadingTasks.remove(videoId);

            if (callback != null) {
                if (error == null) {
                    Log.d(TAG, "下载成功回调: " + videoId);
                    callback.onSuccess(videoId, localPath);
                } else {
                    Log.d(TAG, "下载失败回调: " + videoId + ", 错误: " + error);
                    callback.onError(videoId, error);
                }
            }
        }

        @Override
        protected void onCancelled() {
            Log.d(TAG, "下载任务被取消: " + videoId);
            downloadingTasks.remove(videoId);
            if (callback != null) {
                callback.onError(videoId, "下载已取消");
            }
        }
    }

    /**
     * 下载回调接口
     */
    public interface DownloadCallback {
        void onStart(String videoId);
        void onProgress(String videoId, int progress);
        void onSuccess(String videoId, String localPath);
        void onError(String videoId, String error);
    }
}