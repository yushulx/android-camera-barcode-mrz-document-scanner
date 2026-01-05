package com.dynamsoft.documentscanner.scan;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.dynamsoft.core.basic_structures.CompletionListener;
import com.dynamsoft.core.basic_structures.EnumCapturedResultItemType;
import com.dynamsoft.core.basic_structures.EnumCrossVerificationStatus;
import com.dynamsoft.cvr.CaptureVisionRouter;
import com.dynamsoft.cvr.CaptureVisionRouterException;
import com.dynamsoft.cvr.CapturedResultReceiver;
import com.dynamsoft.cvr.EnumPresetTemplate;
import com.dynamsoft.dce.CameraEnhancer;
import com.dynamsoft.dce.CameraView;
import com.dynamsoft.dce.utils.PermissionUtil;
import com.dynamsoft.ddn.ProcessedDocumentResult;
import com.dynamsoft.documentscanner.R;
import com.dynamsoft.license.LicenseManager;
import com.dynamsoft.utility.MultiFrameResultCrossFilter;

public class ScannerFragment extends Fragment {
    private DocumentScannerViewModel mViewModel;
    private CameraEnhancer mCamera;
    private CaptureVisionRouter mRouter;

    private boolean mIsBtnClicked = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        PermissionUtil.requestCameraPermission(requireActivity());

        mViewModel = new ViewModelProvider(requireActivity()).get(DocumentScannerViewModel.class);
        mViewModel.actionBarTitle.setValue(requireContext().getString(R.string.scan_page_title));

        if (savedInstanceState == null) {
            LicenseManager.initLicense("DLS2eyJvcmdhbml6YXRpb25JRCI6IjIwMDAwMSJ9", (isSuccess, error) -> {
                if (!isSuccess && error != null) {
                    error.printStackTrace();
                }
            });
        }
        return inflater.inflate(R.layout.fragment_scanner, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        CameraView cameraView = view.findViewById(R.id.cameraView);
        mCamera = new CameraEnhancer(cameraView, getViewLifecycleOwner());
        mRouter = new CaptureVisionRouter();

        MultiFrameResultCrossFilter filter = new MultiFrameResultCrossFilter();
        filter.enableResultCrossVerification(EnumCapturedResultItemType.CRIT_DESKEWED_IMAGE, true);
        mRouter.addResultFilter(filter);


        try {
            mRouter.setInput(mCamera);
        } catch (CaptureVisionRouterException e) {
            e.printStackTrace();
            return;
        }

        mRouter.addResultReceiver(new CapturedResultReceiver() {
            @Override
            public void onProcessedDocumentResultReceived(@NonNull ProcessedDocumentResult result) {
                if (result.getDeskewedImageResultItems().length > 0 &&
                        (mIsBtnClicked || result.getDeskewedImageResultItems()[0].getCrossVerificationStatus() == EnumCrossVerificationStatus.CVS_PASSED)) {
                    mIsBtnClicked = false;

                    mViewModel.normalizedResultImage = result.getDeskewedImageResultItems()[0].getImageData();
                    mViewModel.resultLocation = result.getDeskewedImageResultItems()[0].getSourceDeskewQuad();
                    mViewModel.originalImage = mRouter.getIntermediateResultManager().getOriginalImage(result.getOriginalImageHashId());

                    goToResultFragment();
                }
            }
        });

        view.findViewById(R.id.btn_capture).setOnClickListener(v -> mIsBtnClicked = true);
    }

    @Override
    public void onResume() {
        super.onResume();
        mCamera.open();
        mRouter.startCapturing(EnumPresetTemplate.PT_DETECT_AND_NORMALIZE_DOCUMENT, new CompletionListener() {
            @Override
            public void onSuccess() {
                /*no-op*/
            }

            @Override
            public void onFailure(int errorCode, String errorString) {
                mViewModel.startCapturingError.postValue(errorString);
            }
        });
    }

    @Override
    public void onPause() {
        super.onPause();
        mCamera.close();
        mRouter.stopCapturing();
    }

    private void goToResultFragment() {
        requireActivity().runOnUiThread(() ->
                requireActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.container, new ResultFragment())
                        .addToBackStack("ResultFragment")
                        .commit());
    }
}