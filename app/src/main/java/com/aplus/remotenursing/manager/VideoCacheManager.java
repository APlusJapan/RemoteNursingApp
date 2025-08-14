package com.aplus.remotenursing.manager;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 视频缓存管理器（.part 临时文件 + 原子重命名；支持断点续传；命中缓存直接返回）
 */
public class VideoCacheManager {
    private static final String TAG = "VideoCacheManager";
    private static final String CACHE_DIR_NAME = "video_cache";
    private static final String VIDEO_INFO_SUFFIX = ".info";
    private static final int CONNECT_TIMEOUT = 15000; // ms
    private static final int READ_TIMEOUT = 30000;    // ms
    private static final int BUFFER_SIZE = 8192;

    private final Context context;
    private File cacheDir;
    // 用“文件名”作为 key，避免相同 videoId 不同 URL 的冲突
    private final ConcurrentHashMap<String, DownloadTask> downloadingTasks = new ConcurrentHashMap<>();
    private static volatile VideoCacheManager instance;

    private VideoCacheManager(Context context) {
        this.context = context.getApplicationContext();
        initCacheDir();
    }

    public static synchronized VideoCacheManager getInstance(Context context) {
        if (instance == null) {
            instance = new VideoCacheManager(context);
        }
        return instance;
    }

    /** 初始化缓存目录（/Android/data/<pkg>/files/Movies/video_cache） */
    private void initCacheDir() {
        File externalDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES);
        if (externalDir == null) {
            // 极端情况下返回 null，退回到内部 files 目录
            externalDir = context.getFilesDir();
            Log.w(TAG, "getExternalFilesDir 返回 null，退回内部存储");
        }

        cacheDir = new File(externalDir, CACHE_DIR_NAME);
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            Log.e(TAG, "无法创建缓存目录: " + cacheDir.getAbsolutePath());
        }
        Log.d(TAG, "缓存目录: " + cacheDir.getAbsolutePath()
                + " 可读=" + cacheDir.canRead()
                + " 可写=" + cacheDir.canWrite());
    }

    /**
     * 获取视频的本地缓存路径（若有效返回绝对路径，否则返回 null）
     */
    public String getLocalVideoPath(String videoId, String videoUrl) {
        if (videoId == null || videoUrl == null || cacheDir == null) return null;

        String fileName = generateCacheFileName(videoId, videoUrl);
        File cacheFile = new File(cacheDir, fileName);
        File infoFile  = new File(cacheDir, fileName + VIDEO_INFO_SUFFIX);

        if (cacheFile.exists() && infoFile.exists()) {
            if (isCacheValid(videoId, videoUrl, cacheFile, infoFile)) {
                return cacheFile.getAbsolutePath();
            } else {
                // 失效则清理
                safeDelete(cacheFile);
                safeDelete(infoFile);
            }
        }
        return null;
    }

    /**
     * 下载并缓存视频（命中缓存直接回调成功；支持断点续传；写到 .part 完成后 rename）
     */
    public void downloadAndCacheVideo(String videoId, String videoUrl, DownloadCallback callback) {
        if (videoId == null || videoUrl == null) {
            if (callback != null) callback.onError(videoId, "VideoID或URL为空");
            return;
        }

        // 命中缓存即返回
        String cachedPath = getLocalVideoPath(videoId, videoUrl);
        if (cachedPath != null) {
            Log.d(TAG, "已命中缓存，跳过下载: " + videoId);
            if (callback != null) callback.onSuccess(videoId, cachedPath);
            return;
        }

        String taskKey = generateCacheFileName(videoId, videoUrl);
        if (downloadingTasks.containsKey(taskKey)) {
            Log.d(TAG, "该文件已在下载中: " + taskKey);
            return;
        }

        DownloadTask task = new DownloadTask(videoId, videoUrl, callback, taskKey);
        downloadingTasks.put(taskKey, task);
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /** 校验缓存有效性：id/url 一致、文件存在且大小>0（可选比对 info 里记录的大小） */
    private boolean isCacheValid(String videoId, String videoUrl, File cacheFile, File infoFile) {
        try (BufferedReader br = new BufferedReader(new FileReader(infoFile))) {
            String lineId  = br.readLine(); // 1: videoId
            String lineUrl = br.readLine(); // 2: url
            String lineTs  = br.readLine(); // 3: timestamp (optional)
            String lineSz  = br.readLine(); // 4: fileSize  (optional)

            boolean idOk  = videoId.equals(lineId);
            boolean urlOk = videoUrl.equals(lineUrl);
            boolean fileOk = cacheFile.exists() && cacheFile.length() > 0;

            if (!idOk || !urlOk || !fileOk) return false;

            if (lineSz != null) {
                try {
                    long recorded = Long.parseLong(lineSz.trim());
                    if (recorded > 0 && recorded != cacheFile.length()) return false;
                } catch (Exception ignore) {}
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "检查缓存有效性失败: " + videoId, e);
            return false;
        }
    }

    /** 生成缓存文件名：<videoId>_<md5(videoId_url)>.mp4 */
    private String generateCacheFileName(String videoId, String videoUrl) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest((videoId + "_" + videoUrl).getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return videoId + "_" + sb + ".mp4";
        } catch (Exception e) {
            Log.e(TAG, "MD5生成失败，使用简单hash", e);
            return videoId + "_" + Math.abs(videoUrl.hashCode()) + ".mp4";
        }
    }

    /** 保存视频信息到 .info：videoId / url / timestamp / fileSize */
    private void saveVideoInfo(String videoId, String videoUrl, String fileName, long fileSize) {
        File infoFile = new File(cacheDir, fileName + VIDEO_INFO_SUFFIX);
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(infoFile);
            String info = videoId + "\n" + videoUrl + "\n" + System.currentTimeMillis() + "\n" + fileSize;
            fos.write(info.getBytes());
            fos.flush();
        } catch (Exception e) {
            Log.e(TAG, "保存视频信息失败: " + videoId, e);
        } finally {
            closeQuiet(fos);
        }
    }

    /** 清理缓存目录下的所有文件 */
    public void clearCache() {
        if (cacheDir == null || !cacheDir.exists()) return;
        File[] files = cacheDir.listFiles();
        if (files == null) return;
        for (File f : files) safeDelete(f);
    }

    /** 计算缓存大小 */
    public long getCacheSize() {
        long size = 0;
        if (cacheDir != null && cacheDir.exists()) {
            File[] files = cacheDir.listFiles();
            if (files != null) {
                for (File f : files) size += f.length();
            }
        }
        return size;
    }

    /** 下载任务 */
    private class DownloadTask extends AsyncTask<Void, Integer, String> {
        private final String videoId;
        private final String videoUrl;
        private final DownloadCallback callback;
        private final String taskKey; // 文件名
        private String localPath;

        DownloadTask(String videoId, String videoUrl, DownloadCallback callback, String taskKey) {
            this.videoId = videoId;
            this.videoUrl = videoUrl;
            this.callback = callback;
            this.taskKey = taskKey;
        }

        @Override
        protected void onPreExecute() {
            if (callback != null) callback.onStart(videoId);
        }

        @Override
        protected String doInBackground(Void... voids) {
            HttpURLConnection connection = null;
            InputStream in = null;
            OutputStream out = null;

            try {
                // 再次短路：若此时已有缓存（可能别的任务刚下完）
                String cached = getLocalVideoPath(videoId, videoUrl);
                if (cached != null) {
                    localPath = cached;
                    return null;
                }

                String fileName = taskKey; // 已经是生成好的文件名
                File finalFile = new File(cacheDir, fileName);
                File partFile  = new File(cacheDir, fileName + ".part");

                long existing = partFile.exists() ? partFile.length() : 0L;

                URL url = new URL(videoUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(CONNECT_TIMEOUT);
                connection.setReadTimeout(READ_TIMEOUT);
                connection.setRequestMethod("GET");

                if (existing > 0) {
                    // 断点续传
                    connection.setRequestProperty("Range", "bytes=" + existing + "-");
                }
                connection.connect();

                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK
                        && responseCode != HttpURLConnection.HTTP_PARTIAL) {
                    return "下载失败: HTTP " + responseCode;
                }

                // 若服务器不支持续传（给了 200），但我们有 .part，则从头下：清空旧 part
                boolean append = (responseCode == HttpURLConnection.HTTP_PARTIAL) && (existing > 0);
                if (!append && partFile.exists()) {
                    // 200 或者没有 Range，则重下
                    safeDelete(partFile);
                    existing = 0L;
                }

                // 计算总大小（兼容老版本 API，避免 getContentLengthLong）
                long totalSize = resolveContentLength(connection, responseCode, existing);

                in = new BufferedInputStream(connection.getInputStream(), BUFFER_SIZE);
                out = new BufferedOutputStream(new FileOutputStream(partFile, append), BUFFER_SIZE);

                byte[] buffer = new byte[BUFFER_SIZE];
                long downloaded = existing;
                long lastPublish = System.currentTimeMillis();

                while (true) {
                    if (isCancelled()) return "下载已取消";
                    int read = in.read(buffer);
                    if (read == -1) break;

                    out.write(buffer, 0, read);
                    downloaded += read;

                    if (totalSize > 0) {
                        int progress = (int) (downloaded * 100 / totalSize);
                        long now = System.currentTimeMillis();
                        if (now - lastPublish >= 200) { // 200ms 节流
                            publishProgress(progress);
                            lastPublish = now;
                        }
                    }
                }

                out.flush();
                closeQuiet(out);
                closeQuiet(in);
                if (connection != null) connection.disconnect();

                // 原子替换：.part -> .mp4
                if (finalFile.exists() && !finalFile.delete()) {
                    return "无法替换旧的缓存文件";
                }
                if (!partFile.renameTo(finalFile)) {
                    return "重命名缓存文件失败";
                }

                long finalSize = finalFile.length();
                saveVideoInfo(videoId, videoUrl, fileName, finalSize);
                localPath = finalFile.getAbsolutePath();
                // 再补发 100%
                publishProgress(100);
                return null;

            } catch (Exception e) {
                Log.e(TAG, "下载视频失败: " + videoId, e);
                return "下载失败: " + e.getMessage();
            } finally {
                closeQuiet(out);
                closeQuiet(in);
                if (connection != null) connection.disconnect();
            }
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            if (callback != null && values != null && values.length > 0) {
                callback.onProgress(videoId, values[0]);
            }
        }

        @Override
        protected void onPostExecute(String error) {
            downloadingTasks.remove(taskKey);
            if (callback != null) {
                if (error == null) {
                    callback.onSuccess(videoId, localPath);
                } else {
                    callback.onError(videoId, error);
                }
            }
        }

        @Override
        protected void onCancelled() {
            downloadingTasks.remove(taskKey);
            if (callback != null) callback.onError(videoId, "下载已取消");
        }
    }

    // ---------- 辅助方法 ----------

    /** 兼容解析总长度（避免 API 24 的 getContentLengthLong） */
    private static long resolveContentLength(HttpURLConnection conn, int responseCode, long existing) {
        try {
            if (responseCode == HttpURLConnection.HTTP_PARTIAL) {
                // Content-Range: bytes <start>-<end>/<total>
                String range = conn.getHeaderField("Content-Range");
                if (range != null) {
                    int slash = range.lastIndexOf('/');
                    if (slash >= 0 && slash + 1 < range.length()) {
                        String totalStr = range.substring(slash + 1).trim();
                        return Long.parseLong(totalStr);
                    }
                }
            } else {
                // 200 场景：优先 Content-Length 头
                String cl = conn.getHeaderField("Content-Length");
                if (cl != null) {
                    return Long.parseLong(cl.trim());
                }
                // 退化为 getContentLength（int）
                int len = conn.getContentLength();
                if (len > 0) return (long) len;
            }
        } catch (Exception ignore) {}
        // 未知总长度（如分块传输），返回 -1 表示无法计算进度
        return -1L;
    }

    private static void closeQuiet(InputStream is) {
        try { if (is != null) is.close(); } catch (Exception ignore) {}
    }

    private static void closeQuiet(OutputStream os) {
        try { if (os != null) os.close(); } catch (Exception ignore) {}
    }

    private static void safeDelete(File f) {
        try { if (f != null && f.exists()) f.delete(); } catch (Exception ignore) {}
    }

    /** 下载回调接口 */
    public interface DownloadCallback {
        void onStart(String videoId);
        void onProgress(String videoId, int progress);
        void onSuccess(String videoId, String localPath);
        void onError(String videoId, String error);
    }
}
