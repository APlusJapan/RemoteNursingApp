package com.aplus.remotenursing.common;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.aplus.remotenursing.models.UserAccount;
import com.aplus.remotenursing.models.PointRuleTaskType;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserUtils {

    private static final String TAG = "UserUtils";

    // ====== 用户账号相关方法 ======
    public static void saveUserAccount(Context context, UserAccount account) {
        SharedPreferences sp = context.getSharedPreferences(Contants.LOCAL_FILE_NAME, Context.MODE_PRIVATE);
        sp.edit().putString(Contants.LOCAL_FILE_JSON_KEY, new Gson().toJson(account)).apply();
    }

    public static UserAccount getUserAccount(Context context) {
        SharedPreferences sp = context.getSharedPreferences(Contants.LOCAL_FILE_NAME, Context.MODE_PRIVATE);
        String json = sp.getString(Contants.LOCAL_FILE_JSON_KEY, null);
        if (json != null) {
            return new Gson().fromJson(json, UserAccount.class);
        }
        return null;
    }

    public static String loadUserId(Context context) {
        UserAccount account = getUserAccount(context);
        return account != null ? account.getUserId() : null;
    }

    public static void logout(Context context) {
        SharedPreferences sp = context.getSharedPreferences(Contants.LOCAL_FILE_NAME, Context.MODE_PRIVATE);
        sp.edit().clear().apply();

        // 同时清除积分规则缓存
        SharedPreferences pointSp = context.getSharedPreferences(Contants.USER_POINT_RULES, Context.MODE_PRIVATE);
        pointSp.edit().clear().apply();

        // 清除体检结果缓存
        SharedPreferences checkupSp = context.getSharedPreferences("checkup_cache", Context.MODE_PRIVATE);
        checkupSp.edit().clear().apply();
    }

    // ====== 积分规则相关方法 ======

    /**
     * 保存积分规则到本地缓存
     * @param context 上下文
     * @param rules 积分规则列表
     */
    public static void savePointRules(Context context, List<PointRuleTaskType> rules) {
        if (context == null || rules == null) {
            Log.w(TAG, "savePointRules: context或rules为null");
            return;
        }

        SharedPreferences sp = context.getSharedPreferences(Contants.USER_POINT_RULES, Context.MODE_PRIVATE);
        Gson gson = new Gson();
        String json = gson.toJson(rules);
        sp.edit().putString(Contants.USER_POINT_KEY, json).apply();

        Log.d(TAG, "积分规则已保存到缓存，共" + rules.size() + "条规则");
    }

    /**
     * 从本地缓存读取积分规则列表
     * @param context 上下文
     * @return 积分规则列表，如果没有缓存则返回空列表
     */
    public static List<PointRuleTaskType> getPointRules(Context context) {
        List<PointRuleTaskType> result = new ArrayList<>();
        if (context == null) {
            Log.w(TAG, "getPointRules: context为null");
            return result;
        }

        SharedPreferences sp = context.getSharedPreferences(Contants.USER_POINT_RULES, Context.MODE_PRIVATE);
        String json = sp.getString(Contants.USER_POINT_KEY, null);

        if (json == null || json.isEmpty()) {
            Log.d(TAG, "没有找到缓存的积分规则");
            return result;
        }

        Gson gson = new Gson();
        Type listType = new TypeToken<List<PointRuleTaskType>>(){}.getType();

        try {
            List<PointRuleTaskType> list = gson.fromJson(json, listType);
            if (list != null) {
                result = list;
                Log.d(TAG, "从缓存读取积分规则成功，共" + result.size() + "条规则");
            }
        } catch (Exception e) {
            Log.e(TAG, "解析积分规则缓存失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 构建任务类型到积分数量的映射表
     * @param context 上下文
     * @return Map<TaskType, PointAmount>
     */
    public static Map<String, Integer> getPointRuleMap(Context context) {
        Map<String, Integer> map = new HashMap<>();
        List<PointRuleTaskType> rules = getPointRules(context);

        for (PointRuleTaskType rule : rules) {
            if (rule != null && rule.getTaskType() != null && rule.getPointAmount() != null) {
                map.put(rule.getTaskType(), rule.getPointAmount());
            }
        }

        return map;
    }

    /**
     * 根据任务类型获取对应的积分数量
     * @param context 上下文
     * @param taskType 任务类型 ("01":视频任务, "02":体检任务, "03":每日打卡, "04":问卷调查)
     * @return 积分数量，如果未找到则返回0
     */
    public static int getPointForTaskType(Context context, String taskType) {
        if (taskType == null || taskType.isEmpty()) {
            Log.w(TAG, "getPointForTaskType: taskType为空");
            return 0;
        }

        Map<String, Integer> ruleMap = getPointRuleMap(context);
        Integer points = ruleMap.get(taskType);

        if (points != null) {
            Log.d(TAG, "任务类型" + taskType + "对应积分: " + points);
            return points;
        } else {
            Log.w(TAG, "未找到任务类型" + taskType + "对应的积分规则");
            return 0;
        }
    }

    /**
     * 检查积分规则是否已缓存
     * @param context 上下文
     * @return true如果有缓存的积分规则，false否则
     */
    public static boolean hasPointRulesCache(Context context) {
        return !getPointRules(context).isEmpty();
    }

    /**
     * 清除积分规则缓存
     * @param context 上下文
     */
    public static void clearPointRulesCache(Context context) {
        if (context == null) return;

        SharedPreferences sp = context.getSharedPreferences(Contants.USER_POINT_RULES, Context.MODE_PRIVATE);
        sp.edit().clear().apply();
        Log.d(TAG, "积分规则缓存已清除");
    }

    // ====== 积分增加API调用方法 ======

    /**
     * 调用后台API给用户增加积分（简单的网络请求工具方法）
     * @param userId 用户ID
     * @param points 要增加的积分数
     * @param callback OkHttp回调
     */
    public static void addPointsToUserApi(String userId, int points, okhttp3.Callback callback) {
        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();

        // 构建POST请求的表单数据
        okhttp3.FormBody formBody = new okhttp3.FormBody.Builder()
                .add("userId", userId)
                .add("point", String.valueOf(points))
                .build();

        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(ApiConfig.API_ADD_USERPOINT) // 需要在ApiConfig中定义此常量
                .post(formBody)
                .build();

        Log.d(TAG, "发送积分增加请求: userId=" + userId + ", points=" + points);
        client.newCall(request).enqueue(callback);
    }

    // ====== 体检结果缓存相关方法（纯工具方法） ======

    private static final String CACHE_CHECKUP_PREFIX = "checkup_result_";

    /**
     * 保存体检结果到缓存（支持历史记录）
     * @param context 上下文
     * @param userId 用户ID
     * @param dataJson 体检数据JSON字符串
     * @param conclusion 体检结论文案
     * @param checkupTime 体检时间
     */
    public static void saveCheckupResultCache(Context context, String userId, String dataJson,
                                              String conclusion, String checkupTime) {
        saveCheckupResultCacheWithCustomTime(context, userId, dataJson, conclusion, checkupTime, null);
    }
    /**
     * 保存体检结果到缓存（支持自定义时间，用于测试）
     * @param customDate 自定义日期（格式：yyyy-MM-dd），如果为null则使用当前时间
     */
    public static void saveCheckupResultCacheWithCustomTime(Context context, String userId, String dataJson,
                                                            String conclusion, String checkupTime, String customDate) {
        if (context == null || userId == null || userId.isEmpty()) {
            Log.w(TAG, "saveCheckupResultCache: context或userId为空");
            return;
        }

        try {
            SharedPreferences historyPrefs = context.getSharedPreferences("checkup_history", Context.MODE_PRIVATE);

            String timeStamp;
            if (customDate != null && !customDate.isEmpty()) {
                java.text.SimpleDateFormat timeSdf = new java.text.SimpleDateFormat("HHmmss", java.util.Locale.getDefault());
                String timeOnly = timeSdf.format(new java.util.Date());
                String dateOnly = customDate.replace("-", "");
                timeStamp = dateOnly + "_" + timeOnly;
                Log.d(TAG, "使用自定义日期: " + customDate + ", 生成时间戳: " + timeStamp);
            } else {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault());
                timeStamp = sdf.format(new java.util.Date());
            }

            String dataKey = userId + "_data_" + timeStamp;
            String conclusionKey = userId + "_conclusion_" + timeStamp;

            historyPrefs.edit()
                    .putString(dataKey, dataJson)
                    .putString(conclusionKey, conclusion != null ? conclusion : "")
                    .apply();

            Log.d(TAG, "体检结果已保存到历史记录: " + dataKey);

            SharedPreferences cachePrefs = context.getSharedPreferences("checkup_cache", Context.MODE_PRIVATE);
            cachePrefs.edit()
                    .putString(CACHE_CHECKUP_PREFIX + "data_" + userId, dataJson)
                    .putString(CACHE_CHECKUP_PREFIX + "conclusion_" + userId, conclusion != null ? conclusion : "")
                    .putString(CACHE_CHECKUP_PREFIX + "time_" + userId, checkupTime)
                    .apply();

        } catch (Exception e) {
            Log.e(TAG, "保存体检结果到缓存失败: " + e.getMessage());
        }
    }
    /**
     * 从缓存读取体检结果数据JSON
     * @param context 上下文
     * @param userId 用户ID
     * @return 体检数据JSON字符串，没有则返回null
     */
    public static String getCheckupResultDataJson(Context context, String userId) {
        if (context == null || userId == null || userId.isEmpty()) {
            return null;
        }

        SharedPreferences prefs = context.getSharedPreferences("checkup_cache", Context.MODE_PRIVATE);
        return prefs.getString(CACHE_CHECKUP_PREFIX + "data_" + userId, null);
    }

    /**
     * 从缓存读取体检结论
     * @param context 上下文
     * @param userId 用户ID
     * @return 体检结论，没有则返回null
     */
    public static String getCheckupResultConclusion(Context context, String userId) {
        if (context == null || userId == null || userId.isEmpty()) {
            return null;
        }

        SharedPreferences prefs = context.getSharedPreferences("checkup_cache", Context.MODE_PRIVATE);
        return prefs.getString(CACHE_CHECKUP_PREFIX + "conclusion_" + userId, null);
    }

    /**
     * 从缓存读取体检时间
     * @param context 上下文
     * @param userId 用户ID
     * @return 体检时间，没有则返回null
     */
    public static String getCheckupResultTime(Context context, String userId) {
        if (context == null || userId == null || userId.isEmpty()) {
            return null;
        }

        SharedPreferences prefs = context.getSharedPreferences("checkup_cache", Context.MODE_PRIVATE);
        return prefs.getString(CACHE_CHECKUP_PREFIX + "time_" + userId, null);
    }

    /**
     * 清除指定用户的体检结果缓存
     * @param context 上下文
     * @param userId 用户ID
     */
    public static void clearCheckupResultCache(Context context, String userId) {
        if (context == null || userId == null || userId.isEmpty()) {
            return;
        }

        SharedPreferences prefs = context.getSharedPreferences("checkup_cache", Context.MODE_PRIVATE);
        prefs.edit()
                .remove(CACHE_CHECKUP_PREFIX + "data_" + userId)
                .remove(CACHE_CHECKUP_PREFIX + "conclusion_" + userId)
                .remove(CACHE_CHECKUP_PREFIX + "time_" + userId)
                .apply();

        Log.d(TAG, "已清除用户体检结果缓存: userId=" + userId);
    }

    /**
     * 检查是否有体检结果缓存
     * @param context 上下文
     * @param userId 用户ID
     * @return true如果有缓存，false否则
     */
    public static boolean hasCheckupResultCache(Context context, String userId) {
        if (context == null || userId == null || userId.isEmpty()) {
            return false;
        }

        String dataJson = getCheckupResultDataJson(context, userId);
        return dataJson != null && !dataJson.isEmpty();
    }

    /**
     * 生成测试体检数据（用于开发测试）
     */
    public static void generateTestCheckupData(Context context, String userId, int daysAgo) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.add(java.util.Calendar.DAY_OF_MONTH, -daysAgo);
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
        String customDate = sdf.format(cal.getTime());

        java.util.Random random = new java.util.Random();
        int steps = 3000 + random.nextInt(7000);
        int heartRate = 60 + random.nextInt(30);
        int spo2 = 95 + random.nextInt(5);
        int bpHigh = 110 + random.nextInt(30);
        int bpLow = 70 + random.nextInt(20);
        float bloodGlucose = 4.5f + random.nextFloat() * 2.5f;
        int sleep = 360 + random.nextInt(180);

        String dataJson = String.format(java.util.Locale.US,
                "{\"steps\":%d,\"heartRate\":%d,\"spo2\":%d,\"bpHigh\":%d,\"bpLow\":%d,\"bloodGlucose\":%.1f,\"sleep\":%d}",
                steps, heartRate, spo2, bpHigh, bpLow, bloodGlucose, sleep);

        String conclusion = "测试数据 - " + customDate;
        String checkupTime = customDate + " 12:00";

        saveCheckupResultCacheWithCustomTime(context, userId, dataJson, conclusion, checkupTime, customDate);
        Log.d(TAG, "生成测试数据: " + customDate);
    }

    /**
     * 批量生成测试数据
     */
    public static void generateMultipleTestData(Context context, String userId, int days) {
        for (int i = 0; i < days; i++) {
            generateTestCheckupData(context, userId, i);
        }
        Log.d(TAG, "已生成 " + days + " 天的测试数据");
    }

}