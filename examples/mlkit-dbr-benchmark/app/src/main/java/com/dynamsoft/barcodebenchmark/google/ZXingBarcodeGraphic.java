package com.dynamsoft.barcodebenchmark.google;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;

import com.dynamsoft.barcodebenchmark.google.ui.camera.GraphicOverlay;

import zxingcpp.BarcodeReader;

/**
 * Graphic for rendering ZXing-CPP barcode position on the overlay.
 * Uses the 4-corner Position data from BarcodeReader.Result.
 */
public class ZXingBarcodeGraphic extends GraphicOverlay.Graphic {

    private static final int COLOR = Color.parseColor("#FF9800"); // Orange for ZXing-CPP

    private final Paint mRectPaint;
    private final Paint mTextPaint;
    private volatile BarcodeReader.Result mResult;

    public ZXingBarcodeGraphic(GraphicOverlay overlay) {
        super(overlay);

        mRectPaint = new Paint();
        mRectPaint.setColor(COLOR);
        mRectPaint.setStyle(Paint.Style.STROKE);
        mRectPaint.setStrokeWidth(4.0f);

        mTextPaint = new Paint();
        mTextPaint.setColor(COLOR);
        mTextPaint.setTextSize(36.0f);
    }

    public void updateItem(BarcodeReader.Result result) {
        mResult = result;
        postInvalidate();
    }

    @Override
    public void draw(Canvas canvas) {
        BarcodeReader.Result result = mResult;
        if (result == null || result.getPosition() == null) {
            return;
        }

        BarcodeReader.Position pos = result.getPosition();

        // Draw polygon through the 4 corners
        Path path = new Path();
        path.moveTo(translateX(pos.getTopLeft().x),     translateY(pos.getTopLeft().y));
        path.lineTo(translateX(pos.getTopRight().x),    translateY(pos.getTopRight().y));
        path.lineTo(translateX(pos.getBottomRight().x), translateY(pos.getBottomRight().y));
        path.lineTo(translateX(pos.getBottomLeft().x),  translateY(pos.getBottomLeft().y));
        path.close();
        canvas.drawPath(path, mRectPaint);

        // Draw label above the top-left corner
        String label = result.getText();
        if (label != null && label.length() > 30) {
            label = label.substring(0, 30) + "...";
        }
        if (label != null) {
            canvas.drawText(label,
                    translateX(pos.getTopLeft().x),
                    translateY(pos.getTopLeft().y) - 10,
                    mTextPaint);
        }
    }
}
