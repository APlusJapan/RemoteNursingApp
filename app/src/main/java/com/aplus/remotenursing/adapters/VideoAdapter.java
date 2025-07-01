package com.aplus.remotenursing.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.aplus.remotenursing.R;
import com.aplus.remotenursing.models.VideoItem;
import com.bumptech.glide.Glide;

import java.util.List;

public class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.VH> {

    public interface OnVideoClickListener {
        void onVideoClick(VideoItem item);
    }

    private final List<VideoItem> items;
    private final OnVideoClickListener listener;

    public VideoAdapter(List<VideoItem> items, OnVideoClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_video, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        VideoItem it = items.get(position);
        holder.tvTitle.setText(it.getVedioName());

        // 加载视频封面（vedioSurfaceImage）
        Glide.with(holder.ivThumb.getContext())
                .load(it.getVedioSurfaceImage())
                .placeholder(R.drawable.ic_video) // 你可以放一张默认图
                .error(R.drawable.ic_video)      // 加载失败用默认图
                .into(holder.ivThumb);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onVideoClick(it);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView ivThumb;
        TextView tvTitle;

        VH(@NonNull View v) {
            super(v);
            ivThumb = v.findViewById(R.id.iv_video_thumb);
            tvTitle = v.findViewById(R.id.tv_video_title);
        }
    }
}
