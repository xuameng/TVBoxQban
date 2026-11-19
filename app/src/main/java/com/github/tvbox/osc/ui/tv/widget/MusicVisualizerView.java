package com.github.tvbox.osc.ui.tv.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.animation.ValueAnimator;

/**
xuameng
音乐可视化视图组件（带振幅颜色渐变）
新增特性：
1，颜色随振幅大小变化
2，保持原有动画平滑性
3，完全兼容原有接口
4，振幅随音量大小变化
5，三种颜色随机变化
6，新增音柱分段效果
7，鼓点检测 + 强音扩散：只对鼓点/节奏乐器触发扩散，相邻柱协同弹起（xuameng 新增）
*/

public class MusicVisualizerView extends View {
    private static final int MAX_AMPLITUDE = 12222;  //xuameng判断音量大小
    private static final int BAR_COUNT = 22;   //xuameng 音柱数量
    private static final int ANIMATION_DURATION = 80; //xuameng 音柱动画时长

    // ==================== xuameng 新增：鼓点检测参数 ====================
    private static final int KICK_BAND_START = 0;      // 鼓点检测起始频带（最低频）
    private static final int KICK_BAND_END = 6;        // 鼓点检测结束频带（前4个bar覆盖底鼓区域）
    private static final float KICK_THRESHOLD = 1.6f;  // 能量突增倍率阈值（当前能量 > 历史均值*1.6 判定为鼓点）
    private static final long MIN_BEAT_INTERVAL = 80;  // 最小鼓点间隔(ms)，防止连续触发
    private float mKickEnergyHistory = 0f;             // 低频能量历史均值（指数滑动平均）
    private long mLastBeatTime = 0;                    // 上次鼓点触发时间
    // =================================================================

    // ==================== xuameng 新增：强音扩散参数 ====================
    private static final int BEAT_SPREAD_RADIUS = 3;   // 扩散半径（向左右各影响几个音柱）
    private static final float BEAT_DECAY = 0.50f;     // 扩散衰减系数（越大衰减越快，0~1之间）
    // =================================================================

    // 改为非静态变量实现动态刷新
    private int[][] colorSchemes = new int[3][3];
    // 新增音柱分段效果相关变量
    private boolean mShowStripes = true; // 是否显示分段效果
    private int mSegmentCount = 20; // 每个音柱的分段数
    private float mSegmentSpacingFixed = 2.5f; // 段间距固定值（像素）
    // 新增颜色方案刷新方法
    private void refreshColorSchemes() {
        for(int i = 0; i < 3; i++) {
            float baseHue = (float)(Math.random() * 360);
            colorSchemes[i] = new int[] {
                Color.HSVToColor(new float[] {
                        baseHue,
                        1f,
                        1f
                    }),
                    Color.HSVToColor(new float[] {
                        (baseHue + 120) % 360, 1f, 1f
                    }),
                    Color.HSVToColor(new float[] {
                        (baseHue + 240) % 360, 1f, 1f
                    })
            };
        }
        postInvalidate(); // 新增：立即刷新视图
    }
    private static final long COLOR_CYCLE_DURATION = (long)(0.1 * 60 * 1000); // 6秒切换
    private int currentSchemeIndex = 0;
    private long lastSwitchTime = 0;
    // 绘图工具
    private final Paint mBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] mBarHeights = new float[BAR_COUNT];
    private final float[] mTargetHeights = new float[BAR_COUNT];
    private final float[] mAmplitudeLevels = new float[BAR_COUNT]; // 存储各柱振幅强度
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

    // ==================== xuameng 新增：鼓点检测方法 ====================
    /**
     * 鼓点检测算法
     * 原理：低频区（底鼓集中区域）能量突发检测
     * 判定条件：
     * 1. 低频区总能量 > 历史均值 × 阈值倍率（突发）
     * 2. 距离上次鼓点 > 最小间隔（防抖）
     * 3. 使用指数滑动平均跟踪历史能量（自适应不同音量）
     *
     * @return true = 检测到鼓点，false = 非鼓点
     */
    private boolean detectBeat() {
        // 计算低频区总能量（前4个bar对应最低频区域）
        float currentKickEnergy = 0f;
        for (int i = KICK_BAND_START; i <= KICK_BAND_END && i < BAR_COUNT; i++) {
            currentKickEnergy += mTargetHeights[i];
        }

        // 首次调用时初始化历史均值
        if (mKickEnergyHistory == 0f) {
            mKickEnergyHistory = currentKickEnergy;
            return false;
        }

        // 判定突发：当前能量远超历史均值
        boolean isSpike = currentKickEnergy > mKickEnergyHistory * KICK_THRESHOLD;

        // 防抖：距离上次鼓点至少 MIN_BEAT_INTERVAL ms
        long now = System.currentTimeMillis();
        boolean isIntervalOk = (now - mLastBeatTime) > MIN_BEAT_INTERVAL;

        // 指数滑动平均更新历史（80%旧值 + 20%新值，平滑跟踪音量变化）
        mKickEnergyHistory = mKickEnergyHistory * 0.8f + currentKickEnergy * 0.2f;

        // 同时满足突发 + 防抖条件才判定为鼓点
        if (isSpike && isIntervalOk) {
            mLastBeatTime = now;
            return true;
        }

        return false;
    }
    // =================================================================

    public void updateVisualizer(byte[] fft, float volumeLevel) {
        if(fft == null || fft.length < BAR_COUNT * 2 + 2) return;
        if(volumeLevel == 0f || volumeLevel == 0.0f || volumeLevel == 0.00f) {
            reset(); //xuameng处理静音状态重置音柱
            return;  // xuameng 新增：静音时直接返回，不计算扩散
        }
        // 修改采样策略：前1/3柱子重点采样低频，后2/3均匀采样中高频
        for(int i = 0; i < BAR_COUNT; i++) {
            int barIndex;
            if(i < BAR_COUNT / 3) {
                // 低频段密集采样（每2点取1）
                barIndex = 2 + i * 2;
            } else {
                // 中高频段间隔采样（每4点取1）
                barIndex = 2 + (BAR_COUNT / 3) * 2 + (i - BAR_COUNT / 3) * 4;
            }
            if(barIndex < fft.length - 1) {
                byte rfk = fft[barIndex];
                byte ifk = fft[barIndex + 1];
                float magnitude = (rfk * rfk + ifk * ifk);
                // xuameng改进的频率加权策略（三段式加权）
                float weight;
                if(i < BAR_COUNT / 4) {
                    weight = 1.0f; //xuameng 超低频段(0-200Hz)增益
                } else if(i < BAR_COUNT / 2) {
                    weight = 2.5f; //xuameng 中低频段(200-800Hz)基准值增益
                } else {
                    float freqFactor = (float) Math.pow(1.5, (i - BAR_COUNT / 2) / 2.0); //xuameng 高频段(800Hz+)指数增强
                    weight = 3.5f * freqFactor;
                }
                mTargetHeights[i] = Math.min(
                    (magnitude * getHeight() * weight * volumeLevel) / MAX_AMPLITUDE, //xuameng判断音量大小
                    getHeight() * 0.95f);
                mAmplitudeLevels[i] = Math.min(magnitude / MAX_AMPLITUDE, 1.0f);
            }
        }

        // ==================== xuameng 新增：鼓点检测 + 强音扩散 ====================
        // 只在检测到鼓点时才触发扩散，避免人声/高频乐器导致乱跳
        boolean isBeat = detectBeat();

        if (isBeat) {
            // 找到当前能量最强的主音柱（鼓点核心）
            int dominantIndex = 0;
            float maxTarget = 0f;
            for (int i = 0; i < BAR_COUNT; i++) {
                if (mTargetHeights[i] > maxTarget) {
                    maxTarget = mTargetHeights[i];
                    dominantIndex = i;
                }
            }

            // 创建临时数组存储扩散后的高度
            float[] spreadHeights = new float[BAR_COUNT];
            for (int i = 0; i < BAR_COUNT; i++) {
                spreadHeights[i] = mTargetHeights[i];
            }

            // 以主音柱为中心，向左右扩散
            for (int i = 0; i < BAR_COUNT; i++) {
                int distance = Math.abs(i - dominantIndex);
                float spreadFactor;

                if (distance == 0) {
                    // 主音柱本身保持最高
                    spreadFactor = 1.0f;
                } else if (distance <= BEAT_SPREAD_RADIUS) {
                    // 相邻柱：距离越近衰减越小，越近越高
                    spreadFactor = (float) Math.pow(BEAT_DECAY, distance);
                } else {
                    // 超出扩散半径不参与
                    spreadFactor = 0f;
                }

                if (distance > 0 && spreadFactor > 0f) {
                    // 相邻柱在自身原始高度基础上叠加主柱能量的衰减部分
                    // 0.85f 控制扩散强度，避免叠加后过高
                    spreadHeights[i] = mTargetHeights[i]
                            + maxTarget * spreadFactor * 0.85f;
                }

                // 限制最大高度不超过视图的95%
                spreadHeights[i] = Math.min(spreadHeights[i], getHeight() * 0.95f);
            }

            // 将扩散结果写回目标高度数组
            System.arraycopy(spreadHeights, 0, mTargetHeights, 0, BAR_COUNT);
        }
        // ==================== 鼓点检测 + 扩散结束 ====================

        startAnimation();
    }

    private void startAnimation() {
        if (mAnimator != null) {
            mAnimator.cancel();
        }
        mAnimator = ValueAnimator.ofFloat(0f, 1f);
        mAnimator.setDuration(ANIMATION_DURATION);
        mAnimator.addUpdateListener(animation -> {
            // xuameng 新增：找到动画过程中当前最高的主音柱
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
                    // xuameng 新增：起跳快（缓出感），主音柱起跳更快，相邻柱稍慢
                    float boost = (i == dominantIdx) ? 0.65f : 0.45f;
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
        if(mBarHeights == null) return;
        // 检查是否需要切换颜色方案
        checkColorCycleSwitch();
        final int width = getWidth();
        final int height = getHeight();
        final float barWidth = width / (float) BAR_COUNT;
        final float gap = barWidth * 0.2f;
        final float barUnitWidth = barWidth - gap;
        // 新增：定义分段效果的最小高度阈值（例如4像素）
        final float MIN_HEIGHT_FOR_STRIPES = 20f;
        for(int i = 0; i < BAR_COUNT; i++) {
            float left = i * barWidth + gap / 2;
            float right = left + barUnitWidth;
            float barHeight = Math.min(mBarHeights[i], height * 0.9f);
            float top = height - barHeight;
            // 根据当前颜色方案和振幅强度计算颜色
            int color = getDynamicColor(mAmplitudeLevels[i], colorSchemes[currentSchemeIndex]);
            mBarPaint.setColor(color);
            // 分段绘制音柱（实现"一段一段"效果）
            // 修改点：分段绘制音柱（仅当音柱高度达到最小阈值时才显示分段效果）
            if(mShowStripes && barHeight >= MIN_HEIGHT_FOR_STRIPES) {
                // 动态计算实际能显示的分段数量
                float segmentHeightWithSpacing = mSegmentSpacingFixed * 2; // 每段高度+间距
                int actualSegmentCount = (int)(barHeight / segmentHeightWithSpacing);
                actualSegmentCount = Math.min(actualSegmentCount, mSegmentCount);
                actualSegmentCount = Math.max(1, actualSegmentCount); // 至少显示1段
                // 计算实际段高度（考虑固定间距）
                float actualSegmentHeight = (barHeight - (actualSegmentCount - 1) * mSegmentSpacingFixed) / actualSegmentCount;
                // 从下往上绘制各个段
                for(int segment = 0; segment < actualSegmentCount; segment++) {
                    float segmentTop = height - (segment + 1) * (actualSegmentHeight + mSegmentSpacingFixed);
                    float segmentBottom = segmentTop + actualSegmentHeight;
                    // 确保段在音柱范围内
                    if(segmentTop >= top) {
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
        if(now - lastSwitchTime > COLOR_CYCLE_DURATION) {
            currentSchemeIndex = (currentSchemeIndex + 1) % colorSchemes.length;
            lastSwitchTime = now;
            // 优化刷新逻辑（不再需要currentSchemeIndex检查）
            refreshColorSchemes();
        }
    }

    /**

    根据振幅强度计算渐变颜色
    */
    private int getDynamicColor(float amplitude, int[] currentScheme) {
        amplitude = Math.max(0.0f, Math.min(1.0f, amplitude)); // 确保振幅在有效范围内
        if(amplitude < 0.3f) {
            return interpolateColor(amplitude / 0.3f, currentScheme[0], currentScheme[1]);
        } else {
            return interpolateColor((amplitude - 0.3f) / 0.7f, currentScheme[1], currentScheme[2]);
        }
    }

    /**

    颜色插值计算
    */
    private int interpolateColor(float ratio, int startColor, int endColor) {
        int alpha = (int)(Color.alpha(startColor) + (Color.alpha(endColor) - Color.alpha(startColor)) * ratio);
        int red = (int)(Color.red(startColor) + (Color.red(endColor) - Color.red(startColor)) * ratio);
        int green = (int)(Color.green(startColor) + (Color.green(endColor) - Color.green(startColor)) * ratio);
        int blue = (int)(Color.blue(startColor) + (Color.blue(endColor) - Color.blue(startColor)) * ratio);
        return Color.argb(alpha, red, green, blue);
    }

    public void reset() {
        for(int i = 0; i < BAR_COUNT; i++) {
            mBarHeights[i] = 0;
            mTargetHeights[i] = 0;
            mAmplitudeLevels[i] = 0;
        }
        postInvalidate();
    }

    public void release() {
        if(mAnimator != null) {
            mAnimator.cancel();
            mAnimator.removeAllUpdateListeners();
        }
        reset();
    }
}
