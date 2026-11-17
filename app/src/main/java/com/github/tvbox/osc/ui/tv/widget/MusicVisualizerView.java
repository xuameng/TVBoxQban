package com.github.tvbox.osc.ui.tv.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class MusicVisualizerView extends View {
    private static final int MAX_AMPLITUDE = 9222;
    private static final int BAR_COUNT = 22;

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

    // ============ 核心设计：全局脉冲 + 三浪高度调制 ============

    // 三个浪中心
    private int mCenterLow = 3;
    private int mCenterMid = 11;
    private int mCenterHigh = 18;

    // 全局瞬态脉冲（所有柱共享，驱动"跳"）
    private float mGlobalPulse = 0f;

    // 三个频带的能量（只决定浪的高度，不决定是否跳）
    private float mEnergyLow = 0f;
    private float mEnergyMid = 0f;
    private float mEnergyHigh = 0f;

    // 每个频带的近期平均（用于检测瞬态）
    private float mAvgLow = 1f, mAvgMid = 1f, mAvgHigh = 1f;

    // ===== 参数（已调优） =====
    private static final float PULSE_ATTACK = 0.85f;   // 脉冲上升（快）
    private static final float PULSE_RELEASE = 0.50f;  // 脉冲回落（快！关键）
    private static final float ENERGY_SMOOTH = 0.15f;  // 浪高平滑（慢一点，浪高变化柔和）
    private static final float AVG_DECAY = 0.93f;      // 平均能量遗忘（快=对变化敏感）
    private static final float AVG_LEARN = 0.07f;      // 平均能量学习
    private static final float TRANSIENT_THRESHOLD = 1.25f; // 瞬态触发门槛（低=容易跳）
    private static final float DISTANCE_SIGMA = 0.35f; // 距离衰减（宽=更多柱参与）
    private static final float MIN_PULSE_FLOOR = 0.25f; // 全局最低脉冲（保证所有柱都在动！）
    private static final float VOLUME_WEIGHT = 0.5f;   // 全局音量对脉冲的影响

    // FFT bin 范围
    private static final int LOW_BIN_START = 2,  LOW_BIN_END = 8;
    private static final int MID_BIN_START = 9,  MID_BIN_END = 16;
    private static final int HIGH_BIN_START = 17, HIGH_BIN_END = 24;

    // 校准
    private final float[] mEnergyAccum = new float[BAR_COUNT];
    private int mCalibrateCount = 0;
    private long mLastCalibrateTime = 0;
    private static final int CALIBRATE_FRAMES = 30;
    private static final long CALIBRATE_INTERVAL_MS = 3000;
    // ============================================

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
        if (fft == null || fft.length < HIGH_BIN_END * 2 + 2) return;
        if (volumeLevel <= 0.001f) { reset(); return; }

        // ---------- 1. 读 FFT 各频带 ----------
        float rawLow = 0, rawMid = 0, rawHigh = 0;

        for (int i = LOW_BIN_START; i <= LOW_BIN_END; i++) {
            byte rfk = fft[i * 2], ifk = fft[i * 2 + 1];
            rawLow += (rfk * rfk + ifk * ifk);
        }
        for (int i = MID_BIN_START; i <= MID_BIN_END; i++) {
            byte rfk = fft[i * 2], ifk = fft[i * 2 + 1];
            rawMid += (rfk * rfk + ifk * ifk);
        }
        for (int i = HIGH_BIN_START; i <= HIGH_BIN_END; i++) {
            byte rfk = fft[i * 2], ifk = fft[i * 2 + 1];
            rawHigh += (rfk * rfk + ifk * ifk);
        }

        // ---------- 2. 更新平均能量 ----------
        mAvgLow  = mAvgLow  * AVG_DECAY + rawLow  * AVG_LEARN;
        mAvgMid  = mAvgMid  * AVG_DECAY + rawMid  * AVG_LEARN;
        mAvgHigh = mAvgHigh * AVG_DECAY + rawHigh * AVG_LEARN;
        mAvgLow = Math.max(mAvgLow, 1f);
        mAvgMid = Math.max(mAvgMid, 1f);
        mAvgHigh = Math.max(mAvgHigh, 1f);

        // ---------- 3. 检测瞬态 → 驱动全局脉冲 ----------
        float relLow = rawLow / mAvgLow;
        float relMid = rawMid / mAvgMid;
        float relHigh = rawHigh / mAvgHigh;

        // 任一频带有瞬态 → 触发全局脉冲
        float maxRel = Math.max(Math.max(relLow, relMid), relHigh);
        float transientStrength = 0f;
        if (maxRel > TRANSIENT_THRESHOLD) {
            transientStrength = Math.min((maxRel - TRANSIENT_THRESHOLD) / TRANSIENT_THRESHOLD, 1.0f);
        }

        // 全局脉冲 = 瞬态强度 × 音量权重 + 音量基础
        float pulseTarget = MIN_PULSE_FLOOR + transientStrength * (1.0f - MIN_PULSE_FLOOR);
        pulseTarget = pulseTarget * (VOLUME_WEIGHT + (1f - VOLUME_WEIGHT) * volumeLevel);

        // 快起快落
        if (pulseTarget > mGlobalPulse) {
            mGlobalPulse += (pulseTarget - mGlobalPulse) * PULSE_ATTACK;
        } else {
            mGlobalPulse += (pulseTarget - mGlobalPulse) * PULSE_RELEASE;
        }

        // ---------- 4. 更新三浪能量（决定各浪高度） ----------
        float normLow  = Math.min(rawLow  / MAX_AMPLITUDE, 1f);
        float normMid  = Math.min(rawMid  / MAX_AMPLITUDE, 1f);
        float normHigh = Math.min(rawHigh / MAX_AMPLITUDE, 1f);

        mEnergyLow  += (normLow  - mEnergyLow)  * ENERGY_SMOOTH;
        mEnergyMid  += (normMid  - mEnergyMid)  * ENERGY_SMOOTH;
        mEnergyHigh += (normHigh - mEnergyHigh) * ENERGY_SMOOTH;

        // ---------- 5. 校准 ----------
        for (int i = 0; i < BAR_COUNT; i++) {
            int idx = (i < BAR_COUNT / 3) ? (2 + i * 2) : (2 + (BAR_COUNT / 3) * 2 + (i - BAR_COUNT / 3) * 4);
            if (idx < fft.length - 1) {
                byte rfk = fft[idx], ifk = fft[idx + 1];
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

        // ---------- 6. 每根柱的高度 = 全局脉冲 × 距离加权 ----------
        float maxH = getHeight() * 0.95f;

        for (int i = 0; i < BAR_COUNT; i++) {
            float dLow = Math.abs(i - mCenterLow) / (float) BAR_COUNT;
            float dMid = Math.abs(i - mCenterMid) / (float) BAR_COUNT;
            float dHigh = Math.abs(i - mCenterHigh) / (float) BAR_COUNT;

            // 每个浪对这根柱的"影响力"
            float wLow  = (float) Math.exp(-(dLow  * dLow)  / (2 * DISTANCE_SIGMA * DISTANCE_SIGMA));
            float wMid  = (float) Math.exp(-(dMid  * dMid)  / (2 * DISTANCE_SIGMA * DISTANCE_SIGMA));
            float wHigh = (float) Math.exp(-(dHigh * dHigh) / (2 * DISTANCE_SIGMA * DISTANCE_SIGMA));

            // 加权浪高
            float waveHeight = wLow * mEnergyLow + wMid * mEnergyMid + wHigh * mEnergyHigh;
            // 归一化权重
            float totalW = wLow + wMid + wHigh;
            if (totalW > 0) waveHeight /= totalW;

            // 最终高度 = 全局脉冲 × (基底 + 浪高调制)
            float barRatio = mGlobalPulse * (0.3f + 0.7f * waveHeight);
            barRatio = Math.max(barRatio, MIN_PULSE_FLOOR * 0.5f);
            barRatio = Math.min(barRatio, 1.0f);

            mTargetHeights[i] = maxH * barRatio;
            mAmplitudeLevels[i] = barRatio;
        }

        // ---------- 7. 直接插值 + 绘制（无 ValueAnimator） ----------
        for (int i = 0; i < BAR_COUNT; i++) {
            mBarHeights[i] += (mTargetHeights[i] - mBarHeights[i]) * 0.55f;
        }
        postInvalidate();
    }

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

        float t = 0.3f;
        mCenterLow  = (int)(mCenterLow  * (1 - t) + bestLow  * t);
        mCenterMid  = (int)(mCenterMid  * (1 - t) + bestMid  * t);
        mCenterHigh = (int)(mCenterHigh * (1 - t) + bestHigh * t);
        mCenterLow = Math.max(0, Math.min(BAR_COUNT - 1, mCenterLow));
        mCenterMid = Math.max(0, Math.min(BAR_COUNT - 1, mCenterMid));
        mCenterHigh = Math.max(0, Math.min(BAR_COUNT - 1, mCenterHigh));

        for (int i = 0; i < BAR_COUNT; i++) mEnergyAccum[i] = 0;
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
        mGlobalPulse = 0f;
        mEnergyLow = mEnergyMid = mEnergyHigh = 0f;
        postInvalidate();
    }

    public void release() {
        reset();
    }
}
