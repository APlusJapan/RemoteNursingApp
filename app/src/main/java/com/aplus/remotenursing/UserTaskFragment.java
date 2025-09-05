package com.aplus.remotenursing;

import android.annotation.SuppressLint;
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
    private boolean pointRulesLoaded = false;

    private boolean tasksLoaded = false;
    private List<UserTask> userTaskList = Collections.emptyList();
    private Map<String, Integer> taskPointRuleMap = new HashMap<>();
    private androidx.cardview.widget.CardView cardLearnVideo;
    private Runnable autoScrollRunnable;
    private Handler autoScrollHandler;
    private BannerAdapter currentBannerAdapter;
    private boolean isAutoScrolling = false;

    // 数据加载状态控制
    private boolean isDataLoaded = false;
    private boolean isInitialized = false;

    // 请求状态跟踪
    private boolean isBannerLoading = false;
    private boolean isTasksLoading = false;
    private boolean isPointLoading = false;
    private boolean isPointRulesLoading = false;

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
        Log.d(TAG, "isInitialized: " + isInitialized + ", isDataLoaded: " + isDataLoaded);

        if (!isInitialized) {
            Log.d(TAG, "首次初始化视图");
            initViews(view);
            setupRecyclerView();
            isInitialized = true;
        } else {
            Log.d(TAG, "视图已初始化，重新绑定");
            // 重新绑定视图组件
            TextView tvNickName = view.findViewById(R.id.tv_nick_name);
            tvPoint = view.findViewById(R.id.tv_point);
            tvNotice = view.findViewById(R.id.tv_notice);
            cardLearnVideo = view.findViewById(R.id.card_learn_video);
            swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout);

            setUserInfo(tvNickName);
            setupSwipeRefresh();
            setupRecyclerView();
        }

        // 只在第一次创建时加载数据
        if (!isDataLoaded) {
            Log.d(TAG, "第一次加载数据");
            loadInitialData();
            isDataLoaded = true;
        } else {
            Log.d(TAG, "数据已加载，跳过初始数据加载");
        }

        Log.d(TAG, "=== onViewCreated 完成 ===");
    }

    private void initViews(View view) {
        TextView tvNickName = view.findViewById(R.id.tv_nick_name);
        tvPoint = view.findViewById(R.id.tv_point);
        tvNotice = view.findViewById(R.id.tv_notice);
        cardLearnVideo = view.findViewById(R.id.card_learn_video);
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout);

        cardLearnVideo.setVisibility(View.GONE);
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

    private void setupRecyclerView() {
        if (rvTasks == null) {
            rvTasks = getView().findViewById(R.id.rv_tasks);
        }

        GridLayoutManager layoutManager = new GridLayoutManager(requireContext(), 2);
        rvTasks.setLayoutManager(layoutManager);

        if (adapter == null) {
            adapter = new UserTaskAdapter();
            adapter.setOnTaskClickListener(this);
        }
        rvTasks.setAdapter(adapter);
    }

    private void loadInitialData() {
        Log.d(TAG, "=== loadInitialData 开始 ===");
        ViewPager2 vpBanner = getView().findViewById(R.id.vp_notice_banner);

        // 并行加载各种数据，但避免重复请求
        if (!isBannerLoading) {
            Log.d(TAG, "开始加载Banner数据");
            fetchBannerData(vpBanner);
        } else {
            Log.d(TAG, "Banner正在加载中，跳过");
        }

        if (!isPointLoading) {
            Log.d(TAG, "开始加载积分数据");
            fetchUserPoint();
        } else {
            Log.d(TAG, "积分正在加载中，跳过");
        }

        if (!isPointRulesLoading) {
            Log.d(TAG, "开始加载积分规则数据");
            fetchPointRules();
        } else {
            Log.d(TAG, "积分规则正在加载中，跳过");
        }

        if (!isTasksLoading) {
            Log.d(TAG, "开始加载任务数据");
            fetchTasks();
        } else {
            Log.d(TAG, "任务正在加载中，跳过");
        }

        Log.d(TAG, "=== loadInitialData 调用完成 ===");
    }

    @Override
    public void onResume() {
        super.onResume();

        // 如果数据已加载但UI可能需要刷新（比如从其他页面返回）
        if (isDataLoaded) {
            // 刷新积分
            if (!isPointLoading) {
                refreshUserPointOnly();
            }

            // 检查并刷新UI显示
            refreshUIIfNeeded();
        } else {
            // 如果数据未加载，重新加载
            loadInitialData();
            isDataLoaded = true;
        }

        if (autoScrollRunnable != null && autoScrollHandler != null) {
            startAutoScrollDelayed();
        }
    }

    // 检查并刷新UI显示
    private void refreshUIIfNeeded() {
        if (getView() == null) return;

        // 检查RecyclerView是否有数据显示
        if (adapter != null && userTaskList != null && !userTaskList.isEmpty()) {
            // 如果adapter没有数据，重新设置
            if (adapter.getItemCount() == 0) {
                tryRefreshTaskUI();
            }
        }

        // 检查Banner是否正常显示
        ViewPager2 vpBanner = getView().findViewById(R.id.vp_notice_banner);
        if (vpBanner != null && vpBanner.getAdapter() == null) {
            if (!isBannerLoading) {
                fetchBannerData(vpBanner);
            }
        }

        // 检查用户信息显示
        TextView tvNickName = getView().findViewById(R.id.tv_nick_name);
        if (tvNickName != null && tvNickName.getText().toString().contains("未登录")) {
            setUserInfo(tvNickName);
        }
    }

    // 刷新所有数据（下拉刷新时调用）
    private void refreshAllData() {
        Log.d(TAG, "开始刷新所有数据");

        // 重置所有加载状态
        resetLoadingStates();

        // 重置数据状态
        pointRulesLoaded = false;
        tasksLoaded = false;
        userTaskList = Collections.emptyList();
        taskPointRuleMap.clear();

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

        // 重置状态，但保留初始化状态
        isDataLoaded = false;
        isBannerLoading = false;
        isTasksLoading = false;
        isPointLoading = false;
        isPointRulesLoading = false;
    }

    private void fetchBannerData(ViewPager2 vpBanner) {
        if (isBannerLoading) return;
        isBannerLoading = true;

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
        BannerActionManager actionManager = new BannerActionManager(requireContext());

        BannerAdapter adapter = BannerAdapter.createWithBanners(requireContext(), banners);
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
                        Log.d(TAG, "用户开始拖拽banner");
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
                            tryRefreshTaskUI();
                        } catch (Exception e) {}
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
        if (tasksLoaded && pointRulesLoaded && getActivity() != null) {
            requireActivity().runOnUiThread(() -> {
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
                if (videoTask != null) {
                    cardLearnVideo.setVisibility(View.VISIBLE);
                    TextView tvTitle = cardLearnVideo.findViewById(R.id.tv_learn_video_title);
                    tvTitle.setTextSize(26);
                    tvTitle.setText(videoTask.getTaskName());
                } else {
                    cardLearnVideo.setVisibility(View.GONE);
                }
                adapter.setTaskPointRuleMap(new HashMap<>(taskPointRuleMap));
                adapter.setTasks(showTasks);

                // 所有主要数据加载完成后，停止刷新动画
                if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
                    swipeRefreshLayout.setRefreshing(false);
                    Log.d(TAG, "数据刷新完成，停止刷新动画");
                }
            });
        }
    }

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
}