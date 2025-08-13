package com.aplus.remotenursing.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.aplus.remotenursing.R;
import com.aplus.remotenursing.models.VideoTaskDetail;
import com.bumptech.glide.Glide;
import java.util.List;

public class VideoTaskDetailAdapter extends RecyclerView.Adapter<VideoTaskDetailAdapter.VH> {

    private final List<VideoTaskDetail> items;
    private final OnVideoClickListener listener;
    private VideoTaskDetail currentPlayingItem; // 新增：当前播放的视频

    public interface OnVideoClickListener {
        void onVideoClick(VideoTaskDetail item);
    }

    public VideoTaskDetailAdapter(List<VideoTaskDetail> items, OnVideoClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    // 新增：设置当前播放的视频
    public void setCurrentPlayingItem(VideoTaskDetail item) {
        VideoTaskDetail oldPlaying = this.currentPlayingItem;
        this.currentPlayingItem = item;

        // 更新旧的播放项
        if (oldPlaying != null) {
            int oldIndex = items.indexOf(oldPlaying);
            if (oldIndex >= 0) {
                notifyItemChanged(oldIndex);
            }
        }

        // 更新新的播放项
        if (item != null) {
            int newIndex = items.indexOf(item);
            if (newIndex >= 0) {
                notifyItemChanged(newIndex);
            }
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_video_task_detail, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        VideoTaskDetail item = items.get(position);

        // 设置视频标题
        holder.tvTitle.setText(item.getVideoName());

        // 设置视频描述
        if (holder.tvDescription != null) {
            if (item.getVideoDescription() != null && !item.getVideoDescription().isEmpty()) {
                holder.tvDescription.setText(item.getVideoDescription());
                holder.tvDescription.setVisibility(View.VISIBLE);
            } else {
                holder.tvDescription.setText("视频训练内容，请点击播放观看");
                holder.tvDescription.setVisibility(View.VISIBLE);
            }
        }

        // 设置视频时长
        if (holder.tvDuration != null) {
            if (item.getVideoDuration() != null && !item.getVideoDuration().isEmpty()) {
                holder.tvDuration.setText(item.getVideoDuration());
            } else {
                holder.tvDuration.setText("05:30"); // 默认时长
            }
        }

        // 设置播放状态指示
        boolean isCurrentPlaying = item.equals(currentPlayingItem);
        if (holder.tvPlayingStatus != null) {
            holder.tvPlayingStatus.setVisibility(isCurrentPlaying ? View.VISIBLE : View.GONE);
        }
        if (holder.viewPlayingIndicator != null) {
            holder.viewPlayingIndicator.setVisibility(isCurrentPlaying ? View.VISIBLE : View.GONE);
        }

        // 加载缩略图
        Glide.with(holder.ivThumb.getContext())
                .load(item.getVideoSurfaceImage())
                .placeholder(R.drawable.ic_video)
                .error(R.drawable.ic_video)
                .into(holder.ivThumb);

        // 设置点击事件
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onVideoClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView ivThumb;
        TextView tvTitle;
        TextView tvDescription;     // 新增：视频描述
        TextView tvDuration;        // 新增：视频时长
        TextView tvPlayingStatus;   // 新增：播放状态文字
        View viewPlayingIndicator;  // 新增：播放状态指示器

        VH(@NonNull View v) {
            super(v);
            ivThumb = v.findViewById(R.id.iv_video_thumbnail);
            tvTitle = v.findViewById(R.id.tv_video_name);
            tvDescription = v.findViewById(R.id.tv_video_description);
            tvDuration = v.findViewById(R.id.tv_video_duration);
            tvPlayingStatus = v.findViewById(R.id.tv_playing_status);
            viewPlayingIndicator = v.findViewById(R.id.view_playing_indicator);
        }
    }
}