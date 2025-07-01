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
import androidx.recyclerview.widget.GridLayoutManager;
import com.aplus.remotenursing.adapters.TaskAdapter;
import com.aplus.remotenursing.models.UserTask;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class TaskFragment extends Fragment {

    private RecyclerView rvTasks;
    private TaskAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_task, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        rvTasks = view.findViewById(R.id.rv_tasks);
        rvTasks.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        adapter = new TaskAdapter();
        rvTasks.setAdapter(adapter);
        fetchTasks();
    }

    private void fetchTasks() {
        String userId = "U001";
        String url = "http://192.168.2.9:8080/api/usertask?userId="+userId;
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
}

