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

public class RoundTransformation implements Transformation {

    private int viewWidth, viewHeight;
    private int radius;
    private int bottomShapeHeight;
    private boolean centerCorp;
    private int roundType;
    private final String key;

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

        // ---------- 1. 兜底：统一 Bitmap 配置 ----------
        if (source != null && source.getConfig() != Bitmap.Config.ARGB_8888) {
            Bitmap argb = source.copy(Bitmap.Config.ARGB_8888, false);
            if (argb != null) {
                source.recycle();
                source = argb;
            }
        }

        int srcW = source.getWidth();
        int srcH = source.getHeight();

        int outW = viewWidth <= 0 ? srcW : viewWidth;
        int outH = viewHeight <= 0 ? srcH : viewHeight;

        Bitmap out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        canvas.setDrawFilter(new PaintFlagsDrawFilter(
                0, Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));

        // ---------- 2. BitmapShader + Matrix ----------
        BitmapShader shader = new BitmapShader(
                source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);

        Matrix matrix = new Matrix();
        float scale;
        if ((float) srcW / outW > (float) srcH / outH) {
            scale = (float) outH / srcH;
            float dx = (srcW * scale - outW) / 2f;
            matrix.setScale(scale, scale);
            matrix.postTranslate(-dx, 0);
        } else {
            scale = (float) outW / srcW;
            float dy = (srcH * scale - outH) / 2f;
            matrix.setScale(scale, scale);
            matrix.postTranslate(0, centerCorp ? -dy : 0);
        }
        shader.setLocalMatrix(matrix);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setFilterBitmap(true);
        paint.setShader(shader);

        // ---------- 3. 圆角绘制（不使用 clipPath） ----------
        RectF rect = new RectF(0, 0, outW, outH);
        float[] radii = getCornerRadii();

        canvas.drawRoundRect(rect, radii[0], radii[1], paint);

        // ---------- 4. bottom shape ----------
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

        source.recycle();
        return out;
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
            default:
                // NONE
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
