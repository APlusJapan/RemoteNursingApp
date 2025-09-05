package com.aplus.remotenursing;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.aplus.remotenursing.common.ApiConfig;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.io.File;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_BLE_PERMISSIONS = 100;
    private static final String SP_NAME = "privacy_prefs";
    private static final String SP_KEY_ACCEPTED = "privacy_accepted_v1";

    private boolean isInitialized = false; // 防止重复初始化

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 首次打开 => 先做隐私合规拦截
        if (hasAcceptedPrivacy(this)) {
            // 已同意，直接正常启动
            bootstrapAfterConsent();
        } else {
            // 未同意，先弹隐私对话框
            showFirstRunPrivacyDialog();
        }
    }

    /** 首次同意后再做：初始化默认Fragment、底部导航、申请权限 */
    private void bootstrapAfterConsent() {
        // 防止重复初始化
        if (isInitialized) {
            return;
        }
        isInitialized = true;

        // 检查是否已有Fragment，避免重复创建
        Fragment existingFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (existingFragment == null) {
            // 只有在没有Fragment时才创建新的
            Fragment defaultFragment = new UserTaskFragment();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, defaultFragment)
                    .commit();
        }

        // 设置底部导航
        setupBottomNavigation();

        // 申请 BLE 动态权限
        requestBlePermissionsIfNeeded();
    }

    /** 设置底部导航切换逻辑 */
    private void setupBottomNavigation() {
        BottomNavigationView nav = findViewById(R.id.bottom_nav);

        // 防止重复设置监听器
        nav.setOnItemSelectedListener(null);

        nav.setOnItemSelectedListener(item -> {
            Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            Fragment targetFragment = null;

            if (item.getItemId() == R.id.navigation_task) {
                // 如果当前已经是UserTaskFragment，就不重复替换
                if (!(currentFragment instanceof UserTaskFragment)) {
                    targetFragment = new UserTaskFragment();
                }
            } else if (item.getItemId() == R.id.navigation_myInfo) {
                // 如果当前已经是MyInfoFragment，就不重复替换
                if (!(currentFragment instanceof MyInfoFragment)) {
                    targetFragment = new MyInfoFragment();
                }
            } else {
                return false;
            }

            // 只有在需要切换Fragment时才执行替换
            if (targetFragment != null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, targetFragment)
                        .commit();
            }
            return true;
        });

        // 设置默认选中第一个tab（只在第一次设置）
        if (nav.getSelectedItemId() != R.id.navigation_task) {
            nav.setSelectedItemId(R.id.navigation_task);
        }
    }

    /** 首次打开时的隐私弹窗（必须同意才继续） */
    private void showFirstRunPrivacyDialog() {
        // 构造一个简单、安全的对话框内容视图
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        root.setPadding(pad, dp(12), pad, 0);

        TextView tv = new TextView(this);
        tv.setText("感谢使用本应用！为向您提供视频播放、健康手表连接等服务，我们将按照《隐私政策》处理您的信息。请阅读并同意后继续。");
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        root.addView(tv);

        Button btnView = new Button(this);
        btnView.setText("查看《隐私政策》");
        LinearLayout.LayoutParams lpBtn = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpBtn.topMargin = dp(14);
        btnView.setLayoutParams(lpBtn);
        root.addView(btnView);

        CheckBox cb = new CheckBox(this);
        cb.setText("我已阅读并同意《隐私政策》");
        LinearLayout.LayoutParams lpCb = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpCb.topMargin = dp(8);
        cb.setLayoutParams(lpCb);
        root.addView(cb);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("隐私政策提示")
                .setView(root)
                .setCancelable(false)
                .setPositiveButton("同意并继续", null)
                .setNegativeButton("不同意并退出", (d, w) -> {
                    d.dismiss();
                    finish(); // 不同意则退出应用
                })
                .create();

        dialog.setOnShowListener(dlg -> {
            final Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positive.setEnabled(false); // 先禁用，勾选后再启用

            cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
                positive.setEnabled(isChecked);
            });

            positive.setOnClickListener(v -> {
                // 记录同意
                markAcceptedPrivacy(MainActivity.this);
                dialog.dismiss();
                // 继续正常启动
                bootstrapAfterConsent();
            });
        });

        // 点击"查看隐私政策"按钮：优先打开线上URL，没填则走本地assets
        btnView.setOnClickListener(v -> openPrivacyPage());

        dialog.show();
    }

    /** 打开隐私页面：有线上就传URL，没有就走本地assets */
    private void openPrivacyPage() {
        String url = null;
        try {
            url = ApiConfig.ALIYUN_OSS_PRIVACY_URL;
        } catch (Throwable ignore) {}
        Intent it = new Intent(this, PrivacyActivity.class);
        if (url != null && url.startsWith("http")) {
            it.putExtra(PrivacyActivity.EXTRA_PRIVACY_URL, url);
        }
        startActivity(it);
    }

    /** 供Fragment调用，主动切换tab */
    public void switchToTab(int itemId) {
        BottomNavigationView nav = findViewById(R.id.bottom_nav);
        if (nav.getSelectedItemId() != itemId) {
            nav.setSelectedItemId(itemId);
            // nav的监听器会自动切换Fragment
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("isInitialized", isInitialized);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (savedInstanceState != null) {
            isInitialized = savedInstanceState.getBoolean("isInitialized", false);
        }
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

    // ===== 隐私同意持久化 =====

    private static boolean hasAcceptedPrivacy(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(SP_NAME, MODE_PRIVATE);
        return sp.getBoolean(SP_KEY_ACCEPTED, false);
    }

    private static void markAcceptedPrivacy(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(SP_NAME, MODE_PRIVATE);
        sp.edit().putBoolean(SP_KEY_ACCEPTED, true).apply();
    }

    private int dp(int dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics()));
    }
}