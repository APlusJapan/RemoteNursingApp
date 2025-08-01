package com.aplus.remotenursing;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.content.Context;
import android.content.SharedPreferences;
import com.aplus.remotenursing.adapters.VideoTaskDetailAdapter;
import com.aplus.remotenursing.models.VideoTaskDetail;
import com.aplus.remotenusing.common.ApiConfig;
import com.aplus.remotenusing.common.UserUtil;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.aplus.remotenursing.models.UserInfo;
import java.io.IOException;
import java.util.List;
import android.widget.Toast;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class VideoTaskDetailFragment extends Fragment {

    private static final int REQ_FULLSCREEN = 1001;
    private PlayerView playerView;
    private ExoPlayer player;
    private VideoTaskDetail currentItem;
    private RecyclerView rvOther;
    private final Gson gson = new Gson(); // 推荐全类唯一



    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_videotask_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        String userId = UserUtil.loadUserId(requireContext());
        if (userId == null) {
            Toast.makeText(requireContext(), "请先登录", Toast.LENGTH_SHORT).show();
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new UserLoginFragment())
                    .commit();
            return;
        }
        String vedioSeriesId = getArguments() != null ? getArguments().getString("vedioSeriesId") : null;

        rvOther = view.findViewById(R.id.rv_other_videos);
        rvOther.setLayoutManager(new LinearLayoutManager(requireContext()));

        fetchVideoList(userId, vedioSeriesId, videoList -> {
            if (videoList == null || videoList.isEmpty()) return;

            currentItem = videoList.get(0);
            playerView = view.findViewById(R.id.player_view);
            player = new ExoPlayer.Builder(requireContext()).build();
            playerView.setPlayer(player);
            playVideo(currentItem);

            rvOther.setAdapter(new VideoTaskDetailAdapter(videoList, item -> {
                currentItem = item;
                playVideo(item);
            }));

            view.findViewById(R.id.btn_fullscreen).setOnClickListener(v -> {
                long pos = player.getCurrentPosition();
                boolean playReady = player.getPlayWhenReady();
                player.pause();
                Intent it = new Intent(requireContext(), VedioFullscreenPlayerActivity.class)
                        .putExtra(VedioFullscreenPlayerActivity.EXTRA_URL, currentItem.getVedioURL())
                        .putExtra(VedioFullscreenPlayerActivity.EXTRA_START_POS, pos)
                        .putExtra(VedioFullscreenPlayerActivity.EXTRA_START_PLAYREADY, playReady);
                startActivityForResult(it, REQ_FULLSCREEN);
            });

            ((Button) view.findViewById(R.id.VideoDetailPage_btn_back))
                    .setOnClickListener(v -> requireActivity().onBackPressed());
        });
    }

    private interface VideoListCallback {
        void onResult(List<VideoTaskDetail> videoList);
    }

    private void fetchVideoList(String userId, String vedioSeriesId, VideoListCallback callback) {
        OkHttpClient client = new OkHttpClient();
        String url = ApiConfig.API_VIDEOS + "?userId=" + userId + "&vedioSeriesId=" + vedioSeriesId;
        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                if (getActivity() != null) getActivity().runOnUiThread(() -> callback.onResult(null));
            }
            @Override public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String json = response.body().string();
                    List<VideoTaskDetail> videoList = gson.fromJson(json, new TypeToken<List<VideoTaskDetail>>(){}.getType());
                    if (getActivity() != null) getActivity().runOnUiThread(() -> callback.onResult(videoList));
                } else {
                    if (getActivity() != null) getActivity().runOnUiThread(() -> callback.onResult(null));
                }
            }
        });
    }

    private void playVideo(VideoTaskDetail item) {
        player.setMediaItem(MediaItem.fromUri(item.getVedioURL()));
        player.prepare();
        player.play();
    }

    @Override
    public void onActivityResult(int req, int res, @Nullable Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_FULLSCREEN && res == Activity.RESULT_OK && data != null) {
            long pos = data.getLongExtra(
                    VedioFullscreenPlayerActivity.EXTRA_END_POS, 0L);
            boolean playReady = data.getBooleanExtra(
                    VedioFullscreenPlayerActivity.EXTRA_END_PLAYREADY, true);
            player.seekTo(pos);
            player.setPlayWhenReady(playReady);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (player != null) {
            player.release();
            player = null;
        }
    }
}
