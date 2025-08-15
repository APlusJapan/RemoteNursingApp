package com.aplus.remotenursing;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.aplus.remotenursing.adapters.VideoTaskAdapter;
import com.aplus.remotenursing.models.UserAccount;
import com.aplus.remotenursing.models.VideoTask;
import com.aplus.remotenursing.common.ApiConfig;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.MediaType;


import com.aplus.remotenursing.common.UserUtils;
import com.aplus.remotenursing.manager.VideoPlayHistoryManager;
import com.aplus.remotenursing.models.VideoPlayRecord;
import com.aplus.remotenursing.models.VideoPlayBatchRequest;

public class VideoTaskFragment extends Fragment implements VideoTaskAdapter.OnSeriesClickListener {

    private RecyclerView rvSeries;
    private VideoTaskAdapter adapter;
    private List<VideoTask> seriesList;

    // 新增：播放时长管理
    private VideoPlayHistoryManager playHistoryManager;
    private Gson gson = new Gson();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_video_task, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        view.findViewById(R.id.VideoListPage_btn_back)
                .setOnClickListener(v -> requireActivity().onBackPressed());

        rvSeries = view.findViewById(R.id.rv_series);
        rvSeries.setLayoutManager(new LinearLayoutManager(requireContext()));

        // 初始化空列表，避免null问题
        seriesList = new ArrayList<>();
        adapter = new VideoTaskAdapter(seriesList, this);
        rvSeries.setAdapter(adapter);

        fetchSeriesList();

        // 新增：初始化播放历史管理器
        playHistoryManager = VideoPlayHistoryManager.getInstance(getContext());

    }

    private void fetchSeriesList() {
        String userId = UserUtils.loadUserId(requireContext());
        if (userId == null) {
            Toast.makeText(requireContext(), "请先登录", Toast.LENGTH_SHORT).show();
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new UserLoginFragment())
                    .commit();
            return;
        }

        String url = ApiConfig.API_VIDEO_TASK_BY_USER + userId;
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), "加载视频列表失败", Toast.LENGTH_SHORT).show();
                    });
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && getActivity() != null) {
                    String json = response.body().string();
                    Gson gson = new Gson();
                    List<VideoTask> list = gson.fromJson(json, new TypeToken<List<VideoTask>>(){}.getType());

                    getActivity().runOnUiThread(() -> {
                        if (list != null && !list.isEmpty()) {
                            seriesList.clear();
                            seriesList.addAll(list);
                            adapter.notifyDataSetChanged();
                        } else {
                            Toast.makeText(requireContext(), "暂无视频数据", Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            Toast.makeText(requireContext(), "加载视频列表失败", Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            }
        });
    }

    @Override
    public void onSeriesClick(int position) {
        if (seriesList != null && position < seriesList.size()) {
            VideoTask selSeries = seriesList.get(position);
            VideoTaskDetailFragment detailFragment = new VideoTaskDetailFragment();
            Bundle args = new Bundle();
            args.putString("videoSeriesId", selSeries.getVideoSeriesId());
            args.putString("videoSeriesName", selSeries.getVideoSeriesName());
            detailFragment.setArguments(args);
            requireActivity()
                    .getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, detailFragment)
                    .addToBackStack(null)
                    .commit();
        }
    }
    /**
     * 检查并上报历史播放数据
     */
    private void checkAndUploadHistoryPlayData() {
        String currentUserId = UserUtils.loadUserId(requireContext());
        if (currentUserId == null || currentUserId.isEmpty()) {
            Log.w("VideoTaskFragment", "用户未登录，跳过历史数据上报");
            return;
        }
        String adminId = "";
        UserAccount userAccount = UserUtils.getUserAccount(requireContext());
        adminId =  userAccount.getAdminId();
        Log.d("取得adminId：", adminId);

        List<VideoPlayRecord> historyData = playHistoryManager.getHistoryPlayData(currentUserId,adminId);
        if (!historyData.isEmpty()) {
            Log.d("VideoTaskFragment", "发现 " + historyData.size() + " 条历史播放数据，开始上报");
            uploadHistoryPlayData(currentUserId, historyData);
        } else {
            Log.d("VideoTaskFragment", "无历史播放数据需要上报");
        }
    }

    /**
     * 上报历史播放数据
     */
    private void uploadHistoryPlayData(String userId, List<VideoPlayRecord> historyData) {
        // 构建批量上报请求
        VideoPlayBatchRequest request = new VideoPlayBatchRequest();
        request.setUserId(userId);
        request.setRecords(historyData);

        // 转换为JSON
        String jsonData = gson.toJson(request);
        Log.d("VideoTaskFragment", "发送的JSON数据: " + jsonData);
        // 构建HTTP请求 - 请根据您的实际API地址修改
        String url = ApiConfig.API_VIDEO_HISTORY_RECORD_SAVE;

        MediaType JSON = MediaType.get("application/json; charset=utf-8");
        RequestBody body = RequestBody.create(jsonData, JSON);

        Request httpRequest = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        OkHttpClient client = new OkHttpClient();
        client.newCall(httpRequest).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("VideoTaskFragment", "历史播放数据上报失败", e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (Response r = response) { // 自动关闭响应体
                    if (r.isSuccessful()) {
                        playHistoryManager.clearHistoryData();
                        Log.d("VideoTaskFragment", "历史播放数据上报成功，本地数据已清理");
                    } else {
                        Log.e("VideoTaskFragment", "历史播放数据上报失败: " + r.message());
                    }
                }
            }
        });
    }
    // 添加新的 onResume 方法（如果已有则修改）：
    @Override
    public void onResume() {
        super.onResume();

        // 新增：检查并上报历史播放数据
        checkAndUploadHistoryPlayData();
    }
}