package com.aplus.remotenursing.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.MeasureSpec;
import androidx.cardview.widget.CardView;

public class SquareCardView extends CardView {
    public SquareCardView(Context context) {
        super(context);
    }
    public SquareCardView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
    public SquareCardView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

//    @Override
//    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
//        //super.onMeasure(widthMeasureSpec, heightMeasureSpec);
//    }
}