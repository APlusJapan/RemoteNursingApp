package com.aplus.remotenursing;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class FullyGridLayoutManager extends GridLayoutManager {
    public FullyGridLayoutManager(Context context, int spanCount) {
        super(context, spanCount);
    }

    public FullyGridLayoutManager(Context context, int spanCount, int orientation, boolean reverseLayout) {
        super(context, spanCount, orientation, reverseLayout);
    }

    @Override
    public void onMeasure(RecyclerView.Recycler recycler, RecyclerView.State state, int widthSpec, int heightSpec) {
        int itemCount = getItemCount();
        int span = getSpanCount();
        int rows = (itemCount + span - 1) / span;
        int totalHeight = 0;
        for (int i = 0; i < rows; i++) {
            try {
                View view = recycler.getViewForPosition(i * span);
                if (view != null) {
                    measureChildWithMargins(view, 0, 0);
                    int h = getDecoratedMeasuredHeight(view);
                    totalHeight += h;
                }
            } catch (Exception e) { }
        }
        setMeasuredDimension(View.MeasureSpec.getSize(widthSpec), totalHeight);
    }
}
