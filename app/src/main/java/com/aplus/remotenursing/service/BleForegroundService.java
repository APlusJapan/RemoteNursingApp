package com.aplus.remotenursing.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.aplus.remotenursing.MainActivity;
import com.aplus.remotenursing.R;

/**
 * 前台 BLE 保活服务（无需 ServiceInfoCompat）
 * - 负责创建通知渠道并以前台模式运行
 * - 你可以在 startBleWork()/stopBleWork() 中挂接实际的 BLE 连接/保活逻辑
 */
public class BleForegroundService extends Service {

    public static final String ACTION_START = "com.aplus.remotenursing.service.BleForegroundService.START";
    public static final String ACTION_STOP  = "com.aplus.remotenursing.service.BleForegroundService.STOP";

    // 通知渠道与 ID
    private static final String CHANNEL_ID = "rn_ble";
    private static final String CHANNEL_NAME = "蓝牙连接服务";
    private static final int NOTI_ID = 10001;

    /** 便捷启动（从 Activity/Fragment 调用） */
    public static void start(Context context) {
        Intent i = new Intent(context, BleForegroundService.class).setAction(ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(i);
        } else {
            context.startService(i);
        }
    }

    /** 便捷停止 */
    public static void stop(Context context) {
        Intent i = new Intent(context, BleForegroundService.class).setAction(ACTION_STOP);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(i);
        } else {
            context.startService(i);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        final String action = intent != null ? intent.getAction() : ACTION_START;

        if (ACTION_STOP.equals(action)) {
            stopBleWork();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        // 默认走启动前台服务
        Notification notification = buildNotification(/*connected*/ false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // API 29+
            int types = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                    | ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
            startForeground(NOTI_ID, notification, types);
        } else {
            startForeground(NOTI_ID, notification);
        }

        startBleWork(); // 开始你的 BLE 保活/连接逻辑
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopBleWork();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        // 本服务仅做前台保活，通常不需要绑定
        return null;
    }

    // ========================= 你的逻辑钩子 =========================

    /** 在这里启动或恢复 BLE 扫描、连接、心跳、数据同步等 */
    private void startBleWork() {
        // TODO: 挂接你的 BLE 代码（例如连接指定型号手表）
        // 连接成功后，可调用 updateNotification(true) 把文案改为“已连接”
        // updateNotification(true);
    }

    /** 在这里停止 BLE 相关工作，释放资源 */
    private void stopBleWork() {
        // TODO: 断开连接、关闭扫描、释放 GATT 等
    }

    // ========================= 通知相关 =========================

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW
            );
            ch.setDescription("用于保持与健康手表的蓝牙连接");
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification(boolean connected) {
        // 点击通知回到主界面
        int flags = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent contentIntent = PendingIntent.getActivity(
                this, 0,
                new Intent(this, MainActivity.class),
                flags
        );

        // 小图标：优先用 ic_stat_bluetooth，没有就用应用图标兜底
        int smallIcon = R.drawable.ic_stat_bluetooth;
        try {
            getResources().getDrawable(smallIcon, null);
        } catch (Throwable t) {
            smallIcon = R.mipmap.ic_launcher;
        }

        String title = connected ? "健康手表已连接" : "正在保持蓝牙连接";
        String text  = connected ? "远程护理：设备连接正常" : "远程护理：正在连接/维持连接…";

        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(smallIcon)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        // 可选：添加“停止”动作
        PendingIntent stopPi = PendingIntent.getService(
                this, 1,
                new Intent(this, BleForegroundService.class).setAction(ACTION_STOP),
                flags
        );
        b.addAction(0, "停止", stopPi);

        return b.build();
    }

    /** 连接状态变化时可调用，更新通知文案 */
    public void updateNotification(boolean connected) {
        Notification n = buildNotification(connected);
        // 前台服务运行期间，直接更新同一个通知 ID
        startForeground(NOTI_ID, n);
    }
}
