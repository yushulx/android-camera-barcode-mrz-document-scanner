package com.dynamsoft.dcv.driverslicensescanner.fragments;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.dynamsoft.core.basic_structures.CompletionListener;
import com.dynamsoft.cvr.CaptureVisionRouter;
import com.dynamsoft.cvr.CaptureVisionRouterException;
import com.dynamsoft.cvr.CapturedResultReceiver;
import com.dynamsoft.dbr.DecodedBarcodesResult;
import com.dynamsoft.dce.CameraEnhancer;
import com.dynamsoft.dce.CameraEnhancerException;
import com.dynamsoft.dcp.ParsedResult;
import com.dynamsoft.dcv.driverslicensescanner.MainViewModel;
import com.dynamsoft.dcv.driverslicensescanner.ParseUtil;
import com.dynamsoft.dcv.driverslicensescanner.R;
import com.dynamsoft.dcv.driverslicensescanner.databinding.FragmentScannerBinding;

import java.util.Locale;

public class ScannerFragment extends Fragment {
    private static final String TEMPLATE_ASSETS_FILE_NAME = "drivers-license.json";
    private static final String TEMPLATE_READ_PDF417 = "ReadPDF417";
    private FragmentScannerBinding binding;
    private CameraEnhancer mCamera;
    private CaptureVisionRouter mRouter;
    private MainViewModel viewModel;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentScannerBinding.inflate(inflater, container, false);
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        viewModel.reset();
        mCamera = new CameraEnhancer(binding.cameraView, getViewLifecycleOwner());
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
        try {
            mCamera.open();
        } catch (CameraEnhancerException e) {
            e.printStackTrace();
        }
        mRouter.startCapturing(TEMPLATE_READ_PDF417, new CompletionListener() {

            @Override
            public void onSuccess() {

            }

            @Override
            public void onFailure(int errorCode, String errorString) {
                requireActivity().runOnUiThread(() ->
                        showDialog("Error", String.format(Locale.getDefault(), "ErrorCode: %d %nErrorMessage: %s", errorCode, errorString)));
            }
        });
    }

    @Override
    public void onPause() {
        super.onPause();
        try {
            mCamera.close();
        } catch (CameraEnhancerException e) {
            e.printStackTrace();
        }
        mRouter.stopCapturing();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void initCaptureVisionRouter() {
        mRouter = new CaptureVisionRouter(requireContext());
        try {
            mRouter.initSettingsFromFile(TEMPLATE_ASSETS_FILE_NAME);
        } catch (CaptureVisionRouterException e) {
            e.printStackTrace();
        }
        mRouter.addResultReceiver(new CapturedResultReceiver() {
            @Override
            public void onDecodedBarcodesReceived(DecodedBarcodesResult result) {
                if (result.getItems().length > 0) {
                    viewModel.parsedText = result.getItems()[0].getText();
                }
            }

            @Override
            public void onParsedResultsReceived(ParsedResult result) {
                if (result.getItems().length > 0) {
                    String[] displayStrings = ParseUtil.parsedItemToDisplayStrings(result.getItems()[0]);
                    if (displayStrings == null || displayStrings.length <= 1/*Only have Document Type content*/) {
                        showParsedText();
                        return;
                    }
                    viewModel.results = displayStrings;
                    requireActivity().runOnUiThread(() -> NavHostFragment.findNavController(ScannerFragment.this)
                            .navigate(R.id.action_ScannerFragment_to_ResultFragment));
                    mRouter.stopCapturing();
                } else {
                    showParsedText();
                }
            }
        });
    }

    private void showParsedText() {
        if (viewModel.parsedText != null && !viewModel.parsedText.isEmpty()) {
            requireActivity().runOnUiThread(() -> {
                if (binding != null) {
                    binding.tvParsed.setText("Failed to parse the result. The drivers' information does not exist in the barcode! ");
                }
            });
        }
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