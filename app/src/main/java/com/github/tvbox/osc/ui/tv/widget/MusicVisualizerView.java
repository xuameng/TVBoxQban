package com.github.tvbox.osc.ui.tv.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

public class MusicVisualizerView extends View {
    private static final String TAG = "xuameng_MusicVis";
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
    private final float[] mAmplitudeLevels = new float[BAR_COUNT];

    // ============ 峰值检测 ============
    private int mCenterLow = 3;
    private int mCenterMid = 10;
    private int mCenterHigh = 17;

    private float mPeakLow = 0f;
    private float mPeakMid = 0f;
    private float mPeakHigh = 0f;

    // 自适应：跟踪每个频带的历史最大值
    private float mMaxSeenLow = 1f;
    private float mMaxSeenMid = 1f;
    private float mMaxSeenHigh = 1f;

    private static final float PEAK_DECAY = 0.18f;
    private static final float DISTANCE_SIGMA = 0.22f;

    // FFT bin 范围
    private static final int LOW_BIN_START = 2;
    private static final int LOW_BIN_END = 10;
    private static final int MID_BIN_START = 11;
    private static final int MID_BIN_END = 20;
    private static final int HIGH_BIN_START = 21;
    private static final int HIGH_BIN_END = 32;

    // 校准
    private final float[] mEnergyAccum = new float[BAR_COUNT];
    private int mCalibrateCount = 0;
    private long mLastCalibrateTime = 0;
    private static final int CALIBRATE_FRAMES = 45;
    private static final long CALIBRATE_INTERVAL_MS = 4000;

    // 调试
    private int mDebugFrameCount = 0;
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
        if (fft == null) {
            Log.d(TAG, "fft is null");
            return;
        }
        if (fft.length < HIGH_BIN_END * 2 + 2) {
            Log.d(TAG, "fft too short: " + fft.length);
            return;
        }
        if (volumeLevel <= 0.001f) {
            reset();
            return;
        }

        // ---------- 1. 读 FFT，取各频带最大 bin ----------
        float peak_bin_low = 0, peak_bin_mid = 0, peak_bin_high = 0;

        for (int i = LOW_BIN_START; i <= LOW_BIN_END; i++) {
            int idx = i * 2;
            if (idx + 1 >= fft.length) break;
            byte rfk = fft[idx], ifk = fft[idx + 1];
            float mag = ((rfk & 0xFF) * (rfk & 0xFF)) + ((ifk & 0xFF) * (ifk & 0xFF));
            if (mag > peak_bin_low) peak_bin_low = mag;
        }
        for (int i = MID_BIN_START; i <= MID_BIN_END; i++) {
            int idx = i * 2;
            if (idx + 1 >= fft.length) break;
            byte rfk = fft[idx], ifk = fft[idx + 1];
            float mag = ((rfk & 0xFF) * (rfk & 0xFF)) + ((ifk & 0xFF) * (ifk & 0xFF));
            if (mag > peak_bin_mid) peak_bin_mid = mag;
        }
        for (int i = HIGH_BIN_START; i <= HIGH_BIN_END; i++) {
            int idx = i * 2;
            if (idx + 1 >= fft.length) break;
            byte rfk = fft[idx], ifk = fft[idx + 1];
            float mag = ((rfk & 0xFF) * (rfk & 0xFF)) + ((ifk & 0xFF) * (ifk & 0xFF));
            if (mag > peak_bin_high) peak_bin_high = mag;
        }

        // ---------- 2. 自适应归一化（关键修复！） ----------
        // 跟踪历史最大值，用 99th percentile 做归一化
        mMaxSeenLow  = Math.max(mMaxSeenLow,  peak_bin_low);
        mMaxSeenMid  = Math.max(mMaxSeenMid,  peak_bin_mid);
        mMaxSeenHigh = Math.max(mMaxSeenHigh, peak_bin_high);

        // 缓慢衰减最大值（防止一开始的尖峰永远影响归一化）
        mMaxSeenLow  *= 0.9995f;
        mMaxSeenMid  *= 0.9995f;
        mMaxSeenHigh *= 0.9995f;

        float normLow  = (mMaxSeenLow  > 1f)  ? (peak_bin_low  / mMaxSeenLow)  : 0f;
        float normMid  = (mMaxSeenMid  > 1f)  ? (peak_bin_mid  / mMaxSeenMid)  : 0f;
        float normHigh = (mMaxSeenHigh > 1f)  ? (peak_bin_high / mMaxSeenHigh) : 0f;

        // 音量加权
        normLow  *= volumeLevel;
        normMid  *= volumeLevel;
        normHigh *= volumeLevel;

        // ---------- 3. 峰值检测 ----------
        if (normLow > mPeakLow) {
            mPeakLow = normLow;
        } else {
            mPeakLow -= mPeakLow * PEAK_DECAY;
            if (mPeakLow < 0.01f) mPeakLow = 0.01f;
        }

        if (normMid > mPeakMid) {
            mPeakMid = normMid;
        } else {
            mPeakMid -= mPeakMid * PEAK_DECAY;
            if (mPeakMid < 0.01f) mPeakMid = 0.01f;
        }

        if (normHigh > mPeakHigh) {
            mPeakHigh = normHigh;
        } else {
            mPeakHigh -= mPeakHigh * PEAK_DECAY;
            if (mPeakHigh < 0.01f) mPeakHigh = 0.01f;
        }

        // ---------- 4. 调试日志（前100帧） ----------
        mDebugFrameCount++;
        if (mDebugFrameCount <= 100) {
            Log.d(TAG, String.format("frame=%d vol=%.3f low=%.3f mid=%.3f high=%.3f peak=%.3f/%.3f/%.3f maxSeen=%.0f/%.0f/%.0f",
                    mDebugFrameCount, volumeLevel,
                    normLow, normMid, normHigh,
                    mPeakLow, mPeakMid, mPeakHigh,
                    mMaxSeenLow, mMaxSeenMid, mMaxSeenHigh));
        }

        // ---------- 5. 校准 ----------
        for (int i = 0; i < BAR_COUNT; i++) {
            int idx = (i < BAR_COUNT / 3) ? (2 + i * 2) : (2 + (BAR_COUNT / 3) * 2 + (i - BAR_COUNT / 3) * 4);
            if (idx < fft.length - 1) {
                byte rfk = fft[idx], ifk = fft[idx + 1];
                mEnergyAccum[i] += ((rfk & 0xFF) * (rfk & 0xFF)) + ((ifk & 0xFF) * (ifk & 0xFF));
            }
        }
        mCalibrateCount++;
        long now = System.currentTimeMillis();
        if (mCalibrateCount >= CALIBRATE_FRAMES && now - mLastCalibrateTime > CALIBRATE_INTERVAL_MS) {
            autoCalibrate();
            mCalibrateCount = 0;
            mLastCalibrateTime = now;
        }

        // ---------- 6. 每根柱高度 ----------
        float maxH = getHeight() * 0.95f;
        if (maxH <= 0) maxH = 100f; // 防止 height 还没测量

        for (int i = 0; i < BAR_COUNT; i++) {
            float dLow  = Math.abs(i - mCenterLow)  / (float) BAR_COUNT;
            float dMid  = Math.abs(i - mCenterMid)  / (float) BAR_COUNT;
            float dHigh = Math.abs(i - mCenterHigh) / (float) BAR_COUNT;

            float wLow  = (float) Math.exp(-(dLow  * dLow)  / (2 * DISTANCE_SIGMA * DISTANCE_SIGMA));
            float wMid  = (float) Math.exp(-(dMid  * dMid)  / (2 * DISTANCE_SIGMA * DISTANCE_SIGMA));
            float wHigh = (float) Math.exp(-(dHigh * dHigh) / (2 * DISTANCE_SIGMA * DISTANCE_SIGMA));

            float barRatio = mPeakLow * wLow + mPeakMid * wMid + mPeakHigh * wHigh;
            barRatio = Math.max(barRatio, 0.05f);
            barRatio = Math.min(barRatio, 1.0f);

            mBarHeights[i] = maxH * barRatio;
            mAmplitudeLevels[i] = barRatio;
        }

        postInvalidate();
    }

    private void autoCalibrate() {
        int third = BAR_COUNT / 3;
        int bestLow = 3, bestMid = 10, bestHigh = 17;
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

        mCenterLow  = bestLow;
        mCenterMid  = bestMid;
        mCenterHigh = bestHigh;

        for (int i = 0; i < BAR_COUNT; i++) mEnergyAccum[i] = 0;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        checkColorCycleSwitch();

        final int width = getWidth(), height = getHeight();
        if (width == 0 || height == 0) return;
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
            mBarHeights[i] = 0;
            mAmplitudeLevels[i] = 0;
        }
        mPeakLow = mPeakMid = mPeakHigh = 0f;
        postInvalidate();
    }

    public void release() {
        reset();
    }
}
