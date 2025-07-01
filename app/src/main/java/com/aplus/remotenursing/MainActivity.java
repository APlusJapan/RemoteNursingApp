package com.aplus.remotenursing;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_BLE_PERMISSIONS = 100;
    private Fragment taskFragment;
    private Fragment meFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. 申请 BLE 相关权限
        requestBlePermissionsIfNeeded();

        // 2. 实例化并 add 两个 Fragment
        taskFragment = new TaskFragment();
        meFragment   = new MeFragment();

        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.add(R.id.fragment_container, taskFragment, "task");
        ft.add(R.id.fragment_container, meFragment, "me").hide(meFragment);
        ft.commit();

        // 3. 底部导航切换
        BottomNavigationView nav = findViewById(R.id.bottom_nav);
        nav.setOnItemSelectedListener(item -> {
            FragmentTransaction tx = getSupportFragmentManager().beginTransaction();
            tx.hide(taskFragment)
                    .hide(meFragment);

            int id = item.getItemId();
            if (id == R.id.navigation_task) {
                tx.show(taskFragment);
            } else if (id == R.id.navigation_me) {
                tx.show(meFragment);
            }
            tx.commit();
            return true;
        });
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
}