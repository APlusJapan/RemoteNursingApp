package com.aplus.remotenursing;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.SharedPreferences;
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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.viewpager2.widget.ViewPager2;
import android.content.Context;
import android.util.Log;
import com.aplus.remotenursing.common.InfoPopup;
import com.aplus.remotenursing.common.UserUtils;
import com.aplus.remotenursing.manager.LoginCheckerManager;
import com.aplus.remotenursing.models.UserAccount;
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
    private SwipeRefreshLayout swipeRefreshLayout;
    private androidx.cardview.widget.CardView cardLearnVideo;
    private Runnable autoScrollRunnable;
    private Handler autoScrollHandler;
    private BannerAdapter currentBannerAdapter;
    private boolean isAutoScrolling = false;

    // 数据状态管理
    private boolean isViewInitialized = false;
    private boolean tasksLoaded = false;
    private boolean pointRulesLoaded = false;
    private List<UserTask> userTaskList = Collections.emptyList();
    private Map<String, Integer> taskPointRuleMap = new HashMap<>();

    // 请求状态跟踪
    private boolean isBannerLoading = false;
    private boolean isTasksLoading = false;
    private boolean isPointLoading = false;
    private boolean isPointRulesLoading = false;

    // 通知缓存相关变量
    private String cachedNoticeContent = null;
    private long noticeLastUpdateTime = 0; // 记录最后更新时间
    private static final long NOTICE_CACHE_DURATION = 1 * 60 * 1000; // 缓存5分钟
    // 添加设备验证相关常量
    private static final String PREFS_NAME = "device_info";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final String KEY_LAST_LOGIN_PHONE = "last_login_phone";
    private static final String KEY_DEVICE_ACTIVATED = "device_activated";

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
        Log.d(TAG, "=== onViewCreated 开始 ===");
        // **首先检查登录状态**
        if (!LoginCheckerManager.checkLogin(this)) {
            Log.d(TAG, "用户未登录,已跳转登录页面");
            return; // 未登录则直接返回,不继续初始化
        }
        // 每次都重新初始化所有组件
        initViews(view);
        forceSetupRecyclerView(); // 强制重新设置RecyclerView
        isViewInitialized = true;

        // 如果有数据，延迟刷新UI
        if (tasksLoaded && pointRulesLoaded) {
            Log.d(TAG, "数据已存在，延迟刷新UI");
            new Handler().postDelayed(() -> {
                if (isAdded() && getView() != null) {
                    forceRefreshTaskUI();
                }
            }, 200);
        }

        // 加载数据
        if (!tasksLoaded || !pointRulesLoaded) {
            loadInitialData();
        } else {
            refreshUserPointOnly();
        }

        Log.d(TAG, "=== onViewCreated 完成 ===");
    }

    // 检查通知缓存是否过期
    private boolean isNoticeCacheExpired() {
        return (System.currentTimeMillis() - noticeLastUpdateTime) > NOTICE_CACHE_DURATION;
    }

    // 强制设置RecyclerView的方法
    private void forceSetupRecyclerView() {
        Log.d(TAG, "=== forceSetupRecyclerView 开始 ===");

        if (getView() == null) {
            Log.e(TAG, "View为null，无法设置RecyclerView");
            return;
        }

        // 重新获取RecyclerView引用
        rvTasks = getView().findViewById(R.id.rv_tasks);
        if (rvTasks == null) {
            Log.e(TAG, "找不到RecyclerView组件 R.id.rv_tasks");
            return;
        }

        // 清除旧的设置
        rvTasks.setAdapter(null);
        rvTasks.setLayoutManager(null);

        // 重新设置LayoutManager
        GridLayoutManager layoutManager = new GridLayoutManager(requireContext(), 2);
        rvTasks.setLayoutManager(layoutManager);
        Log.d(TAG, "LayoutManager重新设置完成");

        // 重新创建Adapter
        adapter = new UserTaskAdapter();
        adapter.setOnTaskClickListener(this);
        rvTasks.setAdapter(adapter);

        Log.d(TAG, "Adapter重新创建并设置完成");

        // 验证设置
        if (rvTasks.getAdapter() != null) {
            Log.d(TAG, "RecyclerView适配器设置成功验证");
        } else {
            Log.e(TAG, "RecyclerView适配器设置失败！");
        }
    }

    // 强制刷新UI的方法
    private void forceRefreshTaskUI() {
        Log.d(TAG, "=== forceRefreshTaskUI 开始 ===");

        if (!tasksLoaded || !pointRulesLoaded || !isAdded() || getActivity() == null) {
            Log.d(TAG, "刷新条件不满足");
            return;
        }

        requireActivity().runOnUiThread(() -> {
            // 检查通知TextView状态和缓存过期
            if (tvNotice != null) {
                String currentText = tvNotice.getText().toString();
                Log.d(TAG, "当前通知显示内容: " + currentText);

                // 如果缓存过期，重新获取通知
                if (isNoticeCacheExpired()) {
                    Log.d(TAG, "通知缓存已过期，重新获取");
                    fetchNoticeContent();
                } else if ("加载中...".equals(currentText) && cachedNoticeContent != null && !cachedNoticeContent.isEmpty()) {
                    tvNotice.setText(cachedNoticeContent);
                    Log.d(TAG, "更新通知显示为缓存内容: " + cachedNoticeContent);
                }
            } else {
                Log.w(TAG, "tvNotice为null，重新获取");
                if (getView() != null) {
                    tvNotice = getView().findViewById(R.id.tv_notice);
                    if (tvNotice != null && cachedNoticeContent != null && !isNoticeCacheExpired()) {
                        tvNotice.setText(cachedNoticeContent);
                        Log.d(TAG, "重新获取tvNotice并设置缓存内容");
                    }
                }
            }

            // 检查Banner ViewPager
            if (getView() != null) {
                ViewPager2 vpBanner = getView().findViewById(R.id.vp_notice_banner);
                if (vpBanner != null && vpBanner.getAdapter() == null) {
                    Log.w(TAG, "Banner ViewPager没有适配器，检查Banner数据加载状态");
                    if (!isBannerLoading) {
                        Log.d(TAG, "重新加载Banner数据");
                        fetchBannerData(vpBanner);
                    }
                }
            }

            // 再次检查RecyclerView
            if (rvTasks == null || rvTasks.getAdapter() == null) {
                Log.w(TAG, "RecyclerView或适配器为null，重新设置");
                forceSetupRecyclerView();
            }

            Log.d(TAG, "任务列表大小: " + (userTaskList != null ? userTaskList.size() : "null"));

            if (userTaskList == null || userTaskList.isEmpty()) {
                Log.w(TAG, "任务列表为空");
                return;
            }

            // 分离视频任务和其他任务
            UserTask videoTask = null;
            List<UserTask> showTasks = new ArrayList<>();

            for (UserTask task : userTaskList) {
                String type = task.getTaskType();
                if (type.length() == 1) type = "0" + type;

                if ("01".equals(type)) {
                    videoTask = task;
                } else {
                    showTasks.add(task);
                    Log.d(TAG, "添加显示任务: " + task.getTaskName() + " (类型: " + task.getTaskType() + ")");
                }
            }

            // 设置视频卡片
            if (videoTask != null) {
                cardLearnVideo.setVisibility(View.VISIBLE);
                TextView tvTitle = cardLearnVideo.findViewById(R.id.tv_learn_video_title);
                if (tvTitle != null) {
                    tvTitle.setText(videoTask.getTaskName());
                }
                Log.d(TAG, "视频卡片已设置: " + videoTask.getTaskName());
            } else {
                cardLearnVideo.setVisibility(View.GONE);
            }

            // 更新RecyclerView数据
            if (adapter != null && !showTasks.isEmpty()) {
                Log.d(TAG, "开始更新RecyclerView数据，任务数量: " + showTasks.size());

                adapter.setTaskPointRuleMap(new HashMap<>(taskPointRuleMap));
                adapter.setTasks(showTasks);
                adapter.notifyDataSetChanged();

                // 强制请求布局
                rvTasks.requestLayout();
                rvTasks.invalidate();

                Log.d(TAG, "RecyclerView数据更新完成");

                // 验证更新结果
                new Handler().postDelayed(() -> {
                    if (rvTasks.getAdapter() != null) {
                        Log.d(TAG, "验证：RecyclerView项目数 = " + rvTasks.getAdapter().getItemCount());
                    } else {
                        Log.e(TAG, "验证失败：RecyclerView适配器为null");
                    }
                }, 100);
            } else {
                Log.e(TAG, "适配器为null或任务列表为空，无法更新");
            }
        });
    }

    private void initViews(View view) {
        TextView tvNickName = view.findViewById(R.id.tv_nick_name);
        tvPoint = view.findViewById(R.id.tv_point);
        tvNotice = view.findViewById(R.id.tv_notice);
        cardLearnVideo = view.findViewById(R.id.card_learn_video);
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout);

        cardLearnVideo.setVisibility(View.GONE);

        // 智能通知初始化
        if (tvNotice != null) {
            // 检查缓存是否过期
            if (cachedNoticeContent != null && !cachedNoticeContent.isEmpty() && !isNoticeCacheExpired()) {
                tvNotice.setText(cachedNoticeContent);
                Log.d(TAG, "使用有效缓存的通知内容: " + cachedNoticeContent);
            } else {
                tvNotice.setText("加载中...");
                if (isNoticeCacheExpired()) {
                    Log.d(TAG, "通知缓存已过期，将重新加载");
                } else {
                    Log.d(TAG, "通知TextView初始化为加载中");
                }
            }
        } else {
            Log.e(TAG, "找不到通知TextView组件");
        }

        setUserInfo(tvNickName);
        setupCardClickListeners();
        setupSwipeRefresh();
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

    private void setupSwipeRefresh() {
        if (swipeRefreshLayout != null) {
            // 设置下拉刷新的颜色
            swipeRefreshLayout.setColorSchemeResources(
                    android.R.color.holo_blue_bright,
                    android.R.color.holo_green_light,
                    android.R.color.holo_orange_light,
                    android.R.color.holo_red_light
            );

            // 设置下拉刷新监听器
            swipeRefreshLayout.setOnRefreshListener(() -> {
                Log.d(TAG, "用户触发下拉刷新");
                refreshAllData();
            });
        }
    }

    private void loadInitialData() {
        Log.d(TAG, "=== loadInitialData 开始 ===");

        if (getView() == null) {
            Log.w(TAG, "View为null，跳过数据加载");
            return;
        }

        // 确保ViewPager2正确获取
        ViewPager2 vpBanner = getView().findViewById(R.id.vp_notice_banner);
        if (vpBanner == null) {
            Log.e(TAG, "找不到Banner ViewPager2组件 R.id.vp_notice_banner");
        } else {
            Log.d(TAG, "Banner ViewPager2组件获取成功");
        }

        // 并行加载各种数据，但避免重复请求
        if (!isBannerLoading && vpBanner != null) {
            Log.d(TAG, "开始加载Banner数据");
            fetchBannerData(vpBanner);
        }

        if (!isPointLoading) {
            Log.d(TAG, "开始加载积分数据");
            fetchUserPoint();
        }

        if (!isPointRulesLoading) {
            Log.d(TAG, "开始加载积分规则数据");
            fetchPointRules();
        }

        if (!isTasksLoading) {
            Log.d(TAG, "开始加载任务数据");
            fetchTasks();
        }

        Log.d(TAG, "=== loadInitialData 调用完成 ===");
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "=== onResume 开始 ===");

        // 设置用户信息
        if (getView() != null) {
            TextView tvNickName = getView().findViewById(R.id.tv_nick_name);
            if (tvNickName != null) {
                setUserInfo(tvNickName);
            }
        }

        // 检查通知缓存是否过期
        if (isNoticeCacheExpired()) {
            Log.d(TAG, "onResume - 通知缓存已过期，重新获取");
            refreshNoticeContent();
        }

        // 如果数据已加载，强制刷新UI
        if (isViewInitialized && tasksLoaded && pointRulesLoaded) {
            Log.d(TAG, "onResume - 执行强制刷新");
            new Handler().postDelayed(() -> {
                if (isAdded() && getView() != null) {
                    forceRefreshTaskUI();
                }
            }, 150);
        }

        // 刷新积分
        if (!isPointLoading) {
            refreshUserPointOnly();
        }

        // 恢复自动滚动
        if (autoScrollRunnable != null && autoScrollHandler != null) {
            startAutoScrollDelayed();
        }

        Log.d(TAG, "=== onResume 完成 ===");
    }

    // 手动刷新通知的方法
    public void refreshNoticeContent() {
        Log.d(TAG, "手动刷新通知内容");
        // 清空缓存，强制重新获取
        cachedNoticeContent = null;
        noticeLastUpdateTime = 0;

        if (tvNotice != null) {
            tvNotice.setText("加载中...");
        }

        fetchNoticeContent();
    }

    // 刷新所有数据（下拉刷新时调用）
    private void refreshAllData() {
        Log.d(TAG, "开始刷新所有数据");

        // 重置所有加载状态
        resetLoadingStates();

        // 重置数据状态（包括通知缓存）
        pointRulesLoaded = false;
        tasksLoaded = false;
        userTaskList = Collections.emptyList();
        taskPointRuleMap.clear();

        // 清空通知缓存，强制重新加载
        cachedNoticeContent = null;
        noticeLastUpdateTime = 0;

        // 清空适配器数据
        if (adapter != null) {
            adapter.setTasks(Collections.emptyList());
        }

        // 重新加载所有数据
        loadInitialData();

        // 延迟停止刷新动画，确保用户看到刷新效果
        new Handler().postDelayed(() -> {
            if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
                swipeRefreshLayout.setRefreshing(false);
                Log.d(TAG, "刷新完成");
            }
        }, 1500); // 1.5秒后停止刷新动画
    }

    // 重置所有加载状态
    private void resetLoadingStates() {
        isBannerLoading = false;
        isTasksLoading = false;
        isPointLoading = false;
        isPointRulesLoading = false;
    }

    // 停止刷新动画的统一方法
    private void stopRefreshAnimation() {
        if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
            swipeRefreshLayout.setRefreshing(false);
            Log.d(TAG, "停止刷新动画");
        }
    }

    // 检查所有数据是否加载完成，如果完成则停止刷新
    private void checkAndStopRefresh() {
        // 检查主要数据是否都加载完成
        boolean allMainDataLoaded = tasksLoaded && pointRulesLoaded;

        if (allMainDataLoaded && !isBannerLoading && !isPointLoading) {
            stopRefreshAnimation();
        }
    }

    // 只刷新积分的方法，避免重复请求
    private void refreshUserPointOnly() {
        UserAccount userAccount = UserUtils.getUserAccount(requireContext());
        if (userAccount == null) {
            Log.w(TAG, "无法获取用户账户信息，跳过积分刷新");
            return;
        }

        String userId = userAccount.getUserId();
        if (userId != null && !userId.isEmpty() && !isPointLoading) {
            isPointLoading = true;
            OkHttpClient client = new OkHttpClient();
            String url = ApiConfig.API_USER_POINT_ACCOUNT + "?userId=" + userId;
            Request request = new Request.Builder().url(url).build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "积分刷新失败: " + e.getMessage());
                    isPointLoading = false;
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        if (response.isSuccessful() && getActivity() != null) {
                            String body = response.body().string();
                            try {
                                JSONObject obj = new JSONObject(body);
                                final int point = obj.optInt("totalPoint", 0);
                                requireActivity().runOnUiThread(() -> {
                                    if (tvPoint != null) {
                                        tvPoint.setText("当前积分：" + point);
                                    }
                                });
                            } catch (Exception e) {
                                Log.e(TAG, "积分数据解析失败: " + e.getMessage());
                                requireActivity().runOnUiThread(() -> {
                                    if (tvPoint != null) {
                                        tvPoint.setText("当前积分：0");
                                    }
                                });
                            }
                        }
                    } finally {
                        response.close();
                        isPointLoading = false;
                    }
                }
            });
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        stopAutoScroll();

        if (currentBannerAdapter != null) {
            currentBannerAdapter.setUserInteracting(false);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopAutoScroll();
        currentBannerAdapter = null;

        // 重置状态，但保留通知缓存给下次使用
        isViewInitialized = false;
        resetLoadingStates();
        // 注意：不清空cachedNoticeContent和noticeLastUpdateTime，让它们在Fragment重建时可用
    }

    private void fetchBannerData(ViewPager2 vpBanner) {
        if (isBannerLoading) return;
        isBannerLoading = true;

        UserAccount userAccount = UserUtils.getUserAccount(requireContext());

        String projectId = userAccount.getProjectId();
        String teamId = userAccount.getTeamId();

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
                isBannerLoading = false;
                if (getActivity() != null) {
                    requireActivity().runOnUiThread(() -> setupDefaultBanners(vpBanner));
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (response.isSuccessful() && getActivity() != null) {
                        String json = response.body().string();
                        Log.d(TAG, "Banner响应成功，数据长度: " + json.length());

                        try {
                            Gson gson = new Gson();
                            List<AppBanner> list = gson.fromJson(json, new TypeToken<List<AppBanner>>(){}.getType());

                            if (list != null && !list.isEmpty()) {
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
                    isBannerLoading = false;
                }
            }
        });
    }

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

    private boolean isValidBanner(AppBanner banner) {
        if (banner == null) return false;

        if (banner.getId() == null) {
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
            new JSONObject(banner.getActionData());
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Banner动作数据JSON无效: " + banner.getTitle());
            return false;
        }
    }

    private void setupBannerViewPager(ViewPager2 vpBanner, List<AppBanner> banners) {
        if (vpBanner == null) {
            Log.e(TAG, "vpBanner为null，无法设置Banner");
            return;
        }

        if (banners == null || banners.isEmpty()) {
            Log.w(TAG, "Banner列表为空");
            setupDefaultBanners(vpBanner);
            return;
        }

        Log.d(TAG, "开始设置Banner ViewPager，Banner数量: " + banners.size());

        BannerActionManager actionManager = new BannerActionManager(getContext());

        BannerAdapter adapter = BannerAdapter.createWithBanners(getContext(), banners);
        currentBannerAdapter = adapter;

        adapter.setOnBannerClickListener(new BannerAdapter.OnBannerClickListener() {
            @Override
            public void onBannerClick(AppBanner banner, int position) {
                Log.d(TAG, "Banner被真实点击: " + banner.getTitle());
                actionManager.handleBannerClick(banner);
            }

            @Override
            public void onBannerView(AppBanner banner, int position) {
                // 不自动记录展示
            }

            @Override
            public void onLegacyBannerClick(String url, int position) {
                // 兼容旧版本
            }
        });

        vpBanner.setAdapter(adapter);
        Log.d(TAG, "Banner适配器已设置");

        // 验证设置是否成功
        if (vpBanner.getAdapter() != null) {
            Log.d(TAG, "Banner ViewPager设置成功，项目数: " + vpBanner.getAdapter().getItemCount());
        } else {
            Log.e(TAG, "Banner ViewPager设置失败");
        }

        vpBanner.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageScrollStateChanged(int state) {
                super.onPageScrollStateChanged(state);
                switch (state) {
                    case ViewPager2.SCROLL_STATE_DRAGGING:
                        if (currentBannerAdapter != null) {
                            currentBannerAdapter.setUserInteracting(true);
                        }
                        isAutoScrolling = false;
                        stopAutoScroll();
                        break;

                    case ViewPager2.SCROLL_STATE_SETTLING:
                        if (!isAutoScrolling && currentBannerAdapter != null) {
                            currentBannerAdapter.setUserInteracting(true);
                        }
                        break;

                    case ViewPager2.SCROLL_STATE_IDLE:
                        if (currentBannerAdapter != null) {
                            currentBannerAdapter.setUserInteracting(false);
                        }
                        if (!isAutoScrolling && banners.size() > 1) {
                            startAutoScrollDelayed();
                        }
                        break;
                }
            }

            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                Log.d(TAG, "Banner切换到位置: " + position);
            }
        });

        vpBanner.setOnTouchListener(new View.OnTouchListener() {
            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        if (currentBannerAdapter != null) {
                            currentBannerAdapter.setUserInteracting(true);
                        }
                        stopAutoScroll();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.postDelayed(() -> {
                            if (currentBannerAdapter != null && !isAutoScrolling) {
                                currentBannerAdapter.setUserInteracting(false);
                            }
                            if (banners.size() > 1) {
                                startAutoScrollDelayed();
                            }
                        }, 200);
                        break;
                }
                return false;
            }
        });

        if (banners.size() > 1) {
            startAutoScroll(vpBanner);
        }
    }

    private void setupDefaultBanners(ViewPager2 vpBanner) {
        List<String> defaultUrls = Arrays.asList(
                "https://preview.qiantucdn.com/auto_machine/20231019/46d772bb-d956-43ad-9c9f-cdd320d87caa.png!qt_h320",
                "https://img2.baidu.com/it/u=3487252190,2163576535&fm=253&fmt=auto&app=138&f=JPEG?w=500&h=255"
        );

        BannerAdapter defaultAdapter = new BannerAdapter(requireContext(), defaultUrls);
        currentBannerAdapter = defaultAdapter;
        vpBanner.setAdapter(defaultAdapter);

        if (defaultUrls.size() > 1) {
            startAutoScroll(vpBanner);
        }
    }

    private void startAutoScroll(ViewPager2 vpBanner) {
        stopAutoScroll();

        autoScrollHandler = new Handler();
        autoScrollRunnable = new Runnable() {
            @Override
            public void run() {
                if (vpBanner != null && vpBanner.getAdapter() != null && isAdded()) {
                    int itemCount = vpBanner.getAdapter().getItemCount();
                    if (itemCount > 1) {
                        isAutoScrolling = true;
                        if (currentBannerAdapter != null) {
                            currentBannerAdapter.setUserInteracting(false);
                        }

                        int nextItem = (vpBanner.getCurrentItem() + 1) % itemCount;
                        vpBanner.setCurrentItem(nextItem, true);

                        vpBanner.postDelayed(() -> {
                            isAutoScrolling = false;
                        }, 500);

                        autoScrollHandler.postDelayed(this, 3000);
                    }
                }
            }
        };
        autoScrollHandler.postDelayed(autoScrollRunnable, 3000);
    }

    private void startAutoScrollDelayed() {
        if (autoScrollHandler != null && autoScrollRunnable != null) {
            autoScrollHandler.postDelayed(autoScrollRunnable, 2000);
        }
    }

    private void stopAutoScroll() {
        if (autoScrollRunnable != null && autoScrollHandler != null) {
            autoScrollHandler.removeCallbacks(autoScrollRunnable);
        }
        isAutoScrolling = false;
    }

    private void fetchUserPoint() {
        if (isPointLoading) return;
        isPointLoading = true;

        String userId = UserUtils.loadUserId(requireContext());
        if (userId == null) {
            isPointLoading = false;
            return;
        }

        OkHttpClient client = new OkHttpClient();
        String url = ApiConfig.API_USER_POINT_ACCOUNT + "?userId=" + userId;
        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                isPointLoading = false;
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (response.isSuccessful() && getActivity() != null) {
                        String body = response.body().string();
                        try {
                            JSONObject obj = new JSONObject(body);
                            final int point = obj.optInt("totalPoint", 0);
                            requireActivity().runOnUiThread(() -> {
                                if (tvPoint != null) {
                                    tvPoint.setText("当前积分：" + point);
                                }
                            });
                        } catch (Exception e) {
                            requireActivity().runOnUiThread(() -> {
                                if (tvPoint != null) {
                                    tvPoint.setText("当前积分：0");
                                }
                            });
                        }
                    }
                } finally {
                    response.close();
                    isPointLoading = false;
                }
            }
        });
    }

    private void fetchPointRules() {
        if (isPointRulesLoading) return;
        isPointRulesLoading = true;

        OkHttpClient client = new OkHttpClient();
        String url = ApiConfig.API_POINT_RULES;
        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "获取积分规则失败: " + e.getMessage());
                isPointRulesLoading = false;
            }

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
                            Log.d(TAG, "积分规则加载完成，尝试刷新UI");
                            tryRefreshTaskUI();
                        } catch (Exception e) {
                            Log.e(TAG, "解析积分规则失败: " + e.getMessage());
                        }
                    }
                } finally {
                    response.close();
                    isPointRulesLoading = false;
                }
            }
        });

        fetchNoticeContent();
    }

    private void fetchTasks() {
        if (isTasksLoading) return;
        isTasksLoading = true;

        String userId = UserUtils.loadUserId(requireContext());
        if (userId == null) {
            isTasksLoading = false;
            return;
        }

        String url = ApiConfig.API_USER_TASK + "?userId=" + userId;
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "fetchTasks failed: " + e.getMessage());
                isTasksLoading = false;
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
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
                        Log.d(TAG, "任务数据加载完成，尝试刷新UI");
                        tryRefreshTaskUI();
                    }
                } finally {
                    response.close();
                    isTasksLoading = false;
                    // 检查是否可以停止刷新动画
                    checkAndStopRefresh();
                }
            }
        });
    }

    private void tryRefreshTaskUI() {
        Log.d(TAG, "tryRefreshTaskUI - tasksLoaded: " + tasksLoaded + ", pointRulesLoaded: " + pointRulesLoaded + ", isAdded: " + isAdded());

        if (tasksLoaded && pointRulesLoaded && getActivity() != null && isAdded()) {
            requireActivity().runOnUiThread(() -> {
                Log.d(TAG, "开始刷新任务UI，userTaskList.size = " + (userTaskList != null ? userTaskList.size() : "null"));

                if (userTaskList == null || userTaskList.isEmpty()) {
                    Log.w(TAG, "任务列表为空，隐藏所有任务卡片");
                    cardLearnVideo.setVisibility(View.GONE);
                    if (adapter != null) {
                        adapter.setTasks(Collections.emptyList());
                    }
                    return;
                }

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

                Log.d(TAG, "videoTask: " + (videoTask != null ? videoTask.getTaskName() : "null"));
                Log.d(TAG, "showTasks.size: " + showTasks.size());

                // 处理视频任务卡片
                if (videoTask != null) {
                    cardLearnVideo.setVisibility(View.VISIBLE);
                    TextView tvTitle = cardLearnVideo.findViewById(R.id.tv_learn_video_title);
                    if (tvTitle != null) {
                        tvTitle.setTextSize(26);
                        tvTitle.setText(videoTask.getTaskName());
                    }
                    Log.d(TAG, "视频卡片已显示: " + videoTask.getTaskName());
                } else {
                    cardLearnVideo.setVisibility(View.GONE);
                    Log.d(TAG, "没有视频任务，隐藏视频卡片");
                }

                // 处理其他任务
                if (adapter != null) {
                    adapter.setTaskPointRuleMap(new HashMap<>(taskPointRuleMap));
                    adapter.setTasks(showTasks);
                    Log.d(TAG, "RecyclerView适配器已更新，任务数量: " + showTasks.size());

                    // 强制通知适配器数据已更改
                    adapter.notifyDataSetChanged();
                } else {
                    Log.w(TAG, "适配器为null，无法更新任务");
                }

                // 所有主要数据加载完成后，停止刷新动画
                if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
                    swipeRefreshLayout.setRefreshing(false);
                    Log.d(TAG, "数据刷新完成，停止刷新动画");
                }
            });
        } else {
            Log.d(TAG, "UI刷新条件不满足，等待后续调用");
        }
    }

    // 修改后的通知获取方法，添加缓存机制和防缓存策略
    private void fetchNoticeContent() {
        UserAccount userAccount = UserUtils.getUserAccount(requireContext());
        if (userAccount == null || userAccount.getProjectId() == null || userAccount.getTeamId() == null) {
            Log.w(TAG, "用户账户信息不完整，设置默认通知");
            setDefaultNotice("欢迎使用远程护理系统");
            return;
        }

        String projectId = userAccount.getProjectId();
        String teamId = userAccount.getTeamId();

        OkHttpClient client = new OkHttpClient();
        String noticeUrl = ApiConfig.API_GET_NOTICE +
                "?projectId=" + projectId +
                "&teamId=" + teamId +
                "&t=" + System.currentTimeMillis(); // 添加时间戳防止HTTP缓存

        Log.d(TAG, "通知请求URL: " + noticeUrl);

        Request noticeRequest = new Request.Builder()
                .url(noticeUrl)
                .addHeader("Cache-Control", "no-cache") // 禁用HTTP缓存
                .build();

        client.newCall(noticeRequest).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "获取通知失败: " + e.getMessage());
                setDefaultNotice("（网络错误，无法获取通知）");
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (response.isSuccessful() && getActivity() != null) {
                        String noticeBody = response.body().string();
                        Log.d(TAG, "通知响应成功，数据长度: " + noticeBody.length());
                        Log.d(TAG, "通知原始数据: " + noticeBody);

                        try {
                            JSONArray noticeArray = new JSONArray(noticeBody);
                            String displayNotice = processNoticeList(noticeArray);
                            Log.d(TAG, "处理后的通知内容: " + displayNotice);

                            // 更新缓存和时间戳
                            cachedNoticeContent = displayNotice;
                            noticeLastUpdateTime = System.currentTimeMillis();
                            Log.d(TAG, "通知缓存已更新，时间戳: " + noticeLastUpdateTime);

                            requireActivity().runOnUiThread(() -> {
                                if (tvNotice != null) {
                                    tvNotice.setText(displayNotice);
                                    Log.d(TAG, "通知已设置到TextView: " + displayNotice);
                                } else {
                                    Log.e(TAG, "tvNotice为null，无法设置通知");
                                }
                            });

                        } catch (Exception e) {
                            Log.e(TAG, "解析通知数据失败: " + e.getMessage());
                            setDefaultNotice("（数据解析错误）");
                        }
                    } else {
                        Log.w(TAG, "获取通知失败: " + response.code() + " " + response.message());
                        setDefaultNotice("（服务器错误）");
                    }
                } finally {
                    response.close();
                }
            }
        });
    }

    private void setDefaultNotice(String notice) {
        Log.d(TAG, "设置默认通知: " + notice);

        // 缓存默认通知和时间戳
        cachedNoticeContent = notice;
        noticeLastUpdateTime = System.currentTimeMillis();

        if (getActivity() != null) {
            requireActivity().runOnUiThread(() -> {
                if (tvNotice != null) {
                    tvNotice.setText(notice);
                    Log.d(TAG, "默认通知已设置: " + notice);
                } else {
                    Log.e(TAG, "tvNotice为null，无法设置默认通知");
                }
            });
        }
    }

    private String processNoticeList(JSONArray noticeArray) {
        if (noticeArray == null || noticeArray.length() == 0) {
            return "（暂无新通知）";
        }

        List<String> noticeTexts = new ArrayList<>();

        for (int i = 0; i < noticeArray.length(); i++) {
            try {
                JSONObject noticeObj = noticeArray.getJSONObject(i);
                String noticeText = noticeObj.optString("noticeText");

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

        if (noticeTexts.size() == 1) {
            return noticeTexts.get(0);
        }

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
    /**
     * 验证当前设备ID是否与保存的一致
     * 如果不一致，提示用户重新登录
     */
    private void verifyDeviceConsistency() {
        Log.d(TAG, "开始验证设备一致性");

        UserAccount userAccount = UserUtils.getUserAccount(requireContext());
        if (userAccount == null) {
            Log.d(TAG, "用户未登录，跳过设备验证");
            return;
        }

        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String savedDeviceId = prefs.getString(KEY_DEVICE_ID, "");
        boolean deviceActivated = prefs.getBoolean(KEY_DEVICE_ACTIVATED, false);
        String currentDeviceId = getCurrentDeviceId();

        Log.d(TAG, "设备ID验证 - 保存的设备ID: " + savedDeviceId);
        Log.d(TAG, "设备ID验证 - 当前设备ID: " + currentDeviceId);
        Log.d(TAG, "设备ID验证 - 设备已激活: " + deviceActivated);

        // 如果没有保存的设备ID，或者设备未激活，跳过验证
        if (savedDeviceId.isEmpty() || !deviceActivated) {
            Log.d(TAG, "设备未激活或无保存的设备ID，跳过验证");
            return;
        }

        // 如果设备ID不一致，提示重新登录
        if (!savedDeviceId.equals(currentDeviceId)) {
            Log.w(TAG, "设备ID不一致，需要重新登录");
            showDeviceChangedDialog();
        } else {
            Log.d(TAG, "设备ID验证通过");
        }
    }

    /**
     * 显示设备更换提示对话框
     */
    private void showDeviceChangedDialog() {
        if (getActivity() == null || !isAdded()) {
            return;
        }

        requireActivity().runOnUiThread(() -> {
            new AlertDialog.Builder(requireContext())
                    .setTitle("设备安全提醒")
                    .setMessage("检测到您的设备信息已更改，为了账户安全，请重新登录验证身份。")
                    .setPositiveButton("立即登录", (dialog, which) -> {
                        dialog.dismiss();
                        // 清除本地用户信息和设备激活状态
                        clearUserDataAndNavigateToLogin();
                    })
                    .setNegativeButton("稍后处理", (dialog, which) -> {
                        dialog.dismiss();
                        // 用户选择稍后处理，但标记需要重新验证
                        markDeviceNeedReauth();
                    })
                    .setCancelable(false) // 不允许点击外部取消
                    .show();
        });
    }

    /**
     * 清除用户数据并跳转到登录页面
     */
    private void clearUserDataAndNavigateToLogin() {
        Log.d(TAG, "清除用户数据并跳转登录页面");

        // 清除SharedPreferences中的设备激活数据
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .remove(KEY_DEVICE_ID)
                .remove(KEY_LAST_LOGIN_PHONE)
                .putBoolean(KEY_DEVICE_ACTIVATED, false)
                .remove("device_need_reauth")
                .remove("reauth_remind_time")
                .apply();

        // 使用UserUtils的logout方法清除所有用户相关数据
        UserUtils.logout(requireContext());

        Log.d(TAG, "所有用户数据已清除");

        // 跳转到登录页面
        if (getActivity() instanceof MainActivity) {
            MainActivity mainActivity = (MainActivity) getActivity();
            // 切换到用户信息Tab，通常那里有登录入口
            mainActivity.switchToTab(R.id.navigation_myInfo);

            // 显示提示信息
            new Handler().postDelayed(() -> {
                if (getActivity() != null) {
                    InfoPopup.showError(requireContext(), "您的设备信息已更改，重新登录以确保账户安全");
                }
            }, 500);
        } else {
            // 如果不是MainActivity，尝试启动登录Fragment
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new UserLoginFragment())
                    .addToBackStack(null)
                    .commit();
        }
    }

    /**
     * 标记设备需要重新认证（用户选择稍后处理时）
     */
    private void markDeviceNeedReauth() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putBoolean("device_need_reauth", true)
                .putLong("reauth_remind_time", System.currentTimeMillis())
                .apply();
        Log.d(TAG, "已标记设备需要重新认证");
    }

    /**
     * 获取当前设备ID（与UserLoginFragment中的方法保持一致）
     */
    private String getCurrentDeviceId() {
        try {
            String androidId = android.provider.Settings.Secure.getString(
                    requireContext().getContentResolver(),
                    android.provider.Settings.Secure.ANDROID_ID);

            String manufacturer = android.os.Build.MANUFACTURER;
            String model = android.os.Build.MODEL;
            String serial = android.os.Build.SERIAL;

            String deviceInfo = manufacturer + "_" + model + "_" + serial;

            if (androidId != null && !androidId.isEmpty() && !"9774d56d682e549c".equals(androidId)) {
                return androidId + "_" + deviceInfo.hashCode();
            } else {
                return String.valueOf(deviceInfo.hashCode());
            }

        } catch (Exception e) {
            Log.e(TAG, "获取设备ID失败: " + e.getMessage());
            long timestamp = System.currentTimeMillis();
            int random = (int) (Math.random() * 10000);
            return "device_" + timestamp + "_" + random;
        }
    }

}