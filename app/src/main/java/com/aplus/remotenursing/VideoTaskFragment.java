package com.aplus.remotenursing;

import android.os.Bundle;
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
import okhttp3.Response;

import com.aplus.remotenursing.common.UserUtils;

public class VideoTaskFragment extends Fragment implements VideoTaskAdapter.OnSeriesClickListener {

    private RecyclerView rvSeries;
    private VideoTaskAdapter adapter;
    private List<VideoTask> seriesList;

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
}