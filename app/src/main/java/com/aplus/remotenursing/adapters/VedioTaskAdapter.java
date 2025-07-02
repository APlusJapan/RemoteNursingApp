package com.aplus.remotenursing.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.aplus.remotenursing.R;
import com.aplus.remotenursing.models.VedioTask;
import com.bumptech.glide.Glide;

import java.util.List;

public class VedioTaskAdapter extends RecyclerView.Adapter<VedioTaskAdapter.SeriesViewHolder> {

    public interface OnSeriesClickListener {
        void onSeriesClick(int position);
    }

    private List<VedioTask> seriesList;
    private OnSeriesClickListener listener;

    public VedioTaskAdapter(List<VedioTask> seriesList, OnSeriesClickListener listener) {
        this.seriesList = seriesList;
        this.listener = listener;
    }

    public void setSeriesList(List<VedioTask> list) {
        this.seriesList = list;
    }

    @NonNull
    @Override
    public SeriesViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_vediotask_vedioseries, parent, false);
        return new SeriesViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SeriesViewHolder holder, int position) {
        VedioTask current = seriesList.get(position);
        // 加载封面图片（用Glide等库，避免本地resId）
        Glide.with(holder.ivCover.getContext())
                .load(current.getVedioSurfaceImage())
                .placeholder(R.drawable.ic_video) // 默认占位
                .error(R.drawable.ic_video)       // 加载失败
                .into(holder.ivCover);

        holder.tvTitle.setText(current.getVedioSeriesName());

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSeriesClick(position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return seriesList == null ? 0 : seriesList.size();
    }

    static class SeriesViewHolder extends RecyclerView.ViewHolder {
        ImageView ivCover;
        TextView tvTitle;

        public SeriesViewHolder(@NonNull View itemView) {
            super(itemView);
            ivCover = itemView.findViewById(R.id.iv_series_cover);
            tvTitle = itemView.findViewById(R.id.tv_series_name);
        }
    }
}
