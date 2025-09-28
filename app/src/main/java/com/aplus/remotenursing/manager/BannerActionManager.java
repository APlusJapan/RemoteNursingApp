package com.aplus.remotenursing.manager;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.aplus.remotenursing.VideoTaskFragment;
import com.aplus.remotenursing.WebViewActivity;
import com.aplus.remotenursing.common.ApiConfig;
import com.aplus.remotenursing.common.UserUtils;
import com.aplus.remotenursing.models.AppBanner;
import com.aplus.remotenursing.models.UserAccount;

import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class BannerActionManager {
    private static final String TAG = "BannerActionManager";
    private Context context;

    public BannerActionManager(Context context) {
        this.context = context;
    }

    public void handleBannerClick(AppBanner banner) {
        Log.d(TAG, "=== Banner点击调试信息 ===");
        Log.d(TAG, "Banner ID: " + banner.getId());
        Log.d(TAG, "Banner标题: " + banner.getTitle());
        Log.d(TAG, "ActionType: " + banner.getActionType());
        Log.d(TAG, "ActionData: " + banner.getActionData());

        try {
            JSONObject actionData = new JSONObject(banner.getActionData());
            Log.d(TAG, "解析后的ActionData: " + actionData.toString());

            // 记录Banner点击
            recordBannerClick(banner);

            Log.d(TAG, "准备处理ActionType: " + banner.getActionType());
            switch (banner.getActionType()) {
                case 1: // 网页链接
                    Log.d(TAG, "处理网页图片链接");
                    handleWebClick(actionData);
                    break;
                case 2: // App内页面
                    Log.d(TAG, "处理App内页面点击");
                    handleInternalClick(actionData);
                    break;
                case 3: // 外部App
                    Log.d(TAG, "处理外部App跳转");
                    handleExternalAppClick(actionData);
                    break;
                case 4: // 直播
                    Log.d(TAG, "处理直播间点击");
                    handleLiveClick(actionData);
                    break;
                case 5: // 下载
                    Log.d(TAG, "处理下载点击");
                    handleDownloadClick(actionData);

                    break;
                case 6: // 分享
                    Log.d(TAG, "处理分享点击");
                    handleShareClick(actionData);
                    break;
                default:
                    Log.w(TAG, "未知的ActionType: " + banner.getActionType());
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "处理Banner点击失败", e);
        }
    }

    private void handleWebClick(JSONObject actionData) {
        try {
            JSONObject webData = actionData.getJSONObject("web");
            String url = webData.getString("url");
            String title = webData.optString("title", "网页");
            boolean openInApp = webData.optBoolean("openInApp", true);

            Log.d(TAG, "处理网页跳转: url=" + url + ", openInApp=" + openInApp);

            // 特殊处理购物网站和头条短链接 - 强制使用外部浏览器打开
            if (isShoppingSite(url) || isToutiaoLink(url)) {
                if (isToutiaoLink(url)) {
                    Log.d(TAG, "检测到今日头条链接，使用外部浏览器打开");
                } else {
                    Log.d(TAG, "检测到购物网站，使用外部浏览器打开");
                }
                openInExternalBrowser(url);
            } else if (openInApp) {
                // 在App内打开
                Intent intent = new Intent(context, WebViewActivity.class);
                intent.putExtra("url", url);
                intent.putExtra("title", title);
                context.startActivity(intent);
            } else {
                // 使用外部浏览器打开
                openInExternalBrowser(url);
            }
        } catch (Exception e) {
            Log.e(TAG, "处理网页点击失败", e);
        }
    }

    private boolean isShoppingSite(String url) {
        if (url == null) return false;

        // 检测主要购物网站
        String[] shoppingSites = {
                "taobao.com", "m.taobao.com", "www.taobao.com",
                "tmall.com", "m.tmall.com", "www.tmall.com",
                "yangkeduo.com", "mobile.yangkeduo.com", "www.yangkeduo.com",
                "jd.com", "m.jd.com", "www.jd.com",
                "suning.com", "m.suning.com",
                "vip.com", "m.vip.com"
        };

        String lowerUrl = url.toLowerCase();
        for (String site : shoppingSites) {
            if (lowerUrl.contains(site)) {
                Log.d(TAG, "识别购物网站: " + site);
                return true;
            }
        }
        return false;
    }

    // 新增：检测今日头条链接
    private boolean isToutiaoLink(String url) {
        if (url == null) return false;

        // 检测今日头条相关域名
        String[] toutiaoSites = {
                "toutiao.com", "m.toutiao.com", "www.toutiao.com",
                "jinritoutiao.com", "m.jinritoutiao.com"
        };

        String lowerUrl = url.toLowerCase();
        for (String site : toutiaoSites) {
            if (lowerUrl.contains(site)) {
                Log.d(TAG, "识别今日头条链接: " + site);
                return true;
            }
        }
        return false;
    }

    private void openInExternalBrowser(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            Log.d(TAG, "已用外部浏览器打开: " + url);
        } catch (Exception e) {
            Log.e(TAG, "打开外部浏览器失败", e);
        }
    }

    private void handleInternalClick(JSONObject actionData) {
        try {
            JSONObject internalData = actionData.getJSONObject("internal");
            String fragmentClass = internalData.getString("fragmentClass");
            Log.d(TAG, "处理内部页面跳转: fragmentClass=" + fragmentClass);

            if (context instanceof FragmentActivity) {
                FragmentActivity activity = (FragmentActivity) context;
                Fragment fragment = null;

                switch (fragmentClass) {
                    case "VideoTaskFragment":
                        fragment = new VideoTaskFragment();
                        break;
                    // 可以添加其他Fragment
                    default:
                        Log.w(TAG, "未知的Fragment类型: " + fragmentClass);
                        return;
                }

                if (fragment != null) {
                    activity.getSupportFragmentManager()
                            .beginTransaction()
                            .replace(android.R.id.content, fragment)  // 使用正确的容器ID
                            .addToBackStack(null)
                            .commit();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "处理内部页面点击失败", e);
        }
    }

    private void handleExternalAppClick(JSONObject actionData) {
        try {
            JSONObject appData = actionData.getJSONObject("app");
            String packageName = appData.getString("packageName");
            String deepLink = appData.optString("deepLink");
            String className = appData.optString("className");
            String fallbackUrl = appData.optString("fallbackUrl");

            Log.d(TAG, "处理外部App: packageName=" + packageName + ", deepLink=" + deepLink);

            // 检查App是否已安装
            PackageManager pm = context.getPackageManager();
            boolean isAppInstalled = false;

            try {
                pm.getPackageInfo(packageName, 0);
                isAppInstalled = true;
                Log.d(TAG, "App已安装: " + packageName);
            } catch (PackageManager.NameNotFoundException e) {
                Log.d(TAG, "App未安装: " + packageName);
            }

            if (isAppInstalled) {
                // App已安装，尝试打开
                Intent intent = null;

                // 优先使用深链接
                if (deepLink != null && !deepLink.isEmpty()) {
                    try {
                        intent = new Intent(Intent.ACTION_VIEW, Uri.parse(deepLink));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(intent);
                        Log.d(TAG, "通过深链接打开App成功: " + deepLink);
                        return;
                    } catch (Exception e) {
                        Log.w(TAG, "深链接打开失败，尝试其他方式: " + e.getMessage());
                    }
                }

                // 如果深链接失败，尝试通过包名和类名打开
                if (className != null && !className.isEmpty()) {
                    try {
                        intent = new Intent();
                        intent.setComponent(new ComponentName(packageName, className));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(intent);
                        Log.d(TAG, "通过ComponentName打开App成功");
                        return;
                    } catch (Exception e) {
                        Log.w(TAG, "ComponentName打开失败: " + e.getMessage());
                    }
                }

                // 最后尝试通过包名打开主Activity
                try {
                    intent = pm.getLaunchIntentForPackage(packageName);
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(intent);
                        Log.d(TAG, "通过LaunchIntent打开App成功");
                        return;
                    }
                } catch (Exception e) {
                    Log.w(TAG, "LaunchIntent打开失败: " + e.getMessage());
                }
            }

            // App未安装或打开失败，使用fallback URL或浏览器打开
            if (fallbackUrl != null && !fallbackUrl.isEmpty()) {
                Log.d(TAG, "App未安装或打开失败，使用fallback URL: " + fallbackUrl);
                openInExternalBrowser(fallbackUrl);
            } else {
                Log.w(TAG, "无法打开App且无fallback URL");
            }

        } catch (Exception e) {
            Log.e(TAG, "处理外部App点击失败", e);
        }
    }

    private void handleLiveClick(JSONObject actionData) {
        try {
            JSONObject liveData = actionData.getJSONObject("live");
            String platform = liveData.getString("platform");
            String deepLink = liveData.optString("deepLink");
            String fallbackUrl = liveData.optString("fallbackUrl");

            Log.d(TAG, "处理直播跳转: platform=" + platform + ", deepLink=" + deepLink);

            // 特殊处理今日头条：直接用浏览器打开fallbackUrl
            if ("toutiao".equalsIgnoreCase(platform)) {
                Log.d(TAG, "处理今日头条内容，使用浏览器打开");
                if (fallbackUrl != null && !fallbackUrl.isEmpty()) {
                    openInExternalBrowser(fallbackUrl);
                } else {
                    Log.w(TAG, "今日头条fallbackUrl为空");
                }
                return;
            }

            // 根据平台选择对应的App包名
            String packageName = "";
            switch (platform.toLowerCase()) {
                case "douyin":
                    packageName = "com.ss.android.ugc.aweme";
                    break;
                case "kuaishou":
                    packageName = "com.smile.gifmaker";
                    break;
                case "bilibili":
                    packageName = "tv.danmaku.bili";
                    break;
                case "xiaohongshu":
                    packageName = "com.xingin.xhs";
                    break;
                case "weibo":
                    packageName = "com.sina.weibo";
                    break;
                default:
                    Log.w(TAG, "未知的直播平台: " + platform);
                    // 对于未知平台，如果有fallbackUrl就用浏览器打开
                    if (fallbackUrl != null && !fallbackUrl.isEmpty()) {
                        openInExternalBrowser(fallbackUrl);
                    }
                    return;
            }

            // 构造临时的App数据
            JSONObject tempAppData = new JSONObject();
            JSONObject appInfo = new JSONObject();
            appInfo.put("packageName", packageName);
            appInfo.put("deepLink", deepLink);
            appInfo.put("fallbackUrl", fallbackUrl);
            tempAppData.put("app", appInfo);

            // 复用外部App处理逻辑
            handleExternalAppClick(tempAppData);

        } catch (Exception e) {
            Log.e(TAG, "处理直播点击失败", e);
        }
    }

    private void handleDownloadClick(JSONObject actionData) {
        try {
            String url = actionData.getString("url");
            Log.d(TAG, "处理下载: url=" + url);

            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);

        } catch (Exception e) {
            Log.e(TAG, "处理下载点击失败", e);
        }
    }

    private void handleShareClick(JSONObject actionData) {
        try {
            String text = actionData.getString("text");
            String url = actionData.optString("url");

            Log.d(TAG, "处理分享: text=" + text + ", url=" + url);

            // 构建分享内容，包含App下载链接
            String appDownloadUrl = getAppDownloadUrl();
            String shareText = text;

            if (url != null && !url.isEmpty()) {
                shareText += "\n\n活动链接: " + url;
            }

            if (appDownloadUrl != null && !appDownloadUrl.isEmpty()) {
                shareText += "\n\n下载App体验更多功能: " + appDownloadUrl;
            }

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "分享来自远程护理助手");

            Intent chooser = Intent.createChooser(shareIntent, "分享到");
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(chooser);

        } catch (Exception e) {
            Log.e(TAG, "处理分享点击失败", e);
        }
    }

    // 获取App下载链接
    private String getAppDownloadUrl() {
        // 这里可以配置您的App下载页面
        // 可以是应用商店链接、官网下载页面等
        return "https://your-app-download-page.com/download"; // 请替换为实际的下载链接
    }

    private void recordBannerClick(AppBanner banner) {
        // 异步记录Banner点击
        new Thread(() -> {
            try {
                Log.d(TAG, "尝试获取用户账户信息...");
                UserAccount userAccount = UserUtils.getUserAccount(context);
                if (userAccount == null) {
                    Log.w(TAG, "无法获取用户账户信息，跳过视频更新");
                    return;
                }

                String userId = userAccount.getUserId();
//                String projectId = userAccount.getProjectId();
//                String teamId = userAccount.getTeamId();
                String adminId = userAccount.getAdminId();
                if (userId == null) {
                    Log.w(TAG, "用户ID为空，无法记录Banner点击");
                    return;
                }

                Log.d(TAG, "记录Banner点击: bannerId=" + banner.getId() + ", userId=" + userId);

                OkHttpClient client = new OkHttpClient();
                JSONObject requestBody = new JSONObject();
                requestBody.put("bannerId", banner.getId());
                requestBody.put("userId", userId);
                requestBody.put("clickTime", System.currentTimeMillis());
                requestBody.put("adminId", adminId);

                RequestBody body = RequestBody.create(
                        MediaType.parse("application/json"),
                        requestBody.toString()
                );

                Request request = new Request.Builder()
                        .url(ApiConfig.API_BANNER_CLICK)
                        .post(body)
                        .build();

                client.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        Log.e(TAG, "记录Banner点击失败: " + e.getMessage());
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        try {
                            if (response.isSuccessful()) {
                                Log.d(TAG, "记录Banner点击成功");
                            } else {
                                Log.w(TAG, "记录Banner点击响应失败: " + response.code());
                            }
                        } finally {
                            response.close();
                        }
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "记录Banner点击异常", e);
            }
        }).start();
    }
}