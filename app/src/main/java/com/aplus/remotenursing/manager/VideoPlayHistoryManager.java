package com.aplus.remotenursing.manager;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.aplus.remotenursing.models.VideoPlayRecord;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class VideoPlayHistoryManager {
    private static final String TAG = "VideoPlayHistoryManager";
    private static final String PREF_NAME = "video_play_data";
    private static VideoPlayHistoryManager instance;

    private Context context;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());

    private VideoPlayHistoryManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized VideoPlayHistoryManager getInstance(Context context) {
        if (instance == null) {
            instance = new VideoPlayHistoryManager(context);
        }
        return instance;
    }

    /**
     * 获取昨天及之前的播放数据
     */
    public List<VideoPlayRecord> getHistoryPlayData(String userId, String adminId) {
        List<VideoPlayRecord> historyData = new ArrayList<>();
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        String today = dateFormat.format(new Date());
        SimpleDateFormat apiDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()); // API使用的日期格式

        Map<String, ?> allData = prefs.getAll();
        for (Map.Entry<String, ?> entry : allData.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("video_play_time_") && !key.endsWith("_series_id")
                    && !key.endsWith("_video_name") && !key.endsWith("_series_name")) {

                String[] parts = key.split("_");
                if (parts.length >= 5) {
                    String dateStr = parts[3]; // video_play_time_YYYYMMDD_videoId
                    String videoId = parts[4];

                    // 只处理昨天及之前的数据
                    if (dateStr.compareTo(today) < 0) {
                        try {
                            // 将 YYYYMMDD 转换为 YYYY-MM-DD 格式
                            Date date = dateFormat.parse(dateStr);
                            String apiDateStr = apiDateFormat.format(date);

                            VideoPlayRecord record = new VideoPlayRecord();
                            record.setUserId(userId);
                            record.setPlayDate(apiDateStr); // 使用 YYYY-MM-DD 格式
                            record.setVideoId(videoId);
                            record.setPlayTime((Integer) entry.getValue());
                            record.setAdminId(adminId); // 新增：设置adminId

                            // 获取视频相关信息
                            record.setVideoSeriesId(prefs.getString(key + "_series_id", ""));
                            record.setVideoName(prefs.getString(key + "_video_name", ""));
                            record.setVideoSeriesName(prefs.getString(key + "_series_name", ""));

                            historyData.add(record);

                            Log.d(TAG, "找到历史数据 - 日期: " + apiDateStr + ", 视频ID: " + videoId + ", 时长: " + entry.getValue() + "秒");
                        } catch (ParseException e) {
                            Log.e(TAG, "日期解析错误: " + dateStr, e);
                        }
                    }
                }
            }
        }

        Log.d(TAG, "共找到 " + historyData.size() + " 条历史播放数据");
        return historyData;
    }

    /**
     * 清除已上报的历史数据（保留今天的数据）
     */
    public void clearHistoryData() {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        String today = dateFormat.format(new Date());
        int deletedCount = 0;

        Map<String, ?> allData = prefs.getAll();
        for (String key : allData.keySet()) {
            if (key.startsWith("video_play_time_")) {
                String[] parts = key.split("_");
                if (parts.length >= 4) {
                    String dateStr = parts[3];
                    // 删除昨天及之前的数据，保留今天的
                    if (dateStr.compareTo(today) < 0) {
                        editor.remove(key);
                        deletedCount++;
                        Log.d(TAG, "删除历史数据: " + key);
                    }
                }
            }
        }

        editor.apply();
        Log.d(TAG, "清理完成，共删除 " + deletedCount + " 条历史数据");
    }

    /**
     * 清理超过指定天数的数据
     */
    public void cleanupOldData(int keepDays) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        // 计算截止日期
        long cutoffTime = System.currentTimeMillis() - (keepDays * 24 * 60 * 60 * 1000L);
        String cutoffDate = dateFormat.format(new Date(cutoffTime));

        Map<String, ?> allData = prefs.getAll();
        for (String key : allData.keySet()) {
            if (key.startsWith("video_play_time_")) {
                String[] parts = key.split("_");
                if (parts.length >= 4) {
                    String dateStr = parts[3];
                    if (dateStr.compareTo(cutoffDate) < 0) {
                        editor.remove(key);
                    }
                }
            }
        }

        editor.apply();
        Log.d(TAG, "清理超过 " + keepDays + " 天的旧数据完成");
    }
    /**
     * 调试方法：打印所有本地播放数据
     */
    public void logAllLocalData() {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Map<String, ?> allData = prefs.getAll();

        Log.d(TAG, "=== 所有本地播放数据 ===");
        int dataCount = 0;
        for (Map.Entry<String, ?> entry : allData.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("video_play_time_") && !key.endsWith("_series_id")
                    && !key.endsWith("_video_name") && !key.endsWith("_series_name")) {

                String[] parts = key.split("_");
                if (parts.length >= 5) {
                    String dateStr = parts[3];
                    String videoId = parts[4];
                    int playTime = (Integer) entry.getValue();

                    String seriesId = prefs.getString(key + "_series_id", "");
                    String videoName = prefs.getString(key + "_video_name", "");

                    Log.d(TAG, String.format("数据%d - 日期:%s, 视频ID:%s, 时长:%d秒, 系列:%s, 名称:%s",
                            ++dataCount, dateStr, videoId, playTime, seriesId, videoName));
                }
            }
        }
        Log.d(TAG, "共 " + dataCount + " 条播放数据");
        Log.d(TAG, "========================");
    }
}