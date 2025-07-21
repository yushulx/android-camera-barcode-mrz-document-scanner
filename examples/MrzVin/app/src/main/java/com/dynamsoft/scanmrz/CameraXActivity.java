package com.dynamsoft.scanmrz;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.dynamsoft.core.basic_structures.CapturedResultItem;
import com.dynamsoft.cvr.CaptureVisionRouter;
import com.dynamsoft.cvr.CapturedResult;
import com.dynamsoft.dcp.ParsedResultItem;
import com.dynamsoft.license.LicenseManager;
import com.dynamsoft.mrzscannerbundle.ui.EnumDetectionType;
import com.dynamsoft.mrzscannerbundle.ui.ScannerConfig;
import com.google.common.util.concurrent.ListenableFuture;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraXActivity extends AppCompatActivity {
    private static final String TAG = "CameraXActivity";
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS = {Manifest.permission.CAMERA};

    private PreviewView previewView;
    private ExecutorService cameraExecutor;
    private CaptureVisionRouter mRouter;
    private ScannerConfig configuration;
    private String mCurrentTemplate = "ReadPassportAndId";
    private boolean isProcessing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camerax);

        previewView = findViewById(R.id.preview_view);

        // Get configuration from intent
        configuration = (ScannerConfig) getIntent().getSerializableExtra("scanner_config");

        if (configuration != null && configuration.getLicense() != null) {
            LicenseManager.initLicense(configuration.getLicense(), this, (isSuccess, error) -> {
                if (!isSuccess) {
                    error.printStackTrace();
                }
            });
        }

        // Initialize CaptureVisionRouter
        initializeCVR();

        // Setup close button
        ImageView closeButton = findViewById(R.id.iv_close);
        closeButton.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        cameraExecutor = Executors.newSingleThreadExecutor();

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }
    }

    private void initializeCVR() {
        mRouter = new CaptureVisionRouter();
        try {
            if (configuration != null) {
                switch (configuration.getDetectionType()) {
                    case MRZ:
                        mCurrentTemplate = "ReadPassportAndId";
                        break;
                    case VIN:
                        mCurrentTemplate = "ReadVINText";
                        break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis imageAnalyzer = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalyzer.setAnalyzer(cameraExecutor, new MRZAnalyzer());

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                try {
                    cameraProvider.unbindAll();
                    cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer);
                } catch (Exception exc) {
                    Log.e(TAG, "Use case binding failed", exc);
                }

            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Error starting camera", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private class MRZAnalyzer implements ImageAnalysis.Analyzer {
        @Override
        public void analyze(@NonNull ImageProxy imageProxy) {
            if (isProcessing) {
                imageProxy.close();
                return;
            }

            isProcessing = true;

            try {
                // Convert ImageProxy to Bitmap
                Bitmap bitmap = imageProxyToBitmap(imageProxy);
                if (bitmap != null) {
                    // Process with Dynamsoft SDK
                    CapturedResult capturedResult = mRouter.capture(bitmap, mCurrentTemplate);
                    CapturedResultItem[] items = capturedResult.getItems();

                    for (CapturedResultItem item : items) {
                        if (item instanceof ParsedResultItem) {
                            ParsedResultItem parsedItem = (ParsedResultItem) item;
                            if (isValidMRZResult(parsedItem)) {
                                runOnUiThread(() -> {
                                    returnResult(parsedItem);
                                });
                                return;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error processing frame", e);
            } finally {
                isProcessing = false;
                imageProxy.close();
            }
        }
    }

    private Bitmap imageProxyToBitmap(ImageProxy imageProxy) {
        try {
            ImageProxy.PlaneProxy[] planes = imageProxy.getPlanes();
            ByteBuffer yBuffer = planes[0].getBuffer();
            ByteBuffer uBuffer = planes[1].getBuffer();
            ByteBuffer vBuffer = planes[2].getBuffer();

            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();

            byte[] nv21 = new byte[ySize + uSize + vSize];

            yBuffer.get(nv21, 0, ySize);
            vBuffer.get(nv21, ySize, vSize);
            uBuffer.get(nv21, ySize + vSize, uSize);

            android.graphics.YuvImage yuvImage = new android.graphics.YuvImage(nv21, android.graphics.ImageFormat.NV21, imageProxy.getWidth(), imageProxy.getHeight(), null);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            yuvImage.compressToJpeg(new android.graphics.Rect(0, 0, imageProxy.getWidth(), imageProxy.getHeight()), 50, out);
            byte[] imageBytes = out.toByteArray();
            Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);

            // Rotate bitmap 90 degrees clockwise for portrait mode
            if (bitmap != null) {
                android.graphics.Matrix matrix = new android.graphics.Matrix();
                matrix.postRotate(90);
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            }

            return bitmap;
        } catch (Exception e) {
            Log.e(TAG, "Error converting ImageProxy to Bitmap", e);
            return null;
        }
    }

    private boolean isValidMRZResult(ParsedResultItem item) {
        if (configuration != null && configuration.getDetectionType() == EnumDetectionType.VIN) {
            return item.getCodeType().equals("VIN");
        } else {
            // MRZ validation
            java.util.HashMap<String, String> entry = item.getParsedFields();
            return entry.get("sex") != null &&
                   entry.get("issuingState") != null &&
                   entry.get("nationality") != null &&
                   entry.get("dateOfBirth") != null &&
                   entry.get("dateOfExpiry") != null;
        }
    }

    private void returnResult(ParsedResultItem item) {
        android.content.Intent intent = new android.content.Intent();
        intent.putExtra("status_code", 1); // Success
        intent.putExtra("doc_type", item.getCodeType());

        java.util.HashMap<String, String> entry = item.getParsedFields();

        if (item.getCodeType().equals("VIN")) {
            // Handle VIN results - ensure correct field names match VINData.extractItem expectations
            java.util.HashMap<String, String> vinData = new java.util.HashMap<>();
            vinData.put("vinString", entry.get("vinString"));
            vinData.put("wmi", entry.get("WMI")); // Uppercase as expected by VINData
            vinData.put("region", entry.get("region"));
            vinData.put("vds", entry.get("VDS")); // Uppercase as expected by VINData
            vinData.put("checkDigit", entry.get("checkDigit"));
            vinData.put("modelYear", entry.get("modelYear"));
            vinData.put("plantCode", entry.get("plantCode"));
            vinData.put("serialNumber", entry.get("serialNumber"));

            intent.putExtra("result", vinData);
        } else {
            // Handle MRZ results - format to match ScannerActivity output
            intent.putExtra("nationality", item.getFieldRawValue("nationality"));
            intent.putExtra("issuing_state", item.getFieldRawValue("issuingState"));

            String number = entry.get("passportNumber") != null ? entry.get("passportNumber") :
                           entry.get("documentNumber") != null ? entry.get("documentNumber") :
                           entry.get("longDocumentNumber");
            intent.putExtra("number", number);

            // Create properly formatted MRZ result data
            java.util.HashMap<String, String> resultData = new java.util.HashMap<>(entry);

            // Extract and set firstName and lastName from the parsed fields
            String primaryIdentifier = entry.get("primaryIdentifier");
            String secondaryIdentifier = entry.get("secondaryIdentifier");

            if (primaryIdentifier != null) {
                resultData.put("lastName", primaryIdentifier);
            }
            if (secondaryIdentifier != null) {
                // Secondary identifier contains all given names
                resultData.put("firstName", secondaryIdentifier.trim());
            }

            // Calculate age from date of birth if not directly available
            String dateOfBirth = entry.get("dateOfBirth");
            if (dateOfBirth != null && !dateOfBirth.isEmpty() && entry.get("age") == null) {
                try {
                    // Date format is typically YYMMDD
                    if (dateOfBirth.length() >= 6) {
                        int birthYear = Integer.parseInt(dateOfBirth.substring(0, 2));
                        int birthMonth = Integer.parseInt(dateOfBirth.substring(2, 4));
                        int birthDay = Integer.parseInt(dateOfBirth.substring(4, 6));

                        // Convert 2-digit year to 4-digit year
                        if (birthYear <= 30) {
                            birthYear += 2000;
                        } else {
                            birthYear += 1900;
                        }

                        // Calculate age properly considering current date
                        java.util.Calendar birthDate = java.util.Calendar.getInstance();
                        birthDate.set(birthYear, birthMonth - 1, birthDay);

                        java.util.Calendar currentDate = java.util.Calendar.getInstance();

                        int age = currentDate.get(java.util.Calendar.YEAR) - birthDate.get(java.util.Calendar.YEAR);

                        // Adjust age if birthday hasn't occurred this year yet
                        if (currentDate.get(java.util.Calendar.DAY_OF_YEAR) < birthDate.get(java.util.Calendar.DAY_OF_YEAR)) {
                            age--;
                        }

                        resultData.put("age", String.valueOf(age));
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error calculating age from date: " + dateOfBirth, e);
                }
            }

            // Ensure documentType is available
            if (entry.get("documentType") == null) {
                resultData.put("documentType", item.getCodeType());
            }

            intent.putExtra("result", resultData);
        }

        setResult(RESULT_OK, intent);
        finish();
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
    }
}
