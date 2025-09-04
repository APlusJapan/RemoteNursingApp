package com.aplus.remotenursing;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
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
import com.aplus.remotenursing.models.UserAccount;
import com.aplus.remotenursing.models.UserTask;
import com.aplus.remotenursing.common.ApiConfig;
import com.aplus.remotenursing.common.UserUtils;
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

    private RecyclerView rvTasks;
    private UserTaskAdapter adapter;
    private TextView tvPoint, tvNotice;
    private boolean pointRulesLoaded = false;

    private boolean tasksLoaded = false;
    private List<UserTask> userTaskList = Collections.emptyList();
    private Map<String, Integer> taskPointRuleMap = new HashMap<>();
    private androidx.cardview.widget.CardView cardLearnVideo, cardDailyCheck;
    private TextView tvVideoHint, tvCheckHint;
    private Runnable autoScrollRunnable;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_user_task, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        // 积分区
        TextView tvNickName = view.findViewById(R.id.tv_nick_name);
        tvPoint = view.findViewById(R.id.tv_point);
        tvNotice = view.findViewById(R.id.tv_notice);

        // 找到功能区2个CardView
        cardLearnVideo = view.findViewById(R.id.card_learn_video);
        //cardDailyCheck = view.findViewById(R.id.card_daily_check);
        //tvCheckHint = view.findViewById(R.id.tv_check_hint);

        // 默认隐藏
        cardLearnVideo.setVisibility(View.GONE);
        //cardDailyCheck.setVisibility(View.GONE);

        // 登录状态
        UserAccount userAccount = UserUtils.getUserAccount(requireContext());
        if (userAccount != null && userAccount.getNickName() != null) {
            tvNickName.setText(userAccount.getNickName());
        } else {
            tvNickName.setText("未登录，请先完成登录。");
        }

        // 卡片点击事件
        cardLearnVideo.setOnClickListener(v -> {
            // 跳转到视频任务
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new VideoTaskFragment())
                    .addToBackStack(null)
                    .commit();
        });

        // 通知图片轮播区
        ViewPager2 vpBanner = view.findViewById(R.id.vp_notice_banner);
        List<String> bannerUrls = Arrays.asList(
                "https://preview.qiantucdn.com/auto_machine/20231019/46d772bb-d956-43ad-9c9f-cdd320d87caa.png!qt_h320",
                "https://img2.baidu.com/it/u=3487252190,2163576535&fm=253&fmt=auto&app=138&f=JPEG?w=500&h=255"
        );
        vpBanner.setAdapter(new BannerAdapter(requireContext(), bannerUrls));

        // 自动滚动轮播
        autoScrollRunnable = new Runnable() {
            @Override
            public void run() {
                int itemCount = bannerUrls.size();
                int nextItem = (vpBanner.getCurrentItem() + 1) % itemCount;
                vpBanner.setCurrentItem(nextItem, true);
                vpBanner.postDelayed(this, 3000); // 3秒
            }
        };
        // 启动自动滚动
        vpBanner.postDelayed(autoScrollRunnable, 3000);

        // 任务区RecyclerView
        rvTasks = view.findViewById(R.id.rv_tasks);
        GridLayoutManager layoutManager = new GridLayoutManager(requireContext(), 2);
        rvTasks.setLayoutManager(layoutManager);
        if (adapter == null) {
            adapter = new UserTaskAdapter();
            adapter.setOnTaskClickListener(this);
        }
        rvTasks.setAdapter(adapter);

        // 加载数据
        fetchUserPoint();
        fetchPointRules();
        fetchTasks();
    }

    @Override
    public void onResume() {
        super.onResume();
        fetchUserPoint();
        fetchPointRules();
        fetchTasks();
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
                            final int point = obj.optInt("availablePoint", 0);
                            requireActivity().runOnUiThread(() -> tvPoint.setText("当前总积分：" + point));
                        } catch (Exception e) {
                            requireActivity().runOnUiThread(() -> tvPoint.setText("当前总积分：0"));
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
                Log.e("UserTaskFragment", "fetchTasks failed: " + e.getMessage());
            }
            @Override public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (response.isSuccessful() && getActivity() != null) {
                        String json = response.body().string();
                        Log.d("UserTaskFragment", "fetchTasks返回: " + json);
                        Gson gson = new Gson();
                        List<UserTask> list = gson.fromJson(json, new TypeToken<List<UserTask>>(){}.getType());
                        Log.d("UserTaskFragment", "解析后list.size=" + (list != null ? list.size() : "null"));
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
                refreshTaskStat();
            });
        }
    }

    private void refreshTaskStat() {
        // 这个方法已简化，不再显示任务数统计
        // 任务数相关的控件已从布局中删除
    }

    /**
     * 获取通知内容的新方法
     */
    private void fetchNoticeContent() {
        // 获取用户账户信息以获取projectId和teamId
        UserAccount userAccount = UserUtils.getUserAccount(requireContext());
        if (userAccount == null || userAccount.getProjectId() == null || userAccount.getTeamId() == null) {
            // 如果无法获取项目和团队信息，设置默认通知
            if (getActivity() != null) {
                requireActivity().runOnUiThread(() -> {
                    if (tvNotice != null) {
                        tvNotice.setText("欢迎使用远程护理系统");
                    }
                });
            }
            return;
        }

        String projectId = userAccount.getProjectId();
        String teamId = userAccount.getTeamId();

        OkHttpClient client = new OkHttpClient();

        // 构建通知查询URL
        String noticeUrl = ApiConfig.API_GET_NOTICE +
                "?projectId=" + projectId +
                "&teamId=" + teamId;

        Log.d("UserTaskFragment", "通知请求URL: " + noticeUrl);

        Request noticeRequest = new Request.Builder().url(noticeUrl).build();

        client.newCall(noticeRequest).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("UserTaskFragment", "获取通知失败: " + e.getMessage());
                // 网络失败时设置默认通知
                if (getActivity() != null) {
                    requireActivity().runOnUiThread(() -> {
                        if (tvNotice != null) {
                            tvNotice.setText("欢迎使用远程护理系统");
                        }
                    });
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (response.isSuccessful() && getActivity() != null) {
                        String noticeBody = response.body().string();
                        Log.d("UserTaskFragment", "通知响应: " + noticeBody);

                        try {
                            JSONArray noticeArray = new JSONArray(noticeBody);

                            // 处理通知列表，选择要显示的通知
                            String displayNotice = processNoticeList(noticeArray);

                            // 在UI线程中更新通知显示
                            requireActivity().runOnUiThread(() -> {
                                if (tvNotice != null) {
                                    tvNotice.setText(displayNotice);
                                }
                            });

                        } catch (Exception e) {
                            Log.e("UserTaskFragment", "解析通知数据失败: " + e.getMessage());
                            // 解析失败时设置默认通知
                            requireActivity().runOnUiThread(() -> {
                                if (tvNotice != null) {
                                    tvNotice.setText("（暂无新通知）");
                                }
                            });
                        }
                    } else {
                        Log.w("UserTaskFragment", "获取通知响应不成功: " + response.code());
                        // 响应不成功时设置默认通知
                        if (getActivity() != null) {
                            requireActivity().runOnUiThread(() -> {
                                if (tvNotice != null) {
                                    tvNotice.setText("（暂无新通知）");
                                }
                            });
                        }
                    }
                } finally {
                    response.close();
                }
            }
        });
    }

    /**
     * 处理通知列表，选择要显示的通知内容
     * @param noticeArray 通知JSON数组
     * @return 要显示的通知文本
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
                Log.w("UserTaskFragment", "处理第" + i + "个通知时出错: " + e.getMessage());
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