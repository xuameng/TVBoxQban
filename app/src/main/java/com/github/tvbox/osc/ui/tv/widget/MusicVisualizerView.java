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
    private static final int ANIMATION_DURATION = 60;

    private int mCenterLow = 4;
    private int mCenterMid = 11;
    private int mCenterHigh = 18;

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

    private float mPeakLow = 0.02f;
    private float mPeakMid = 0.02f;
    private float mPeakHigh = 0.02f;

    private static final float PEAK_DECAY_LOW = 0.45f;
    private static final float PEAK_DECAY_MID = 0.45f;
    private static final float PEAK_DECAY_HIGH = 0.45f;

    private static final float DISTANCE_SIGMA = 0.18f;

    private final float[] mEnergyAccum = new float[BAR_COUNT];
    private int mCalibrateCount = 0;
    private long mLastCalibrateTime = 0;
    private static final int CALIBRATE_FRAMES = 45;
    private static final long CALIBRATE_INTERVAL_MS = 4000;

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

        // ---------- 1. 读三频带峰值 ----------
        float peakLow = 0, peakMid = 0, peakHigh = 0;

        for (int i = 2; i <= 10; i++) {
            int idx = i * 2;
            if (idx + 1 >= fft.length) break;
            byte rfk = fft[idx], ifk = fft[idx + 1];
            float mag = (rfk * rfk + ifk * ifk);
            if (mag > peakLow) peakLow = mag;
        }
        for (int i = 11; i <= 20; i++) {
            int idx = i * 2;
            if (idx + 1 >= fft.length) break;
            byte rfk = fft[idx], ifk = fft[idx + 1];
            float mag = (rfk * rfk + ifk * ifk);
            if (mag > peakMid) peakMid = mag;
        }
        for (int i = 21; i <= 32; i++) {
            int idx = i * 2;
            if (idx + 1 >= fft.length) break;
            byte rfk = fft[idx], ifk = fft[idx + 1];
            float mag = (rfk * rfk + ifk * ifk);
            if (mag > peakHigh) peakHigh = mag;
        }

        // ---------- 2. 归一化 ----------
        float normLow  = Math.min(peakLow  / (float) MAX_AMPLITUDE, 1.0f);
        float normMid  = Math.min(peakMid  / (float) MAX_AMPLITUDE, 1.0f);
        float normHigh = Math.min(peakHigh / (float) MAX_AMPLITUDE, 1.0f);

        normLow  = normLow  * (0.2f + 0.8f * volumeLevel);
        normMid  = normMid  * (0.2f + 0.8f * volumeLevel);
        normHigh = normHigh * (0.2f + 0.8f * volumeLevel);

        // ---------- 3. 峰值检测 ----------
        if (normLow > mPeakLow) mPeakLow = normLow;
        if (normMid > mPeakMid) mPeakMid = normMid;
        if (normHigh > mPeakHigh) mPeakHigh = normHigh;

        mPeakLow -= mPeakLow * PEAK_DECAY_LOW;
        mPeakMid -= mPeakMid * PEAK_DECAY_MID;
        mPeakHigh -= mPeakHigh * PEAK_DECAY_HIGH;
        if (mPeakLow < 0.02f) mPeakLow = 0.02f;
        if (mPeakMid < 0.02f) mPeakMid = 0.02f;
        if (mPeakHigh < 0.02f) mPeakHigh = 0.02f;

        // ---------- 4. 校准 ----------
        for (int i = 0; i < BAR_COUNT; i++) {
            int barIndex;
            if (i < BAR_COUNT / 3) {
                barIndex = 2 + i * 2;
            } else {
                barIndex = 2 + (BAR_COUNT / 3) * 2 + (i - BAR_COUNT / 3) * 4;
            }
            if (barIndex < fft.length - 1) {
                byte rfk = fft[barIndex], ifk = fft[barIndex + 1];
                mEnergyAccum[i] += (rfk * rfk + ifk * ifk);
            }
        }
        mCalibrateCount++;
        long now = System.currentTimeMillis();
        if (mCalibrateCount >= CALIBRATE_FRAMES && now - mLastCalibrateTime > CALIBRATE_INTERVAL_MS) {
            autoCalibrate();
            mCalibrateCount = 0;
            mLastCalibrateTime = now;
        }

        // ---------- 5. 计算目标高度 ----------
        // ★ 改1：maxH 直接取满高度，不再留 5% 余量
        float maxH = getHeight();
        if (maxH <= 0) maxH = 100f;

        final float SHARP = 2.0f;
        final float HEIGHT_GAMMA = 1.5f;
        // ★ 改2：FLOOR 改为 0，彻底去掉初始/静音时的底高度
        final float FLOOR = 0.0f;

        for (int i = 0; i < BAR_COUNT; i++) {
            float dLow  = Math.abs(i - mCenterLow)  / (float) BAR_COUNT;
            float dMid  = Math.abs(i - mCenterMid)  / (float) BAR_COUNT;
            float dHigh = Math.abs(i - mCenterHigh) / (float) BAR_COUNT;

            float wLow  = (float) Math.exp(-(dLow  * dLow)  / (2 * DISTANCE_SIGMA * DISTANCE_SIGMA));
            float wMid  = (float) Math.exp(-(dMid  * dMid)  / (2 * DISTANCE_SIGMA * DISTANCE_SIGMA));
            float wHigh = (float) Math.exp(-(dHigh * dHigh) / (2 * DISTANCE_SIGMA * DISTANCE_SIGMA));

            wLow  = (float) Math.pow(wLow,  SHARP);
            wMid  = (float) Math.pow(wMid,  SHARP);
            wHigh = (float) Math.pow(wHigh, SHARP);

            float totalWeight = wLow + wMid + wHigh;

            float combinedRatio = 0f;
            if (totalWeight > 0.001f) {
                combinedRatio = (mPeakLow * wLow + mPeakMid * wMid + mPeakHigh * wHigh) / totalWeight;
            }

            combinedRatio = Math.max(combinedRatio, FLOOR);

            float heightRatio = (float) Math.pow(combinedRatio, HEIGHT_GAMMA);

            mTargetHeights[i] = Math.min(heightRatio * maxH, maxH);
            mAmplitudeLevels[i] = Math.min(heightRatio * 3.0f, 1.0f);
        }

        startAnimation();
    }

    private void autoCalibrate() {
        int third = BAR_COUNT / 3;
        int bestLow = 4, bestMid = 11, bestHigh = 18;
        float maxL = 0, maxM = 0, maxHCal = 0;
        int samples = Math.max(1, mCalibrateCount);

        for (int i = 0; i < third; i++) {
            float avg = mEnergyAccum[i] / samples;
            if (avg > maxL) { maxL = avg; bestLow = i; }
        }
        for (int i = third; i < third * 2; i++) {
            float avg = mEnergyAccum[i] / samples;
            if (avg > maxM) { maxM = avg; bestMid = i; }
        }
        for (int i = third * 2; i < BAR_COUNT; i++) {
            float avg = mEnergyAccum[i] / samples;
            if (avg > maxHCal) { maxHCal = avg; bestHigh = i; }
        }

        mCenterLow  = bestLow;
        mCenterMid  = bestMid;
        mCenterHigh = bestHigh;

        for (int i = 0; i < BAR_COUNT; i++) mEnergyAccum[i] = 0;
    }

    private void startAnimation() {
        if (mAnimator != null) {
            mAnimator.cancel();
        }
        mAnimator = ValueAnimator.ofFloat(0f, 1f);
        mAnimator.setDuration(ANIMATION_DURATION);
        mAnimator.addUpdateListener(animation -> {
            float lerpFactor = 0.65f;
            for (int i = 0; i < BAR_COUNT; i++) {
                mBarHeights[i] += (mTargetHeights[i] - mBarHeights[i]) * lerpFactor;
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
            // ★ 改3：上限从 height * 0.9f 改为 height，满高可达顶
            float barHeight = Math.min(mBarHeights[i], height);
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
        }
        mPeakLow = mPeakMid = mPeakHigh = 0.02f;
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
