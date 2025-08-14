package com.aplus.remotenursing.manager;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

/**
 * 存储权限助手（面向“应用私有外部目录”默认不需要任何权限）
 */
public class PermissionManager {

    private static final int REQUEST_STORAGE_PERMISSION = 1001;

    /**
     * 你的缓存目录在 getExternalFilesDir(...)，不需要任何运行时权限。
     * 只有当你需要访问“系统媒体库/公共目录”时，才需要下面的权限。
     */
    public static boolean hasStoragePermission(Context context) {
        // 针对当前缓存方案，恒为 true
        return true;
    }

    /** 无需请求（保持兼容调用处，不做任何动作） */
    public static void requestStoragePermission(Activity activity) {
        // no-op
    }

    /** 无需请求（保持兼容调用处，不做任何动作） */
    public static void requestStoragePermission(Fragment fragment) {
        // no-op
    }

    /** 兼容外部传进来的回调，恒认为失败/或直接 true 均可，这里返回 true 避免阻断流程 */
    public static boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        return true;
    }

    /** 若未来真的要访问系统媒体库，可用这个辅助（目前没用到） */
    public static boolean ensureMediaLibraryReadPermission(Fragment fragment) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(fragment.requireContext(), Manifest.permission.READ_MEDIA_VIDEO)
                    != PackageManager.PERMISSION_GRANTED) {
                fragment.requestPermissions(new String[]{Manifest.permission.READ_MEDIA_VIDEO}, REQUEST_STORAGE_PERMISSION);
                return false;
            }
            return true;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(fragment.requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                fragment.requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_STORAGE_PERMISSION);
                return false;
            }
            return true;
        } else {
            return true;
        }
    }

    // 网络权限通常不需要运行时请求
    public static boolean hasNetworkPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.INTERNET)
                == PackageManager.PERMISSION_GRANTED;
    }
}
