
package com.github.tvbox.osc.ui.tv.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.animation.ValueAnimator;

/**
 * xuameng
 * 音乐可视化视图组件（带振幅颜色渐变）
 * 新增特性：
 * 1. 颜色随振幅大小变化
 * 2. 保持原有动画平滑性
 * 3. 完全兼容原有接口
 * 4. 振幅随音量大小变化
 * 5. 三种颜色随机变化
 * 6. 新增横纹样式选项
 */
public class MusicVisualizerView extends View {
    private static final int MAX_AMPLITUDE = 6222;
    private static final int BAR_COUNT = 22;
    private static final int ANIMATION_DURATION = 200;
    
    // 改为非静态变量实现动态刷新
    private int[][] colorSchemes = new int[3][3];

    // 新增横纹样式相关变量
    private boolean mShowStripes = true; // 是否显示横纹
    private int mStripeColor = Color.argb(180, 255, 255, 255); // 横纹颜色
    private float mStripeSpacingRatio = 0.15f; // 横纹间距比例（相对于音柱高度）
    private float mStripeWidth = 1.5f; // 横纹线宽
    private Paint mStripePaint; // 横纹画笔

    // 新增颜色方案刷新方法
    private void refreshColorSchemes() {
        for (int i = 0; i < 3; i++) {
            float baseHue = (float) (Math.random() * 360);
            colorSchemes[i] = new int[]{
                Color.HSVToColor(new float[]{baseHue, 1f, 1f}),
                Color.HSVToColor(new float[]{(baseHue + 120) % 360, 1f, 1f}),
                Color.HSVToColor(new float[]{(baseHue + 240) % 360, 1f, 1f})
            };
        }
        postInvalidate(); // 新增：立即刷新视图
    }

    private static final long COLOR_CYCLE_DURATION = (long)(0.1 * 60 * 1000); // 6秒切换
    private int currentSchemeIndex = 0;
    private long lastSwitchTime = 0;

    // 绘图工具
    private final Paint mBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] mBarHeights = new float[BAR_COUNT];
    private final float[] mTargetHeights = new float[BAR_COUNT];
    private final float[] mAmplitudeLevels = new float[BAR_COUNT]; // 存储各柱振幅强度

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

    private void init() {
        mBarPaint.setStyle(Paint.Style.FILL);
        refreshColorSchemes();  // 新增：初始化时预加载颜色方案
        
        // 初始化横纹画笔
        mStripePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mStripePaint.setStyle(Paint.Style.STROKE);
        mStripePaint.setStrokeWidth(mStripeWidth);
        mStripePaint.setColor(mStripeColor);
    }

    // 新增横纹样式设置方法
    public void setShowStripes(boolean show) {
        mShowStripes = show;
        postInvalidate();
    }

    public void setStripeColor(int color) {
        mStripeColor = color;
        mStripePaint.setColor(color);
        postInvalidate();
    }

    public void setStripeSpacingRatio(float ratio) {
        mStripeSpacingRatio = Math.max(0.05f, Math.min(0.3f, ratio));
        postInvalidate();
    }

    public void setStripeWidth(float width) {
        mStripeWidth = width;
        mStripePaint.setStrokeWidth(width);
        postInvalidate();
    }

    public void updateVisualizer(byte[] fft, float volumeLevel) {
        if (fft == null || fft.length < BAR_COUNT * 2 + 2) return;
        if (volumeLevel == 0f || volumeLevel == 0.0f || volumeLevel == 0.00f) {
            reset();   //xuameng处理静音状态重置音柱
        }
        // 修改采样策略：前1/3柱子重点采样低频，后2/3均匀采样中高频
        for (int i = 0; i < BAR_COUNT; i++) {
            int barIndex;
            if (i < BAR_COUNT / 3) {
                // 低频段密集采样（每2点取1）
                barIndex = 2 + i * 2;
            } else {
                // 中高频段间隔采样（每4点取1）
                barIndex = 2 + (BAR_COUNT / 3) * 2 + (i - BAR_COUNT / 3) * 4;
            }
    
            if (barIndex < fft.length - 1) {
                byte rfk = fft[barIndex];
                byte ifk = fft[barIndex + 1];
                float magnitude = (rfk * rfk + ifk * ifk);
                // xuameng改进的频率加权策略（三段式加权）
                float weight;
                if (i < BAR_COUNT / 4) {
                    weight = 1.0f;      //xuameng 超低频段(0-200Hz)增益
                } else if (i < BAR_COUNT / 2) {
                    weight = 2.0f;  //xuameng 中低频段(200-800Hz)基准值增益
                } else {
                    float freqFactor = (float) Math.pow(1.5, (i - BAR_COUNT / 2) / 2.0);   //xuameng 高频段(800Hz+)指数增强
                    weight = 3.5f * freqFactor;
                }
                mTargetHeights[i] = Math.min(
                    (magnitude * getHeight() * weight * volumeLevel) / MAX_AMPLITUDE,    //xuameng判断音量大小
                    getHeight() * 0.95f
                );
                mAmplitudeLevels[i] = Math.min(magnitude / MAX_AMPLITUDE, 1.0f);
            }
        }
        startAnimation();
    }		

    private void startAnimation() {
        if (mAnimator != null) {
            mAnimator.cancel();
        }

        mAnimator = ValueAnimator.ofFloat(0f, 1f);
        mAnimator.setDuration(ANIMATION_DURATION);
        mAnimator.addUpdateListener(animation -> {
            float fraction = animation.getAnimatedFraction();
            for (int i = 0; i < BAR_COUNT; i++) {
                mBarHeights[i] += (mTargetHeights[i] - mBarHeights[i]) * fraction;
            }
            postInvalidate();
        });
        mAnimator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mBarHeights == null) return;

        // 检查是否需要切换颜色方案
        checkColorCycleSwitch();

        final int width = getWidth();
        final int height = getHeight();
        final float barWidth = width / (float) BAR_COUNT;
        final float gap = barWidth * 0.2f;
        final float barUnitWidth = barWidth - gap;

        for (int i = 0; i < BAR_COUNT; i++) {
            float left = i * barWidth + gap / 2;
            float right = left + barUnitWidth;
            float barHeight = Math.min(mBarHeights[i], height * 0.9f);
            float top = height - barHeight;
        
            // 根据当前颜色方案和振幅强度计算颜色
            int color = getDynamicColor(mAmplitudeLevels[i],colorSchemes[currentSchemeIndex]); 
            mBarPaint.setColor(color);
            canvas.drawRect(left, top, right, height, mBarPaint);
            
            // 新增：绘制横纹样式

// 新增：绘制横纹样式
if (mShowStripes && barHeight > 0) {
    // 使用固定黑色横纹，确保横纹可见
    int stripeColor = Color.argb(
        0, // 固定透明度180（70%不透明）
        0,   // 红色分量：0（黑色）
        0,   // 绿色分量：0（黑色）
        0    // 蓝色分量：0（黑色）
    );
    mStripePaint.setColor(stripeColor);

    // 固定间距（例如10像素）
    float stripeSpacing = 5f;
    float stripeY = top + stripeSpacing;

    // 绘制横纹
    while (stripeY < height - stripeSpacing) {
        canvas.drawLine(left, stripeY, right, stripeY, mStripePaint);
        stripeY += stripeSpacing * 2;
    }
}

        }
    }

    // 修改调用方式
    private void checkColorCycleSwitch() {
        long now = System.currentTimeMillis();
        if (now - lastSwitchTime > COLOR_CYCLE_DURATION) {
            currentSchemeIndex = (currentSchemeIndex + 1) % colorSchemes.length;
            lastSwitchTime = now;
            // 优化刷新逻辑（不再需要currentSchemeIndex检查）
            refreshColorSchemes();
        }
    }

    /**
     * 根据振幅强度计算渐变颜色
     */
    private int getDynamicColor(float amplitude, int[] currentScheme) {
        amplitude = Math.max(0.0f, Math.min(1.0f, amplitude));  // 确保振幅在有效范围内
        if (amplitude < 0.3f) {
            return interpolateColor(amplitude / 0.3f, currentScheme[0], currentScheme[1]);
        } else {
            return interpolateColor((amplitude - 0.3f) / 0.7f, currentScheme[1], currentScheme[2]);
        }
    }
    
    /**
     * 颜色插值计算
     */
    private int interpolateColor(float ratio, int startColor, int endColor) {
        int alpha = (int)(Color.alpha(startColor) + (Color.alpha(endColor) - Color.alpha(startColor)) * ratio);
        int red = (int)(Color.red(startColor) + (Color.red(endColor) - Color.red(startColor)) * ratio);
        int green = (int)(Color.green(startColor) + (Color.green(endColor) - Color.green(startColor)) * ratio);
        int blue = (int)(Color.blue(startColor) + (Color.blue(endColor) - Color.blue(startColor)) * ratio);
        return Color.argb(alpha, red, green, blue);
    }

    public void reset() {
        for (int i = 0; i < BAR_COUNT; i++) {
            mBarHeights[i] = 0;
            mTargetHeights[i] = 0;
            mAmplitudeLevels[i] = 0;
        }
        postInvalidate();
    }

    public void release() {
        if (mAnimator != null) {
            mAnimator.cancel();
            mAnimator.removeAllUpdateListeners();
        }
        reset();
    }
}
