package com.aplus.remotenursing;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import java.io.File;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import android.util.Log;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_BLE_PERMISSIONS = 100;
    private Fragment usertaskFragment;
    private Fragment myInfoFragment;

    // 用于记录当前 tab 索引
    private int lastTabIndex = 0;
    private Fragment[] fragments;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. 申请 BLE 相关权限
        requestBlePermissionsIfNeeded();

        // 2. 实例化 Fragment
        usertaskFragment = new UserTaskFragment();
        myInfoFragment   = new MyInfoFragment();
        fragments = new Fragment[] { usertaskFragment, myInfoFragment };

        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.add(R.id.fragment_container, usertaskFragment, "usertask");
        ft.add(R.id.fragment_container, myInfoFragment, "myInfo").hide(myInfoFragment);
        ft.commit();

        // 3. 底部导航切换
        BottomNavigationView nav = findViewById(R.id.bottom_nav);
        nav.setOnItemSelectedListener(item -> {
            Fragment fragment;
            if (item.getItemId() == R.id.navigation_task) {
                fragment = new UserTaskFragment();
            } else if (item.getItemId() == R.id.navigation_myInfo) {
                fragment = new MyInfoFragment();
            } else {
                return false;
            }
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .commit();
            return true;
        });


    }

    // 新增：供Fragment调用，主动切换tab并刷新fragment
    public void switchToTab(int itemId) {
        BottomNavigationView nav = findViewById(R.id.bottom_nav);
        nav.setSelectedItemId(itemId);
        // nav的监听会自动切换Fragment，不需额外操作
    }

    private void requestBlePermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            boolean scanOk = ContextCompat.checkSelfPermission(this,
                    Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
            boolean connOk = ContextCompat.checkSelfPermission(this,
                    Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
            if (!scanOk || !connOk) {
                ActivityCompat.requestPermissions(this,
                        new String[]{
                                Manifest.permission.BLUETOOTH_SCAN,
                                Manifest.permission.BLUETOOTH_CONNECT
                        },
                        REQ_BLE_PERMISSIONS
                );
            }
        }
        // Android 12 以下无需额外动态申请
    }
    private void clearAppCache() {
        try {
            deleteDir(getCacheDir());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean deleteDir(File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] children = dir.list();
            if (children != null) {
                for (String child : children) {
                    boolean success = deleteDir(new File(dir, child));
                    if (!success) return false;
                }
            }
        }
        return dir != null && dir.delete();
    }
}
