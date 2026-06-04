package com.github.tvbox.osc.subtitle;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * xuameng
 * LRC歌词显示控件
 * 支持卡拉OK效果的歌词同步显示
 * 新增：双语歌词支持
 */
public class LrcView extends View {

    /**
     * LRC歌词行数据结构
     */
    private static class LrcLine {
        long time;           // 时间戳（毫秒）
        String mainText;     // 主歌词文本
        String subText;      // 副歌词文本 (可为空)
        float mainWidth;     // 主文本宽度
        float subWidth;      // 副文本宽度
        boolean isDual;      // 是否为双语行
    }

    private List<LrcLine> mLrcLines = new ArrayList<>();
    private Paint mNormalPaint, mHighlightPaint;
    private Paint mSubNormalPaint, mSubHighlightPaint; // 新增：副歌词画笔

    private int mCurrentLine = 0;
    private long mCurrentPosition = 0;

    // 平滑滚动相关变量
    private float mScrollOffset = 0f;
    private ValueAnimator mScrollAnimator;
    private int mScrollDuration = 300;

    // 控制是否显示歌词的标志
    private boolean mShouldShowLyrics = false;
    private static final long MIN_POSITION_TO_SHOW = 100;

    // 标记是否正在初始定位
    private boolean mIsInitialPositioning = true;

    // 最大滚动距离（行数）
    private static final int MAX_SCROLL_DISTANCE = 1;

    public LrcView(Context context) {
        super(context);
        init();
    }

    public LrcView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LrcView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    /**
     * 初始化画笔
     */
    private void init() {
        // 主歌词画笔
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

        // 副歌词画笔 (默认初始化，实际大小和颜色会在 set 时计算)
        mSubNormalPaint = new Paint();
        mSubNormalPaint.setAntiAlias(true);
        mSubNormalPaint.setTextSize(24); // 默认大小
        mSubNormalPaint.setColor(Color.parseColor("#AAAAAA")); // 默认暗灰色
        mSubNormalPaint.setShadowLayer(2, 1, 1, Color.BLACK);

        mSubHighlightPaint = new Paint();
        mSubHighlightPaint.setAntiAlias(true);
        mSubHighlightPaint.setTextSize(24);
        mSubHighlightPaint.setColor(Color.parseColor("#DDDD00")); // 默认暗黄色
        mSubHighlightPaint.setShadowLayer(2, 1, 1, Color.BLACK);
        mSubHighlightPaint.setFakeBoldText(true);
    }

    /**
     * 设置滚动动画时长
     */
    public void setScrollDuration(int duration) {
        mScrollDuration = duration;
    }

    /**
     * 设置普通文本大小 (主歌词)
     * 副歌词大小会自动跟随
     */
    public void setNormalTextSize(float textSize) {
        float pxSize = spToPx(getContext(), textSize);
        mNormalPaint.setTextSize(pxSize);
        // 副歌词大小为主歌词的 2/3
        float subSize = pxSize * 0.66f;
        mSubNormalPaint.setTextSize(subSize);
        mSubHighlightPaint.setTextSize(subSize);
        recalculateLineWidths();
        invalidate();
    }

    /**
     * 设置高亮文本大小 (主歌词)
     * 副歌词大小会自动跟随
     */
    public void setHighlightTextUpSize(float textSize) {
        float pxSize = spToPx(getContext(), textSize);
        mHighlightPaint.setTextSize(pxSize);
        // 副歌词大小为主歌词的 2/3
        float subSize = pxSize * 0.66f;
        mSubHighlightPaint.setTextSize(subSize);
        recalculateLineWidths();
        invalidate();
    }

    /**
     * 设置普通文本颜色 (主歌词)
     * 副歌词颜色会自动变暗
     */
    public void setNormalColor(int color) {
        mNormalPaint.setColor(color);
        // 副歌词变暗 (简单处理：取主颜色的 60% 亮度)
        mSubNormalPaint.setColor(tint_color(color, 0.6f));
        invalidate();
    }

    /**
     * 设置高亮文本颜色 (主歌词)
     * 副歌词高亮颜色会自动变暗
     */
    public void setHighlightColor(int color) {
        mHighlightPaint.setColor(color);
        // 副歌词高亮变暗
        mSubHighlightPaint.setColor(tint_color(color, 0.7f));
        invalidate();
    }

    /**
     * 辅助方法：简单的颜色变暗处理
     */
    private int tint_color(int color, float factor) {
        int alpha = Color.alpha(color);
        int red = (int) (Color.red(color) * factor);
        int green = (int) (Color.green(color) * factor);
        int blue = (int) (Color.blue(color) * factor);
        return Color.argb(alpha, Math.min(red, 255), Math.min(green, 255), Math.min(blue, 255));
    }

    /**
     * 重新计算所有歌词行的宽度
     */
    private void recalculateLineWidths() {
        for (LrcLine line : mLrcLines) {
            line.mainWidth = mNormalPaint.measureText(line.mainText);
            if (line.isDual) {
                line.subWidth = mSubNormalPaint.measureText(line.subText);
            }
        }
    }

    /**
     * 解析LRC格式歌词
     * 增强逻辑以支持双语
     */
    public void setLrcText(String lrcContent) {
        mLrcLines.clear();
        String[] lines = lrcContent.split("\n");
        Pattern pattern = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{1,3})\\]");

        // 临时存储解析出的所有行（包括空行和元数据）
        List<LrcLine> tempLines = new ArrayList<>();

        for (String line : lines) {
            if (line.trim().isEmpty()) continue;
            
            Matcher matcher = pattern.matcher(line);
            List<Long> times = new ArrayList<>();
            String text = "";
            int lastEnd = 0;

            while (matcher.find()) {
                int min = Integer.parseInt(matcher.group(1));
                int sec = Integer.parseInt(matcher.group(2));
                String msStr = matcher.group(3);
                long ms;
                if (msStr.length() == 2) ms = Integer.parseInt(msStr) * 10L;
                else if (msStr.length() == 1) ms = Integer.parseInt(msStr) * 100L;
                else ms = Integer.parseInt(msStr);
                times.add((min * 60 + sec) * 1000L + ms);
                lastEnd = matcher.end();
            }

            text = line.substring(lastEnd).trim();
            if (!text.isEmpty()) {
                for (Long time : times) {
                    LrcLine lrcLine = new LrcLine();
                    lrcLine.time = time;
                    lrcLine.mainText = text;
                    lrcLine.subText = "";
                    lrcLine.isDual = false;
                    lrcLine.mainWidth = mNormalPaint.measureText(text);
                    lrcLine.subWidth = 0;
                    tempLines.add(lrcLine);
                }
            }
        }

        // 排序
        Collections.sort(tempLines, (a, b) -> Long.compare(a.time, b.time));
        removeDuplicateLines(tempLines);

        // 智能匹配双语
        // 策略：如果当前行和下一行时间接近(小于2秒)，且文本内容看起来像互为翻译，则合并
        for (int i = 0; i < tempLines.size() - 1; i++) {
            LrcLine current = tempLines.get(i);
            LrcLine next = tempLines.get(i + 1);
            
            // 时间间隔小于2秒
            if (next.time - current.time < 2000) {
                // 简单判断：一行包含中文，另一行包含英文/拼音特征
                boolean currentHasEn = current.mainText.matches(".*[a-zA-Z].*");
                boolean currentHasCn = current.mainText.matches(".*[\\u4e00-\\u9fa5].*");
                boolean nextHasEn = next.mainText.matches(".*[a-zA-Z].*");
                boolean nextHasCn = next.mainText.matches(".*[\\u4e00-\\u9fa5].*");

                // 如果一行主要是中文，另一行主要是非中文(英文/拼音)，则认为是双语
                if ((currentHasCn && nextHasEn && !nextHasCn) || 
                    (nextHasCn && currentHasEn && !currentHasCn)) {
                    
                    // 将中文作为主歌词，英文作为副歌词
                    if (currentHasCn) {
                        current.mainText = current.mainText;
                        current.subText = next.mainText;
                    } else {
                        current.mainText = next.mainText;
                        current.subText = current.mainText; // 这里逻辑修正
                        // 实际上应该交换
                        String temp = current.mainText;
                        current.mainText = next.mainText;
                        current.subText = temp;
                    }
                    current.isDual = true;
                    // 移除下一行，将其内容合并到当前行
                    tempLines.remove(i + 1);
                    // 更新宽度
                    current.mainWidth = mNormalPaint.measureText(current.mainText);
                    current.subWidth = mSubNormalPaint.measureText(current.subText);
                    i--; // 重新检查当前位置
                    continue;
                }
            }
            // 如果不满足双语条件，则保持单行
            LrcLine single = new LrcLine();
            single.time = current.time;
            single.mainText = current.mainText;
            single.subText = "";
            single.isDual = false;
            single.mainWidth = mNormalPaint.measureText(single.mainText);
            mLrcLines.add(single);
        }

        // 如果上面的循环没有覆盖到最后一个元素（或者逻辑有遗漏），补充剩余的
        // 这里简化处理，直接将 tempLines 赋值给 mLrcLines (在去重后)
        // 重新构建 mLrcLines 以确保逻辑清晰
        mLrcLines.clear();
        mLrcLines.addAll(tempLines);

        // 重置状态
        reset();
        invalidate();
    }

    /**
     * 移除重复的歌词行
     */
    private void removeDuplicateLines(List<LrcLine> lines) {
        if (lines.size() <= 1) return;
        List<LrcLine> unique = new ArrayList<>();
        for (LrcLine line : lines) {
            boolean dup = false;
            for (LrcLine ex : unique) {
                if (ex.time == line.time && ex.mainText.equals(line.mainText)) {
                    dup = true;
                    break;
                }
            }
            if (!dup) unique.add(line);
        }
        lines.clear();
        lines.addAll(unique);
    }

    /**
     * 平滑滚动到指定行
     */
    private void smoothScrollTo(int targetLine) {
        if (mScrollAnimator != null && mScrollAnimator.isRunning()) {
            return;
        }
        int lineDiff = targetLine - mCurrentLine;
        if (lineDiff == 0) return;

        mScrollAnimator = ValueAnimator.ofFloat(0f, (float) lineDiff);
        mScrollAnimator.setDuration(mScrollDuration);
        mScrollAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        mScrollAnimator.addUpdateListener(animation -> {
            mScrollOffset = (float) animation.getAnimatedValue();
            invalidate();
        });
        mScrollAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mCurrentLine = targetLine;
                mScrollOffset = 0f;
            }
            @Override
            public void onAnimationCancel(Animator animation) {
                mCurrentLine = targetLine;
                mScrollOffset = 0f;
            }
        });
        mScrollAnimator.start();
    }

    /**
     * 更新播放进度
     */
    public void updateTime(long position) {
        if (mLrcLines.isEmpty()) return;

        if (position < MIN_POSITION_TO_SHOW) {
            mShouldShowLyrics = false;
            mCurrentLine = 0;
            mScrollOffset = 0f;
            invalidate();
            return;
        }

        if (!mShouldShowLyrics) {
            mShouldShowLyrics = true;
        }
        mCurrentPosition = position;

        int targetLine = 0;
        if (position < mLrcLines.get(0).time) {
            targetLine = 0;
        } else {
            for (int i = 0; i < mLrcLines.size(); i++) {
                if (i == mLrcLines.size() - 1 || (position >= mLrcLines.get(i).time && position < mLrcLines.get(i + 1).time)) {
                    targetLine = i;
                    break;
                }
            }
        }

        // 初始定位
        if (mIsInitialPositioning) {
            mCurrentLine = targetLine;
            mScrollOffset = 0f;
            mIsInitialPositioning = false;
            invalidate();
            return;
        }

        // 滚动逻辑
        if (targetLine != mCurrentLine) {
            int lineDiff = targetLine - mCurrentLine;
            int lineDistance = Math.abs(lineDiff);

            if (lineDistance > MAX_SCROLL_DISTANCE) {
                if (mScrollAnimator != null && mScrollAnimator.isRunning()) mScrollAnimator.cancel();
                mCurrentLine = targetLine;
                mScrollOffset = 0f;
                invalidate();
            } else {
                boolean isForward = lineDiff > 0;
                if (isForward && targetLine > 3) {
                    smoothScrollTo(targetLine);
                } else {
                    if (mScrollAnimator != null && mScrollAnimator.isRunning()) mScrollAnimator.cancel();
                    mCurrentLine = targetLine;
                    mScrollOffset = 0f;
                    invalidate();
                }
            }
        } else {
            invalidate();
        }
    }

    /**
     * 重置显示状态
     */
    public void reset() {
        mShouldShowLyrics = false;
        mCurrentPosition = 0;
        mCurrentLine = 0;
        mScrollOffset = 0f;
        mIsInitialPositioning = true;
        invalidate();
    }

    /**
     * 绘制逻辑
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (!mShouldShowLyrics) return;
        if (mLrcLines.isEmpty()) return;

        // 计算行高
        float mainLineHeight = mNormalPaint.getTextSize() * 1.5f;
        float subLineHeight = mSubNormalPaint.getTextSize() * 1.3f; // 副歌词行高稍小

        // 可见行数计算 (考虑双语会占用更多空间)
        // 这里简单处理：双语算作1.5行的高度
        int visibleLines = 7; 
        float totalHeight = 0;
        // 粗略计算总高度
        for (int i = 0; i < visibleLines && (mCurrentLine + i) < mLrcLines.size(); i++) {
            LrcLine line = mLrcLines.get(mCurrentLine + i);
            totalHeight += line.isDual ? (mainLineHeight + subLineHeight) * 0.7f : mainLineHeight;
        }

        // 起始Y位置
        float startY = (getHeight() - totalHeight) / 2;
        if (startY < 0) startY = 0;

        // 绘制可见的行
        int index = 0;
        int lineNum = mCurrentLine;
        float currentY = startY;

        while (index < visibleLines && lineNum < mLrcLines.size()) {
            LrcLine line = mLrcLines.get(lineNum);
            
            // 计算当前行的垂直偏移
            float offsetY = 0;
            if (lineNum == mCurrentLine) {
                // 当前行应用滚动偏移
                // 如果是双语，偏移量需要根据总高度计算
                float lineHeight = line.isDual ? (mainLineHeight + subLineHeight) * 0.7f : mainLineHeight;
                offsetY = mScrollOffset * lineHeight;
            }

            float y = currentY - offsetY;

            if (line.isDual) {
                // 绘制双语
                // 主歌词
                float mainX = getWidth() / 2 - line.mainWidth / 2;
                if (lineNum == mCurrentLine) {
                    // 高亮逻辑
                    long duration = (lineNum + 1 < mLrcLines.size()) ? mLrcLines.get(lineNum + 1).time - line.time : 5000;
                    float progress = (float) (mCurrentPosition - line.time) / duration;
                    progress = Math.max(0, Math.min(1, progress));
                    
                    // 绘制主歌词背景
                    canvas.drawText(line.mainText, mainX, y, mNormalPaint);
                    // 高亮裁剪
                    float highlightWidth = line.mainWidth * progress;
                    canvas.save();
                    canvas.clipRect(mainX, y + mHighlightPaint.getFontMetrics().top, mainX + highlightWidth, y + mHighlightPaint.getFontMetrics().bottom);
                    canvas.drawText(line.mainText, mainX, y, mHighlightPaint);
                    canvas.restore();
                } else {
                    canvas.drawText(line.mainText, mainX, y, mNormalPaint);
                }

                // 副歌词 (位置在主歌词下方)
                float subX = getWidth() / 2 - line.subWidth / 2;
                float subY = y + mainLineHeight * 0.8f; // 调整副歌词垂直位置
                if (lineNum == mCurrentLine) {
                    // 副歌词高亮跟随主歌词
                    canvas.drawText(line.subText, subX, subY, mSubNormalPaint);
                    float highlightWidth = line.subWidth * progress;
                    canvas.save();
                    canvas.clipRect(subX, subY + mSubHighlightPaint.getFontMetrics().top, subX + highlightWidth, subY + mSubHighlightPaint.getFontMetrics().bottom);
                    canvas.drawText(line.subText, subX, subY, mSubHighlightPaint);
                    canvas.restore();
                } else {
                    canvas.drawText(line.subText, subX, subY, mSubNormalPaint);
                }

                currentY += (mainLineHeight + subLineHeight) * 0.7f; // 双语行间距
            } else {
                // 绘制单语
                float x = getWidth() / 2 - line.mainWidth / 2;
                if (lineNum == mCurrentLine) {
                    long duration = (lineNum + 1 < mLrcLines.size()) ? mLrcLines.get(lineNum + 1).time - line.time : 5000;
                    float progress = (float) (mCurrentPosition - line.time) / duration;
                    progress = Math.max(0, Math.min(1, progress));
                    
                    canvas.drawText(line.mainText, x, y, mNormalPaint);
                    float highlightWidth = line.mainWidth * progress;
                    canvas.save();
                    canvas.clipRect(x, y + mHighlightPaint.getFontMetrics().top, x + highlightWidth, y + mHighlightPaint.getFontMetrics().bottom);
                    canvas.drawText(line.mainText, x, y, mHighlightPaint);
                    canvas.restore();
                } else {
                    canvas.drawText(line.mainText, x, y, mNormalPaint);
                }
                currentY += mainLineHeight; // 单语行间距
            }

            index++;
            lineNum++;
        }
    }

    /**
     * 将 sp 值转换为 px 值
     */
    private float spToPx(Context context, float sp) {
        return sp * context.getResources().getDisplayMetrics().scaledDensity;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mScrollAnimator != null) {
            mScrollAnimator.cancel();
            mScrollAnimator = null;
        }
    }
}
