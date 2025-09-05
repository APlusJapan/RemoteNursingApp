// WebViewActivity.java
package com.aplus.remotenursing;

import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

public class WebViewActivity extends AppCompatActivity {

    private WebView webView;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 设置状态栏和导航栏样式
        setupStatusBar();

        setContentView(R.layout.activity_webview);

        initViews();
        initWebView();
        loadUrl();
    }

    private void setupStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Window window = getWindow();

            // 设置状态栏背景为白色
            window.setStatusBarColor(ContextCompat.getColor(this, android.R.color.white));

            // 设置状态栏文字为深色（黑色）
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            );

            // 如果需要，也可以设置导航栏
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                window.setNavigationBarColor(ContextCompat.getColor(this, android.R.color.white));
                window.getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR |
                                View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                );
            }
        }
    }

    private void initViews() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // 显示返回按钮
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);

            // 设置标题文字颜色为黑色
            getSupportActionBar().setTitle("");
            toolbar.setTitleTextColor(ContextCompat.getColor(this, android.R.color.black));

            // 设置返回箭头颜色为黑色
            toolbar.setNavigationIcon(R.drawable.ic_arrow_back_black_24dp);
            // 如果没有黑色箭头图标，可以动态着色
            if (toolbar.getNavigationIcon() != null) {
                toolbar.getNavigationIcon().setTint(ContextCompat.getColor(this, android.R.color.black));
            }
        }

        webView = findViewById(R.id.webview);
        progressBar = findViewById(R.id.progress_bar);
    }

    private void initWebView() {
        WebSettings webSettings = webView.getSettings();

        // 启用JavaScript
        webSettings.setJavaScriptEnabled(true);

        // 启用DOM存储
        webSettings.setDomStorageEnabled(true);

        // 启用缓存
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);

        // 支持缩放
        webSettings.setSupportZoom(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);

        // 自适应屏幕
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);

        // 设置User-Agent
        webSettings.setUserAgentString(webSettings.getUserAgentString() + " RemoteNursing/1.0");

        // WebViewClient处理页面导航
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(ProgressBar.GONE);

                // 设置标题
                if (getSupportActionBar() != null) {
                    String title = view.getTitle();
                    if (title != null && !title.isEmpty()) {
                        getSupportActionBar().setTitle(title);
                        // 确保标题颜色为黑色
                        Toolbar toolbar = findViewById(R.id.toolbar);
                        toolbar.setTitleTextColor(ContextCompat.getColor(WebViewActivity.this, android.R.color.black));
                    }
                }
            }
        });

        // WebChromeClient处理进度条和标题
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                progressBar.setProgress(newProgress);

                if (newProgress == 100) {
                    progressBar.setVisibility(ProgressBar.GONE);
                } else {
                    progressBar.setVisibility(ProgressBar.VISIBLE);
                }
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                super.onReceivedTitle(view, title);
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setTitle(title);
                    // 确保标题颜色为黑色
                    Toolbar toolbar = findViewById(R.id.toolbar);
                    toolbar.setTitleTextColor(ContextCompat.getColor(WebViewActivity.this, android.R.color.black));
                }
            }
        });
    }

    private void loadUrl() {
        String url = getIntent().getStringExtra("url");
        String title = getIntent().getStringExtra("title");

        if (url != null && !url.isEmpty()) {
            webView.loadUrl(url);
        }

        if (title != null && !title.isEmpty() && getSupportActionBar() != null) {
            getSupportActionBar().setTitle(title);
            // 确保标题颜色为黑色
            Toolbar toolbar = findViewById(R.id.toolbar);
            toolbar.setTitleTextColor(ContextCompat.getColor(this, android.R.color.black));
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}