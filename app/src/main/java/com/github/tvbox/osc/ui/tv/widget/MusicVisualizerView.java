
package com.github.tvbox.osc.ui.tv.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.animation.ValueAnimator;

public class MusicVisualizerView extends View {
    private static final int MAX_AMPLITUDE = 32767;
    private static final int BAR_COUNT = 16;
    private static final int ANIMATION_DURATION = 200;
    
    private Paint mBarPaint;
    private float[] mBarHeights;
    private float[] mTargetHeights;
    private ValueAnimator mAnimator;
    
    public MusicVisualizerView(Context context) {
        super(context);
        init();
    }
    
    public MusicVisualizerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public MusicVisualizerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    private void init() {
        mBarPaint = new Paint();
        mBarPaint.setColor(Color.parseColor("#4CAF50"));
        mBarPaint.setStyle(Paint.Style.FILL);
        mBarPaint.setAntiAlias(true);
        
        mBarHeights = new float[BAR_COUNT];
        mTargetHeights = new float[BAR_COUNT];
    }
    
    public void updateVisualizer(byte[] fft) {
        if (fft == null) return;
        
        for (int i = 0; i < BAR_COUNT; i++) {
            int x = 2 + i * 4;
            int barIndex = x;
            if (barIndex < fft.length) {
                byte rfk = fft[barIndex];
                byte ifk = fft[barIndex + 1];
                float magnitude = (rfk * rfk + ifk * ifk);
                mTargetHeights[i] = (magnitude * getHeight()) / (MAX_AMPLITUDE * 4);
            }
        }
        
        startAnimation();
    }
    
    private void startAnimation() {
        if (mAnimator != null && mAnimator.isRunning()) {
            mAnimator.cancel();
        }
        
        mAnimator = ValueAnimator.ofFloat(0f, 1f);
        mAnimator.setDuration(ANIMATION_DURATION);
        mAnimator.addUpdateListener(animation -> {
            float fraction = animation.getAnimatedFraction();
            for (int i = 0; i < BAR_COUNT; i++) {
                mBarHeights[i] = mBarHeights[i] + (mTargetHeights[i] - mBarHeights[i]) * fraction;
            }
            invalidate();
        });
        mAnimator.start();
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        if (mBarHeights == null) return;
        
        int width = getWidth();
        int height = getHeight();
        float barWidth = width / (float) BAR_COUNT;
        float gap = barWidth * 0.2f;
        float barUnitWidth = barWidth - gap;
        
        for (int i = 0; i < BAR_COUNT; i++) {
            float left = i * barWidth + gap / 2;
            float right = left + barUnitWidth;
            float barHeight = Math.min(mBarHeights[i], height * 0.9f);
            float top = height - barHeight;
            float bottom = height;
            
            canvas.drawRect(left, top, right, bottom, mBarPaint);
        }
    }
    
    public void setBarColor(int color) {
        mBarPaint.setColor(color);
        invalidate();
    }
    
    public void reset() {
        for (int i = 0; i < BAR_COUNT; i++) {
            mBarHeights[i] = 0;
            mTargetHeights[i] = 0;
        }
        invalidate();
    }
}
