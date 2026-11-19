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
 * 7，强音扩散算法：强节奏时主柱+相邻柱协同弹起，距离越远越矮（xuameng 新增）
 * 8，节奏检测触发扩散：检测能量突变只在Beat发生时扩散，避免误触发（xuameng 升级）
 */
public class MusicVisualizerView extends View {

    private static final int MAX_AMPLITUDE = 9222;  // xuameng判断音量大小
    private static final int BAR_COUNT = 22;          // xuameng 音柱数量
    private static final int ANIMATION_DURATION = 60; // xuameng 音柱动画时长

    // ==================== xuameng 新增：强音扩散参数 ====================
    // 强音扩散半径（向左右各影响几个音柱）
    private static final int BEAT_SPREAD_RADIUS = 3;
    // 扩散衰减系数（越大衰减越慢，扩散越远，0~1之间）
    private static final float BEAT_DECAY = 0.65f;
    // =====================================================================

    // ==================== xuameng 新增：节奏检测参数（方案C） ====================
    // 节奏检测灵敏度（当前帧能量 / 历史平均能量的比值超过此值才判定为Beat）
    // 1.3 = 极灵敏（电子乐/快歌）  1.6 = 标准（流行/摇滚）  2.0 = 迟钝（只响应重鼓）
    private static final float BEAT_SENSITIVITY = 1.6f;
    // Beat触发后的冷却时间(ms)，防止同一拍触发多次扩散
    // 120ms ≈ 500BPM极限速度，正常歌曲不会超过
    private static final long BEAT_COOLDOWN = 120;
    // 历史能量平滑系数（指数移动平均），控制阈值跟随速度
    // 越小 = 阈值越稳定适合稳定节奏  越大 = 阈值跟得快适合变速音乐
    private static final float ENERGY_SMOOTHING = 0.08f;
    // 最低能量门槛：当前帧总能量必须超过视图总高度的这个比例才允许触发
    // 防止极弱信号误判为Beat
    private static final float MIN_BEAT_ENERGY_RATIO = 0.05f;
    // 能量突增比例门槛：当前能量/历史能量必须超过此值
    // 进一步验证"是不是真的突然变大了"而不是缓慢爬升
    private static final float ENERGY_SPIKE_RATIO = 1.3f;

    // 节奏检测内部状态变量
    private float mEnergyHistory = 0f;        // 历史能量均值（指数移动平均）
    private long mLastBeatTime = 0;           // 上次Beat发生的时间戳(ms)
    // =========================================================================

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

    public void updateVisualizer(byte[] fft, float volumeLevel) {
        if (fft == null || fft.length < BAR_COUNT * 2 + 2) return;
        // xuameng处理静音状态重置音柱
        if (volumeLevel <= 0f) {
            reset();
            // xuameng 静音时重置节奏检测状态
            mEnergyHistory = 0f;
            mLastBeatTime = 0;
            return;
        }
        // 修改采样策略：前1/3柱子重点采样低频，后2/3均匀采样中高频
        for (int i = 0; i < BAR_COUNT; i++) {
            int barIndex;
            if (i < BAR_COUNT / 3) {
                // 低频段密集采样（每4点取1）
                barIndex = 2 + i * 4;
            } else {
                // 中高频段间隔采样（每2点取1）
                barIndex = 2 + (BAR_COUNT / 3) * 4 + (i - BAR_COUNT / 3) * 2;
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

                mTargetHeights[i] = Math.min(
                        (magnitude * getHeight() * weight * volumeLevel) / MAX_AMPLITUDE, // xuameng判断音量大小
                        getHeight() * 0.95f
                );
                mAmplitudeLevels[i] = Math.min(magnitude / MAX_AMPLITUDE, 1.0f);
            }
        }

        // ==================== xuameng 升级：节奏检测触发扩散算法（方案C） ====================
        // 原理：检测"能量突变"而非"绝对值大小"，只在Beat发生时扩散
        // 人耳对节奏的感知是"突然变大声"而不是"声音大"，所以检测突变最准确

        // 1. 计算当前帧总能量（所有音柱高度之和）
        float currentEnergy = 0f;
        for (int i = 0; i < BAR_COUNT; i++) {
            currentEnergy += mTargetHeights[i];
        }

        // 2. 更新历史能量均值（指数移动平均，让阈值能自适应不同歌曲的音量）
        mEnergyHistory = mEnergyHistory * (1 - ENERGY_SMOOTHING)
                       + currentEnergy * ENERGY_SMOOTHING;

        // 3. 计算动态阈值 = 历史均值 × 灵敏度系数
        float beatThreshold = mEnergyHistory * BEAT_SENSITIVITY;

        // 4. 节奏检测：判断是否满足Beat触发条件
        long now = System.currentTimeMillis();
        boolean isBeat = false;

        // 条件A：当前能量超过动态阈值
        // 条件B：当前能量超过最低绝对门槛（防止极弱信号误判）
        // 条件C：距离上次Beat已超过冷却时间（防止同一拍触发多次）
        if (currentEnergy > beatThreshold
                && currentEnergy > getHeight() * BAR_COUNT * MIN_BEAT_ENERGY_RATIO
                && (now - mLastBeatTime) > BEAT_COOLDOWN) {

            // 5. 额外验证：能量突增比例（确认是"突然变大"而非缓慢爬升）
            float energyRatio = currentEnergy / Math.max(mEnergyHistory, 1f);
            if (energyRatio > ENERGY_SPIKE_RATIO) {
                // ✅ 确认为Beat！
                isBeat = true;
                mLastBeatTime = now;
            }
        }

        // 6. 只在Beat确认时执行扩散
        if (isBeat) {
            // 6a. 找能量最强的低频柱作为扩散中心（鼓点通常在低频段）
            int dominantIndex = 0;
            float maxLowFreq = 0f;

            // 优先从前1/3低频段找主音柱（鼓点集中在低频）
            int searchEnd = Math.min(BAR_COUNT / 3, BAR_COUNT);
            for (int i = 0; i < searchEnd; i++) {
                if (mTargetHeights[i] > maxLowFreq) {
                    maxLowFreq = mTargetHeights[i];
                    dominantIndex = i;
                }
            }

            // 6b. 如果低频段整体不够强（比如某些歌曲低频弱），退而求其次找全局最高柱
            if (maxLowFreq < getHeight() * 0.1f) {
                maxLowFreq = 0f;
                for (int i = 0; i < BAR_COUNT; i++) {
                    if (mTargetHeights[i] > maxLowFreq) {
                        maxLowFreq = mTargetHeights[i];
                        dominantIndex = i;
                    }
                }
            }

            // 6c. 创建临时数组存储扩散后的高度，不破坏原始计算结果
            float[] spreadHeights = new float[BAR_COUNT];
            System.arraycopy(mTargetHeights, 0, spreadHeights, 0, BAR_COUNT);

            // 6d. 以主音柱为中心，向左右扩散能量
            for (int i = 0; i < BAR_COUNT; i++) {
                int distance = Math.abs(i - dominantIndex);
                if (distance == 0) continue; // 主柱本身不处理，保持最高

                if (distance <= BEAT_SPREAD_RADIUS) {
                    // 相邻柱：距离越近衰减越小，弹得越高
                    float spreadFactor = (float) Math.pow(BEAT_DECAY, distance);
                    // 在自身原始高度基础上叠加主柱能量的衰减部分
                    // 0.85f 控制扩散强度，避免叠加后过高
                    spreadHeights[i] = mTargetHeights[i]
                            + maxLowFreq * spreadFactor * 0.85f;
                }

                // 限制最大高度不超过视图的95%
                spreadHeights[i] = Math.min(spreadHeights[i], getHeight() * 0.95f);
            }

            // 6e. 主音柱额外加强15%（增强打击感，让Beat更醒目）
            spreadHeights[dominantIndex] = Math.min(
                    spreadHeights[dominantIndex] * 1.15f,
                    getHeight() * 0.95f
            );

            // 6f. 将扩散结果写回目标高度数组
            System.arraycopy(spreadHeights, 0, mTargetHeights, 0, BAR_COUNT);
        }
        // ==================== 节奏检测扩散算法结束 ====================

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
            // 修改点：分段绘制音柱（仅当音柱高度达到最小阈值时才显示分段效果）
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
