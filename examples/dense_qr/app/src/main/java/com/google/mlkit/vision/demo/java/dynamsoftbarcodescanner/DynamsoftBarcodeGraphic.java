package com.google.mlkit.vision.demo.java.dynamsoftbarcodescanner;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;

import com.dynamsoft.dbr.BarcodeResultItem;
import com.google.mlkit.vision.demo.GraphicOverlay;
import com.google.mlkit.vision.demo.GraphicOverlay.Graphic;

/** Graphic instance for rendering the barcode detection contour in an overlay view. */
public class DynamsoftBarcodeGraphic extends Graphic {

    private static final int MARKER_COLOR = 0xFF00B04F; // Dynamsoft green
    private static final float STROKE_WIDTH = 4.0f;

    private final Paint rectPaint;
    private final BarcodeResultItem result;

    DynamsoftBarcodeGraphic(GraphicOverlay overlay, BarcodeResultItem result) {
        super(overlay);

        this.result = result;

        rectPaint = new Paint();
        rectPaint.setColor(MARKER_COLOR);
        rectPaint.setStyle(Paint.Style.STROKE);
        rectPaint.setStrokeWidth(STROKE_WIDTH);
        rectPaint.setAntiAlias(true);
    }

    /** Draws the barcode contour on the supplied canvas by connecting the four corners. */
    @Override
    public void draw(Canvas canvas) {
        if (result == null) {
            throw new IllegalStateException("Attempting to draw a null barcode.");
        }

        Point[] points = result.getLocation().points;
        if (points == null || points.length < 4) {
            return;
        }
        // The four corners are reported in order (top-left, top-right, bottom-right,
        // bottom-left); connect them into a closed contour so perspective-distorted
        // barcodes are outlined correctly instead of being drawn as a bounding rectangle.
        Path contour = new Path();
        contour.moveTo(translateX(points[0].x), translateY(points[0].y));
        for (int i = 1; i < 4; i++) {
            contour.lineTo(translateX(points[i].x), translateY(points[i].y));
        }
        contour.close();
        canvas.drawPath(contour, rectPaint);
    }
}
