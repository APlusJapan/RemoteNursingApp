// adapters/BannerAdapter.java
package com.aplus.remotenursing.adapters;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.aplus.remotenursing.R;
import com.aplus.remotenursing.models.AppBanner;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

import java.util.List;

public class BannerAdapter extends RecyclerView.Adapter<BannerAdapter.BannerViewHolder> {
    private static final String TAG = "BannerAdapter";
    private static final long CLICK_DEBOUNCE_TIME = 1000; // 1秒防抖

    private Context context;
    private List<AppBanner> bannerList;
    private List<String> legacyUrls; // 兼容旧的字符串URL列表
    private OnBannerClickListener clickListener;
    private boolean isLegacyMode = false;
    private boolean isUserInteracting = false; // 用户交互状态
    private long lastClickTime = 0;

    // 点击事件接口
    public interface OnBannerClickListener {
        void onBannerClick(AppBanner banner, int position);
        void onBannerView(AppBanner banner, int position);
        void onLegacyBannerClick(String url, int position); // 兼容旧版本
    }

    // 私有构造函数
    private BannerAdapter(Context context) {
        this.context = context;
    }

    // 静态工厂方法 - 创建AppBanner版本
    public static BannerAdapter createWithBanners(Context context, List<AppBanner> bannerList) {
        BannerAdapter adapter = new BannerAdapter(context);
        adapter.bannerList = bannerList;
        adapter.isLegacyMode = false;
        return adapter;
    }

    // 静态工厂方法 - 创建String URL版本（兼容旧版本）
    public static BannerAdapter createWithUrls(Context context, List<String> urls) {
        BannerAdapter adapter = new BannerAdapter(context);
        adapter.legacyUrls = urls;
        adapter.isLegacyMode = true;
        return adapter;
    }

    // 为了兼容现有代码，保留原来的构造函数（只用于String版本）
    public BannerAdapter(Context context, List<String> urls) {
        this.context = context;
        this.legacyUrls = urls;
        this.isLegacyMode = true;
    }

    // 设置点击监听器
    public void setOnBannerClickListener(OnBannerClickListener listener) {
        this.clickListener = listener;
    }

    // 设置用户交互状态
    public void setUserInteracting(boolean interacting) {
        this.isUserInteracting = interacting;
        Log.d(TAG, "用户交互状态设置为: " + interacting);
    }

    @NonNull
    @Override
    public BannerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_banner, parent, false);
        return new BannerViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BannerViewHolder holder, int position) {
        if (isLegacyMode) {
            // 兼容旧版本 - 使用String URL
            String imageUrl = legacyUrls.get(position);
            loadImage(holder.imageView, imageUrl);

            holder.itemView.setOnClickListener(v -> {
                if (shouldProcessClick()) {
                    if (clickListener != null) {
                        clickListener.onLegacyBannerClick(imageUrl, position);
                    }
                }
            });
        } else {
            // 新版本 - 使用AppBanner对象
            AppBanner banner = bannerList.get(position);
            loadImage(holder.imageView, banner.getImageUrl());

            // 点击事件 - 添加防抖和用户交互检测
            holder.itemView.setOnClickListener(v -> {
                if (shouldProcessClick()) {
                    Log.d(TAG, "Banner真实点击: " + banner.getTitle() + ", position: " + position);
                    if (clickListener != null) {
                        clickListener.onBannerClick(banner, position);
                    }
                } else {
                    Log.d(TAG, "Banner点击被防抖过滤或非用户交互");
                }
            });

            // 添加触摸反馈
            holder.itemView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            // 按下时添加视觉反馈并标记用户交互
                            v.setAlpha(0.8f);
                            isUserInteracting = true;
                            Log.d(TAG, "用户开始触摸Banner");
                            break;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            // 释放时恢复正常
                            v.setAlpha(1.0f);
                            // 延迟重置交互状态，给点击事件留时间
                            v.postDelayed(() -> {
                                isUserInteracting = false;
                                Log.d(TAG, "用户触摸Banner结束");
                            }, 200);
                            break;
                    }
                    return false; // 让点击事件继续传递
                }
            });
        }
    }

    /**
     * 判断是否应该处理点击事件
     */
    private boolean shouldProcessClick() {
        long currentTime = System.currentTimeMillis();

        // 防抖处理
        if (currentTime - lastClickTime < CLICK_DEBOUNCE_TIME) {
            Log.d(TAG, "点击过于频繁，忽略此次点击");
            return false;
        }

        // 检查用户交互状态
        if (!isUserInteracting) {
            Log.d(TAG, "非用户主动点击，忽略");
            return false;
        }

        lastClickTime = currentTime;
        return true;
    }

    /**
     * 加载图片的方法 - 修复版本
     */
    private void loadImage(ImageView imageView, String imageUrl) {
        // 验证URL有效性
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            Log.w(TAG, "图片URL为空，使用默认占位符");
            imageView.setImageResource(android.R.drawable.ic_menu_gallery);
            return;
        }

        // 验证URL格式
        if (!isValidImageUrl(imageUrl)) {
            Log.w(TAG, "无效的图片URL: " + imageUrl);
            imageView.setImageResource(android.R.drawable.ic_menu_gallery);
            return;
        }

        try {
            Glide.with(context)
                    .load(imageUrl.trim()) // 去除空格
                    .placeholder(android.R.drawable.ic_menu_gallery) // 加载中显示的图片
                    .error(android.R.drawable.ic_delete) // 加载失败显示的图片
                    .fallback(android.R.drawable.ic_menu_gallery) // URL为null时显示的图片
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .timeout(10000) // 10秒超时
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                            Log.w(TAG, "Glide加载图片失败: " + imageUrl, e);
                            return false; // 让Glide处理错误图片显示
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                            Log.d(TAG, "Glide加载图片成功: " + imageUrl);
                            return false; // 让Glide继续处理
                        }
                    })
                    .into(imageView);
        } catch (Exception e) {
            Log.e(TAG, "Glide加载图片异常: " + imageUrl, e);
            // 异常时显示默认图片
            imageView.setImageResource(android.R.drawable.ic_menu_gallery);
        }
    }

    /**
     * 验证图片URL是否有效
     */
    private boolean isValidImageUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }

        String trimmedUrl = url.trim().toLowerCase();

        // 检查是否以http或https开头
        if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
            Log.w(TAG, "图片URL协议不正确: " + url);
            return false;
        }

        // 基本长度检查
        if (trimmedUrl.length() < 10) {
            Log.w(TAG, "图片URL太短: " + url);
            return false;
        }

        // 检查是否包含域名
        if (!trimmedUrl.contains(".")) {
            Log.w(TAG, "图片URL格式不正确: " + url);
            return false;
        }

        return true;
    }

    @Override
    public int getItemCount() {
        return isLegacyMode ?
                (legacyUrls != null ? legacyUrls.size() : 0) :
                (bannerList != null ? bannerList.size() : 0);
    }

    static class BannerViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;

        public BannerViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.iv_banner);
        }
    }
}