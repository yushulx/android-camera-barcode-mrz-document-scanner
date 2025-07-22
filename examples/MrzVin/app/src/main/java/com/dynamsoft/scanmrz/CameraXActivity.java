package com.dynamsoft.scanmrz;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Base64;
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
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import android.util.Size;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.dynamsoft.core.basic_structures.CapturedResultItem;
import com.dynamsoft.cvr.CaptureVisionRouter;
import com.dynamsoft.cvr.CapturedResult;
import com.dynamsoft.cvr.EnumPresetTemplate;
import com.dynamsoft.dcp.ParsedResultItem;
import com.dynamsoft.ddn.DeskewedImageResultItem;
import com.dynamsoft.license.LicenseManager;
import com.dynamsoft.mrzscannerbundle.ui.EnumDetectionType;
import com.dynamsoft.mrzscannerbundle.ui.ScannerConfig;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

// Removed OpenCV imports - using Android built-in APIs for document detection
import java.util.ArrayList;
import java.util.List;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.List;
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
    private Bitmap mLastProcessedBitmap; // Store the last bitmap for face detection
    private Bitmap mLastDocumentBitmap; // Store the last cropped document

    // Result validation fields
    private static final int VALIDATION_FRAME_COUNT = 5;
    private static final int REQUIRED_MATCHES = 2;
    private java.util.List<String> recentResults = new java.util.ArrayList<>();
    private java.util.Map<String, Integer> resultCounts = new java.util.HashMap<>();

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

    @Override
    public void onResume() {
        super.onResume();
        // Document detection will use Android's built-in image processing
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
                
                // Create a ResolutionSelector with target resolution 1920x1080
                ResolutionSelector resolutionSelector = new ResolutionSelector.Builder()
                        .setResolutionStrategy(new ResolutionStrategy(
                                new Size(1920, 1080),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                        .build();
                
                ImageAnalysis imageAnalyzer = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        // Set target resolution to 1920x1080 for better image quality
                        .setResolutionSelector(resolutionSelector)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
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
                    // Store the bitmap for potential face detection
                    mLastProcessedBitmap = bitmap;

                    // Detect and crop document using Android APIs
            mLastDocumentBitmap = detectAndCropDocument(bitmap);

                    // Process with Dynamsoft SDK
                    CapturedResult capturedResult = mRouter.capture(bitmap, mCurrentTemplate);
                    CapturedResultItem[] items = capturedResult.getItems();

                    for (CapturedResultItem item : items) {
                        if (item instanceof ParsedResultItem) {
                            ParsedResultItem parsedItem = (ParsedResultItem) item;
                            if (isValidMRZResult(parsedItem)) {
                                // Use validation strategy instead of immediate return
                                if (shouldReturnResult(parsedItem)) {
                                    runOnUiThread(() -> {
                                        returnResult(parsedItem);
                                    });
                                    return;
                                }
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
            yuvImage.compressToJpeg(new android.graphics.Rect(0, 0, imageProxy.getWidth(), imageProxy.getHeight()), 100, out);
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

    private boolean shouldReturnResult(ParsedResultItem item) {
        // Create a unique identifier for this MRZ result based on key fields
        String resultKey = createResultKey(item);

        // Add this result to our recent results list
        recentResults.add(resultKey);

        // Update the count for this result
        Integer currentCount = resultCounts.get(resultKey);
        resultCounts.put(resultKey, (currentCount != null ? currentCount : 0) + 1);

        // Keep only the last VALIDATION_FRAME_COUNT results
        if (recentResults.size() > VALIDATION_FRAME_COUNT) {
            String removedResult = recentResults.remove(0);
            int count = resultCounts.get(removedResult);
            if (count <= 1) {
                resultCounts.remove(removedResult);
            } else {
                resultCounts.put(removedResult, count - 1);
            }
        }

        // Check if we have at least REQUIRED_MATCHES of the same result
        int finalCount = (currentCount != null ? currentCount : 0);
        boolean shouldReturn = finalCount >= REQUIRED_MATCHES;

        if (shouldReturn) {
            Log.d(TAG, "MRZ validation passed: " + finalCount + " matches out of " + recentResults.size() + " frames");
        } else {
            Log.d(TAG, "MRZ validation pending: " + finalCount + " matches, need " + REQUIRED_MATCHES);
        }

        return shouldReturn;
    }

    private String createResultKey(ParsedResultItem item) {
        // Create a unique key based on critical MRZ fields that should remain consistent
        java.util.HashMap<String, String> entry = item.getParsedFields();
        StringBuilder keyBuilder = new StringBuilder();

        // Use document number as primary identifier
        String documentNumber = entry.get("passportNumber") != null ? entry.get("passportNumber") :
                               entry.get("documentNumber") != null ? entry.get("documentNumber") :
                               entry.get("longDocumentNumber");
        if (documentNumber != null) {
            keyBuilder.append(documentNumber).append("|");
        }

        // Add other critical fields
        keyBuilder.append(entry.get("dateOfBirth")).append("|");
        keyBuilder.append(entry.get("dateOfExpiry")).append("|");
        keyBuilder.append(entry.get("nationality")).append("|");
        keyBuilder.append(entry.get("issuingState"));

        return keyBuilder.toString();
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
            setResult(RESULT_OK, intent);
            finish();
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

            // Add document image if available
            if (mLastDocumentBitmap != null) {
                String documentImagePath = saveBitmapToCache(mLastDocumentBitmap, "document");
                intent.putExtra("document_image_path", documentImagePath);
                Log.d(TAG, "Document image saved to: " + documentImagePath);
            }

            // For MRZ results, try to detect and crop face from the current frame
            detectAndCropFace(intent);
        }
    }

    private void detectAndCropFace(android.content.Intent intent) {
        try {
            // Get the current frame bitmap for face detection
            Bitmap currentBitmap = getCurrentFrameBitmap();
            if (currentBitmap == null) {
                // No face image available, proceed with normal result
                setResult(RESULT_OK, intent);
                finish();
                return;
            }

            // Configure face detector for faster detection
            FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                    .setMinFaceSize(0.05f)
                    .enableTracking()
                    .build();

            FaceDetector detector = FaceDetection.getClient(options);
            InputImage image = InputImage.fromBitmap(currentBitmap, 0);

            detector.process(image)
                    .addOnSuccessListener(new OnSuccessListener<List<Face>>() {
                        @Override
                        public void onSuccess(List<Face> faces) {
                            if (!faces.isEmpty()) {
                                // Get the largest face (most likely the main subject)
                                Face largestFace = getLargestFace(faces);
                                Bitmap croppedFace = cropFaceFromBitmap(currentBitmap, largestFace);

                                if (croppedFace != null) {
                                    // Save cropped face to cache file and pass the file path
                                    String faceImagePath = saveBitmapToCache(croppedFace, "face");
                                    intent.putExtra("face_image_path", faceImagePath);
                                    Log.d(TAG, "Face detected and saved to: " + faceImagePath);
                                }
                            }

                            setResult(RESULT_OK, intent);
                            finish();
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Log.e(TAG, "Face detection failed: " + e.getMessage());
                            // Proceed without face image
                            setResult(RESULT_OK, intent);
                            finish();
                        }
                    });

        } catch (Exception e) {
            Log.e(TAG, "Error in face detection: " + e.getMessage());
            // Proceed without face image
            setResult(RESULT_OK, intent);
            finish();
        }
    }

    private Bitmap getCurrentFrameBitmap() {
        // For now, we'll use the last processed bitmap
        // You might want to store the bitmap from the last successful MRZ detection
        return mLastProcessedBitmap;
    }

    private Face getLargestFace(List<Face> faces) {
        Face largestFace = faces.get(0);
        float largestArea = largestFace.getBoundingBox().width() * largestFace.getBoundingBox().height();

        for (Face face : faces) {
            Rect boundingBox = face.getBoundingBox();
            float area = boundingBox.width() * boundingBox.height();
            if (area > largestArea) {
                largestArea = area;
                largestFace = face;
            }
        }

        return largestFace;
    }

    private Bitmap cropFaceFromBitmap(Bitmap originalBitmap, Face face) {
        try {
            Rect boundingBox = face.getBoundingBox();

            // Add some padding around the face
            int padding = Math.min(boundingBox.width(), boundingBox.height()) / 4;
            int left = Math.max(0, boundingBox.left - padding);
            int top = Math.max(0, boundingBox.top - padding);
            int right = Math.min(originalBitmap.getWidth(), boundingBox.right + padding);
            int bottom = Math.min(originalBitmap.getHeight(), boundingBox.bottom + padding);

            int width = right - left;
            int height = bottom - top;

            if (width > 0 && height > 0) {
                return Bitmap.createBitmap(originalBitmap, left, top, width, height);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error cropping face: " + e.getMessage());
        }

        return null;
    }

    private Bitmap detectAndCropDocument(Bitmap originalBitmap) {
        try {
            CapturedResult capturedResult = mRouter.capture(originalBitmap, EnumPresetTemplate.PT_DETECT_AND_NORMALIZE_DOCUMENT);
            CapturedResultItem[] items = capturedResult.getItems();

            for (CapturedResultItem item : items) {
                if (item instanceof DeskewedImageResultItem) {
                    DeskewedImageResultItem deskewedImageResultItem = (DeskewedImageResultItem) item;
                    return deskewedImageResultItem.getImageData().toBitmap();
                }
            }
            Log.d(TAG, "Document detection using Android APIs not yet implemented");
            return originalBitmap;
        } catch (Exception e) {
            Log.e(TAG, "Error in document detection: " + e.getMessage());
            return originalBitmap;
        }
    }

    // Removed sortCorners method as it's no longer needed without OpenCV

    private String saveBitmapToCache(Bitmap bitmap, String prefix) {
        try {
            // Create a file in the cache directory
            File cacheDir = getApplicationContext().getCacheDir();
            File imageFile = new File(cacheDir, prefix + "_" + System.currentTimeMillis() + ".jpg");
            
            // Save the bitmap to the file
            FileOutputStream fos = new FileOutputStream(imageFile);
            // Compress with high quality to avoid artifacts
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            fos.flush();
            fos.close();
            
            // Return the file path
            return imageFile.getAbsolutePath();
        } catch (Exception e) {
            Log.e(TAG, "Error saving bitmap to cache: " + e.getMessage());
            return null;
        }
    }
    
    // Keep this method for backward compatibility if needed
    private String bitmapToBase64(Bitmap bitmap) {
        try {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            // Increase quality from 80 to 100 to avoid compression artifacts
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream);
            byte[] byteArray = byteArrayOutputStream.toByteArray();
            return Base64.encodeToString(byteArray, Base64.DEFAULT);
        } catch (Exception e) {
            Log.e(TAG, "Error converting bitmap to Base64: " + e.getMessage());
            return null;
        }
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
