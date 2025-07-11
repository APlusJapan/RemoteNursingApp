package com.aplus.remotenursing;

import android.content.Context;
import android.content.SharedPreferences;
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

import com.aplus.remotenursing.adapters.VedioTaskAdapter;
import com.aplus.remotenursing.models.UserAccount;
import com.aplus.remotenursing.models.VedioTask;
import com.aplus.remotenusing.common.ApiConfig;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import com.aplus.remotenusing.common.UserUtil;
public class VideoTaskFragment extends Fragment implements VedioTaskAdapter.OnSeriesClickListener {

    private RecyclerView rvSeries;
    private VedioTaskAdapter adapter;
    private List<VedioTask> seriesList;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_videotask, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        view.findViewById(R.id.VideoListPage_btn_back)
                .setOnClickListener(v -> requireActivity().onBackPressed());
        rvSeries = view.findViewById(R.id.rv_series);
        rvSeries.setLayoutManager(new LinearLayoutManager(requireContext()));
        // 初始加载空adapter，防止空指针
        if (adapter == null) {
            adapter = new VedioTaskAdapter(seriesList, this);
            rvSeries.setAdapter(adapter);
        }
        fetchSeriesList();
    }

    private void fetchSeriesList() {
        String userId = UserUtil.loadUserId(requireContext());
        if (userId == null) {
            Toast.makeText(requireContext(), "请先登录", Toast.LENGTH_SHORT).show();
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new UserLoginFragment())
                    .commit();
            return;
        }

        String url = ApiConfig.API_SERIES + "?userId=" + userId;
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                e.printStackTrace();
            }
            @Override public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && getActivity() != null) {
                    String json = response.body().string();
                    Gson gson = new Gson();
                    List<VedioTask> list = gson.fromJson(json, new TypeToken<List<VedioTask>>(){}.getType());
                    getActivity().runOnUiThread(() -> {
                        seriesList = list;
                        adapter.setSeriesList(seriesList);
                        adapter.notifyDataSetChanged();
                    });
                }
            }
        });
    }
        @Override
    public void onSeriesClick(int position) {
        VedioTask selSeries = seriesList.get(position);
        VideoTaskDetailFragment detailFragment = new VideoTaskDetailFragment();
        Bundle args = new Bundle();
        args.putString("vedioSeriesId", selSeries.getVedioSeriesId());
        args.putString("vedioSeriesName", selSeries.getVedioSeriesName());
        detailFragment.setArguments(args);
        requireActivity()
                .getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, detailFragment)
                .addToBackStack(null)
                .commit();
    }
}
