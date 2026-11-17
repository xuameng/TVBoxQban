package com.github.tvbox.osc.ui.tv.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.animation.ValueAnimator;

public class MusicVisualizerView extends View {
    private static final int MAX_AMPLITUDE = 9222;
    private static final int BAR_COUNT = 22;
    private static final int ANIMATION_DURATION = 35;

    // 颜色方案
    private int[][] colorSchemes = new int[3][3];
    private boolean mShowStripes = true;
    private int mSegmentCount = 20;
    private float mSegmentSpacingFixed = 2.5f;

    private void refreshColorSchemes() {
        for (int i = 0; i < 3; i++) {
            float baseHue = (float) (Math.random() * 360);
            colorSchemes[i] = new int[]{
                    Color.HSVToColor(new float[]{baseHue, 1f, 1f}),
                    Color.HSVToColor(new float[]{(baseHue + 120) % 360, 1f, 1f}),
                    Color.HSVToColor(new float[]{(baseHue + 240) % 360, 1f, 1f})
            };
        }
        postInvalidate();
    }

    private static final long COLOR_CYCLE_DURATION = (long) (0.1 * 60 * 1000);
    private int currentSchemeIndex = 0;
    private long lastSwitchTime = 0;

    private final Paint mBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] mBarHeights = new float[BAR_COUNT];
    private final float[] mTargetHeights = new float[BAR_COUNT];
    private final float[] mAmplitudeLevels = new float[BAR_COUNT];

    // ============ 三频带方案 ============
    private static final int BAND_LOW_CENTER = 3;
    private static final int BAND_MID_CENTER = 11;
    private static final int BAND_HIGH_CENTER = 18;

    private float mEnergyLow = 0f;
    private float mEnergyMid = 0f;
    private float mEnergyHigh = 0f;
    private float[] mBarEnergies = new float[BAR_COUNT];

    // 🔴 改大 ATTACK，改大 RELEASE（快起快落）
    private static final float ATTACK = 0.85f;
    private static final float RELEASE = 0.40f;

    // 🔴 sigma 从 0.18 改到 0.45 → 影响范围大很多
    private static final float SIGMA = 0.45f;

    // 🔴 基底能量：让非锚点柱也有基础高度
    private static final float BASE_ENERGY_FLOOR = 0.15f;
    // =========================================

    // ============ 自动校准 ============
    private int mAutoBandLowCenter = BAND_LOW_CENTER;
    private int mAutoBandMidCenter = BAND_MID_CENTER;
    private int mAutoBandHighCenter = BAND_HIGH_CENTER;

    private final float[] mBandEnergyAccum = new float[BAR_COUNT];
    private int mCalibrateSampleCount = 0;
    private static final int CALIBRATE_INTERVAL = 15; // 🔴 从25改到15
    private long mLastCalibrateTime = 0;
    private static final long CALIBRATE_MIN_INTERVAL_MS = 800; // 🔴 从1500改到800
    // =========================================

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
        refreshColorSchemes();
    }

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

    public void updateVisualizer(byte[] fft, float volumeLevel) {
        if (fft == null || fft.length < BAR_COUNT * 2 + 2) return;

        if (volumeLevel <= 0.001f) {
            reset();
            return;
        }

        // ---------- 第一步：计算每个柱的频谱能量 ----------
        float[] magnitudes = new float[BAR_COUNT];
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
                magnitudes[i] = (rfk * rfk + ifk * ifk);
            }
        }

        // ---------- 第二步：累计能量用于校准 ----------
        for (int i = 0; i < BAR_COUNT; i++) {
            mBandEnergyAccum[i] += magnitudes[i];
        }
        mCalibrateSampleCount++;

        long now = System.currentTimeMillis();
        if (mCalibrateSampleCount >= CALIBRATE_INTERVAL
                && now - mLastCalibrateTime > CALIBRATE_MIN_INTERVAL_MS) {
            performAutoCalibrate();
            // 🔴 不清 mCalibrateSampleCount 到 0，而是减半，让校准更频繁
            mCalibrateSampleCount = mCalibrateSampleCount / 2;
            mLastCalibrateTime = now;
        }

        // ---------- 第三步：用校准后的频带中心计算三带能量 ----------
        int lowStart = Math.max(0, mAutoBandLowCenter - 3);
        int lowEnd = Math.min(BAR_COUNT - 1, mAutoBandLowCenter + 3);
        float lowEnergy = 0;
        for (int i = lowStart; i <= lowEnd; i++) {
            if (magnitudes[i] > lowEnergy) lowEnergy = magnitudes[i];
        }

        int midStart = Math.max(0, mAutoBandMidCenter - 4);
        int midEnd = Math.min(BAR_COUNT - 1, mAutoBandMidCenter + 4);
        float midEnergy = 0;
        for (int i = midStart; i <= midEnd; i++) {
            if (magnitudes[i] > midEnergy) midEnergy = magnitudes[i];
        }

        int highStart = Math.max(0, mAutoBandHighCenter - 3);
        int highEnd = Math.min(BAR_COUNT - 1, mAutoBandHighCenter + 3);
        float highEnergy = 0;
        for (int i = highStart; i <= highEnd; i++) {
            if (magnitudes[i] > highEnergy) highEnergy = magnitudes[i];
        }

        // 归一化
        float lowNorm = Math.min(lowEnergy / MAX_AMPLITUDE, 1.0f) * volumeLevel;
        float midNorm = Math.min(midEnergy / MAX_AMPLITUDE, 1.0f) * volumeLevel;
        float highNorm = Math.min(highEnergy / MAX_AMPLITUDE, 1.0f) * volumeLevel;

        // ---------- 第四步：快起快落 ----------
        if (lowNorm > mEnergyLow) {
            mEnergyLow += (lowNorm - mEnergyLow) * ATTACK;
        } else {
            mEnergyLow += (lowNorm - mEnergyLow) * RELEASE;
        }

        if (midNorm > mEnergyMid) {
            mEnergyMid += (midNorm - mEnergyMid) * ATTACK;
        } else {
            mEnergyMid += (midNorm - mEnergyMid) * RELEASE;
        }

        if (highNorm > mEnergyHigh) {
            mEnergyHigh += (highNorm - mEnergyHigh) * ATTACK;
        } else {
            mEnergyHigh += (highNorm - mEnergyHigh) * RELEASE;
        }

        // ---------- 第五步：计算每个柱的目标高度 ----------
        float maxHeight = getHeight() * 0.95f;

        for (int i = 0; i < BAR_COUNT; i++) {
            float distLow = Math.abs(i - mAutoBandLowCenter);
            float distMid = Math.abs(i - mAutoBandMidCenter);
            float distHigh = Math.abs(i - mAutoBandHighCenter);

            float factorLow = (float) Math.exp(-(distLow * distLow) / (2 * SIGMA * SIGMA));
            float factorMid = (float) Math.exp(-(distMid * distMid) / (2 * SIGMA * SIGMA));
            float factorHigh = (float) Math.exp(-(distHigh * distHigh) / (2 * SIGMA * SIGMA));

            // 🔴 加权合成 + 基底能量
            float barEnergy = mEnergyLow * factorLow
                    + mEnergyMid * factorMid
                    + mEnergyHigh * factorHigh;

            // 🔴 加基底：让所有柱至少有 15% 的基础高度
            barEnergy = Math.max(barEnergy, BASE_ENERGY_FLOOR);
            barEnergy = Math.min(barEnergy, 1.0f);

            mBarEnergies[i] = barEnergy;
            mTargetHeights[i] = maxHeight * barEnergy;
            mTargetHeights[i] = Math.min(mTargetHeights[i], maxHeight);
            mAmplitudeLevels[i] = barEnergy;
        }

        // 🔴 直接驱动动画（不再 cancel 重开，减少延迟）
        driveAnimation();
    }

    /**
     * 自动校准三个频带的中心位置
     */
    private void performAutoCalibrate() {
        int barCount = BAR_COUNT;
        int lowBoundary = barCount / 3;
        int midBoundary = lowBoundary * 2;

        int bestLow = 3, bestMid = 11, bestHigh = 18;
        float maxLow = 0, maxMid = 0, maxHigh = 0;

        int samples = Math.max(1, mCalibrateSampleCount);

        for (int i = 0; i < lowBoundary; i++) {
            float avg = mBandEnergyAccum[i] / samples;
            if (avg > maxLow) {
                maxLow = avg;
                bestLow = i;
            }
        }

        for (int i = lowBoundary; i < midBoundary; i++) {
            float avg = mBandEnergyAccum[i] / samples;
            if (avg > maxMid) {
                maxMid = avg;
                bestMid = i;
            }
        }

        for (int i = midBoundary; i < barCount; i++) {
            float avg = mBandEnergyAccum[i] / samples;
            if (avg > maxHigh) {
                maxHigh = avg;
                bestHigh = i;
            }
        }

        // 🔴 过渡速度从 0.3 提到 0.5
        float transitionSpeed = 0.5f;
        mAutoBandLowCenter = (int) (mAutoBandLowCenter * (1f - transitionSpeed) + bestLow * transitionSpeed);
        mAutoBandMidCenter = (int) (mAutoBandMidCenter * (1f - transitionSpeed) + bestMid * transitionSpeed);
        mAutoBandHighCenter = (int) (mAutoBandHighCenter * (1f - transitionSpeed) + bestHigh * transitionSpeed);

        mAutoBandLowCenter = Math.max(0, Math.min(barCount - 1, mAutoBandLowCenter));
        mAutoBandMidCenter = Math.max(0, Math.min(barCount - 1, mAutoBandMidCenter));
        mAutoBandHighCenter = Math.max(0, Math.min(barCount - 1, mAutoBandHighCenter));

        // 清零累计
        for (int i = 0; i < barCount; i++) {
            mBandEnergyAccum[i] = 0;
        }
    }

    // 🔴 改：不再每次 cancel 重开 Animator，改为直接驱动
    private void driveAnimation() {
        if (mAnimator != null && mAnimator.isRunning()) {
            mAnimator.cancel();
        }
        mAnimator = ValueAnimator.ofFloat(0f, 1f);
        mAnimator.setDuration(ANIMATION_DURATION);
        mAnimator.addUpdateListener(animation -> {
            for (int i = 0; i < BAR_COUNT; i++) {
                // 🔴 0.7f → 更干脆，几乎直接到位
                mBarHeights[i] += (mTargetHeights[i] - mBarHeights[i]) * 0.70f;
            }
            postInvalidate();
        });
        mAnimator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mBarHeights == null) return;

        checkColorCycleSwitch();
        final int width = getWidth();
        final int height = getHeight();
        final float barWidth = width / (float) BAR_COUNT;
        final float gap = barWidth * 0.2f;
        final float barUnitWidth = barWidth - gap;
        final float MIN_HEIGHT_FOR_STRIPES = 20f;

        for (int i = 0; i < BAR_COUNT; i++) {
            float left = i * barWidth + gap / 2;
            float right = left + barUnitWidth;
            float barHeight = Math.min(mBarHeights[i], height * 0.9f);
            float top = height - barHeight;

            int color = getDynamicColor(mAmplitudeLevels[i], colorSchemes[currentSchemeIndex]);
            mBarPaint.setColor(color);

            if (mShowStripes && barHeight >= MIN_HEIGHT_FOR_STRIPES) {
                float segmentHeightWithSpacing = mSegmentSpacingFixed * 2;
                int actualSegmentCount = (int) (barHeight / segmentHeightWithSpacing);
                actualSegmentCount = Math.min(actualSegmentCount, mSegmentCount);
                actualSegmentCount = Math.max(1, actualSegmentCount);

                float actualSegmentHeight = (barHeight - (actualSegmentCount - 1) * mSegmentSpacingFixed) / actualSegmentCount;

                for (int segment = 0; segment < actualSegmentCount; segment++) {
                    float segmentTop = height - (segment + 1) * (actualSegmentHeight + mSegmentSpacingFixed);
                    float segmentBottom = segmentTop + actualSegmentHeight;
                    if (segmentTop >= top) {
                        canvas.drawRect(left, segmentTop, right, segmentBottom, mBarPaint);
                    }
                }
            } else {
                canvas.drawRect(left, top, right, height, mBarPaint);
            }
        }
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
        int alpha = (int) (Color.alpha(startColor) + (Color.alpha(endColor) - Color.alpha(startColor)) * ratio);
        int red = (int) (Color.red(startColor) + (Color.red(endColor) - Color.red(startColor)) * ratio);
        int green = (int) (Color.green(startColor) + (Color.green(endColor) - Color.green(startColor)) * ratio);
        int blue = (int) (Color.blue(startColor) + (Color.blue(endColor) - Color.blue(startColor)) * ratio);
        return Color.argb(alpha, red, green, blue);
    }

    public void reset() {
        for (int i = 0; i < BAR_COUNT; i++) {
            mBarHeights[i] = 0;
            mTargetHeights[i] = 0;
            mAmplitudeLevels[i] = 0;
            mBarEnergies[i] = 0;
        }
        mEnergyLow = 0;
        mEnergyMid = 0;
        mEnergyHigh = 0;
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
