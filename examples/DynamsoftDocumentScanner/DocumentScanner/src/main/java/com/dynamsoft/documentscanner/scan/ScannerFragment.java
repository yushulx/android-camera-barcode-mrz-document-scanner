package com.dynamsoft.documentscanner.scan;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dynamsoft.core.basic_structures.CompletionListener;
import com.dynamsoft.core.basic_structures.EnumCapturedResultItemType;
import com.dynamsoft.core.basic_structures.EnumCrossVerificationStatus;
import com.dynamsoft.core.basic_structures.ImageData;
import com.dynamsoft.core.basic_structures.Quadrilateral;
import com.dynamsoft.cvr.CaptureVisionRouter;
import com.dynamsoft.cvr.CaptureVisionRouterException;
import com.dynamsoft.cvr.CapturedResult;
import com.dynamsoft.cvr.CapturedResultReceiver;
import com.dynamsoft.cvr.EnumPresetTemplate;
import com.dynamsoft.dce.CameraEnhancer;
import com.dynamsoft.dce.CameraView;
import com.dynamsoft.dce.utils.PermissionUtil;
import com.dynamsoft.ddn.DeskewedImageResultItem;
import com.dynamsoft.ddn.ProcessedDocumentResult;
import com.dynamsoft.documentscanner.R;
import com.dynamsoft.documentscanner.utils.QuadStabilizer;
import com.dynamsoft.license.LicenseManager;
import com.dynamsoft.utility.MultiFrameResultCrossFilter;

import androidx.exifinterface.media.ExifInterface;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class ScannerFragment extends Fragment {
    private DocumentScannerViewModel mViewModel;
    private CameraEnhancer mCamera;
    private CaptureVisionRouter mRouter;
    private QuadStabilizer mQuadStabilizer;

    private ThumbnailAdapter mThumbnailAdapter;
    private RecyclerView mRvThumbnails;
    private ImageButton mBtnNext;
    private TextView mTvAutoIndicator;

    private boolean mIsBtnClicked = false;
    private boolean mCooldown = false;
    private boolean mIsRetakeMode = false;
    private int mRetakeIndex = -1;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    // Timeout runnable for capture fallback (capture raw frame if no quad detected)
    private final Runnable mCaptureTimeoutRunnable = () -> {
        if (mIsBtnClicked) {
            mIsBtnClicked = false;
            captureRawFrame();
        }
    };

    // Store the latest result for manual capture fallback
    private volatile DeskewedImageResultItem mLatestDeskewedItem = null;
    private volatile String mLatestOriginalImageHashId = null;

    private final ActivityResultLauncher<Intent> mGalleryLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    if (imageUri != null) {
                        processGalleryImage(imageUri);
                    }
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        PermissionUtil.requestCameraPermission(requireActivity());

        mViewModel = new ViewModelProvider(requireActivity()).get(DocumentScannerViewModel.class);
        mViewModel.actionBarTitle.setValue(requireContext().getString(R.string.scan_page_title));

        if (savedInstanceState == null) {
            LicenseManager.initLicense("DLS2eyJoYW5kc2hha2VDb2RlIjoiMjAwMDAxLTE2NDk4Mjk3OTI2MzUiLCJvcmdhbml6YXRpb25JRCI6IjIwMDAwMSIsInNlc3Npb25QYXNzd29yZCI6IndTcGR6Vm05WDJrcEQ5YUoifQ==", (isSuccess, error) -> {
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

        // Initialize quad stabilizer
        mQuadStabilizer = new QuadStabilizer(requireContext());
        mQuadStabilizer.setCallback(this::onAutoCapture);

        // Camera setup
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
                if (result.getDeskewedImageResultItems().length > 0) {
                    DeskewedImageResultItem item = result.getDeskewedImageResultItems()[0];

                    // Store latest for manual capture
                    mLatestDeskewedItem = item;
                    mLatestOriginalImageHashId = result.getOriginalImageHashId();

                    if (mIsBtnClicked) {
                        // Manual capture — use this fresh detection
                        mIsBtnClicked = false;
                        mHandler.removeCallbacks(mCaptureTimeoutRunnable);
                        captureResult(item, result.getOriginalImageHashId());
                    } else if (item.getCrossVerificationStatus() == EnumCrossVerificationStatus.CVS_PASSED) {
                        // Feed to stabilizer for auto-capture
                        Quadrilateral quad = item.getSourceDeskewQuad();
                        if (quad != null) {
                            mQuadStabilizer.feedQuad(quad);
                        }
                    }
                }
            }
        });

        // Thumbnail bar
        mRvThumbnails = view.findViewById(R.id.rv_thumbnails);
        mRvThumbnails.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        mThumbnailAdapter = new ThumbnailAdapter();
        mThumbnailAdapter.setListener(position -> {
            mViewModel.removePage(position);
        });
        mRvThumbnails.setAdapter(mThumbnailAdapter);

        // Auto-capture indicator
        mTvAutoIndicator = view.findViewById(R.id.tv_auto_capture_indicator);

        // Check retake mode
        Integer retakeIndex = mViewModel.retakePageIndex.getValue();
        mIsRetakeMode = (retakeIndex != null && retakeIndex >= 0);
        mRetakeIndex = mIsRetakeMode ? retakeIndex : -1;

        // Next button
        mBtnNext = view.findViewById(R.id.btn_next);
        mBtnNext.setOnClickListener(v -> goToResultFragment());

        if (mIsRetakeMode) {
            mBtnNext.setVisibility(View.GONE);
            mRvThumbnails.setVisibility(View.GONE);
        }

        // Capture button — always require fresh detection
        view.findViewById(R.id.btn_capture).setOnClickListener(v -> {
            if (mCooldown) return;
            mIsBtnClicked = true;
            mLatestDeskewedItem = null;
            mLatestOriginalImageHashId = null;
            // If no detection within 500ms, capture raw frame
            mHandler.postDelayed(mCaptureTimeoutRunnable, 500);
        });

        // Gallery button
        view.findViewById(R.id.btn_gallery).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            mGalleryLauncher.launch(Intent.createChooser(intent, "Select Image"));
        });

        // Settings button
        view.findViewById(R.id.btn_settings).setOnClickListener(v -> {
            StabilizationSettingsDialog dialog = new StabilizationSettingsDialog();
            dialog.setOnSettingsChangedListener(() -> {
                mQuadStabilizer.loadSettings(requireContext());
                mQuadStabilizer.reset();
            });
            dialog.show(getChildFragmentManager(), "stabilization_settings");
        });

        // Observe pages
        mViewModel.pages.observe(getViewLifecycleOwner(), pages -> {
            if (mIsRetakeMode) return;
            mThumbnailAdapter.updatePages(pages);
            boolean hasPages = pages != null && !pages.isEmpty();
            mBtnNext.setEnabled(hasPages);
            mBtnNext.setAlpha(hasPages ? 1.0f : 0.3f);
            if (hasPages) {
                mRvThumbnails.scrollToPosition(pages.size() - 1);
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        mCamera.open();
        mRouter.startCapturing(EnumPresetTemplate.PT_DETECT_AND_NORMALIZE_DOCUMENT, new CompletionListener() {
            @Override
            public void onSuccess() { }

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

    private void onAutoCapture() {
        if (mCooldown || mLatestDeskewedItem == null) return;
        captureResult(mLatestDeskewedItem, mLatestOriginalImageHashId);

        // Show indicator
        mHandler.post(() -> {
            if (mTvAutoIndicator != null) {
                mTvAutoIndicator.setVisibility(View.VISIBLE);
                mHandler.postDelayed(() -> {
                    if (mTvAutoIndicator != null) {
                        mTvAutoIndicator.setVisibility(View.GONE);
                    }
                }, 1500);
            }
        });
    }

    private void captureResult(DeskewedImageResultItem item, String originalImageHashId) {
        if (mCooldown) return;
        mCooldown = true;

        ImageData normalizedImage = item.getImageData();
        Quadrilateral quad = item.getSourceDeskewQuad();
        ImageData originalImage = mRouter.getIntermediateResultManager().getOriginalImage(originalImageHashId);

        DocumentPage page = new DocumentPage(originalImage, normalizedImage, quad);

        if (mIsRetakeMode && mRetakeIndex >= 0) {
            mViewModel.replacePage(mRetakeIndex, page);
            mViewModel.retakePageIndex.postValue(-1);
            mHandler.post(() -> requireActivity().getSupportFragmentManager().popBackStack());
        } else {
            mViewModel.addPage(page);
        }

        mQuadStabilizer.reset();

        // Cooldown to prevent duplicate captures
        mHandler.postDelayed(() -> mCooldown = false, 1500);
    }

    private void captureRawFrame() {
        if (mCooldown) return;
        mCooldown = true;

        try {
            ImageData frame = mCamera.getImage();
            if (frame != null) {
                DocumentPage page = new DocumentPage(frame, frame, null);
                if (mIsRetakeMode && mRetakeIndex >= 0) {
                    mViewModel.replacePage(mRetakeIndex, page);
                    mViewModel.retakePageIndex.postValue(-1);
                    mHandler.post(() -> requireActivity().getSupportFragmentManager().popBackStack());
                } else {
                    mViewModel.addPage(page);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        mHandler.postDelayed(() -> mCooldown = false, 1500);
    }

    private void processGalleryImage(Uri imageUri) {
        try {
            // Read EXIF orientation before decoding
            int exifRotation = 0;
            try (InputStream exifStream = requireContext().getContentResolver().openInputStream(imageUri)) {
                if (exifStream != null) {
                    ExifInterface exif = new ExifInterface(exifStream);
                    int orientation = exif.getAttributeInt(
                            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                    switch (orientation) {
                        case ExifInterface.ORIENTATION_ROTATE_90:  exifRotation = 90;  break;
                        case ExifInterface.ORIENTATION_ROTATE_180: exifRotation = 180; break;
                        case ExifInterface.ORIENTATION_ROTATE_270: exifRotation = 270; break;
                    }
                }
            } catch (IOException ignored) { }

            // Decode bitmap
            Bitmap bitmap;
            try (InputStream decodeStream = requireContext().getContentResolver().openInputStream(imageUri)) {
                if (decodeStream == null) return;
                bitmap = BitmapFactory.decodeStream(decodeStream);
            }
            if (bitmap == null) return;

            // Apply EXIF rotation so the image is correctly oriented
            if (exifRotation != 0) {
                android.graphics.Matrix m = new android.graphics.Matrix();
                m.postRotate(exifRotation);
                Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);
                if (rotated != bitmap) bitmap.recycle();
                bitmap = rotated;
            }

            // Feed corrected bitmap to Dynamsoft for document detection
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, baos);
            byte[] jpegBytes = baos.toByteArray();

            CapturedResult capturedResult = mRouter.capture(jpegBytes, EnumPresetTemplate.PT_DETECT_AND_NORMALIZE_DOCUMENT);
            if (capturedResult != null) {
                ProcessedDocumentResult docResult = capturedResult.getProcessedDocumentResult();
                if (docResult != null && docResult.getDeskewedImageResultItems().length > 0) {
                    DeskewedImageResultItem deskewedItem = docResult.getDeskewedImageResultItems()[0];
                    ImageData normalizedImage = deskewedItem.getImageData();
                    Quadrilateral quad = deskewedItem.getSourceDeskewQuad();
                    DocumentPage page = new DocumentPage(null, normalizedImage, quad);
                    bitmap.recycle();
                    mViewModel.addPage(page);
                    return;
                }
            }

            // No document detected — store the orientation-corrected bitmap directly
            Toast.makeText(requireContext(), R.string.no_document_detected, Toast.LENGTH_SHORT).show();
            DocumentPage page = new DocumentPage(bitmap);
            mViewModel.addPage(page);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void goToResultFragment() {
        requireActivity().runOnUiThread(() ->
                requireActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.container, new ResultFragment())
                        .addToBackStack("ResultFragment")
                        .commit());
    }
}