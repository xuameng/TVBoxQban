package com.github.tvbox.osc.subtitle;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.animation.ValueAnimator;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.content.res.Resources;
import android.util.DisplayMetrics;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.lang.Math;

/**
 * xuameng
 * LRC歌词显示控件
 * 支持卡拉OK效果的歌词同步显示
 * 新增平滑滚动功能
 * 新增：未获取到进度或进度小于0.1秒时不显示歌词
 * 新增：初始位不显示滚动动画
 * 新增：不是相邻行不不显示滚动动画
 * 新增：播放进度到当前行的上行或多行不显示滚动动画
 */
public class LrcView extends View {

    /**
     * LRC歌词行数据结构
     */
private static class LrcLine {
    long time;                 // 时间戳（毫秒）
    String mainText;           // 主歌词（高亮）
    String translateText = ""; // 翻译（始终灰色）
    float mainWidth;  //文本宽度（用于绘制）
    float translateWidth;  //翻译宽度
}

    private List<LrcLine> mLrcLines = new ArrayList<>();
    private Paint mNormalPaint, mHighlightPaint;
    private int mCurrentLine = 0;
    private long mCurrentPosition = 0;

    // 平滑滚动相关变量
    private float mScrollOffset = 0f; // 当前滚动偏移量（行数）
    private ValueAnimator mScrollAnimator; // 滚动动画
    private int mScrollDuration = 300; // 滚动动画时长（毫秒）

    // 新增：控制是否显示歌词的标志
    private boolean mShouldShowLyrics = false;
    private static final long MIN_POSITION_TO_SHOW = 100; // 0.1秒，单位：毫秒
    // 标记是否正在初始定位
    private boolean mIsInitialPositioning = true;
    // 最大滚动距离（行数）
    private static final int MAX_SCROLL_DISTANCE = 1;
	private Paint mTranslatePaint;



    public LrcView(Context context) {
        super(context);
        init();
    }

    public LrcView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LrcView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);  // 修复这里
        init();
    }

    /**
     * 初始化画笔
     */
    private void init() {
        mNormalPaint = new Paint();
        mNormalPaint.setAntiAlias(true);
        mNormalPaint.setTextSize(36);
        mNormalPaint.setColor(Color.WHITE);
        mNormalPaint.setShadowLayer(3, 1, 1, Color.BLACK);

        mHighlightPaint = new Paint();
        mHighlightPaint.setAntiAlias(true);
        mHighlightPaint.setTextSize(36);
        mHighlightPaint.setColor(Color.YELLOW);
        mHighlightPaint.setShadowLayer(3, 1, 1, Color.BLACK);
        mHighlightPaint.setFakeBoldText(true);
    }

    /**
     * 设置滚动动画时长
     *
     * @param duration 动画时长（毫秒）
     */
    public void setScrollDuration(int duration) {
        mScrollDuration = duration;
    }

    /**
     * 设置普通文本大小
     *
     * @param textSize 文本大小
     */
    public void setNormalTextSize(float textSize) {
        // 将 sp 转换为 px 以便与字幕的字体大小一致
        float pxSize = spToPx(getContext(), textSize);
        mNormalPaint.setTextSize(pxSize);
        // 重新计算所有歌词行的宽度
        recalculateMainWidths();
        invalidate();
    }

    /**
     * 设置高亮文本大小
     *
     * @param textSize 文本大小
     */
    public void setHighlightTextSize(float textSize) {
        // 将 sp 转换为 px 以便与字幕的字体大小一致
        float pxSize = spToPx(getContext(), textSize);
        mHighlightPaint.setTextSize(pxSize);
        // 重新计算所有歌词行的宽度
        recalculateMainWidths();
        invalidate();
    }

    /**
     * 重新计算所有歌词行的宽度
     */
private void recalculateMainWidths() {
    for (LrcLine line : mLrcLines) {
        line.mainWidth = mNormalPaint.measureText(line.mainText);
    }
}
    /**
     * 设置普通文本颜色
     *
     * @param color 颜色值
     */
    public void setNormalColor(int color) {
        mNormalPaint.setColor(color);
        invalidate();
    }

    /**
     * 设置高亮文本颜色
     *
     * @param color 颜色值
     */
    public void setHighlightColor(int color) {
        mHighlightPaint.setColor(color);
        invalidate();
    }

    /**
     * 解析LRC格式歌词
     *
     * @param lrcContent LRC格式歌词内容
     */
public void setLrcText(String lrcContent) {
    mLrcLines.clear();

    String[] lines = lrcContent.split("\n");
    Pattern pattern = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{1,3})\\]");

    // 临时 Map，用来合并双语
    java.util.Map<Long, LrcLine> lineMap = new java.util.LinkedHashMap<>();

    for (String line : lines) {
        if (line.trim().isEmpty()) continue;

        Matcher matcher = pattern.matcher(line);
        List<Long> times = new ArrayList<>();
        int lastEnd = 0;

        while (matcher.find()) {
            int min = Integer.parseInt(matcher.group(1));
            int sec = Integer.parseInt(matcher.group(2));
            String msStr = matcher.group(3);
            long ms;

            if (msStr.length() == 2) {
                ms = Integer.parseInt(msStr) * 10L;
            } else if (msStr.length() == 1) {
                ms = Integer.parseInt(msStr) * 100L;
            } else {
                ms = Integer.parseInt(msStr);
            }

            times.add((min * 60 + sec) * 1000L + ms);
            lastEnd = matcher.end();
        }

        String text = line.substring(lastEnd).trim();
        if (text.isEmpty()) continue;

        for (Long time : times) {
            LrcLine lrcLine = lineMap.get(time);
            if (lrcLine == null) {
                // 第一次出现 → 主歌词
                lrcLine = new LrcLine();
                lrcLine.time = time;
                lrcLine.mainText = text;
                lrcLine.translateText = "";
                lineMap.put(time, lrcLine);
            } else {
                // 第二次出现 → 翻译
                lrcLine.translateText = text;
            }
        }
    }

    mLrcLines.addAll(lineMap.values());

    // 计算宽度
    for (LrcLine line : mLrcLines) {
        line.mainWidth = mNormalPaint.measureText(line.mainText);
        line.translateWidth = mNormalPaint.measureText(line.translateText);
    }

    Collections.sort(mLrcLines, (a, b) -> Long.compare(a.time, b.time));

    // 重置状态
    mShouldShowLyrics = false;
    mCurrentLine = 0;
    mScrollOffset = 0f;
    mCurrentPosition = 0;
    mIsInitialPositioning = true;

    if (mScrollAnimator != null && mScrollAnimator.isRunning()) {
        mScrollAnimator.cancel();
    }

    invalidate();
}


    /**
     * 平滑滚动到指定行
     *
     * @param targetLine 目标行索引
     */
    private void smoothScrollTo(int targetLine) {
        if (mScrollAnimator != null && mScrollAnimator.isRunning()) {
            return; // 无需滚动
        }

        // 计算滚动距离（行数差）
        int lineDiff = targetLine - mCurrentLine;
        if (lineDiff == 0) {
            return; // 无需滚动
        }

        // 设置动画
        mScrollAnimator = ValueAnimator.ofFloat(0f, (float) lineDiff);
        mScrollAnimator.setDuration(mScrollDuration);
        mScrollAnimator.setInterpolator(new AccelerateDecelerateInterpolator());  //加速减速插值器
    //    mScrollAnimator.setInterpolator(new LinearInterpolator()); // 改为线性插值器
    //    mScrollAnimator.setInterpolator(new DecelerateInterpolator(1.5f));  //减速插值器

        mScrollAnimator.addUpdateListener(animation -> {
            mScrollOffset = (float) animation.getAnimatedValue();
            invalidate();
        });

        mScrollAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // 动画结束后更新当前行
                mCurrentLine = targetLine;
                mScrollOffset = 0f;
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                // 动画取消时也更新当前行
                mCurrentLine = targetLine;
                mScrollOffset = 0f;
            }

        });

        mScrollAnimator.start();
    }

    /**
     * 更新播放进度
     *
     * @param position 当前播放时间（毫秒）
     */
    public void updateTime(long position) {
        if (mLrcLines.isEmpty()) {
            return;
        }

        // 检查是否达到最小显示位置
        if (position < MIN_POSITION_TO_SHOW) {
            // 进度小于1秒，不显示歌词，但保持在第一行
            mShouldShowLyrics = false;
            mCurrentLine = 0; // 确保强制重置到第一行
            mScrollOffset = 0f; // 重置滚动偏移
            invalidate();
            return;
        }

        // 达到最小显示位置，开始显示歌词
        if (!mShouldShowLyrics) {
            mShouldShowLyrics = true;
        }

        mCurrentPosition = position;

        // 查找当前应该显示的行
        int targetLine = 0;

        // 如果当前时间比第一行还早，保持在第一行
        if (position < mLrcLines.get(0).time) {
            targetLine = 0;
        } else {
            // 否则找到合适的时间点
            for (int i = 0; i < mLrcLines.size(); i++) {
                if (i == mLrcLines.size() - 1 ||
                        position >= mLrcLines.get(i).time &&
                                position < mLrcLines.get(i + 1).time) {
                    targetLine = i;
                    break;
                }
            }
        }

        // 关键修改：处理初始定位
        if (mIsInitialPositioning) {
            // 初始定位阶段，直接跳转到目标行，不执行滚动动画
            mCurrentLine = targetLine;
            mScrollOffset = 0f;
            mIsInitialPositioning = false; // 定位完成，退出初始状态
            invalidate();
            return;
        }

        // 正常播放中的滚动逻辑
        if (targetLine != mCurrentLine) {
            // 计算目标行与当前行的距离和方向
            int lineDiff = targetLine - mCurrentLine;
            int lineDistance = Math.abs(lineDiff);
    
            // 判断滚动方向：正数表示向前（下一行），负数表示向后（上一行）
            boolean isForward = lineDiff > 0;
    
            // 如果距离超过阈值（不是相邻行），直接跳转而不滚动
            if (lineDistance > MAX_SCROLL_DISTANCE) {
                if (mScrollAnimator != null && mScrollAnimator.isRunning()) {
                    mScrollAnimator.cancel();
                }
                // 直接跳转逻辑
                mCurrentLine = targetLine;
                mScrollOffset = 0f;
                invalidate();
            } else {
                // 相邻行：只有向前滚动（到下一行）才执行平滑滚动
                if (isForward && targetLine > 3) {
                    smoothScrollTo(targetLine);
                } else {
                    // 向后滚动（到上一行）或前三行：直接跳转
                    if (mScrollAnimator != null && mScrollAnimator.isRunning()) {
                        mScrollAnimator.cancel();
                    }
                    mCurrentLine = targetLine;
                    mScrollOffset = 0f;
                    invalidate();
                }
            }
        } else {
            // 行数不变，只更新进度
            invalidate();
        }
    }

    /**
     * 新增：手动设置是否显示歌词
     *
     * @param show 是否显示歌词
     */
    public void setShowLyrics(boolean show) {
        if (mShouldShowLyrics != show) {
            mShouldShowLyrics = show;
            invalidate();
        }
    }

    /**
     * 新增：获取当前是否显示歌词
     *
     * @return 是否显示歌词
     */
    public boolean isShowingLyrics() {
        return mShouldShowLyrics;
    }

    /**
     * 新增：重置显示状态
     */
    public void reset() {
        mShouldShowLyrics = false;
        mCurrentPosition = 0;
        mCurrentLine = 0;
        mScrollOffset = 0f;
        mIsInitialPositioning = true; // 新增：重置初始定位状态
        invalidate();
    }

    /**
     * 绘制卡拉OK效果
     */
@Override
protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
ensureTranslatePaint();
    if (!mShouldShowLyrics) {
        String hint = "";
        float textWidth = mNormalPaint.measureText(hint);
        canvas.drawText(
                hint,
                getWidth() / 2f - textWidth / 2f,
                getHeight() / 2f,
                mNormalPaint
        );
        return;
    }

    if (mLrcLines.isEmpty()) return;

    float lineHeight = mNormalPaint.getTextSize() * 1.8f;
    int visibleLines = Math.min(mLrcLines.size(), 7);
    float totalHeight = lineHeight * visibleLines;

    float scrollOffsetPixels = Math.round(mScrollOffset * lineHeight);
    float startY = (getHeight() - totalHeight) / 2f
            + mNormalPaint.getTextSize()
            - scrollOffsetPixels;

    int startLineIndex = Math.max(0, mCurrentLine - 3);
    int endLineIndex = Math.min(mLrcLines.size() - 1, mCurrentLine + 3);

    for (int i = 0; i < visibleLines; i++) {
        int index = startLineIndex + i;
        if (index < 0 || index >= mLrcLines.size()) continue;

        LrcLine line = mLrcLines.get(index);
        float y = startY + i * lineHeight;

        boolean isCurrent = index == mCurrentLine;
float progress = 0f; 
        // ===== 主歌词 =====
        if (isCurrent) {
            float progress = 0f;
            long nextTime = (index + 1 < mLrcLines.size())
                    ? mLrcLines.get(index + 1).time
                    : line.time + 5000;
            long duration = nextTime - line.time;

            if (duration > 0 && mCurrentPosition >= line.time) {
                progress = (float) (mCurrentPosition - line.time) / duration;
            }
            progress = Math.max(0, Math.min(1, progress));

            Paint.FontMetrics fm = mHighlightPaint.getFontMetrics();
            float top = y + fm.top;
            float bottom = y + fm.bottom;

            float x = getWidth() / 2f - line.mainWidth / 2f;

            // 背景
            canvas.drawText(line.mainText, x, y, mNormalPaint);

            // 高亮裁剪
            canvas.save();
            canvas.clipRect(
                    x,
                    top,
                    x + line.mainWidth * progress,
                    bottom
            );
            canvas.drawText(line.mainText, x, y, mHighlightPaint);
            canvas.restore();

        } else {
            canvas.drawText(
                    line.mainText,
                    getWidth() / 2f - line.mainWidth / 2f,
                    y,
                    mNormalPaint
            );
        }

// ===== 翻译歌词（同步高亮）=====
if (!line.translateText.isEmpty()) {
    float translateY = y + lineHeight * 0.85f;

	Paint translatePaint = mTranslatePaint;


    float tx = getWidth() / 2f - line.translateWidth / 2f;

    // 背景（未高亮）
    canvas.drawText(line.translateText, tx, translateY, translatePaint);

    if (isCurrent) {
        // 副歌词同步高亮
        canvas.save();
        canvas.clipRect(
                tx,
                translateY + translatePaint.getFontMetrics().top,
                tx + line.translateWidth * progress,
                translateY + translatePaint.getFontMetrics().bottom
        );
        canvas.drawText(line.translateText, tx, translateY, mHighlightPaint);
        canvas.restore();
    }
}
    }
}

    /**
     * 将 sp 值转换为 px 值  以便与字幕的字体大小一致
     */
    private float spToPx(Context context, float sp) {
        return sp * context.getResources().getDisplayMetrics().scaledDensity;
    }

    /**
     * 清理资源
     */
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mScrollAnimator != null) {
            mScrollAnimator.cancel();
            mScrollAnimator = null;
        }
    }

private void ensureTranslatePaint() {
    if (mTranslatePaint == null) {
        mTranslatePaint = new Paint(mNormalPaint);
        mTranslatePaint.setColor(Color.WHITE);
        mTranslatePaint.setAlpha(140);
    }
    mTranslatePaint.setTextSize(mNormalPaint.getTextSize() * 2f / 3f);
}
}
