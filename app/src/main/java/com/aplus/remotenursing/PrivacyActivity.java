package com.aplus.remotenursing;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.aplus.remotenursing.common.ApiConfig;

import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * 隐私政策展示页：
 * 1) 优先加载线上 ApiConfig.ALIYUN_OSS_PRIVACY_URL 或外部传入的 EXTRA；
 * 2) 无/加载失败则回落到本地 file:///android_asset/privacy.htm。
 */
public class PrivacyActivity extends AppCompatActivity {

    public static final String EXTRA_PRIVACY_URL = "extra_privacy_url";
    private static final String LOCAL_ASSET_URL = "file:///android_asset/privacy.htm";

    private WebView webView;
    private ProgressBar progressBar;
    private boolean didFallbackToLocal = false;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 简单的代码构建布局：WebView + 中间的圆形进度条
        FrameLayout root = new FrameLayout(this);
        webView = new WebView(this);
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleLarge);

        FrameLayout.LayoutParams webLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        );
        FrameLayout.LayoutParams pbLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        pbLp.gravity = Gravity.CENTER;

        root.addView(webView, webLp);
        root.addView(progressBar, pbLp);
        setContentView(root);

        // WebView 基本设置
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(false);      // 默认关闭 JS；若你的隐私页需要 JS 再改为 true
        s.setDomStorageEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        // Android 5.0+ 混合内容（如 http 图片）按需放开：
        // s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress >= 90) progressBar.setVisibility(View.GONE);
            }
        });

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // 打开外链：同域内继续 WebView 打开；明显的外站用系统浏览器
                Uri uri = request.getUrl();
                String scheme = uri != null ? uri.getScheme() : null;
                if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                    // 允许 WebView 内部继续加载
                    return false;
                } else {
                    // 其它 scheme（如 wechat://）交给系统
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, uri));
                    } catch (Exception ignored) {}
                    return true;
                }
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
            }

            @Override
            @SuppressWarnings("deprecation") // 兼容老机型错误回调
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                tryFallbackToLocal();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, android.webkit.WebResourceError error) {
                // 仅主框架失败时才回落
                if (request == null || request.isForMainFrame()) {
                    tryFallbackToLocal();
                }
            }
        });

        // 加载逻辑：Intent.EXTRA > ApiConfig > 本地 assets
        String fromIntent = getIntent() != null ? getIntent().getStringExtra(EXTRA_PRIVACY_URL) : null;
        String candidate = !TextUtils.isEmpty(fromIntent) ? fromIntent : ApiConfig.ALIYUN_OSS_PRIVACY_URL;
        if (!TextUtils.isEmpty(candidate) && (candidate.startsWith("http://") || candidate.startsWith("https://"))) {
            webView.loadUrl(candidate);
        } else {
            // 没配置或不是 http(s)，直接本地
            webView.loadUrl(LOCAL_ASSET_URL);
            didFallbackToLocal = true;
        }
    }

    private void tryFallbackToLocal() {
        if (!didFallbackToLocal) {
            didFallbackToLocal = true;
            progressBar.setVisibility(View.VISIBLE);
            webView.loadUrl(LOCAL_ASSET_URL);
        }
    }

    @Override
    protected void onDestroy() {
        // 标准的 WebView 释放，避免内存泄漏
        if (webView != null) {
            ((FrameLayout) webView.getParent()).removeView(webView);
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.clearHistory();
            webView.clearCache(true);
            webView.removeAllViews();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    // 物理返回键（部分机型）
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView != null && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}

