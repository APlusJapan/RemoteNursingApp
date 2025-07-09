package com.aplus.remotenursing;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.aplus.remotenursing.adapters.UserTaskAdapter;
import com.aplus.remotenursing.models.UserTask;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import android.content.Context;
import android.content.SharedPreferences;
import java.io.IOException;
import java.util.List;
import com.aplus.remotenursing.models.UserInfo;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class UserTaskFragment extends Fragment implements UserTaskAdapter.OnTaskClickListener{

    private RecyclerView rvTasks;
    private UserTaskAdapter adapter;

    private String loadUserId() {
        SharedPreferences sp = requireContext().getSharedPreferences("user_info", Context.MODE_PRIVATE);
        String json = sp.getString("data", null);
        if (json != null) {
            UserInfo info = new Gson().fromJson(json, UserInfo.class);
            return info.getUserId();
        }
        return null;
    }

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
        adapter = new UserTaskAdapter();
        adapter.setOnTaskClickListener(this);
        rvTasks.setAdapter(adapter);
        fetchTasks();
    }

    private void fetchTasks() {
        String userId = loadUserId();
        if (userId == null) return;
        String url = "http://192.168.2.9:8080/api/usertask?userId=" + userId;
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) { e.printStackTrace(); }
            @Override public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && getActivity() != null) {
                    String json = response.body().string();
                    Gson gson = new Gson();
                    List<UserTask> list = gson.fromJson(json, new TypeToken<List<UserTask>>(){}.getType());
                    java.util.Collections.sort(list,
                            (a, b) -> Integer.compare(a.getTask_order(), b.getTask_order()));
                    getActivity().runOnUiThread(() -> {
                        adapter.setTasks(list);
                        adapter.notifyDataSetChanged();
                    });
                }
            }
        });
    }
    @Override
    public void onTaskClick(UserTask task) {
        if ("01".equals(task.getTask_type())) {
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new VideoTaskFragment())
                    .addToBackStack(null)
                    .commit();
        } else if ("02".equals(task.getTask_type())) {
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new SmartwatchCheckupFragment())
                    .addToBackStack(null)
                    .commit();
        }
    }
}

