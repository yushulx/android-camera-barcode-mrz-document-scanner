package com.dynamsoft.documentscanner.scan;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;

import com.dynamsoft.core.basic_structures.CoreException;
import com.dynamsoft.core.basic_structures.ImageData;
import com.dynamsoft.core.basic_structures.Quadrilateral;
import com.dynamsoft.ddn.EnumImageColourMode;

public class DocumentPage {
    private ImageData originalImage;
    private ImageData normalizedImage;
    private Bitmap normalizedBitmap; // Used when quad is manually edited
    private Quadrilateral quad;
    private int colorMode = EnumImageColourMode.ICM_COLOUR;
    private int rotationDegrees = 0;

    private Bitmap cachedBitmap;

    public DocumentPage(ImageData originalImage, ImageData normalizedImage, Quadrilateral quad) {
        this.originalImage = originalImage;
        this.normalizedImage = normalizedImage;
        this.quad = quad;
    }

    /** Use when the image is already decoded/corrected as a Bitmap (e.g. gallery import). */
    public DocumentPage(Bitmap bitmap) {
        this.normalizedBitmap = bitmap;
    }

    public ImageData getOriginalImage() {
        return originalImage;
    }

    public ImageData getNormalizedImage() {
        return normalizedImage;
    }

    public Quadrilateral getQuad() {
        return quad;
    }

    public void setQuad(Quadrilateral quad) {
        this.quad = quad;
    }

    public boolean hasOriginalImage() {
        return originalImage != null;
    }

    public void updateFromQuadEdit(Bitmap deskewedBitmap, Quadrilateral newQuad) {
        if (normalizedBitmap != null && !normalizedBitmap.isRecycled()) {
            normalizedBitmap.recycle();
        }
        normalizedBitmap = deskewedBitmap;
        normalizedImage = null;
        this.quad = newQuad;
        invalidateCache();
    }

    public int getColorMode() {
        return colorMode;
    }

    public void setColorMode(int colorMode) {
        if (this.colorMode != colorMode) {
            this.colorMode = colorMode;
            invalidateCache();
        }
    }

    public int getRotationDegrees() {
        return rotationDegrees;
    }

    public void rotate90() {
        rotationDegrees = (rotationDegrees + 90) % 360;
        invalidateCache();
    }

    private void invalidateCache() {
        if (cachedBitmap != null && cachedBitmap != normalizedBitmap && !cachedBitmap.isRecycled()) {
            cachedBitmap.recycle();
        }
        cachedBitmap = null;
    }

    public Bitmap getDisplayBitmap() throws CoreException {
        if (cachedBitmap != null && !cachedBitmap.isRecycled()) {
            return cachedBitmap;
        }

        // Always convert to bitmap first so that orientation metadata in ImageData
        // is applied by toBitmap(). Then apply color mode at bitmap level, which
        // avoids the misorientation that ImageProcessor conversions can cause.
        Bitmap sourceBitmap;
        if (normalizedBitmap != null) {
            sourceBitmap = normalizedBitmap;
        } else {
            sourceBitmap = normalizedImage.toBitmap();
        }

        Bitmap bitmap = applyColorModeBitmap(sourceBitmap, colorMode);
        // Recycle the intermediate source only if it's a fresh decode (not normalizedBitmap)
        if (bitmap != sourceBitmap && normalizedBitmap == null) {
            sourceBitmap.recycle();
        }

        if (rotationDegrees != 0) {
            Matrix matrix = new Matrix();
            matrix.postRotate(rotationDegrees);
            Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            if (rotated != bitmap && bitmap != normalizedBitmap) {
                bitmap.recycle();
            }
            bitmap = rotated;
        }

        cachedBitmap = bitmap;
        return cachedBitmap;
    }

    public Bitmap getThumbnail(int maxSize) throws CoreException {
        Bitmap full = getDisplayBitmap();
        if (full == null) return null;

        int w = full.getWidth();
        int h = full.getHeight();
        float scale = Math.min((float) maxSize / w, (float) maxSize / h);
        int newW = Math.round(w * scale);
        int newH = Math.round(h * scale);
        return Bitmap.createScaledBitmap(full, newW, newH, true);
    }

    public static Bitmap perspectiveTransform(Bitmap source, Quadrilateral quad) {
        float w1 = distance(quad.points[0].x, quad.points[0].y, quad.points[1].x, quad.points[1].y);
        float w2 = distance(quad.points[3].x, quad.points[3].y, quad.points[2].x, quad.points[2].y);
        float h1 = distance(quad.points[0].x, quad.points[0].y, quad.points[3].x, quad.points[3].y);
        float h2 = distance(quad.points[1].x, quad.points[1].y, quad.points[2].x, quad.points[2].y);
        int outW = Math.round(Math.max(w1, w2));
        int outH = Math.round(Math.max(h1, h2));
        if (outW <= 0) outW = source.getWidth();
        if (outH <= 0) outH = source.getHeight();

        float[] src = {
                quad.points[0].x, quad.points[0].y,
                quad.points[1].x, quad.points[1].y,
                quad.points[2].x, quad.points[2].y,
                quad.points[3].x, quad.points[3].y
        };
        float[] dst = {
                0, 0,
                outW, 0,
                outW, outH,
                0, outH
        };

        Matrix matrix = new Matrix();
        matrix.setPolyToPoly(src, 0, dst, 0, 4);

        Bitmap result = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(source, matrix, paint);
        return result;
    }

    private static float distance(float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static Bitmap applyColorModeBitmap(Bitmap src, int colorMode) {
        if (colorMode == EnumImageColourMode.ICM_COLOUR) {
            return src;
        }
        Bitmap result = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        Paint paint = new Paint();
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);
        paint.setColorFilter(new ColorMatrixColorFilter(cm));
        canvas.drawBitmap(src, 0, 0, paint);

        if (colorMode == EnumImageColourMode.ICM_BINARY) {
            int w = result.getWidth(), h = result.getHeight();
            int[] pixels = new int[w * h];
            result.getPixels(pixels, 0, w, 0, 0, w, h);
            for (int i = 0; i < pixels.length; i++) {
                int gray = pixels[i] & 0xFF;
                pixels[i] = gray > 128 ? 0xFFFFFFFF : 0xFF000000;
            }
            result.setPixels(pixels, 0, w, 0, 0, w, h);
        }
        return result;
    }
}
