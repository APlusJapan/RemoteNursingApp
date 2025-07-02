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
    public interface OnVideoClickListener { void onVideoClick(VideoTaskDetail item); }
    public VideoTaskDetailAdapter(List<VideoTaskDetail> items, OnVideoClickListener listener) {
        this.items = items; this.listener = listener;
    }
    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.videotask_detaili_videoitem, parent, false);
        return new VH(v);
    }
    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        VideoTaskDetail it = items.get(position);
        holder.tvTitle.setText(it.getVedioName());
        Glide.with(holder.ivThumb.getContext())
                .load(it.getVedioSurfaceImage())
                .placeholder(R.drawable.ic_video)
                .error(R.drawable.ic_video)
                .into(holder.ivThumb);
        holder.itemView.setOnClickListener(v -> { if (listener != null) listener.onVideoClick(it); });
    }
    @Override public int getItemCount() { return items.size(); }
    static class VH extends RecyclerView.ViewHolder {
        ImageView ivThumb; TextView tvTitle;
        VH(@NonNull View v) {
            super(v);
            ivThumb = v.findViewById(R.id.iv_video_thumb);
            tvTitle = v.findViewById(R.id.tv_video_title);
        }
    }
}
