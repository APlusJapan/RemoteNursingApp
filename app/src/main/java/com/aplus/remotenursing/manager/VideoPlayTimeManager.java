package com.aplus.remotenursing.manager;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class VideoPlayTimeManager {
    private static final String TAG = "VideoPlayTimeManager";
    private static final String PREF_NAME = "video_play_data";

    private Context context;
    private long sessionStartTime;
    private String currentVideoId;
    private String currentVideoSeriesId;
    private String currentVideoName;
    private String currentVideoSeriesName;
    private boolean isTracking = false;

    public VideoPlayTimeManager(Context context) {
        this.context = context;
    }

    /**
     * 开始记录播放时长
     */
    public void startSession(String videoId, String videoSeriesId, String videoName, String videoSeriesName) {
        this.currentVideoId = videoId;
        this.currentVideoSeriesId = videoSeriesId;
        this.currentVideoName = videoName;
        this.currentVideoSeriesName = videoSeriesName;
        this.sessionStartTime = System.currentTimeMillis();
        this.isTracking = true;

        Log.d(TAG, "开始记录播放时长 - 视频ID: " + videoId);
    }

    /**
     * 暂停记录（但不结束session）
     */
    public void pauseSession() {
        if (isTracking && sessionStartTime > 0) {
            long sessionDuration = (System.currentTimeMillis() - sessionStartTime) / 1000;
            if (sessionDuration > 0) {
                saveToLocal(currentVideoId, (int) sessionDuration);
                Log.d(TAG, "暂停播放，保存时长: " + sessionDuration + "秒");
            }
        }
    }

    /**
     * 恢复记录（从暂停状态恢复）
     */
    public void resumeSession() {
        if (isTracking) {
            this.sessionStartTime = System.currentTimeMillis();
            Log.d(TAG, "恢复播放记录");
        }
    }

    /**
     * 结束记录
     */
    public void endSession() {
        if (isTracking && sessionStartTime > 0) {
            long sessionDuration = (System.currentTimeMillis() - sessionStartTime) / 1000;
            if (sessionDuration > 0) {
                saveToLocal(currentVideoId, (int) sessionDuration);
                Log.d(TAG, "结束播放，总时长: " + sessionDuration + "秒");
            }
            isTracking = false;
            resetSession();
        }
    }

    /**
     * 重置session数据
     */
    private void resetSession() {
        sessionStartTime = 0;
        currentVideoId = null;
        currentVideoSeriesId = null;
        currentVideoName = null;
        currentVideoSeriesName = null;
    }

    /**
     * 保存播放时长到本地SharedPreferences
     */
    private void saveToLocal(String videoId, int seconds) {
        if (seconds <= 0) return;

        String today = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());
        String key = "video_play_time_" + today + "_" + videoId;

        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        int currentTotal = prefs.getInt(key, 0);
        int newTotal = currentTotal + seconds;

        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(key, newTotal);

        // 同时保存视频相关信息，用于后续上报
        // 注意：这里不能覆盖，要检查是否已存在
        if (!prefs.contains(key + "_series_id") || prefs.getString(key + "_series_id", "").isEmpty()) {
            editor.putString(key + "_series_id", currentVideoSeriesId);
        }
        if (!prefs.contains(key + "_video_name") || prefs.getString(key + "_video_name", "").isEmpty()) {
            editor.putString(key + "_video_name", currentVideoName);
        }
        if (!prefs.contains(key + "_series_name") || prefs.getString(key + "_series_name", "").isEmpty()) {
            editor.putString(key + "_series_name", currentVideoSeriesName);
        }

        editor.apply();

        Log.d(TAG, "保存播放时长到本地 - 视频ID: " + videoId + ", 本次: " + seconds + "秒, 累计: " + newTotal + "秒");
        Log.d(TAG, "视频信息 - 系列ID: " + currentVideoSeriesId + ", 视频名: " + currentVideoName);
    }

    /**
     * 获取当前video的今日累计播放时长
     */
    public int getTodayPlayTime(String videoId) {
        String today = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());
        String key = "video_play_time_" + today + "_" + videoId;

        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(key, 0);
    }
}