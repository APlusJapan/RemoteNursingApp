package com.aplus.remotenursing;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.aplus.remotenursing.models.VideoTaskDetail;
import com.aplus.remotenusing.common.ApiConfig;
import com.aplus.remotenusing.common.UserUtil;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class VideoFullscreenPlayerActivity extends AppCompatActivity {

    public static final String EXTRA_URL             = "video_url";
    public static final String EXTRA_START_POS       = "video_start_pos";
    public static final String EXTRA_START_PLAYREADY = "video_start_playready";
    public static final String EXTRA_END_POS         = "video_end_pos";
    public static final String EXTRA_END_PLAYREADY   = "video_end_playready";

    // 新增：自动播放相关参数
    public static final String EXTRA_VIDEO_SERIES_ID = "video_series_id";
    public static final String EXTRA_CURRENT_VIDEO_INDEX = "current_video_index";

    private PlayerView playerView;
    private ExoPlayer   player;
    private TextView    btnExitFs;

    // 新增：自动播放相关变量
    private List<VideoTaskDetail> videoList;
    private int currentVideoIndex = 0;
    private String videoSeriesId;
    private final Gson gson = new Gson();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1) 布局铺满到系统栏
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_video_fullscreen_player);

        // 2) 隐藏系统栏并开启 Immersive-Sticky
        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        );

        // 3) 拿控件
        playerView = findViewById(R.id.fs_player_view);
        btnExitFs  = findViewById(R.id.btn_exit_fullscreen);

        // 4) 取 Intent 里的参数
        Intent it = getIntent();
        String url = it.getStringExtra(EXTRA_URL);
        long   pos = it.getLongExtra(EXTRA_START_POS, 0L);
        boolean playWhenReady = it.getBooleanExtra(EXTRA_START_PLAYREADY, true);

        // 新增：获取自动播放相关参数
        videoSeriesId = it.getStringExtra(EXTRA_VIDEO_SERIES_ID);
        currentVideoIndex = it.getIntExtra(EXTRA_CURRENT_VIDEO_INDEX, 0);

        if (url == null) {
            finish();
            return;
        }

        // 5) 初始化 ExoPlayer
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);

        // 新增：添加播放状态监听器，实现自动播放下一个视频
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_ENDED) {
                    // 播放结束，自动播放下一个视频
                    playNextVideo();
                }
            }
        });

        // 6) 加载视频列表并开始播放
        if (videoSeriesId != null) {
            loadVideoListAndPlay(url, pos, playWhenReady);
        } else {
            // 如果没有系列ID，直接播放单个视频
            playVideo(url, pos, playWhenReady);
        }

        // 7) 退出全屏：把当前进度和状态回传
        btnExitFs.setOnClickListener((View v) -> {
            Intent data = new Intent()
                    .putExtra(EXTRA_END_POS, player.getCurrentPosition())
                    .putExtra(EXTRA_END_PLAYREADY, player.getPlayWhenReady())
                    .putExtra(EXTRA_CURRENT_VIDEO_INDEX, currentVideoIndex); // 新增：返回当前视频索引
            setResult(RESULT_OK, data);
            finish();
        });
    }

    // 新增：加载视频列表并播放
    private void loadVideoListAndPlay(String initialUrl, long pos, boolean playWhenReady) {
        String userId = UserUtil.loadUserId(this);
        if (userId == null) {
            // 如果没有用户ID，直接播放单个视频
            playVideo(initialUrl, pos, playWhenReady);
            return;
        }

        String url = ApiConfig.API_VIDEO_DETAIL_BY_SERIES_ID + videoSeriesId;
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                // 如果加载失败，直接播放单个视频
                runOnUiThread(() -> playVideo(initialUrl, pos, playWhenReady));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String json = response.body().string();
                    List<VideoTaskDetail> list = gson.fromJson(json, new TypeToken<List<VideoTaskDetail>>(){}.getType());

                    runOnUiThread(() -> {
                        videoList = list;
                        // 开始播放当前视频
                        playVideo(initialUrl, pos, playWhenReady);
                    });
                } else {
                    runOnUiThread(() -> playVideo(initialUrl, pos, playWhenReady));
                }
            }
        });
    }

    // 新增：播放视频的通用方法
    private void playVideo(String videoUrl, long pos, boolean playWhenReady) {
        if (player != null && videoUrl != null) {
            MediaItem item = MediaItem.fromUri(videoUrl);
            player.setMediaItem(item);
            player.prepare();
            // 恢复到指定位置和状态
            player.seekTo(pos);
            player.setPlayWhenReady(playWhenReady);
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

            VideoTaskDetail nextVideo = videoList.get(currentVideoIndex);
            if (nextVideo != null && nextVideo.getVideoURL() != null) {
                // 播放下一个视频，从头开始
                playVideo(nextVideo.getVideoURL(), 0, true);
            }
        }
        // 如果没有视频列表，播放结束后不做任何操作（保持原有行为）
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) {
            player.release();
            player = null;
        }
    }
}