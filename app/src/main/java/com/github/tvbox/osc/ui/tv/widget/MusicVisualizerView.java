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
private float mFloorDb = -75f;
private float mCeilDb = -12f;
private float mAutoGain = 1f;
private float mSmoothEnergy = 0f;

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

    // 静音直接重置
    if (volumeLevel <= 0f) {
        reset();
        return;
    }

    mVisible = true;

    float maxH = getHeight();
    if (maxH <= 0) maxH = 100f;

    // ===== AGC 相关（类成员变量，需补在类里）=====
    // private float mFloorDb = -75f;
    // private float mCeilDb = -12f;
    // private float mAutoGain = 1f;
    // private float mSmoothEnergy = 0f;

    float frameMaxDb = -120f;
    float frameAvgDb = 0f;

    // ===== 1. 先算 dB =====
    for (int i = 0; i < BAR_COUNT; i++) {
        int barIndex;
        if (i < BAR_COUNT / 3) {
            barIndex = 2 + i * 2;
        } else {
            barIndex = 2 + (BAR_COUNT / 3) * 2 + (i - BAR_COUNT / 3) * 4;
        }

        if (barIndex >= fft.length - 1) continue;

        byte rfk = fft[barIndex];
        byte ifk = fft[barIndex + 1];
        float magnitude = (rfk * rfk + ifk * ifk);

        float db = magnitude > 0
                ? 10f * (float) Math.log10(magnitude)
                : -120f;

        frameMaxDb = Math.max(frameMaxDb, db);
        frameAvgDb += db;
    }
    frameAvgDb /= BAR_COUNT;

    // ===== 2. 动态范围自适应 =====
    if (frameMaxDb > mCeilDb) {
        mCeilDb += (frameMaxDb - mCeilDb) * 0.1f;
    } else {
        mCeilDb += (-20f - mCeilDb) * 0.01f; // 慢慢回落
    }

    if (frameAvgDb > mFloorDb) {
        mFloorDb += (frameAvgDb - mFloorDb) * 0.05f;
    } else {
        mFloorDb += (-70f - mFloorDb) * 0.02f;
    }

    mCeilDb = Math.min(mCeilDb, -6f);
    mFloorDb = Math.max(mFloorDb, -80f);

    // ===== 3. 自动增益 =====
    float energyRatio = (frameAvgDb - mFloorDb) / (mCeilDb - mFloorDb);
    energyRatio = Math.max(0f, Math.min(1f, energyRatio));

    mSmoothEnergy += (energyRatio - mSmoothEnergy) * 0.05f;

    float targetGain = 1f + (1f - mSmoothEnergy) * 2.5f;
    targetGain = Math.max(1f, Math.min(targetGain, 3.5f));
    mAutoGain += (targetGain - mAutoGain) * 0.08f;

    // ===== 4. 正式算每个 bar =====
    for (int i = 0; i < BAR_COUNT; i++) {
        int barIndex;
        if (i < BAR_COUNT / 3) {
            barIndex = 2 + i * 2;
        } else {
            barIndex = 2 + (BAR_COUNT / 3) * 2 + (i - BAR_COUNT / 3) * 4;
        }

        if (barIndex >= fft.length - 1) continue;

        byte rfk = fft[barIndex];
        byte ifk = fft[barIndex + 1];
        float magnitude = (rfk * rfk + ifk * ifk);

        float db = magnitude > 0
                ? 10f * (float) Math.log10(magnitude)
                : -120f;

        // 归一化
        float norm = (db - mFloorDb) / (mCeilDb - mFloorDb);
        norm = Math.max(0f, Math.min(1f, norm));

        // 音量加权
        norm *= (0.3f + 1.2f * volumeLevel);

        // 频段补偿（克制版）
        if (i < BAR_COUNT / 4) {
            norm *= 1.3f; // 低频
        } else if (i < BAR_COUNT / 2) {
            norm *= 1.1f; // 中频
        } else {
            norm *= 1.5f; // 高频
        }

        norm = Math.min(norm, 1f);

        // 峰值保持
        if (norm > mBarPeaks[i]) {
            mBarPeaks[i] = norm;
        }

        float decay = (i >= BAR_COUNT / 2) ? 0.45f : 0.55f;
        mBarPeaks[i] -= mBarPeaks[i] * decay;
        if (mBarPeaks[i] < 0.003f) mBarPeaks[i] = 0f;

        // 高度映射
        float heightRatio = (float) Math.pow(mBarPeaks[i], 0.45f);
        heightRatio = Math.max(heightRatio, 0.05f); // 最小可见

        mTargetHeights[i] = Math.min(
                heightRatio * maxH * mAutoGain,
                maxH * 0.95f
        );

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
