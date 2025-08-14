package com.aplus.remotenursing.helper;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import com.aplus.remotenursing.R;

public class NotificationHelper {
    public static final String CH_BLE = "ble_status";
    public static final String CH_SYNC = "data_sync";

    public static void ensureChannels(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = ctx.getSystemService(NotificationManager.class);
            if (nm.getNotificationChannel(CH_BLE) == null) {
                nm.createNotificationChannel(new NotificationChannel(
                        CH_BLE, "设备连接", NotificationManager.IMPORTANCE_LOW));
            }
            if (nm.getNotificationChannel(CH_SYNC) == null) {
                nm.createNotificationChannel(new NotificationChannel(
                        CH_SYNC, "数据同步/下载", NotificationManager.IMPORTANCE_LOW));
            }
        }
    }

    public static Notification buildBleForegroundNotification(Context ctx, String text) {
        return new NotificationCompat.Builder(ctx, CH_BLE)
                .setSmallIcon(R.drawable.ic_stat_play) // 准备一个小图标
                .setContentTitle("康复手表连接")
                .setContentText(text == null ? "正在连接/保持连接…" : text)
                .setOngoing(true)
                .build();
    }

    public static Notification buildSyncForegroundNotification(Context ctx, String text) {
        return new NotificationCompat.Builder(ctx, CH_SYNC)
                .setSmallIcon(R.drawable.ic_stat_play)
                .setContentTitle("数据同步")
                .setContentText(text == null ? "正在同步/下载…" : text)
                .setOngoing(true)
                .build();
    }
}
