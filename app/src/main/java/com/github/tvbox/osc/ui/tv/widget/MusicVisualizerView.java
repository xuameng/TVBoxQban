package com.github.tvbox.osc.ui.tv.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/**
 * 节奏版 MusicVisualizerView - 修复饱和问题
 * 
 * 修复内容：
 * 1. FFT 能量 log 压缩，防止数值爆炸
 * 2. 峰值每帧强制衰减，不会只增不减
 * 3. 去掉 ValueAnimator，onDraw 直接插值（无叠加放大）
 * 4. 灵敏度参数可调
 */
public class MusicVisualizerView extends View {

    // ==================== 常量 ====================
    private static final int   BAR_COUNT        = 22;
    private static final int   RHYTHM_BAR_COUNT = 7;          // 前7根跟节奏
    private static final float ATTACK           = 0.45f;       // 上升（不要太大）
    private static final float DECAY            = 0.08f;       // 下降（慢一点有节奏感）
    private static final float PEAK_DECAY       = 0.85f;       // 峰值每帧衰减率
    private static final float ENERGY_BOOST     = 0.6f;        // 整体能量放大（调小=更低）
    private static final float MIN_BAR_HEIGHT   = 0.03f;       // 最小高度比例

    // ==================== 绘图 ====================
    private final Paint mBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] mBarHeights    = new float[BAR_COUNT];
    private final float[] mTargetHeights = new float[BAR_COUNT];
    private final float[] mAmplitudeLevels = new float[BAR_COUNT];

    // ==================== 节奏系统 ====================
    private float  mCurrentPeak  = 0f;
    private float  mSmoothedPeak = 0f;
    private boolean mIsBeat      = false;

    // ==================== 颜色 ====================
    private int[][] mColorSchemes = new int[3][3];
    private int     mColorIndex   = 0;
    private long    mLastColorSwitch = 0;
    private static final long COLOR_INTERVAL = 4000;

    // ==================== 分段效果 ====================
    private boolean mShowStripes = true;
    private int     mSegmentCount = 20;
    private float   mSegmentSpacing = 2.5f;

    // ==================== 构造 ====================
    public MusicVisualizerView(Context context) { super(context); init(); }
    public MusicVisualizerView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public MusicVisualizerView(Context context, AttributeSet attrs, int defStyle) { super(context, attrs, defStyle); init(); }

    private void init() {
        mBarPaint.setStyle(Paint.Style.FILL);
        generateColors();
    }

    private void generateColors() {
        for (int i = 0; i < 3; i++) {
            float hue = (float)(Math.random() * 360);
            mColorSchemes[i] = new int[] {
                Color.HSVToColor(new float[]{ hue, 0.85f, 0.95f }),
                Color.HSVToColor(new float[]{ (hue + 120) % 360, 0.9f, 1f }),
                Color.HSVToColor(new float[]{ (hue + 240) % 360, 0.85f, 0.95f })
            };
        }
    }

    // ==================== 分段设置 ====================
    public void setShowStripes(boolean show) { mShowStripes = show; invalidate(); }
    public void setSegmentCount(int c) { mSegmentCount = Math.max(1, c); invalidate(); }
    public void setSegmentSpacing(float s) { mSegmentSpacing = Math.max(0, s); invalidate(); }

    // ==================== 核心：更新数据 ====================
    public void updateVisualizer(byte[] fft, float volumeLevel) {
        if (fft == null || fft.length < BAR_COUNT * 2 + 2) return;

        if (volumeLevel <= 0.001f) {
            reset();
            return;
        }

        // ---------- 1. 计算低频峰值（log压缩，防爆炸） ----------
        float lowFreqEnergy = 0f;
        for (int i = 0; i < RHYTHM_BAR_COUNT; i++) {
            int idx = 2 + i * 2;
            if (idx < fft.length - 1) {
                int rfk = fft[idx] & 0xFF;      // 转无符号
                int ifk = fft[idx + 1] & 0xFF;
                float mag = (float) Math.sqrt(rfk * rfk + ifk * ifk);
                lowFreqEnergy = Math.max(lowFreqEnergy, mag);
            }
        }

        // log 压缩 + 归一化（关键！）
        float logPeak = (float) (Math.log10(lowFreqEnergy + 1) / Math.log10(256));
        logPeak = Math.min(logPeak, 1.0f);

        // 平滑
        mSmoothedPeak = mSmoothedPeak * 0.9f + logPeak * 0.1f;

        // 节拍检测
        if (logPeak > mCurrentPeak * 0.7f + 0.25f) {
            mCurrentPeak = logPeak;
            mIsBeat = true;
        } else {
            mIsBeat = false;
        }

        // 峰值每帧强制衰减（关键！）
        mCurrentPeak *= PEAK_DECAY;

        // ---------- 2. 计算每根柱目标高度 ----------
        for (int i = 0; i < BAR_COUNT; i++) {
            int idx;
            if (i < BAR_COUNT / 3) {
                idx = 2 + i * 2;
            } else {
                idx = 2 + (BAR_COUNT / 3) * 2 + (i - BAR_COUNT / 3) * 4;
            }

            float mag = 0f;
            if (idx < fft.length - 1) {
                int rfk = fft[idx] & 0xFF;
                int ifk = fft[idx + 1] & 0xFF;
                mag = (float) Math.sqrt(rfk * rfk + ifk * ifk);
            }

            // log 压缩
            float normalizedMag = (float) (Math.log10(mag + 1) / Math.log10(256));
            normalizedMag = Math.min(normalizedMag, 1.0f);

            float target;
            if (i < RHYTHM_BAR_COUNT) {
                // 节奏柱：跟峰值，但上限压住
                target = mCurrentPeak * ENERGY_BOOST;
            } else if (i < BAR_COUNT / 2) {
                // 中频：混合
                target = (normalizedMag * 0.6f + mCurrentPeak * 0.3f) * ENERGY_BOOST;
            } else {
                // 高频：只跟自己
                target = normalizedMag * ENERGY_BOOST * 0.7f;
            }

            // 最小高度
            target = Math.max(target, MIN_BAR_HEIGHT);
            // 最大限制
            target = Math.min(target, 0.95f);

            mTargetHeights[i] = target;
            mAmplitudeLevels[i] = normalizedMag;
        }

        // 节拍换色
        if (mIsBeat) {
            long now = System.currentTimeMillis();
            if (now - mLastColorSwitch > COLOR_INTERVAL) {
                mColorIndex = (mColorIndex + 1) % 3;
                mLastColorSwitch = now;
                generateColors();
            }
        }

        invalidate(); // 直接触发 onDraw
    }

    // ==================== 绘制 ====================
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        float barWidth     = w / (float) BAR_COUNT;
        float gap          = barWidth * 0.15f;
        float barUnitWidth = barWidth - gap;

        for (int i = 0; i < BAR_COUNT; i++) {
            // ---- 插值（Attack / Decay）----
            if (mTargetHeights[i] > mBarHeights[i]) {
                mBarHeights[i] += (mTargetHeights[i] - mBarHeights[i]) * ATTACK;
            } else {
                mBarHeights[i] += (mTargetHeights[i] - mBarHeights[i]) * DECAY;
            }

            float barH = mBarHeights[i] * h;
            float top  = h - barH;
            float left = i * barWidth + gap / 2;
            float right = left + barUnitWidth;

            // 节奏柱稍微加宽
            if (i < RHYTHM_BAR_COUNT && mIsBeat) {
                left  -= gap * 0.2f;
                right += gap * 0.2f;
            }

            // 颜色
            int color = interpolateColor(
                mAmplitudeLevels[i],
                mColorSchemes[mColorIndex][0],
                mColorSchemes[mColorIndex][1]
            );
            mBarPaint.setColor(color);

            // 分段效果
            if (mShowStripes && barH > 20f) {
                float segH = (barH - (mSegmentCount - 1) * mSegmentSpacing) / mSegmentCount;
                for (int s = 0; s < mSegmentCount; s++) {
                    float segTop    = h - (s + 1) * (segH + mSegmentSpacing);
                    float segBottom = segTop + segH;
                    if (segTop >= top) {
                        canvas.drawRect(left, segTop, right, segBottom, mBarPaint);
                    }
                }
            } else {
                canvas.drawRect(left, top, right, h, mBarPaint);
            }
        }

        // 持续动画
        if (isShown()) {
            invalidate();
        }
    }

    // ==================== 工具 ====================
    private int interpolateColor(float ratio, int start, int end) {
        ratio = Math.max(0, Math.min(1, ratio));
        return Color.argb(
            255,
            (int)(Color.red(start)   + (Color.red(end)   - Color.red(start))   * ratio),
            (int)(Color.green(start) + (Color.green(end) - Color.green(start)) * ratio),
            (int)(Color.blue(start)  + (Color.blue(end)  - Color.blue(start))  * ratio)
        );
    }

    public void reset() {
        for (int i = 0; i < BAR_COUNT; i++) {
            mBarHeights[i] = 0;
            mTargetHeights[i] = 0;
            mAmplitudeLevels[i] = 0;
        }
        mCurrentPeak = 0;
        mSmoothedPeak = 0;
        mIsBeat = false;
        invalidate();
    }

    public void release() {
        reset();
    }
}
