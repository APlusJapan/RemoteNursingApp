package com.aplus.remotenursing;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;  // ← 改为 TextView

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.ui.PlayerView;

public class FullscreenPlayerActivity extends AppCompatActivity {

    public static final String EXTRA_URL             = "video_url";
    public static final String EXTRA_START_POS       = "start_position";
    public static final String EXTRA_START_PLAYREADY = "start_playWhenReady";
    public static final String EXTRA_END_POS         = "end_position";
    public static final String EXTRA_END_PLAYREADY   = "end_playWhenReady";

    private PlayerView playerView;
    private ExoPlayer   player;
    private TextView    btnExitFs;  // ← 这里改成 TextView

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1) 布局铺满到系统栏
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_fullscreen_player);

        // 2) 隐藏系统栏并开启 Immersive-Sticky
        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        );

        // 3) 拿控件
        playerView = findViewById(R.id.fs_player_view);
        btnExitFs  = findViewById(R.id.btn_exit_fullscreen);  // ← 这里也无需 cast 成 ImageButton

        // 4) 取 Intent 里的 URL、起始位置和播放状态
        Intent it = getIntent();
        String url = it.getStringExtra(EXTRA_URL);
        long   pos = it.getLongExtra(EXTRA_START_POS, 0L);
        boolean playWhenReady = it.getBooleanExtra(EXTRA_START_PLAYREADY, true);
        if (url == null) {
            finish();
            return;
        }

        // 5) 初始化 ExoPlayer
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        MediaItem item = MediaItem.fromUri(url);
        player.setMediaItem(item);
        player.prepare();

        // 6) 恢复到上次位置和状态
        player.seekTo(pos);
        player.setPlayWhenReady(playWhenReady);

        // 7) 退出全屏：把当前进度和状态回传
        btnExitFs.setOnClickListener((View v) -> {
            Intent data = new Intent()
                    .putExtra(EXTRA_END_POS, player.getCurrentPosition())
                    .putExtra(EXTRA_END_PLAYREADY, player.getPlayWhenReady());
            setResult(RESULT_OK, data);
            finish();
        });
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
