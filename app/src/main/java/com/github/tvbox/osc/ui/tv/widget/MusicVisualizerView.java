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
    private static final int MAX_AMPLITUDE = 2222;

    // xuameng 音柱数量
    private static final int BAR_COUNT = 22;

    // xuameng 音柱动画时长
    private static final int ANIMATION_DURATION = 50;

    // xuameng 是否应该绘制音柱
    private boolean mVisible = true;

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
        if (volumeLevel <= 0f) {
            reset();  
            return;
        }
        mVisible = true; // xuameng 有数据就显示
        float maxH = getHeight();
        if (maxH <= 0) maxH = 100f;

        // 修改采样策略：前1/3柱子重点采样低频，后2/3均匀采样中高频
        for (int i = 0; i < BAR_COUNT; i++) {
            int barIndex;
            if (i < BAR_COUNT / 3) {
                // 低频段密集采样（每2点取1）
                barIndex = 2 + i * 2;
            } else {
                // 中高频段间隔采样（每4点取1）
                barIndex = 2 + (BAR_COUNT / 3) * 2 + (i - BAR_COUNT / 3) * 4;
            }
            if (barIndex < fft.length - 1) {
                byte rfk = fft[barIndex];
                byte ifk = fft[barIndex + 1];
                float magnitude = (rfk * rfk + ifk * ifk);

                // xuameng改进的频率加权策略（三段式加权）
                // ★ 中高频 weight 拉大
                float weight;
                if (i < BAR_COUNT / 4) {
                    // xuameng 超低频段(0-200Hz)增益
                    weight = 3.0f;
                } else if (i < BAR_COUNT / 2) {
                    // xuameng 中低频段(200-800Hz)基准值增益
                    weight = 2.5f;
                } else {
                    // xuameng 高频段(800Hz+)指数增强
                    float freqFactor = (float) Math.pow(2.0, (i - BAR_COUNT / 2) / 2.0);
                    weight = 1.5f * freqFactor;
                }

                // xuameng判断音量大小
                // 归一化
                float norm = Math.min(magnitude / (float) MAX_AMPLITUDE, 1.0f);
                norm = norm * (0.25f + 1.25f * volumeLevel);

                // ★ 中高频额外补偿
                if (i >= BAR_COUNT / 4 && i < BAR_COUNT / 2) {
                    norm = Math.min(norm * 3.0f, 1.0f);
                } else if (i >= BAR_COUNT / 2) {
                    norm = Math.min(norm * 5.0f, 1.0f);
                }

                // 峰值保持 + 衰减
                if (norm > mBarPeaks[i]) {
                    mBarPeaks[i] = norm;
                }

                // xuameng 节拍衰减控制
                // ★ 中高频衰减更慢，撑得更久
                float decay = BAR_PEAK_DECAY;
                if (i >= BAR_COUNT / 2) {
                    decay = 0.50f;
                } else if (i >= BAR_COUNT / 4) {
                    decay = 0.45f;
                }
                mBarPeaks[i] -= mBarPeaks[i] * decay;
                if (mBarPeaks[i] < 0.003f) mBarPeaks[i] = 0f;

                // ★ 高度映射（pow + 最低可见兜底）
                float heightRatio = (float) Math.pow(Math.min(mBarPeaks[i], 1.0f), 0.4f);
                heightRatio = Math.max(heightRatio, MIN_VISIBLE_RATIO);

                mTargetHeights[i] = Math.min(heightRatio * maxH, maxH * 0.95f);
                mAmplitudeLevels[i] = heightRatio;
            }
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

        // xuameng 静音/暂停时不画
        if (!mVisible) {
            return;
        }
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
        if (mAnimator != null) {
            mAnimator.cancel();
        }
        for (int i = 0; i < BAR_COUNT; i++) {
            mBarHeights[i] = 0;
            mTargetHeights[i] = 0;
            mAmplitudeLevels[i] = 0;
            mBarPeaks[i] = 0;
        }
        mVisible = false;
        invalidate(); // xuameng 改用 invalidate 立即重绘
    }

    public void release() {
        if (mAnimator != null) {
            mAnimator.cancel();
            mAnimator.removeAllUpdateListeners();
        }
        reset();
    }
}
