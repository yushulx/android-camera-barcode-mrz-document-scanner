package com.example.qrcodescanner;

import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Bundle;
import android.view.MotionEvent;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.dynamsoft.dbr.BarcodeReaderException;
import com.dynamsoft.dbr.TextResult;
import com.dynamsoft.dce.*;

import com.dynamsoft.dbr.*;

public class DceActivity extends AppCompatActivity implements DCEFrameListener, AutoTorchController.TorchStatus, ZoomController.ZoomStatus {
    public final static String TAG = "DCE";
    private DCECameraView previewView;
    private TextView resultView, zoomView, qrDecodingView, resolutionView;
    private CameraEnhancer cameraEnhancer;
    private BarcodeReader reader;
    private AutoTorchController autoTorchController;
    private ZoomController zoomController;
    private GraphicOverlay overlay;
    private boolean needUpdateGraphicOverlayImageSourceInfo;
    private boolean isPortrait = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dce_main);
        overlay = findViewById(R.id.dce_overlay);
        previewView = findViewById(R.id.dce_viewFinder);
        resultView = findViewById(R.id.dce_result);
        zoomView = findViewById(R.id.dce_zoom_ratio);
        qrDecodingView = findViewById(R.id.dce_qrdecoding_time);
        resolutionView = findViewById(R.id.dce_camera_resolution);

        autoTorchController = new AutoTorchController(this);
        autoTorchController.addListener(this);

        zoomController = new ZoomController(this);
        zoomController.addListener(this);

        try {
            // Get a license key from https://www.dynamsoft.com/customer/license/trialLicense?product=dbr
            BarcodeReader.initLicense(
            "DLS2eyJoYW5kc2hha2VDb2RlIjoiMjAwMDAxLTE2NDk4Mjk3OTI2MzUiLCJvcmdhbml6YXRpb25JRCI6IjIwMDAwMSIsInNlc3Npb25QYXNzd29yZCI6IndTcGR6Vm05WDJrcEQ5YUoifQ==",
                new DBRLicenseVerificationListener() {
                    @Override
                    public void DBRLicenseVerificationCallback(boolean isSuccessful, Exception e) {
                    }
                });
            reader = new BarcodeReader();
        } catch (BarcodeReaderException e) {
            e.printStackTrace();
        }

        cameraEnhancer = new CameraEnhancer(this);
        cameraEnhancer.setCameraView(previewView);
        try {
            cameraEnhancer.setResolution(EnumResolution.RESOLUTION_480P);
        } catch (CameraEnhancerException e) {
            e.printStackTrace();
        }
        cameraEnhancer.addListener(this);

        zoomController.initZoomRatio(1.0f, 10.f);
        needUpdateGraphicOverlayImageSourceInfo = true;
    }

    @Override
    protected void onResume() {
        super.onResume();

        int orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            isPortrait = true;
        } else {
            isPortrait = false;
        }
        needUpdateGraphicOverlayImageSourceInfo = true;

        try {
            cameraEnhancer.open();
        } catch (CameraEnhancerException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            cameraEnhancer.close();
        } catch (CameraEnhancerException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void frameOutputCallback(DCEFrame dceFrame, long l) {
        if (needUpdateGraphicOverlayImageSourceInfo) {
            if (isPortrait) {
                overlay.setImageSourceInfo(
                        dceFrame.toBitmap().getHeight(), dceFrame.toBitmap().getWidth(), false);
            }
            else {
                overlay.setImageSourceInfo(
                        dceFrame.toBitmap().getWidth(), dceFrame.toBitmap().getHeight(), false);
            }

            needUpdateGraphicOverlayImageSourceInfo = false;
            runOnUiThread(()->{
                resolutionView.setText("Camera resolution: " + dceFrame.toBitmap().getWidth() + "x" + dceFrame.toBitmap().getHeight());
            });
        }
        // image processing
        // Log.i(TAG, "image processing.................");
        TextResult[] results = null;
        // Rotate 90 degree to get correct bounding box
//        Matrix matrix = new Matrix();
//        matrix.postRotate(90);
//        Bitmap rotatedBitmap = Bitmap.createBitmap(dceFrame.toBitmap(), 0, 0, dceFrame.toBitmap().getWidth(), dceFrame.toBitmap().getHeight(), matrix, true);

        try {
            PublicRuntimeSettings settings = reader.getRuntimeSettings();
            settings.barcodeFormatIds = EnumBarcodeFormat.BF_QR_CODE;
            reader.updateRuntimeSettings(settings);
        } catch (BarcodeReaderException e) {
            e.printStackTrace();
        }

        long start = System.currentTimeMillis();
        try {
            results = reader.decodeBufferedImage(dceFrame.toBitmap(), "");
        } catch (BarcodeReaderException e) {
            e.printStackTrace();
        }
        final long decodingTime = System.currentTimeMillis() - start;
        runOnUiThread(()->{
            qrDecodingView.setText("QR decoding time: " + decodingTime + " ms");
        });
        String output = "No barcode found!";
        overlay.clear();
        if (results != null && results.length > 0) {
            output = "Found " + results.length + " barcodes.\n\n";
            for (int i = 0; i < results.length; i++) {
                TextResult result = results[i];
                output += "Index: " + i + "\n";
                output += "Format: " + result.barcodeFormatString + "\n";
                output += "Text: " + result.barcodeText + "\n\n";
                overlay.add(new BarcodeGraphic(overlay, null, result, isPortrait));
            }
            overlay.postInvalidate();
        }

//        final String result = output;
//        runOnUiThread(()->{resultView.setText(result);});
        // image processing
    }

    @Override
    protected void onStart() {
        super.onStart();

        autoTorchController.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
        autoTorchController.onStop();
    }

    @Override
    public void onTorchChange(boolean status) {
        if (status) {
            try {
                cameraEnhancer.turnOnTorch();
            } catch (CameraEnhancerException e) {
                e.printStackTrace();
            }
        }
        else {
            try {
                cameraEnhancer.turnOffTorch();
            } catch (CameraEnhancerException e) {
                e.printStackTrace();
            }
        }
    }

    private void updateZoomRatio(float ratio) {
        zoomView.setText("Zoom ratio: " + ratio);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        zoomController.onTouchEvent(event);
        return super.onTouchEvent(event);
    }

    @Override
    public void onZoomChange(float ratio) {
        try {
            cameraEnhancer.setZoom(ratio);
        } catch (CameraEnhancerException e) {
            e.printStackTrace();
        }
        updateZoomRatio(ratio);
    }
}
