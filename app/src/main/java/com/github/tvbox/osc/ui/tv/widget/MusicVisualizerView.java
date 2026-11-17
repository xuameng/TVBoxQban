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
    private static final int ANIMATION_DURATION = 30;

    // 颜色
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

    // ============ 三主音柱方案 ============
    // 三个频带中心（可自动校准）
    private int mCenterLow = 3;
    private int mCenterMid = 11;
    private int mCenterHigh = 18;

    // 三个频带的能量（独立快起快落）
    private float mEnergyLow = 0f;
    private float mEnergyMid = 0f;
    private float mEnergyHigh = 0f;

    // 响应参数
    private static final float ATTACK = 0.8f;
    private static final float RELEASE = 0.35f;
    private static final float DISTANCE_SIGMA = 0.28f;   // 每个浪的影响范围
    private static final float BLEND_RATIO = 0.6f;       // 多浪叠加时的融合比例
    private static final float BASE_FLOOR = 0.10f;       // 所有柱的最小基底

    // 自动校准
    private final float[] mEnergyAccum = new float[BAR_COUNT];
    private int mCalibrateCount = 0;
    private long mLastCalibrateTime = 0;
    private static final int CALIBRATE_FRAMES = 15;
    private static final long CALIBRATE_INTERVAL_MS = 1000;
    // ============================================

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
        if (volumeLevel <= 0.001f) { reset(); return; }

        // ---------- 1. 计算每个柱的频谱能量 ----------
        float[] magnitudes = new float[BAR_COUNT];
        for (int i = 0; i < BAR_COUNT; i++) {
            int idx = (i < BAR_COUNT / 3) ? (2 + i * 2) : (2 + (BAR_COUNT / 3) * 2 + (i - BAR_COUNT / 3) * 4);
            if (idx < fft.length - 1) {
                byte rfk = fft[idx], ifk = fft[idx + 1];
                magnitudes[i] = (rfk * rfk + ifk * ifk);
            }
        }

        // ---------- 2. 累计能量用于校准 ----------
        for (int i = 0; i < BAR_COUNT; i++) mEnergyAccum[i] += magnitudes[i];
        mCalibrateCount++;
        long now = System.currentTimeMillis();
        if (mCalibrateCount >= CALIBRATE_FRAMES && now - mLastCalibrateTime > CALIBRATE_INTERVAL_MS) {
            autoCalibrate();
            mCalibrateCount = 0;
            mLastCalibrateTime = now;
        }

        // ---------- 3. 计算三个频带的能量 ----------
        // 低频带：centerLow ±3
        float lowE = 0;
        for (int i = Math.max(0, mCenterLow - 3); i <= Math.min(BAR_COUNT - 1, mCenterLow + 3); i++)
            if (magnitudes[i] > lowE) lowE = magnitudes[i];

        // 中频带：centerMid ±4
        float midE = 0;
        for (int i = Math.max(0, mCenterMid - 4); i <= Math.min(BAR_COUNT - 1, mCenterMid + 4); i++)
            if (magnitudes[i] > midE) midE = magnitudes[i];

        // 高频带：centerHigh ±3
        float highE = 0;
        for (int i = Math.max(0, mCenterHigh - 3); i <= Math.min(BAR_COUNT - 1, mCenterHigh + 3); i++)
            if (magnitudes[i] > highE) highE = magnitudes[i];

        // 归一化
        float lowN = Math.min(lowE / MAX_AMPLITUDE, 1f) * volumeLevel;
        float midN = Math.min(midE / MAX_AMPLITUDE, 1f) * volumeLevel;
        float highN = Math.min(highE / MAX_AMPLITUDE, 1f) * volumeLevel;

        // ---------- 4. 快起快落 ----------
        mEnergyLow = (lowN > mEnergyLow) ? mEnergyLow + (lowN - mEnergyLow) * ATTACK : mEnergyLow + (lowN - mEnergyLow) * RELEASE;
        mEnergyMid = (midN > mEnergyMid) ? mEnergyMid + (midN - mEnergyMid) * ATTACK : mEnergyMid + (midN - mEnergyMid) * RELEASE;
        mEnergyHigh = (highN > mEnergyHigh) ? mEnergyHigh + (highN - mEnergyHigh) * ATTACK : mEnergyHigh + (highN - mEnergyHigh) * RELEASE;

        // ---------- 5. 每个柱 = 三浪叠加 + 基底 ----------
        float maxH = getHeight() * 0.95f;

        for (int i = 0; i < BAR_COUNT; i++) {
            // 三个浪各自的距离衰减
            float dLow = Math.abs(i - mCenterLow);
            float dMid = Math.abs(i - mCenterMid);
            float dHigh = Math.abs(i - mCenterHigh);

            float fLow = (float) Math.exp(-(dLow * dLow) / (2 * DISTANCE_SIGMA * DISTANCE_SIGMA));
            float fMid = (float) Math.exp(-(dMid * dMid) / (2 * DISTANCE_SIGMA * DISTANCE_SIGMA));
            float fHigh = (float) Math.exp(-(dHigh * dHigh) / (2 * DISTANCE_SIGMA * DISTANCE_SIGMA));

            // 每浪贡献 = 该浪能量 × 距离因子
            float contribution = mEnergyLow * fLow
                               + mEnergyMid * fMid
                               + mEnergyHigh * fHigh;

            // 融合：如果多个浪同时覆盖，加强叠加效果
            int coveringWaves = 0;
            if (fLow > 0.1f) coveringWaves++;
            if (fMid > 0.1f) coveringWaves++;
            if (fHigh > 0.1f) coveringWaves++;
            if (coveringWaves > 1) {
                contribution *= (1f + (coveringWaves - 1) * BLEND_RATIO);
            }

            // 加基底（保证所有柱都在动）
            contribution = Math.max(contribution, BASE_FLOOR);
            contribution = Math.min(contribution, 1.0f);

            mTargetHeights[i] = maxH * contribution;
            mAmplitudeLevels[i] = contribution;
        }

        driveAnimation();
    }

    /** 自动校准：在三个频段内分别找能量最高的柱 */
    private void autoCalibrate() {
        int third = BAR_COUNT / 3;
        int bestLow = 3, bestMid = 11, bestHigh = 18;
        float maxL = 0, maxM = 0, maxH = 0;
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
            if (avg > maxH) { maxH = avg; bestHigh = i; }
        }

        // 平滑过渡
        float t = 0.4f;
        mCenterLow = (int)(mCenterLow * (1 - t) + bestLow * t);
        mCenterMid = (int)(mCenterMid * (1 - t) + bestMid * t);
        mCenterHigh = (int)(mCenterHigh * (1 - t) + bestHigh * t);

        mCenterLow = Math.max(0, Math.min(BAR_COUNT - 1, mCenterLow));
        mCenterMid = Math.max(0, Math.min(BAR_COUNT - 1, mCenterMid));
        mCenterHigh = Math.max(0, Math.min(BAR_COUNT - 1, mCenterHigh));

        // 清零
        for (int i = 0; i < BAR_COUNT; i++) mEnergyAccum[i] = 0;
    }

    private void driveAnimation() {
        if (mAnimator != null && mAnimator.isRunning()) mAnimator.cancel();
        mAnimator = ValueAnimator.ofFloat(0f, 1f);
        mAnimator.setDuration(ANIMATION_DURATION);
        mAnimator.addUpdateListener(animation -> {
            for (int i = 0; i < BAR_COUNT; i++) {
                mBarHeights[i] += (mTargetHeights[i] - mBarHeights[i]) * 0.65f;
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

        final int width = getWidth(), height = getHeight();
        final float barWidth = width / (float) BAR_COUNT;
        final float gap = barWidth * 0.2f;
        final float barUnitWidth = barWidth - gap;
        final float MIN_H = 20f;

        for (int i = 0; i < BAR_COUNT; i++) {
            float left = i * barWidth + gap / 2;
            float right = left + barUnitWidth;
            float barH = Math.min(mBarHeights[i], height * 0.9f);
            float top = height - barH;

            int color = getDynamicColor(mAmplitudeLevels[i], colorSchemes[currentSchemeIndex]);
            mBarPaint.setColor(color);

            if (mShowStripes && barH >= MIN_H) {
                float segWithSpace = mSegmentSpacingFixed * 2;
                int segCount = Math.min((int)(barH / segWithSpace), mSegmentCount);
                segCount = Math.max(1, segCount);
                float segH = (barH - (segCount - 1) * mSegmentSpacingFixed) / segCount;

                for (int s = 0; s < segCount; s++) {
                    float sTop = height - (s + 1) * (segH + mSegmentSpacingFixed);
                    float sBot = sTop + segH;
                    if (sTop >= top) canvas.drawRect(left, sTop, right, sBot, mBarPaint);
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

    private int getDynamicColor(float amp, int[] scheme) {
        amp = Math.max(0f, Math.min(1f, amp));
        if (amp < 0.3f) return interpolateColor(amp / 0.3f, scheme[0], scheme[1]);
        else return interpolateColor((amp - 0.3f) / 0.7f, scheme[1], scheme[2]);
    }

    private int interpolateColor(float r, int sc, int ec) {
        return Color.argb(
                (int)(Color.alpha(sc) + (Color.alpha(ec) - Color.alpha(sc)) * r),
                (int)(Color.red(sc) + (Color.red(ec) - Color.red(sc)) * r),
                (int)(Color.green(sc) + (Color.green(ec) - Color.green(sc)) * r),
                (int)(Color.blue(sc) + (Color.blue(ec) - Color.blue(sc)) * r)
        );
    }

    public void reset() {
        for (int i = 0; i < BAR_COUNT; i++) {
            mBarHeights[i] = 0; mTargetHeights[i] = 0; mAmplitudeLevels[i] = 0;
        }
        mEnergyLow = mEnergyMid = mEnergyHigh = 0f;
        postInvalidate();
    }

    public void release() {
        if (mAnimator != null) { mAnimator.cancel(); mAnimator.removeAllUpdateListeners(); }
        reset();
    }
}
