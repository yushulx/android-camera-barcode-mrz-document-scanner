package com.dynamsoft.ionic.idscanner;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import com.dynamsoft.cvr.CaptureVisionRouter;
import com.dynamsoft.cvr.CaptureVisionRouterException;
import com.dynamsoft.cvr.CapturedResult;
import com.dynamsoft.dcp.ParsedResult;
import com.dynamsoft.license.LicenseManager;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Thin wrapper around the Dynamsoft MRZ Scanner native SDK.
 *
 * A single {@link CaptureVisionRouter} is shared by the live camera scanner and the
 * still-image scanner so that the deep learning models are only loaded once.
 */
public final class IdScannerEngine {

    private static final String TAG = "IdScannerEngine";
    private static final long LICENSE_TIMEOUT_SECONDS = 20;

    private static volatile IdScannerEngine instance;

    private final CaptureVisionRouter router = new CaptureVisionRouter();
    private volatile boolean licenseInitialized = false;
    private volatile boolean templateReady = false;

    private IdScannerEngine() {
    }

    public static IdScannerEngine getInstance() {
        if (instance == null) {
            synchronized (IdScannerEngine.class) {
                if (instance == null) {
                    instance = new IdScannerEngine();
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

    /** Name of the built-in MRZ template shipped with the mrzscannerbundle. */
    public static String template() {
        return "ReadPassportAndId";
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
     * Load the MRZ templates bundled with the SDK. Called once when the first
     * scanning screen is opened; it is a no-op when the templates are already loaded.
     */
    public synchronized void ensureTemplates() throws ScannerException {
        if (templateReady) {
            return;
        }
        try {
            router.initSettingsFromFile("mrzscanner-mobile-templates.json");
            templateReady = true;
        } catch (CaptureVisionRouterException e) {
            throw new ScannerException("Failed to load MRZ templates: " + e.getMessage());
        }
    }

    /**
     * Parse a document from a still image. This is the "file" data source.
     */
    public CapturedResult parseBitmap(Bitmap bitmap) throws ScannerException {
        if (bitmap == null) {
            throw new ScannerException("Source bitmap is null");
        }
        return ensureParsed(router.capture(bitmap, template()));
    }

    /**
     * Parse a document from a local file path. This is the "file" data source.
     */
    public CapturedResult parseFile(String path) throws ScannerException {
        if (path == null || path.isEmpty()) {
            throw new ScannerException("Source path is empty");
        }
        return ensureParsed(router.capture(path, template()));
    }

    private static CapturedResult ensureParsed(CapturedResult result) throws ScannerException {
        if (result == null) {
            throw new ScannerException("No result was produced for the input image");
        }
        ParsedResult parsed = result.getParsedResult();
        if (parsed == null || parsed.getItems() == null || parsed.getItems().length == 0) {
            throw new ScannerException("No MRZ was found in the image. Make sure the Machine-Readable Zone is clearly visible.");
        }
        return result;
    }

    /** Error type raised by the scanner engine. */
    public static class ScannerException extends Exception {
        public ScannerException(String message) {
            super(message);
        }
    }
}
