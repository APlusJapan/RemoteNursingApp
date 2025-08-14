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

import java.util.ArrayList;
import java.util.List;

public class VideoTaskDetailAdapter extends RecyclerView.Adapter<VideoTaskDetailAdapter.VH> {

    private final List<VideoTaskDetail> items;
    private final OnVideoClickListener listener;
    private VideoTaskDetail currentPlayingItem; // 当前播放的视频（仅保存引用，比较用id）

    public interface OnVideoClickListener {
        void onVideoClick(VideoTaskDetail item);
    }

    public VideoTaskDetailAdapter(List<VideoTaskDetail> items, OnVideoClickListener listener) {
        // 确保内部列表可变
        this.items = (items != null) ? items : new ArrayList<>();
        this.listener = listener;
    }

    /** 新增：外部刷新数据源（与 Fragment 中的 adapter.setData(...) 对应） */
    public void setData(List<VideoTaskDetail> newItems) {
        this.items.clear();
        if (newItems != null) {
            this.items.addAll(newItems);
        }
        // 如果当前播放项存在，尽量在新列表中定位它，便于高亮
        if (currentPlayingItem != null) {
            String playingId = currentPlayingItem.getVideoId();
            int idx = findIndexById(playingId);
            if (idx >= 0) {
                // 用新列表里的对象替换引用，避免 equals 失败
                currentPlayingItem = items.get(idx);
            } else {
                currentPlayingItem = null;
            }
        }
        notifyDataSetChanged();
    }

    /** 新增：设置当前播放的视频（根据 id 做局部刷新，避免全量刷新） */
    public void setCurrentPlayingItem(VideoTaskDetail item) {
        String oldId = (currentPlayingItem != null) ? currentPlayingItem.getVideoId() : null;
        String newId = (item != null) ? item.getVideoId() : null;

        // 更新引用为列表里真实持有的对象，避免 indexOf 因对象不同失败
        int newIndex = findIndexById(newId);
        int oldIndex = findIndexById(oldId);

        currentPlayingItem = (newIndex >= 0) ? items.get(newIndex) : null;

        if (oldIndex >= 0) notifyItemChanged(oldIndex);
        if (newIndex >= 0) notifyItemChanged(newIndex);
    }

    private int findIndexById(String videoId) {
        if (videoId == null) return -1;
        for (int i = 0; i < items.size(); i++) {
            VideoTaskDetail it = items.get(i);
            if (videoId.equals(it.getVideoId())) return i;
        }
        return -1;
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

        // 标题
        holder.tvTitle.setText(item.getVideoName());

        // 描述
        if (holder.tvDescription != null) {
            if (item.getVideoDescription() != null && !item.getVideoDescription().isEmpty()) {
                holder.tvDescription.setText(item.getVideoDescription());
                holder.tvDescription.setVisibility(View.VISIBLE);
            } else {
                holder.tvDescription.setText("视频训练内容，请点击播放观看");
                holder.tvDescription.setVisibility(View.VISIBLE);
            }
        }

        // 时长
        if (holder.tvDuration != null) {
            if (item.getVideoDuration() != null && !item.getVideoDuration().isEmpty()) {
                holder.tvDuration.setText(item.getVideoDuration());
            } else {
                holder.tvDuration.setText("05:30"); // 默认时长
            }
        }

        // 播放状态（按 videoId 比较更稳妥）
        boolean isCurrentPlaying = false;
        if (currentPlayingItem != null && currentPlayingItem.getVideoId() != null) {
            isCurrentPlaying = currentPlayingItem.getVideoId().equals(item.getVideoId());
        }
        if (holder.tvPlayingStatus != null) {
            holder.tvPlayingStatus.setVisibility(isCurrentPlaying ? View.VISIBLE : View.GONE);
        }
        if (holder.viewPlayingIndicator != null) {
            holder.viewPlayingIndicator.setVisibility(isCurrentPlaying ? View.VISIBLE : View.GONE);
        }

        // 缩略图
        Glide.with(holder.ivThumb.getContext())
                .load(item.getVideoSurfaceImage())
                .placeholder(R.drawable.ic_video)
                .error(R.drawable.ic_video)
                .into(holder.ivThumb);

        // 点击事件
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onVideoClick(item);
        });
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView ivThumb;
        TextView tvTitle;
        TextView tvDescription;
        TextView tvDuration;
        TextView tvPlayingStatus;
        View viewPlayingIndicator;

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
