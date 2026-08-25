package com.google.mlkit.vision.demo.java;

import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.widget.TextView;
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
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.dynamsoft.dbr.BarcodeResultItem;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.Barcode;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.demo.GraphicOverlay;
import com.google.mlkit.vision.demo.R;
import com.google.mlkit.vision.demo.java.dynamsoftbarcodescanner.DynamsoftBarcodeProcessor;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Live camera comparison mode: every analysis frame is fed to both the Dynamsoft Barcode Reader
 * bundle and Google ML Kit, and the status panel shows each engine's result and latency in real
 * time, making it easy to compare them on high-density QR codes.
 */
public final class CameraLiveActivity extends AppCompatActivity {

  private static final String TAG = "CameraLiveActivity";
  private static final int REQUEST_CAMERA_PERMISSION = 3001;
  private static final int MAX_TEXT_PREVIEW_CHARS = 120;

  private PreviewView previewView;
  private GraphicOverlay graphicOverlay;
  private TextView dynamsoftResultView;
  private TextView mlkitResultView;

  private DynamsoftBarcodeProcessor dynamsoftProcessor;
  private BarcodeScanner mlkitScanner;

  private final ExecutorService analysisExecutor = Executors.newSingleThreadExecutor();
  private final AtomicBoolean dynamsoftBusy = new AtomicBoolean(false);
  private final AtomicBoolean mlkitBusy = new AtomicBoolean(false);

  private long lastDynamsoftMs = -1;
  private long lastMlkitMs = -1;
  private String lastDynamsoftText = "";
  private String lastMlkitText = "";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_camera_live);

    previewView = findViewById(R.id.preview_view);
    graphicOverlay = findViewById(R.id.graphic_overlay);
    dynamsoftResultView = findViewById(R.id.dynamsoft_result);
    mlkitResultView = findViewById(R.id.mlkit_result);

    // The preview is rendered with the same fit-center semantics as the still-image
    // preview, so the overlay can use a single fit-center transform for both modes and
    // the contour stays aligned with the barcode on the screen.
    previewView.setScaleType(PreviewView.ScaleType.FIT_CENTER);

    findViewById(R.id.back_button).setOnClickListener(v -> finish());

    dynamsoftProcessor = new DynamsoftBarcodeProcessor(this);
    dynamsoftProcessor.setDecodeListener(
        new DynamsoftBarcodeProcessor.DecodeListener() {
          @Override
          public void onDecoded(BarcodeResultItem[] items, long elapsedMs) {
            lastDynamsoftMs = elapsedMs;
            lastDynamsoftText = summarizeDynamsoft(items);
            dynamsoftBusy.set(false);
            refreshResultPanel();
          }

          @Override
          public void onError(String message) {
            lastDynamsoftMs = -1;
            lastDynamsoftText = "error: " + message;
            dynamsoftBusy.set(false);
            refreshResultPanel();
          }
        });

    mlkitScanner = BarcodeScanning.getClient();

    if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
        != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(
          this, new String[] {android.Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
    } else {
      startCamera();
    }
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    if (requestCode == REQUEST_CAMERA_PERMISSION) {
      if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        startCamera();
      } else {
        Toast.makeText(this, "Camera permission is required for live scanning.", Toast.LENGTH_LONG)
            .show();
        finish();
      }
      return;
    }
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
  }

  private void startCamera() {
    ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
        ProcessCameraProvider.getInstance(this);
    cameraProviderFuture.addListener(
        () -> {
          try {
            ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
            Preview preview = new Preview.Builder().build();
            preview.setSurfaceProvider(previewView.getSurfaceProvider());

            ImageAnalysis imageAnalysis =
                new ImageAnalysis.Builder()
                    .setResolutionSelector(
                        new ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                new ResolutionStrategy(
                                    new Size(1440, 1080),
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                            .build())
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build();
            imageAnalysis.setAnalyzer(analysisExecutor, this::analyzeFrame);

            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(
                this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis);
          } catch (Exception e) {
            Log.e(TAG, "Failed to start camera", e);
            Toast.makeText(this, "Camera start failed: " + e.getMessage(), Toast.LENGTH_LONG)
                .show();
            finish();
          }
        },
        ContextCompat.getMainExecutor(this));
  }

  private void analyzeFrame(ImageProxy imageProxy) {
    // Only process a new frame when both engines are idle; frames dropped in between are
    // skipped by STRATEGY_KEEP_ONLY_LATEST.
    if (dynamsoftBusy.get() || mlkitBusy.get()) {
      imageProxy.close();
      return;
    }
    try {
      int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();
      Bitmap bitmap = rotateBitmap(imageProxy.toBitmap(), rotationDegrees);
      if (bitmap == null) {
        imageProxy.close();
        return;
      }

      // The preview is displayed with fit-center semantics (see setScaleType above), so the
      // overlay uses its default fit-center transform. The overlay is not cleared here:
      // DynamsoftBarcodeProcessor clears and redraws it when this frame's decoding finishes,
      // which keeps the latest detection visible on the preview.
      graphicOverlay.setImageSourceInfo(bitmap.getWidth(), bitmap.getHeight(), /* isFlipped= */ false);

      dynamsoftBusy.set(true);
      dynamsoftProcessor.processBitmap(bitmap, graphicOverlay);

      if (imageProxy.getImage() != null) {
        mlkitBusy.set(true);
        long frameStartMs = SystemClock.elapsedRealtime();
        mlkitScanner
            .process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener(
                ContextCompat.getMainExecutor(this),
                barcodes -> {
                  lastMlkitMs = SystemClock.elapsedRealtime() - frameStartMs;
                  lastMlkitText = summarizeMlKit(barcodes);
                  mlkitBusy.set(false);
                  refreshResultPanel();
                })
            .addOnFailureListener(
                ContextCompat.getMainExecutor(this),
                e -> {
                  lastMlkitMs = -1;
                  lastMlkitText = "error: " + e.getMessage();
                  mlkitBusy.set(false);
                  refreshResultPanel();
                })
            .addOnCompleteListener(task -> imageProxy.close());
      } else {
        imageProxy.close();
      }
    } catch (Exception e) {
      Log.e(TAG, "Failed to analyze frame", e);
      dynamsoftBusy.set(false);
      mlkitBusy.set(false);
      imageProxy.close();
    }
  }

  /** Rotates the frame into the natural display orientation so decoding coordinates match the preview. */
  private static Bitmap rotateBitmap(Bitmap bitmap, int rotationDegrees) {
    if (bitmap == null || rotationDegrees == 0) {
      return bitmap;
    }
    Matrix matrix = new Matrix();
    matrix.postRotate(rotationDegrees);
    return Bitmap.createBitmap(
        bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, /* filter= */ true);
  }

  private void refreshResultPanel() {
    int colorSuccess = 0xFF0B7D00;
    int colorFailure = 0xFFB00020;
    boolean dynamsoftOk = lastDynamsoftMs >= 0 && !lastDynamsoftText.contains("no barcode");
    boolean mlkitOk = lastMlkitMs >= 0 && !lastMlkitText.contains("no barcode");

    String dynamsoftLine =
        lastDynamsoftMs < 0
            ? "Dynamsoft: \u2717 " + lastDynamsoftText
            : String.format("Dynamsoft: %s (%d ms)", lastDynamsoftText, lastDynamsoftMs);
    String mlkitLine =
        lastMlkitMs < 0
            ? "ML Kit: \u2717 " + lastMlkitText
            : String.format("ML Kit: %s (%d ms)", lastMlkitText, lastMlkitMs);
    dynamsoftResultView.setTextColor(dynamsoftOk ? colorSuccess : colorFailure);
    mlkitResultView.setTextColor(mlkitOk ? colorSuccess : colorFailure);
    dynamsoftResultView.setText(dynamsoftLine);
    mlkitResultView.setText(mlkitLine);
  }

  private static String summarizeDynamsoft(BarcodeResultItem[] items) {
    if (items == null || items.length == 0) {
      return "\u2717 no barcode";
    }
    StringBuilder sb = new StringBuilder("\u2713 ").append(items.length).append(" barcode(s)");
    for (BarcodeResultItem item : items) {
      sb.append('\n')
          .append('[')
          .append(item.getFormatString())
          .append("] ")
          .append(truncate(item.getText()));
    }
    return sb.toString();
  }

  private static String summarizeMlKit(List<Barcode> barcodes) {
    if (barcodes == null || barcodes.isEmpty()) {
      return "\u2717 no barcode";
    }
    StringBuilder sb = new StringBuilder("\u2713 ").append(barcodes.size()).append(" barcode(s)");
    for (Barcode barcode : barcodes) {
      sb.append('\n')
          .append('[')
          .append(formatName(barcode.getFormat()))
          .append("] ")
          .append(truncate(barcode.getRawValue()));
    }
    return sb.toString();
  }

  private static String formatName(int format) {
    switch (format) {
      case Barcode.FORMAT_QR_CODE:
        return "QR_CODE";
      case Barcode.FORMAT_UPC_A:
        return "UPC_A";
      case Barcode.FORMAT_UPC_E:
        return "UPC_E";
      case Barcode.FORMAT_EAN_13:
        return "EAN_13";
      case Barcode.FORMAT_EAN_8:
        return "EAN_8";
      case Barcode.FORMAT_CODE_128:
        return "CODE_128";
      case Barcode.FORMAT_CODE_39:
        return "CODE_39";
      case Barcode.FORMAT_CODE_93:
        return "CODE_93";
      case Barcode.FORMAT_DATA_MATRIX:
        return "DATA_MATRIX";
      case Barcode.FORMAT_PDF417:
        return "PDF417";
      case Barcode.FORMAT_CODABAR:
        return "CODABAR";
      case Barcode.FORMAT_ITF:
        return "ITF";
      case Barcode.FORMAT_AZTEC:
        return "AZTEC";
      default:
        return String.valueOf(format);
    }
  }

  private static String truncate(String text) {
    if (text == null) {
      return "";
    }
    if (text.length() <= MAX_TEXT_PREVIEW_CHARS) {
      return text;
    }
    return text.substring(0, MAX_TEXT_PREVIEW_CHARS) + "\u2026 (" + text.length() + " chars)";
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    if (dynamsoftProcessor != null) {
      dynamsoftProcessor.stop();
      dynamsoftProcessor = null;
    }
    if (mlkitScanner != null) {
      mlkitScanner.close();
      mlkitScanner = null;
    }
    analysisExecutor.shutdown();
  }
}
