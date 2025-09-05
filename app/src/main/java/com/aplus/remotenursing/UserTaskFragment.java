package com.aplus.remotenursing;

import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.aplus.remotenursing.adapters.BannerAdapter;
import com.aplus.remotenursing.adapters.UserTaskAdapter;
import com.aplus.remotenursing.common.Contants;
import com.aplus.remotenursing.models.AppBanner;
import com.aplus.remotenursing.models.UserAccount;
import com.aplus.remotenursing.models.UserTask;
import com.aplus.remotenursing.common.ApiConfig;
import com.aplus.remotenursing.common.UserUtils;
import com.aplus.remotenursing.manager.BannerActionManager;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class UserTaskFragment extends Fragment implements UserTaskAdapter.OnTaskClickListener {
    private static final String TAG = "UserTaskFragment";

    private RecyclerView rvTasks;
    private UserTaskAdapter adapter;
    private TextView tvPoint, tvNotice;
    private boolean pointRulesLoaded = false;

    private boolean tasksLoaded = false;
    private List<UserTask> userTaskList = Collections.emptyList();
    private Map<String, Integer> taskPointRuleMap = new HashMap<>();
    private androidx.cardview.widget.CardView cardLearnVideo;
    private Runnable autoScrollRunnable;
    private Handler autoScrollHandler;
    private BannerAdapter currentBannerAdapter;
    private boolean isAutoScrolling = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_user_task, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        setupRecyclerView();
        loadInitialData();
    }

    private void initViews(View view) {
        // 积分区
        TextView tvNickName = view.findViewById(R.id.tv_nick_name);
        tvPoint = view.findViewById(R.id.tv_point);
        tvNotice = view.findViewById(R.id.tv_notice);
        cardLearnVideo = view.findViewById(R.id.card_learn_video);

        // 默认隐藏
        cardLearnVideo.setVisibility(View.GONE);

        // 设置用户信息
        setUserInfo(tvNickName);

        // 设置卡片点击事件
        setupCardClickListeners();
    }

    private void setUserInfo(TextView tvNickName) {
        UserAccount userAccount = UserUtils.getUserAccount(requireContext());
        if (userAccount != null && userAccount.getNickName() != null) {
            tvNickName.setText(userAccount.getNickName());
        } else {
            tvNickName.setText("未登录，请先完成登录。");
        }
    }

    private void setupCardClickListeners() {
        cardLearnVideo.setOnClickListener(v -> {
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new VideoTaskFragment())
                    .addToBackStack(null)
                    .commit();
        });
    }

    private void setupRecyclerView() {
        rvTasks = getView().findViewById(R.id.rv_tasks);
        GridLayoutManager layoutManager = new GridLayoutManager(requireContext(), 2);
        rvTasks.setLayoutManager(layoutManager);

        if (adapter == null) {
            adapter = new UserTaskAdapter();
            adapter.setOnTaskClickListener(this);
        }
        rvTasks.setAdapter(adapter);
    }

    private void loadInitialData() {
        // 通知图片轮播区
        ViewPager2 vpBanner = getView().findViewById(R.id.vp_notice_banner);
        fetchBannerData(vpBanner);

        // 加载其他数据
        fetchUserPoint();
        fetchPointRules();
        fetchTasks();
    }

    @Override
    public void onResume() {
        super.onResume();

        // 只刷新必要的数据
        refreshUserPoint();

        // 恢复自动滚动
        if (autoScrollRunnable != null && autoScrollHandler != null) {
            startAutoScrollDelayed();
        }
    }

    private void refreshUserPoint() {
        // 只刷新积分，不重复加载其他数据
        String userId = UserUtils.loadUserId(requireContext());
        if (userId != null) {
            fetchUserPoint();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // 暂停自动滚动
        stopAutoScroll();

        // 重置banner适配器的交互状态
        if (currentBannerAdapter != null) {
            currentBannerAdapter.setUserInteracting(false);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // 清理自动滚动
        stopAutoScroll();
        currentBannerAdapter = null;
    }

    // 获取Banner数据的方法
    private void fetchBannerData(ViewPager2 vpBanner) {
        UserAccount userAccount = UserUtils.getUserAccount(requireContext());

        String projectId = "PJT1001";
        String teamId = "T001";

        if (userAccount != null) {
            if (userAccount.getProjectId() != null && !userAccount.getProjectId().isEmpty()) {
                projectId = userAccount.getProjectId();
            }
            teamId = userAccount.getTeamId();
        }

        OkHttpClient client = new OkHttpClient();
        String url = ApiConfig.API_GET_BANNERS +
                "?projectId=" + projectId +
                (teamId != null && !teamId.isEmpty() ? "&teamId=" + teamId : "");

        Log.d(TAG, "Banner请求URL: " + url);

        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "获取Banner失败: " + e.getMessage());
                if (getActivity() != null) {
                    requireActivity().runOnUiThread(() -> setupDefaultBanners(vpBanner));
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    // 修复：正确判断HTTP状态码
                    if (response.isSuccessful() && getActivity() != null) {
                        String json = response.body().string();
                        Log.d(TAG, "Banner响应成功，数据长度: " + json.length());

                        try {
                            Gson gson = new Gson();
                            List<AppBanner> list = gson.fromJson(json, new TypeToken<List<AppBanner>>(){}.getType());

                            if (list != null && !list.isEmpty()) {
                                // 过滤有效的Banner
                                List<AppBanner> validBanners = filterValidBanners(list);

                                if (!validBanners.isEmpty()) {
                                    requireActivity().runOnUiThread(() ->
                                            setupBannerViewPager(vpBanner, validBanners));
                                } else {
                                    Log.w(TAG, "没有有效Banner，使用默认");
                                    requireActivity().runOnUiThread(() -> setupDefaultBanners(vpBanner));
                                }
                            } else {
                                Log.w(TAG, "Banner列表为空");
                                requireActivity().runOnUiThread(() -> setupDefaultBanners(vpBanner));
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "解析Banner数据失败", e);
                            requireActivity().runOnUiThread(() -> setupDefaultBanners(vpBanner));
                        }
                    } else {
                        Log.w(TAG, "Banner响应失败: " + response.code() + " " + response.message());
                        if (getActivity() != null) {
                            requireActivity().runOnUiThread(() -> setupDefaultBanners(vpBanner));
                        }
                    }
                } finally {
                    response.close();
                }
            }
        });
    }

    // 过滤有效Banner的方法
    private List<AppBanner> filterValidBanners(List<AppBanner> banners) {
        List<AppBanner> validBanners = new ArrayList<>();

        for (AppBanner banner : banners) {
            if (banner.isActive() && isValidBanner(banner)) {
                validBanners.add(banner);
            } else {
                Log.w(TAG, "跳过无效Banner: " +
                        (banner != null ? banner.getTitle() : "null"));
            }
        }

        Log.d(TAG, "有效Banner数量: " + validBanners.size() + "/" + banners.size());
        return validBanners;
    }

    // 验证Banner数据的完整性
    private boolean isValidBanner(AppBanner banner) {
        if (banner == null) return false;

        if (banner.getId() == null || banner.getId()== null ) {
            return false;
        }

        if (banner.getTitle() == null || banner.getTitle().trim().isEmpty()) {
            return false;
        }

        if (banner.getImageUrl() == null || banner.getImageUrl().trim().isEmpty()) {
            Log.w(TAG, "Banner图片URL为空: " + banner.getTitle());
            return false;
        }

        if (banner.getActionData() == null || banner.getActionData().trim().isEmpty()) {
            Log.w(TAG, "Banner动作数据为空: " + banner.getTitle());
            return false;
        }

        try {
            // 验证ActionData JSON格式
            new JSONObject(banner.getActionData());
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Banner动作数据JSON无效: " + banner.getTitle());
            return false;
        }
    }

    // 设置动态Banner ViewPager - 添加用户交互检测
    private void setupBannerViewPager(ViewPager2 vpBanner, List<AppBanner> banners) {
        BannerActionManager actionManager = new BannerActionManager(requireContext());

        BannerAdapter adapter = BannerAdapter.createWithBanners(requireContext(), banners);
        currentBannerAdapter = adapter; // 保存引用

        adapter.setOnBannerClickListener(new BannerAdapter.OnBannerClickListener() {
            @Override
            public void onBannerClick(AppBanner banner, int position) {
                Log.d(TAG, "Banner被真实点击: " + banner.getTitle());
                actionManager.handleBannerClick(banner);
            }

            @Override
            public void onBannerView(AppBanner banner, int position) {
                // 记录展示统计 - 不再自动调用，避免过度记录
                // recordBannerView(banner);
            }

            @Override
            public void onLegacyBannerClick(String url, int position) {
                // 兼容旧版本，不需要实现
            }
        });

        vpBanner.setAdapter(adapter);

        // 添加页面变化监听器，用于区分自动滑动和用户滑动
        vpBanner.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageScrollStateChanged(int state) {
                super.onPageScrollStateChanged(state);

                switch (state) {
                    case ViewPager2.SCROLL_STATE_DRAGGING:
                        // 用户开始拖拽
                        if (currentBannerAdapter != null) {
                            currentBannerAdapter.setUserInteracting(true);
                        }
                        isAutoScrolling = false;
                        stopAutoScroll(); // 停止自动滚动
                        Log.d(TAG, "用户开始拖拽banner");
                        break;

                    case ViewPager2.SCROLL_STATE_SETTLING:
                        // 滑动到目标位置
                        if (!isAutoScrolling && currentBannerAdapter != null) {
                            currentBannerAdapter.setUserInteracting(true);
                        }
                        break;

                    case ViewPager2.SCROLL_STATE_IDLE:
                        // 滑动结束
                        if (currentBannerAdapter != null) {
                            currentBannerAdapter.setUserInteracting(false);
                        }
                        // 延迟重启自动滚动
                        if (!isAutoScrolling && banners.size() > 1) {
                            startAutoScrollDelayed();
                        }
                        Log.d(TAG, "Banner滑动结束");
                        break;
                }
            }

            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                Log.d(TAG, "Banner切换到位置: " + position);
            }
        });

        // 设置触摸监听器
        vpBanner.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        // 用户按下时标记为交互状态并停止自动滚动
                        if (currentBannerAdapter != null) {
                            currentBannerAdapter.setUserInteracting(true);
                        }
                        stopAutoScroll();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        // 延迟重置交互状态，给点击事件留时间
                        v.postDelayed(() -> {
                            if (currentBannerAdapter != null && !isAutoScrolling) {
                                currentBannerAdapter.setUserInteracting(false);
                            }
                            // 延迟重启自动滚动
                            if (banners.size() > 1) {
                                startAutoScrollDelayed();
                            }
                        }, 200);
                        break;
                }
                return false;
            }
        });

        // 如果有多个Banner才启动自动滚动
        if (banners.size() > 1) {
            startAutoScroll(vpBanner);
        }
    }

    // 设置默认Banner
    private void setupDefaultBanners(ViewPager2 vpBanner) {
        List<String> defaultUrls = Arrays.asList(
                "https://preview.qiantucdn.com/auto_machine/20231019/46d772bb-d956-43ad-9c9f-cdd320d87caa.png!qt_h320",
                "https://img2.baidu.com/it/u=3487252190,2163576535&fm=253&fmt=auto&app=138&f=JPEG?w=500&h=255"
        );

        BannerAdapter defaultAdapter = new BannerAdapter(requireContext(), defaultUrls);
        currentBannerAdapter = defaultAdapter; // 保存引用
        vpBanner.setAdapter(defaultAdapter);

        if (defaultUrls.size() > 1) {
            startAutoScroll(vpBanner);
        }
    }

    // 启动自动滚动 - 添加自动滚动状态控制
    private void startAutoScroll(ViewPager2 vpBanner) {
        stopAutoScroll(); // 先停止之前的滚动

        autoScrollHandler = new Handler();
        autoScrollRunnable = new Runnable() {
            @Override
            public void run() {
                if (vpBanner != null && vpBanner.getAdapter() != null && isAdded()) {
                    int itemCount = vpBanner.getAdapter().getItemCount();
                    if (itemCount > 1) {
                        isAutoScrolling = true;
                        // 标记为非用户交互
                        if (currentBannerAdapter != null) {
                            currentBannerAdapter.setUserInteracting(false);
                        }

                        int nextItem = (vpBanner.getCurrentItem() + 1) % itemCount;
                        vpBanner.setCurrentItem(nextItem, true);

                        // 滚动完成后恢复状态
                        vpBanner.postDelayed(() -> {
                            isAutoScrolling = false;
                        }, 500); // 等待滚动动画完成

                        autoScrollHandler.postDelayed(this, 3000); // 3秒自动滚动
                    }
                }
            }
        };
        autoScrollHandler.postDelayed(autoScrollRunnable, 3000);
    }

    // 延迟启动自动滚动
    private void startAutoScrollDelayed() {
        if (autoScrollHandler != null && autoScrollRunnable != null) {
            autoScrollHandler.postDelayed(autoScrollRunnable, 2000); // 2秒后重启
        }
    }

    // 停止自动滚动
    private void stopAutoScroll() {
        if (autoScrollRunnable != null && autoScrollHandler != null) {
            autoScrollHandler.removeCallbacks(autoScrollRunnable);
        }
        isAutoScrolling = false;
    }

    private void fetchUserPoint() {
        String userId = UserUtils.loadUserId(requireContext());
        if (userId == null) return;
        OkHttpClient client = new OkHttpClient();
        String url = ApiConfig.API_USER_POINT_ACCOUNT + "?userId=" + userId;
        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) { }
            @Override public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (response.isSuccessful() && getActivity() != null) {
                        String body = response.body().string();
                        try {
                            JSONObject obj = new JSONObject(body);
                            final int point = obj.optInt("totalPoint", 0);
                            requireActivity().runOnUiThread(() -> tvPoint.setText("当前积分：" + point));
                        } catch (Exception e) {
                            requireActivity().runOnUiThread(() -> tvPoint.setText("当前积分：0"));
                        }
                    }
                } finally {
                    response.close();
                }
            }
        });
    }

    private void fetchPointRules() {
        OkHttpClient client = new OkHttpClient();
        String url = ApiConfig.API_POINT_RULES;
        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (response.isSuccessful() && getActivity() != null) {
                        String body = response.body().string();
                        try {
                            JSONArray arr = new JSONArray(body);
                            taskPointRuleMap.clear();
                            if (arr != null) {
                                for (int i = 0; i < arr.length(); i++) {
                                    JSONObject obj = arr.getJSONObject(i);
                                    String taskType = obj.optString("taskType");
                                    int pointAmount = obj.optInt("pointAmount", 0);
                                    taskPointRuleMap.put(taskType, pointAmount);
                                }
                            }
                            pointRulesLoaded = true;
                            tryRefreshTaskUI();
                        } catch (Exception e) {}
                    }
                } finally {
                    response.close();
                }
            }
        });

        // 获取通知内容
        fetchNoticeContent();
    }

    private void fetchTasks() {
        String userId = UserUtils.loadUserId(requireContext());
        if (userId == null) return;
        String url = ApiConfig.API_USER_TASK + "?userId=" + userId;
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                Log.e(TAG, "fetchTasks failed: " + e.getMessage());
            }
            @Override public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (response.isSuccessful() && getActivity() != null) {
                        String json = response.body().string();
                        Log.d(TAG, "fetchTasks返回: " + json);
                        Gson gson = new Gson();
                        List<UserTask> list = gson.fromJson(json, new TypeToken<List<UserTask>>(){}.getType());
                        Log.d(TAG, "解析后list.size=" + (list != null ? list.size() : "null"));
                        if (list != null) {
                            Collections.sort(list, (a, b) -> Integer.compare(a.getTaskOrder(), b.getTaskOrder()));
                        }
                        userTaskList = list;
                        tasksLoaded = true;
                        tryRefreshTaskUI();
                    }
                } finally {
                    response.close();
                }
            }
        });
    }

    private void tryRefreshTaskUI() {
        if (tasksLoaded && pointRulesLoaded && getActivity() != null) {
            requireActivity().runOnUiThread(() -> {
                // 01类型给康复视频库，剩下的放任务区
                UserTask videoTask = null;
                List<UserTask> showTasks = new ArrayList<>();
                for (UserTask t : userTaskList) {
                    String type = t.getTaskType();
                    if (type.length() == 1) type = "0" + type;
                    if ("01".equals(type)) {
                        videoTask = t;
                    } else {
                        showTasks.add(t);
                    }
                }
                // 康复视频库模块显示/隐藏和内容
                if (videoTask != null) {
                    cardLearnVideo.setVisibility(View.VISIBLE);
                    TextView tvTitle = cardLearnVideo.findViewById(R.id.tv_learn_video_title);
                    tvTitle.setTextSize(26); // 字再大一点
                    tvTitle.setText(videoTask.getTaskName());
                } else {
                    cardLearnVideo.setVisibility(View.GONE);
                }
                // Adapter设置
                adapter.setTaskPointRuleMap(new HashMap<>(taskPointRuleMap));
                adapter.setTasks(showTasks);
            });
        }
    }

    /**
     * 获取通知内容的方法
     */
    private void fetchNoticeContent() {
        UserAccount userAccount = UserUtils.getUserAccount(requireContext());
        if (userAccount == null || userAccount.getProjectId() == null || userAccount.getTeamId() == null) {
            setDefaultNotice("欢迎使用远程护理系统");
            return;
        }

        String projectId = userAccount.getProjectId();
        String teamId = userAccount.getTeamId();

        OkHttpClient client = new OkHttpClient();
        String noticeUrl = ApiConfig.API_GET_NOTICE +
                "?projectId=" + projectId +
                "&teamId=" + teamId;

        Log.d(TAG, "通知请求URL: " + noticeUrl);

        Request noticeRequest = new Request.Builder().url(noticeUrl).build();

        client.newCall(noticeRequest).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "获取通知失败: " + e.getMessage());
                setDefaultNotice("欢迎使用远程护理系统");
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    // 修复：正确判断HTTP状态码
                    if (response.isSuccessful() && getActivity() != null) {
                        String noticeBody = response.body().string();
                        Log.d(TAG, "通知响应成功，数据长度: " + noticeBody.length());

                        try {
                            JSONArray noticeArray = new JSONArray(noticeBody);
                            String displayNotice = processNoticeList(noticeArray);

                            requireActivity().runOnUiThread(() -> {
                                if (tvNotice != null) {
                                    tvNotice.setText(displayNotice);
                                }
                            });

                        } catch (Exception e) {
                            Log.e(TAG, "解析通知数据失败: " + e.getMessage());
                            setDefaultNotice("（暂无新通知）");
                        }
                    } else {
                        Log.w(TAG, "获取通知失败: " + response.code() + " " + response.message());
                        setDefaultNotice("（暂无新通知）");
                    }
                } finally {
                    response.close();
                }
            }
        });
    }

    private void setDefaultNotice(String notice) {
        if (getActivity() != null) {
            requireActivity().runOnUiThread(() -> {
                if (tvNotice != null) {
                    tvNotice.setText(notice);
                }
            });
        }
    }

    /**
     * 处理通知列表，选择要显示的通知内容
     */
    private String processNoticeList(JSONArray noticeArray) {
        if (noticeArray == null || noticeArray.length() == 0) {
            return "（暂无新通知）";
        }

        // 收集所有有效的通知文本
        List<String> noticeTexts = new ArrayList<>();

        for (int i = 0; i < noticeArray.length(); i++) {
            try {
                JSONObject noticeObj = noticeArray.getJSONObject(i);
                String noticeText = noticeObj.optString("noticeText");

                // 只添加非空的通知文本
                if (noticeText != null && !noticeText.trim().isEmpty()) {
                    noticeTexts.add(noticeText.trim());
                }
            } catch (Exception e) {
                Log.w(TAG, "处理第" + i + "个通知时出错: " + e.getMessage());
            }
        }

        if (noticeTexts.isEmpty()) {
            return "（暂无新通知）";
        }

        // 如果只有一条通知，直接显示
        if (noticeTexts.size() == 1) {
            return noticeTexts.get(0);
        }

        // 如果有多条通知，显示第一条
        return noticeTexts.get(0);
    }

    @Override
    public void onTaskClick(UserTask task) {
        if (Contants.USER_TASK_TYPE_VIDEO_01.equals(task.getTaskType())) {
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new VideoTaskFragment())
                    .addToBackStack(null)
                    .commit();
        } else if (Contants.USER_TASK_TYPE_CHECKUP_02.equals(task.getTaskType())) {
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new SmartwatchCheckupFragment())
                    .addToBackStack(null)
                    .commit();
        } else if (Contants.USER_TASK_TYPE_DAILYCHECKIN_03.equals(task.getTaskType())) {
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new DailyCheckInFragment())
                    .addToBackStack(null)
                    .commit();
        } else if (Contants.USER_TASK_TYPE_QA_04.equals(task.getTaskType())) {
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new QuestionnaireFragment())
                    .addToBackStack(null)
                    .commit();
        }
    }
}