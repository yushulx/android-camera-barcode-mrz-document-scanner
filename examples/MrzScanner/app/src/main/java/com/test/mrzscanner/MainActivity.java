package com.test.mrzscanner;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.dynamsoft.core.basic_structures.CompletionListener;
import com.dynamsoft.cvr.CaptureVisionRouter;
import com.dynamsoft.cvr.CaptureVisionRouterException;
import com.dynamsoft.cvr.CapturedResultReceiver;
import com.dynamsoft.dce.CameraEnhancer;
import com.dynamsoft.dce.CameraView;
import com.dynamsoft.dce.DrawingLayer;
import com.dynamsoft.dce.utils.PermissionUtil;
import com.dynamsoft.dcp.ParsedResult;
import com.dynamsoft.dcp.ParsedResultItem;
import com.dynamsoft.dlr.RecognizedTextLinesResult;
import com.dynamsoft.dlr.TextLineResultItem;
import com.dynamsoft.license.LicenseManager;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Live Camera Scanning Activity
 * Uses Dynamsoft SDK for real-time MRZ scanning and parsing.
 */
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private CameraEnhancer mCamera;
    private CameraView mCameraView;
    private final CaptureVisionRouter mRouter = new CaptureVisionRouter();
    private String mText = "";
    private AlertDialog mAlertDialog;
    private boolean succeed = false;
    private int mBirthYear;

    // UI Elements
    private ImageView btnBack;
    // private SwitchMaterial switchDynamsoft; // Removed
    private TextView tvMessage;
    private TextView tvResult;
    private TextView tvStatus;
    private TextView tvInstruction;
    private CircularProgressIndicator progressIndicator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);

        initViews();
        setupClickListeners();
        initializeScanner();
    }

    private void initViews() {
        mCameraView = findViewById(R.id.dce_camera_view);
        btnBack = findViewById(R.id.btn_back);
        tvMessage = findViewById(R.id.tv_message);
        tvResult = findViewById(R.id.tv_result);
        tvStatus = findViewById(R.id.tv_status);
        tvInstruction = findViewById(R.id.tv_instruction);
        progressIndicator = findViewById(R.id.progress_indicator);
    }

    private void setupClickListeners() {
        btnBack.setOnClickListener(v -> finish());
    }

    private void initializeScanner() {
        PermissionUtil.requestCameraPermission(this);

        // Initialize Dynamsoft License
        MrzUtils.initLicense();

        // Initialize Camera
        mCamera = new CameraEnhancer(mCameraView, this);

        try {
            mRouter.setInput(mCamera);
        } catch (CaptureVisionRouterException e) {
            throw new RuntimeException(e);
        }

        // Set up result receiver
        mRouter.addResultReceiver(new CapturedResultReceiver() {
            @Override
            public void onRecognizedTextLinesReceived(@NonNull RecognizedTextLinesResult result) {
                onLabelTextReceived(result);
            }

            @Override
            public void onParsedResultsReceived(@NonNull ParsedResult result) {
                if (!succeed) {
                    onParsedResultReceived(result);
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        succeed = false;
        mText = "";
        mCamera.open();
        startCapturing();
    }

    private void startCapturing() {
        updateStatus("Scanning for MRZ...");

        mRouter.startCapturing("ReadPassportAndId", new CompletionListener() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> updateStatus("Ready - Position MRZ in frame"));
            }

            @Override
            public void onFailure(int errorCode, String errorString) {
                runOnUiThread(() -> showDialog("Error", String.format(Locale.getDefault(),
                        "ErrorCode: %d %nErrorMessage: %s", errorCode, errorString)));
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        succeed = false;
        mCamera.close();
        mRouter.stopCapturing();
    }

    @Override
    protected void onStop() {
        mCameraView.getDrawingLayer(DrawingLayer.DLR_LAYER_ID).clearDrawingItems();
        super.onStop();
    }

    private void onLabelTextReceived(RecognizedTextLinesResult result) {
        if (result.getItems() == null) {
            return;
        }

        TextLineResultItem[] results = result.getItems();
        StringBuilder resultBuilder = new StringBuilder();

        if (results != null && results.length > 0) {
            for (TextLineResultItem item : results) {
                resultBuilder.append(item.getText()).append("\n");
            }
            mText = resultBuilder.toString();

            // Show recognized text in preview
            runOnUiThread(() -> {
                if (!mText.isEmpty()) {
                    tvResult.setText(mText.trim());
                    tvResult.setVisibility(View.VISIBLE);
                    updateStatus("MRZ detected - Parsing...");
                }
            });
        }
    }

    private void onParsedResultReceived(ParsedResult result) {
        if (result.getItems() == null) {
            return;
        }

        if (result.getItems().length == 0) {
            runOnUiThread(() -> {
                if (!mText.isEmpty()) {
                    showError("Failed to parse MRZ. Please adjust position.");
                }
            });
        } else {
            HashMap<String, String> labelMap = MrzUtils.parseDynamsoftResult(result.getItems()[0]);

            if (!labelMap.isEmpty()) {
                succeed = true;
                runOnUiThread(() -> {
                    updateStatus("MRZ parsed successfully!");
                    progressIndicator.setVisibility(View.GONE);
                });

                // Navigate to result screen
                Intent intent = new Intent(this, ScanResultActivity.class);
                intent.putExtra(ScanResultActivity.EXTRA_LABEL_MAP, labelMap);
                startActivity(intent);
            } else {
                runOnUiThread(() -> {
                    if (!mText.isEmpty()) {
                        showError("Failed to parse MRZ content.");
                    }
                });
            }
        }
    }

    private void updateStatus(String status) {
        if (tvStatus != null) {
            tvStatus.setText(status);
        }
    }

    private void showError(String message) {
        if (tvMessage != null) {
            tvMessage.setText(message);
            tvMessage.setVisibility(View.VISIBLE);
        }
    }

    private void showDialog(String title, String message) {
        if (mAlertDialog == null) {
            mAlertDialog = new AlertDialog.Builder(this)
                    .setCancelable(true)
                    .setPositiveButton("OK", null)
                    .create();
        }
        mAlertDialog.setTitle(title);
        mAlertDialog.setMessage(message);
        mAlertDialog.show();
    }
}
