package com.github.tvbox.osc.ui.tv.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.animation.ValueAnimator;

/**
 * 音乐可视化视图组件
 * 功能：通过FFT数据动态显示16柱状音频频谱
 * 特性：
 * 1. 平滑动画过渡（200ms持续时间）
 * 2. 自定义柱状图颜色
 * 3. 自适应视图尺寸
 * 4. 支持动态重置效果
 */
public class MusicVisualizerView extends View {
    // 常量定义
    private static final int MAX_AMPLITUDE = 32767;   // 最大振幅值（16位有符号整数范围）
    private static final int BAR_COUNT = 16;          // 柱状图数量
    private static final int ANIMATION_DURATION = 200;// 动画时长(ms)

    // 绘图工具
    private final Paint mBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] mBarHeights = new float[BAR_COUNT];     // 当前柱状图高度
    private final float[] mTargetHeights = new float[BAR_COUNT];  // 目标柱状图高度

    // 动画控制器
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

    /**
     * 初始化组件默认参数
     */
    private void init() {
        mBarPaint.setColor(Color.parseColor("#4CAF50")); // 默认绿色
        mBarPaint.setStyle(Paint.Style.FILL);
    }

    /**
     * 更新可视化数据
     * @param fft FFT变换后的音频数据字节数组
     */
    public void updateVisualizer(byte[] fft) {
        if (fft == null || fft.length < 66) return; // 验证数据有效性

        // 计算每个柱状图的目标高度
        for (int i = 0; i < BAR_COUNT; i++) {
            int barIndex = 2 + i * 4; // FFT数据采样点偏移计算
            if (barIndex < fft.length - 1) {
                byte rfk = fft[barIndex];
                byte ifk = fft[barIndex + 1];
                float magnitude = (rfk * rfk + ifk * ifk);
                mTargetHeights[i] = (magnitude * getHeight()) / (MAX_AMPLITUDE * 4);
            }
        }
        startAnimation();
    }

    /**
     * 启动高度变化动画
     */
    private void startAnimation() {
        // 停止已有动画
        if (mAnimator != null) {
            mAnimator.cancel();
        }

        // 创建新动画
        mAnimator = ValueAnimator.ofFloat(0f, 1f);
        mAnimator.setDuration(ANIMATION_DURATION);
        mAnimator.addUpdateListener(animation -> {
            float fraction = animation.getAnimatedFraction();
            // 插值计算当前高度
            for (int i = 0; i < BAR_COUNT; i++) {
                mBarHeights[i] += (mTargetHeights[i] - mBarHeights[i]) * fraction;
            }
            postInvalidate(); // 线程安全的重绘请求
        });
        mAnimator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mBarHeights == null) return;

        final int width = getWidth();
        final int height = getHeight();
        final float barWidth = width / (float) BAR_COUNT;
        final float gap = barWidth * 0.2f;       // 柱状图间距(20%宽度)
        final float barUnitWidth = barWidth - gap;

        // 绘制所有柱状图
        for (int i = 0; i < BAR_COUNT; i++) {
            float left = i * barWidth + gap / 2;
            float right = left + barUnitWidth;
            float barHeight = Math.min(mBarHeights[i], height * 0.9f); // 限制最大高度
            float top = height - barHeight;

            canvas.drawRect(left, top, right, height, mBarPaint);
        }
    }

    /**
     * 设置柱状图颜色
     * @param color 颜色值（如Color.RED或0xFFFF0000）
     */
    public void setBarColor(int color) {
        mBarPaint.setColor(color);
        postInvalidate();
    }

    /**
     * 重置可视化效果
     */
    public void reset() {
        for (int i = 0; i < BAR_COUNT; i++) {
            mBarHeights[i] = 0;
            mTargetHeights[i] = 0;
        }
        postInvalidate();
    }

    /**
     * 清理资源
     */
    public void release() {
        if (mAnimator != null) {
            mAnimator.cancel();
            mAnimator.removeAllUpdateListeners();
        }
        reset();
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
}
