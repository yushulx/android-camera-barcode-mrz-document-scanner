// https://github.com/googlesamples/mlkit/blob/master/android/vision-quickstart/app/src/main/java/com/google/mlkit/vision/demo/java/barcodescanner/BarcodeGraphic.java
package com.example.qrcodescanner;

import static java.lang.Math.max;
import static java.lang.Math.min;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.Log;

import com.dynamsoft.dbr.Point;
import com.dynamsoft.dbr.TextResult;

public class BarcodeGraphic extends GraphicOverlay.Graphic {
    private static final int TEXT_COLOR = Color.BLACK;
    private static final int MARKER_COLOR = Color.RED;
    private static final int LINE_MARKER_COLOR = Color.BLUE;
    private static final float TEXT_SIZE = 54.0f;
    private static final float STROKE_WIDTH = 4.0f;

    private final Paint rectPaint;
    private final Paint barcodePaint;
    private final Paint linePaint;
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

        linePaint = new Paint();
        linePaint.setColor(LINE_MARKER_COLOR);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(STROKE_WIDTH);
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
        }

        if (barcode != null) {
            Point[] points = barcode.localizationResult.resultPoints;
            if (isPortrait) {
                points = new Point[points.length];
                for (int i = 0; i < points.length; i++) {
                    points[i] = ImageUtils.rotateCW90(barcode.localizationResult.resultPoints[i], overlay.getImageWidth());
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

                canvas.drawLine(translateX(points[from].x), translateY(points[from].y), translateX(points[to].x), translateY(points[to].y), linePaint);
            }

            RectF currentRect = new RectF();
            currentRect.left = translateX(xmin);
            currentRect.top = translateY(ymin);
            currentRect.right = translateX(xmax);
            currentRect.bottom = translateY(ymax);

            float lineHeight = TEXT_SIZE + (2 * STROKE_WIDTH);
            float textWidth = barcodePaint.measureText(barcode.barcodeText);
            canvas.drawRect(
                    currentRect.left - STROKE_WIDTH,
                    currentRect.top - lineHeight,
                    currentRect.left + textWidth + (2 * STROKE_WIDTH),
                    currentRect.top,
                    labelPaint);
            // Renders the barcode at the bottom of the box.
            canvas.drawText(barcode.barcodeText, currentRect.left, currentRect.top - STROKE_WIDTH, barcodePaint);
        }
    }


}
