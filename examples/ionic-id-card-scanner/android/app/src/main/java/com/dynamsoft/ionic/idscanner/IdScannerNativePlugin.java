package com.dynamsoft.ionic.idscanner;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;

import androidx.activity.result.ActivityResult;

import com.dynamsoft.core.basic_structures.ImageData;
import com.dynamsoft.cvr.CapturedResult;
import com.dynamsoft.dcp.ParsedResultItem;
import com.dynamsoft.ddn.DeskewedImageResultItem;
import com.dynamsoft.ddn.ProcessedDocumentResult;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * In-app Capacitor plugin that exposes the Dynamsoft MRZ Scanner **native** Android
 * SDK to the Ionic web layer.
 *
 * Methods:
 * <ul>
 *   <li>{@code initLicense} - activate the SDK</li>
 *   <li>{@code startScan} - live camera data source</li>
 *   <li>{@code scanFromGallery} - still image data source via the system photo picker</li>
 *   <li>{@code scanFile} - still image data source from a known path or content URI</li>
 * </ul>
 */
@CapacitorPlugin(name = "IdScannerNative")
public class IdScannerNativePlugin extends Plugin {

    private static final String TAG = "IdScannerNative";

    /** Simple worker pool used for off-main-thread image parsing. */
    private final java.util.concurrent.ExecutorService backgroundExecutor =
            java.util.concurrent.Executors.newSingleThreadExecutor();

    @PluginMethod
    public void initLicense(PluginCall call) {
        String license = call.getString("license");
        if (license == null || license.isEmpty()) {
            license = getContext().getString(R.string.dynamsoft_license);
        }
        boolean ok = IdScannerEngine.getInstance().initLicense(license);
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
        Intent intent = new Intent(getContext(), IdScanActivity.class);
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
        startActivityForResult(call, Intent.createChooser(intent, "Select a document image"), "handleGalleryResult");
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
                    CapturedResult result = captureUri(uri);
                    call.resolve(buildResult(result));
                } catch (Exception e) {
                    Log.e(TAG, "Failed to parse image", e);
                    call.reject("Failed to parse image: " + e.getMessage(), e);
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
            String json = data.getStringExtra(ScanResultContract.EXTRA_JSON);
            if (json != null) {
                try {
                    call.resolve(new JSObject(json));
                } catch (Exception e) {
                    Log.e(TAG, "Invalid scan result JSON", e);
                    call.reject("Invalid scan result: " + e.getMessage());
                }
                return;
            }
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
                    CapturedResult captured = captureUri(uri.toString());
                    call.resolve(buildResult(captured));
                } catch (Exception e) {
                    Log.e(TAG, "Failed to parse picked image", e);
                    call.reject("Failed to parse image: " + e.getMessage(), e);
                }
            });
    }

    private boolean ensureLicense(PluginCall call) {
        IdScannerEngine engine = IdScannerEngine.getInstance();
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
    private CapturedResult captureUri(String uri) throws IdScannerEngine.ScannerException {
        IdScannerEngine engine = IdScannerEngine.getInstance();
        if (uri.startsWith("content://") || uri.startsWith("android.resource://")) {
            return engine.parseBitmap(loadBitmap(Uri.parse(uri)));
        }
        String path = uri.startsWith("file://") ? uri.substring("file://".length()) : uri;
        return engine.parseFile(path);
    }

    private Bitmap loadBitmap(Uri uri) throws IdScannerEngine.ScannerException {
        ContentResolver resolver = getContext().getContentResolver();
        try (InputStream stream = resolver.openInputStream(uri)) {
            if (stream == null) {
                throw new IdScannerEngine.ScannerException("Cannot open " + uri);
            }
            return BitmapFactory.decodeStream(stream);
        } catch (IdScannerEngine.ScannerException e) {
            throw e;
        } catch (Exception e) {
            throw new IdScannerEngine.ScannerException("Cannot read " + uri + ": " + e.getMessage());
        }
    }

    /**
     * Build the JSON payload from a single-file capture result:
     * parsed MRZ fields plus the deskewed document image when available.
     */
    private static JSObject buildResult(CapturedResult captured) throws Exception {
        ParsedResultItem item = captured.getParsedResult().getItems()[0];
        JSONObject fields = IdResultFormatter.toJson(item);
        JSObject payload = new JSObject();
        payload.put("fields", new JSObject(fields.toString()));
        ProcessedDocumentResult doc = captured.getProcessedDocumentResult();
        if (doc != null && doc.getDeskewedImageResultItems() != null
                && doc.getDeskewedImageResultItems().length > 0) {
            DeskewedImageResultItem deskewed = doc.getDeskewedImageResultItems()[0];
            ImageData imageData = deskewed.getImageData();
            if (imageData != null) {
                Bitmap bitmap = imageData.toBitmap();
                if (bitmap != null) {
                    payload.put("documentImageBase64", toBase64(bitmap));
                }
            }
        }
        return payload;
    }

    private static String toBase64(Bitmap bitmap) {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, os);
        return "data:image/jpeg;base64," + Base64.encodeToString(os.toByteArray(), Base64.NO_WRAP);
    }
}
