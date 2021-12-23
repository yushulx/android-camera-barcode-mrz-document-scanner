package com.example.qrcodescanner;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.google.common.util.concurrent.ListenableFuture;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.dynamsoft.dbr.*;

public class CameraXActivity extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback, AutoTorchController.TorchStatus {

    public final static String TAG = "CameraX";

    private Camera camera;
    private PreviewView previewView;
    private TextView resultView;
    private BarcodeReader reader;
    private ExecutorService cameraExecutor;
    private AutoTorchController autoTorchController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.camerax_main);
        previewView = findViewById(R.id.camerax_viewFinder);
        resultView = findViewById(R.id.camerax_result);

        autoTorchController = new AutoTorchController(this);
        autoTorchController.addListener(this);
        try {
            reader = new BarcodeReader();
            reader.initLicense("LICENSE-KEY"); // Get a license key from https://www.dynamsoft.com/customer/license/trialLicense?product=dbr
        } catch (BarcodeReaderException e) {
            e.printStackTrace();
        }

        cameraExecutor = Executors.newSingleThreadExecutor();

        if (!CameraUtils.allPermissionsGranted(this)) {
            CameraUtils.getRuntimePermissions(this);
        } else {
            startCamera();
        }
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

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(getApplication());
        cameraProviderFuture.addListener(
                () -> {
                    try {
                        // Used to bind the lifecycle of cameras to the lifecycle owner
                        ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                        // Preview
                        Preview.Builder builder = new Preview.Builder();
                        Preview previewUseCase = builder.build();
                        previewUseCase.setSurfaceProvider(previewView.getSurfaceProvider());

                        // Image
                        ImageAnalysis analysisUseCase = new ImageAnalysis.Builder().build();
                        analysisUseCase.setAnalyzer(cameraExecutor,
                                imageProxy -> {
                                    // image processing
//                                    Log.i(TAG, "image processing.................");
                                    TextResult[] results = null;
                                    ByteBuffer buffer = imageProxy.getPlanes()[0].getBuffer();
                                    int nRowStride = imageProxy.getPlanes()[0].getRowStride();
                                    int nPixelStride = imageProxy.getPlanes()[0].getPixelStride();
                                    int length = buffer.remaining();
                                    byte[] bytes = new byte[length];
                                    buffer.get(bytes);
                                    try {
                                        results = reader.decodeBuffer(bytes, imageProxy.getWidth(), imageProxy.getHeight(), nRowStride * nPixelStride, EnumImagePixelFormat.IPF_NV21, "");
                                    } catch (BarcodeReaderException e) {
                                        e.printStackTrace();
                                    }
                                    String output = "No barcode found!";
                                    if (results != null && results.length > 0) {
                                        output = "Found " + results.length + " barcodes.";
                                        for (int i = 0; i < results.length; i++) {
                                            output += "Index: " + i;
                                            output += "Format: " + results[i].barcodeFormatString + "\n";
                                            output += "Text: " + results[i].barcodeText + "\n\n";
                                        }
                                    }

                                    final String result = output;
                                    runOnUiThread(()->{resultView.setText(result);});
                                    // image processing

                                    // Must call close to keep receiving frames.
                                    imageProxy.close();
                                });

                        // Select back camera as a default
                        CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                        // Unbind use cases before rebinding
                        cameraProvider.unbindAll();

                        // Bind use cases to camera
                        camera = cameraProvider.bindToLifecycle(/* lifecycleOwner= */ this, cameraSelector, previewUseCase, analysisUseCase);
                    } catch (ExecutionException | InterruptedException e) {
                        // Handle any errors (including cancellation) here.
                        Log.e(TAG, "Unhandled exception", e);
                    }
                },
                ContextCompat.getMainExecutor(getApplication()));
    }



    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        Log.i(TAG, "Permission granted!");
        if (CameraUtils.allPermissionsGranted(this)) {
            startCamera();
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }

    @Override
    public void onTorchChange(boolean status) {
        if (camera != null) camera.getCameraControl().enableTorch(status);
    }
}