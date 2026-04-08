package com.github.tvbox.osc.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Base64;

import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.api.ApiConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.json.JSONException;
import org.json.JSONObject;

import android.text.TextUtils;
import android.graphics.Rect;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;

import me.jessyan.autosize.utils.AutoSizeUtils;

/**
 * xuameng
 * base64图片
 * 解决base64图片圆角变形等问题
 * @version 2.1.0 <br/>
 * 修改说明：修复宽图模式下文字变形及圆角不圆的问题
 */
public class ImgUtil {
    private static final Map<String, Drawable> drawableCache = new HashMap<>();
    
    public static boolean isBase64Image(String picUrl) {
        return picUrl.startsWith("data:image");
    }
    
    public static int defaultWidth = 220;
    public static int defaultHeight = 280;

    /**
     * style 数据结构：ratio 指定宽高比（宽 / 高），type 表示风格（例如 rect、list）
     */
    public static class Style {
        public float ratio;
        public String type;

        public Style(float ratio, String type) {
            this.ratio = ratio;
            this.type = type;
        }
    }

    // ... (initStyle, spanCountByStyle, getStyleDefaultWidth 方法保持不变，此处省略) ...
    // 注意：如果你的项目中这些方法有变动，请保留你自己的逻辑，仅替换 createTextDrawable 方法即可

    /**
     * 判断是否为中文字符
     */
    private static boolean isChineseChar(char c) {
        Character.UnicodeBlock ub = Character.UnicodeBlock.of(c);
        return ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || ub == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A;
    }

    public static Drawable createTextDrawable(String text) {
        if (TextUtils.isEmpty(text)) {
            text = "聚";
        }
        text = text.substring(0, 1);

        if (drawableCache.containsKey(text)) {
            return drawableCache.get(text);
        }

        Style style = initStyle();
        char firstChar = text.charAt(0);
        boolean isChinese = isChineseChar(firstChar);

        int width;
        int height;

        if (style != null) {
            width = getStyleDefaultWidth(style);

            if ("list".equals(style.type)) {
                height = width;
            } else {
                float safeRatio = normalizeRatio(style.ratio);
                height = (int) (width / safeRatio);
            }
        } else {
            width = defaultWidth;
            height = defaultHeight;
        }

        if (height <= 0) {
            height = defaultHeight;
        }

        int randomColor = getRandomColor();

        // 1. 修复圆角变形：基于对角线长度计算圆角，避免宽图下圆角过小
        float diagonal = (float) Math.sqrt(width * width + height * height);
        float cornerRadius = diagonal * 0.03f; // 3% 的对角线作为圆角半径

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(randomColor);
        paint.setStyle(Paint.Style.FILL);

        RectF rectF = new RectF(0, 0, width, height);
        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, paint);

        // 2. 绘制文字
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setAntiAlias(true);

        // 3. 修复文字抻拉：根据比例和字符类型动态调整字号
        float textSize;
        boolean isWideImage = style != null && "rect".equals(style.type) && style.ratio > 1.5f;

        if (isWideImage) {
            // 宽图模式：基于宽度计算字号，防止英文/符号被压扁
            textSize = width * 0.12f; // 调整系数以获得最佳视觉效果
        } else {
            // 竖图或方图：保持原逻辑，基于高度（最小边）计算
            float baseSize = Math.min(width, height);
            textSize = baseSize * 0.25f;
        }
        paint.setTextSize(textSize);

        // 4. 增加内边距：限制文字绘制区域，防止贴边
        float paddingVertical = height * 0.2f; // 上下各留 20% 的空白
        float textAreaTop = paddingVertical;
        float textAreaBottom = height - paddingVertical;
        
        Paint.FontMetrics fm = paint.getFontMetrics();
        float x = width / 2f;
        // 在限定的区域内垂直居中
        float y = textAreaTop + (textAreaBottom - textAreaTop - fm.bottom - fm.top) / 2f;

        canvas.drawText(text, x, y, paint);

        Drawable drawable = new BitmapDrawable(bitmap);
        drawableCache.put(text, drawable);
        return drawable;
    }

    // ... (decodeBase64ToBitmap, createTextDrawable, getRandomColor, clearCache, decodeBase64ToRoundBitmap, normalizeRatio 方法保持不变) ...
    
    public static int getRandomColor() {
        Random random = new Random();
        return Color.argb(255, random.nextInt(256), random.nextInt(256), random.nextInt(256));
    }

    public static void clearCache() {
        drawableCache.clear();
    }
    
    // 下方其他方法省略，请保留你原文件中的实现
    public static Bitmap decodeBase64ToBitmap(String base64Str) { /* ... */ return null; }
    public static Bitmap decodeBase64ToRoundBitmap(String base64Str, int radiusPx) { /* ... */ return null; }
    public static float normalizeRatio(float ratio) { /* ... */ return ratio; }
    public static Style initStyle() { /* ... */ return null; }
    public static int spanCountByStyle(Style style, int defaultCount) { /* ... */ return defaultCount; }
    public static int getStyleDefaultWidth(Style style) { /* ... */ return defaultWidth; }
}
