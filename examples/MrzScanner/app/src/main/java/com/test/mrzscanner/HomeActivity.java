package com.test.mrzscanner;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.mlkit.vision.documentscanner.GmsDocumentScanner;
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult;

/**
 * Home Activity - Main entry point for the MRZ Scanner Pro app.
 * Provides options for document scanning using Google ML Kit or
 * live camera scanning with Dynamsoft SDK.
 */
public class HomeActivity extends AppCompatActivity {

    private static final String TAG = "HomeActivity";

    private LinearLayout btnDocumentScan;
    private LinearLayout btnLiveScan;

    // Document Scanner
    private GmsDocumentScanner documentScanner;
    private ActivityResultLauncher<IntentSenderRequest> scannerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        initViews();
        initDocumentScanner();
        setupClickListeners();
    }

    private void initViews() {
        btnDocumentScan = findViewById(R.id.btn_document_scan);
        btnLiveScan = findViewById(R.id.btn_live_scan);
    }

    private void initDocumentScanner() {
        // Configure the document scanner - use BASE mode (no enhancement by default)
        GmsDocumentScannerOptions options = new GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setPageLimit(1)
                .setResultFormats(
                        GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE)
                .build();

        documentScanner = GmsDocumentScanning.getClient(options);

        // Register for scanner result
        scannerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartIntentSenderForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        GmsDocumentScanningResult scanResult =
                                GmsDocumentScanningResult.fromActivityResultIntent(result.getData());

                        if (scanResult != null && scanResult.getPages() != null
                                && !scanResult.getPages().isEmpty()) {

                            // Get the first page's image URI
                            Uri imageUri = scanResult.getPages().get(0).getImageUri();
                            Log.d(TAG, "Scanned document URI: " + imageUri);

                            // Navigate to ScanResultActivity with the image
                            navigateToResult(imageUri);
                        } else {
                            Toast.makeText(this, "No document scanned", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Log.d(TAG, "Document scanning cancelled or failed");
                    }
                });
    }

    private void setupClickListeners() {
        // Document Scan button - uses Google ML Kit Document Scanner
        btnDocumentScan.setOnClickListener(v -> {
            startDocumentScanner();
        });

        // Live Scan button - navigates to live camera scanning
        btnLiveScan.setOnClickListener(v -> {
            navigateToLiveScan();
        });
    }

    private void startDocumentScanner() {
        documentScanner.getStartScanIntent(this)
                .addOnSuccessListener(intentSender -> {
                    scannerLauncher.launch(
                            new IntentSenderRequest.Builder(intentSender).build());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to start document scanner", e);
                    Toast.makeText(this,
                            "Failed to start scanner: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    private void navigateToResult(Uri imageUri) {
        Intent intent = new Intent(this, ScanResultActivity.class);
        intent.putExtra(ScanResultActivity.EXTRA_IMAGE_URI, imageUri.toString());
        intent.putExtra(ScanResultActivity.EXTRA_USE_DYNAMSOFT, true);
        startActivity(intent);
    }

    private void navigateToLiveScan() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("use_dynamsoft", true);
        startActivity(intent);
    }
}