/*
 * Copyright 2020 Google LLC. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.mlkit.vision.demo.java;

import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.MediaStore;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import com.dynamsoft.dbr.BarcodeResultItem;
import com.google.mlkit.vision.barcode.Barcode;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.demo.BitmapUtils;
import com.google.mlkit.vision.demo.GraphicOverlay;
import com.google.mlkit.vision.demo.R;
import com.google.mlkit.vision.demo.java.dynamsoftbarcodescanner.DynamsoftBarcodeProcessor;
import java.io.IOException;
import java.util.List;

/**
 * Activity demonstrating barcode detection on a still image with both the Dynamsoft Barcode
 * Reader bundle and Google ML Kit, so their results and latency can be compared side by side.
 */
public final class StillImageActivity extends AppCompatActivity {

  private static final String TAG = "StillImageActivity";

  private static final int REQUEST_IMAGE_CAPTURE = 1001;
  private static final int REQUEST_CHOOSE_IMAGE = 1002;
  private static final int REQUEST_CAMERA_PERMISSION = 2001;

  private static final int MAX_TEXT_PREVIEW_CHARS = 120;

  private ImageView preview;
  private GraphicOverlay graphicOverlay;
  private TextView dynamsoftResultView;
  private TextView mlkitResultView;

  private Uri imageUri;

  private DynamsoftBarcodeProcessor dynamsoftProcessor;
  private BarcodeScanner mlkitScanner;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_still_image);

    preview = findViewById(R.id.preview);
    graphicOverlay = findViewById(R.id.graphic_overlay);
    dynamsoftResultView = findViewById(R.id.dynamsoft_result);
    mlkitResultView = findViewById(R.id.mlkit_result);

    findViewById(R.id.select_image_button)
        .setOnClickListener(view -> startChooseImageIntentForResult());
    findViewById(R.id.take_photo_button)
        .setOnClickListener(view -> startCameraIntentForResult());
    findViewById(R.id.live_camera_button)
        .setOnClickListener(
            view -> startActivity(new Intent(this, CameraLiveActivity.class)));

    if (savedInstanceState != null) {
      imageUri = savedInstanceState.getParcelable("image_uri");
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    Log.d(TAG, "onResume");
    if (dynamsoftProcessor == null) {
      dynamsoftProcessor = new DynamsoftBarcodeProcessor(this);
      dynamsoftProcessor.setDecodeListener(
          new DynamsoftBarcodeProcessor.DecodeListener() {
            @Override
            public void onDecoded(BarcodeResultItem[] items, long elapsedMs) {
              updateDynamsoftResult(items, elapsedMs);
            }

            @Override
            public void onError(String message) {
              dynamsoftResultView.setText("Dynamsoft: \u2717 error: " + message);
            }
          });
    }
    if (mlkitScanner == null) {
      mlkitScanner = BarcodeScanning.getClient();
    }
    if (imageUri == null) {
      // No user-selected image yet, so show the built-in high-density QR sample.
      loadDefaultSample();
    } else {
      tryReloadAndDetectInImage();
    }
  }

  @Override
  public void onPause() {
    super.onPause();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    if (dynamsoftProcessor != null) {
      dynamsoftProcessor.stop();
      dynamsoftProcessor = null;
    }
    if (mlkitScanner != null) {
      mlkitScanner.close();
      mlkitScanner = null;
    }
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putParcelable("image_uri", imageUri);
  }

  private void loadDefaultSample() {
    Bitmap sampleBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.dense_qr_sample);
    if (sampleBitmap == null) {
      Log.e(TAG, "Failed to decode the built-in dense QR sample");
      return;
    }
    runBoth(sampleBitmap);
  }

  private void tryReloadAndDetectInImage() {
    try {
      Bitmap bitmap = BitmapUtils.getBitmapFromContentUri(getContentResolver(), imageUri);
      if (bitmap == null) {
        return;
      }
      runBoth(bitmap);
    } catch (IOException e) {
      Log.e(TAG, "Error retrieving saved image", e);
      imageUri = null;
    }
  }

  /** Runs both barcode engines on the same bitmap and shows their results side by side. */
  private void runBoth(Bitmap bitmap) {
    preview.setImageBitmap(bitmap);
    graphicOverlay.setImageSourceInfo(bitmap.getWidth(), bitmap.getHeight(), /* isFlipped= */ false);
    graphicOverlay.clear();

    dynamsoftResultView.setText("Dynamsoft: running\u2026");
    mlkitResultView.setText("ML Kit: running\u2026");

    if (dynamsoftProcessor != null) {
      dynamsoftProcessor.processBitmap(bitmap, graphicOverlay);
    }
    runMlKit(bitmap);
  }

  private void runMlKit(Bitmap bitmap) {
    if (mlkitScanner == null) {
      return;
    }
    long frameStartMs = SystemClock.elapsedRealtime();
    mlkitScanner
        .process(InputImage.fromBitmap(bitmap, 0))
        .addOnSuccessListener(
            barcodes -> {
              long elapsedMs = SystemClock.elapsedRealtime() - frameStartMs;
              updateMlKitResult(barcodes, elapsedMs);
            })
        .addOnFailureListener(
            e -> mlkitResultView.setText("ML Kit: \u2717 " + e.getMessage()));
  }

  private static final int COLOR_SUCCESS = 0xFF0B7D00;
  private static final int COLOR_FAILURE = 0xFFB00020;

  private void updateDynamsoftResult(BarcodeResultItem[] items, long elapsedMs) {
    boolean ok = items != null && items.length > 0;
    dynamsoftResultView.setTextColor(ok ? COLOR_SUCCESS : COLOR_FAILURE);
    if (!ok) {
      dynamsoftResultView.setText(
          String.format("Dynamsoft: \u2717 no barcode (%d ms)", elapsedMs));
      return;
    }
    StringBuilder sb = new StringBuilder();
    sb.append(String.format("Dynamsoft: \u2713 %d barcode(s) in %d ms", items.length, elapsedMs));
    for (BarcodeResultItem item : items) {
      sb.append('\n')
          .append('[')
          .append(item.getFormatString())
          .append("] ")
          .append(truncate(item.getText()));
    }
    dynamsoftResultView.setText(sb.toString());
  }

  private void updateMlKitResult(List<Barcode> barcodes, long elapsedMs) {
    boolean ok = barcodes != null && !barcodes.isEmpty();
    mlkitResultView.setTextColor(ok ? COLOR_SUCCESS : COLOR_FAILURE);
    if (!ok) {
      mlkitResultView.setText(String.format("ML Kit: \u2717 no barcode (%d ms)", elapsedMs));
      return;
    }
    StringBuilder sb = new StringBuilder();
    sb.append(String.format("ML Kit: \u2713 %d barcode(s) in %d ms", barcodes.size(), elapsedMs));
    for (Barcode barcode : barcodes) {
      sb.append('\n')
          .append('[')
          .append(formatName(barcode.getFormat()))
          .append("] ")
          .append(truncate(barcode.getRawValue()));
    }
    mlkitResultView.setText(sb.toString());
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

  private void startChooseImageIntentForResult() {
    Intent intent = new Intent();
    intent.setType("image/*");
    intent.setAction(Intent.ACTION_GET_CONTENT);
    startActivityForResult(Intent.createChooser(intent, "Select Picture"), REQUEST_CHOOSE_IMAGE);
  }

  private void startCameraIntentForResult() {
    // Starting the system camera with ACTION_IMAGE_CAPTURE requires the CAMERA permission,
    // otherwise the system camera activity rejects the intent and the app crashes.
    if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
        != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(
          this, new String[] {android.Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
      return;
    }

    // Clean up last time's image
    imageUri = null;
    preview.setImageBitmap(null);

    Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
    if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
      ContentValues values = new ContentValues();
      values.put(MediaStore.Images.Media.TITLE, "New Picture");
      values.put(MediaStore.Images.Media.DESCRIPTION, "From Camera");
      imageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
      takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
      startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
    }
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    if (requestCode == REQUEST_CAMERA_PERMISSION) {
      if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        startCameraIntentForResult();
      } else {
        Toast.makeText(this, "Camera permission is required to take a photo.", Toast.LENGTH_LONG)
            .show();
      }
      return;
    }
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == REQUEST_IMAGE_CAPTURE) {
      // Reload either the captured photo (RESULT_OK) or the built-in sample (canceled),
      // so the preview is never left blank after the camera is dismissed.
      tryReloadAndDetectInImage();
    } else if (requestCode == REQUEST_CHOOSE_IMAGE && resultCode == RESULT_OK) {
      imageUri = data.getData();
      tryReloadAndDetectInImage();
    } else {
      super.onActivityResult(requestCode, resultCode, data);
    }
  }
}
