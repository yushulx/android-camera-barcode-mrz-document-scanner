package com.dynamsoft.barcodebenchmark.fragments;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.dynamsoft.barcodebenchmark.MainViewModel;
import com.dynamsoft.barcodebenchmark.R;
import com.dynamsoft.barcodebenchmark.google.ZXingBarcodeGraphic;
import com.dynamsoft.barcodebenchmark.google.ui.camera.CameraSource;
import com.dynamsoft.barcodebenchmark.google.ui.camera.CameraSourcePreview;
import com.dynamsoft.barcodebenchmark.google.ui.camera.FrameProcessor;
import com.dynamsoft.barcodebenchmark.google.ui.camera.GraphicOverlay;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Locale;

import zxingcpp.BarcodeReader;

public class ZXingCppScannerFragment extends Fragment implements FrameProcessor {

    private static final String TAG = "ZXingCppScanner";
    private static final int RC_HANDLE_GMS = 9001;
    private static final int RC_HANDLE_CAMERA_PERM = 2;

    private CameraSource mCameraSource;
    private CameraSourcePreview mPreview;
    private GraphicOverlay<ZXingBarcodeGraphic> mGraphicOverlay;
    private TextView tvResolution;
    private TextView tvStats;
    private TextView tvBarcodeResult;
    private MainViewModel viewModel;
    private BarcodeReader barcodeReader;

    private long lastScanTime = 0;
    private int barcodeCount = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        return inflater.inflate(R.layout.fragment_zxingcpp_scanner, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mPreview = view.findViewById(R.id.preview);
        mPreview.setOnCameraStartedListener(() -> {
            if (mCameraSource != null) {
                com.google.android.gms.common.images.Size size = mCameraSource.getPreviewSize();
                if (size != null) {
                    requireActivity().runOnUiThread(() -> {
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

        // Initialize ZXing-CPP reader
        barcodeReader = new BarcodeReader();
        barcodeReader.getOptions().setTryHarder(true);
        barcodeReader.getOptions().setTryRotate(true);

        int rc = ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA);
        if (rc == PackageManager.PERMISSION_GRANTED) {
            createCameraSource(true, false);
        } else {
            requestCameraPermission();
        }
    }

    private void requestCameraPermission() {
        Log.w(TAG, "Camera permission is not granted. Requesting permission");
        final String[] permissions = new String[]{Manifest.permission.CAMERA};

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
        if (barcodeReader == null) return;

        try {
            // Use data.array() directly to avoid ByteBuffer position issues:
            // the camera reuses the same ByteBuffer across frames, so data.remaining()
            // would return 0 after the first data.get() call.
            byte[] nv21 = data.array();

            // Convert NV21 to Bitmap via YuvImage
            YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, width, height, null);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new Rect(0, 0, width, height), 85, baos);
            byte[] jpegBytes = baos.toByteArray();
            Bitmap bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length);

            if (bitmap == null) return;

            List<BarcodeReader.Result> results = barcodeReader.read(
                    bitmap,
                    new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight()),
                    rotationDegrees);
            bitmap.recycle();

            mGraphicOverlay.clear();
            if (results != null && !results.isEmpty()) {
                barcodeCount += results.size();
                StringBuilder sb = new StringBuilder();
                for (BarcodeReader.Result result : results) {
                    ZXingBarcodeGraphic graphic = new ZXingBarcodeGraphic(mGraphicOverlay);
                    mGraphicOverlay.add(graphic);
                    graphic.updateItem(result);

                    sb.append("[").append(result.getFormat().name()).append("]\n");
                    String text = result.getText();
                    if (text != null && text.length() > 50) {
                        text = text.substring(0, 50) + "...";
                    }
                    sb.append(text != null ? text : "").append("\n\n");
                }

                final String displayText = sb.toString().trim();
                // Guard against fragment detachment — requireActivity() throws
                // IllegalStateException if the fragment is no longer attached
                if (isAdded() && getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (tvBarcodeResult != null) {
                            tvBarcodeResult.setText(displayText);
                            updateStats();
                        }
                    });
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing frame", e);
        }
    }

    private void updateStats() {
        if (tvStats == null) return;
        long elapsed = (System.currentTimeMillis() - lastScanTime) / 1000;
        String stats = String.format(Locale.getDefault(),
                "Barcodes found: %d | Time: %ds", barcodeCount, elapsed);
        tvStats.setText(stats);
    }
}
