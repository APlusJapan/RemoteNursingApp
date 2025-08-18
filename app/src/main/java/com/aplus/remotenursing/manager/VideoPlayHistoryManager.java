package com.aplus.remotenursing.manager;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;
import com.aplus.remotenursing.common.ApiConfig;
import com.aplus.remotenursing.common.UserUtils;
import com.aplus.remotenursing.models.UserAccount;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import okhttp3.*;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 视频播放历史管理器
 * 记录每个视频的播放次数，下次打开应用时自动上传并清除本地记录
 */
public class VideoPlayHistoryManager {
    private static final String TAG = "VideoPlayHistoryManager";
    private static final String PREFS_NAME = "video_play_history";
    private static final String KEY_PLAY_RECORDS = "play_records";

    private Context context;
    private SharedPreferences prefs;
    private Gson gson;
    // 修改：使用List存储所有播放记录，避免覆盖
    private List<PlayCountRecord> playRecordsList;

    // 单例模式
    private static VideoPlayHistoryManager instance;

    public static VideoPlayHistoryManager getInstance(Context context) {
        if (instance == null) {
            synchronized (VideoPlayHistoryManager.class) {
                if (instance == null) {
                    instance = new VideoPlayHistoryManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private VideoPlayHistoryManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
        this.playRecordsList = new ArrayList<>();
        loadLocalRecords();
    }

    /**
     * 播放记录数据结构 - 对应后台 VideoPlayHistory 实体
     */
    public static class PlayCountRecord {
        public String playHistoryId;     // 播放记录ID
        public String userId;            // 用户ID
        public String videoId;           // 视频ID
        public String videoSeriesId;     // 视频系列ID
        public String videoName;         // 视频名称
        public String videoSeriesName;   // 视频系列名称
        public String playTime;          // 播放次数（作为字符串存储）
        public String videoDuration;     // 视频时长
        public String playDate;          // 播放日期 yyyy-MM-dd 格式
        public int playCount;            // 本地计数器
        public long timestamp;           // 添加时间戳用于区分记录

        public PlayCountRecord() {}

        public PlayCountRecord(String videoId, String videoSeriesId, String videoName,
                               String videoSeriesName, String videoDuration) {
            this.videoId = videoId;
            this.videoSeriesId = videoSeriesId;
            this.videoName = videoName;
            this.videoSeriesName = videoSeriesName;
            this.videoDuration = videoDuration;
            this.playCount = 1; // 改为1，表示播放了1次
            this.playTime = "1"; // 初始播放次数为1
            this.timestamp = System.currentTimeMillis(); // 记录时间戳

            // 设置今天的日期
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            this.playDate = dateFormat.format(new Date());

            // 生成唯一的播放历史ID
            this.playHistoryId = "PH" + System.currentTimeMillis();
        }
    }

    /**
     * 记录视频播放一次
     * 修改：每次播放都创建新记录，或合并同一天同一视频的播放次数
     */
    public void recordVideoPlay(String videoId, String videoSeriesId, String videoName,
                                String videoSeriesName, String videoDuration) {
        if (TextUtils.isEmpty(videoId)) {
            Log.w(TAG, "videoId为空，跳过记录");
            return;
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String today = dateFormat.format(new Date());

        // 查找是否有今天同一视频的记录
        PlayCountRecord existingRecord = null;
        for (PlayCountRecord record : playRecordsList) {
            if (today.equals(record.playDate) && videoId.equals(record.videoId)) {
                existingRecord = record;
                break;
            }
        }

        if (existingRecord != null) {
            // 增加已有记录的播放次数
            existingRecord.playCount++;
            existingRecord.playTime = String.valueOf(existingRecord.playCount);
            Log.d(TAG, "更新播放记录 - VideoID: " + videoId +
                    ", 日期: " + today + ", 累计次数: " + existingRecord.playCount);
        } else {
            // 创建新的播放记录
            PlayCountRecord newRecord = new PlayCountRecord(videoId, videoSeriesId,
                    videoName, videoSeriesName, videoDuration);
            playRecordsList.add(newRecord);
            Log.d(TAG, "创建新播放记录 - VideoID: " + videoId +
                    ", 视频名: " + videoName + ", 日期: " + today);
        }

        // 保存到本地
        saveLocalRecords();

        // 调试输出当前所有记录
        Log.d(TAG, "当前共有 " + playRecordsList.size() + " 条播放记录");
    }

    /**
     * 加载本地保存的播放记录
     */
    private void loadLocalRecords() {
        try {
            String json = prefs.getString(KEY_PLAY_RECORDS, "[]");
            playRecordsList = gson.fromJson(json, new TypeToken<List<PlayCountRecord>>(){}.getType());
            if (playRecordsList == null) {
                playRecordsList = new ArrayList<>();
            }
            Log.d(TAG, "加载本地播放记录，共 " + playRecordsList.size() + " 条");

            // 调试：打印所有加载的记录
            for (PlayCountRecord record : playRecordsList) {
                Log.d(TAG, "  - VideoID: " + record.videoId +
                        ", 视频名: " + record.videoName +
                        ", 播放次数: " + record.playCount +
                        ", 日期: " + record.playDate);
            }
        } catch (Exception e) {
            Log.e(TAG, "加载本地播放记录失败: " + e.getMessage());
            playRecordsList = new ArrayList<>();
        }
    }

    /**
     * 保存播放记录到本地
     */
    private void saveLocalRecords() {
        try {
            String json = gson.toJson(playRecordsList);
            prefs.edit().putString(KEY_PLAY_RECORDS, json).apply();
            Log.d(TAG, "保存 " + playRecordsList.size() + " 条播放记录到本地成功");
        } catch (Exception e) {
            Log.e(TAG, "保存播放记录到本地失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有本地播放记录
     */
    public List<PlayCountRecord> getAllLocalRecords() {
        return new ArrayList<>(playRecordsList);
    }

    /**
     * 上传播放记录到服务器并清除本地记录
     */
    public void uploadAndClearRecords() {
        UserAccount userAccount = UserUtils.getUserAccount(context);
        String userId = userAccount.getUserId();
        String adminId = userAccount.getAdminId();
        if (TextUtils.isEmpty(userId)) {
            Log.w(TAG, "用户未登录，跳过上传播放记录");
            return;
        }

        if (playRecordsList.isEmpty()) {
            Log.d(TAG, "无播放记录需要上传");
            return;
        }

        Log.d(TAG, "开始上传播放记录，共 " + playRecordsList.size() + " 条");

        // 调试：打印要上传的所有记录
        for (PlayCountRecord record : playRecordsList) {
            Log.d(TAG, "准备上传 - VideoID: " + record.videoId +
                    ", 视频名: " + record.videoName +
                    ", 播放次数: " + record.playCount);
        }

        // 构建上传数据
        Map<String, Object> uploadData = new HashMap<>();
        uploadData.put("userId", userId);
        uploadData.put("adminId", adminId);

        List<Map<String, Object>> records = new ArrayList<>();
        for (PlayCountRecord record : playRecordsList) {
            // 为每条记录设置userId
            record.userId = userId;

            Map<String, Object> recordMap = new HashMap<>();
            recordMap.put("playHistoryId", record.playHistoryId);
            recordMap.put("userId", userId); // 确保userId被设置
            recordMap.put("videoId", record.videoId);
            recordMap.put("videoSeriesId", record.videoSeriesId);
            recordMap.put("videoName", record.videoName);
            recordMap.put("videoSeriesName", record.videoSeriesName);
            recordMap.put("playTime", record.playTime); // 播放次数
            recordMap.put("videoDuration", record.videoDuration);
            recordMap.put("playDate", record.playDate);
            recordMap.put("adminId", adminId); // 添加adminId

            records.add(recordMap);
        }
        uploadData.put("records", records);

        // 发送到服务器
        String json = gson.toJson(uploadData);
        Log.d(TAG, "上传JSON数据: " + json); // 调试：打印完整的上传数据

        RequestBody body = RequestBody.create(json, MediaType.get("application/json; charset=utf-8"));

        Request request = new Request.Builder()
                .url(ApiConfig.API_VIDEO_HISTORY_RECORD_SAVE)
                .post(body)
                .build();

        OkHttpClient client = new OkHttpClient();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "上传播放记录失败: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body().string();
                if (response.isSuccessful()) {
                    Log.d(TAG, "上传播放记录成功，响应: " + responseBody);
                    // 上传成功后清除本地记录
                    clearLocalRecords();
                } else {
                    Log.e(TAG, "上传播放记录失败，响应码: " + response.code() +
                            ", 响应内容: " + responseBody);
                }
            }
        });
    }

    /**
     * 清除本地播放记录
     */
    private void clearLocalRecords() {
        Log.d(TAG, "清除前有 " + playRecordsList.size() + " 条记录");
        playRecordsList.clear();
        prefs.edit().remove(KEY_PLAY_RECORDS).apply();
        Log.d(TAG, "清除本地播放记录完成");
    }

    /**
     * 调试用：打印所有本地数据
     */
    public void logAllLocalData() {
        Log.d(TAG, "=== 播放记录本地数据 ===");
        if (playRecordsList.isEmpty()) {
            Log.d(TAG, "无本地播放记录");
        } else {
            Log.d(TAG, "共找到 " + playRecordsList.size() + " 条播放记录：");
            for (int i = 0; i < playRecordsList.size(); i++) {
                PlayCountRecord record = playRecordsList.get(i);
                Log.d(TAG, String.format("[%d] VideoID: %s, 视频名: %s, 系列: %s, " +
                                "播放次数: %s, 日期: %s, 时长: %s",
                        i + 1,
                        record.videoId,
                        record.videoName,
                        record.videoSeriesName,
                        record.playTime,
                        record.playDate,
                        record.videoDuration));
            }
        }
        Log.d(TAG, "========================");
    }

    /**
     * 调试用：获取记录数量
     */
    public int getRecordCount() {
        return playRecordsList.size();
    }
}