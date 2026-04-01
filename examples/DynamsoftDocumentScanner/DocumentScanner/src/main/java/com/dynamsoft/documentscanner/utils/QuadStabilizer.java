package com.dynamsoft.documentscanner.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.dynamsoft.core.basic_structures.Quadrilateral;

import android.graphics.Point;

public class QuadStabilizer {

    public interface StabilityCallback {
        void onStable();
    }

    private static final String PREFS_NAME = "quad_stabilizer_prefs";
    private static final String KEY_IOU_THRESHOLD = "iou_threshold";
    private static final String KEY_AREA_DELTA_THRESHOLD = "area_delta_threshold";
    private static final String KEY_STABLE_FRAME_COUNT = "stable_frame_count";
    private static final String KEY_AUTO_CAPTURE_ENABLED = "auto_capture_enabled";

    private float iouThreshold;
    private float areaDeltaThreshold;
    private int stableFrameCount;
    private boolean autoCaptureEnabled;

    private Quadrilateral previousQuad = null;
    private int consecutiveStableFrames = 0;
    private StabilityCallback callback;

    public QuadStabilizer(Context context) {
        loadSettings(context);
    }

    public void loadSettings(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        iouThreshold = prefs.getFloat(KEY_IOU_THRESHOLD, 0.85f);
        areaDeltaThreshold = prefs.getFloat(KEY_AREA_DELTA_THRESHOLD, 0.15f);
        stableFrameCount = prefs.getInt(KEY_STABLE_FRAME_COUNT, 3);
        autoCaptureEnabled = prefs.getBoolean(KEY_AUTO_CAPTURE_ENABLED, true);
    }

    public static void saveSettings(Context context, float iouThreshold, float areaDeltaThreshold,
                                    int stableFrameCount, boolean autoCaptureEnabled) {
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        editor.putFloat(KEY_IOU_THRESHOLD, iouThreshold);
        editor.putFloat(KEY_AREA_DELTA_THRESHOLD, areaDeltaThreshold);
        editor.putInt(KEY_STABLE_FRAME_COUNT, stableFrameCount);
        editor.putBoolean(KEY_AUTO_CAPTURE_ENABLED, autoCaptureEnabled);
        editor.apply();
    }

    public void setCallback(StabilityCallback callback) {
        this.callback = callback;
    }

    public void reset() {
        previousQuad = null;
        consecutiveStableFrames = 0;
    }

    public void feedQuad(Quadrilateral quad) {
        if (!autoCaptureEnabled) {
            return;
        }

        if (previousQuad == null) {
            previousQuad = quad;
            consecutiveStableFrames = 0;
            return;
        }

        float iou = calculateIoU(previousQuad, quad);
        double prevArea = calculateQuadArea(previousQuad);
        double currArea = calculateQuadArea(quad);
        float areaDelta = prevArea > 0 ? (float) Math.abs(currArea - prevArea) / (float) prevArea : 1.0f;

        if (iou >= iouThreshold && areaDelta <= areaDeltaThreshold) {
            consecutiveStableFrames++;
            if (consecutiveStableFrames >= stableFrameCount && callback != null) {
                callback.onStable();
                reset();
            }
        } else {
            consecutiveStableFrames = 0;
        }

        previousQuad = quad;
    }

    public static float calculateIoU(Quadrilateral a, Quadrilateral b) {
        int[] boundsA = getBounds(a);
        int[] boundsB = getBounds(b);

        int intersectLeft = Math.max(boundsA[0], boundsB[0]);
        int intersectTop = Math.max(boundsA[1], boundsB[1]);
        int intersectRight = Math.min(boundsA[2], boundsB[2]);
        int intersectBottom = Math.min(boundsA[3], boundsB[3]);

        if (intersectLeft >= intersectRight || intersectTop >= intersectBottom) {
            return 0f;
        }

        float intersectionArea = (float) (intersectRight - intersectLeft) * (intersectBottom - intersectTop);
        float areaA = (float) (boundsA[2] - boundsA[0]) * (boundsA[3] - boundsA[1]);
        float areaB = (float) (boundsB[2] - boundsB[0]) * (boundsB[3] - boundsB[1]);
        float unionArea = areaA + areaB - intersectionArea;

        return unionArea > 0 ? intersectionArea / unionArea : 0f;
    }

    private static int[] getBounds(Quadrilateral quad) {
        Point[] points = quad.points;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (Point p : points) {
            minX = Math.min(minX, p.x);
            minY = Math.min(minY, p.y);
            maxX = Math.max(maxX, p.x);
            maxY = Math.max(maxY, p.y);
        }
        return new int[]{minX, minY, maxX, maxY};
    }

    private static double calculateQuadArea(Quadrilateral quad) {
        Point[] p = quad.points;
        // Shoelace formula for polygon area
        double area = 0;
        int n = p.length;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            area += (double) p[i].x * p[j].y;
            area -= (double) p[j].x * p[i].y;
        }
        return Math.abs(area) / 2.0;
    }

    // Getters for current settings
    public float getIouThreshold() { return iouThreshold; }
    public float getAreaDeltaThreshold() { return areaDeltaThreshold; }
    public int getStableFrameCount() { return stableFrameCount; }
    public boolean isAutoCaptureEnabled() { return autoCaptureEnabled; }
}
