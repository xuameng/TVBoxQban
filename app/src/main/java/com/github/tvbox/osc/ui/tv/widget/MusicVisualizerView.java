
package com.github.tvbox.osc.ui.tv.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.animation.ValueAnimator;
import java.util.Arrays;

public class MusicVisualizerView extends View {
    // 常量定义
    private static final int MAX_AMPLITUDE = 32767;
    private static final int BAR_COUNT = 16;
    private static final int ANIMATION_DURATION = 200;
    private static final float BAR_GAP_RATIO = 0.2f;

    // 成员变量
    private final Paint mBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] mCurrentHeights = new float[BAR_COUNT];
    private final float[] mTargetHeights = new float[BAR_COUNT];
    private ValueAnimator mAnimator;
    private boolean mIsRawDataMode = false;

    // 构造方法组
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
        mBarPaint.setColor(Color.parseColor("#4CAF50"));
        mBarPaint.setStyle(Paint.Style.FILL);
    }

    /**
     * 原始音频数据输入接口
     */
    public void onRawDataReceived(byte[] rawData) {
        if (rawData == null) return;
        
        // 简单模拟FFT转换过程
        byte[] simulatedFFT = new byte[66];
        System.arraycopy(rawData, 0, simulatedFFT, 2, 
            Math.min(rawData.length, simulatedFFT.length - 2));
        updateVisualizer(simulatedFFT);
    }

    /**
     * FFT数据处理接口
     */
    public void updateVisualizer(byte[] fft) {
        if (fft == null || fft.length < 66) return;

        for (int i = 0; i < BAR_COUNT; i++) {
            int idx = 2 + i * 4;
            if (idx < fft.length - 1) {
                float magnitude = (fft[idx] * fft[idx] + fft[idx+1] * fft[idx+1]);
                mTargetHeights[i] = (magnitude * getHeight()) / (MAX_AMPLITUDE * 4);
            }
        }
        startAnimation();
    }

    // 其余方法保持不变...
}
