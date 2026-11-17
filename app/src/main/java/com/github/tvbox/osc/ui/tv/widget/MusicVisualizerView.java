package com.github.tvbox.osc.ui.tv.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import androidx.annotation.RequiresApi;

public class MusicVisualizerView extends View {
    private static final String TAG = "xuameng_MusicVis";
    private static final int BAR_COUNT = 22;

    // ===== 颜色 =====
    private int[][] colorSchemes = new int[3][3];
    private boolean mShowStripes = true;
    private int mSegmentCount = 20;
    private float mSegmentSpacingFixed;
    private float mLastBaseHue;
    private static final long COLOR_CYCLE_DURATION = 20 * 1000;
    private int currentSchemeIndex = 0;
    private long lastSwitchTime = 0;

    private final Paint mBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] mBarHeights = new float[BAR_COUNT];
    private final float[] mAmplitudeLevels = new float[BAR_COUNT];

    // ===== 峰值检测 =====
    private int mCenterLow = 3;
    private int mCenterMid = 10;
    private int mCenterHigh = 17;

    private float mPeakLow = 0.01f;
    private float mPeakMid = 0.01f;
    private float mPeakHigh = 0.01f;

    private float mMaxSeenLow = 1f;
    private float mMaxSeenMid = 1f;
    private float mMaxSeenHigh = 1f;

    // ===== 核心参数 =====
    private static final float PEAK_DECAY = 0.08f;
    private static final float DISTANCE_SIGMA = 0.28f;
    private static final float HEIGHT_LERP_FACTOR = 0.65f;
    private static final float DYNAMIC_GAIN = 1.6f;

    // ===== FFT bin 范围 =====
    private static final int LOW_BIN_START = 2;
    private static final int LOW_BIN_END = 10;
    private static final int MID_BIN_START = 11;
    private static final int MID_BIN_END = 20;
    private static final int HIGH_BIN_START = 21;
    private static final int HIGH_BIN_END = 32;

    // ===== 校准 =====
    private final float[] mEnergyAccum = new float[BAR_COUNT];
    private int mCalibrateCount = 0;
    private long mLastCalibrateTime = 0;
    private static final int CALIBRATE_FRAMES = 45;
    private static final long CALIBRATE_INTERVAL_MS = 4000;

    // ===== 绘制控制 =====
    private long mLastDrawTime = 0;
    private static final long MIN_DRAW_INTERVAL = 16; // 锁 60fps
    private boolean mIsPaused = false;
    private float mBarWidth, mGap, mBarUnitWidth, mCornerRadius;

    private int mDebugFrameCount = 0;

    // ================================================================

    public MusicVisualizerView(Context context) { super(context); init(); }
    public MusicVisualizerView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public MusicVisualizerView(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); init(); }

    private void init() {
        mBarPaint.setStyle(Paint.Style.FILL);
        mSegmentSpacingFixed = dp2px(2.5f);
        mLastBaseHue = (float) (Math.random() * 360);
        refreshColorSchemes();
    }

    private float dp2px(float dp) {
        return dp * getResources().getDisplayMetrics().density + 0.5f;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) {
            mBarWidth = w / (float) BAR_COUNT;
            mGap = mBarWidth * 0.2f;
            mBarUnitWidth = mBarWidth - mGap;
            mCornerRadius = mBarUnitWidth * 0.25f;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mIsPaused = false;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        release();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        mIsPaused = visibility != VISIBLE;
        if (!mIsPaused) postInvalidateOnAnimation();
    }

    public void setShowStripes(boolean show) { mShowStripes = show; postInvalidateOnAnimation(); }
    public void setSegmentCount(int count) { mSegmentCount = Math.max(1, count); postInvalidateOnAnimation(); }
    public void setSegmentSpacingFixed(float spacingDp) {
        mSegmentSpacingFixed = dp2px(Math.max(0f, spacingDp));
        postInvalidateOnAnimation();
    }

    public void setPaused(boolean paused) {
        mIsPaused = paused;
        if (!paused) postInvalidateOnAnimation();
    }

    // ================================================================
    // 核心：音频数据驱动
    // ================================================================
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void updateVisualizer(byte[] fft, float volumeLevel) {
        if (mIsPaused) return;

        boolean hasValidFft = fft != null && fft.length >= HIGH_BIN_END * 2 + 2;

        // ---------- 1. FFT 读取 + 峰值更新（有数据就做） ----------
        if (hasValidFft) {
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

            // 自适应归一化
            mMaxSeenLow  = Math.max(mMaxSeenLow,  peak_bin_low);
            mMaxSeenMid  = Math.max(mMaxSeenMid,  peak_bin_mid);
            mMaxSeenHigh = Math.max(mMaxSeenHigh, peak_bin_high);
            mMaxSeenLow  *= 0.996f;
            mMaxSeenMid  *= 0.997f;
            mMaxSeenHigh *= 0.998f;

            float normLow  = (mMaxSeenLow  > 1f) ? Math.min(1.0f, (peak_bin_low  / mMaxSeenLow)  * DYNAMIC_GAIN) : 0f;
            float normMid  = (mMaxSeenMid  > 1f) ? Math.min(1.0f, (peak_bin_mid  / mMaxSeenMid)  * DYNAMIC_GAIN) : 0f;
            float normHigh = (mMaxSeenHigh > 1f) ? Math.min(1.0f, (peak_bin_high / mMaxSeenHigh) * DYNAMIC_GAIN) : 0f;

            // volumeLevel 只做温和加权，不为 0 就不影响显示
            float volWeight = (volumeLevel > 0.001f) ? Math.min(volumeLevel * 2f, 1.0f) : 0.3f;
            normLow  *= volWeight;
            normMid  *= volWeight;
            normHigh *= volWeight;

            // 峰值更新
            if (normLow > mPeakLow) mPeakLow = normLow;
            if (normMid > mPeakMid) mPeakMid = normMid;
            if (normHigh > mPeakHigh) mPeakHigh = normHigh;

            // 校准
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

            // 调试日志
            mDebugFrameCount++;
            if (mDebugFrameCount <= 60) {
                Log.d(TAG, String.format("f=%d vol=%.3f norm=%.3f/%.3f/%.3f peak=%.3f/%.3f/%.3f bar0=%.1f",
                        mDebugFrameCount, volumeLevel, normLow, normMid, normHigh,
                        mPeakLow, mPeakMid, mPeakHigh, mBarHeights[0]));
            }
        }

        // ---------- 2. 峰值衰减（始终执行，不锁在 if 里） ----------
        mPeakLow -= mPeakLow * PEAK_DECAY;
        mPeakMid -= mPeakMid * PEAK_DECAY;
        mPeakHigh -= mPeakHigh * PEAK_DECAY;
        if (mPeakLow < 0.01f) mPeakLow = 0.01f;
        if (mPeakMid < 0.01f) mPeakMid = 0.01f;
        if (mPeakHigh < 0.01f) mPeakHigh = 0.01f;

        // ---------- 3. 柱高计算（始终执行） ----------
        float maxH = getHeight() * 0.95f;
        if (maxH <= 0) maxH = dp2px(100f);

        for (int i = 0; i < BAR_COUNT; i++) {
            float dLow  = Math.abs(i - mCenterLow)  / (float) BAR_COUNT;
            float dMid  = Math.abs(i - mCenterMid)  / (float) BAR_COUNT;
            float dHigh = Math.abs(i - mCenterHigh) / (float) BAR_COUNT;

            float wLow  = (float) Math.exp(-(dLow  * dLow)  / (2 * DISTANCE_SIGMA * DISTANCE_SIGMA));
            float wMid  = (float) Math.exp(-(dMid  * dMid)  / (2 * DISTANCE_SIGMA * DISTANCE_SIGMA));
            float wHigh = (float) Math.exp(-(dHigh * dHigh) / (2 * DISTANCE_SIGMA * DISTANCE_SIGMA));

            float totalWeight = wLow + wMid + wHigh + 0.001f;
            float barRatio = (mPeakLow * wLow + mPeakMid * wMid + mPeakHigh * wHigh) / totalWeight;
            barRatio = Math.max(barRatio, 0.05f);
            barRatio = Math.min(barRatio, 1.0f);

            float targetH = maxH * barRatio;
            mBarHeights[i] += (targetH - mBarHeights[i]) * HEIGHT_LERP_FACTOR;
            mAmplitudeLevels[i] = barRatio;
        }

        // ---------- 4. 触发绘制（16ms 节流，不丢帧） ----------
        long now = System.currentTimeMillis();
        if (now - mLastDrawTime >= MIN_DRAW_INTERVAL) {
            mLastDrawTime = now;
            postInvalidateOnAnimation();
        }
    }

    // ================================================================
    // 校准
    // ================================================================
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

    // ================================================================
    // 绘制
    // ================================================================
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        checkColorCycleSwitch();

        final int width = getWidth(), height = getHeight();
        if (width == 0 || height == 0) return;
        final float MIN_H = dp2px(20f);

        for (int i = 0; i < BAR_COUNT; i++) {
            float barH = mBarHeights[i];
            // 兜底：至少 4px 高度，确保静默时也能看到
            if (barH < 4f) barH = 4f;
            barH = Math.min(barH, height * 0.9f);

            float left = i * mBarWidth + mGap / 2;
            float right = left + mBarUnitWidth;
            float top = height - barH;

            int color = getDynamicColor(mAmplitudeLevels[i], colorSchemes[currentSchemeIndex]);
            mBarPaint.setColor(color);

            if (mShowStripes && barH >= MIN_H) {
                float segWithSpace = mSegmentSpacingFixed * 2;
                int segCount = Math.min((int)(barH / segWithSpace), mSegmentCount);
                segCount = Math.max(1, segCount);
                float segH = (barH - (segCount - 1) * mSegmentSpacingFixed) / segCount;
                segH = Math.max(1f, segH);

                for (int s = 0; s < segCount; s++) {
                    float sTop = height - (s + 1) * (segH + mSegmentSpacingFixed);
                    float sBot = sTop + segH;
                    if (sTop >= top) {
                        canvas.drawRoundRect(left, sTop, right, sBot, mCornerRadius, mCornerRadius, mBarPaint);
                    }
                }
            } else {
                canvas.drawRoundRect(left, top, right, height, mCornerRadius, mCornerRadius, mBarPaint);
            }
        }
    }

    // ================================================================
    // 颜色
    // ================================================================
    private void refreshColorSchemes() {
        for (int i = 0; i < 3; i++) {
            mLastBaseHue = (mLastBaseHue + 30f + (float) (Math.random() * 60f)) % 360;
            float baseHue = mLastBaseHue;
            colorSchemes[i] = new int[]{
                    Color.HSVToColor(new float[]{baseHue, 0.9f, 1f}),
                    Color.HSVToColor(new float[]{(baseHue + 120) % 360, 0.9f, 1f}),
                    Color.HSVToColor(new float[]{(baseHue + 240) % 360, 0.9f, 1f})
            };
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
        r = Math.max(0f, Math.min(1f, r));
        float[] hsv1 = new float[3], hsv2 = new float[3];
        Color.colorToHSV(sc, hsv1);
        Color.colorToHSV(ec, hsv2);

        float h1 = hsv1[0], h2 = hsv2[0];
        float dh = h2 - h1;
        if (dh > 180) dh -= 360;
        if (dh < -180) dh += 360;
        float h = (h1 + dh * r) % 360;
        if (h < 0) h += 360;

        float s = hsv1[1] + (hsv2[1] - hsv1[1]) * r;
        float v = hsv1[2] + (hsv2[2] - hsv1[2]) * r;

        int alpha = (int)(Color.alpha(sc) + (Color.alpha(ec) - Color.alpha(sc)) * r);
        return Color.HSVToColor(alpha, new float[]{h, s, v});
    }

    // ================================================================
    // 生命周期
    // ================================================================
    public void reset() {
        mPeakLow = mPeakMid = mPeakHigh = 0.01f;
        for (int i = 0; i < BAR_COUNT; i++) {
            mBarHeights[i] = 0;
            mAmplitudeLevels[i] = 0;
        }
        postInvalidateOnAnimation();
    }

    public void release() {
        mIsPaused = true;
        removeCallbacks(null);
        for (int i = 0; i < BAR_COUNT; i++) {
            mBarHeights[i] = 0;
            mAmplitudeLevels[i] = 0;
            mEnergyAccum[i] = 0;
        }
        mMaxSeenLow = mMaxSeenMid = mMaxSeenHigh = 1f;
        mPeakLow = mPeakMid = mPeakHigh = 0.01f;
        invalidate();
    }
}
