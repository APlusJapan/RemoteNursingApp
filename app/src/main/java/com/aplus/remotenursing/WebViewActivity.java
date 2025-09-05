// WebViewActivity.java
package com.aplus.remotenursing;

import android.os.Bundle;
import android.view.MenuItem;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class WebViewActivity extends AppCompatActivity {

    private WebView webView;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_webview);

        initViews();
        initWebView();
        loadUrl();
    }

    private void initViews() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // 显示返回按钮
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
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

        // 移除已废弃的setAppCacheEnabled方法
        // webSettings.setAppCacheEnabled(true); // 这行已被废弃，删除

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