package com.github.tvbox.osc.picasso;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;

import androidx.annotation.IntDef;

import com.squareup.picasso.Transformation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 描述
 *
 * @author pj567
 * @since 2020/12/22
 */
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
        int srcW = source.getWidth();
        int srcH = source.getHeight();

        int outW = viewWidth <= 0 ? srcW : viewWidth;
        int outH = viewHeight <= 0 ? srcH : viewHeight;

        Bitmap out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        canvas.setDrawFilter(new PaintFlagsDrawFilter(0,
                Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));

        // ---- bitmap paint ----
        Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bitmapPaint.setFilterBitmap(true);
        bitmapPaint.setShader(new BitmapShader(
                source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));

        Matrix m = new Matrix();
        float scale;
        if ((float) srcW / outW > (float) srcH / outH) {
            scale = (float) outH / srcH;
            float dx = (srcW * scale - outW) / 2f;
            m.setScale(scale, scale);
            m.postTranslate(-dx, 0);
        } else {
            scale = (float) outW / srcW;
            float dy = (srcH * scale - outH) / 2f;
            m.setScale(scale, scale);
            m.postTranslate(0, centerCorp ? -dy : 0);
        }
        bitmapPaint.getShader().setLocalMatrix(m);

        // ---- mask paint ----
        Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Path path = buildRoundPath(outW, outH);
        canvas.save();
        canvas.clipPath(path);
        canvas.drawPaint(bitmapPaint);
        canvas.restore();

        // bottom label
        if (bottomShapeHeight > 0) {
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

    private Path buildRoundPath(int w, int h) {
        Path p = new Path();
        RectF r = new RectF(0, 0, w, h);
        switch (roundType) {
            case RoundType.ALL:
                p.addRoundRect(r, radius, radius, Path.Direction.CW);
                break;
            case RoundType.TOP:
                p.addRoundRect(r, new float[]{
                        radius, radius, radius, radius, 0, 0, 0, 0}, Path.Direction.CW);
                break;
            case RoundType.BOTTOM:
                p.addRoundRect(r, new float[]{
                        0, 0, 0, 0, radius, radius, radius, radius}, Path.Direction.CW);
                break;
            case RoundType.LEFT:
                p.addRoundRect(r, new float[]{
                        radius, radius, 0, 0, 0, 0, radius, radius}, Path.Direction.CW);
                break;
            case RoundType.RIGHT:
                p.addRoundRect(r, new float[]{
                        0, 0, radius, radius, radius, radius, 0, 0}, Path.Direction.CW);
                break;
            default:
                p.addRect(r, Path.Direction.CW);
        }
        return p;
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
