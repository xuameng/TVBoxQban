package com.github.tvbox.osc.ui.tv.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.animation.ValueAnimator;

/**
 * xuameng 20261118 (修正版)
 * 音乐可视化视图组件（带振幅颜色渐变）
 *
 * 本次修正要点（解决电音/DJ 等曲目中高频音柱不跳动的问题）：
 * 1. 采样改为按数组长度做「对数频率映射」（低频密、高频疏），
 *    并把 bin 上限钳制到 fft.length/2-1、idx 钳制到 n-2，彻底消除高频越界取不到数据的问题。
 * 2. 幅度还原改为 sqrt(rfk*rfk + ifk*ifk)，再除以合理的满量程（约 128），
 *    不再用「平方和 除以 9222」的对数压缩值，避免电音中高频被压成 0。
 * 3. 让 weight 真正生效：在 norm 上乘以 weight；高频段由过猛的指数增强改为温和线性抬升，
 *    并对电音场景压低低频、抬升中高频，使 hi-hat/军鼓/synth 等节奏成分可见。
 * 4. 高度映射 gamma 由 0.4 放宽到 0.5，让弱信号也能露出高度。
 * 5. 加快瞬态响应：LERP 0.40 提到 0.55、ANIMATION_DURATION 50 降到 33，高频柱衰减更快，密集鼓点不被抹平。
 *
 * 保持原有接口完全兼容。
 */
public class MusicVisualizerView extends View {

    // xuameng判断音量大小（仅作 reset 阈值参考，不再参与幅度归一化）
    private static final int MAX_AMPLITUDE = 9222;

    // 幅度还原满量程：byte 量级最大约 128，作为除数更接近真实能量比
    private static final float MAGNITUDE_FULL_SCALE = 128.0f;

    // xuameng 音柱数量
    private static final int BAR_COUNT = 22;

    // xuameng 音柱动画时长（原 50 改 33，加快瞬态响应）
    private static final int ANIMATION_DURATION = 33;

    /**
     * 峰值衰减率（低频基准）
     * 调大 -> 掉更快节拍利落
     * 调小 -> 拖尾更长
     */
    private static final float BAR_PEAK_DECAY = 0.55f;

    /**
     * 最低可见高度比例（防止小音量时柱子消失）
     */
    private static final float MIN_VISIBLE_RATIO = 0.02f;

    /**
     * 动画插值因子（原 0.40 改 0.55，瞬态更跟手）
     */
    private static final float LERP_FACTOR = 0.55f;

    // 高度映射 gamma（原 0.4 改 0.5，弱信号更易露出高度）
    private static final float HEIGHT_GAMMA = 0.5f;

    // 对数频率映射的起始 bin（跳过 fft[0..1] 的总幅值对，从第 1 个真实频点开始）
    private static final int FREQ_BIN_MIN = 1;

    // 颜色系统
    private int[][] colorSchemes = new int[3][3];

    // 新增音柱分段效果相关变量
    private boolean mShowStripes = true; // 是否显示分段效果
    private int mSegmentCount = 20; // 每个音柱的分段数
    private float mSegmentSpacingFixed = 2.5f; // 段间距固定值（像素）

    // 新增颜色方案刷新方法
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

    // 6秒切换
    private static final long COLOR_CYCLE_DURATION = (long) (0.1 * 60 * 1000);
    private int currentSchemeIndex = 0;
    private long lastSwitchTime = 0;

    // 绘图工具
    private final Paint mBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] mBarHeights = new float[BAR_COUNT];
    private final float[] mTargetHeights = new float[BAR_COUNT];
    private final float[] mAmplitudeLevels = new float[BAR_COUNT]; // 存储各柱振幅强度
    private final float[] mBarPeaks = new float[BAR_COUNT];

    private ValueAnimator mAnimator;

    public MusicVisualizerView(Context context) { super(context); init(); }
    public MusicVisualizerView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public MusicVisualizerView(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); init(); }

    private void init() {
        mBarPaint.setStyle(Paint.Style.FILL);
        refreshColorSchemes();
    }

    // 新增音柱分段效果设置方法
    public void setShowStripes(boolean show) { mShowStripes = show; postInvalidate(); }
    public void setSegmentCount(int count) { mSegmentCount = Math.max(1, count); postInvalidate(); }
    public void setSegmentSpacingFixed(float spacing) { mSegmentSpacingFixed = Math.max(0f, spacing); postInvalidate(); }

    public void updateVisualizer(byte[] fft, float volumeLevel) {
        if (fft == null || fft.length < BAR_COUNT * 2 + 2) return;

        // xuameng处理静音状态重置音柱
        if (volumeLevel <= 0.001f) {
            reset();
            return;
        }

        float maxH = getHeight();
        if (maxH <= 0) maxH = 100f;

        // ===== 修正1：对数频率映射 =====
        // fft 中每个频点占 2 字节（real, imaginary），可用 bin 数 = fft.length/2
        final int n = fft.length;
        final int maxBin = n / 2 - 1;              // 最后一个完整 bin 的索引（钳制上限）
        final int logMin = (int) FREQ_BIN_MIN;      // 起始 bin
        final int logMax = Math.max(logMin + 1, maxBin);

        for (int i = 0; i < BAR_COUNT; i++) {
            // 对数分布：低频密、高频疏；把柱序 i in [0,BAR_COUNT) 映射到 [logMin, logMax]
            float t = BAR_COUNT <= 1 ? 0f : (float) i / (float) (BAR_COUNT - 1);
            int bin = (int) Math.round(logMin * Math.pow((double) logMax / (double) logMin, t));
            if (bin > maxBin) bin = maxBin;          // bin 上限钳制
            if (bin < logMin) bin = logMin;

            int idx = bin * 2;                        // 该 bin 的 real 起始下标
            if (idx > n - 2) idx = n - 2;             // idx 钳制，保证 idx+1 不越界
            if (idx < 2) idx = 2;                     // 跳过 fft[0..1] 总幅值对

            byte rfk = fft[idx];
            byte ifk = fft[idx + 1];

            // ===== 修正2：幅度还原（开方 + 合理满量程）=====
            double magnitude = Math.sqrt((double) rfk * rfk + (double) ifk * ifk);
            float norm = (float) (magnitude / MAGNITUDE_FULL_SCALE);

            // ===== 修正3：让 weight 真正生效（温和线性抬升 + 压低低频、抬升中高频）=====
            float weight;
            if (t < 0.25f) {
                // 低频段（kick/bass 本就强）适度压低，避免顶满
                weight = 0.6f;
            } else if (t < 0.5f) {
                weight = 1.0f;
            } else {
                // 中高频段温和线性抬升（不再用指数猛增导致封顶等高、反而不动）
                weight = 1.0f + (t - 0.5f) * 1.5f;
            }
            norm = norm * weight;

            // 音量补偿
            norm = norm * (0.15f + 0.85f * volumeLevel);

            if (norm > 1.0f) norm = 1.0f;
            if (norm < 0.0f) norm = 0.0f;

            // 峰值保持 + 衰减
            if (norm > mBarPeaks[i]) {
                mBarPeaks[i] = norm;
            }

            // ===== 修正5：高频柱衰减更快，密集鼓点利落不糊 =====
            float decay = BAR_PEAK_DECAY;
            if (t >= 0.5f) {
                decay = 0.75f;          // 高频掉得更快，hi-hat/军鼓拍点清晰
            } else if (t >= 0.25f) {
                decay = 0.60f;
            }
            mBarPeaks[i] -= mBarPeaks[i] * decay;
            if (mBarPeaks[i] < 0.003f) mBarPeaks[i] = 0f;

            // ===== 修正4：高度映射 gamma 放宽到 0.5 =====
            float heightRatio = (float) Math.pow(Math.min(mBarPeaks[i], 1.0f), HEIGHT_GAMMA);
            heightRatio = Math.max(heightRatio, MIN_VISIBLE_RATIO);

            mTargetHeights[i] = Math.min(heightRatio * maxH, maxH * 0.95f);
            mAmplitudeLevels[i] = heightRatio;
        }
        startAnimation();
    }

    // 动画控制器
    private void startAnimation() {
        if (mAnimator != null) {
            mAnimator.cancel();
        }
        mAnimator = ValueAnimator.ofFloat(0f, 1f);
        mAnimator.setDuration(ANIMATION_DURATION);
        mAnimator.addUpdateListener(animation -> {
            // xuameng 动画平滑插值
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

    /**
     * 根据振幅强度计算渐变颜色
     */
    private int getDynamicColor(float amplitude, int[] currentScheme) {
        amplitude = Math.max(0.0f, Math.min(1.0f, amplitude));
        if (amplitude < 0.3f) {
            return interpolateColor(amplitude / 0.3f, currentScheme[0], currentScheme[1]);
        } else {
            return interpolateColor((amplitude - 0.3f) / 0.7f, currentScheme[1], currentScheme[2]);
        }
    }

    /**
     * 颜色插值计算
     */
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
