package com.google.mlkit.vision.demo.java.dynamsoftbarcodescanner;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;

import com.dynamsoft.cvr.CapturedResult;
import com.dynamsoft.cvr.CaptureVisionRouter;
import com.dynamsoft.cvr.EnumPresetTemplate;
import com.dynamsoft.dbr.BarcodeResultItem;
import com.dynamsoft.dbr.DecodedBarcodesResult;
import com.dynamsoft.license.LicenseManager;
import com.dynamsoft.license.LicenseVerificationListener;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.demo.GraphicOverlay;
import com.google.mlkit.vision.demo.java.VisionProcessorBase;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Decodes barcodes from still images with the Dynamsoft Barcode Reader bundle (v11). */
public class DynamsoftBarcodeProcessor extends VisionProcessorBase<List<BarcodeResultItem>> {

    private static final String TAG = "DynamsoftBarcodeProcessor";

    // Public trial license. A network connection is required for the first online license validation.
    // Get a 30-day trial license from https://www.dynamsoft.com/customer/license/trialLicense?product=dbr
    private static final String LICENSE_KEY = "DLS2eyJoYW5kc2hha2VDb2RlIjoiMjAwMDAxLTE2NDk4Mjk3OTI2MzUiLCJvcmdhbml6YXRpb25JRCI6IjIwMDAwMSIsInNlc3Npb25QYXNzd29yZCI6IndTcGR6Vm05WDJrcEQ5YUoifQ==";

    private final CaptureVisionRouter router;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private DecodeListener decodeListener;

    /** Callback to report decoding results back to the UI. */
    public interface DecodeListener {
        void onDecoded(BarcodeResultItem[] items, long elapsedMs);
        void onError(String message);
    }

    public void setDecodeListener(DecodeListener listener) {
        this.decodeListener = listener;
    }

    public DynamsoftBarcodeProcessor(Context context) {
        super(context);

        LicenseManager.initLicense(LICENSE_KEY, new LicenseVerificationListener() {
            @Override
            public void onLicenseVerified(boolean isSuccessful, Exception e) {
                if (isSuccessful) {
                    Log.i(TAG, "License verified");
                } else {
                    Log.e(TAG, "License verification failed: " + (e == null ? "" : e.getMessage()));
                }
            }
        });

        router = new CaptureVisionRouter(context);
    }

    @Override
    public void stop() {
        super.stop();
        executor.shutdown();
    }

    @Override
    protected Task<List<BarcodeResultItem>> detectInImage(InputImage image) {
        return null;
    }

    @Override
    public void processBitmap(Bitmap bitmap, final GraphicOverlay graphicOverlay) {
        // stop() shuts the executor down while the activity is paused (e.g. the camera
        // intent is in the foreground), but the activity can still deliver a result and
        // start a new detection before this processor gets replaced. Recreate the executor
        // in that case instead of crashing with a RejectedExecutionException.
        if (executor.isShutdown()) {
            executor = Executors.newSingleThreadExecutor();
        }
        // Decoding is CPU intensive, so run it off the main thread.
        executor.execute(() -> {
            long frameStartMs = SystemClock.elapsedRealtime();
            CapturedResult result = router.capture(bitmap, EnumPresetTemplate.PT_READ_BARCODES);
            long frameEndMs = SystemClock.elapsedRealtime();

            mainHandler.post(() -> {
                if (result == null || result.getErrorCode() != 0) {
                    String message = result == null ? "null result" : result.getErrorMessage();
                    Log.e(TAG, "Decode failed: " + message);
                    if (decodeListener != null) {
                        decodeListener.onError(message);
                    }
                    if (graphicOverlay != null) {
                        graphicOverlay.clear();
                        graphicOverlay.postInvalidate();
                    }
                    return;
                }

                DecodedBarcodesResult barcodes = result.getDecodedBarcodesResult();
                BarcodeResultItem[] items = barcodes == null ? null : barcodes.getItems();
                Log.i(TAG, "Decoded " + (items == null ? 0 : items.length)
                        + " barcode(s) in " + (frameEndMs - frameStartMs) + " ms");
                if (graphicOverlay != null) {
                    graphicOverlay.clear();
                    if (items != null && items.length > 0) {
                        for (BarcodeResultItem item : items) {
                            graphicOverlay.add(new DynamsoftBarcodeGraphic(graphicOverlay, item));
                        }
                    }
                    graphicOverlay.postInvalidate();
                }
                if (decodeListener != null) {
                    decodeListener.onDecoded(items, frameEndMs - frameStartMs);
                }
            });
        });
    }

    @Override
    protected void onSuccess(@NonNull List<BarcodeResultItem> results, @NonNull GraphicOverlay graphicOverlay) {
        for (BarcodeResultItem item : results) {
            graphicOverlay.add(new DynamsoftBarcodeGraphic(graphicOverlay, item));
        }
    }

    @Override
    protected void onFailure(@NonNull Exception e) {
        Log.e(TAG, "Barcode detection failed " + e);
    }
}
