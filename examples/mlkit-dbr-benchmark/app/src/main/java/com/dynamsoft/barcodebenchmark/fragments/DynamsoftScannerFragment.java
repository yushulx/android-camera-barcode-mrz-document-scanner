package com.dynamsoft.barcodebenchmark.fragments;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.dynamsoft.core.basic_structures.CompletionListener;
import com.dynamsoft.cvr.CaptureVisionRouter;
import com.dynamsoft.cvr.CaptureVisionRouterException;
import com.dynamsoft.cvr.CapturedResultReceiver;
import com.dynamsoft.cvr.EnumPresetTemplate;
import com.dynamsoft.dbr.BarcodeResultItem;
import com.dynamsoft.dbr.DecodedBarcodesResult;
import com.dynamsoft.dce.CameraEnhancer;
import com.dynamsoft.dce.CameraEnhancerException;
import com.dynamsoft.dce.EnumResolution;
import com.dynamsoft.barcodebenchmark.MainViewModel;
import com.dynamsoft.barcodebenchmark.R;
import com.dynamsoft.barcodebenchmark.databinding.FragmentDynamsoftScannerBinding;

import java.util.Locale;

public class DynamsoftScannerFragment extends Fragment {
    private FragmentDynamsoftScannerBinding binding;
    private CameraEnhancer mCamera;
    private CaptureVisionRouter mRouter;
    private MainViewModel viewModel;
    private long lastScanTime = 0;
    private int barcodeCount = 0;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentDynamsoftScannerBinding.inflate(inflater, container, false);
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        
        mCamera = new CameraEnhancer(binding.cameraView, getViewLifecycleOwner());
        
        if (viewModel.resolutionIndex == 0) {
            mCamera.setResolution(EnumResolution.RESOLUTION_720P);
        } else {
            mCamera.setResolution(EnumResolution.RESOLUTION_1080P);
        }

        if (mRouter == null) {
            initCaptureVisionRouter();
        }
        try {
            mRouter.setInput(mCamera);
        } catch (CaptureVisionRouterException e) {
            e.printStackTrace();
        }
        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        barcodeCount = 0;
        lastScanTime = System.currentTimeMillis();
        updateStats();
        
        mCamera.open();
        try {
            android.util.Size size = mCamera.getResolution();
            if (size != null) {
                binding.tvResolution.setText("Resolution: " + size.getWidth() + "x" + size.getHeight());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        mRouter.startCapturing(EnumPresetTemplate.PT_READ_BARCODES, new CompletionListener() {
            @Override
            public void onSuccess() {
            }

            @Override
            public void onFailure(int errorCode, String errorString) {
                requireActivity().runOnUiThread(() -> showDialog("Error", String.format(Locale.getDefault(),
                        "ErrorCode: %d %nErrorMessage: %s", errorCode, errorString)));
            }
        });
    }

    @Override
    public void onPause() {
        super.onPause();
        mCamera.close();
        mRouter.stopCapturing();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void initCaptureVisionRouter() {
        mRouter = new CaptureVisionRouter(requireContext());
        mRouter.addResultReceiver(new CapturedResultReceiver() {
            @Override
            public void onDecodedBarcodesReceived(DecodedBarcodesResult result) {
                if (result != null && result.getItems() != null && result.getItems().length > 0) {
                    barcodeCount += result.getItems().length;
                    
                    StringBuilder sb = new StringBuilder();
                    for (BarcodeResultItem item : result.getItems()) {
                        sb.append("[").append(item.getFormatString()).append("]\n");
                        String text = item.getText();
                        if (text.length() > 50) {
                            text = text.substring(0, 50) + "...";
                        }
                        sb.append(text).append("\n\n");
                    }
                    
                    requireActivity().runOnUiThread(() -> {
                        if (binding != null) {
                            binding.tvBarcodeResult.setText(sb.toString().trim());
                            updateStats();
                        }
                    });
                }
            }
        });
    }

    private void updateStats() {
        if (binding == null) return;
        
        long elapsed = (System.currentTimeMillis() - lastScanTime) / 1000;
        String stats = String.format(Locale.getDefault(), 
                "Barcodes found: %d | Time: %ds", barcodeCount, elapsed);
        binding.tvStats.setText(stats);
    }

    private void showDialog(String title, String message) {
        new AlertDialog.Builder(requireContext())
                .setCancelable(true)
                .setPositiveButton("OK", null)
                .setTitle(title)
                .setMessage(message)
                .show();
    }
}
