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
    /**
     * 只上传“到昨天为止”的播放记录；今天的记录保留在本地。
     * 上传成功后，仅删除已上传的记录，并持久化剩余记录。
     */
    public void uploadAndClearRecords() {
        UserAccount userAccount = UserUtils.getUserAccount(context);
        String userId = (userAccount != null) ? userAccount.getUserId() : null;
        String adminId = (userAccount != null) ? userAccount.getAdminId() : null;

        if (TextUtils.isEmpty(userId)) {
            Log.w(TAG, "用户未登录，跳过上传播放记录");
            return;
        }
        if (playRecordsList.isEmpty()) {
            Log.d(TAG, "无播放记录需要上传");
            return;
        }

        // 计算“今天”的 yyyy-MM-dd（本地时区）。如果你希望固定中国时区，可取消注释 timeZone 设置。
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        // sdf.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai")); // 固定中国时区（可选）
        final String today = sdf.format(new Date());

        // 按“早于今天”过滤：只上传这些；其余（含今天/未来）保留
        final List<PlayCountRecord> toUpload = new ArrayList<>();
        final List<PlayCountRecord> toKeep = new ArrayList<>();

        for (PlayCountRecord r : playRecordsList) {
            String d = r.playDate;
            // 记录没有日期，视为需要上传（避免永久积压）
            if (TextUtils.isEmpty(d)) {
                toUpload.add(r);
            } else if (d.compareTo(today) < 0) { // yyyy-MM-dd 同格式可直接字典序比较
                toUpload.add(r);
            } else {
                toKeep.add(r); // 今天及以后 → 保留
            }
        }

        if (toUpload.isEmpty()) {
            Log.d(TAG, "没有早于今天的记录需要上传（今天的记录将保留到明天）");
            return;
        }

        Log.d(TAG, "开始上传播放记录：总数=" + playRecordsList.size()
                + "，上传（<今天）=" + toUpload.size()
                + "，保留（>=今天）=" + toKeep.size());

        // 组装上传数据（仅 toUpload）
        Map<String, Object> uploadData = new HashMap<>();
        uploadData.put("userId", userId);
        uploadData.put("adminId", adminId);

        List<Map<String, Object>> records = new ArrayList<>();
        for (PlayCountRecord r : toUpload) {
            r.userId = userId; // 保底写入
            Map<String, Object> row = new HashMap<>();
            row.put("playHistoryId", r.playHistoryId);
            row.put("userId", userId);
            row.put("videoId", r.videoId);
            row.put("videoSeriesId", r.videoSeriesId);
            row.put("videoName", r.videoName);
            row.put("videoSeriesName", r.videoSeriesName);
            row.put("playTime", r.playTime);
            row.put("videoDuration", r.videoDuration);
            row.put("playDate", r.playDate);
            row.put("adminId", adminId);
            records.add(row);
        }
        uploadData.put("records", records);

        String json = gson.toJson(uploadData);
        Log.d(TAG, "上传JSON数据（仅<今天）: " + json);

        RequestBody body = RequestBody.create(json, MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(ApiConfig.API_VIDEO_HISTORY_RECORD_SAVE)
                .post(body)
                .build();

        OkHttpClient client = new OkHttpClient();
        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                Log.e(TAG, "上传播放记录失败: " + e.getMessage());
                // 失败时不清理，留待下次再传
            }
            @Override public void onResponse(Call call, Response response) throws IOException {
                String resp = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    Log.d(TAG, "上传播放记录成功，响应: " + resp);
                    // 只清理已上传的，保留今天及以后
                    playRecordsList = toKeep;
                    saveLocalRecords(); // 持久化剩余记录
                    Log.d(TAG, "清理已上传的<今天记录。剩余本地记录数: " + playRecordsList.size());
                } else {
                    Log.e(TAG, "上传播放记录失败，HTTP " + response.code() + "，响应: " + resp);
                    // 失败时不清理，留待下次再传
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