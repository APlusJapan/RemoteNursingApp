package com.aplus.remotenursing.manager;


import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.List;

/**
 * 增强的权限检查工具类 - 适配不同Android版本
 */
public class PermissionManager {
    private static final int REQUEST_STORAGE_PERMISSION = 1001;

    /**
     * 获取当前Android版本需要的存储权限
     */
    public static String[] getRequiredStoragePermissions() {
        List<String> permissions = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 使用新的媒体权限
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO);
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6-12 使用传统存储权限
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
        }

        return permissions.toArray(new String[0]);
    }

    /**
     * 检查存储权限
     */
    public static boolean hasStoragePermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 检查是否有所有文件访问权限
            return Environment.isExternalStorageManager();
        } else {
            // 检查传统权限
            String[] permissions = getRequiredStoragePermissions();
            for (String permission : permissions) {
                if (ContextCompat.checkSelfPermission(context, permission)
                        != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * 在Activity中请求存储权限
     */
    public static void requestStoragePermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 需要特殊处理
            requestManageExternalStoragePermission(activity);
        } else {
            // 传统权限请求
            String[] permissions = getRequiredStoragePermissions();
            ActivityCompat.requestPermissions(activity, permissions, REQUEST_STORAGE_PERMISSION);
        }
    }

    /**
     * 在Fragment中请求存储权限
     */
    public static void requestStoragePermission(Fragment fragment) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 需要特殊处理
            requestManageExternalStoragePermission(fragment.requireActivity());
        } else {
            // 传统权限请求
            String[] permissions = getRequiredStoragePermissions();
            fragment.requestPermissions(permissions, REQUEST_STORAGE_PERMISSION);
        }
    }

    /**
     * Android 11+ 请求管理外部存储权限
     */
    private static void requestManageExternalStoragePermission(Activity activity) {
        try {
            android.content.Intent intent = new android.content.Intent();
            intent.setAction(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            android.net.Uri uri = android.net.Uri.fromParts("package", activity.getPackageName(), null);
            intent.setData(uri);
            activity.startActivity(intent);
        } catch (Exception e) {
            // 如果无法打开设置，降级到传统权限请求
            String[] permissions = getRequiredStoragePermissions();
            ActivityCompat.requestPermissions(activity, permissions, REQUEST_STORAGE_PERMISSION);
        }
    }

    /**
     * 处理权限请求结果
     */
    public static boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.length > 0) {
                for (int result : grantResults) {
                    if (result != PackageManager.PERMISSION_GRANTED) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }

    /**
     * 检查网络权限（通常不需要运行时请求）
     */
    public static boolean hasNetworkPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.INTERNET)
                == PackageManager.PERMISSION_GRANTED;
    }
}