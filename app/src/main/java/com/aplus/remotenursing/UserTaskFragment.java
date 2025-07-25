package com.aplus.remotenursing;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.aplus.remotenursing.adapters.UserTaskAdapter;
import com.aplus.remotenursing.models.UserTask;
import com.aplus.remotenusing.common.ApiConfig;
import com.aplus.remotenusing.common.UserUtil;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class UserTaskFragment extends Fragment implements UserTaskAdapter.OnTaskClickListener {

    private RecyclerView rvTasks;
    private UserTaskAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_usertask, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        rvTasks = view.findViewById(R.id.rv_tasks);
        rvTasks.setLayoutManager(new LinearLayoutManager(requireContext()));

        if (adapter == null) {
            adapter = new UserTaskAdapter();
            adapter.setOnTaskClickListener(this);
        }
        rvTasks.setAdapter(adapter);
        fetchTasks();
    }

    @Override
    public void onResume() {
        super.onResume();
        fetchTasks();
    }

    private void fetchTasks() {
        Log.d("UserTaskFragment", "fetchTasks(), start!!!");
        String userId = UserUtil.loadUserId(requireContext());
        Log.d("UserTaskFragment", "fetchTasks called, userId=" + userId);

        if (userId == null) return;
        String url = ApiConfig.API_USER_TASK + "?userId=" + userId;
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("UserTaskFragment", "fetchTasks failed: " + e.getMessage());
            }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && getActivity() != null) {
                    String json = response.body().string();
                    Log.d("UserTaskFragment", "API返回json=" + json);
                    Gson gson = new Gson();
                    List<UserTask> list = gson.fromJson(json, new TypeToken<List<UserTask>>(){}.getType());
                    Log.d("UserTaskFragment", "解析后任务数=" + (list == null ? "null" : list.size()));
                    if (list != null) {
                        java.util.Collections.sort(list,
                                (a, b) -> Integer.compare(a.getTask_order(), b.getTask_order()));
                    }
                    getActivity().runOnUiThread(() -> {
                        adapter.setTasks(list);
                        adapter.notifyDataSetChanged();
                    });
                } else {
                    Log.d("UserTaskFragment", "接口失败，code=" + response.code());
                }
            }
        });
    }

    @Override
    public void onTaskClick(UserTask task) {
        Log.d("UserTaskFragment", "onTaskClick called, type=" + task.getTask_type());
        if ("01".equals(task.getTask_type())) {
            Log.d("UserTaskFragment", "准备跳到VideoTaskFragment");
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new VideoTaskFragment())
                    .addToBackStack(null)
                    .commit();
        } else if ("02".equals(task.getTask_type())) {
            Log.d("UserTaskFragment", "准备跳到SmartwatchCheckupFragment");
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new SmartwatchCheckupFragment())
                    .addToBackStack(null)
                    .commit();
        } else if ("03".equals(task.getTask_type())) {
            Log.d("UserTaskFragment", "准备跳到DailyCheckInFragment");
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new DailyCheckInFragment())
                    .addToBackStack(null)
                    .commit();
        }
    }


}
