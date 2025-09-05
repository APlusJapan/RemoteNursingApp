// adapters/BannerAdapter.java
package com.aplus.remotenursing.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.aplus.remotenursing.R;
import com.aplus.remotenursing.models.AppBanner;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import java.util.List;

public class BannerAdapter extends RecyclerView.Adapter<BannerAdapter.BannerViewHolder> {

    private Context context;
    private List<AppBanner> bannerList;
    private List<String> legacyUrls; // 兼容旧的字符串URL列表
    private OnBannerClickListener clickListener;
    private boolean isLegacyMode = false;

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
                if (clickListener != null) {
                    clickListener.onLegacyBannerClick(imageUrl, position);
                }
            });
        } else {
            // 新版本 - 使用AppBanner对象
            AppBanner banner = bannerList.get(position);
            loadImage(holder.imageView, banner.getImageUrl());

            // 记录展示
            if (clickListener != null) {
                clickListener.onBannerView(banner, position);
            }

            // 点击事件
            holder.itemView.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onBannerClick(banner, position);
                }
            });
        }
    }

    private void loadImage(ImageView imageView, String imageUrl) {
        Glide.with(context)
                .load(imageUrl)
                .placeholder(android.R.drawable.ic_menu_gallery) // 使用系统内置图标作为占位符
                .error(android.R.drawable.ic_delete) // 使用系统内置图标作为错误图
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .into(imageView);
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