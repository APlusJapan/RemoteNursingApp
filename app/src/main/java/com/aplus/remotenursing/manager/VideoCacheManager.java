// 文件：com/aplus/remotenursing/manager/VideoCacheManager.java
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

import javax.net.ssl.SSLException;

/**
 * 视频缓存管理器
 * - .part 临时文件
 * - .staging 占用时旁路暂存
 * - 可断点续传
 * - 命中缓存直接返回
 */
public class VideoCacheManager {
    private static final String TAG = "VideoCacheManager";
    private static final String VCM_VERSION = "VCM-20250825-r4";

    private static final String CACHE_DIR_NAME = "video_cache";
    private static final String SUFFIX_INFO    = ".info";
    private static final String SUFFIX_PART    = ".part";
    private static final String SUFFIX_STAGING = ".staging";

    private static final int CONNECT_TIMEOUT = 15000; // ms
    private static final int READ_TIMEOUT    = 30000; // ms
    private static final int BUFFER_SIZE     = 8192;

    private final Context context;
    private File cacheDir;
    private final ConcurrentHashMap<String, DownloadTask> downloadingTasks = new ConcurrentHashMap<>();
    private static volatile VideoCacheManager instance;

    private VideoCacheManager(Context context) {
        this.context = context.getApplicationContext();
        Log.i(TAG, "VideoCacheManager version = " + VCM_VERSION);
        initCacheDir();
    }

    public static synchronized VideoCacheManager getInstance(Context context) {
        if (instance == null) instance = new VideoCacheManager(context);
        return instance;
    }

    /** 初始化缓存目录：/Android/data/<pkg>/files/Movies/video_cache */
    private void initCacheDir() {
        File externalDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES);
        if (externalDir == null) {
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

    /** 归一化 URL：去 query/fragment */
    private static String canonicalizeUrl(String url) {
        if (url == null) return null;
        int i = url.indexOf('#'); if (i >= 0) url = url.substring(0, i);
        int j = url.indexOf('?'); if (j >= 0) url = url.substring(0, j);
        return url;
    }

    /** 获取本地缓存路径；内部会尝试把 .staging 提升为最终文件 */
    public String getLocalVideoPath(String videoId, String videoUrl) {
        if (videoId == null || videoUrl == null || cacheDir == null) return null;

        String fileName = generateCacheFileName(videoId, videoUrl);
        File finalFile  = new File(cacheDir, fileName);
        File infoFile   = new File(cacheDir, fileName + SUFFIX_INFO);
        File staging    = new File(cacheDir, fileName + SUFFIX_STAGING);

        // 若存在 staging，尝试提升为最终文件
        tryPromoteStagingIfPossible(videoId, videoUrl, finalFile, infoFile, staging);

        if (finalFile.exists() && infoFile.exists()) {
            if (isCacheValid(videoId, videoUrl, finalFile, infoFile)) {
                return finalFile.getAbsolutePath();
            } else {
                safeDelete(finalFile);
                safeDelete(infoFile);
            }
        }

        // 兼容老命名
        String legacy = generateCacheFileNameLegacy(videoId, videoUrl);
        if (!legacy.equals(fileName)) {
            File legacyFile = new File(cacheDir, legacy);
            File legacyInfo = new File(cacheDir, legacy + SUFFIX_INFO);
            if (legacyFile.exists() && legacyInfo.exists()) {
                if (isCacheValidLegacy(videoId, videoUrl, legacyFile, legacyInfo)) {
                    return legacyFile.getAbsolutePath();
                } else {
                    safeDelete(legacyFile);
                    safeDelete(legacyInfo);
                }
            }
        }
        return null;
    }

    /** 主动尝试把 .staging 立即提升为最终 .mp4（适用于你手动切到下一条之后） */
    public boolean tryPromoteNow(String videoId, String videoUrl) {
        String fileName = generateCacheFileName(videoId, videoUrl);
        File finalFile  = new File(cacheDir, fileName);
        File infoFile   = new File(cacheDir, fileName + SUFFIX_INFO);
        File staging    = new File(cacheDir, fileName + SUFFIX_STAGING);
        return tryPromoteStagingIfPossible(videoId, videoUrl, finalFile, infoFile, staging);
    }

    /** 下载并缓存（命中缓存直接回调成功） */
    public void downloadAndCacheVideo(String videoId, String videoUrl, DownloadCallback callback) {
        if (videoId == null || videoUrl == null) {
            if (callback != null) callback.onError(videoId, "VideoID或URL为空");
            return;
        }

        // 命中缓存
        String cached = getLocalVideoPath(videoId, videoUrl);
        if (cached != null) {
            if (callback != null) callback.onSuccess(videoId, cached);
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

    /** 强制重新下载（删除现有缓存与片段） */
    public void downloadAndCacheVideoForce(String videoId, String videoUrl, DownloadCallback callback) {
        try { deleteLocalVideo(videoId, videoUrl); } catch (Throwable ignore) {}
        downloadAndCacheVideo(videoId, videoUrl, callback);
    }

    /** 删除本地缓存（含 .mp4/.info/.part/.staging） */
    public boolean deleteLocalVideo(String videoId, String videoUrl) {
        boolean deleted = false;
        String name = generateCacheFileName(videoId, videoUrl);
        File f = new File(cacheDir, name);
        File info = new File(cacheDir, name + SUFFIX_INFO);
        File part = new File(cacheDir, name + SUFFIX_PART);
        File staging = new File(cacheDir, name + SUFFIX_STAGING);
        if (f.exists())       deleted |= f.delete();
        if (info.exists())    deleted |= info.delete();
        if (part.exists())    deleted |= part.delete();
        if (staging.exists()) deleted |= staging.delete();

        // 兼容老命名
        String legacy = generateCacheFileNameLegacy(videoId, videoUrl);
        if (!legacy.equals(name)) {
            File lf = new File(cacheDir, legacy);
            File linfo = new File(cacheDir, legacy + SUFFIX_INFO);
            File lpart = new File(cacheDir, legacy + SUFFIX_PART);
            File lstaging = new File(cacheDir, legacy + SUFFIX_STAGING);
            if (lf.exists())       deleted |= lf.delete();
            if (linfo.exists())    deleted |= linfo.delete();
            if (lpart.exists())    deleted |= lpart.delete();
            if (lstaging.exists()) deleted |= lstaging.delete();
        }
        return deleted;
    }

    /** 校验缓存有效性 */
    private boolean isCacheValid(String videoId, String videoUrl, File cacheFile, File infoFile) {
        try (BufferedReader br = new BufferedReader(new FileReader(infoFile))) {
            String lineId  = br.readLine();
            String lineUrl = br.readLine();
            String lineTs  = br.readLine();
            String lineSz  = br.readLine();

            boolean idOk  = videoId.equals(lineId);
            boolean urlOk = canonicalizeUrl(videoUrl).equals(canonicalizeUrl(lineUrl));
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

    /** 兼容老 info 校验 */
    private boolean isCacheValidLegacy(String videoId, String videoUrl, File cacheFile, File infoFile) {
        try (BufferedReader br = new BufferedReader(new FileReader(infoFile))) {
            String lineId  = br.readLine();
            String lineUrl = br.readLine();
            String lineTs  = br.readLine();
            String lineSz  = br.readLine();

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
            return false;
        }
    }

    /** 新规则文件名：<videoId>_<md5(videoId + "_" + canonicalUrl)>.mp4 */
    private String generateCacheFileName(String videoId, String videoUrl) {
        String can = canonicalizeUrl(videoUrl);
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest((videoId + "_" + can).getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return videoId + "_" + sb + ".mp4";
        } catch (Exception e) {
            Log.e(TAG, "MD5生成失败，使用简化hash", e);
            return videoId + "_" + Math.abs(can.hashCode()) + ".mp4";
        }
    }

    /** 旧规则（兼容读取） */
    private String generateCacheFileNameLegacy(String videoId, String videoUrl) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest((videoId + "_" + videoUrl).getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return videoId + "_" + sb + ".mp4";
        } catch (Exception e) {
            return videoId + "_" + Math.abs(videoUrl.hashCode()) + ".mp4";
        }
    }

    /** 保存 .info（videoId / canonicalUrl / timestamp / fileSize） */
    private void saveVideoInfo(String videoId, String videoUrl, String baseName, long fileSize) {
        File infoFile = new File(cacheDir, baseName + SUFFIX_INFO);
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(infoFile);
            String info = videoId + "\n" + canonicalizeUrl(videoUrl) + "\n" + System.currentTimeMillis() + "\n" + fileSize;
            fos.write(info.getBytes());
            fos.flush();
        } catch (Exception e) {
            Log.e(TAG, "保存视频信息失败: " + videoId, e);
        } finally {
            closeQuiet(fos);
        }
    }

    /** 清空缓存目录 */
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
            if (files != null) for (File f : files) size += f.length();
        }
        return size;
    }

    /** 下载任务 */
    private class DownloadTask extends AsyncTask<Void, Integer, String> {
        private final String videoId;
        private final String videoUrl;
        private final DownloadCallback callback;
        private final String taskKey; // 目标基础名（含 .mp4）
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

            final int maxRetries = 3;          // 自动重试次数
            int attempt = 0;
            long backoffMs = 800;              // 指数回退起点
            Exception lastErr = null;

            try {
                // 若别的线程刚下完，直接命中
                String cached = getLocalVideoPath(videoId, videoUrl);
                if (cached != null) {
                    localPath = cached;
                    return null;
                }

                final String baseName = taskKey; // 新规则生成的最终文件名（带 .mp4）
                final File finalFile = new File(cacheDir, baseName);
                File partFile = new File(cacheDir, baseName + SUFFIX_PART);

                while (attempt <= maxRetries && !isCancelled()) {
                    attempt++;

                    try {
                        long existing = partFile.exists() ? partFile.length() : 0L;

                        URL url = new URL(videoUrl);
                        connection = openWithRetry(url, existing);

                        int code = connection.getResponseCode();
                        if (code != HttpURLConnection.HTTP_OK
                                && code != HttpURLConnection.HTTP_PARTIAL) {
                            return "下载失败: HTTP " + code;
                        }

                        boolean append = (code == HttpURLConnection.HTTP_PARTIAL) && (existing > 0);
                        if (!append && partFile.exists()) {
                            safeDelete(partFile);
                            existing = 0L;
                        }

                        long totalSize = resolveContentLength(connection, code, existing);

                        in = new BufferedInputStream(connection.getInputStream(), BUFFER_SIZE);
                        out = new BufferedOutputStream(new FileOutputStream(partFile, append), BUFFER_SIZE);

                        byte[] buffer = new byte[BUFFER_SIZE];
                        long downloaded = existing;
                        long lastPublish = System.currentTimeMillis();

                        while (!isCancelled()) {
                            int read = in.read(buffer);
                            if (read == -1) break;

                            out.write(buffer, 0, read);
                            downloaded += read;

                            if (totalSize > 0) {
                                int progress = (int) (downloaded * 100 / totalSize);
                                long now = System.currentTimeMillis();
                                if (now - lastPublish >= 200) {
                                    publishProgress(progress);
                                    lastPublish = now;
                                }
                            }
                        }

                        out.flush();

                        // —— 收尾：把 .part 提升到最终或 .staging ——
                        PromoteResult pr = promotePartToFinalOrStaging(partFile, finalFile);
                        if (pr.status == PromoteStatus.FAILED) {
                            return "重命名缓存文件失败";
                        }

                        if (pr.status == PromoteStatus.FINAL_READY) {
                            long finalSize = finalFile.length();
                            saveVideoInfo(videoId, videoUrl, baseName, finalSize);
                            localPath = finalFile.getAbsolutePath();
                            publishProgress(100);
                            return null;
                        }

                        // STAGED：被占用（例如正在播放），不当成失败；沿用旧文件播放
                        if (finalFile.exists() && finalFile.length() > 0) {
                            localPath = finalFile.getAbsolutePath();
                            Log.w(TAG, "目标被占用，已暂存为 staging，稍后自动提升: " + baseName + SUFFIX_STAGING);
                            publishProgress(100);
                            return null;
                        } else {
                            return "目标占用且无旧文件可用";
                        }

                    } catch (Exception e) {
                        lastErr = e;

                        // 网络类异常：保留 .part，准备重试（断点续传）
                        closeQuiet(out);
                        closeQuiet(in);
                        if (connection != null) connection.disconnect();

                        if (attempt <= maxRetries &&
                                (e instanceof java.net.SocketTimeoutException
                                        || e instanceof java.net.SocketException
                                        || e instanceof java.io.EOFException
                                        || e instanceof SSLException)) {
                            try { Thread.sleep(backoffMs); } catch (InterruptedException ignored) {}
                            backoffMs = Math.min(backoffMs * 2, 5000);
                            Log.w(TAG, "下载中断，第 " + attempt + " 次重试即将开始: " + e.getMessage());
                            continue;
                        }

                        // 非可重试或已用尽次数
                        Log.e(TAG, "下载视频失败: " + videoId, e);
                        return "下载失败: " + e.getMessage();

                    } finally {
                        closeQuiet(out);
                        closeQuiet(in);
                        if (connection != null) connection.disconnect();
                    }
                }

                return "下载失败: " + (lastErr != null ? lastErr.getMessage() : "未知错误");

            } finally {
                // no-op
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

    // ===== 提升/移动相关 =====

    private enum PromoteStatus { FINAL_READY, STAGED, FAILED }

    private static class PromoteResult {
        final PromoteStatus status;
        final File stagingFile;
        PromoteResult(PromoteStatus s, File f) { status = s; stagingFile = f; }
        static PromoteResult okFinal()  { return new PromoteResult(PromoteStatus.FINAL_READY, null); }
        static PromoteResult okStaged(File f) { return new PromoteResult(PromoteStatus.STAGED, f); }
        static PromoteResult fail()     { return new PromoteResult(PromoteStatus.FAILED, null); }
    }

    /** 将 .part 提升为最终文件；若目标被占用，改为 .staging 暂存 */
    private PromoteResult promotePartToFinalOrStaging(File partFile, File finalFile) {
        if (partFile == null || !partFile.exists()) return PromoteResult.fail();

        // 若目标文件不存在或可以删除，则直接“移动/复制兜底”
        if (!finalFile.exists() || finalFile.delete()) {
            boolean moved = moveWithRetry(partFile, finalFile);
            return moved ? PromoteResult.okFinal() : PromoteResult.fail();
        }

        // 目标存在且删除失败 -> 认为被占用：改名为 .staging
        File staging = new File(finalFile.getParentFile(), finalFile.getName() + SUFFIX_STAGING);
        if (staging.exists() && !staging.delete()) {
            staging = new File(finalFile.getParentFile(),
                    finalFile.getName() + "." + System.currentTimeMillis() + SUFFIX_STAGING);
        }
        boolean staged = moveWithRetry(partFile, staging);
        return staged ? PromoteResult.okStaged(staging) : PromoteResult.fail();
    }

    /** 如果存在 .staging，尝试提升为最终文件（成功则补写 .info） */
    private boolean tryPromoteStagingIfPossible(String videoId, String videoUrl,
                                                File finalFile, File infoFile, File staging) {
        if (!staging.exists()) return false;

        // 若目标不存在或可删除 -> 尝试移动 staging → final
        if (!finalFile.exists() || finalFile.delete()) {
            if (moveWithRetry(staging, finalFile)) {
                long finalSize = finalFile.length();
                saveVideoInfo(videoId, videoUrl, finalFile.getName(), finalSize);
                Log.i(TAG, "已将 staging 提升为最终文件: " + finalFile.getName());
                return true;
            } else {
                Log.w(TAG, "staging 提升失败（稍后重试）: " + finalFile.getName());
                return false;
            }
        } else {
            // 目标仍被占用，保持 staging，等下次再试
            Log.d(TAG, "目标仍被占用，保持 staging: " + finalFile.getName());
            return false;
        }
    }

    /** 可靠移动：先多次 renameTo，失败再复制兜底（并删除源） */
    private static boolean moveWithRetry(File src, File dst) {
        if (src == null || dst == null || !src.exists()) return false;

        File parent = dst.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();

        // 多次 renameTo（最多 6 次，每次间隔 100ms）
        for (int i = 0; i < 6; i++) {
            if (src.renameTo(dst)) return true;
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            try { System.gc(); } catch (Throwable ignore) {}
        }

        // 复制兜底
        FileInputStream fis = null;
        FileOutputStream fos = null;
        try {
            fis = new FileInputStream(src);
            fos = new FileOutputStream(dst, false);
            byte[] buf = new byte[BUFFER_SIZE];
            int r;
            while ((r = fis.read(buf)) != -1) {
                fos.write(buf, 0, r);
            }
            fos.flush();
            try { fos.getFD().sync(); } catch (Throwable ignore) {}
        } catch (Exception e) {
            Log.e(TAG, "moveWithRetry: 复制兜底失败: " + e.getMessage());
            return false;
        } finally {
            closeQuiet(fis);
            closeQuiet(fos);
        }
        boolean del = src.delete();
        if (!del) Log.w(TAG, "moveWithRetry: 复制后删除源失败: " + src.getAbsolutePath());
        return true;
    }

    // ===== 网络与工具 =====

    /** 带重试 + 退避 的连接打开（支持断点续传） */
    private HttpURLConnection openWithRetry(URL url, long resumeFrom) throws Exception {
        int attempt = 0;
        long backoff = 600;
        while (true) {
            attempt++;
            HttpURLConnection c = (HttpURLConnection) url.openConnection();
            c.setInstanceFollowRedirects(true);
            c.setConnectTimeout(CONNECT_TIMEOUT + 10000);
            c.setReadTimeout(READ_TIMEOUT + 10000);
            c.setRequestMethod("GET");
            c.setRequestProperty("User-Agent", "RN-VideoCache/1.1");
            c.setRequestProperty("Accept-Encoding", "identity"); // 争取拿到 Content-Length
            if (resumeFrom > 0) c.setRequestProperty("Range", "bytes=" + resumeFrom + "-");
            c.setRequestProperty("Cache-Control", "no-cache");
            c.setUseCaches(false);
            try {
                c.connect();
                return c;
            } catch (Exception e) {
                try { c.disconnect(); } catch (Throwable ignore) {}
                if (attempt >= 3) throw e;
                try { Thread.sleep(backoff); } catch (InterruptedException ignored) {}
                backoff = Math.min(backoff * 2, 4000);
                Log.w(TAG, "openWithRetry: 第 " + attempt + " 次连接失败，重试… 原因: " + e.getMessage());
            }
        }
    }

    /** 解析总长度（兼容 HTTP 200/206） */
    private static long resolveContentLength(HttpURLConnection conn, int code, long existing) {
        try {
            if (code == HttpURLConnection.HTTP_PARTIAL) {
                String range = conn.getHeaderField("Content-Range"); // bytes start-end/total
                if (range != null) {
                    int slash = range.lastIndexOf('/');
                    if (slash >= 0 && slash + 1 < range.length()) {
                        String totalStr = range.substring(slash + 1).trim();
                        return Long.parseLong(totalStr);
                    }
                }
            } else {
                String cl = conn.getHeaderField("Content-Length");
                if (cl != null) return Long.parseLong(cl.trim());
                int len = conn.getContentLength();
                if (len > 0) return (long) len;
            }
        } catch (Exception ignore) {}
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

    /** 回调接口 */
    public interface DownloadCallback {
        void onStart(String videoId);
        void onProgress(String videoId, int progress);
        void onSuccess(String videoId, String localPath);
        void onError(String videoId, String error);
    }
}
