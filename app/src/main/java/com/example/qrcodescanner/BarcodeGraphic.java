// https://github.com/googlesamples/mlkit/blob/master/android/vision-quickstart/app/src/main/java/com/google/mlkit/vision/demo/java/barcodescanner/BarcodeGraphic.java
package com.example.qrcodescanner;

import static java.lang.Math.max;
import static java.lang.Math.min;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

import com.dynamsoft.dbr.Point;
import com.dynamsoft.dbr.TextResult;

public class BarcodeGraphic extends GraphicOverlay.Graphic {
    private static final int TEXT_COLOR = Color.BLACK;
    private static final int MARKER_COLOR = Color.RED;
    private static final float TEXT_SIZE = 54.0f;
    private static final float STROKE_WIDTH = 4.0f;

    private final Paint rectPaint;
    private final Paint barcodePaint;
    private final TextResult barcode;
    private final Paint labelPaint;
    private RectF rect;
    private GraphicOverlay overlay;
    private boolean isPortrait = true;

    BarcodeGraphic(GraphicOverlay overlay, RectF boundingBox, TextResult barcode, boolean isPortrait) {
        super(overlay);

        this.barcode = barcode;
        this.rect = boundingBox;
        this.overlay = overlay;
        this.isPortrait = isPortrait;
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
        // Draws the bounding box around the BarcodeBlock.
        if (rect != null) {
            float x0 = translateX(rect.left);
            float x1 = translateX(rect.right);
            rect.left = min(x0, x1);
            rect.right = max(x0, x1);
            rect.top = translateY(rect.top);
            rect.bottom = translateY(rect.bottom);
            canvas.drawRect(rect, rectPaint);
            // Draws other object info.
            if (barcode != null) {
                float lineHeight = TEXT_SIZE + (2 * STROKE_WIDTH);
                float textWidth = barcodePaint.measureText(barcode.barcodeText);
                canvas.drawRect(
                        rect.left - STROKE_WIDTH,
                        rect.top - lineHeight,
                        rect.left + textWidth + (2 * STROKE_WIDTH),
                        rect.top,
                        labelPaint);
                // Renders the barcode at the bottom of the box.
                canvas.drawText(barcode.barcodeText, rect.left, rect.top - STROKE_WIDTH, barcodePaint);
            }
        }
        else {
            if (barcode != null) {
                Point[] points = barcode.localizationResult.resultPoints;
                if (isPortrait) {
                    for (int i = 0; i < points.length; i++) {
                        points[i] = ImageUtils.rotateCW90(points[i], overlay.getImageWidth());
                    }
                }

                int xmin = points[0].x, ymin = points[0].y, xmax = points[0].x, ymax = points[0].y;
                for (int i = 0; i < 4; i++) {
                    int from = i, to = i + 1;
                    if (to == 4) to = 0;

                    if (i > 0) {
                        if (points[from].x < xmin) xmin = points[from].x;
                        if (points[from].x > xmax) xmax = points[from].x;
                        if (points[from].y < ymin) ymin = points[from].y;
                        if (points[from].y > ymax) ymax = points[from].y;
                    }

                    canvas.drawLine(translateX(points[from].x), translateY(points[from].y), translateX(points[to].x), translateY(points[to].y), rectPaint);
                }

                rect = new RectF();
                rect.left = translateX(xmin);
                rect.top = translateY(ymin);
                rect.right = translateX(xmax);
                rect.bottom = translateY(ymax);

                float lineHeight = TEXT_SIZE + (2 * STROKE_WIDTH);
                float textWidth = barcodePaint.measureText(barcode.barcodeText);
                canvas.drawRect(
                        rect.left - STROKE_WIDTH,
                        rect.top - lineHeight,
                        rect.left + textWidth + (2 * STROKE_WIDTH),
                        rect.top,
                        labelPaint);
                // Renders the barcode at the bottom of the box.
                canvas.drawText(barcode.barcodeText, rect.left, rect.top - STROKE_WIDTH, barcodePaint);
            }
        }

    }
}
