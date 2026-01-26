package com.dynamsoft.barcodebenchmark.fragments;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.hardware.Camera;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.dynamsoft.barcodebenchmark.MainViewModel;
import com.dynamsoft.barcodebenchmark.R;
import com.dynamsoft.barcodebenchmark.google.BarcodeGraphic;
import com.dynamsoft.barcodebenchmark.google.ui.camera.CameraSource;
import com.dynamsoft.barcodebenchmark.google.ui.camera.CameraSourcePreview;
import com.dynamsoft.barcodebenchmark.google.ui.camera.FrameProcessor;
import com.dynamsoft.barcodebenchmark.google.ui.camera.GraphicOverlay;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.google.android.gms.tasks.Tasks;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

public class MLkitScannerFragment extends Fragment implements FrameProcessor {

    private static final String TAG = "MLkitScanner";
    private static final int RC_HANDLE_GMS = 9001;
    private static final int RC_HANDLE_CAMERA_PERM = 2;

    private CameraSource mCameraSource;
    private CameraSourcePreview mPreview;
    private GraphicOverlay<BarcodeGraphic> mGraphicOverlay;
    private TextView tvResolution;
    private TextView tvStats;
    private TextView tvBarcodeResult;
    private MainViewModel viewModel;
    private BarcodeScanner scanner;
    
    private long lastScanTime = 0;
    private int barcodeCount = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        return inflater.inflate(R.layout.fragment_mlkit_scanner, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mPreview = view.findViewById(R.id.preview);
        mPreview.setOnCameraStartedListener(() -> {
            if (mCameraSource != null) {
                com.google.android.gms.common.images.Size size = mCameraSource.getPreviewSize();
                if (size != null) {
                    getActivity().runOnUiThread(() -> {
                        int orientation = getResources().getConfiguration().orientation;
                        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                            tvResolution.setText("Resolution: " + size.getHeight() + "x" + size.getWidth());
                        } else {
                            tvResolution.setText("Resolution: " + size.getWidth() + "x" + size.getHeight());
                        }
                    });
                }
            }
        });
        mGraphicOverlay = view.findViewById(R.id.graphicOverlay);
        tvResolution = view.findViewById(R.id.tv_resolution);
        tvStats = view.findViewById(R.id.tv_stats);
        tvBarcodeResult = view.findViewById(R.id.tv_barcode_result);

        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build();
        scanner = BarcodeScanning.getClient(options);

        int rc = ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA);
        if (rc == PackageManager.PERMISSION_GRANTED) {
            createCameraSource(true, false);
        } else {
            requestCameraPermission();
        }
    }

    private void requestCameraPermission() {
        Log.w(TAG, "Camera permission is not granted. Requesting permission");
        final String[] permissions = new String[] { Manifest.permission.CAMERA };

        if (!ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(),
                Manifest.permission.CAMERA)) {
            ActivityCompat.requestPermissions(requireActivity(), permissions, RC_HANDLE_CAMERA_PERM);
            return;
        }

        Toast.makeText(requireContext(), "Camera permission required", Toast.LENGTH_LONG).show();
        ActivityCompat.requestPermissions(requireActivity(), permissions, RC_HANDLE_CAMERA_PERM);
    }

    @SuppressLint("InlinedApi")
    private void createCameraSource(boolean autoFocus, boolean useFlash) {
        int width = 1280;
        int height = 720;
        if (viewModel.resolutionIndex == 1) {
            width = 1920;
            height = 1080;
        }

        CameraSource.Builder builder = new CameraSource.Builder(requireContext(), this)
                .setFacing(CameraSource.CAMERA_FACING_BACK)
                .setRequestedPreviewSize(width, height)
                .setRequestedFps(15.0f);

        if (autoFocus) {
            builder = builder.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        }

        mCameraSource = builder
                .setFlashMode(useFlash ? Camera.Parameters.FLASH_MODE_TORCH : null)
                .build();
    }

    @Override
    public void onResume() {
        super.onResume();
        barcodeCount = 0;
        lastScanTime = System.currentTimeMillis();
        updateStats();
        startCameraSource();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mPreview != null) {
            mPreview.stop();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mPreview != null) {
            mPreview.release();
        }
        if (scanner != null) {
            scanner.close();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        if (requestCode != RC_HANDLE_CAMERA_PERM) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }

        if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            createCameraSource(true, false);
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Camera Permission")
                .setMessage("No camera permission")
                .setPositiveButton("OK", (dialog, id) -> requireActivity().finish())
                .show();
    }

    private void startCameraSource() throws SecurityException {
        int code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(
                requireContext());
        if (code != ConnectionResult.SUCCESS) {
            GoogleApiAvailability.getInstance().getErrorDialog(requireActivity(), code, RC_HANDLE_GMS).show();
        }

        if (mCameraSource != null) {
            try {
                mPreview.start(mCameraSource, mGraphicOverlay);
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                mCameraSource.release();
                mCameraSource = null;
            }
        }
    }

    @Override
    public void process(ByteBuffer data, int width, int height, int rotationDegrees) {
        InputImage image = InputImage.fromByteBuffer(data, width, height, rotationDegrees, InputImage.IMAGE_FORMAT_NV21);
        
        try {
            List<Barcode> barcodes = Tasks.await(scanner.process(image));
            
            mGraphicOverlay.clear();
            if (barcodes != null && !barcodes.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                
                for (Barcode barcode : barcodes) {
                    BarcodeGraphic graphic = new BarcodeGraphic(mGraphicOverlay);
                    mGraphicOverlay.add(graphic);
                    graphic.updateItem(barcode);
                    
                    barcodeCount++;
                    
                    sb.append("[").append(getBarcodeFormatName(barcode.getFormat())).append("]\n");
                    String text = barcode.getRawValue();
                    if (text != null) {
                        if (text.length() > 50) {
                            text = text.substring(0, 50) + "...";
                        }
                        sb.append(text).append("\n\n");
                    }
                }
                
                final String resultText = sb.toString().trim();
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (tvBarcodeResult != null) {
                            tvBarcodeResult.setText(resultText);
                            updateStats();
                        }
                    });
                }
            }
        } catch (ExecutionException | InterruptedException e) {
            Log.e(TAG, "Barcode detection failed", e);
        }
    }

    private void updateStats() {
        if (tvStats == null) return;
        
        long elapsed = (System.currentTimeMillis() - lastScanTime) / 1000;
        String stats = String.format(Locale.getDefault(), 
                "Barcodes found: %d | Time: %ds", barcodeCount, elapsed);
        tvStats.setText(stats);
    }

    private String getBarcodeFormatName(int format) {
        switch (format) {
            case Barcode.FORMAT_CODE_128: return "CODE_128";
            case Barcode.FORMAT_CODE_39: return "CODE_39";
            case Barcode.FORMAT_CODE_93: return "CODE_93";
            case Barcode.FORMAT_CODABAR: return "CODABAR";
            case Barcode.FORMAT_DATA_MATRIX: return "DATA_MATRIX";
            case Barcode.FORMAT_EAN_13: return "EAN_13";
            case Barcode.FORMAT_EAN_8: return "EAN_8";
            case Barcode.FORMAT_ITF: return "ITF";
            case Barcode.FORMAT_QR_CODE: return "QR_CODE";
            case Barcode.FORMAT_UPC_A: return "UPC_A";
            case Barcode.FORMAT_UPC_E: return "UPC_E";
            case Barcode.FORMAT_PDF417: return "PDF417";
            case Barcode.FORMAT_AZTEC: return "AZTEC";
            default: return "UNKNOWN";
        }
    }
}
