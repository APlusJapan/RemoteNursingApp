package com.aplus.remotenursing;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.aplus.remotenursing.common.ApiConfig;
import com.aplus.remotenursing.common.UserUtils;
import com.aplus.remotenursing.common.VideoPlayUtils;
import com.aplus.remotenursing.manager.VideoCacheManager;
import com.aplus.remotenursing.models.VideoTaskDetail;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
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

    // 自动播放系列
    public static final String EXTRA_VIDEO_SERIES_ID     = "video_series_id";
    public static final String EXTRA_CURRENT_VIDEO_INDEX = "current_video_index";

    // 新增：首条视频如果从外层带了明确的 id，就用它（避免重复缓存）
    public static final String EXTRA_VIDEO_ID = "video_id";

    private PlayerView playerView;
    private ExoPlayer  player;
    private TextView   btnExitFs;

    private List<VideoTaskDetail> videoList;
    private int currentVideoIndex = 0;
    private String videoSeriesId;
    private final Gson gson = new Gson();
    private VideoCacheManager cacheManager;

    // 记录“从外层传入的首条视频”的 URL 和 ID，用于首条播放时优先使用
    private String initialUrlFromIntent;
    private String initialVideoIdFromIntent;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cacheManager = VideoCacheManager.getInstance(getApplicationContext());

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

        // 4) 取 Intent 参数
        Intent it = getIntent();
        initialVideoIdFromIntent = it.getStringExtra(EXTRA_VIDEO_ID); // 👈 记录首条传入的 id
        String url = it.getStringExtra(EXTRA_URL);
        initialUrlFromIntent = url;                                    // 👈 记录首条 URL
        long   pos = it.getLongExtra(EXTRA_START_POS, 0L);
        boolean playWhenReady = it.getBooleanExtra(EXTRA_START_PLAYREADY, true);

        videoSeriesId = it.getStringExtra(EXTRA_VIDEO_SERIES_ID);
        currentVideoIndex = it.getIntExtra(EXTRA_CURRENT_VIDEO_INDEX, 0);

        if (TextUtils.isEmpty(url)) {
            finish();
            return;
        }

        // 5) 初始化 ExoPlayer
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);

        // 自动播放监听
        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_ENDED) {
                    playNextVideo();
                }
            }
            @Override public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
                int cur = player.getCurrentMediaItemIndex();
                if (cur != C.INDEX_UNSET) {
                    maybeSwapToLocal(cur);
                }
            }
        });

        // 6) 加载视频列表并开始播放
        if (!TextUtils.isEmpty(videoSeriesId)) {
            loadVideoListAndPlay(url, pos, playWhenReady);
        } else {
            // 没有列表时，播放首条：会优先用 initialVideoIdFromIntent
            playVideo(url, /*explicitId*/ null, pos, playWhenReady);
        }

        // 7) 退出全屏
        btnExitFs.setOnClickListener((View v) -> {
            Intent data = new Intent()
                    .putExtra(EXTRA_END_POS, player.getCurrentPosition())
                    .putExtra(EXTRA_END_PLAYREADY, player.getPlayWhenReady())
                    .putExtra(EXTRA_CURRENT_VIDEO_INDEX, currentVideoIndex);
            setResult(RESULT_OK, data);
            finish();
        });
    }

    private void maybeSwapToLocal(int index) {
        if (player == null) return;
        if (index < 0 || index >= player.getMediaItemCount()) return;

        MediaItem cur = player.getMediaItemAt(index);
        if (cur == null || cur.localConfiguration == null) return;

        Uri uri = cur.localConfiguration.uri;
        if (uri == null) return;

        // 已经是本地文件就不动
        if ("file".equalsIgnoreCase(uri.getScheme())) return;

        // 取出 tag（优先），否则用 URL 反推 videoId（与下载端一致）
        String videoUrl = uri.toString();
        String videoId;
        Object tagObj = cur.localConfiguration.tag;
        if (tagObj instanceof VideoTag) {
            videoId = ((VideoTag) tagObj).id;
            videoUrl = ((VideoTag) tagObj).url;
        } else {
            videoId = videoIdFromUrl(videoUrl);
        }

        // 问缓存
        String localPath = VideoCacheManager
                .getInstance(getApplicationContext())
                .getLocalVideoPath(videoId, videoUrl);

        if (localPath == null) return; // 还没缓存好

        // 命中缓存：用相同配置但换成本地 file://
        Uri localUri = Uri.fromFile(new File(localPath));
        MediaItem localItem = cur.buildUpon().setUri(localUri).build();

        boolean isCurrent = index == player.getCurrentMediaItemIndex();
        long pos = isCurrent ? Math.max(0, player.getCurrentPosition()) : 0L;
        boolean playWhenReady = player.getPlayWhenReady();

        player.removeMediaItem(index);
        player.addMediaItem(index, localItem);

        if (isCurrent) {
            player.seekTo(index, pos);
            player.setPlayWhenReady(playWhenReady);
        }

        Log.d("VideoCache", "切换为本地播放: " + videoId + " -> " + localUri);
    }

    // 加载视频列表
    private void loadVideoListAndPlay(String initialUrl, long pos, boolean playWhenReady) {
        String userId = UserUtils.loadUserId(this);
        if (userId == null) {
            // 用户未登录，直接按单条播放逻辑
            playVideo(initialUrl, /*explicitId*/ null, pos, playWhenReady);
            return;
        }

        String url = ApiConfig.API_VIDEO_DETAIL_BY_SERIES_ID + videoSeriesId;
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> playVideo(initialUrl, /*explicitId*/ null, pos, playWhenReady));
            }
            @Override public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String json = response.body().string();
                    List<VideoTaskDetail> list = gson.fromJson(
                            json, new TypeToken<List<VideoTaskDetail>>(){}.getType());
                    runOnUiThread(() -> {
                        videoList = list;
                        // 列表拿到以后仍然按首条 URL 播放；
                        // 如果外层传了 videoId，playVideo 会优先用它
                        playVideo(initialUrl, /*explicitId*/ null, pos, playWhenReady);
                    });
                } else {
                    runOnUiThread(() -> playVideo(initialUrl, /*explicitId*/ null, pos, playWhenReady));
                }
            }
        });
    }

    /**
     * 对外旧签名：不改调用方
     */
    private void playVideo(String videoUrl, long pos, boolean playWhenReady) {
        playVideo(videoUrl, /*explicitId*/ null, pos, playWhenReady);
    }

    /**
     * 新核心：允许传入明确的 videoId；否则：
     *  - 如果是“首条且和 intent URL 一致”→ 用 intent 里带的 id
     *  - 其余情况 → 用 VideoPlayUtils.videoIdFromUrl(url)
     */
    private void playVideo(String videoUrl, String explicitVideoId, long pos, boolean playWhenReady) {
        if (player == null || TextUtils.isEmpty(videoUrl)) return;

        // 选 id（顺序：明确传入 → 与首条匹配的 intent id → URL 生成）
        String videoId = !TextUtils.isEmpty(explicitVideoId)
                ? explicitVideoId
                : ((!TextUtils.isEmpty(initialVideoIdFromIntent) && TextUtils.equals(videoUrl, initialUrlFromIntent))
                ? initialVideoIdFromIntent
                : videoIdFromUrl(videoUrl));

        // 本地优先
        Uri playable = resolvePlayableUri(videoUrl, videoId);

        // 建立含 mediaId + tag 的 MediaItem（tag 里保存“原始 URL”）
        MediaItem item = new MediaItem.Builder()
                .setUri(playable)
                .setMediaId(videoId)
                .setTag(new VideoTag(videoId, videoUrl))
                .build();

        player.setMediaItem(item);

        // 尝试把 index=0 替换为本地
        maybeSwapToLocal(0);

        player.prepare();
        player.seekTo(pos);
        player.setPlayWhenReady(playWhenReady);

        // 若当前仍是网络地址，后台开始缓存“当前视频”
        if (isNetworkUri(playable)) {
            VideoCacheManager.getInstance(this).downloadAndCacheVideo(
                    videoId, videoUrl,
                    new VideoCacheManager.DownloadCallback() {
                        @Override public void onStart(String id) {}
                        @Override public void onProgress(String id, int p) {}
                        @Override public void onSuccess(String id, String localPath) {
                            int cur = player.getCurrentMediaItemIndex();
                            if (cur != C.INDEX_UNSET) {
                                maybeSwapToLocal(cur);
                            }
                        }
                        @Override public void onError(String id, String err) {}
                    }
            );
        }

        // 预取“下一条”
        prefetchNextInSeries();
    }

    // 连播：优先用服务端给的 videoId，避免重复缓存
    private void playNextVideo() {
        if (videoList != null && !videoList.isEmpty()) {
            if (currentVideoIndex >= videoList.size() - 1) {
                currentVideoIndex = 0;
            } else {
                currentVideoIndex++;
            }
            VideoTaskDetail next = videoList.get(currentVideoIndex);
            if (next != null && !TextUtils.isEmpty(next.getVideoURL())) {
                playVideo(next.getVideoURL(), next.getVideoId(), 0, true); // 👈 带上 id
            }
        }
    }

    // —— 辅助方法 —— //

    /** 先问缓存，命中则返回 file://，否则返回网络 Uri（用同一套 videoId） */
    private Uri resolvePlayableUri(String videoUrl, String videoId) {
        try {
            String local = VideoCacheManager.getInstance(this).getLocalVideoPath(videoId, videoUrl);
            if (!TextUtils.isEmpty(local)) {
                return Uri.fromFile(new File(local));
            }
        } catch (Throwable ignore) {}
        return Uri.parse(videoUrl);
    }

    /** 预取“下一个”视频（系列场景），优先用服务端 id */
    private void prefetchNextInSeries() {
        if (videoList == null || videoList.isEmpty()) return;
        int nextIndex = (currentVideoIndex >= videoList.size() - 1) ? 0 : (currentVideoIndex + 1);
        VideoTaskDetail next = videoList.get(nextIndex);
        if (next == null || TextUtils.isEmpty(next.getVideoURL())) return;

        String nextUrl = next.getVideoURL();
        String nextId  = !TextUtils.isEmpty(next.getVideoId())
                ? next.getVideoId()
                : videoIdFromUrl(nextUrl);

        VideoCacheManager.getInstance(this).downloadAndCacheVideo(
                nextId, nextUrl,
                new VideoCacheManager.DownloadCallback() {
                    @Override public void onStart(String videoId) {}
                    @Override public void onProgress(String videoId, int progress) {}
                    @Override public void onSuccess(String videoId, String localPath) {}
                    @Override public void onError(String videoId, String error) {}
                }
        );
    }

    // 一个简单的 tag 类型（也可以用 Bundle）
    public static final class VideoTag {
        public final String id;
        public final String url;
        public VideoTag(String id, String url) { this.id = id; this.url = url; }
    }

    // 网络 Uri 判定
    private boolean isNetworkUri(Uri uri) {
        if (uri == null) return false;
        String s = uri.getScheme();
        return "http".equalsIgnoreCase(s) || "https".equalsIgnoreCase(s);
    }

    // 用 URL 生成一个稳定的 videoId（你没有显式 ID 时使用）
    private String videoIdFromUrl(String url) {
        return VideoPlayUtils.videoIdFromUrl(url);
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
