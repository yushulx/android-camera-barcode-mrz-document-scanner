package com.dynamsoft.dcv.driverslicensescanner.google;

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
import com.dynamsoft.dcv.driverslicensescanner.google.ui.camera.FrameProcessor;
import com.dynamsoft.dcv.driverslicensescanner.google.ui.camera.GraphicOverlay;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class GoogleScannerFragment extends Fragment implements FrameProcessor {

    private static final String TAG = "GoogleScanner";
    private static final int RC_HANDLE_GMS = 9001;
    private static final int RC_HANDLE_CAMERA_PERM = 2;

    private CameraSource mCameraSource;
    private CameraSourcePreview mPreview;
    private GraphicOverlay<BarcodeGraphic> mGraphicOverlay;
    private android.widget.TextView tvResolution;
    private boolean isNavigating = false;
    private MainViewModel viewModel;
    private BarcodeScanner scanner;

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
        Context context = requireContext();

        int width = 1280;
        int height = 720;
        if (viewModel.resolutionIndex == 0) {
            width = 640;
            height = 480;
        } else if (viewModel.resolutionIndex == 2) {
            width = 1920;
            height = 1080;
        }

        CameraSource.Builder builder = new CameraSource.Builder(requireContext(), this)
                .setFacing(CameraSource.CAMERA_FACING_BACK)
                .setRequestedPreviewSize(width, height)
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
        if (scanner != null) {
            scanner.close();
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
                com.google.android.gms.common.images.Size size = mCameraSource.getPreviewSize();
                if (size != null) {
                    tvResolution.setText("Resolution: " + size.getWidth() + "x" + size.getHeight());
                }
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                mCameraSource.release();
                mCameraSource = null;
            }
        }
    }

    @Override
    public void process(ByteBuffer data, int width, int height, int rotationDegrees) {
        if (isNavigating) return;

        InputImage image = InputImage.fromByteBuffer(data, width, height, rotationDegrees, InputImage.IMAGE_FORMAT_NV21);
        
        try {
            List<Barcode> barcodes = Tasks.await(scanner.process(image));
            
            if (isNavigating) return;
            mGraphicOverlay.clear();
            if (barcodes != null) {
                for (Barcode barcode : barcodes) {
                    BarcodeGraphic graphic = new BarcodeGraphic(mGraphicOverlay);
                    mGraphicOverlay.add(graphic);
                    graphic.updateItem(barcode);

                    if (barcode.getFormat() == Barcode.FORMAT_PDF417 && barcode.getDriverLicense() != null) {
                        isNavigating = true;
                        navigateToResult(barcode);
                        break; // Only process one license
                    }
                }
            }
        } catch (ExecutionException | InterruptedException e) {
            Log.e(TAG, "Barcode detection failed", e);
        }
    }

    private void navigateToResult(Barcode barcode) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                if (isAdded() && getView() != null) {
                    try {
                        List<String> list = new ArrayList<>();
                        Barcode.DriverLicense driverLicense = barcode.getDriverLicense();

                        if (driverLicense.getDocumentType() != null)
                            list.add("Document Type: " + driverLicense.getDocumentType());
                        if (driverLicense.getFirstName() != null)
                            list.add("First Name: " + driverLicense.getFirstName());
                        if (driverLicense.getMiddleName() != null)
                            list.add("Middle Name: " + driverLicense.getMiddleName());
                        if (driverLicense.getLastName() != null)
                            list.add("Last Name: " + driverLicense.getLastName());
                        if (driverLicense.getGender() != null)
                            list.add("Gender: " + driverLicense.getGender());
                        if (driverLicense.getAddressStreet() != null)
                            list.add("Street: " + driverLicense.getAddressStreet());
                        if (driverLicense.getAddressCity() != null)
                            list.add("City: " + driverLicense.getAddressCity());
                        if (driverLicense.getAddressState() != null)
                            list.add("State: " + driverLicense.getAddressState());
                        if (driverLicense.getAddressZip() != null)
                            list.add("Zip: " + driverLicense.getAddressZip());
                        if (driverLicense.getLicenseNumber() != null)
                            list.add("License Number: " + driverLicense.getLicenseNumber());
                        if (driverLicense.getIssueDate() != null)
                            list.add("Issue Date: " + driverLicense.getIssueDate());
                        if (driverLicense.getExpiryDate() != null)
                            list.add("Expiry Date: " + driverLicense.getExpiryDate());
                        if (driverLicense.getBirthDate() != null)
                            list.add("Birth Date: " + driverLicense.getBirthDate());
                        if (driverLicense.getIssuingCountry() != null)
                            list.add("Issue Country: " + driverLicense.getIssuingCountry());

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