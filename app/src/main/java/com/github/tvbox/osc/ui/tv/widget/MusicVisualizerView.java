package com.github.tvbox.osc.ui.tv.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.animation.ValueAnimator;

/**
 * 节奏版音乐可视化视图
 * 核心改进：
 * 1. 使用低频瞬态峰值驱动主节奏柱（前1/3）
 * 2. Attack/Decay 动画模型（快起慢落）
 * 3. 节拍检测触发颜色闪变
 * 4. 完全兼容原有接口
 *
 * 作者: xuameng (节奏增强版)
 */
public class MusicVisualizerView extends View {

    // ==================== 常量 ====================
    private static final int   MAX_AMPLITUDE      = 12222;
    private static final int   BAR_COUNT          = 22;
    private static final float ATTACK_SPEED       = 0.65f;   // 上升速度（越大越快）
    private static final float DECAY_SPEED        = 0.12f;   // 衰减速度（越小越慢）
    private static final float BEAT_DECAY         = 0.93f;   // 节拍峰值衰减率
    private static final float BEAT_THRESHOLD     = 0.35f;   // 节拍触发阈值（0~1）
    private static final int   RHYTHM_BAR_COUNT   = BAR_COUNT / 3; // 节奏柱数量

    // ==================== 颜色系统 ====================
    private int[][] colorSchemes = new int[3][3];
    private static final long   COLOR_CYCLE_DURATION = (long)(0.1 * 60 * 1000);
    private int     currentSchemeIndex = 0;
    private long    lastSwitchTime     = 0;

    // ==================== 分段效果 ====================
    private boolean mShowStripes       = true;
    private int     mSegmentCount      = 20;
    private float   mSegmentSpacingFixed = 2.5f;

    // ==================== 绘图 & 状态 ====================
    private final Paint mBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] mBarHeights      = new float[BAR_COUNT];
    private final float[] mTargetHeights   = new float[BAR_COUNT];
    private final float[] mAmplitudeLevels = new float[BAR_COUNT];

    // ==================== 节奏系统（核心新增） ====================
    private float  mPeakVolume      = 0f;     // 当前帧低频峰值
    private float  mBeatIntensity   = 0f;     // 节拍强度（0~1）
    private boolean mIsBeat          = false;  // 当前帧是否检测到节拍
    private long   mLastBeatTime     = 0;      // 上次节拍时间
    private float  mSmoothedPeak     = 0f;     // 平滑后的峰值（用于阈值自适应）

    // ==================== 动画 ====================
    private ValueAnimator mAnimator;

    // ==================== 构造 ====================
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
        refreshColorSchemes();
    }

    // ==================== 分段效果设置 ====================
    public void setShowStripes(boolean show) {
        mShowStripes = show;
        postInvalidate();
    }

    public void setSegmentCount(int count) {
        mSegmentCount = Math.max(1, count);
        postInvalidate();
    }

    public void setSegmentSpacingFixed(float spacing) {
        mSegmentSpacingFixed = Math.max(0f, spacing);
        postInvalidate();
    }

    // ==================== 核心：更新可视化数据 ====================
    public void updateVisualizer(byte[] fft, float volumeLevel) {
        if (fft == null || fft.length < BAR_COUNT * 2 + 2) return;

        if (volumeLevel <= 0.001f) {
            reset();
            return;
        }

        // ---------- 1. 计算低频瞬态峰值（节奏检测核心） ----------
        float currentLowFreqPeak = 0f;
        for (int i = 0; i < RHYTHM_BAR_COUNT; i++) {
            int barIndex = 2 + i * 2;
            if (barIndex < fft.length - 1) {
                byte rfk = fft[barIndex];
                byte ifk = fft[barIndex + 1];
                float magnitude = (rfk * rfk + ifk * ifk);
                currentLowFreqPeak = Math.max(currentLowFreqPeak, magnitude);
            }
        }

        // 归一化峰值
        float normalizedPeak = Math.min(currentLowFreqPeak / MAX_AMPLITUDE, 1.0f);

        // 平滑峰值（用于自适应阈值）
        mSmoothedPeak = mSmoothedPeak * 0.95f + normalizedPeak * 0.05f;

        // ---------- 2. 节拍检测 ----------
        float adaptiveThreshold = Math.max(BEAT_THRESHOLD, mSmoothedPeak * 0.6f);

        if (normalizedPeak > adaptiveThreshold && normalizedPeak > mPeakVolume) {
            mPeakVolume = normalizedPeak;
            mBeatIntensity = normalizedPeak;
            mIsBeat = true;
            mLastBeatTime = System.currentTimeMillis();

            // 节拍触发颜色闪变
            refreshColorSchemes();
        } else {
            mIsBeat = false;
            // 峰值自然衰减
            mPeakVolume *= BEAT_DECAY;
            mBeatIntensity *= 0.88f;
        }

        // ---------- 3. 计算每根柱子的目标高度 ----------
        for (int i = 0; i < BAR_COUNT; i++) {
            int barIndex;
            if (i < BAR_COUNT / 3) {
                barIndex = 2 + i * 2;
            } else {
                barIndex = 2 + (BAR_COUNT / 3) * 2 + (i - BAR_COUNT / 3) * 4;
            }

            float magnitude = 0f;
            if (barIndex < fft.length - 1) {
                byte rfk = fft[barIndex];
                byte ifk = fft[barIndex + 1];
                magnitude = (rfk * rfk + ifk * ifk);
            }

            float weight;
            if (i < BAR_COUNT / 4) {
                weight = 1.0f;
            } else if (i < BAR_COUNT / 2) {
                weight = 2.5f;
            } else {
                float freqFactor = (float) Math.pow(1.5, (i - BAR_COUNT / 2) / 2.0);
                weight = 3.5f * freqFactor;
            }

            float target;

            if (i < RHYTHM_BAR_COUNT) {
                // ★ 节奏柱：直接跟峰值，反应最激烈
                target = mPeakVolume * getHeight() * 0.95f * weight;
            } else if (i < BAR_COUNT / 2) {
                // ★ 中频柱：混合峰值 + 自身频谱
                float selfRatio = Math.min(magnitude / MAX_AMPLITUDE, 1.0f);
                target = (mPeakVolume * 0.4f + selfRatio * 0.6f) * getHeight() * 0.8f * weight;
            } else {
                // ★ 高频柱：主要跟自身频谱，轻微受节拍影响
                float selfRatio = Math.min(magnitude / MAX_AMPLITUDE, 1.0f);
                target = (selfRatio + mBeatIntensity * 0.15f) * getHeight() * 0.6f * weight;
            }

            mTargetHeights[i] = Math.min(target, getHeight() * 0.95f);
            mAmplitudeLevels[i] = Math.min(magnitude / MAX_AMPLITUDE, 1.0f);
        }

        startAnimation();
    }

    // ==================== Attack/Decay 动画 ====================
    private void startAnimation() {
        if (mAnimator != null) {
            mAnimator.cancel();
        }

        mAnimator = ValueAnimator.ofFloat(0f, 1f);
        mAnimator.setDuration(ANIMATION_DURATION);
        mAnimator.addUpdateListener(animation -> {
            for (int i = 0; i < BAR_COUNT; i++) {
                if (mTargetHeights[i] > mBarHeights[i]) {
                    // 上升：快（attack）
                    mBarHeights[i] += (mTargetHeights[i] - mBarHeights[i]) * ATTACK_SPEED;
                } else {
                    // 下降：慢（decay）
                    mBarHeights[i] += (mTargetHeights[i] - mBarHeights[i]) * DECAY_SPEED;
                }
            }
            postInvalidate();
        });
        mAnimator.start();
    }

    // ==================== 绘制 ====================
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mBarHeights == null) return;

        checkColorCycleSwitch();

        final int width  = getWidth();
        final int height = getHeight();
        final float barWidth      = width / (float) BAR_COUNT;
        final float gap           = barWidth * 0.2f;
        final float barUnitWidth  = barWidth - gap;
        final float MIN_HEIGHT_FOR_STRIPES = 20f;

        for (int i = 0; i < BAR_COUNT; i++) {
            float left     = i * barWidth + gap / 2;
            float right    = left + barUnitWidth;
            float barHeight = Math.min(mBarHeights[i], height * 0.9f);
            float top      = height - barHeight;

            // 节奏柱加粗
            float actualLeft  = left;
            float actualRight = right;
            if (i < RHYTHM_BAR_COUNT && mIsBeat) {
                actualLeft  -= gap * 0.3f;
                actualRight += gap * 0.3f;
            }

            int color = getDynamicColor(mAmplitudeLevels[i], colorSchemes[currentSchemeIndex]);
            mBarPaint.setColor(color);

            if (mShowStripes && barHeight >= MIN_HEIGHT_FOR_STRIPES) {
                float segmentHeightWithSpacing = mSegmentSpacingFixed * 2;
                int actualSegmentCount = (int)(barHeight / segmentHeightWithSpacing);
                actualSegmentCount = Math.min(actualSegmentCount, mSegmentCount);
                actualSegmentCount = Math.max(1, actualSegmentCount);

                float actualSegmentHeight = (barHeight - (actualSegmentCount - 1) * mSegmentSpacingFixed) / actualSegmentCount;

                for (int segment = 0; segment < actualSegmentCount; segment++) {
                    float segmentTop    = height - (segment + 1) * (actualSegmentHeight + mSegmentSpacingFixed);
                    float segmentBottom = segmentTop + actualSegmentHeight;
                    if (segmentTop >= top) {
                        canvas.drawRect(actualLeft, segmentTop, actualRight, segmentBottom, mBarPaint);
                    }
                }
            } else {
                canvas.drawRect(actualLeft, top, actualRight, height, mBarPaint);
            }
        }
    }

    // ==================== 颜色系统 ====================
    private void refreshColorSchemes() {
        for (int i = 0; i < 3; i++) {
            float baseHue = (float)(Math.random() * 360);
            colorSchemes[i] = new int[] {
                Color.HSVToColor(new float[]{ baseHue,        1f, 1f }),
                Color.HSVToColor(new float[]{ (baseHue + 120) % 360, 1f, 1f }),
                Color.HSVToColor(new float[]{ (baseHue + 240) % 360, 1f, 1f })
            };
        }
        postInvalidate();
    }

    private void checkColorCycleSwitch() {
        long now = System.currentTimeMillis();
        if (now - lastSwitchTime > COLOR_CYCLE_DURATION) {
            currentSchemeIndex = (currentSchemeIndex + 1) % colorSchemes.length;
            lastSwitchTime = now;
            refreshColorSchemes();
        }
    }

    private int getDynamicColor(float amplitude, int[] currentScheme) {
        amplitude = Math.max(0.0f, Math.min(1.0f, amplitude));
        if (amplitude < 0.3f) {
            return interpolateColor(amplitude / 0.3f, currentScheme[0], currentScheme[1]);
        } else {
            return interpolateColor((amplitude - 0.3f) / 0.7f, currentScheme[1], currentScheme[2]);
        }
    }

    private int interpolateColor(float ratio, int startColor, int endColor) {
        int alpha = (int)(Color.alpha(startColor)  + (Color.alpha(endColor)  - Color.alpha(startColor))  * ratio);
        int red   = (int)(Color.red(startColor)    + (Color.red(endColor)    - Color.red(startColor))    * ratio);
        int green = (int)(Color.green(startColor)  + (Color.green(endColor)  - Color.green(startColor))  * ratio);
        int blue  = (int)(Color.blue(startColor)   + (Color.blue(endColor)   - Color.blue(startColor))   * ratio);
        return Color.argb(alpha, red, green, blue);
    }

    // ==================== 重置 & 释放 ====================
    public void reset() {
        for (int i = 0; i < BAR_COUNT; i++) {
            mBarHeights[i]    = 0;
            mTargetHeights[i] = 0;
            mAmplitudeLevels[i] = 0;
        }
        mPeakVolume     = 0f;
        mBeatIntensity  = 0f;
        mSmoothedPeak   = 0f;
        mIsBeat         = false;
        postInvalidate();
    }

    public void release() {
        if (mAnimator != null) {
            mAnimator.cancel();
            mAnimator.removeAllUpdateListeners();
        }
        reset();
    }

    // 动画时长常量（补上）
    private static final int ANIMATION_DURATION = 80;
}
