package com.github.tvbox.osc.picasso;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.RectF;
import android.graphics.Shader;

import androidx.annotation.IntDef;

import com.squareup.picasso.Transformation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 内存安全版 RoundTransformation
 * 特性：
 * 1. 使用 Xfermode/drawRoundRect 替代 clipPath，兼容性更好
 * 2. 智能 Bitmap 配置，减少内存占用
 * 3. 限制最大尺寸，防止 OOM
 *
 * @author pj567 (original)
 * @optimized by AI Assistant
 */
public class RoundTransformation implements Transformation {

    private static final int MAX_BITMAP_SIZE = 2048; // 防止超大图导致 OOM

    private int viewWidth, viewHeight;
    private int radius;
    private int bottomShapeHeight;
    private boolean centerCorp;
    private int roundType;
    private final String key;

    // 复用 Matrix，减少对象创建
    private final Matrix matrix = new Matrix();

    public RoundTransformation(String key) {
        this.key = key;
        this.viewWidth = 0;
        this.viewHeight = 0;
        this.radius = 0;
        this.bottomShapeHeight = 0;
        this.centerCorp = true;
        this.roundType = RoundType.NONE;
    }

    public RoundTransformation override(int width, int height) {
        this.viewWidth = width;
        this.viewHeight = height;
        return this;
    }

    public RoundTransformation roundRadius(int radius, int roundType) {
        this.radius = radius;
        this.roundType = roundType;
        return this;
    }

    public RoundTransformation centerCorp(boolean centerCorp) {
        this.centerCorp = centerCorp;
        return this;
    }

    public RoundTransformation bottomShapeHeight(int height) {
        this.bottomShapeHeight = height;
        return this;
    }

    @Override
    public Bitmap transform(Bitmap source) {
        if (source == null) {
            return null;
        }

        try {
            int srcW = source.getWidth();
            int srcH = source.getHeight();

            // 1. 计算输出尺寸，并限制最大尺寸以防 OOM
            int outW = viewWidth <= 0 ? srcW : viewWidth;
            int outH = viewHeight <= 0 ? srcH : viewHeight;

            float scaleFactor = Math.min(
                    (float) MAX_BITMAP_SIZE / outW,
                    (float) MAX_BITMAP_SIZE / outH
            );

            if (scaleFactor < 1f) {
                outW = (int) (outW * scaleFactor);
                outH = (int) (outH * scaleFactor);
            }

            // 2. 决定输出 Bitmap 的配置 (关键优化点)
            // 如果有圆角或底部遮罩，必须用 ARGB_8888；否则用 RGB_565 节省一半内存
            Bitmap.Config outConfig =
                    (radius > 0 || bottomShapeHeight > 0)
                            ? Bitmap.Config.ARGB_8888
                            : Bitmap.Config.RGB_565;

            Bitmap out = Bitmap.createBitmap(outW, outH, outConfig);
            Canvas canvas = new Canvas(out);
            canvas.setDrawFilter(new PaintFlagsDrawFilter(
                    0, Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));

            // 3. 智能处理输入 Bitmap (关键优化点)
            // 只有当源图不是 ARGB_8888 且我们需要透明通道时，才进行昂贵的 copy 操作
            Bitmap workingBitmap = source;
            if (outConfig == Bitmap.Config.ARGB_8888 &&
                    source.getConfig() != Bitmap.Config.ARGB_8888) {
                Bitmap converted = source.copy(Bitmap.Config.ARGB_8888, false);
                if (converted != null) {
                    workingBitmap = converted;
                }
            }

            // 4. 设置 BitmapShader 和 Matrix
            BitmapShader shader = new BitmapShader(
                    workingBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);

            matrix.reset();
            float scale;
            float dx = 0, dy = 0;

            if ((float) srcW / outW > (float) srcH / outH) {
                scale = (float) outH / srcH;
                dx = (srcW * scale - outW) / 2f;
            } else {
                scale = (float) outW / srcW;
                dy = (srcH * scale - outH) / 2f;
            }

            matrix.setScale(scale, scale);
            matrix.postTranslate(-dx, centerCorp ? -dy : 0);
            shader.setLocalMatrix(matrix);

            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setFilterBitmap(true);
            paint.setShader(shader);

            // 5. 绘制圆角矩形 (不使用 clipPath，更稳定)
            RectF rect = new RectF(0, 0, outW, outH);
            float[] radii = getCornerRadii();
            canvas.drawRoundRect(rect, radii[0], radii[1], paint);

            // 6. 绘制底部遮罩
            if (bottomShapeHeight > 0) {
                Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                maskPaint.setColor(0x99000000);
                canvas.drawRect(
                        0,
                        outH - bottomShapeHeight,
                        outW,
                        outH,
                        maskPaint
                );
            }

            // 7. 回收临时创建的 Bitmap
            if (workingBitmap != source) {
                workingBitmap.recycle();
            }

            return out;
        } finally {
            // 确保 source 被回收
            if (source != null && !source.isRecycled()) {
                source.recycle();
            }
        }
    }

    private float[] getCornerRadii() {
        float[] radii = new float[8];
        float r = radius;
        switch (roundType) {
            case RoundType.ALL:
                for (int i = 0; i < 8; i++) radii[i] = r;
                break;
            case RoundType.TOP:
                radii[0] = r; radii[1] = r;
                radii[2] = r; radii[3] = r;
                break;
            case RoundType.BOTTOM:
                radii[4] = r; radii[5] = r;
                radii[6] = r; radii[7] = r;
                break;
            case RoundType.LEFT:
                radii[0] = r; radii[1] = r;
                radii[6] = r; radii[7] = r;
                break;
            case RoundType.RIGHT:
                radii[2] = r; radii[3] = r;
                radii[4] = r; radii[5] = r;
                break;
            default: // NONE
        }
        return radii;
    }

    @Override
    public String key() {
        return key + "_" + viewWidth + "x" + viewHeight + "_r" + radius + "_t" + roundType;
    }

    @IntDef({RoundType.ALL, RoundType.TOP, RoundType.BOTTOM, RoundType.LEFT, RoundType.RIGHT, RoundType.NONE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface RoundType {
        int ALL = 0;
        int TOP = 1;
        int RIGHT = 2;
        int BOTTOM = 3;
        int LEFT = 4;
        int NONE = 5;
    }
}
