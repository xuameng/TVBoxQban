package com.github.tvbox.osc.ui.tv.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.animation.ValueAnimator;

/**
 * xuameng
 * 音乐可视化视图组件（带振幅颜色渐变）
 * 新增特性：
 * 1，颜色随振幅大小变化
 * 2，保持原有动画平滑性
 * 3，完全兼容原有接口
 * 4，振幅随音量大小变化
 * 5，三种颜色随机变化
 * 6，新增音柱分段效果
 */
public class MusicVisualizerView extends View {

    // ============================================================
    // ★ 基础参数
    // ============================================================

    private static final int MAX_AMPLITUDE = 12222;  // xuameng判断音量大小
    private static final int BAR_COUNT = 22;         // xuameng 音柱数量
    private static final int ANIMATION_DURATION = 80; // xuameng 音柱动画时长

    // ============================================================
    // ★★★ 节奏感调参区（新增） ★★★
    // ============================================================

    /**
     * ★★★ 抬升增益 GAIN ★★★
     * 控制整体高度缩放，影响波峰波谷差距。
     * 调大（2.2~2.5）→ 高值更容易到顶，但差距缩小，柱子整体偏高
     * 调小（1.5~1.8）→ 低值被压得更矮，差距拉大，节奏感更强
     * 推荐范围：1.5~2.5，当前 1.5（差距最大）。
     */
    private static final float GAIN = 1.5f;

    /**
     * ★★★ 峰值衰减率 BAR_PEAK_DECAY ★★★
     * 每根柱子独立峰值的每帧衰减比例。
     * 调大（0.50~0.65）→ 掉更快，柱子迅速回落，节拍更利落
     * 调小（0.25~0.35）→ 掉更慢，拖尾更长，有余音感
     * 推荐范围：0.25~0.65，当前 0.55（掉更快，节拍利落）。
     * 想要拖尾长就改回 0.30。
     */
    private static final float BAR_PEAK_DECAY = 0.55f;

    /**
     * ★★★ 动画插值因子 lerpFactor ★★★
     * 每帧柱子追赶目标高度的比例。
     * 调大（0.55~0.80）→ 追得快，反应灵敏但可能略显跳跃
     * 调小（0.35~0.45）→ 追得慢，动画更丝滑柔和
     * 推荐范围：0.35~0.80，当前 0.40（更丝滑）。
     */
    private static final float LERP_FACTOR = 0.40f;

    // ============================================================
    // ★ 颜色系统
    // ============================================================

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
        postInvalidate(); // 新增：立即刷新视图
    }

    private static final long COLOR_CYCLE_DURATION = (long) (0.1 * 60 * 1000); // 6秒切换
    private int currentSchemeIndex = 0;
    private long lastSwitchTime = 0;

    // ============================================================
    // ★ 运行变量
    // ============================================================

    // 绘图工具
    private final Paint mBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] mBarHeights = new float[BAR_COUNT];
    private final float[] mTargetHeights = new float[BAR_COUNT];
    private final float[] mAmplitudeLevels = new float[BAR_COUNT]; // 存储各柱振幅强度
    private final float[] mBarPeaks = new float[BAR_COUNT];        // 每根柱子独立峰值（新增）

    // 动画控制器
    private ValueAnimator mAnimator;

    // ============================================================
    // 构造
    // ============================================================

    public MusicVisualizerView(Context context) {
        super(context);
        init();
    }

    public MusicVisualizerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MusicVisualizerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mBarPaint.setStyle(Paint.Style.FILL);
        refreshColorSchemes(); // 新增：初始化时预加载颜色方案
    }

    // ============================================================
    // 分段效果设置
    // ============================================================

    public void setShowStripes(boolean show) {
        mShowStripes = show;
        postInvalidate();
    }

    public void setSegmentCount(int count) {
        mSegmentCount = Math.max(1, count); // 至少1段
        postInvalidate();
    }

    public void setSegmentSpacingFixed(float spacing) {
        mSegmentSpacingFixed = Math.max(0f, spacing); // 确保非负值
        postInvalidate();
    }

    // ============================================================
    // ★★★ 核心：FFT 更新（加入 GAIN + BAR_PEAK_DECAY） ★★★
    // ============================================================

    public void updateVisualizer(byte[] fft, float volumeLevel) {
        if (fft == null || fft.length < BAR_COUNT * 2 + 2) return;

        if (volumeLevel == 0f || volumeLevel == 0.0f || volumeLevel == 0.00f) {
            reset(); // xuameng处理静音状态重置音柱
            return;
        }

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
                float weight;
                if (i < BAR_COUNT / 4) {
                    weight = 1.0f; // xuameng 超低频段(0-200Hz)增益
                } else if (i < BAR_COUNT / 2) {
                    weight = 2.5f; // xuameng 中低频段(200-800Hz)基准值增益
                } else {
                    float freqFactor = (float) Math.pow(1.5, (i - BAR_COUNT / 2) / 2.0); // xuameng 高频段(800Hz+)指数增强
                    weight = 3.5f * freqFactor;
                }

                // ---- 归一化 ----
                float norm = Math.min(magnitude / (float) MAX_AMPLITUDE, 1.0f);
                norm = norm * (0.15f + 0.85f * volumeLevel);

                // ---- 峰值保持 + 衰减 ----
                if (norm > mBarPeaks[i]) {
                    mBarPeaks[i] = norm;
                }
                mBarPeaks[i] -= mBarPeaks[i] * BAR_PEAK_DECAY;
                if (mBarPeaks[i] < 0.005f) mBarPeaks[i] = 0f;

                // ---- ★ GAIN + CURVE 高度映射 ----
                float shaped = Math.min(mBarPeaks[i] * GAIN, 1.0f);
                float heightRatio = (float) Math.pow(shaped, 0.4f); // CURVE=0.4 固定

                mTargetHeights[i] = Math.min(heightRatio * maxH, maxH * 0.95f);
                mAmplitudeLevels[i] = heightRatio;
            }
        }
        startAnimation();
    }

    // ============================================================
    // ★★★ 动画（使用 LERP_FACTOR） ★★★
    // ============================================================

    private void startAnimation() {
        if (mAnimator != null) {
            mAnimator.cancel();
        }
        mAnimator = ValueAnimator.ofFloat(0f, 1f);
        mAnimator.setDuration(ANIMATION_DURATION);
        mAnimator.addUpdateListener(animation -> {
            for (int i = 0; i < BAR_COUNT; i++) {
                mBarHeights[i] += (mTargetHeights[i] - mBarHeights[i]) * LERP_FACTOR;
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

        // 检查是否需要切换颜色方案
        checkColorCycleSwitch();

        final int width = getWidth();
        final int height = getHeight();
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
            if (mShowStripes && barHeight >= MIN_HEIGHT_FOR_STRIPES) {
                // 动态计算实际能显示的分段数量
                float segmentHeightWithSpacing = mSegmentSpacingFixed * 2; // 每段高度+间距
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
