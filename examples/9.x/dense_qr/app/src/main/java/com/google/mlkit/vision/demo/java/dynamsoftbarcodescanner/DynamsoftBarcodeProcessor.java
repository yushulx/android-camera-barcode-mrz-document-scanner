package com.google.mlkit.vision.demo.java.dynamsoftbarcodescanner;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.android.odml.image.BitmapMlImageBuilder;
import com.google.android.odml.image.MlImage;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.demo.GraphicOverlay;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;

import com.dynamsoft.dbr.*;
import com.google.mlkit.vision.demo.InferenceInfoGraphic;
import com.google.mlkit.vision.demo.java.VisionProcessorBase;

/** Barcode Detector Demo. */
public class DynamsoftBarcodeProcessor extends VisionProcessorBase<List<TextResult>> {

    private static final String TAG = "BarcodeProcessor";

    private BarcodeReader barcodeScanner;

    private HandlerThread handlerThread;
    private volatile boolean isBusy = false;

    public DynamsoftBarcodeProcessor(Context context) {
        super(context);

        try {
            // Get a license key from https://www.dynamsoft.com/customer/license/trialLicense?product=dbr
            BarcodeReader.initLicense(
                    "DLS2eyJoYW5kc2hha2VDb2RlIjoiMjAwMDAxLTE2NDk4Mjk3OTI2MzUiLCJvcmdhbml6YXRpb25JRCI6IjIwMDAwMSIsInNlc3Npb25QYXNzd29yZCI6IndTcGR6Vm05WDJrcEQ5YUoifQ==",
                    new DBRLicenseVerificationListener() {
                        @Override
                        public void DBRLicenseVerificationCallback(boolean isSuccessful, Exception e) {
                        }
                    });
            barcodeScanner = new BarcodeReader();
            PublicRuntimeSettings settings = barcodeScanner.getRuntimeSettings();
            settings.expectedBarcodesCount = 1;
            barcodeScanner.updateRuntimeSettings(settings);
        } catch (BarcodeReaderException e) {
            e.printStackTrace();
        }

        handlerThread = new HandlerThread("dbr");
        handlerThread.start();
    }

    @Override
    public void stop() {
        super.stop();
    }

    @Override
    protected Task<List<TextResult>> detectInImage(InputImage image) {
        return null;
    }

    @Override
    public void processBitmap(Bitmap bitmap, final GraphicOverlay graphicOverlay) {
        try {
            long frameStartMs = SystemClock.elapsedRealtime();
            TextResult[] results = barcodeScanner.decodeBufferedImage(bitmap);
            long frameEndMs = SystemClock.elapsedRealtime();

            if (results != null) {
                for (int i = 0; i < results.length; ++i) {
                    TextResult barcode = results[i];
                    graphicOverlay.add(new DynamsoftBarcodeGraphic(graphicOverlay, barcode));
                }
                graphicOverlay.add(
                        new InferenceInfoGraphic(
                                graphicOverlay,
                                frameEndMs - frameStartMs,
                                frameEndMs - frameStartMs,
                                null));
            }

        } catch (BarcodeReaderException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onSuccess(@NonNull List<TextResult> results, @NonNull GraphicOverlay graphicOverlay) {
        for (int i = 0; i < results.size(); ++i) {
            TextResult barcode = results.get(i);
            graphicOverlay.add(new DynamsoftBarcodeGraphic(graphicOverlay, barcode));
        }
    }

    @Override
    protected void onFailure(@NonNull Exception e) {
        Log.e(TAG, "Barcode detection failed " + e);
    }
}
