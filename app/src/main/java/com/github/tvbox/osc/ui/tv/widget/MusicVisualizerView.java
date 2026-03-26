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
private float[] mPreviousAmplitudeLevels = new float[BAR_COUNT];
private float mRhythmEnergy = 0;
private static final float RHYTHM_THRESHOLD = 0.3f;

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
    
    // 优化采样策略：三段式采样，增强鼓声响应
    for(int i = 0; i < BAR_COUNT; i++) {
        int barIndex;
        if(i < BAR_COUNT / 4) {
            // 低频段(0-300Hz)：每2点取1，对应底鼓
            barIndex = 2 + i * 2;
        } else if(i < BAR_COUNT * 3 / 4) {
            // 中频段(300-3kHz)：每1点取1，对应军鼓、嗵鼓（重点改进）
            barIndex = 2 + (BAR_COUNT / 4) * 2 + (i - BAR_COUNT / 4);
        } else {
            // 高频段(3kHz以上)：每2点取1，对应镲片
            barIndex = 2 + (BAR_COUNT / 4) * 2 + (BAR_COUNT / 2) + (i - BAR_COUNT * 3 / 4) * 2;
        }
        
        if(barIndex < fft.length - 1) {
            byte rfk = fft[barIndex];
            byte ifk = fft[barIndex + 1];
            float magnitude = (rfk * rfk + ifk * ifk);
            
            // 优化频率加权策略，增强鼓声响应
            float weight;
            if(i < BAR_COUNT / 4) {
                weight = 1.0f; // 超低频段(0-300Hz)增益
            } else if(i < BAR_COUNT * 3 / 4) {
                // 鼓声核心频段(300Hz-3kHz)增强响应
                weight = 4.0f; // 显著增强中频段
            } else {
                float freqFactor = (float) Math.pow(1.8, (i - BAR_COUNT * 3 / 4) / 2.0); // 提高指数因子
                weight = 3.0f * freqFactor;
            }
            
            // 添加瞬态能量检测（增强节奏感）
            float previousEnergy = mAmplitudeLevels[i];
            float energyChange = Math.max(0, magnitude - previousEnergy * 0.7f);
            float transientBoost = 1.0f + energyChange * 0.5f; // 瞬态增强因子
            
            mTargetHeights[i] = Math.min(
                (magnitude * getHeight() * weight * volumeLevel * transientBoost) / MAX_AMPLITUDE,
                getHeight() * 0.95f);
            mAmplitudeLevels[i] = Math.min(magnitude / MAX_AMPLITUDE, 1.0f);
        }
    }
    startAnimation();
}

private void startAnimation() {
    if(mAnimator != null) {
        mAnimator.cancel();
    }
    
    // 计算节奏能量
    mRhythmEnergy = 0;
    for(int i = 0; i < BAR_COUNT; i++) {
        if(i >= BAR_COUNT/4 && i < BAR_COUNT*3/4) { // 只计算鼓声核心频段
            float energyChange = Math.max(0, mAmplitudeLevels[i] - mPreviousAmplitudeLevels[i] * 0.7f);
            mRhythmEnergy += energyChange;
        }
        mPreviousAmplitudeLevels[i] = mAmplitudeLevels[i];
    }
    
    // 根据节奏能量调整动画速度
    int animationDuration = ANIMATION_DURATION;
    if(mRhythmEnergy > RHYTHM_THRESHOLD) {
        // 检测到强节奏时加快动画
        animationDuration = 50; // 50ms快速响应
    }
    
    mAnimator = ValueAnimator.ofFloat(0f, 1f);
    mAnimator.setDuration(animationDuration);
    mAnimator.addUpdateListener(animation -> {
        float fraction = animation.getAnimatedFraction();
        for(int i = 0; i < BAR_COUNT; i++) {
            // 根据频段调整动画曲线
            float animationFactor = 1.0f;
            if(i >= BAR_COUNT/4 && i < BAR_COUNT*3/4) {
                // 鼓声频段使用更快的动画曲线
                animationFactor = 1.5f;
            }
            mBarHeights[i] += (mTargetHeights[i] - mBarHeights[i]) * fraction * animationFactor;
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
        // 新增：定义分段效果的最小高度阈值（例如4像素）
        final float MIN_HEIGHT_FOR_STRIPES = 20f;
        for(int i = 0; i < BAR_COUNT; i++) {
            float left = i * barWidth + gap / 2;
            float right = left + barUnitWidth;
            float barHeight = Math.min(mBarHeights[i], height * 0.9f);
            float top = height - barHeight;
            // 根据当前颜色方案和振幅强度计算颜色
            int color = getDynamicColor(mAmplitudeLevels[i], colorSchemes[currentSchemeIndex]);
            mBarPaint.setColor(color);
            // 分段绘制音柱（实现"一段一段"效果）
            // 修改点：分段绘制音柱（仅当音柱高度达到最小阈值时才显示分段效果）
            if(mShowStripes && barHeight >= MIN_HEIGHT_FOR_STRIPES) {
                // 动态计算实际能显示的分段数量
                float segmentHeightWithSpacing = mSegmentSpacingFixed * 2; // 每段高度+间距
                int actualSegmentCount = (int)(barHeight / segmentHeightWithSpacing);
                actualSegmentCount = Math.min(actualSegmentCount, mSegmentCount);
                actualSegmentCount = Math.max(1, actualSegmentCount); // 至少显示1段
                // 计算实际段高度（考虑固定间距）
                float actualSegmentHeight = (barHeight - (actualSegmentCount - 1) * mSegmentSpacingFixed) / actualSegmentCount;
                // 从下往上绘制各个段
                for(int segment = 0; segment < actualSegmentCount; segment++) {
                    float segmentTop = height - (segment + 1) * (actualSegmentHeight + mSegmentSpacingFixed);
                    float segmentBottom = segmentTop + actualSegmentHeight;
                    // 确保段在音柱范围内
                    if(segmentTop >= top) {
                        canvas.drawRect(left, segmentTop, right, segmentBottom, mBarPaint);
                    }
                }
            } else {
                // 如果不显示分段效果，绘制完整音柱
                canvas.drawRect(left, top, right, height, mBarPaint);
            }
        }
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
    
    // 根据节奏能量调整颜色响应
    float adjustedAmplitude = amplitude;
    if(mRhythmEnergy > RHYTHM_THRESHOLD) {
        // 强节奏时增强颜色变化
        adjustedAmplitude = Math.min(1.0f, amplitude * 1.3f);
    }
    
    if(adjustedAmplitude < 0.3f) {
        return interpolateColor(adjustedAmplitude / 0.3f, currentScheme[0], currentScheme[1]);
    } else {
        return interpolateColor((adjustedAmplitude - 0.3f) / 0.7f, currentScheme[1], currentScheme[2]);
    }
}


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
        mPreviousAmplitudeLevels[i] = 0;
    }
    mRhythmEnergy = 0;
    postInvalidate();
}

    public void release() {
        if(mAnimator != null) {
            mAnimator.cancel();
            mAnimator.removeAllUpdateListeners();
        }
        reset();
    }
}
