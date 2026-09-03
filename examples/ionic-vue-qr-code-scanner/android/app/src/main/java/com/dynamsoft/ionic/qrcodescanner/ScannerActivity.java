package com.dynamsoft.ionic.qrcodescanner;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.dynamsoft.core.basic_structures.CompletionListener;
import com.dynamsoft.cvr.CaptureVisionRouter;
import com.dynamsoft.cvr.CaptureVisionRouterException;
import com.dynamsoft.cvr.CapturedResultReceiver;
import com.dynamsoft.dbr.BarcodeResultItem;
import com.dynamsoft.dbr.DecodedBarcodesResult;
import com.dynamsoft.dce.CameraEnhancer;
import com.dynamsoft.dce.CameraView;
import com.dynamsoft.dce.DrawingLayer;
import com.dynamsoft.dce.QuadDrawingItem;
import com.dynamsoft.dce.utils.PermissionUtil;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/**
 * Full-screen native camera scanner.
 *
 * The preview comes from the Dynamsoft Camera Enhancer (CameraX under the hood) and every
 * frame is processed by the Dynamsoft Capture Vision native SDK. Nothing here touches WebView
 * camera APIs or any JavaScript SDK.
 */
public class ScannerActivity extends AppCompatActivity {

    private static final String TAG = "ScannerActivity";

    private CameraEnhancer camera;
    private CameraView cameraView;
    private CaptureVisionRouter router;
    private CapturedResultReceiver resultReceiver;

    private TextView statusView;
    private Button captureButton;

    private BarcodeResultItem[] latestItems = new BarcodeResultItem[0];
    private boolean confirmed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scanner);

        cameraView = findViewById(R.id.camera_view);
        statusView = findViewById(R.id.status_text);
        captureButton = findViewById(R.id.btn_capture);
        Button cancelButton = findViewById(R.id.btn_cancel);

        cancelButton.setOnClickListener(v -> finishCanceled());
        captureButton.setOnClickListener(v -> confirm());

        ScannerEngine engine = ScannerEngine.getInstance();
        if (!engine.isLicenseInitialized()) {
            if (!engine.initLicense(getString(R.string.dynamsoft_license))) {
                setResult(RESULT_CANCELED, ScanResultContract.error("Dynamsoft license initialization failed. Check your license key."));
                finish();
                return;
            }
        }

        PermissionUtil.requestCameraPermission(this);

        router = engine.getRouter();
        camera = new CameraEnhancer(cameraView, this);
        try {
            router.setInput(camera);
        } catch (CaptureVisionRouterException e) {
            Log.e(TAG, "Failed to bind camera to Capture Vision", e);
            setResult(RESULT_CANCELED, ScanResultContract.error("Failed to bind camera: " + e.getMessage()));
            finish();
            return;
        }

        resultReceiver = new CapturedResultReceiver() {
            @Override
            public void onDecodedBarcodesReceived(@NonNull DecodedBarcodesResult result) {
                latestItems = result.getItems() == null ? new BarcodeResultItem[0] : result.getItems();
                runOnUiThread(ScannerActivity.this::refreshUi);
            }
        };
        router.addResultReceiver(resultReceiver);
    }

    @Override
    protected void onResume() {
        super.onResume();
        latestItems = new BarcodeResultItem[0];
        confirmed = false;
        try {
            camera.open();
        } catch (Exception e) {
            Log.e(TAG, "Failed to open camera", e);
        }
        router.startCapturing(ScannerEngine.template(), new CompletionListener() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> statusView.setText(R.string.status_scanning));
            }

            @Override
            public void onFailure(int errorCode, String errorString) {
                runOnUiThread(() -> statusView.setText(errorString));
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        router.stopCapturing();
        camera.close();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (router != null && resultReceiver != null) {
            router.removeResultReceiver(resultReceiver);
        }
    }

    private void refreshUi() {
        int count = latestItems.length;
        if (count == 0) {
            statusView.setText(R.string.status_scanning);
            captureButton.setEnabled(false);
            clearOverlay();
            return;
        }
        statusView.setText(getResources().getQuantityString(R.plurals.barcodes_found, count, count));
        captureButton.setEnabled(true);
        drawOverlay();
    }

    private void drawOverlay() {
        DrawingLayer layer = cameraView.getDrawingLayer(DrawingLayer.DBR_LAYER_ID);
        if (layer == null) {
            return;
        }
        List<com.dynamsoft.dce.DrawingItem> items = new ArrayList<>();
        for (BarcodeResultItem item : latestItems) {
            if (item.getLocation() != null) {
                items.add(new QuadDrawingItem(item.getLocation()));
            }
        }
        layer.setDrawingItems(items);
    }

    private void clearOverlay() {
        DrawingLayer layer = cameraView.getDrawingLayer(DrawingLayer.DBR_LAYER_ID);
        if (layer != null) {
            layer.clearDrawingItems();
        }
    }

    private void confirm() {
        if (confirmed) {
            return;
        }
        confirmed = true;
        router.stopCapturing();
        try {
            JSONArray array = BarcodeResultMapper.toJson(latestItems);
            setResult(RESULT_OK, ScanResultContract.ok(array.toString()));
        } catch (Exception e) {
            setResult(RESULT_CANCELED, ScanResultContract.error("Failed to serialize results: " + e.getMessage()));
        }
        finish();
    }

    private void finishCanceled() {
        setResult(RESULT_CANCELED, ScanResultContract.error("canceled"));
        finish();
    }

    @Override
    public void onBackPressed() {
        finishCanceled();
        super.onBackPressed();
    }
}
