package com.dynamsoft.ionic.qrcodescanner;

import android.graphics.Bitmap;
import android.util.Log;

import com.dynamsoft.cvr.CapturedResult;
import com.dynamsoft.cvr.CaptureVisionRouter;
import com.dynamsoft.cvr.EnumPresetTemplate;
import com.dynamsoft.dbr.DecodedBarcodesResult;
import com.dynamsoft.license.LicenseManager;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Thin wrapper around the Dynamsoft Capture Vision native SDK.
 *
 * A single {@link CaptureVisionRouter} is shared by the live camera scanner and the
 * still-image scanner so that the deep learning models are only loaded once.
 */
public final class ScannerEngine {

    private static final String TAG = "ScannerEngine";
    private static final long LICENSE_TIMEOUT_SECONDS = 20;

    private static volatile ScannerEngine instance;

    private final CaptureVisionRouter router = new CaptureVisionRouter();
    private volatile boolean licenseInitialized = false;

    private ScannerEngine() {
    }

    public static ScannerEngine getInstance() {
        if (instance == null) {
            synchronized (ScannerEngine.class) {
                if (instance == null) {
                    instance = new ScannerEngine();
                }
            }
        }
        return instance;
    }

    public CaptureVisionRouter getRouter() {
        return router;
    }

    public boolean isLicenseInitialized() {
        return licenseInitialized;
    }

    /** Template used by both the camera and the file data source. */
    public static String template() {
        return EnumPresetTemplate.PT_READ_BARCODES;
    }

    /**
     * Initialize the Dynamsoft license and block until it finishes or times out.
     *
     * @return {@code true} when the license is valid
     */
    public synchronized boolean initLicense(String license) {
        if (licenseInitialized) {
            return true;
        }
        final CountDownLatch latch = new CountDownLatch(1);
        LicenseManager.initLicense(license, (isSuccess, error) -> {
            licenseInitialized = isSuccess;
            if (!isSuccess) {
                Log.e(TAG, "License initialization failed: " + (error == null ? "" : error.getMessage()));
            }
            latch.countDown();
        });
        try {
            latch.await(LICENSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return licenseInitialized;
    }

    /**
     * Decode barcodes from a still image. This is the "file" data source.
     */
    public DecodedBarcodesResult decodeBitmap(Bitmap bitmap) throws ScannerException {
        if (bitmap == null) {
            throw new ScannerException("Source bitmap is null");
        }
        return unwrap(router.capture(bitmap, template()));
    }

    /**
     * Decode barcodes from a local file path. This is the "file" data source.
     */
    public DecodedBarcodesResult decodeFile(String path) throws ScannerException {
        if (path == null || path.isEmpty()) {
            throw new ScannerException("Source path is empty");
        }
        return unwrap(router.capture(path, template()));
    }

    private static DecodedBarcodesResult unwrap(CapturedResult result) {
        if (result == null) {
            return new DecodedBarcodesResult();
        }
        DecodedBarcodesResult decoded = result.getDecodedBarcodesResult();
        return decoded == null ? new DecodedBarcodesResult() : decoded;
    }

    /** Error type raised by the scanner engine. */
    public static class ScannerException extends Exception {
        public ScannerException(String message) {
            super(message);
        }
    }
}
