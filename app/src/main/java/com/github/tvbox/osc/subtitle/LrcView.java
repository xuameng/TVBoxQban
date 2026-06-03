package com.github.tvbox.osc.subtitle;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.animation.ValueAnimator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ✅ 终极版 LrcView
 * - 单语 / 双语自适应
 * - 永不重叠
 * - 垂直居中正确
 * - 高亮一致
 */
public class LrcView extends View {

    /** 歌词行结构 */
    private static class LrcLine {
        long time;
        String mainText = "";
        String translateText = "";
        float mainWidth;
        float translateWidth;
    }

    /* ---------- 基础 ---------- */
    private final List<LrcLine> mLrcLines = new ArrayList<>();
    private final Paint mNormalPaint;
    private final Paint mHighlightPaint;
    private final Paint mTranslatePaint;

    /* ---------- 状态 ---------- */
    private int mCurrentLine = 0;
    private long mCurrentPosition = 0;
    private boolean mShouldShowLyrics = false;
    private boolean mIsInitialPositioning = true;
    private boolean mHasTranslate = false;

    /* ---------- 滚动 ---------- */
    private ValueAnimator mScrollAnimator;
    private float mScrollOffset = 0f;
    private int mScrollDuration = 300;

    /* ---------- 行高（核心） ---------- */
    private float mMainLineHeight;
    private float mTranslateLineHeight;
    private float mTotalLineHeight;

    public LrcView(Context context) {
        this(context, null);
    }

    public LrcView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LrcView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        mNormalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mNormalPaint.setColor(Color.WHITE);
        mNormalPaint.setTextSize(spToPx(18));
        mNormalPaint.setShadowLayer(3, 1, 1, Color.BLACK);

        mHighlightPaint = new Paint(mNormalPaint);
        mHighlightPaint.setColor(Color.YELLOW);
        mHighlightPaint.setFakeBoldText(true);

        mTranslatePaint = new Paint(mNormalPaint);
        mTranslatePaint.setColor(Color.WHITE);
        mTranslatePaint.setAlpha(180);
        mTranslatePaint.setTextSize(mNormalPaint.getTextSize() * 0.65f);
    }

    /* ===================== 对外接口 ===================== */

    /**
     * xuameng
     * 设置普通歌词字体大小（sp）
     */
public void setNormalTextSize(float textSize) {
    mNormalPaint.setTextSize(spToPx(textSize));
    for (LrcLine l : mLrcLines) {
        l.mainWidth = mNormalPaint.measureText(l.mainText);
    }
    invalidate();
}

    /**
     * xuameng
     * 设置高亮歌词字体大小（sp）
     */
public void setHighlightTextSize(float textSize) {
    mHighlightPaint.setTextSize(spToPx(textSize));
    for (LrcLine l : mLrcLines) {
        l.mainWidth = mNormalPaint.measureText(l.mainText);
    }
    invalidate();
}

    /**
     * xuameng
     * 设置普通歌词颜色
     */
    public void setNormalColor(int color) {
        mNormalPaint.setColor(color);
        invalidate();
    }

    /**
     * xuameng
     * 设置高亮歌词颜色
     */
    public void setHighlightColor(int color) {
        mHighlightPaint.setColor(color);
        invalidate();
    }

    public void setLrcText(String lrc) {
        mLrcLines.clear();

        Map<Long, LrcLine> map = new LinkedHashMap<>();
        Pattern p = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{1,3})\\]");

        String[] lines = lrc.split("\n");
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;

            Matcher m = p.matcher(line);
            List<Long> times = new ArrayList<>();
            int lastEnd = 0;

            while (m.find()) {
                int min = Integer.parseInt(m.group(1));
                int sec = Integer.parseInt(m.group(2));
                int ms = Integer.parseInt(m.group(3));
                if (m.group(3).length() == 2) ms *= 10;
                if (m.group(3).length() == 1) ms *= 100;
                times.add((min * 60L + sec) * 1000L + ms);
                lastEnd = m.end();
            }

            String text = line.substring(lastEnd).trim();
            if (text.isEmpty()) continue;

            for (long t : times) {
                LrcLine l = map.get(t);
                if (l == null) {
                    l = new LrcLine();
                    l.time = t;
                    l.mainText = text;
                    map.put(t, l);
                } else {
                    l.translateText = text;
                }
            }
        }

        mLrcLines.addAll(map.values());
        Collections.sort(mLrcLines, (a, b) -> Long.compare(a.time, b.time));

        // ✅ 计算宽度
        for (LrcLine l : mLrcLines) {
            l.mainWidth = mNormalPaint.measureText(l.mainText);
            l.translateWidth = mTranslatePaint.measureText(l.translateText);
        }

        // ✅ 判断是否为双语
        mHasTranslate = false;
        for (LrcLine l : mLrcLines) {
            if (!l.translateText.isEmpty()) {
                mHasTranslate = true;
                break;
            }
        }

        resetInternal();
        invalidate();
    }

    public void updateTime(long position) {
        if (mLrcLines.isEmpty()) return;

        mCurrentPosition = position;

        if (position < 100) {
            mShouldShowLyrics = false;
            invalidate();
            return;
        }

        mShouldShowLyrics = true;

        int target = findLine(position);

        if (mIsInitialPositioning) {
            mCurrentLine = target;
            mScrollOffset = 0;
            mIsInitialPositioning = false;
            invalidate();
            return;
        }

        if (target != mCurrentLine) {
            smoothScrollTo(target);
        } else {
            invalidate();
        }
    }

    public void reset() {
        resetInternal();
        invalidate();
    }

    /* ===================== 核心绘制 ===================== */

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!mShouldShowLyrics || mLrcLines.isEmpty()) return;

        calculateLineHeight();

        int visibleLines = Math.min(mLrcLines.size(), 7);
        float totalHeight = mTotalLineHeight * visibleLines;

        Paint.FontMetrics fm = mNormalPaint.getFontMetrics();
        float startY = (getHeight() - totalHeight) / 2f - fm.ascent;

        int start = Math.max(0, mCurrentLine - 3);

        for (int i = 0; i < visibleLines; i++) {
            int index = start + i;
            if (index < 0 || index >= mLrcLines.size()) continue;

            LrcLine line = mLrcLines.get(index);
float y = startY
        + i * mTotalLineHeight
        - mScrollOffset * mTotalLineHeight; // ✅ 关键

            boolean current = index == mCurrentLine;
            float progress = calcProgress(line, current);

            drawMain(canvas, line, y, progress, current);

            if (!line.translateText.isEmpty()) {
                float ty = y + mMainLineHeight;
                drawTranslate(canvas, line, ty, progress, current);
            }
        }
    }

    /* ===================== 绘制拆分 ===================== */

    private void drawMain(Canvas c, LrcLine l, float y, float p, boolean cur) {
        float x = getWidth() / 2f - l.mainWidth / 2f;

        if (!cur) {
            c.drawText(l.mainText, x, y, mNormalPaint);
            return;
        }

        Paint.FontMetrics fm = mHighlightPaint.getFontMetrics();
        c.drawText(l.mainText, x, y, mNormalPaint);

        c.save();
        c.clipRect(x, y + fm.top, x + l.mainWidth * p, y + fm.bottom);
        c.drawText(l.mainText, x, y, mHighlightPaint);
        c.restore();
    }

    private void drawTranslate(Canvas c, LrcLine l, float y, float p, boolean cur) {
        float x = getWidth() / 2f - l.translateWidth / 2f;

        if (!cur) {
            c.drawText(l.translateText, x, y, mTranslatePaint);
            return;
        }

        Paint hp = new Paint(mHighlightPaint);
        hp.setTextSize(mTranslatePaint.getTextSize());
        hp.setAlpha(230);

        Paint.FontMetrics fm = hp.getFontMetrics();
        c.save();
        c.clipRect(x, y + fm.top, x + l.translateWidth * p, y + fm.bottom);
        c.drawText(l.translateText, x, y, hp);
        c.restore();
    }

    /* ===================== 工具方法 ===================== */

private void calculateLineHeight() {
    Paint.FontMetrics fm = mNormalPaint.getFontMetrics();
    mMainLineHeight = fm.descent - fm.ascent;   // ✅ 真实主歌词行高

    Paint.FontMetrics tfm = mTranslatePaint.getFontMetrics();
    mTranslateLineHeight = tfm.descent - tfm.ascent; // ✅ 真实副歌词行高

    mTotalLineHeight = mHasTranslate
            ? mMainLineHeight + mTranslateLineHeight
            : mMainLineHeight;
}

    private float calcProgress(LrcLine l, boolean cur) {
        if (!cur) return 0f;
        long next = (mLrcLines.indexOf(l) + 1 < mLrcLines.size())
                ? mLrcLines.get(mLrcLines.indexOf(l) + 1).time
                : l.time + 5000;
        long d = next - l.time;
        if (d <= 0 || mCurrentPosition < l.time) return 0f;
        return Math.max(0f, Math.min(1f, (mCurrentPosition - l.time) / (float) d));
    }

    private int findLine(long pos) {
        for (int i = 0; i < mLrcLines.size() - 1; i++) {
            if (pos >= mLrcLines.get(i).time && pos < mLrcLines.get(i + 1).time)
                return i;
        }
        return mLrcLines.size() - 1;
    }

    private void smoothScrollTo(int target) {
        if (mScrollAnimator != null && mScrollAnimator.isRunning()) return;

        mScrollAnimator = ValueAnimator.ofFloat(0, target - mCurrentLine);
        mScrollAnimator.setDuration(mScrollDuration);
        mScrollAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        mScrollAnimator.addUpdateListener(a -> {
            mScrollOffset = (float) a.getAnimatedValue();
            invalidate();
        });
        mScrollAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                mCurrentLine = target;
                mScrollOffset = 0;
            }
        });
        mScrollAnimator.start();
    }

    private void resetInternal() {
        mShouldShowLyrics = false;
        mCurrentLine = 0;
        mScrollOffset = 0;
        mIsInitialPositioning = true;
        if (mScrollAnimator != null) mScrollAnimator.cancel();
    }

    private float spToPx(float sp) {
        return sp * getResources().getDisplayMetrics().scaledDensity;
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
}
