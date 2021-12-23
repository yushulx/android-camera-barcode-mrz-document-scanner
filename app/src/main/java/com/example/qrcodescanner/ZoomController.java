package com.example.qrcodescanner;

import android.app.Activity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

public class ZoomController {
    public final static String TAG = "ZoomController";
    private float currentFactor = 1.0f;
    private float minZoomRatio = 1.0f, maxZoomRatio = 1.0f;
    private ZoomStatus zoomStatus;
    private ScaleGestureDetector scaleGestureDetector;
    private ScaleGestureDetector.OnScaleGestureListener scaleGestureListener = new ScaleGestureDetector.OnScaleGestureListener() {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            Log.i(TAG, "onScale: " + detector.getScaleFactor());
            currentFactor = detector.getScaleFactor() * currentFactor;
            if (currentFactor < minZoomRatio) currentFactor = minZoomRatio;
            if (currentFactor > maxZoomRatio) currentFactor = maxZoomRatio;

            if (zoomStatus != null) {
                zoomStatus.onZoomChange(currentFactor);
            }

            return true;
        }

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
        }
    };

    public ZoomController(Activity activity) {
        scaleGestureDetector = new ScaleGestureDetector(activity, scaleGestureListener);
    }

    public interface ZoomStatus {
        void onZoomChange(float ratio);
    }

    public void addListener(ZoomStatus zoomStatus) {
        this.zoomStatus = zoomStatus;
    }

    public void initZoomRatio(float minZoomRatio, float maxZoomRatio) {
        this.minZoomRatio = minZoomRatio;
        this.maxZoomRatio = maxZoomRatio;
    }

    public boolean onTouchEvent(MotionEvent event) {
        return scaleGestureDetector.onTouchEvent(event);
    }
}
