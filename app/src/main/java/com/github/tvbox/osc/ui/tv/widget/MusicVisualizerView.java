package com.github.tvbox.osc.ui.tv.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.animation.ValueAnimator;

/**
 * xuameng 20261118
 * 音乐可视化视图组件（带振幅颜色渐变）
 * 新增特性：
 * 1，颜色随振幅大小变化
 * 2，保持原有动画平滑性
 * 3，完全兼容原有接口
 * 4，振幅随音量大小变化
 * 5，三种颜色随机变化
 * 6，新增音柱分段效果
 * 7，新增节奏感增强
 * 8，新增动画丝滑
 */
public class MusicVisualizerView extends View {

    // xuameng判断音量大小
    private static final int MAX_AMPLITUDE = 9222;

    // xuameng 音柱数量
    private static final int BAR_COUNT = 22;

    // xuameng 音柱动画时长
    private static final int ANIMATION_DURATION = 50;

    /**
     * 峰值衰减率
     * 调大 → 掉更快节拍利落
     * 调小 → 拖尾更长
     * 当前 0.55
     */
    private static final float BAR_PEAK_DECAY = 0.55f;

    /**
     * 最低可见高度比例（防止小音量时柱子消失）
     * 调大 → 最小柱子更高
     * 调小 → 差距更大但可能消失
     * 当前 0.02（2%）
     */
    private static final float MIN_VISIBLE_RATIO = 0.02f;

    /**
     * 动画插值因子
     * 当前 0.40（丝滑）
     */
    private static final float LERP_FACTOR = 0.40f;

    // 颜色系统
    // 改为非静态变量实现动态刷新
    private int[][] colorSchemes = new int[3][3];

    // 新增音柱分段效果相关变量
    private boolean mShowStripes = true; // 是否显示分段效果
    private int mSegmentCount = 20; // 每个音柱的分段数
    private float mSegmentSpacingFixed = 2.5f; // 段间距固定值（像素）

    // xuameng 山峰模式
    private static final int PEAK_COUNT = 3;               // 每帧最多山峰数量
    private static final float PEAK_RADIUS = 3.0f;          // 山峰影响半径（bar 数）
    private static final float PEAK_DECAY_POWER = 2.5f;     // 山峰衰减曲线（越大越尖）

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
        // 新增：立即刷新视图
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
        // 新增：初始化时预加载颜色方案
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

        // xuameng 先计算每个频点能量
        float[] rawEnergy = new float[BAR_COUNT];
        int[] rawIndex = new int[BAR_COUNT];

        for (int i = 0; i < BAR_COUNT; i++) {
            int barIndex;
            // xuameng 修改：低频采样右移，让第一个山峰峰顶落在第3~4根，整体山脉居中
            if (i < BAR_COUNT / 3) {
                barIndex = 4 + i * 2;  // ← 右移：从 bin 4 开始
            } else {
                barIndex = 4 + (BAR_COUNT / 3) * 2 + (i - BAR_COUNT / 3) * 4;
            }

            if (barIndex < fft.length - 1) {
                byte rfk = fft[barIndex];
                byte ifk = fft[barIndex + 1];
                float magnitude = (rfk * rfk + ifk * ifk);

                // xuameng改进的频率加权策略（三段式加权）
                float weight;
                // xuameng 修改：低频权重加大，配合右移让峰顶居中
                if (i < BAR_COUNT / 4) {
                    weight = 2.0f;  // ← 低频权重加大
                } else if (i < BAR_COUNT / 2) {
                    weight = 2.5f;
                } else {
                    // xuameng 修改：高频衰减，避免右侧过多半高死柱
                    float freqFactor = (float) Math.pow(1.6, (i - BAR_COUNT / 2) / 2.0);
                    weight = 7.5f * freqFactor;
                }

                float norm = Math.min(magnitude / (float) MAX_AMPLITUDE, 1.0f);
                norm = norm * (0.15f + 0.85f * volumeLevel);

                if (i >= BAR_COUNT / 4 && i < BAR_COUNT / 2) {
                    norm = Math.min(norm * 3.0f, 1.0f);
                } else if (i >= BAR_COUNT / 2) {
                    // xuameng 修改：保持高频增益但配合 weight 降低
                    norm = Math.min(norm * 5.0f, 1.0f);
                }

                rawEnergy[i] = norm;
                rawIndex[i] = i;
            }
        }

        // xuameng 找前 PEAK_COUNT 个最强音柱（山峰）
        for (int i = 0; i < BAR_COUNT - 1; i++) {
            for (int j = i + 1; j < BAR_COUNT; j++) {
                if (rawEnergy[j] > rawEnergy[i]) {
                    float tmpE = rawEnergy[i];
                    rawEnergy[i] = rawEnergy[j];
                    rawEnergy[j] = tmpE;

                    int tmpI = rawIndex[i];
                    rawIndex[i] = rawIndex[j];
                    rawIndex[j] = tmpI;
                }
            }
        }

        // xuameng 山峰衰减叠加
        float[] mountainHeight = new float[BAR_COUNT];

        for (int p = 0; p < PEAK_COUNT; p++) {
            int peakIndex = rawIndex[p];
            float peakEnergy = rawEnergy[p];

            if (peakEnergy < 0.05f) break;

            for (int i = 0; i < BAR_COUNT; i++) {
                float distance = Math.abs(i - peakIndex);
                if (distance > PEAK_RADIUS) continue;

                float falloff = 1.0f - (distance / PEAK_RADIUS);
                falloff = (float) Math.pow(falloff, PEAK_DECAY_POWER);

                mountainHeight[i] += peakEnergy * falloff;
            }
        }

        // xuameng 写入目标高度 & 振幅（全局连续高度场）
        for (int i = 0; i < BAR_COUNT; i++) {
            float norm = Math.min(mountainHeight[i], 1.0f);

            // 峰值保持 + 衰减
            if (norm > mBarPeaks[i]) {
                mBarPeaks[i] = norm;
            }
            float decay = BAR_PEAK_DECAY;
            if (i >= BAR_COUNT / 2) decay = 0.45f;
            else if (i >= BAR_COUNT / 4) decay = 0.55f;

            mBarPeaks[i] -= mBarPeaks[i] * decay;
            if (mBarPeaks[i] < 0.003f) mBarPeaks[i] = 0f;

            float heightRatio = (float) Math.pow(Math.min(mBarPeaks[i], 1.0f), 0.4f);

            // ===== 全局连续斜坡底座 =====
            // xuameng 修改：最小偏移，确保山脉居中，整体右移
            int farthestPeakPos = Math.max(7, 0); // ← 最小偏移，确保山脉居中
            float farthestPeakEnergy = 0;
            for (int p = 0; p < PEAK_COUNT; p++) {
                if (rawEnergy[p] < 0.05f) break;
                if (rawIndex[p] > farthestPeakPos) {
                    farthestPeakPos = rawIndex[p];
                    farthestPeakEnergy = rawEnergy[p];
                }
            }

            // 从最远峰位置到 BAR_COUNT-1 之间：线性斜坡从峰底降到 MIN_VISIBLE_RATIO
            float baseRatio = 0f;
            if (i >= farthestPeakPos) {
                // 在最后一个峰右侧：线性斜坡
                int slopeLength = BAR_COUNT - 1 - farthestPeakPos;
                if (slopeLength > 0) {
                    float slopeProgress = (float)(i - farthestPeakPos) / slopeLength;
                    // xuameng 修改：降低峰底起始高度，右侧不再有半高死柱
                    float peakFloor = Math.max(farthestPeakEnergy * 0.05f, 0f); // ← 从 0.15 降到 0.05
                    baseRatio = peakFloor + (MIN_VISIBLE_RATIO - peakFloor) * slopeProgress;
                } else {
                    baseRatio = MIN_VISIBLE_RATIO;
                }
            } else {
                // 在最后一个峰左侧：也加一个微弱全局底座（从 0 渐入）
                int slopeLength = farthestPeakPos;
                if (slopeLength > 0) {
                    float slopeProgress = 1.0f - (float)i / slopeLength;
                    baseRatio = MIN_VISIBLE_RATIO * 0.3f * slopeProgress;
                }
            }

            // 最终高度 = 山峰高度 + 斜坡底座，取大值
            float finalRatio = Math.max(heightRatio, baseRatio);

            mTargetHeights[i] = Math.min(finalRatio * maxH, maxH * 0.95f);
            mAmplitudeLevels[i] = finalRatio;
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

        // 检查是否需要切换颜色方案
        checkColorCycleSwitch();

        final int width = getWidth();
        final int height = getHeight();
        if (width == 0 || height == 0) return;

        final float barWidth = width / (float) BAR_COUNT;
        final float gap = barWidth * 0.2f;
        final float barUnitWidth = barWidth - gap;

        // 新增：定义分段效果的最小高度阈值（例如4像素）
        final float MIN_HEIGHT_FOR_STRIPES = 20f;

        for (int i = 0; i < BAR_COUNT; i++) {
            float left = i * barWidth + gap / 2;
            float right = left + barUnitWidth;
            float barHeight = Math.min(mBarHeights[i], height * 0.9f);
            float top = height - barHeight;

            // 根据当前颜色方案和振幅强度计算颜色
            int color = getDynamicColor(mAmplitudeLevels[i], colorSchemes[currentSchemeIndex]);
            mBarPaint.setColor(color);

            // 分段绘制音柱（实现"一段一段"效果）
            // 修改点：分段绘制音柱（仅当音柱高度达到最小阈值时才显示分段效果）
            if (mShowStripes && barHeight >= MIN_HEIGHT_FOR_STRIPES) {
                // 动态计算实际能显示的分段数量
                float segmentHeightWithSpacing = mSegmentSpacingFixed * 2;
                int actualSegmentCount = (int) (barHeight / segmentHeightWithSpacing);
                actualSegmentCount = Math.min(actualSegmentCount, mSegmentCount);
                actualSegmentCount = Math.max(1, actualSegmentCount); // 至少显示1段

                // 计算实际段高度（考虑固定间距）
                float actualSegmentHeight = (barHeight - (actualSegmentCount - 1) * mSegmentSpacingFixed) / actualSegmentCount;

                // 从下往上绘制各个段
                for (int segment = 0; segment < actualSegmentCount; segment++) {
                    float segmentTop = height - (segment + 1) * (actualSegmentHeight + mSegmentSpacingFixed);
                    float segmentBottom = segmentTop + actualSegmentHeight;
                    // 确保段在音柱范围内
                    if (segmentTop >= top) {
                        canvas.drawRect(left, segmentTop, right, segmentBottom, mBarPaint);
                    }
                }
            } else {
                // 如果不显示分段效果，绘制完整音柱
                canvas.drawRect(left, top, right, height, mBarPaint);
            }
        }
    }

    // 修改调用方式
    private void checkColorCycleSwitch() {
        long now = System.currentTimeMillis();
        if (now - lastSwitchTime > COLOR_CYCLE_DURATION) {
            currentSchemeIndex = (currentSchemeIndex + 1) % colorSchemes.length;
            lastSwitchTime = now;
            // 优化刷新逻辑（不再需要currentSchemeIndex检查）
            refreshColorSchemes();
        }
    }

    /**
     * 根据振幅强度计算渐变颜色
     */
    private int getDynamicColor(float amplitude, int[] currentScheme) {
        amplitude = Math.max(0.0f, Math.min(1.0f, amplitude)); // 确保振幅在有效范围内
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
