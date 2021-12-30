package com.example.qrcodescanner;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.core.ZoomState;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.RectF;
import android.media.Image;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.TextView;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.dynamsoft.dbr.*;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.dnn.DetectionModel;
import org.opencv.dnn.Dnn;
import org.opencv.dnn.Net;
import org.opencv.imgproc.Imgproc;

public class CameraXActivity extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback, AutoTorchController.TorchStatus, ZoomController.ZoomStatus {

    public final static String TAG = "CameraX";
    private final static boolean DEBUG = false;
    private Camera camera;
    private PreviewView previewView;
    private TextView resultView, zoomView, inferenceView, qrDecodingView, resolutionView;
    private BarcodeReader reader;
    private ExecutorService cameraExecutor;
    private AutoTorchController autoTorchController;
    private ZoomController zoomController;
    private Net net;
    private DetectionModel model;
    private ArrayList<String> classes = new ArrayList<>();
    private GraphicOverlay overlay;
    private boolean needUpdateGraphicOverlayImageSourceInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.camerax_main);
        overlay = findViewById(R.id.camerax_overlay);
        previewView = findViewById(R.id.camerax_viewFinder);
        resultView = findViewById(R.id.camerax_result);
        zoomView = findViewById(R.id.camerax_zoom_ratio);
        inferenceView = findViewById(R.id.camerax_inference_time);
        qrDecodingView = findViewById(R.id.camerax_qrdecoding_time);
        resolutionView = findViewById(R.id.camerax_camera_resolution);

        autoTorchController = new AutoTorchController(this);
        autoTorchController.addListener(this);

        zoomController = new ZoomController(this);
        zoomController.addListener(this);

        try {
            reader = new BarcodeReader();
            reader.initLicense("LICENSE-KEY"); // Get a license key from https://www.dynamsoft.com/customer/license/trialLicense?product=dbr
        } catch (BarcodeReaderException e) {
            e.printStackTrace();
        }

        loadOpenCV();
        net = loadYOLOModel();
        model = new DetectionModel(net);
        model.setInputParams(1 / 255.0, new Size(416, 416), new Scalar(0), false);

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
                        needUpdateGraphicOverlayImageSourceInfo = true;
                        analysisUseCase.setAnalyzer(cameraExecutor,
                                imageProxy -> {
//                                    overlay.postInvalidate();
                                    if (needUpdateGraphicOverlayImageSourceInfo) {
                                        int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();
                                        if (rotationDegrees == 0 || rotationDegrees == 180) {
                                            overlay.setImageSourceInfo(
                                                    imageProxy.getWidth(), imageProxy.getHeight(), false);
                                        } else {
                                            overlay.setImageSourceInfo(
                                                    imageProxy.getHeight(), imageProxy.getWidth(), false);
                                        }
                                        needUpdateGraphicOverlayImageSourceInfo = false;
                                        runOnUiThread(()->{
                                            resolutionView.setText("Camera resolution: " + imageProxy.getWidth() + "x" + imageProxy.getHeight());
                                        });
                                    }
                                    // image processing
                                    if (ImageFormat.YUV_420_888 == imageProxy.getFormat()) {
                                        byte[] yBytes = new byte[imageProxy.getPlanes()[0].getBuffer().remaining()];
                                        Mat yuv = ImageUtils.imageToMat(imageProxy, yBytes);
                                        Mat rgbOut = new Mat(imageProxy.getHeight(), imageProxy.getWidth(), CvType.CV_8UC3);
                                        Imgproc.cvtColor(yuv, rgbOut, Imgproc.COLOR_YUV2RGB_I420);
                                        Mat rgb = new Mat();
                                        Core.rotate(rgbOut, rgb, Core.ROTATE_90_CLOCKWISE);

                                        // https://lindevs.com/yolov4-object-detection-using-opencv/
                                        MatOfInt classIds = new MatOfInt();
                                        MatOfFloat scores = new MatOfFloat();
                                        MatOfRect boxes = new MatOfRect();
                                        long start = System.currentTimeMillis();
                                        model.detect(rgb, classIds, scores, boxes, 0.6f, 0.4f);
                                        final long timeCost = System.currentTimeMillis() - start;

                                        overlay.clear();
                                        if (classIds.rows() > 0) {

                                            for (int i = 0; i < classIds.rows(); i++) {
                                                Rect box = new Rect(boxes.get(i, 0));
                                                Imgproc.rectangle(rgb, box, new Scalar(0, 255, 0), 2);

                                                int classId = (int) classIds.get(i, 0)[0];
                                                double score = scores.get(i, 0)[0];
                                                String text = String.format("%s: %.2f", classes.get(classId), score);
                                                Imgproc.putText(rgb, text, new org.opencv.core.Point(box.x, box.y - 5),
                                                        Imgproc.FONT_HERSHEY_SIMPLEX, 1, new Scalar(0, 255, 0), 2);
                                                RectF rect = new RectF();
                                                rect.left = box.x;
                                                rect.right = box.x + box.width;
                                                rect.top = box.y;
                                                rect.bottom = box.y + box.height;

                                                // Decode QR code
                                                TextResult[] results = null;
                                                int nRowStride = imageProxy.getPlanes()[0].getRowStride();
                                                int nPixelStride = imageProxy.getPlanes()[0].getPixelStride();
                                                try {
                                                    PublicRuntimeSettings settings = reader.getRuntimeSettings();
                                                    settings.barcodeFormatIds = EnumBarcodeFormat.BF_QR_CODE;
                                                    settings.expectedBarcodesCount = 1;
                                                    reader.updateRuntimeSettings(settings);
                                                } catch (BarcodeReaderException e) {
                                                    e.printStackTrace();
                                                }

                                                start = System.currentTimeMillis();
                                                try {
                                                    results = reader.decodeBuffer(yBytes, imageProxy.getWidth(), imageProxy.getHeight(), nRowStride * nPixelStride, EnumImagePixelFormat.IPF_NV21, "");
                                                } catch (BarcodeReaderException e) {
                                                    e.printStackTrace();
                                                }
                                                final long decodingTime = System.currentTimeMillis() - start;
                                                runOnUiThread(()->{
                                                    inferenceView.setText("YOLO inference time: " + timeCost + " ms");
                                                    qrDecodingView.setText("QR decoding time: " + decodingTime + " ms");
                                                });
                                                String output = "No barcode found!";
                                                TextResult result =  null;
                                                if (results != null && results.length > 0) {
                                                    output = "Found " + results.length + " barcodes.\n\n";
                                                    for (int index = 0; i < results.length; i++) {
                                                        result = results[i];
                                                        output += "Index: " + index + "\n";
                                                        output += "Format: " + result.barcodeFormatString + "\n";
                                                        output += "Text: " + result.barcodeText + "\n\n";
                                                    }
                                                }

//                                                final String result = output;
//                                                runOnUiThread(()->{resultView.setText(result);});

                                                overlay.add(new BarcodeGraphic(overlay, rect, result));
                                            }

                                        }
                                        overlay.postInvalidate();

                                        //  Check image display
                                        if (DEBUG) {
                                            ImageUtils.saveRGBMat(rgb);
                                        }

//                                        {
//                                            TextResult[] results = null;
//                                            int nRowStride = imageProxy.getPlanes()[0].getRowStride();
//                                            int nPixelStride = imageProxy.getPlanes()[0].getPixelStride();
//                                            try {
//                                                results = reader.decodeBuffer(yBytes, imageProxy.getWidth(), imageProxy.getHeight(), nRowStride * nPixelStride, EnumImagePixelFormat.IPF_NV21, "");
//                                            } catch (BarcodeReaderException e) {
//                                                e.printStackTrace();
//                                            }
//                                            String output = "No barcode found!";
//                                            if (results != null && results.length > 0) {
//                                                output = "Found " + results.length + " barcodes.\n\n";
//                                                for (int i = 0; i < results.length; i++) {
//                                                    output += "Index: " + i + "\n";
//                                                    output += "Format: " + results[i].barcodeFormatString + "\n";
//                                                    output += "Text: " + results[i].barcodeText + "\n\n";
//                                                }
//                                            }
//
//                                            final String result = output;
//                                            runOnUiThread(()->{resultView.setText(result);});
//                                        }
                                    }

                                    // Must call close to keep receiving frames.
                                    imageProxy.close();
                                });

                        // Select back camera as a default
                        CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                        // Unbind use cases before rebinding
                        cameraProvider.unbindAll();

                        // Bind use cases to camera
                        camera = cameraProvider.bindToLifecycle(/* lifecycleOwner= */ this, cameraSelector, previewUseCase, analysisUseCase);

                        // Get zoom ratio
                        LiveData<ZoomState> zoomstate =  camera.getCameraInfo().getZoomState();
                        zoomController.initZoomRatio(zoomstate.getValue().getMinZoomRatio(), zoomstate.getValue().getMaxZoomRatio());
                        updateZoomRatio(zoomstate.getValue().getMinZoomRatio());
                    } catch (ExecutionException | InterruptedException e) {
                        // Handle any errors (including cancellation) here.
                        Log.e(TAG, "Unhandled exception", e);
                    }
                },
                ContextCompat.getMainExecutor(getApplication()));
    }

    private void updateZoomRatio(float ratio) {
        zoomView.setText("Zoom ratio: " + ratio);
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

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        zoomController.onTouchEvent(event);
        return super.onTouchEvent(event);
    }

    @Override
    public void onZoomChange(float ratio) {
        if (camera != null) {
            camera.getCameraControl().setZoomRatio(ratio);
        }
        updateZoomRatio(ratio);
    }

    private void loadOpenCV() {
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    private Net loadYOLOModel() {
        InputStream inputStream = null;
        MatOfByte cfg = new MatOfByte(), weights = new MatOfByte();

        // Load class names
        try {
            inputStream = this.getAssets().open("obj.names");
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                try {

                    String line;
                    while ((line = reader.readLine()) != null) {
                        classes.add(line);
                        Log.i(TAG, "Class name: " + line);
                    }
                } finally {
                    reader.close();
                }
            } finally {
                inputStream.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Load cfg
        try {
            inputStream = new BufferedInputStream(this.getAssets().open("yolov4-tiny-custom-416.cfg"));
            byte[] data = new byte[inputStream.available()];
            inputStream.read(data);
            inputStream.close();
            cfg.fromArray(data);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Load weights
        try {
            inputStream = new BufferedInputStream(this.getAssets().open("yolov4-tiny-custom-416_last.weights"));
            byte[] data = new byte[inputStream.available()];
            inputStream.read(data);
            inputStream.close();
            weights.fromArray(data);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return Dnn.readNetFromDarknet(cfg, weights);
    }
}