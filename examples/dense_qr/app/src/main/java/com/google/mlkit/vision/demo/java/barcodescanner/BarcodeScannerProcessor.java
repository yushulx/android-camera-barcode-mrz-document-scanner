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

package com.google.mlkit.vision.demo.java.barcodescanner;

import android.content.Context;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import android.util.Log;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.barcode.Barcode;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.demo.GraphicOverlay;
import com.google.mlkit.vision.demo.java.VisionProcessorBase;
import java.util.List;

/** Barcode Detector Demo with a listener to report results for the comparison panel. */
public class BarcodeScannerProcessor extends VisionProcessorBase<List<Barcode>> {

  private static final String TAG = "BarcodeProcessor";

  private final BarcodeScanner barcodeScanner;
  private ScanListener scanListener;
  private long frameStartMs = 0L;

  /** Callback to report detection results back to the UI. */
  public interface ScanListener {
    void onScanned(List<Barcode> barcodes, long elapsedMs);
    void onError(Exception e);
  }

  public BarcodeScannerProcessor(Context context) {
    super(context);
    barcodeScanner = BarcodeScanning.getClient();
  }

  public void setScanListener(ScanListener listener) {
    this.scanListener = listener;
  }

  @Override
  public void stop() {
    super.stop();
    barcodeScanner.close();
  }

  @Override
  protected Task<List<Barcode>> detectInImage(InputImage image) {
    frameStartMs = SystemClock.elapsedRealtime();
    return barcodeScanner.process(image);
  }

  @Override
  protected void onSuccess(
      @NonNull List<Barcode> barcodes, @NonNull GraphicOverlay graphicOverlay) {
    long elapsedMs = SystemClock.elapsedRealtime() - frameStartMs;
    if (barcodes.isEmpty()) {
      Log.v(MANUAL_TESTING_LOG, "No barcode has been detected");
    }
    Log.i(TAG, "ML Kit detected " + barcodes.size() + " barcode(s) in " + elapsedMs + " ms");
    if (scanListener != null) {
      scanListener.onScanned(barcodes, elapsedMs);
    }
  }

  @Override
  protected void onFailure(@NonNull Exception e) {
    Log.e(TAG, "Barcode detection failed " + e);
    if (scanListener != null) {
      scanListener.onError(e);
    }
  }
}
