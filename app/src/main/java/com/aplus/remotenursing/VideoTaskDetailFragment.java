package com.aplus.remotenursing;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

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
import com.google.android.exoplayer2.Player;
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
    private List<VideoTaskDetail> videoList;
    private int currentVideoIndex = 0;
    private RecyclerView rvOther;
    private VideoTaskDetailAdapter adapter;
    private TextView tvSeriesTitle;
    private final Gson gson = new Gson();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_video_task_detail, container, false);
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

        String videoSeriesId = getArguments() != null ? getArguments().getString("videoSeriesId") : null;
        String videoSeriesName = getArguments() != null ? getArguments().getString("videoSeriesName") : "视频系列";

        // 初始化控件
        rvOther = view.findViewById(R.id.rv_other_videos);
        rvOther.setLayoutManager(new LinearLayoutManager(requireContext()));

        tvSeriesTitle = view.findViewById(R.id.tv_more_series);
        if (tvSeriesTitle != null) {
            tvSeriesTitle.setText("更多 " + videoSeriesName + " 视频");
        }

        fetchVideoList(userId, videoSeriesId, videoList -> {
            if (videoList == null || videoList.isEmpty()) return;

            this.videoList = videoList;
            currentVideoIndex = 0;
            currentItem = videoList.get(0);

            playerView = view.findViewById(R.id.player_view);
            player = new ExoPlayer.Builder(requireContext()).build();
            playerView.setPlayer(player);

            // 添加播放状态监听器，实现自动播放下一个视频
            player.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    if (playbackState == Player.STATE_ENDED) {
                        // 播放结束，自动播放下一个视频
                        playNextVideo();
                    }
                }
            });

            playVideo(currentItem);

            // 创建适配器并设置当前播放项
            adapter = new VideoTaskDetailAdapter(videoList, item -> {
                // 找到点击的视频在列表中的位置
                currentVideoIndex = videoList.indexOf(item);
                currentItem = item;
                adapter.setCurrentPlayingItem(item); // 更新播放状态
                scrollToCurrentVideo(); // 滚动到当前视频
                playVideo(item);
            });
            rvOther.setAdapter(adapter);

            // 设置初始播放状态
            adapter.setCurrentPlayingItem(currentItem);

            // 全屏按钮点击事件
            view.findViewById(R.id.btn_fullscreen).setOnClickListener(v -> {
                long pos = player.getCurrentPosition();
                boolean playReady = player.getPlayWhenReady();
                player.pause();
                Intent it = new Intent(requireContext(), VideoFullscreenPlayerActivity.class)
                        .putExtra(VideoFullscreenPlayerActivity.EXTRA_URL, currentItem.getVideoURL())
                        .putExtra(VideoFullscreenPlayerActivity.EXTRA_START_POS, pos)
                        .putExtra(VideoFullscreenPlayerActivity.EXTRA_START_PLAYREADY, playReady)
                        .putExtra(VideoFullscreenPlayerActivity.EXTRA_VIDEO_SERIES_ID, videoSeriesId)  // 传递系列ID
                        .putExtra(VideoFullscreenPlayerActivity.EXTRA_CURRENT_VIDEO_INDEX, currentVideoIndex); // 传递当前索引
                startActivityForResult(it, REQ_FULLSCREEN);
            });

            // 返回按钮点击事件 - 修复：使用ImageButton而不是Button
            ImageButton backButton = view.findViewById(R.id.VideoDetailPage_btn_back);
            if (backButton != null) {
                backButton.setOnClickListener(v -> requireActivity().onBackPressed());
            }
        });
    }

    private interface VideoListCallback {
        void onResult(List<VideoTaskDetail> videoList);
    }

    private void fetchVideoList(String userId, String videoSeriesId, VideoListCallback callback) {
        OkHttpClient client = new OkHttpClient();
        String url = ApiConfig.API_VIDEO_DETAIL_BY_SERIES_ID + videoSeriesId;
        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> callback.onResult(null));
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String json = response.body().string();
                    List<VideoTaskDetail> videoList = gson.fromJson(json, new TypeToken<List<VideoTaskDetail>>(){}.getType());
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> callback.onResult(videoList));
                    }
                } else {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> callback.onResult(null));
                    }
                }
            }
        });
    }

    private void playVideo(VideoTaskDetail item) {
        if (player != null && item != null && item.getVideoURL() != null) {
            player.setMediaItem(MediaItem.fromUri(item.getVideoURL()));
            player.prepare();
            player.play();
        }
    }

    // 新增：播放下一个视频
    private void playNextVideo() {
        if (videoList != null && videoList.size() > 0) {
            // 如果是最后一个视频，回到第一个（循环播放）
            if (currentVideoIndex >= videoList.size() - 1) {
                currentVideoIndex = 0;
            } else {
                currentVideoIndex++;
            }

            currentItem = videoList.get(currentVideoIndex);

            // 更新适配器中的播放状态
            if (adapter != null) {
                adapter.setCurrentPlayingItem(currentItem);
            }

            // 滚动到当前播放的视频，使其居中显示
            scrollToCurrentVideo();

            playVideo(currentItem);
        }
    }

    // 新增：滚动到当前播放的视频
    private void scrollToCurrentVideo() {
        if (rvOther != null && currentVideoIndex >= 0) {
            // 延迟一点执行滚动，确保适配器已更新
            rvOther.postDelayed(() -> {
                LinearLayoutManager layoutManager = (LinearLayoutManager) rvOther.getLayoutManager();
                if (layoutManager != null) {
                    // 滚动到当前项目，使其尽可能居中
                    layoutManager.scrollToPositionWithOffset(currentVideoIndex, 100);
                }
            }, 100);
        }
    }

    @Override
    public void onActivityResult(int req, int res, @Nullable Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_FULLSCREEN && res == Activity.RESULT_OK && data != null) {
            long pos = data.getLongExtra(
                    VideoFullscreenPlayerActivity.EXTRA_END_POS, 0L);
            boolean playReady = data.getBooleanExtra(
                    VideoFullscreenPlayerActivity.EXTRA_END_PLAYREADY, true);
            int returnedIndex = data.getIntExtra(
                    VideoFullscreenPlayerActivity.EXTRA_CURRENT_VIDEO_INDEX, currentVideoIndex);

            // 如果全屏播放时切换了视频，需要同步状态
            if (returnedIndex != currentVideoIndex && videoList != null && returnedIndex < videoList.size()) {
                currentVideoIndex = returnedIndex;
                currentItem = videoList.get(currentVideoIndex);
                if (adapter != null) {
                    adapter.setCurrentPlayingItem(currentItem);
                }
                scrollToCurrentVideo();

                // 加载新的视频
                if (player != null) {
                    player.setMediaItem(MediaItem.fromUri(currentItem.getVideoURL()));
                    player.prepare();
                }
            }

            if (player != null) {
                player.seekTo(pos);
                player.setPlayWhenReady(playReady);
            }
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