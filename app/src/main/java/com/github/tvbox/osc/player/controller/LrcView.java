
package com.github.tvbox.osc.player.controller;

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

public class LrcView extends View {
    private List<LrcLine> mLrcLines = new ArrayList<>();
    private Paint mNormalPaint, mHighlightPaint;
    private int mCurrentLine = 0;
    private long mCurrentPosition = 0;
    
    // LRC歌词行数据结构
    private static class LrcLine {
        long time;      // 时间戳（毫秒）
        String text;    // 歌词文本
        float width;    // 文本宽度（用于绘制）
    }
    
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
    
    // 设置普通文本大小
    public void setNormalTextSize(float textSize) {
        mNormalPaint.setTextSize(textSize);
        invalidate();
    }
    
    // 设置高亮文本大小
    public void setHighlightTextSize(float textSize) {
        mHighlightPaint.setTextSize(textSize);
        invalidate();
    }
    
    // 设置普通文本颜色
    public void setNormalColor(int color) {
        mNormalPaint.setColor(color);
        invalidate();
    }
    
    // 设置高亮文本颜色
    public void setHighlightColor(int color) {
        mHighlightPaint.setColor(color);
        invalidate();
    }
    
    // 解析LRC格式歌词
    public void setLrcText(String lrcContent) {
        mLrcLines.clear();
        String[] lines = lrcContent.split("\n");
        Pattern pattern = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\]");
        
        for (String line : lines) {
            Matcher matcher = pattern.matcher(line);
            while (matcher.find()) {
                LrcLine lrcLine = new LrcLine();
                int min = Integer.parseInt(matcher.group(1));
                int sec = Integer.parseInt(matcher.group(2));
                int ms = Integer.parseInt(matcher.group(3).length() == 2 ? 
                    matcher.group(3) + "0" : matcher.group(3));
                lrcLine.time = (min * 60 + sec) * 1000 + ms;
                lrcLine.text = line.substring(matcher.end()).trim();
                lrcLine.width = mNormalPaint.measureText(lrcLine.text);
                mLrcLines.add(lrcLine);
            }
        }
        Collections.sort(mLrcLines, (a, b) -> Long.compare(a.time, b.time));
        mCurrentLine = 0;
        mCurrentPosition = 0;
        invalidate();
    }
    
    // 更新播放进度
    public void updateTime(long position) {
        if (mLrcLines.isEmpty()) return;
        
        mCurrentPosition = position;
        for (int i = 0; i < mLrcLines.size(); i++) {
            if (i == mLrcLines.size() - 1 || 
                position >= mLrcLines.get(i).time && 
                position < mLrcLines.get(i + 1).time) {
                mCurrentLine = i;
                break;
            }
        }
        invalidate();
    }
    
    // 绘制卡拉OK效果
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mLrcLines.isEmpty()) return;
        
        float centerY = getHeight() / 2;
        float lineHeight = mNormalPaint.getTextSize() * 1.5f;
        
        // 绘制当前行及前后行
        for (int i = -3; i <= 3; i++) {
            int index = mCurrentLine + i;
            if (index < 0 || index >= mLrcLines.size()) continue;
            
            LrcLine line = mLrcLines.get(index);
            float y = centerY + i * lineHeight;
            
            if (i == 0) {
                // 当前行：卡拉OK高亮效果
                float progress = (float)(mCurrentPosition - line.time) / 
                    (index + 1 < mLrcLines.size() ? 
                     mLrcLines.get(index + 1).time - line.time : 1000);
                progress = Math.max(0, Math.min(1, progress));
                
                // 绘制背景文本（完整）
                canvas.drawText(line.text, getWidth()/2 - line.width/2, y, mNormalPaint);
                
                // 绘制高亮部分（渐变填充）
                float highlightWidth = line.width * progress;
                canvas.save();
                canvas.clipRect(getWidth()/2 - line.width/2, y - mHighlightPaint.getTextSize(),
                              getWidth()/2 - line.width/2 + highlightWidth, y + 10);
                canvas.drawText(line.text, getWidth()/2 - line.width/2, y, mHighlightPaint);
                canvas.restore();
            } else {
                // 非当前行：普通显示
                canvas.drawText(line.text, getWidth()/2 - line.width/2, y, mNormalPaint);
            }
        }
    }
}
