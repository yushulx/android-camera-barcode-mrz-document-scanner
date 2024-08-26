package com.google.mlkit.vision.demo.java.dynamsoftbarcodescanner;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

import com.google.mlkit.vision.demo.GraphicOverlay;
import com.google.mlkit.vision.demo.GraphicOverlay.Graphic;

import static java.lang.Math.max;
import static java.lang.Math.min;

import com.dynamsoft.dbr.*;

/** Graphic instance for rendering Barcode position and content information in an overlay view. */
public class DynamsoftBarcodeGraphic extends Graphic {

    private static final int TEXT_COLOR = Color.BLACK;
    private static final int MARKER_COLOR = Color.WHITE;
    private static final float TEXT_SIZE = 54.0f;
    private static final float STROKE_WIDTH = 4.0f;

    private final Paint rectPaint;
    private final Paint barcodePaint;
    private final TextResult result;
    private final Paint labelPaint;

    DynamsoftBarcodeGraphic(GraphicOverlay overlay, TextResult result) {
        super(overlay);

        this.result = result;

        rectPaint = new Paint();
        rectPaint.setColor(MARKER_COLOR);
        rectPaint.setStyle(Paint.Style.STROKE);
        rectPaint.setStrokeWidth(STROKE_WIDTH);

        barcodePaint = new Paint();
        barcodePaint.setColor(TEXT_COLOR);
        barcodePaint.setTextSize(TEXT_SIZE);

        labelPaint = new Paint();
        labelPaint.setColor(MARKER_COLOR);
        labelPaint.setStyle(Paint.Style.FILL);
    }

    /**
     * Draws the barcode block annotations for position, size, and raw value on the supplied canvas.
     */
    @Override
    public void draw(Canvas canvas) {
        if (result == null) {
            throw new IllegalStateException("Attempting to draw a null barcode.");
        }

        // Draws the bounding box around the BarcodeBlock.
        Point[] points = result.localizationResult.resultPoints;
        int minx = points[0].x;
        int miny = points[0].y;
        int maxx = points[0].x;
        int maxy = points[0].y;
        for (int i = 1; i < 4; i++) {
            if (points[i].x < minx) {
                minx = points[i].x;
            }
            else if (points[i].x > maxx) {
                maxx = points[i].x;
            }

            if (points[i].y < miny) {
                miny = points[i].y;
            }
            else if (points[i].y > maxy) {
                maxy = points[i].y;
            }
        }
        RectF rect = new RectF(minx, miny, maxx, maxy);
        // If the image is flipped, the left will be translated to right, and the right to left.
        float x0 = translateX(rect.left);
        float x1 = translateX(rect.right);
        rect.left = min(x0, x1);
        rect.right = max(x0, x1);
        rect.top = translateY(rect.top);
        rect.bottom = translateY(rect.bottom);
        canvas.drawRect(rect, rectPaint);

        // Draws other object info.
        float lineHeight = TEXT_SIZE + (2 * STROKE_WIDTH);
        float textWidth = barcodePaint.measureText(result.barcodeText);
        canvas.drawRect(
                rect.left - STROKE_WIDTH,
                rect.top - lineHeight,
                rect.left + textWidth + (2 * STROKE_WIDTH),
                rect.top,
                labelPaint);
        // Renders the barcode at the bottom of the box.
        canvas.drawText(result.barcodeText, rect.left, rect.top - STROKE_WIDTH, barcodePaint);
    }
}
