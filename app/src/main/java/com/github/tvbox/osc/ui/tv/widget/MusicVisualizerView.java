package com.github.tvbox.osc.ui.tv.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.animation.ValueAnimator;
/**
xuameng 20261117
音乐可视化视图组件（带振幅颜色渐变）
新增特性：
1，颜色随振幅大小变化
2，保持原有动画平滑性
3，完全兼容原有接口
4，振幅随音量大小变化
5，四种颜色随机变化
6，新增音柱分段效果
7. 三段音柱波浪随当前音柱频段最强音跳动
8. 节奏跟随最强音提升节奏感
*/
public class MusicVisualizerView extends View {

    // ============================================================
    // ★ 基础参数
    // ============================================================

    /**
     * FFT 幅度最大值，用于归一化。
     * 调大 → 柱子整体变矮（对声音更不敏感）
     * 调小 → 柱子整体变高（更容易到顶）
     * 一般不用动，10222 是经验值。
     */
    private static final int MAX_AMPLITUDE = 10222;

    /**
     * 音柱总数量。
     * 调大 → 更密更细，像均衡器
     * 调小 → 更粗更少，像跳动块
     * 推荐范围：16~32，当前 22。
     */
    private static final int BAR_COUNT = 22;

    /**
     * 每次高度变化的动画过渡时长（毫秒）。
     * 调大 → 柱子移动更慢更丝滑（拖尾感强）
     * 调小 → 柱子跳动更快更跟手（反应灵敏）
     * 推荐范围：40~120，当前 60。
     */
    private static final int ANIMATION_DURATION = 60;

    // ============================================================
    // ★ 三频带中心位置（校准后自动更新，初始为默认值）
    // ============================================================

    /** 低频浪（左区）中心柱索引，校准后自动漂移 */
    private int mCenterLow = 4;

    /** 中频浪（中区）中心柱索引，校准后自动漂移 */
    private int mCenterMid = 11;

    /** 高频浪（右区）中心柱索引，校准后自动漂移 */

    private int mCenterHigh = 18;

    // ============================================================
    // ★ 颜色系统
    // ============================================================

    /**
     * 颜色方案数组 [3组][4色]。
     * 4个颜色分别对应：矮柱→中柱→高柱→超高柱。
     * 每组随机生成，每 COLOR_CYCLE_DURATION 切换一组。
     */
    private int[][] colorSchemes = new int[3][4];

    /** 是否显示分段条纹效果 */
    private boolean mShowStripes = true;

    /**
     * 每个音柱最多分成几段条纹。
     * 调大 → 条纹更碎更多
     * 调小 → 条纹更粗更少
     * 推荐范围：8~30，当前 20。
     */
    private int mSegmentCount = 20;

    /**
     * 条纹之间的固定间距（像素）。
     * 调大 → 条纹间隙更大，更明显
     * 调小 → 条纹更紧凑
     * 推荐范围：1.5~5.0，当前 2.5。
     */
    private float mSegmentSpacingFixed = 2.5f;

    /**
     * 颜色方案自动切换间隔（毫秒）。
     * 当前 = 0.1 × 60 × 1000 = 6000ms = 6秒换一组。
     * 调大 → 颜色保持更久
     * 调小 → 颜色换得更频繁
     */
    private static final long COLOR_CYCLE_DURATION = (long) (0.1 * 60 * 1000);

    private int currentSchemeIndex = 0;
    private long lastSwitchTime = 0;

    // ============================================================
    // ★ 峰值检测与衰减
    // ============================================================

    /**
     * 峰值衰减率（每帧衰减比例）。
     * 三个频段共用同一个值。
     * 调大 → 柱子掉得更快，更跟节奏但拖尾短
     * 调小 → 柱子掉得更慢，拖尾长有"余音"感
     * 推荐范围：0.3~0.6，当前 0.48。
     */
    private static final float PEAK_DECAY_LOW = 0.48f;
    private static final float PEAK_DECAY_MID = 0.48f;
    private static final float PEAK_DECAY_HIGH = 0.48f;

    // ============================================================
    // ★ 高斯权重分布
    // ============================================================

    /**
     * 高斯分布的 sigma（控制每个频段对周围柱子的影响范围）。
     * 调大 → 影响范围更广，波浪更宽更平缓
     * 调小 → 影响范围更窄，波浪更尖更集中
     * 推荐范围：0.12~0.30，当前 0.18。
     */
    private static final float DISTANCE_SIGMA = 0.2f;

    // ============================================================
    // ★ 核心高度映射参数（最重要！）
    // ============================================================

    /**
     * 高斯权重的锐化指数。
     * 调大 → 每个频段只影响更近的柱子，三个浪更分离
     * 调小 → 频段之间互相影响更多，波浪更融合
     * 推荐范围：1.0~4.0，当前 2.0。
     */
    private static final float SHARP = 1.5f;

    /**
     * ★★★ 抬升增益 ★★★
     * 把 combinedRatio 乘以这个数再 clamp 到 1.0。
     * 调大 → 所有柱子变高，高值更容易到顶，但差距缩小
     * 调小 → 低值被压得更低，差距拉大，但高值可能到不了顶
     * 推荐范围：1.5~2.5，当前 1.8（甜区）。
     */
    private static final float GAIN = 1.5f;

    /**
     * ★★★ 曲线陡峭度 ★★★
     * pow(shaped, CURVE) 的指数。
     * 调大（接近1.0）→ 曲线更缓，高低差距小，所有柱子高度接近
     * 调小（接近0.2）→ 曲线更陡，低值被狠狠压下去，差距拉大
     * 推荐范围：0.35~0.55，当前 0.4（最佳甜区）。
     * ⚠️ 不要低于 0.25，否则高低差距反而被压缩。
     */
    private static final float CURVE = 0.4f;

    // ============================================================
    // ★ 校准系统
    // ============================================================

    /**
     * 校准采样帧数（每多少帧做一次能量统计）。
     * 调大 → 校准更平滑但响应慢
     * 调小 → 校准更快但可能误判
     * 推荐范围：30~60，当前 45。
     */
    private static final int CALIBRATE_FRAMES = 45;

    /**
     * 校准间隔（毫秒）。
     * 调大 → 中心位置更新慢，波浪更稳定
     * 调小 → 中心位置跟得更快，但可能抖动
     * 推荐范围：2000~8000，当前 4000。
     */
    private static final long CALIBRATE_INTERVAL_MS = 4000;

    // ============================================================
    // ★ 动画插值
    // ============================================================

    /**
     * 每帧动画的插值因子（在 startAnimation 里使用）。
     * 调大（接近1）→ 柱子瞬间到位，几乎无过渡
     * 调小（接近0.3）→ 柱子缓慢追上目标，拖尾感强
     * 推荐范围：0.4~0.8，当前 0.65。
     */

    // ============================================================
    // 以下为运行变量，不需要手动调节
    // ============================================================

    private final Paint mBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] mBarHeights = new float[BAR_COUNT];
    private final float[] mTargetHeights = new float[BAR_COUNT];
    private final float[] mAmplitudeLevels = new float[BAR_COUNT];

    private float mPeakLow = 0.02f;
    private float mPeakMid = 0.02f;
    private float mPeakHigh = 0.02f;

    private final float[] mEnergyAccum = new float[BAR_COUNT];
    private int mCalibrateCount = 0;
    private long mLastCalibrateTime = 0;

    private ValueAnimator mAnimator;

    // ============================================================
    // 构造 & 初始化
    // ============================================================

    public MusicVisualizerView(Context context) { super(context); init(); }
    public MusicVisualizerView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public MusicVisualizerView(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); init(); }

    private void init() {
        mBarPaint.setStyle(Paint.Style.FILL);
        refreshColorSchemes();
    }

    // ============================================================
    // 外部控制方法
    // ============================================================

    public void setShowStripes(boolean show) { mShowStripes = show; postInvalidate(); }
    public void setSegmentCount(int count) { mSegmentCount = Math.max(1, count); postInvalidate(); }
    public void setSegmentSpacingFixed(float spacing) { mSegmentSpacingFixed = Math.max(0f, spacing); postInvalidate(); }

    // ============================================================
    // 核心：FFT 数据更新
    // ============================================================

    public void updateVisualizer(byte[] fft, float volumeLevel) {
        if (fft == null || fft.length < BAR_COUNT * 2 + 2) return;

        if (volumeLevel <= 0.001f) {
            reset();
            return;
        }

        // ---------- 1. 读三频带峰值 ----------
        float peakLow = 0, peakMid = 0, peakHigh = 0;

        for (int i = 2; i <= 10; i++) {
            int idx = i * 2;
            if (idx + 1 >= fft.length) break;
            byte rfk = fft[idx], ifk = fft[idx + 1];
            float mag = (rfk * rfk + ifk * ifk);
            if (mag > peakLow) peakLow = mag;
        }
        for (int i = 11; i <= 20; i++) {
            int idx = i * 2;
            if (idx + 1 >= fft.length) break;
            byte rfk = fft[idx], ifk = fft[idx + 1];
            float mag = (rfk * rfk + ifk * ifk);
            if (mag > peakMid) peakMid = mag;
        }
        for (int i = 21; i <= 32; i++) {
            int idx = i * 2;
            if (idx + 1 >= fft.length) break;
            byte rfk = fft[idx], ifk = fft[idx + 1];
            float mag = (rfk * rfk + ifk * ifk);
            if (mag > peakHigh) peakHigh = mag;
        }

        // ---------- 2. 归一化 ----------
        float normLow  = Math.min(peakLow  / (float) MAX_AMPLITUDE, 1.0f);
        float normMid  = Math.min(peakMid  / (float) MAX_AMPLITUDE, 1.0f);
        float normHigh = Math.min(peakHigh / (float) MAX_AMPLITUDE, 1.0f);

        normLow  = normLow  * (0.1f + 0.9f * volumeLevel);
        normMid  = normMid  * (0.1f + 0.9f * volumeLevel);
        normHigh = normHigh * (0.1f + 0.9f * volumeLevel);

        // ---------- 3. 峰值检测 ----------
        if (normLow > mPeakLow) mPeakLow = normLow;
        if (normMid > mPeakMid) mPeakMid = normMid;
        if (normHigh > mPeakHigh) mPeakHigh = normHigh;

        mPeakLow -= mPeakLow * PEAK_DECAY_LOW;
        mPeakMid -= mPeakMid * PEAK_DECAY_MID;
        mPeakHigh -= mPeakHigh * PEAK_DECAY_HIGH;
        if (mPeakLow < 0.005f) mPeakLow = 0f;
        if (mPeakMid < 0.005f) mPeakMid = 0f;
        if (mPeakHigh < 0.005f) mPeakHigh = 0f;

        // ---------- 4. 校准 ----------
        for (int i = 0; i < BAR_COUNT; i++) {
            int barIndex;
            if (i < BAR_COUNT / 3) {
                barIndex = 2 + i * 2;
            } else {
                barIndex = 2 + (BAR_COUNT / 3) * 2 + (i - BAR_COUNT / 3) * 4;
            }
            if (barIndex < fft.length - 1) {
                byte rfk = fft[barIndex], ifk = fft[barIndex + 1];
                mEnergyAccum[i] += (rfk * rfk + ifk * ifk);
            }
        }
        mCalibrateCount++;
        long now = System.currentTimeMillis();
        if (mCalibrateCount >= CALIBRATE_FRAMES && now - mLastCalibrateTime > CALIBRATE_INTERVAL_MS) {
            autoCalibrate();
            mCalibrateCount = 0;
            mLastCalibrateTime = now;
        }

        // ---------- 5. 计算目标高度 ----------
        float maxH = getHeight();
        if (maxH <= 0) maxH = 100f;

        for (int i = 0; i < BAR_COUNT; i++) {
            float dLow  = Math.abs(i - mCenterLow)  / (float) BAR_COUNT;
            float dMid  = Math.abs(i - mCenterMid)  / (float) BAR_COUNT;
            float dHigh = Math.abs(i - mCenterHigh) / (float) BAR_COUNT;

            float wLow  = (float) Math.exp(-(dLow  * dLow)  / (2 * DISTANCE_SIGMA * DISTANCE_SIGMA));
            float wMid  = (float) Math.exp(-(dMid  * dMid)  / (2 * DISTANCE_SIGMA * DISTANCE_SIGMA));
            float wHigh = (float) Math.exp(-(dHigh * dHigh) / (2 * DISTANCE_SIGMA * DISTANCE_SIGMA));

            wLow  = (float) Math.pow(wLow,  SHARP);
            wMid  = (float) Math.pow(wMid,  SHARP);
            wHigh = (float) Math.pow(wHigh, SHARP);

            float totalWeight = wLow + wMid + wHigh;

            float combinedRatio = 0f;
            if (totalWeight > 0.001f) {
                combinedRatio = (mPeakLow * wLow + mPeakMid * wMid + mPeakHigh * wHigh) / totalWeight;
            }

            // ★ 核心：单段曲线，波峰到顶 + 波谷贴底 + 差距拉大
            float shaped = Math.min(combinedRatio * GAIN, 1.0f);
            float heightRatio = (float) Math.pow(shaped, CURVE);

            mTargetHeights[i] = Math.min(heightRatio * maxH, maxH);
            mAmplitudeLevels[i] = heightRatio;
        }

        startAnimation();
    }

    // ============================================================
    // 校准：自动找三频能量中心
    // ============================================================

    private void autoCalibrate() {
        int third = BAR_COUNT / 3;
        int bestLow = 4, bestMid = 11, bestHigh = 18;
        float maxL = 0, maxM = 0, maxHCal = 0;
        int samples = Math.max(1, mCalibrateCount);

        for (int i = 0; i < third; i++) {
            float avg = mEnergyAccum[i] / samples;
            if (avg > maxL) { maxL = avg; bestLow = i; }
        }
        for (int i = third; i < third * 2; i++) {
            float avg = mEnergyAccum[i] / samples;
            if (avg > maxM) { maxM = avg; bestMid = i; }
        }
        for (int i = third * 2; i < BAR_COUNT; i++) {
            float avg = mEnergyAccum[i] / samples;
            if (avg > maxHCal) { maxHCal = avg; bestHigh = i; }
        }

        mCenterLow  = bestLow;
        mCenterMid  = bestMid;
        mCenterHigh = bestHigh;

        for (int i = 0; i < BAR_COUNT; i++) mEnergyAccum[i] = 0;
    }

    // ============================================================
    // 动画驱动
    // ============================================================

    private void startAnimation() {
        if (mAnimator != null) {
            mAnimator.cancel();
        }
        mAnimator = ValueAnimator.ofFloat(0f, 1f);
        mAnimator.setDuration(ANIMATION_DURATION);
        mAnimator.addUpdateListener(animation -> {
            float lerpFactor = 0.65f;  // ★ 见上方注释说明
            for (int i = 0; i < BAR_COUNT; i++) {
                mBarHeights[i] += (mTargetHeights[i] - mBarHeights[i]) * lerpFactor;
            }
            postInvalidate();
        });
        mAnimator.start();
    }

    // ============================================================
    // 绘制
    // ============================================================

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
            float barHeight = Math.min(mBarHeights[i], height);
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

    // ============================================================
    // 颜色切换计时
    // ============================================================

    private void checkColorCycleSwitch() {
        long now = System.currentTimeMillis();
        if (now - lastSwitchTime > COLOR_CYCLE_DURATION) {
            currentSchemeIndex = (currentSchemeIndex + 1) % colorSchemes.length;
            lastSwitchTime = now;
            refreshColorSchemes();
        }
    }

    // ============================================================
    // 颜色方案生成
    // ============================================================

    private void refreshColorSchemes() {
        for (int i = 0; i < 3; i++) {
            float baseHue = (float) (Math.random() * 360);
            colorSchemes[i] = new int[]{
                    Color.HSVToColor(new float[]{baseHue, 1f, 0.4f}),           // 矮：暗
                    Color.HSVToColor(new float[]{(baseHue + 120) % 360, 1f, 0.7f}), // 中：中亮
                    Color.HSVToColor(new float[]{(baseHue + 240) % 360, 1f, 1.0f}), // 高：亮
                    Color.HSVToColor(new float[]{(baseHue + 60) % 360, 1f, 1.0f})   // 超高：另一个随机色
            };
        }
        postInvalidate();
    }

    // ============================================================
    // 颜色映射：根据 amplitude 三段渐变
    // ============================================================

    private int getDynamicColor(float amplitude, int[] currentScheme) {
        amplitude = Math.max(0.0f, Math.min(1.0f, amplitude));
        if (amplitude < 0.33f) {
            // 矮柱：颜色0 → 颜色1
            return interpolateColor(amplitude / 0.33f, currentScheme[0], currentScheme[1]);
        } else if (amplitude < 0.66f) {
            // 中柱：颜色1 → 颜色2
            return interpolateColor((amplitude - 0.33f) / 0.33f, currentScheme[1], currentScheme[2]);
        } else {
            // 高柱：颜色2 → 颜色3（第4个随机色）
            return interpolateColor((amplitude - 0.66f) / 0.34f, currentScheme[2], currentScheme[3]);
        }
    }

    // ============================================================
    // 颜色插值
    // ============================================================

    private int interpolateColor(float ratio, int startColor, int endColor) {
        ratio = Math.max(0f, Math.min(1f, ratio));
        int alpha = (int) (Color.alpha(startColor) + (Color.alpha(endColor) - Color.alpha(startColor)) * ratio);
        int red = (int) (Color.red(startColor) + (Color.red(endColor) - Color.red(startColor)) * ratio);
        int green = (int) (Color.green(startColor) + (Color.green(endColor) - Color.green(startColor)) * ratio);
        int blue = (int) (Color.blue(startColor) + (Color.blue(endColor) - Color.blue(startColor)) * ratio);
        return Color.argb(alpha, red, green, blue);
    }

    // ============================================================
    // 重置 & 释放
    // ============================================================

    public void reset() {
        for (int i = 0; i < BAR_COUNT; i++) {
            mBarHeights[i] = 0;
            mTargetHeights[i] = 0;
            mAmplitudeLevels[i] = 0;
        }
        mPeakLow = mPeakMid = mPeakHigh = 0.02f;
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
