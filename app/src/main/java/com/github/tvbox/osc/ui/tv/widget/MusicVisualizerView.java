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
    private static final String TAG = "MusicVis";
    private static final int BAR_COUNT = 22;

    // 颜色配置
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

    // ============ 峰值检测 ============
    private int mCenterLow = 3;
    private int mCenterMid = 10;
    private int mCenterHigh = 17;

    private float mPeakLow = 0f;
    private float mPeakMid = 0f;
    private float mPeakHigh = 0f;

    // 自适应归一化参数
    private float mMaxSeenLow = 1f;
    private float mMaxSeenMid = 1f;
    private float mMaxSeenHigh = 1f;

    // ============ 核心参数优化 ============
    private static final float PEAK_DECAY = 0.08f; 
    private static final float DISTANCE_SIGMA = 0.28f; 
    private static final float HEIGHT_LERP_FACTOR = 0.65f; 
    private static final float DYNAMIC_GAIN = 1.6f; 
    // ==============================================================

    // FFT bin 范围 (保持原样，但读取时会做边界保护)
    private static final int LOW_BIN_START = 2;
    private static final int LOW_BIN_END = 10;
    private static final int MID_BIN_START = 11;
    private static final int MID_BIN_END = 20;
    private static final int HIGH_BIN_START = 21;
    private static final int HIGH_BIN_END = 32;

    // 校准参数
    private final float[] mEnergyAccum = new float[BAR_COUNT];
    private int mCalibrateCount = 0;
    private long mLastCalibrateTime = 0;
    private static final int CALIBRATE_FRAMES = 45;
    private static final long CALIBRATE_INTERVAL_MS = 4000;

    // 性能与状态控制
    private long mLastDrawTime = 0;
    private static final long MIN_DRAW_INTERVAL = 33;
    private boolean mIsPendingRedraw = false;
    private boolean mIsPaused = false;
    private float mBarWidth, mGap, mBarUnitWidth, mCornerRadius;
    
    // 【新增】用于检测是否收到过有效数据
    private boolean hasReceivedValidData = false;
    private long lastDataTime = 0;

    public MusicVisualizerView(Context context) { super(context); init(); }
    public MusicVisualizerView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public MusicVisualizerView(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); init(); }

    private void init() {
        mBarPaint.setStyle(Paint.Style.FILL);
        mSegmentSpacingFixed = dp2px(2.5f);
        mLastBaseHue = (float) (Math.random() * 360);
        refreshColorSchemes();
        // 初始化一些默认高度，确保一开始能看到东西
        for(int i=0; i<BAR_COUNT; i++) {
            mBarHeights[i] = dp2px(10);
            mAmplitudeLevels[i] = 0.1f;
        }
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
        // 启动初始绘制
        postInvalidateOnAnimation();
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

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void updateVisualizer(byte[] fft, float volumeLevel) {
        if (mIsPaused) return;
        
        // 【关键修复1】：即使 fft 为 null 或 volume 为 0，也不直接 return，而是进入下面的“兜底逻辑”
        // 这样能保证只要调用这个方法，视图就会刷新
        
        float peak_bin_low = 0, peak_bin_mid = 0, peak_bin_high = 0;
        boolean hasValidFft = false;
        
        // 【关键修复2】：放宽 FFT 长度检查。有些播放器只返回前几个字节，或者格式不同
        // 原代码要求 >= 66，这里改为 >= 10 即可尝试读取低频，避免直接跳过
        if (fft != null && fft.length >= 10) {
            hasValidFft = true;
            
            // 安全读取 Low
            for (int i = LOW_BIN_START; i <= LOW_BIN_END; i++) {
                int idx = i * 2;
                if (idx + 1 >= fft.length) break;
                byte rfk = fft[idx], ifk = fft[idx + 1];
                float mag = ((rfk & 0xFF) * (rfk & 0xFF)) + ((ifk & 0xFF) * (ifk & 0xFF));
                if (mag > peak_bin_low) peak_bin_low = mag;
            }
            // 安全读取 Mid
            for (int i = MID_BIN_START; i <= MID_BIN_END; i++) {
                int idx = i * 2;
                if (idx + 1 >= fft.length) break;
                byte rfk = fft[idx], ifk = fft[idx + 1];
                float mag = ((rfk & 0xFF) * (rfk & 0xFF)) + ((ifk & 0xFF) * (ifk & 0xFF));
                if (mag > peak_bin_mid) peak_bin_mid = mag;
            }
            // 安全读取 High
            for (int i = HIGH_BIN_START; i <= HIGH_BIN_END; i++) {
                int idx = i * 2;
                if (idx + 1 >= fft.length) break;
                byte rfk = fft[idx], ifk = fft[idx + 1];
                float mag = ((rfk & 0xFF) * (rfk & 0xFF)) + ((ifk & 0xFF) * (ifk & 0xFF));
                if (mag > peak_bin_high) peak_bin_high = mag;
            }
            
            hasReceivedValidData = true;
            lastDataTime = System.currentTimeMillis();

            // ---------- 自适应归一化 ----------
            mMaxSeenLow  = Math.max(mMaxSeenLow,  peak_bin_low);
            mMaxSeenMid  = Math.max(mMaxSeenMid,  peak_bin_mid);
            mMaxSeenHigh = Math.max(mMaxSeenHigh, peak_bin_high);
            mMaxSeenLow  *= 0.996f;
            mMaxSeenMid  *= 0.997f;
            mMaxSeenHigh *= 0.998f;

            float normLow  = (mMaxSeenLow  > 1f)  ? Math.min(1.0f, (peak_bin_low  / mMaxSeenLow) * DYNAMIC_GAIN) : 0f;
            float normMid  = (mMaxSeenMid  > 1f)  ? Math.min(1.0f, (peak_bin_mid  / mMaxSeenMid) * DYNAMIC_GAIN) : 0f;
            float normHigh = (mMaxSeenHigh > 1f)  ? Math.min(1.0f, (peak_bin_high / mMaxSeenHigh) * DYNAMIC_GAIN) : 0f;

            // 如果 volumeLevel 异常大或异常小，做个保护
            float vol = Math.max(0f, Math.min(1.5f, volumeLevel));
            
            normLow  *= vol;
            normMid  *= vol;
            normHigh *= vol;
            
            if (normLow > mPeakLow) mPeakLow = normLow;
            if (normMid > mPeakMid) mPeakMid = normMid;
            if (normHigh > mPeakHigh) mPeakHigh = normHigh;
            
            // 校准能量累积
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
        } else {
            // 【关键修复3】：如果没有有效 FFT 数据，但视图需要显示，则使用“呼吸模式”
            // 模拟微小的跳动，让用户知道控件还在工作
            if (!hasReceivedValidData || System.currentTimeMillis() - lastDataTime > 2000) {
                 // 简单的正弦波模拟，让柱子有点动静
                 long t = System.currentTimeMillis();
                 mPeakLow = 0.1f + 0.05f * (float)Math.sin(t * 0.005);
                 mPeakMid = 0.1f + 0.05f * (float)Math.sin(t * 0.007 + 1);
                 mPeakHigh = 0.1f + 0.05f * (float)Math.sin(t * 0.009 + 2);
            }
        }
        
        // 峰值平滑衰减
        mPeakLow -= mPeakLow * PEAK_DECAY;
        mPeakMid -= mPeakMid * PEAK_DECAY;
        mPeakHigh -= mPeakHigh * PEAK_DECAY;
        
        // 【关键修复4】：设置一个绝对最小值，防止柱子完全消失
        if (mPeakLow < 0.02f) mPeakLow = 0.02f;
        if (mPeakMid < 0.02f) mPeakMid = 0.02f;
        if (mPeakHigh < 0.02f) mPeakHigh = 0.02f;

        // 计算柱高
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
            
            // 保证最小可见高度
            barRatio = Math.max(barRatio, 0.05f); 
            barRatio = Math.min(barRatio, 1.0f);

            float targetH = maxH * barRatio;
            // 快速平滑插值
            mBarHeights[i] += (targetH - mBarHeights[i]) * HEIGHT_LERP_FACTOR;
            mAmplitudeLevels[i] = barRatio;
        }

        // 帧率控制
        long now = System.currentTimeMillis();
        if (!mIsPendingRedraw && now - mLastDrawTime >= MIN_DRAW_INTERVAL) {
            mIsPendingRedraw = true;
            postInvalidateOnAnimation();
        }
    }

    private void autoCalibrate() {
        int third = BAR_COUNT / 3;
        int bestLow = 0, bestMid = third, bestHigh = third * 2;
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

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        mIsPendingRedraw = false;
        mLastDrawTime = System.currentTimeMillis();
        checkColorCycleSwitch();

        final int width = getWidth(), height = getHeight();
        if (width == 0 || height == 0) return;
        
        // 调试用：在左上角画一个小点，证明 onDraw 正在执行
        // mBarPaint.setColor(Color.RED);
        // canvas.drawCircle(10, 10, 5, mBarPaint);

        final float MIN_H = dp2px(5f); // 降低最小高度阈值，确保能看见

        for (int i = 0; i < BAR_COUNT; i++) {
            float left = i * mBarWidth + mGap / 2;
            float right = left + mBarUnitWidth;
            
            // 确保 barH 至少有一点高度
            float barH = Math.max(mBarHeights[i], dp2px(5f));
            barH = Math.min(barH, height * 0.9f);
            
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

    public void reset() {
        mPeakLow = mPeakMid = mPeakHigh = 0.01f;
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
        invalidate();
    }
}
