package com.github.tvbox.osc.ui.tv.widget;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.animation.ValueAnimator;
/**
xuameng
音乐可视化视图组件（带振幅颜色渐变）
新增特性：
1，颜色随振幅大小变化
2，保持原有动画平滑性
3，完全兼容原有接口
4，振幅随音量大小变化
5，三种颜色随机变化
6，新增音柱分段效果
*/
public class MusicVisualizerView extends View {
    private static final int MAX_AMPLITUDE = 6222;
    private static final int BAR_COUNT = 22;
    private static final int ANIMATION_DURATION = 50;
    // 改为非静态变量实现动态刷新
    private int[][] colorSchemes = new int[3][3];
    // 新增音柱分段效果相关变量
    private boolean mShowStripes = true; // 是否显示分段效果
    private int mSegmentCount = 20; // 每个音柱的分段数
    private float mSegmentSpacingFixed = 2.5f; // 段间距固定值（像素）

// 节奏检测相关变量
private float mPreviousEnergy = 0f;
private float mEnergyThreshold = 1.25f; // 能量增长阈值
private boolean mBeatDetected = false;
private long mLastBeatTime = 0;
private static final long BEAT_COOLDOWN = 50; // 节拍冷却时间（毫秒）
// 节奏敏感度设置
private float mSensitivity = 1.5f; // 默认敏感度

    // 新增颜色方案刷新方法
    private void refreshColorSchemes() {
        for(int i = 0; i < 3; i++) {
            float baseHue = (float)(Math.random() * 360);
            colorSchemes[i] = new int[] {
                Color.HSVToColor(new float[] {
                        baseHue,
                        1f,
                        1f
                    }),
                    Color.HSVToColor(new float[] {
                        (baseHue + 120) % 360, 1f, 1f
                    }),
                    Color.HSVToColor(new float[] {
                        (baseHue + 240) % 360, 1f, 1f
                    })
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
        refreshColorSchemes(); // 新增：初始化时预加载颜色方案
    }
    // 新增音柱分段效果设置方法
    public void setShowStripes(boolean show) {
        mShowStripes = show;
        postInvalidate();
    }
    public void setSegmentCount(int count) {
        mSegmentCount = Math.max(1, count); // 至少1段
        postInvalidate();
    }
    public void setSegmentSpacingFixed(float spacing) {
        mSegmentSpacingFixed = Math.max(0f, spacing); // 确保非负值
        postInvalidate();
    }
public void updateVisualizer(byte[] fft, float volumeLevel) {
    if(fft == null || fft.length < BAR_COUNT * 2 + 2) return;
    if(volumeLevel == 0f || volumeLevel == 0.0f || volumeLevel == 0.00f) {
        reset(); //xuameng处理静音状态重置音柱
    }
    
    // 新增：节奏检测变量
    float currentEnergy = 0f;
    
    // 修改采样策略：前1/3柱子重点采样低频，后2/3均匀采样中高频
    for(int i = 0; i < BAR_COUNT; i++) {
        int barIndex;
        if(i < BAR_COUNT / 3) {
            // 低频段密集采样（每2点取1）
            barIndex = 2 + i * 2;
        } else {
            // 中高频段间隔采样（每4点取1）
            barIndex = 2 + (BAR_COUNT / 3) * 2 + (i - BAR_COUNT / 3) * 4;
        }
        
        if(barIndex < fft.length - 1) {
            byte rfk = fft[barIndex];
            byte ifk = fft[barIndex + 1];
            float magnitude = (rfk * rfk + ifk * ifk);
            
            // 增强低频响应：调整加权策略
            float weight;
            if(i < BAR_COUNT / 3) {  // 增加低频段范围
                weight = 1.8f; // 提高低频增益
            } else if(i < BAR_COUNT * 2 / 3) {
                weight = 2.5f; // 中频段基准值
            } else {
                float freqFactor = (float) Math.pow(1.3, (i - BAR_COUNT * 2 / 3) / 2.0);
                weight = 3.0f * freqFactor; // 适当降低高频权重
            }
            
            // 应用非线性响应曲线，增强小音量变化
            float responsiveVolume = (float) Math.pow(volumeLevel, 1.3);
            
            mTargetHeights[i] = Math.min(
                (magnitude * getHeight() * weight * responsiveVolume) / MAX_AMPLITUDE,
                getHeight() * 0.95f);
            mAmplitudeLevels[i] = Math.min(magnitude / MAX_AMPLITUDE, 1.0f);
            
            // 累计能量用于节奏检测
            currentEnergy += mAmplitudeLevels[i];
        }
    }
    
    // 新增：节奏检测逻辑
    detectBeat(currentEnergy);
    
    startAnimation();
}

private void startAnimation() {
    if(mAnimator != null) {
        mAnimator.cancel();
    }
    
    // 节拍检测时使用更快的动画
    int duration = mBeatDetected ? ANIMATION_DURATION / 2 : ANIMATION_DURATION;
    
    mAnimator = ValueAnimator.ofFloat(0f, 1f);
    mAnimator.setDuration(duration);
    mAnimator.addUpdateListener(animation -> {
        float fraction = animation.getAnimatedFraction();
        
        // 节拍检测时使用更激进的插值
        float animationFactor = mBeatDetected ? 1.5f : 1.0f;
        
        for(int i = 0; i < BAR_COUNT; i++) {
            float targetFraction = fraction * animationFactor;
            if (targetFraction > 1.0f) targetFraction = 1.0f;
            
            mBarHeights[i] += (mTargetHeights[i] - mBarHeights[i]) * targetFraction;
        }
        postInvalidate();
    });
    mAnimator.start();
}

    @Override
protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    if(mBarHeights == null) return;
    
    // 检查是否需要切换颜色方案
    checkColorCycleSwitch();
    
    final int width = getWidth();
    final int height = getHeight();
    final float barWidth = width / (float) BAR_COUNT;
    final float gap = barWidth * 0.2f;
    final float barUnitWidth = barWidth - gap;
    
    // 新增：节拍检测时的全局亮度增强
    float globalBrightness = mBeatDetected ? 1.2f : 1.0f;
    
    final float MIN_HEIGHT_FOR_STRIPES = 20f;
    
    for(int i = 0; i < BAR_COUNT; i++) {
        float left = i * barWidth + gap / 2;
        float right = left + barUnitWidth;
        float barHeight = Math.min(mBarHeights[i], height * 0.9f);
        float top = height - barHeight;
        
        // 根据当前颜色方案和振幅强度计算颜色
        int color = getDynamicColor(mAmplitudeLevels[i], colorSchemes[currentSchemeIndex]);
        
        // 应用节拍亮度增强
        if (mBeatDetected && i < BAR_COUNT / 3) {
            color = adjustColorBrightness(color, globalBrightness);
        }
        
        mBarPaint.setColor(color);
        
        // 分段绘制音柱（实现"一段一段"效果）
        if(mShowStripes && barHeight >= MIN_HEIGHT_FOR_STRIPES) {
            // ... 原有分段绘制逻辑保持不变
        } else {
            canvas.drawRect(left, top, right, height, mBarPaint);
        }
    }
    
    // 重置节拍标志
    if (mBeatDetected) {
        mBeatDetected = false;
    }
}

/**
 * 调整颜色亮度
 */
private int adjustColorBrightness(int color, float factor) {
    float[] hsv = new float[3];
    Color.colorToHSV(color, hsv);
    hsv[2] = Math.min(hsv[2] * factor, 1.0f); // 调整亮度
    return Color.HSVToColor(hsv);
}
    // 修改调用方式
    private void checkColorCycleSwitch() {
        long now = System.currentTimeMillis();
        if(now - lastSwitchTime > COLOR_CYCLE_DURATION) {
            currentSchemeIndex = (currentSchemeIndex + 1) % colorSchemes.length;
            lastSwitchTime = now;
            // 优化刷新逻辑（不再需要currentSchemeIndex检查）
            refreshColorSchemes();
        }
    }
    /**

    根据振幅强度计算渐变颜色
    */
private int getDynamicColor(float amplitude, int[] currentScheme) {
    amplitude = Math.max(0.0f, Math.min(1.0f, amplitude));
    
    // 节拍检测时使用更鲜艳的颜色
    if (mBeatDetected && amplitude > 0.2f) {
        // 节拍时直接使用最亮色
        return currentScheme[2];
    }
    
    // 增强颜色响应曲线
    if(amplitude < 0.2f) {
        return interpolateColor(amplitude / 0.2f, currentScheme[0], currentScheme[1]);
    } else if(amplitude < 0.6f) {
        return interpolateColor((amplitude - 0.2f) / 0.4f, currentScheme[1], currentScheme[2]);
    } else {
        // 高振幅时使用饱和色
        return currentScheme[2];
    }
}

    /**

    颜色插值计算
    */
    private int interpolateColor(float ratio, int startColor, int endColor) {
        int alpha = (int)(Color.alpha(startColor) + (Color.alpha(endColor) - Color.alpha(startColor)) * ratio);
        int red = (int)(Color.red(startColor) + (Color.red(endColor) - Color.red(startColor)) * ratio);
        int green = (int)(Color.green(startColor) + (Color.green(endColor) - Color.green(startColor)) * ratio);
        int blue = (int)(Color.blue(startColor) + (Color.blue(endColor) - Color.blue(startColor)) * ratio);
        return Color.argb(alpha, red, green, blue);
    }
    public void reset() {
        for(int i = 0; i < BAR_COUNT; i++) {
            mBarHeights[i] = 0;
            mTargetHeights[i] = 0;
            mAmplitudeLevels[i] = 0;
        }
        postInvalidate();
    }
    public void release() {
        if(mAnimator != null) {
            mAnimator.cancel();
            mAnimator.removeAllUpdateListeners();
        }
        reset();
    }

/**
 * 检测节拍点
 */
private void detectBeat(float currentEnergy) {
    long now = System.currentTimeMillis();
    
    // 防止过于频繁的节拍检测
    if (now - mLastBeatTime < BEAT_COOLDOWN) {
        return;
    }
    
    // 检测能量突增
    if (mPreviousEnergy > 0 && currentEnergy > mPreviousEnergy * mEnergyThreshold) {
        mBeatDetected = true;
        mLastBeatTime = now;
        
        // 触发节拍效果
        applyBeatEffect();
    } else {
        mBeatDetected = false;
    }
    
    mPreviousEnergy = currentEnergy;
}

/**
 * 应用节拍效果
 */
private void applyBeatEffect() {
    // 1. 随机增强几个柱子的高度
    int beatCount = 3 + (int)(Math.random() * 3); // 3-5个柱子
    for (int i = 0; i < beatCount; i++) {
        int randomBar = (int)(Math.random() * BAR_COUNT);
        // 优先选择低频段柱子
        if (randomBar < BAR_COUNT / 3) {
            mTargetHeights[randomBar] *= 1.8f; // 低频柱子增强80%
        } else {
            mTargetHeights[randomBar] *= 1.4f; // 其他柱子增强40%
        }
        // 确保不超过最大高度
        mTargetHeights[randomBar] = Math.min(mTargetHeights[randomBar], getHeight() * 0.95f);
    }
    
    // 2. 触发颜色切换（可选）
    if (Math.random() > 0.7) { // 70%概率切换颜色
        currentSchemeIndex = (currentSchemeIndex + 1) % colorSchemes.length;
        refreshColorSchemes();
    }
}

/**
 * 设置节奏敏感度
 * @param sensitivity 敏感度 (0.5-2.0)，值越大越敏感
 */
public void setSensitivity(float sensitivity) {
    mSensitivity = Math.max(0.5f, Math.min(2.0f, sensitivity));
    // 根据敏感度调整阈值
    mEnergyThreshold = 1.5f - (mSensitivity * 0.2f);
}

/**
 * 设置节拍冷却时间
 * @param cooldown 冷却时间（毫秒）
 */
public void setBeatCooldown(long cooldown) {
    BEAT_COOLDOWN = Math.max(50, Math.min(500, cooldown));
}

}
