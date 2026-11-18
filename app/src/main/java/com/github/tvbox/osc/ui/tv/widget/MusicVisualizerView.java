package com.github.tvbox.osc.ui.tv.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.animation.ValueAnimator;

public class MusicVisualizerView extends View {

    // ============================================================
    // ★ 基础参数
    // ============================================================

    /**
     * FFT 幅度最大值，用于归一化。
     * 调大 → 柱子整体变矮（对声音更不敏感）
     * 调小 → 柱子整体变高（更容易到顶）
     * 推荐范围：5000~15000，当前 10222。
     */
    private static final int MAX_AMPLITUDE = 10222;

    /**
     * 音柱总数量。
     * 推荐范围：16~32，当前 22。
     */
    private static final int BAR_COUNT = 22;

    /**
     * 每次高度变化的动画过渡时长（毫秒）。
     * 推荐范围：40~120，当前 60。
     */
    private static final int ANIMATION_DURATION = 60;

    // ============================================================
    // ★ 颜色系统
    // ============================================================

    /**
     * 颜色方案数组 [3组][4色]。
     */
    private int[][] colorSchemes = new int[3][4];

    /** 是否显示分段条纹效果 */
    private boolean mShowStripes = true;

    /**
     * 每个音柱最多分成几段条纹。
     * 推荐范围：8~30，当前 20。
     */
    private int mSegmentCount = 20;

    /**
     * 条纹之间的固定间距（像素）。
     * 推荐范围：1.5~5.0，当前 2.5。
     */
    private float mSegmentSpacingFixed = 2.5f;

    /**
     * 颜色方案自动切换间隔（毫秒）。
     * 当前 = 6秒换一组。
     */
    private static final long COLOR_CYCLE_DURATION = (long) (0.1 * 60 * 1000);

    private int currentSchemeIndex = 0;
    private long lastSwitchTime = 0;

    // ============================================================
    // ★ 峰值检测与衰减（每根柱子独立）
    // ============================================================

    /**
     * 每根柱子的独立峰值，用于保持+衰减。
     */
    private final float[] mBarPeaks = new float[BAR_COUNT];

    /**
     * 峰值衰减率（每帧衰减比例）。
     * 调大 → 掉更快更跟节奏
     * 调小 → 拖尾更长
     * 推荐范围：0.3~0.6，当前 0.45。
     */
    private static final float BAR_PEAK_DECAY = 0.45f;

    // ============================================================
    // ★ 核心高度映射参数
    // ============================================================

    /**
     * ★★★ 抬升增益 ★★★
     * 调大 → 更容易到顶但差距缩小
     * 调小 → 低值更矮差距更大
     * 推荐范围：1.5~2.5，当前 1.8。
     */
    private static final float GAIN = 1.8f;

    /**
     * ★★★ 曲线陡峭度 ★★★
     * 调大 → 差距小
     * 调小 → 差距大
     * 推荐范围：0.35~0.55，当前 0.4。
     */
    private static final float CURVE = 0.4f;

    // ============================================================
    // 以下为运行变量
    // ============================================================

    private final Paint mBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] mBarHeights = new float[BAR_COUNT];
    private final float[] mTargetHeights = new float[BAR_COUNT];
    private final float[] mAmplitudeLevels = new float[BAR_COUNT];

    private ValueAnimator mAnimator;

    // ============================================================
    // 构造 & 初始化
    // ============================================================

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

    // ============================================================
    // ★★★ 核心改动：直接每柱读 FFT bin ★★★
    // ============================================================

    public void updateVisualizer(byte[] fft, float volumeLevel) {
        if (fft == null || fft.length < BAR_COUNT * 2 + 2) return;

        if (volumeLevel <= 0.001f) {
            reset();
            return;
        }

        float maxH = getHeight();
        if (maxH <= 0) maxH = 100f;

        // volumeLevel 补偿系数
        float volFactor = 0.15f + 0.85f * volumeLevel;

        for (int i = 0; i < BAR_COUNT; i++) {

            // ---- 每根柱子直接对应一个 FFT bin ----
            // 低索引区密一点，高索引区疏一点，覆盖全频段
            int binIndex;
            if (i < BAR_COUNT / 3) {
                // 低频区：bin 2~10
                binIndex = 2 + i * 2;
            } else if (i < BAR_COUNT * 2 / 3) {
                // 中频区：bin 11~20
                binIndex = 2 + (BAR_COUNT / 3) * 2 + (i - BAR_COUNT / 3) * 2;
            } else {
                // 高频区：bin 21~42（稀疏采样）
                binIndex = 2 + (BAR_COUNT / 3) * 2 + (BAR_COUNT / 3) * 2 + (i - BAR_COUNT * 2 / 3) * 4;
            }

            if (binIndex * 2 + 1 >= fft.length) {
                binIndex = (fft.length / 2 - 1);
            }

            int idx = binIndex * 2;
            byte rfk = fft[idx];
            byte ifk = fft[idx + 1];
            float mag = (rfk * rfk + ifk * ifk);

            // ---- 归一化 ----
            float norm = Math.min(mag / (float) MAX_AMPLITUDE, 1.0f);
            norm = norm * volFactor;

            // ---- 峰值保持 ----
            if (norm > mBarPeaks[i]) {
                mBarPeaks[i] = norm;
            }
            mBarPeaks[i] -= mBarPeaks[i] * BAR_PEAK_DECAY;
            if (mBarPeaks[i] < 0.005f) mBarPeaks[i] = 0f;

            // ---- 高度映射 ----
            float shaped = Math.min(mBarPeaks[i] * GAIN, 1.0f);
            float heightRatio = (float) Math.pow(shaped, CURVE);

            mTargetHeights[i] = Math.min(heightRatio * maxH, maxH);
            mAmplitudeLevels[i] = heightRatio;
        }

        startAnimation();
    }

    // ============================================================
    // 动画驱动
    // ============================================================

    private void startAnimation() {
        if (mAnimator != null) {
            mAnimator.cancel();
        }
        mAnimator = ValueAnimator.ofFloat(0f, 1f);
        mAnimator.setDuration(ANIMATION_DURATION);
        mAnimator.addUpdateListener(animation -> {
            float lerpFactor = 0.55f;  // 追赶速度
            for (int i = 0; i < BAR_COUNT; i++) {
                mBarHeights[i] += (mTargetHeights[i] - mBarHeights[i]) * lerpFactor;
            }
            postInvalidate();
        });
        mAnimator.start();
    }

    // ============================================================
    // 绘制（完全不变）
    // ============================================================

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

    // ============================================================
    // 颜色切换
    // ============================================================

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
                    Color.HSVToColor(new float[]{baseHue, 1f, 0.4f}),
                    Color.HSVToColor(new float[]{(baseHue + 120) % 360, 1f, 0.7f}),
                    Color.HSVToColor(new float[]{(baseHue + 240) % 360, 1f, 1.0f}),
                    Color.HSVToColor(new float[]{(baseHue + 60) % 360, 1f, 1.0f})
            };
        }
        postInvalidate();
    }

    private int getDynamicColor(float amplitude, int[] currentScheme) {
        amplitude = Math.max(0.0f, Math.min(1.0f, amplitude));
        if (amplitude < 0.33f) {
            return interpolateColor(amplitude / 0.33f, currentScheme[0], currentScheme[1]);
        } else if (amplitude < 0.66f) {
            return interpolateColor((amplitude - 0.33f) / 0.33f, currentScheme[1], currentScheme[2]);
        } else {
            return interpolateColor((amplitude - 0.66f) / 0.34f, currentScheme[2], currentScheme[3]);
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

    // ============================================================
    // 重置 & 释放
    // ============================================================

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
