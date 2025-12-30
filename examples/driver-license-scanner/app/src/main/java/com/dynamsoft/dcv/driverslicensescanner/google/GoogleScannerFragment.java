package com.dynamsoft.dcv.driverslicensescanner.google;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.Bundle;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.dynamsoft.dcv.driverslicensescanner.MainViewModel;
import com.dynamsoft.dcv.driverslicensescanner.R;
import com.dynamsoft.dcv.driverslicensescanner.google.ui.camera.CameraSource;
import com.dynamsoft.dcv.driverslicensescanner.google.ui.camera.CameraSourcePreview;
import com.dynamsoft.dcv.driverslicensescanner.google.ui.camera.GraphicOverlay;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GoogleScannerFragment extends Fragment implements BarcodeGraphicTracker.BarcodeUpdateListener {

    private static final String TAG = "GoogleScanner";
    private static final int RC_HANDLE_GMS = 9001;
    private static final int RC_HANDLE_CAMERA_PERM = 2;

    private CameraSource mCameraSource;
    private CameraSourcePreview mPreview;
    private GraphicOverlay<BarcodeGraphic> mGraphicOverlay;
    private boolean isNavigating = false;
    private MainViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        return inflater.inflate(R.layout.fragment_google_scanner, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mPreview = view.findViewById(R.id.preview);
        mGraphicOverlay = view.findViewById(R.id.graphicOverlay);

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
        Context context = requireContext();

        BarcodeDetector barcodeDetector = new BarcodeDetector.Builder(context).build();
        BarcodeTrackerFactory barcodeFactory = new BarcodeTrackerFactory(mGraphicOverlay, this);
        barcodeDetector.setProcessor(
                new MultiProcessor.Builder<>(barcodeFactory).build());

        if (!barcodeDetector.isOperational()) {
            Log.w(TAG, "Detector dependencies are not yet available.");
            IntentFilter lowstorageFilter = new IntentFilter(Intent.ACTION_DEVICE_STORAGE_LOW);
            boolean hasLowStorage = requireActivity().registerReceiver(null, lowstorageFilter) != null;

            if (hasLowStorage) {
                Toast.makeText(requireContext(), "Low Storage", Toast.LENGTH_LONG).show();
                Log.w(TAG, "Low Storage");
            }
        }

        CameraSource.Builder builder = new CameraSource.Builder(requireContext(), barcodeDetector)
                .setFacing(CameraSource.CAMERA_FACING_BACK)
                .setRequestedPreviewSize(1600, 1024)
                .setRequestedFps(15.0f);

        if (autoFocus) {
            builder = builder.setFocusMode(
                    autoFocus ? Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE : null);
        }

        mCameraSource = builder
                .setFlashMode(useFlash ? Camera.Parameters.FLASH_MODE_TORCH : null)
                .build();

    }

    @Override
    public void onResume() {
        super.onResume();
        isNavigating = false;
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
            Log.d(TAG, "Got unexpected permission result: " + requestCode);
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }

        if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Camera permission granted - initialize the camera source");
            createCameraSource(true, false);
            return;
        }

        Log.e(TAG, "Permission not granted: results len = " + grantResults.length +
                " Result code = " + (grantResults.length > 0 ? grantResults[0] : "(empty)"));

        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                requireActivity().finish();
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Multitracker sample")
                .setMessage("No camera permission")
                .setPositiveButton("OK", listener)
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
    public void onBarcodeDetected(Barcode barcode) {
        if (barcode != null && !isNavigating) {
            if (barcode.format == Barcode.PDF417 && barcode.driverLicense != null) {
                isNavigating = true;
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (isAdded() && getView() != null) {
                            try {
                                List<String> list = new ArrayList<>();
                                Barcode.DriverLicense driverLicense = barcode.driverLicense;

                                if (driverLicense.documentType != null)
                                    list.add("Document Type: " + driverLicense.documentType);
                                if (driverLicense.firstName != null)
                                    list.add("First Name: " + driverLicense.firstName);
                                if (driverLicense.middleName != null)
                                    list.add("Middle Name: " + driverLicense.middleName);
                                if (driverLicense.lastName != null)
                                    list.add("Last Name: " + driverLicense.lastName);
                                if (driverLicense.gender != null)
                                    list.add("Gender: " + driverLicense.gender);
                                if (driverLicense.addressStreet != null)
                                    list.add("Street: " + driverLicense.addressStreet);
                                if (driverLicense.addressCity != null)
                                    list.add("City: " + driverLicense.addressCity);
                                if (driverLicense.addressState != null)
                                    list.add("State: " + driverLicense.addressState);
                                if (driverLicense.addressZip != null)
                                    list.add("Zip: " + driverLicense.addressZip);
                                if (driverLicense.licenseNumber != null)
                                    list.add("License Number: " + driverLicense.licenseNumber);
                                if (driverLicense.issueDate != null)
                                    list.add("Issue Date: " + driverLicense.issueDate);
                                if (driverLicense.expiryDate != null)
                                    list.add("Expiry Date: " + driverLicense.expiryDate);
                                if (driverLicense.birthDate != null)
                                    list.add("Birth Date: " + driverLicense.birthDate);
                                if (driverLicense.issuingCountry != null)
                                    list.add("Issue Country: " + driverLicense.issuingCountry);

                                viewModel.results = list.toArray(new String[0]);
                                Navigation.findNavController(requireView())
                                        .navigate(R.id.action_GoogleScannerFragment_to_ResultFragment);
                            } catch (Exception e) {
                                Log.e(TAG, "Error navigating to result", e);
                                isNavigating = false;
                            }
                        } else {
                            isNavigating = false;
                        }
                    });
                } else {
                    isNavigating = false;
                }
            }
        }
    }
}
