package com.github.tvbox.osc.ui.tv.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.animation.ValueAnimator;

/** xuameng
 * 音乐可视化视图组件（带振幅颜色渐变）
 * 新增特性：
 * 1. 振幅大小颜色三种颜色变化
 * 2. 保持原有动画平滑性
 * 3. 完全兼容原有接口
 * 4. 振幅随音量大小变化
 * 5. 三种颜色随机变化
 */
public class MusicVisualizerView extends View {
    private static final int MAX_AMPLITUDE = 20000;
    private static final int BAR_COUNT = 22;
    private static final int ANIMATION_DURATION = 200;
    
    // 改为非静态变量实现动态刷新
    private int[][] colorSchemes = new int[3][3];

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

   // private static final long COLOR_CYCLE_DURATION = 10 * 60 * 1000; // 10分钟
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
    }

public void updateVisualizer(byte[] fft, float volumeLevel) {
    if (fft == null || fft.length < BAR_COUNT * 2 + 2) return;
    
    // 动态振幅检测（新增）
    float dynamicMax = detectDynamicMax(fft);
    
    // 感知音量映射（改造）
    float perceptibleVolume = mapToLogarithmicScale(volumeLevel);
    
    for (int i = 0; i < BAR_COUNT; i++) {
        int barIndex;
        if (i < BAR_COUNT / 3) {
            barIndex = 2 + i * 2;
        } else {
            barIndex = 2 + (BAR_COUNT / 3) * 2 + (i - BAR_COUNT / 3) * 4;
        }
    
        if (barIndex < fft.length - 1) {
            byte rfk = fft[barIndex];
            byte ifk = fft[barIndex + 1];
            float magnitude = (rfk * rfk + ifk * ifk);
            
            // 三段式频率补偿（优化）
            float freqCompensation = i < BAR_COUNT / 4 ? 
                0.8f : 
                (i < BAR_COUNT / 2 ? 1.2f : 
                (float)(1.8 + Math.pow(1.2, (i - BAR_COUNT/2)*2)));
            
            // 音量-振幅非线性映射
            float volumeImpact = (float)Math.pow(perceptibleVolume, 1.5f);
            
            // 动态高度计算
            mTargetHeights[i] = Math.min(
                (magnitude * getHeight() * freqCompensation * volumeImpact) / dynamicMax,
                getHeight() * 0.95f
            );
            
            mAmplitudeLevels[i] = Math.min(magnitude / dynamicMax, 1.0f);
        }
    }
    startAnimation();
}

// 动态振幅检测方法
private float detectDynamicMax(byte[] fft) {
    float max = 0f;
    for (int i = 2; i < fft.length - 1; i += 2) {
        float mag = (fft[i]*fft[i] + fft[i+1]*fft[i+1]);
        max = Math.max(max, mag);
    }
    return max * 0.7f; // 保留30%余量
}

// 对数音量映射
private float mapToLogarithmicScale(float level) {
    return (float)(Math.log(level * 100 + 1) / Math.log(101));
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
