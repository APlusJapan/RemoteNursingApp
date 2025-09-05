// manager/BannerActionManager.java
package com.aplus.remotenursing.manager;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.aplus.remotenursing.R;
import com.aplus.remotenursing.VideoTaskFragment;
import com.aplus.remotenursing.SmartwatchCheckupFragment;
import com.aplus.remotenursing.DailyCheckInFragment;
import com.aplus.remotenursing.QuestionnaireFragment;
import com.aplus.remotenursing.WebViewActivity;
import com.aplus.remotenursing.models.AppBanner;
import com.aplus.remotenursing.common.ApiConfig;
import com.aplus.remotenursing.common.UserUtils;

import org.json.JSONObject;

import java.io.IOException;
import java.util.Iterator;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class BannerActionManager {
    private Context context;
    private static final String TAG = "BannerActionManager";

    public BannerActionManager(Context context) {
        this.context = context;
    }

    public void handleBannerClick(AppBanner banner) {
        Log.d("BannerActionManager", "=== Banner点击调试信息 ===");
        Log.d("BannerActionManager", "Banner ID: " + banner.getId());
        Log.d("BannerActionManager", "Banner标题: " + banner.getTitle());
        Log.d("BannerActionManager", "ActionType: " + banner.getActionType());
        Log.d("BannerActionManager", "ActionType类型: " + banner.getActionType());
        Log.d("BannerActionManager", "ActionData: " + banner.getActionData());

        try {
            JSONObject actionData = banner.getActionDataJson();
            Log.d("BannerActionManager", "解析后的ActionData: " + actionData.toString());

            // 记录点击统计
            recordBannerClick(banner);

            int actionType = banner.getActionType();
            Log.d("BannerActionManager", "准备处理ActionType: " + actionType);

            switch (actionType) {
                case 0: // 添加对actionType=0的处理
                    Log.w("BannerActionManager", "ActionType为0，使用默认处理方式");
                    // 可以设置一个默认行为，比如显示提示信息
                    Toast.makeText(context, "Banner配置有误，请联系管理员", Toast.LENGTH_SHORT).show();
                    break;
                case 1: // 网页
                    Log.d("BannerActionManager", "处理网页点击");
                    handleWebAction(actionData);
                    break;
                case 2: // App内页面
                    Log.d("BannerActionManager", "处理App内页面点击");
                    handleInternalAction(actionData);
                    break;
                case 3: // 外部App
                    Log.d("BannerActionManager", "处理外部App点击");
                    handleExternalAppAction(actionData);
                    break;
                case 4: // 直播间
                    Log.d("BannerActionManager", "处理直播间点击");
                    handleLiveAction(actionData);
                    break;
                case 5: // 下载
                    Log.d("BannerActionManager", "处理下载点击");
                    handleDownloadAction(actionData);
                    break;
                case 6: // 分享
                    Log.d("BannerActionManager", "处理分享点击");
                    handleShareAction(actionData);
                    break;
                default:
                    Log.w("BannerActionManager", "不支持的ActionType: " + actionType + ", 类型: " + actionType);
                    Toast.makeText(context, "暂不支持此类型操作", Toast.LENGTH_SHORT).show();
            }

        } catch (Exception e) {
            Log.e("BannerActionManager", "处理Banner点击失败", e);
            Toast.makeText(context, "操作失败，请稍后重试", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleWebAction(JSONObject data) {
        try {
            JSONObject webData = data.getJSONObject("web");
            String url = webData.getString("url");
            boolean openInApp = webData.optBoolean("openInApp", true);

            if (openInApp) {
                // 使用WebView打开（需要实现WebViewActivity）
                Intent intent = new Intent(context, WebViewActivity.class);
                intent.putExtra("url", url);
                intent.putExtra("title", webData.optString("title", ""));
                context.startActivity(intent);
            } else {
                // 使用系统浏览器
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "打开网页失败", e);
            Toast.makeText(context, "打开网页失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleInternalAction(JSONObject data) {
        try {
            JSONObject internalData = data.getJSONObject("internal");
            String fragmentClass = internalData.getString("fragmentClass");
            JSONObject params = internalData.optJSONObject("params");

            Fragment fragment = createFragmentByClassName(fragmentClass);
            if (fragment == null) {
                Toast.makeText(context, "页面不存在", Toast.LENGTH_SHORT).show();
                return;
            }

            // 设置参数
            if (params != null) {
                Bundle bundle = new Bundle();
                for (Iterator<String> keys = params.keys(); keys.hasNext(); ) {
                    String key = keys.next();
                    bundle.putString(key, params.getString(key));
                }
                fragment.setArguments(bundle);
            }

            // 跳转Fragment
            if (context instanceof androidx.appcompat.app.AppCompatActivity) {
                androidx.appcompat.app.AppCompatActivity activity = (androidx.appcompat.app.AppCompatActivity) context;
                activity.getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, fragment)
                        .addToBackStack(null)
                        .commit();
            }
        } catch (Exception e) {
            Log.e(TAG, "跳转内部页面失败", e);
            Toast.makeText(context, "页面跳转失败", Toast.LENGTH_SHORT).show();
        }
    }

    private Fragment createFragmentByClassName(String className) {
        switch (className) {
            case "VideoTaskFragment":
                return new VideoTaskFragment();
            case "SmartwatchCheckupFragment":
                return new SmartwatchCheckupFragment();
            case "DailyCheckInFragment":
                return new DailyCheckInFragment();
            case "QuestionnaireFragment":
                return new QuestionnaireFragment();
            default:
                return null;
        }
    }

    private void handleExternalAppAction(JSONObject data) {
        try {
            JSONObject appData = data.getJSONObject("app");
            String packageName = appData.getString("packageName");
            String deepLink = appData.optString("deepLink");
            String downloadUrl = appData.optString("downloadUrl");
            String fallbackUrl = appData.optString("fallbackUrl");

            if (isAppInstalled(packageName)) {
                // 尝试Deep Link
                if (!deepLink.isEmpty()) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(deepLink));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(intent);
                        return;
                    } catch (Exception e) {
                        Log.w(TAG, "Deep Link失败，尝试直接启动应用");
                    }
                }

                // 直接启动应用
                Intent intent = context.getPackageManager().getLaunchIntentForPackage(packageName);
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                }
            } else {
                // 应用未安装，引导下载
                showAppDownloadDialog(getAppNameByPackage(packageName), downloadUrl, fallbackUrl);
            }
        } catch (Exception e) {
            Log.e(TAG, "打开外部应用失败", e);
            Toast.makeText(context, "打开应用失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleLiveAction(JSONObject data) {
        try {
            JSONObject liveData = data.getJSONObject("live");
            String platform = liveData.getString("platform");
            String roomId = liveData.getString("roomId");
            String deepLink = liveData.optString("deepLink");

            switch (platform.toLowerCase()) {
                case "douyin":
                    openDouyinLive(roomId, deepLink);
                    break;
                case "kuaishou":
                    openKuaishouLive(roomId, deepLink);
                    break;
                case "bilibili":
                    openBilibiliLive(roomId, deepLink);
                    break;
                default:
                    // 尝试通用深度链接
                    if (!deepLink.isEmpty()) {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(deepLink));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(intent);
                    } else {
                        Toast.makeText(context, "不支持该直播平台", Toast.LENGTH_SHORT).show();
                    }
            }
        } catch (Exception e) {
            Log.e(TAG, "打开直播间失败", e);
            Toast.makeText(context, "打开直播间失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void openDouyinLive(String roomId, String deepLink) {
        String packageName = "com.ss.android.ugc.aweme";
        if (isAppInstalled(packageName)) {
            try {
                String link = !deepLink.isEmpty() ? deepLink : "snssdk1128://live?room_id=" + roomId;
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Exception e) {
                // 深度链接失败，打开抖音首页
                Intent intent = context.getPackageManager().getLaunchIntentForPackage(packageName);
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                }
            }
        } else {
            showAppDownloadDialog("抖音", "https://www.douyin.com/download", "https://www.douyin.com");
        }
    }

    private void openKuaishouLive(String roomId, String deepLink) {
        String packageName = "com.smile.gifmaker";
        if (isAppInstalled(packageName)) {
            try {
                String link = !deepLink.isEmpty() ? deepLink : "kwai://live/" + roomId;
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Exception e) {
                Intent intent = context.getPackageManager().getLaunchIntentForPackage(packageName);
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                }
            }
        } else {
            showAppDownloadDialog("快手", "https://www.kuaishou.com/download", "https://www.kuaishou.com");
        }
    }

    private void openBilibiliLive(String roomId, String deepLink) {
        String packageName = "tv.danmaku.bili";
        if (isAppInstalled(packageName)) {
            try {
                String link = !deepLink.isEmpty() ? deepLink : "bilibili://live/" + roomId;
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Exception e) {
                Intent intent = context.getPackageManager().getLaunchIntentForPackage(packageName);
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                }
            }
        } else {
            showAppDownloadDialog("哔哩哔哩", "https://app.bilibili.com", "https://www.bilibili.com");
        }
    }

    private void handleDownloadAction(JSONObject data) {
        try {
            String downloadUrl = data.optString("url", "");
            if (!downloadUrl.isEmpty()) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "下载失败", e);
            Toast.makeText(context, "下载失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleShareAction(JSONObject data) {
        try {
            String shareText = data.optString("text", "");
            String shareUrl = data.optString("url", "");

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, shareText + " " + shareUrl);
            shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(Intent.createChooser(shareIntent, "分享到"));
        } catch (Exception e) {
            Log.e(TAG, "分享失败", e);
            Toast.makeText(context, "分享失败", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isAppInstalled(String packageName) {
        try {
            context.getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private String getAppNameByPackage(String packageName) {
        switch (packageName) {
            case "com.ss.android.ugc.aweme":
                return "抖音";
            case "com.smile.gifmaker":
                return "快手";
            case "tv.danmaku.bili":
                return "哔哩哔哩";
            default:
                return "应用";
        }
    }

    private void showAppDownloadDialog(String appName, String downloadUrl, String fallbackUrl) {
        new AlertDialog.Builder(context)
                .setTitle("应用未安装")
                .setMessage("需要安装" + appName + "才能打开此内容，是否前往下载？")
                .setPositiveButton("去下载", (dialog, which) -> {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(intent);
                    } catch (Exception e) {
                        if (!fallbackUrl.isEmpty()) {
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl));
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            context.startActivity(intent);
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void recordBannerClick(AppBanner banner) {
        // 异步记录点击统计
        new Thread(() -> {
            try {
                String userId = UserUtils.loadUserId(context);
                OkHttpClient client = new OkHttpClient();
                String url = ApiConfig.API_BANNER_CLICK +
                        "?bannerId=" + banner.getId() +
                        "&userId=" + (userId != null ? userId : "");

                Request request = new Request.Builder().url(url).post(okhttp3.RequestBody.create(new byte[0])).build();
                client.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        Log.w(TAG, "记录Banner点击失败: " + e.getMessage());
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        if (response.isSuccessful()) {
                            Log.d(TAG, "记录Banner点击成功");
                        }
                        response.close();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "记录Banner点击异常", e);
            }
        }).start();
    }
}