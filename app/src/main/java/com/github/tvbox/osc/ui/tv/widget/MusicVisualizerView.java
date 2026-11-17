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
*/
public class MusicVisualizerView extends View {
    private static final int MAX_AMPLITUDE = 12222;  //xuameng判断音量大小
    private static final int BAR_COUNT = 22;   //xuameng 音柱数量
    private static final int ANIMATION_DURATION = 80; //xuameng 音柱动画时长
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
// ==================== 新增：平滑用的成员变量（加到类里） ====================
// 放在 BAR_COUNT 那些变量旁边就行：
private float mSmoothedGlobalEnergy = 0f;
private int mSmoothedDominantBar = BAR_COUNT / 2; // 默认中间
private static final float ENERGY_SMOOTH_FACTOR = 0.25f;   // 能量平滑系数（越小越丝滑）
private static final float DOMINANT_SMOOTH_FACTOR = 0.15f;  // 主频平滑系数（越小越稳）
// ==========================================================================

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

    // ---------- 静音处理 ----------
    if (volumeLevel <= 0.001f) {
        reset();
        return;
    }

    // ---------- 第一步：扫描全频谱，找最大能量和主频 ----------
    float globalMaxMagnitude = 0f;
    int dominantBinIndex = 2; // 跳过DC分量，从第一个有效bin开始

    for (int i = 2; i < fft.length - 1; i += 2) {
        byte rfk = fft[i];
        byte ifk = fft[i + 1];
        float magnitude = (rfk * rfk + ifk * ifk);

        if (magnitude > globalMaxMagnitude) {
            globalMaxMagnitude = magnitude;
            dominantBinIndex = i;
        }
    }

    // ---------- 第二步：把主频bin映射到音柱索引 ----------
    // fft数组：索引2开始，每2个byte是一个复数（实部+虚部）
    // 所以 binIndex = i / 2
    int dominantBin = dominantBinIndex / 2;

    // 映射到 BAR_COUNT 范围（线性映射）
    int dominantBar = (int) ((float) dominantBin / ((fft.length / 2) - 1) * (BAR_COUNT - 1));
    dominantBar = Math.max(0, Math.min(BAR_COUNT - 1, dominantBar));

    // ---------- 第三步：平滑（关键！防止乱跳） ----------
    // 3a. 能量平滑
    float rawEnergy = Math.min(globalMaxMagnitude / MAX_AMPLITUDE, 1.0f);
    rawEnergy *= volumeLevel; // 音量权重
    mSmoothedGlobalEnergy = mSmoothedGlobalEnergy * (1f - ENERGY_SMOOTH_FACTOR)
            + rawEnergy * ENERGY_SMOOTH_FACTOR;

    // 3b. 主频平滑（让主峰位置变化慢一点）
    mSmoothedDominantBar = (int) (mSmoothedDominantBar * (1f - DOMINANT_SMOOTH_FACTOR)
            + dominantBar * DOMINANT_SMOOTH_FACTOR);
    mSmoothedDominantBar = Math.max(0, Math.min(BAR_COUNT - 1, mSmoothedDominantBar));

    // ---------- 第四步：计算每个音柱的目标高度 ----------
    float globalScale = getHeight() * 0.95f * mSmoothedGlobalEnergy;

    for (int i = 0; i < BAR_COUNT; i++) {
        // 距离主频越远 → 衰减越大
        float distance = Math.abs(i - mSmoothedDominantBar);
        float normalizedDistance = distance / (float) BAR_COUNT;

        // 用高斯衰减（比线性好看，中间高两边自然下降）
        float sigma = 0.35f; // 控制衰减宽度，越小越集中
        float distanceFactor = (float) Math.exp(-(normalizedDistance * normalizedDistance)
                / (2 * sigma * sigma));

        // 轻微的频率加权（保留你原来的低频增益思路，但弱化）
        float freqBias;
        if (i < BAR_COUNT / 4) {
            freqBias = 1.1f;       // 超低频微增益
        } else if (i < BAR_COUNT / 2) {
            freqBias = 1.05f;      // 中低频微增益
        } else {
            freqBias = 0.95f;      // 高频微衰减
        }

        mTargetHeights[i] = globalScale * distanceFactor * freqBias;
        mTargetHeights[i] = Math.min(mTargetHeights[i], getHeight() * 0.95f);

        // 振幅等级（给颜色渐变用，保留你原来的逻辑）
        mAmplitudeLevels[i] = Math.min(
                mTargetHeights[i] / (getHeight() * 0.95f),
                1.0f
        );
    }

    // ---------- 第五步：启动动画 ----------
    startAnimation();
}
    private void startAnimation() {
        if(mAnimator != null) {
            mAnimator.cancel();
        }
        mAnimator = ValueAnimator.ofFloat(0f, 1f);
        mAnimator.setDuration(ANIMATION_DURATION);
        mAnimator.addUpdateListener(animation -> {
            float fraction = animation.getAnimatedFraction();
            for(int i = 0; i < BAR_COUNT; i++) {
                mBarHeights[i] += (mTargetHeights[i] - mBarHeights[i]) * fraction;
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
