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

// 替换掉之前的平滑变量，用这组：
private float[] mBarEnergyHistory = new float[BAR_COUNT];  // 每个柱的能量历史（用于瞬态检测）
private float mGlobalEnergy = 0f;
private int mCurrentDominantBar = BAR_COUNT / 2;
private static final float ENERGY_ATTACK = 0.6f;   // 上升速度（大=跟得快）
private static final float ENERGY_RELEASE = 0.15f; // 回落速度（小=落得快=有"回"的感觉）
private static final float HISTORY_DECAY = 0.92f;  // 历史能量衰减
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

    if (volumeLevel <= 0.001f) {
        reset();
        return;
    }

    // ---------- 第一步：计算每个频段的相对能量（而非绝对能量） ----------
    float[] barMagnitudes = new float[BAR_COUNT];
    float maxRelativeEnergy = 0f;
    int transientBar = 0;

    for (int i = 0; i < BAR_COUNT; i++) {
        int barIndex;
        if (i < BAR_COUNT / 3) {
            barIndex = 2 + i * 2;
        } else {
            barIndex = 2 + (BAR_COUNT / 3) * 2 + (i - BAR_COUNT / 3) * 4;
        }

        if (barIndex < fft.length - 1) {
            byte rfk = fft[barIndex];
            byte ifk = fft[barIndex + 1];
            float magnitude = (rfk * rfk + ifk * ifk);

            // 相对能量 = 当前能量 / 历史平均能量
            // 这样低频不再永远最高，而是"突然变响的频率"最高
            float historyAvg = mBarEnergyHistory[i] + 1f; // +1防止除零
            float relativeEnergy = magnitude / historyAvg;

            barMagnitudes[i] = relativeEnergy;

            // 更新历史（快速衰减，让新峰值能凸显）
            mBarEnergyHistory[i] = mBarEnergyHistory[i] * HISTORY_DECAY + magnitude * (1f - HISTORY_DECAY);

            if (relativeEnergy > maxRelativeEnergy) {
                maxRelativeEnergy = relativeEnergy;
                transientBar = i;
            }
        }
    }

    // ---------- 第二步：主频 = 瞬态最强的柱 ----------
    mCurrentDominantBar = transientBar;

    // ---------- 第三步：全局能量（用瞬态柱的绝对能量，轻平滑） ----------
    int dominantIdx = transientBar;
    int rawIdx = 2 + (dominantIdx < BAR_COUNT / 3
            ? dominantIdx * 2
            : (BAR_COUNT / 3) * 2 + (dominantIdx - BAR_COUNT / 3) * 4);

    float dominantMagnitude = 0;
    if (rawIdx < fft.length - 1) {
        byte rfk = fft[rawIdx];
        byte ifk = fft[rawIdx + 1];
        dominantMagnitude = (rfk * rfk + ifk * ifk);
    }

    float rawGlobal = Math.min(dominantMagnitude / MAX_AMPLITUDE, 1.0f) * volumeLevel;

    // 快速攻击 + 快速回落（关键！）
    if (rawGlobal > mGlobalEnergy) {
        mGlobalEnergy += (rawGlobal - mGlobalEnergy) * ENERGY_ATTACK;  // 快速跟上
    } else {
        mGlobalEnergy += (rawGlobal - mGlobalEnergy) * ENERGY_RELEASE;  // 快速回落 → "跳回"感
    }

    // ---------- 第四步：计算每个柱的目标高度 ----------
    float globalScale = getHeight() * 0.95f * mGlobalEnergy;

    for (int i = 0; i < BAR_COUNT; i++) {
        // 距离衰减（用瞬态主频，更陡）
        float distance = Math.abs(i - mCurrentDominantBar);
        float normalizedDistance = distance / (float) BAR_COUNT;

        // 更陡的高斯（sigma小 = 集中 = 只有靠近主频的柱才高）
        float sigma = 0.22f;
        float distanceFactor = (float) Math.exp(-(normalizedDistance * normalizedDistance)
                / (2 * sigma * sigma));

        // 相对能量加权（让瞬态突出的柱额外增高）
        float relativeBoost = Math.min(barMagnitudes[i] / (maxRelativeEnergy + 0.001f), 1.5f);

        mTargetHeights[i] = globalScale * distanceFactor * (0.6f + 0.4f * relativeBoost);
        mTargetHeights[i] = Math.min(mTargetHeights[i], getHeight() * 0.95f);

        mAmplitudeLevels[i] = Math.min(
                mTargetHeights[i] / (getHeight() * 0.95f),
                1.0f
        );
    }

    startAnimation();
}
private void startAnimation() {
    if (mAnimator != null) {
        mAnimator.cancel();
    }
    mAnimator = ValueAnimator.ofFloat(0f, 1f);
    mAnimator.setDuration(40); // 原来80，减半 → 更干脆
    mAnimator.addUpdateListener(animation -> {
        float fraction = animation.getAnimatedFraction();
        // 加大插值系数 → 更快到达目标
        for (int i = 0; i < BAR_COUNT; i++) {
            mBarHeights[i] += (mTargetHeights[i] - mBarHeights[i]) * 0.45f; // 原来用fraction，现在用固定大系数
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
