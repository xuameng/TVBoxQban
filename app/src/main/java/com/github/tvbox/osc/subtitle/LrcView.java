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

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**

LRC歌词显示控件

支持卡拉OK效果的歌词同步显示

新增平滑滚动功能
*/
public class LrcView extends View {

/**

LRC歌词行数据结构
*/
private static class LrcLine {
long time; // 时间戳（毫秒）
String text; // 歌词文本
float width; // 文本宽度（用于绘制）
}
private List<LrcLine> mLrcLines = new ArrayList<>();
private Paint mNormalPaint, mHighlightPaint;
private int mCurrentLine = 0;
private long mCurrentPosition = 0;

// 平滑滚动相关变量
private float mScrollOffset = 0f; // 当前滚动偏移量（行数）
private ValueAnimator mScrollAnimator; // 滚动动画
private int mScrollDuration = 300; // 滚动动画时长（毫秒）

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

初始化画笔
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

设置滚动动画时长
@param duration 动画时长（毫秒）
*/
public void setScrollDuration(int duration) {
mScrollDuration = duration;
}
/**

设置普通文本大小
@param textSize 文本大小
*/
public void setNormalTextSize(float textSize) {
mNormalPaint.setTextSize(textSize);
// 重新计算所有歌词行的宽度
recalculateLineWidths();
invalidate();
}
/**

设置高亮文本大小
@param textSize 文本大小
*/
public void setHighlightTextSize(float textSize) {
mHighlightPaint.setTextSize(textSize);
// 重新计算所有歌词行的宽度
recalculateLineWidths();
invalidate();
}
/**

重新计算所有歌词行的宽度
*/
private void recalculateLineWidths() {
for (LrcLine line : mLrcLines) {
line.width = mNormalPaint.measureText(line.text);
}
}
/**

设置普通文本颜色
@param color 颜色值
*/
public void setNormalColor(int color) {
mNormalPaint.setColor(color);
invalidate();
}
/**

设置高亮文本颜色
@param color 颜色值
*/
public void setHighlightColor(int color) {
mHighlightPaint.setColor(color);
invalidate();
}
/**

解析LRC格式歌词

@param lrcContent LRC格式歌词内容
*/
public void setLrcText(String lrcContent) {
mLrcLines.clear();
String[] lines = lrcContent.split("\n");
Pattern pattern = Pattern.compile("$(\d{2}):(\d{2})\.(\d{1,3})$");

for (String line : lines) {
// 跳过空行
if (line.trim().isEmpty()) {
continue;
}
 Matcher matcher = pattern.matcher(line);
 List<Long> times = new ArrayList<>();
 String text = "";
 int lastEnd = 0;

 // 查找所有时间标签
 while (matcher.find()) {
     int min = Integer.parseInt(matcher.group(1));
     int sec = Integer.parseInt(matcher.group(2));
     // 处理毫秒部分
     String msStr = matcher.group(3);
     long ms;
     if (msStr.length() == 2) {
         // 2位数字，按百分秒处理
         ms = Integer.parseInt(msStr) * 10L; // 百分秒转毫秒
     } else if (msStr.length() == 1) {
         ms = Integer.parseInt(msStr) * 100L; // 如.1 -> 100毫秒
     } else {
         ms = Integer.parseInt(msStr); // 3位数字，直接作为毫秒
     }
     times.add((min * 60 + sec) * 1000L + ms);
     lastEnd = matcher.end();
 }

 // 如果有时间标签，提取歌词文本
 if (!times.isEmpty()) {
     text = line.substring(lastEnd).trim();
     if (!text.isEmpty()) {
         for (Long time : times) {
             LrcLine lrcLine = new LrcLine();
             lrcLine.time = time;
             lrcLine.text = text;
             lrcLine.width = mNormalPaint.measureText(text);
             mLrcLines.add(lrcLine);
         }
     }
 }


}
Collections.sort(mLrcLines, (a, b) -> Long.compare(a.time, b.time));

// 修改初始行，确保能显示7行
if (mLrcLines.size() > 3) {
mCurrentLine = 3; // 从第3行开始显示
} else if (!mLrcLines.isEmpty()) {
mCurrentLine = mLrcLines.size() - 1; // 如果歌词少于4行，显示最后一行
} else {
mCurrentLine = 0;
}
mCurrentPosition = 0;
mScrollOffset = 0f; // 重置滚动偏移
invalidate();
}

/**

平滑滚动到指定行

@param targetLine 目标行索引
*/
private void smoothScrollTo(int targetLine) {
if (mScrollAnimator != null && mScrollAnimator.isRunning()) {
mScrollAnimator.cancel();
}

// 计算滚动距离（行数差）
int lineDiff = targetLine - mCurrentLine;
if (lineDiff == 0) {
return; // 无需滚动
}

// 设置动画
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

更新播放进度

@param position 当前播放时间（毫秒）
*/
public void updateTime(long position) {
if (mLrcLines.isEmpty()) {
return;
}

mCurrentPosition = position;

// 查找当前应该显示的行
int targetLine = 0;
for (int i = 0; i < mLrcLines.size(); i++) {
if (i == mLrcLines.size() - 1 ||
position >= mLrcLines.get(i).time &&
position < mLrcLines.get(i + 1).time) {
targetLine = i;
break;
}
}

// 如果行数发生变化，启动平滑滚动
if (targetLine != mCurrentLine) {
smoothScrollTo(targetLine);
} else {
// 行数不变，只更新进度
invalidate();
}
}

/**

绘制卡拉OK效果
*/
@Override
protected void onDraw(Canvas canvas) {
super.onDraw(canvas);
if (mLrcLines.isEmpty()) {
return;
}

// 计算总高度和起始Y位置，实现垂直居中
float lineHeight = mNormalPaint.getTextSize() * 1.5f;
float totalHeight = lineHeight * 7; // 显示7行歌词

// 应用滚动偏移
float scrollOffsetPixels = mScrollOffset * lineHeight;
float startY = (getHeight() - totalHeight) / 2 + mNormalPaint.getTextSize() - scrollOffsetPixels;

// 绘制当前行及前后行
for (int i = -3; i <= 3; i++) {
int index = mCurrentLine + i;
if (index < 0 || index >= mLrcLines.size()) {
continue;
}

 LrcLine line = mLrcLines.get(index);
 float y = startY + (i + 3) * lineHeight; // 从startY开始计算每行位置

 if (i == 0) {
     // 当前行：卡拉OK高亮效果
     float progress = (float) (mCurrentPosition - line.time) /
             (index + 1 < mLrcLines.size() ?
                     mLrcLines.get(index + 1).time - line.time : 1000);
     progress = Math.max(0, Math.min(1, progress));

     // 绘制背景文本（完整）
     canvas.drawText(line.text, getWidth() / 2 - line.width / 2, y, mNormalPaint);

     // 绘制高亮部分（渐变填充）
     float highlightWidth = line.width * progress;
     canvas.save();
     canvas.clipRect(getWidth() / 2 - line.width / 2, y - mHighlightPaint.getTextSize(),
             getWidth() / 2 - line.width / 2 + highlightWidth, y + 10);
     canvas.drawText(line.text, getWidth() / 2 - line.width / 2, y, mHighlightPaint);
     canvas.restore();
 } else {
     // 非当前行：普通显示
     canvas.drawText(line.text, getWidth() / 2 - line.width / 2, y, mNormalPaint);
 }
}
}

/**

清理资源
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
