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

    // ============ 三浪独立跳动方案 ============
    // 三个浪的中心位置（可校准）
    private int mCenterLow = 3;
    private int mCenterMid = 11;
    private int mCenterHigh = 18;

    // 每个浪的"显示能量"（快起快落，给人看的）
    private float mDisplayEnergyLow = 0f;
    private float mDisplayEnergyMid = 0f;
    private float mDisplayEnergyHigh = 0f;

    // 每个频带的"近期平均能量"（用于计算相对变化）
    private float mAvgEnergyLow = 1f;
    private float mAvgEnergyMid = 1f;
    private float mAvgEnergyHigh = 1f;

    // 每个频带采样的 FFT bin 范围
    private static final int LOW_BIN_START = 2;
    private static final int LOW_BIN_END = 8;
    private static final int MID_BIN_START = 9;
    private static final int MID_BIN_END = 16;
    private static final int HIGH_BIN_START = 17;
    private static final int HIGH_BIN_END = 24;

    // 响应参数
    private static final float ATTACK = 0.75f;       // 显示能量上升
    private static final float RELEASE = 0.30f;      // 显示能量回落
    private static final float AVG_DECAY = 0.96f;    // 平均能量衰减（越小忘记得越快=对变化越敏感）
    private static final float AVG_LEARN = 0.04f;     // 平均能量学习速率
    private static final float DISTANCE_SIGMA = 0.28f;
    private static final float BASE_FLOOR = 0.08f;
    private static final float RELATIVE_THRESHOLD = 1.8f; // 相对能量超过此值才算"瞬态"

    // 自动校准
    private final float[] mEnergyAccum = new float[BAR_COUNT];
    private int mCalibrateCount = 0;
    private long mLastCalibrateTime = 0;
    private static final int CALIBRATE_FRAMES = 20;
    private static final long CALIBRATE_INTERVAL_MS = 2000;
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

        // ---------- 1. 从 FFT 计算各频带的总能量 ----------
        float rawLow = 0, rawMid = 0, rawHigh = 0;

        // 低频带
        for (int i = LOW_BIN_START; i <= LOW_BIN_END && i * 2 + 1 < fft.length; i++) {
            byte rfk = fft[i * 2];
            byte ifk = fft[i * 2 + 1];
            rawLow += (rfk * rfk + ifk * ifk);
        }

        // 中频带
        for (int i = MID_BIN_START; i <= MID_BIN_END && i * 2 + 1 < fft.length; i++) {
            byte rfk = fft[i * 2];
            byte ifk = fft[i * 2 + 1];
            rawMid += (rfk * rfk + ifk * ifk);
        }

        // 高频带
        for (int i = HIGH_BIN_START; i <= HIGH_BIN_END && i * 2 + 1 < fft.length; i++) {
            byte rfk = fft[i * 2];
            byte ifk = fft[i * 2 + 1];
            rawHigh += (rfk * rfk + ifk * ifk);
        }

        // ---------- 2. 更新每个频带的"近期平均能量" ----------
        // 平均能量缓慢跟踪，用于后续计算相对值
        mAvgEnergyLow = mAvgEnergyLow * AVG_DECAY + rawLow * AVG_LEARN;
        mAvgEnergyMid = mAvgEnergyMid * AVG_DECAY + rawMid * AVG_LEARN;
        mAvgEnergyHigh = mAvgEnergyHigh * AVG_DECAY + rawHigh * AVG_LEARN;

        // 防止除零
        mAvgEnergyLow = Math.max(mAvgEnergyLow, 1f);
        mAvgEnergyMid = Math.max(mAvgEnergyMid, 1f);
        mAvgEnergyHigh = Math.max(mAvgEnergyHigh, 1f);

        // ---------- 3. 计算相对能量（当前/平均） ----------
        // 这才是驱动每个浪独立跳动的核心
        float relativeLow = rawLow / mAvgEnergyLow;
        float relativeMid = rawMid / mAvgEnergyMid;
        float relativeHigh = rawHigh / mAvgEnergyHigh;

        // 用相对值驱动显示能量（快起快落）
        // 只有相对值超过阈值时才触发"跳"
        float targetLow = Math.min(relativeLow / RELATIVE_THRESHOLD, 1.0f);
        float targetMid = Math.min(relativeMid / RELATIVE_THRESHOLD, 1.0f);
        float targetHigh = Math.min(relativeHigh / RELATIVE_THRESHOLD, 1.0f);

        // 也受全局音量影响（但权重小，只占 30%）
        float volFactor = 0.3f + 0.7f * volumeLevel;
        targetLow *= volFactor;
        targetMid *= volFactor;
        targetHigh *= volFactor;

        // ---------- 4. 快起快落 ----------
        if (targetLow > mDisplayEnergyLow) {
            mDisplayEnergyLow += (targetLow - mDisplayEnergyLow) * ATTACK;
        } else {
            mDisplayEnergyLow += (targetLow - mDisplayEnergyLow) * RELEASE;
        }

        if (targetMid > mDisplayEnergyMid) {
            mDisplayEnergyMid += (targetMid - mDisplayEnergyMid) * ATTACK;
        } else {
            mDisplayEnergyMid += (targetMid - mDisplayEnergyMid) * RELEASE;
        }

        if (targetHigh > mDisplayEnergyHigh) {
            mDisplayEnergyHigh += (targetHigh - mDisplayEnergyHigh) * ATTACK;
        } else {
            mDisplayEnergyHigh += (targetHigh - mDisplayEnergyHigh) * RELEASE;
        }

        // ---------- 5. 校准累计 ----------
        float[] magnitudes = new float[BAR_COUNT];
        for (int i = 0; i < BAR_COUNT; i++) {
            int idx = (i < BAR_COUNT / 3) ? (2 + i * 2) : (2 + (BAR_COUNT / 3) * 2 + (i - BAR_COUNT / 3) * 4);
            if (idx < fft.length - 1) {
                byte rfk = fft[idx], ifk = fft[idx + 1];
                magnitudes[i] = (rfk * rfk + ifk * ifk);
            }
            mEnergyAccum[i] += magnitudes[i];
        }
        mCalibrateCount++;
        long now = System.currentTimeMillis();
        if (mCalibrateCount >= CALIBRATE_FRAMES && now - mLastCalibrateTime > CALIBRATE_INTERVAL_MS) {
            autoCalibrate();
            mCalibrateCount = 0;
            mLastCalibrateTime = now;
        }

        // ---------- 6. 每个柱 = 三浪各自贡献叠加 ----------
        float maxH = getHeight() * 0.95f;

        for (int i = 0; i < BAR_COUNT; i++) {
            float dLow = Math.abs(i - mCenterLow);
            float dMid = Math.abs(i - mCenterMid);
            float dHigh = Math.abs(i - mCenterHigh);

            float fLow = (float) Math.exp(-(dLow * dLow) / (2 * DISTANCE_SIGMA * DISTANCE_SIGMA));
            float fMid = (float) Math.exp(-(dMid * dMid) / (2 * DISTANCE_SIGMA * DISTANCE_SIGMA));
            float fHigh = (float) Math.exp(-(dHigh * dHigh) / (2 * DISTANCE_SIGMA * DISTANCE_SIGMA));

            // 每浪独立贡献（用各自的 displayEnergy）
            float contribution = mDisplayEnergyLow * fLow
                               + mDisplayEnergyMid * fMid
                               + mDisplayEnergyHigh * fHigh;

            contribution = Math.max(contribution, BASE_FLOOR);
            contribution = Math.min(contribution, 1.0f);

            mTargetHeights[i] = maxH * contribution;
            mAmplitudeLevels[i] = contribution;
        }

        driveAnimation();
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

        float t = 0.35f;
        mCenterLow = (int)(mCenterLow * (1 - t) + bestLow * t);
        mCenterMid = (int)(mCenterMid * (1 - t) + bestMid * t);
        mCenterHigh = (int)(mCenterHigh * (1 - t) + bestHigh * t);

        mCenterLow = Math.max(0, Math.min(BAR_COUNT - 1, mCenterLow));
        mCenterMid = Math.max(0, Math.min(BAR_COUNT - 1, mCenterMid));
        mCenterHigh = Math.max(0, Math.min(BAR_COUNT - 1, mCenterHigh));

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
        mDisplayEnergyLow = mDisplayEnergyMid = mDisplayEnergyHigh = 0f;
        postInvalidate();
    }

    public void release() {
        if (mAnimator != null) { mAnimator.cancel(); mAnimator.removeAllUpdateListeners(); }
        reset();
    }
}
