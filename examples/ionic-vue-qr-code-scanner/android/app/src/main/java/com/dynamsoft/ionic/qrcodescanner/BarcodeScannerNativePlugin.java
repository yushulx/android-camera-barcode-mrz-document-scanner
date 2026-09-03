package com.dynamsoft.ionic.qrcodescanner;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import androidx.activity.result.ActivityResult;

import com.dynamsoft.dbr.BarcodeResultItem;
import com.dynamsoft.dbr.DecodedBarcodesResult;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONArray;

import java.io.InputStream;

/**
 * In-app Capacitor plugin that exposes the Dynamsoft Capture Vision **native** Android SDK to
 * the Ionic web layer.
 *
 * Methods:
 * <ul>
 *   <li>{@code initLicense} - activate the SDK</li>
 *   <li>{@code startScan} - live camera data source</li>
 *   <li>{@code scanFromGallery} - still image data source via the system photo picker</li>
 *   <li>{@code scanFile} - still image data source from a known path or content URI</li>
 * </ul>
 */
@CapacitorPlugin(name = "BarcodeScannerNative")
public class BarcodeScannerNativePlugin extends Plugin {

    private static final String TAG = "BarcodeScannerNative";

    private final java.util.concurrent.ExecutorService backgroundExecutor =
            java.util.concurrent.Executors.newSingleThreadExecutor();

    @PluginMethod
    public void initLicense(PluginCall call) {
        String license = call.getString("license");
        if (license == null || license.isEmpty()) {
            license = getContext().getString(R.string.dynamsoft_license);
        }
        boolean ok = ScannerEngine.getInstance().initLicense(license);
        JSObject ret = new JSObject();
        ret.put("success", ok);
        ret.put("message", ok ? "License activated" : "License initialization failed");
        call.resolve(ret);
    }

    /** Live camera data source. */
    @PluginMethod
    public void startScan(PluginCall call) {
        if (!ensureLicense(call)) {
            return;
        }
        Intent intent = new Intent(getContext(), ScannerActivity.class);
        startActivityForResult(call, intent, "handleScanResult");
    }

    /** Still image data source through the system photo picker. */
    @PluginMethod
    public void scanFromGallery(PluginCall call) {
        if (!ensureLicense(call)) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        if (intent.resolveActivity(getContext().getPackageManager()) == null) {
            call.reject("No photo picker available on this device");
            return;
        }
        startActivityForResult(call, Intent.createChooser(intent, "Select an image"), "handleGalleryResult");
    }

    /** Still image data source from a path or content URI supplied by the web layer. */
    @PluginMethod
    public void scanFile(PluginCall call) {
        String uri = call.getString("uri");
        if (uri == null || uri.isEmpty()) {
            call.reject("Missing 'uri' option");
            return;
        }
        if (!ensureLicense(call)) {
            return;
        }
        backgroundExecutor.execute(() -> {
                try {
                    DecodedBarcodesResult result = decodeUri(uri);
                    call.resolve(buildResult(result.getItems()));
                } catch (Exception e) {
                    Log.e(TAG, "Failed to decode image", e);
                    call.reject("Failed to decode image: " + e.getMessage(), e);
                }
            });
    }

    @ActivityCallback
    private void handleScanResult(PluginCall call, ActivityResult result) {
        if (call == null) {
            return;
        }
        Intent data = result.getData();
        if (result.getResultCode() == Activity.RESULT_OK && data != null) {
            String json = data.getStringExtra(ScanResultContract.EXTRA_RESULTS);
            call.resolve(wrapResults(json));
            return;
        }
        String error = data != null ? data.getStringExtra(ScanResultContract.EXTRA_ERROR) : null;
        call.reject(error == null ? "canceled" : error);
    }

    @ActivityCallback
    private void handleGalleryResult(PluginCall call, ActivityResult result) {
        if (call == null) {
            return;
        }
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
            call.reject("canceled");
            return;
        }
        Uri uri = result.getData().getData();
        if (uri == null) {
            call.reject("No image selected");
            return;
        }
        backgroundExecutor.execute(() -> {
                try {
                    DecodedBarcodesResult decoded = decodeUri(uri.toString());
                    call.resolve(buildResult(decoded.getItems()));
                } catch (Exception e) {
                    Log.e(TAG, "Failed to decode picked image", e);
                    call.reject("Failed to decode image: " + e.getMessage(), e);
                }
            });
    }

    private boolean ensureLicense(PluginCall call) {
        ScannerEngine engine = ScannerEngine.getInstance();
        if (engine.isLicenseInitialized()) {
            return true;
        }
        if (!engine.initLicense(getContext().getString(R.string.dynamsoft_license))) {
            call.reject("Dynamsoft license initialization failed. Check your license key.");
            return false;
        }
        return true;
    }

    /** Decode a content URI, a {@code file://} URI, or a raw absolute path. */
    private DecodedBarcodesResult decodeUri(String uri) throws ScannerEngine.ScannerException {
        ScannerEngine engine = ScannerEngine.getInstance();
        if (uri.startsWith("content://") || uri.startsWith("android.resource://")) {
            return engine.decodeBitmap(loadBitmap(Uri.parse(uri)));
        }
        String path = uri.startsWith("file://") ? uri.substring("file://".length()) : uri;
        return engine.decodeFile(path);
    }

    private Bitmap loadBitmap(Uri uri) throws ScannerEngine.ScannerException {
        ContentResolver resolver = getContext().getContentResolver();
        try (InputStream stream = resolver.openInputStream(uri)) {
            if (stream == null) {
                throw new ScannerEngine.ScannerException("Cannot open " + uri);
            }
            return BitmapFactory.decodeStream(stream);
        } catch (ScannerEngine.ScannerException e) {
            throw e;
        } catch (Exception e) {
            throw new ScannerEngine.ScannerException("Cannot read " + uri + ": " + e.getMessage());
        }
    }

    private static JSObject buildResult(BarcodeResultItem[] items) throws Exception {
        JSONArray array = BarcodeResultMapper.toJson(items);
        return wrapResults(array.toString());
    }

    private static JSObject wrapResults(String json) {
        JSObject ret = new JSObject();
        try {
            ret.put("results", json == null ? new JSArray() : new JSArray(json));
        } catch (Exception e) {
            ret.put("results", new JSArray());
        }
        return ret;
    }
}
