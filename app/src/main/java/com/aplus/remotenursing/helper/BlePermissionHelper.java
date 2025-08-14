package com.aplus.remotenursing.helper;

import android.app.Activity;
import android.content.Context;
import android.location.LocationManager;
import android.os.Build;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.provider.Settings;

public class BlePermissionHelper {
    public static final int REQ_BLE_PERMS = 2001;
    public static final int REQ_POST_NOTI  = 2002;

    public static boolean hasBlePermissions(Context ctx) {
        if (Build.VERSION.SDK_INT >= 31) {
            return ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.BLUETOOTH_SCAN)    == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        } else {
            // <=30 扫描需定位权限（若你需要扫描）
            return ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }

    public static void requestBlePermissionsWithPrePrompt(Activity act, Runnable onCancel) {
        new AlertDialog.Builder(act)
                .setTitle("蓝牙权限申请")
                .setMessage("用于搜索并连接您的康复手表，以同步训练数据。拒绝后将无法搜索/连接设备，但不影响观看视频训练。")
                .setPositiveButton("好，去授权", (d, w) -> requestBlePermissions(act))
                .setNegativeButton("暂不", (d, w) -> { if (onCancel != null) onCancel.run(); })
                .show();
    }

    public static void requestBlePermissions(Activity act) {
        if (Build.VERSION.SDK_INT >= 31) {
            ActivityCompat.requestPermissions(act, new String[] {
                    android.Manifest.permission.BLUETOOTH_SCAN,
                    android.Manifest.permission.BLUETOOTH_CONNECT
            }, REQ_BLE_PERMS);
        } else {
            ActivityCompat.requestPermissions(act, new String[] {
                    android.Manifest.permission.ACCESS_FINE_LOCATION
            }, REQ_BLE_PERMS);
        }
    }

    // <= Android 30：定位开关需打开，否则扫描不到设备
    public static boolean isLocationEnabled(Context ctx) {
        if (Build.VERSION.SDK_INT >= 28) {
            LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
            return lm != null && lm.isLocationEnabled();
        } else {
            try {
                return Settings.Secure.getInt(ctx.getContentResolver(), Settings.Secure.LOCATION_MODE) != Settings.Secure.LOCATION_MODE_OFF;
            } catch (Settings.SettingNotFoundException e) {
                return false;
            }
        }
    }

    public static void promptEnableLocation(Activity act) {
        new AlertDialog.Builder(act)
                .setTitle("开启定位服务")
                .setMessage("为搜索 BLE 设备，请开启系统定位服务（Android 限制）。")
                .setPositiveButton("去设置", (d, w) -> {
                    act.startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // Android 13+ 通知权限（用于前台服务的常驻通知/连接提示）
    public static boolean hasPostNotifications(Context ctx) {
        if (Build.VERSION.SDK_INT >= 33) {
            return ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    public static void requestPostNotificationsWithPrePrompt(Activity act) {
        if (Build.VERSION.SDK_INT >= 33) {
            new AlertDialog.Builder(act)
                    .setTitle("通知权限申请")
                    .setMessage("我们将通过通知展示设备连接状态与同步进度，便于您及时了解。拒绝后不影响使用，但可能无法收到状态提醒。")
                    .setPositiveButton("好，去授权", (d, w) -> {
                        ActivityCompat.requestPermissions(act, new String[] {
                                android.Manifest.permission.POST_NOTIFICATIONS
                        }, REQ_POST_NOTI);
                    })
                    .setNegativeButton("暂不", null)
                    .show();
        }
    }
}
