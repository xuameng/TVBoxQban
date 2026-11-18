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
    private static final int ANIMATION_DURATION = 50;

    /**
     * 峰值衰减率
     */
    private static final float BAR_PEAK_DECAY = 0.55f;

    /**
     * 最低可见高度比例
     */
    private static final float MIN_VISIBLE_RATIO = 0.02f;

    /**
     * 动画插值因子
     */
    private static final float LERP_FACTOR = 0.40f;

    // 颜色系统
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
    private final float[] mBarPeaks = new float[BAR_COUNT];

    private ValueAnimator mAnimator;

    public MusicVisualizerView(Context context) { super(context); init(); }
    public MusicVisualizerView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public MusicVisualizerView(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); init(); }

    private void init() {
        mBarPaint.setStyle(Paint.Style.FILL);
        refreshColorSchemes();
    }

    public void setShowStripes(boolean show) { mShowStripes = show; postInvalidate(); }
    public void setSegmentCount(int count) { mSegmentCount = Math.max(1, count); postInvalidate(); }
    public void setSegmentSpacingFixed(float spacing) { mSegmentSpacingFixed = Math.max(0f, spacing); postInvalidate(); }

    public void updateVisualizer(byte[] fft, float volumeLevel) {
        if (fft == null || fft.length < BAR_COUNT * 2 + 2) return;

        if (volumeLevel <= 0.001f) {
            reset();
            return;
        }

        float maxH = getHeight();
        if (maxH <= 0) maxH = 100f;

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

                // ★ 中高频 weight 拉大
                float weight;
                if (i < BAR_COUNT / 4) {
                    weight = 1.5f;
                } else if (i < BAR_COUNT / 2) {
                    weight = 3.5f;
                } else {
                    float freqFactor = (float) Math.pow(2.0, (i - BAR_COUNT / 2) / 2.0);
                    weight = 7.5f * freqFactor;
                }

                // ★ 方案1：先算能量（不乘音量）
                float energy = magnitude / (float) MAX_AMPLITUDE;
                energy = Math.min(energy * weight, 1.0f);

                // ★ 中高频补偿（只补偿能量，不叠加音量）
                if (i >= BAR_COUNT / 4 && i < BAR_COUNT / 2) {
                    energy = Math.min(energy * 1.8f, 1.0f);
                } else if (i >= BAR_COUNT / 2) {
                    energy = Math.min(energy * 2.5f, 1.0f);
                }

                // 峰值保持
                if (energy > mBarPeaks[i]) {
                    mBarPeaks[i] = energy;
                }

                // 峰值衰减
                float decay = BAR_PEAK_DECAY;
                if (i >= BAR_COUNT / 2) {
                    decay = 0.25f;
                } else if (i >= BAR_COUNT / 4) {
                    decay = 0.2f;
                }
                mBarPeaks[i] -= mBarPeaks[i] * decay;
                if (mBarPeaks[i] < 0.003f) mBarPeaks[i] = 0f;

                // ★ 方案1：音量只参与最终高度
                float heightRatio = (float) Math.pow(Math.min(mBarPeaks[i], 1.0f), 0.6f);
                heightRatio = heightRatio * volumeLevel;
                heightRatio = Math.max(heightRatio, MIN_VISIBLE_RATIO);

                mTargetHeights[i] = Math.min(heightRatio * maxH, maxH * 0.95f);
                mAmplitudeLevels[i] = heightRatio;
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
            for (int i = 0; i < BAR_COUNT; i++) {
                mBarHeights[i] += (mTargetHeights[i] - mBarHeights[i]) * LERP_FACTOR;
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
        if (width == 0 || height == 0) return;

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
        ratio = Math.max(0f, Math.min(1f, ratio));
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
            mBarPeaks[i] = 0;
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
