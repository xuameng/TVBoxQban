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
 * <p>
 * 新增特性：
 * 1，颜色随振幅大小变化
 * 2，保持原有动画平滑性
 * 3，完全兼容原有接口
 * 4，振幅随音量大小变化
 * 5，三种颜色随机变化
 * 6，新增音柱分段效果
 * 7， 20261120强音扩散算法：强节奏时主柱+相邻柱协同弹起，距离越远越矮（xuameng 新增）
 * 8， 20261121新增兜底音柱高度防止音柱消失
 * 9， 20261122判断暂停静音时隐藏音柱，改成64音柱，频段一对一，矮柱两次加权，动画平滑优化
 * 10，20261122实际音柱高度计算颜色，分段防悬空感
 * 11，20261122统一音量响应模型：扩散+余震+主柱兜底+托底全部纳入volumeLevel控制（xuameng 新增）
 */
public class MusicVisualizerView extends View {

    private static final int MAX_AMPLITUDE = 10222;  // xuameng判断音量大小
    private static final int BAR_COUNT = 64;          // xuameng 音柱数量
    private static final int ANIMATION_DURATION = 60; // xuameng 音柱动画时长

    // xuameng 是否应该绘制音柱
    private boolean mVisible = true;

    // ==================== xuameng 新增：强音扩散参数 ====================
    // 强音扩散半径（向左右各影响几个音柱）
    private static final int BEAT_SPREAD_RADIUS = 11;
    // 扩散衰减系数（越大衰减越慢，扩散越远，0~1之间）
    private static final float BEAT_DECAY = 0.80f;
    // =====================================================================

    /**
     * 最低可见高度比例（防止小音量时柱子消失）
     * 调大 → 最小柱子更高
     * 调小 → 差距更大但可能消失
     * 当前 0.005（0.5%）
     */
    private static final float MIN_VISIBLE_RATIO = 0.005f;

    // 改为非静态变量实现动态刷新
    private int[][] colorSchemes = new int[3][3];

    // 新增音柱分段效果相关变量
    private boolean mShowStripes = true; // 是否显示分段效果
    private int mSegmentCount = 20;      // 每个音柱的分段数
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

    // 绘图工具
    private final Paint mBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] mBarHeights = new float[BAR_COUNT];
    private final float[] mTargetHeights = new float[BAR_COUNT];

    // 动画控制器
    private ValueAnimator mAnimator;

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

    // 新增音柱分段效果设置方法
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

    /**
     * xuameng 新增：音量因子映射
     * 将 volumeLevel(0~1) 映射为响应曲线
     * 平方曲线：小音量变化更明显，大音量不溢出
     */
    private float getVolumeFactor(float volumeLevel) {
        volumeLevel = Math.max(0f, Math.min(1f, volumeLevel));
        return volumeLevel * volumeLevel;
    }

    public void updateVisualizer(byte[] fft, float volumeLevel) {
        if (fft == null || fft.length < BAR_COUNT * 2 + 2) return;
        // xuameng处理静音状态重置音柱
        if (volumeLevel <= 0f) {
            reset();  
            return;
        }
        mVisible = true; // xuameng 有数据就显示
        // 修改采样策略：前1/3柱子采样低频，后2/3均匀采样中高频
        for (int i = 0; i < BAR_COUNT; i++) {
            // xuameng 1点取1（每根音柱对应一个复数对）
            int barIndex = 2 + i * 2;
            if (barIndex < fft.length - 1) {
                byte rfk = fft[barIndex];
                byte ifk = fft[barIndex + 1];
                float magnitude = (rfk * rfk + ifk * ifk);

                // xuameng改进的频率加权策略（三段式加权）
                float weight;
                int third = BAR_COUNT / 3; // 21
                if (i < third) {
                    // 第一段：xuameng 超低频段(0-200Hz)增益
                    weight = 1.0f;
                } else if (i < third * 2) {
                    // 第二段：xuameng 中低频段(200-800Hz)基准值增益
                    weight = 2.8f;
                } else {
                    // 第三段：xuameng 高频段(800Hz+)指数增强
                    float freqFactor = (float) Math.pow(1.5, (i - third * 2) / 2.0);
                    weight = 2.8f * freqFactor;
                    weight = Math.min(weight, 15.0f); // 限制最大增益
                }
                mTargetHeights[i] = Math.min(
                        (magnitude * getHeight() * weight * volumeLevel) / MAX_AMPLITUDE, // xuameng判断音量大小
                        getHeight() * 0.95f
                );
            }
        }

        // ==================== 统一音量响应模型（xuameng 20261122 新增） ====================
        // 整合：扩散 + 余震 + 主柱兜底 + 托底，全部由 volumeLevel 统一驱动

        final float volumeFactor = getVolumeFactor(volumeLevel);
        final float maxH = getHeight();

        // ---------- 1. 归一化能量评估（脱离像素，用 0~1） ----------
        int dominantIndex = 0;
        float maxEnergy = 0f;  // 归一化能量 0~1
        float[] normalizedEnergy = new float[BAR_COUNT];

        for (int i = 0; i < BAR_COUNT; i++) {
            normalizedEnergy[i] = maxH > 0 ? (mTargetHeights[i] / maxH) : 0f;
            if (normalizedEnergy[i] > maxEnergy) {
                maxEnergy = normalizedEnergy[i];
                dominantIndex = i;
            }
        }

        // ---------- 2. 主柱决策层（xuameng：替代原 DOMINANT_MIN_RATIO 逻辑） ----------
        // 主柱"够格"判定：归一化能量 × 音量 超过动态阈值
        // 音量小 → 阈值低 → 容易触发（不死寂）
        // 音量大 → 阈值高 → 只有真强拍才触发
        float beatThreshold = 0.08f + 0.12f * volumeFactor; // 8% ~ 20%
        boolean hasBeat = (maxEnergy > beatThreshold) && (volumeLevel > 0.03f);

        // 主柱兜底：不是"拉满"，而是"给面子"
        // xuameng：兜底高度随音量软化，不再固定 90%
        float baseMinRatio = 0.35f + 0.55f * volumeFactor; // 35% ~ 90%
        float dominantMinEnergy = baseMinRatio * volumeFactor;
        dominantMinEnergy = Math.max(dominantMinEnergy, beatThreshold * 1.2f);

        if (normalizedEnergy[dominantIndex] < dominantMinEnergy) {
            normalizedEnergy[dominantIndex] = dominantMinEnergy;
            maxEnergy = dominantMinEnergy;
            mTargetHeights[dominantIndex] = maxEnergy * maxH;
        }

        // ---------- 3. 强音扩散层（xuameng：替代原强音扩散算法） ----------
        if (hasBeat) {
            float spreadStrength = 0.25f + 0.70f * volumeFactor; // 0.25 ~ 0.95

            float[] spreadHeights = new float[BAR_COUNT];
            for (int i = 0; i < BAR_COUNT; i++) {
                spreadHeights[i] = mTargetHeights[i];
            }

            // 以主音柱为中心，向左右扩散
            for (int i = 0; i < BAR_COUNT; i++) {
                int distance = Math.abs(i - dominantIndex);
                if (distance == 0) continue;

                if (distance <= BEAT_SPREAD_RADIUS) {
                    // 相邻柱：距离越近衰减越小，越近越高
                    float spreadFactor = (float) Math.pow(BEAT_DECAY, distance);
                    float boost = maxH * maxEnergy * spreadFactor * spreadStrength;
                    spreadHeights[i] = mTargetHeights[i] + boost;
                    // 限制最大高度不超过视图的95%
                    spreadHeights[i] = Math.min(spreadHeights[i], maxH * 0.95f);
                }
            }

            // 将扩散结果写回目标高度数组
            System.arraycopy(spreadHeights, 0, mTargetHeights, 0, BAR_COUNT);
        }
        // ==================== 强音扩散算法结束 ====================

        // ---------- 4. 弱柱余震层（xuameng：替代原余震跟随逻辑） ----------
        float aftershockMaxEnergy = 0.15f + 0.20f * volumeFactor; // 15% ~ 35%

        for (int i = 0; i < BAR_COUNT; i++) {

            // 1. 已经是正常柱 → 不干预
            float weakThreshold = maxH * 0.01f; // 1% 高度作为弱柱阈值
            if (mTargetHeights[i] >= weakThreshold) {
                continue;
            }

            // 2. 弱柱处理
            if (hasBeat) {

                // 3. 排除已在强音扩散半径内的柱（避免逻辑冲突）
                int distance = Math.abs(i - dominantIndex);
                if (distance <= BEAT_SPREAD_RADIUS) {
                    continue;
                }

                // 4. 余震：随机高度 × 音量因子，但不超过上限
                float randomRatio = (float) Math.random(); // 0~1
                float aftershockEnergy = aftershockMaxEnergy * randomRatio * volumeFactor;
                float aftershockHeight = aftershockEnergy * maxH;

                // 5. 至少保留最低可见高度
                aftershockHeight = Math.max(aftershockHeight, maxH * MIN_VISIBLE_RATIO);

                mTargetHeights[i] = aftershockHeight;
            } else {
                // 6. 无强音时：保持最低可见高度（不跳）
                mTargetHeights[i] = maxH * MIN_VISIBLE_RATIO;
            }
        }
        // ==================== 余震跟随结束 ====================

        // ---------- 5. 最低可见高度托底（统一出口，xuameng 保留原逻辑） ----------
        if (maxH <= 0) maxH = 100f;
        float minVisibleHeight = maxH * MIN_VISIBLE_RATIO;

        for (int i = 0; i < BAR_COUNT; i++) {
            // 1. 先限制最大高度
            mTargetHeights[i] = Math.min(mTargetHeights[i], maxH * 0.95f);
   
            // 2. 小音量托底（防止柱子消失）
            if (mTargetHeights[i] < minVisibleHeight) {
                mTargetHeights[i] = minVisibleHeight;
            }
        }
        // ==================== 托底逻辑结束 ====================
        // ==================== 统一音量响应模型结束 ====================

        startAnimation();
    }

    private void startAnimation() {
        if (mAnimator != null) {
            mAnimator.cancel();
        }
        mAnimator = ValueAnimator.ofFloat(0f, 1f);
        mAnimator.setDuration(ANIMATION_DURATION);
        mAnimator.addUpdateListener(animation -> {
            // xuameng 找到当前主音柱（动画过程中最高的）
            int dominantIdx = 0;
            float maxH = 0f;
            for (int i = 0; i < BAR_COUNT; i++) {
                if (mBarHeights[i] > maxH) {
                    maxH = mBarHeights[i];
                    dominantIdx = i;
                }
            }

            for (int i = 0; i < BAR_COUNT; i++) {
                if (mTargetHeights[i] > mBarHeights[i]) {
                    // 起跳快（缓出感）：主音柱起跳更快，相邻柱稍慢
                    float boost = (i == dominantIdx) ? 0.65f : 0.60f;
                    mBarHeights[i] += (mTargetHeights[i] - mBarHeights[i]) * boost;
                } else {
                    // 下落慢（拖尾感）
                    mBarHeights[i] += (mTargetHeights[i] - mBarHeights[i]) * 0.15f;
                }
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
        final float barWidth = width / (float) BAR_COUNT;
        final float gap = barWidth * 0.15f;  //xuameng 音柱间距
        final float barUnitWidth = barWidth - gap;

        // 新增：定义分段效果的最小高度阈值（例如4像素）
        final float MIN_HEIGHT_FOR_STRIPES = 20f;

        for (int i = 0; i < BAR_COUNT; i++) {
            float left = i * barWidth + gap / 2;
            float right = left + barUnitWidth;
            float barHeight = Math.min(mBarHeights[i], height * 0.9f);
            float top = height - barHeight;

            // xuameng：改为按实际音柱高度计算颜色
            float normalizedHeight = barHeight / (height > 0 ? height : 1f);
            normalizedHeight = Math.max(0f, Math.min(1f, normalizedHeight));
            int color = getDynamicColor(normalizedHeight, colorSchemes[currentSchemeIndex]);
            mBarPaint.setColor(color);

            // 分段绘制音柱（实现"一段一段"效果）
            // 修改点：分段绘制音柱（仅当音柱高度达到最小阈值时才显示分段效果）
            if (mShowStripes && barHeight >= MIN_HEIGHT_FOR_STRIPES) {
                // ========== xuameng: 底部实柱兜底区域（不画分段） ==========
                float minVisibleHeight = height * MIN_VISIBLE_RATIO;
                float stripeTop = height - barHeight + minVisibleHeight; // 分段起始Y（从兜底线以上开始）
                //第一步：画底部实心兜底柱(要不然分段效果会有悬空感)
                canvas.drawRect(left, height - minVisibleHeight, right, height, mBarPaint);
                // =========================================================
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

                    // ========== xuameng: 只画在兜底线以上 ==========
                    if (segmentBottom <= stripeTop) {
                        continue; // 底部兜底区域内不画分段
                    }
                    if (segmentTop < stripeTop) {
                        segmentTop = stripeTop; // 截断，不超出兜底区
                    }
                    // ==============================================
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
        }
        mVisible = false;
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
