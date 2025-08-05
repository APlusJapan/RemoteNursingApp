package com.aplus.remotenursing.adapters;

import android.content.Context;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;

public class BannerAdapter extends RecyclerView.Adapter<BannerAdapter.BannerHolder> {
    private List<String> imageUrls;
    private Context context;

    public BannerAdapter(Context context, List<String> imageUrls) {
        this.context = context;
        this.imageUrls = imageUrls;
    }

    @NonNull
    @Override
    public BannerHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ImageView iv = new ImageView(context);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return new BannerHolder(iv);
    }

    @Override
    public void onBindViewHolder(@NonNull BannerHolder holder, int position) {
        String url = imageUrls.get(position);
        Glide.with(context).load(url).into((ImageView) holder.itemView);
    }

    @Override
    public int getItemCount() {
        return imageUrls.size();
    }

    public static class BannerHolder extends RecyclerView.ViewHolder {
        public BannerHolder(@NonNull android.view.View itemView) {
            super(itemView);
        }
    }
}
