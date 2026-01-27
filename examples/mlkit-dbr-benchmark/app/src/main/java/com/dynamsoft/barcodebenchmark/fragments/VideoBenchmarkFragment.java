package com.dynamsoft.barcodebenchmark.fragments;

import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.dynamsoft.barcodebenchmark.BenchmarkConfig;
import com.dynamsoft.barcodebenchmark.FileUtil;
import com.dynamsoft.barcodebenchmark.MainViewModel;
import com.dynamsoft.barcodebenchmark.R;
import com.dynamsoft.cvr.CaptureVisionRouter;
import com.dynamsoft.cvr.CapturedResult;
import com.dynamsoft.cvr.EnumPresetTemplate;
import com.dynamsoft.dbr.BarcodeResultItem;
import com.dynamsoft.dbr.DecodedBarcodesResult;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VideoBenchmarkFragment extends Fragment {

    private MainViewModel viewModel;
    private ImageView ivVideoThumbnail;
    private LinearLayout layoutPlaceholder;
    private LinearLayout layoutVideoInfo;
    private LinearLayout layoutProgress;
    private TextView tvVideoName;
    private TextView tvVideoDuration;
    private TextView tvProgressStatus;
    private TextView tvProgressDetail;
    private ProgressBar progressVideo;
    private Button btnSelectVideo;
    private Button btnRunBenchmark;

    private Uri selectedVideoUri;
    private long videoDurationMs;
    private int totalFrames;
    private ExecutorService executor;
    private CaptureVisionRouter cvRouter;
    private BarcodeScanner mlkitScanner;
    private volatile boolean isCancelled = false;

    // Frame extraction interval in milliseconds (process 2 frames per second)
    private static final long FRAME_INTERVAL_MS = 500;

    private final ActivityResultLauncher<String> videoPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    loadVideo(uri);
                }
            }
    );

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_video_benchmark, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        executor = Executors.newSingleThreadExecutor();

        // Initialize views
        ivVideoThumbnail = view.findViewById(R.id.iv_video_thumbnail);
        layoutPlaceholder = view.findViewById(R.id.layout_placeholder);
        layoutVideoInfo = view.findViewById(R.id.layout_video_info);
        layoutProgress = view.findViewById(R.id.layout_progress);
        tvVideoName = view.findViewById(R.id.tv_video_name);
        tvVideoDuration = view.findViewById(R.id.tv_video_duration);
        tvProgressStatus = view.findViewById(R.id.tv_progress_status);
        tvProgressDetail = view.findViewById(R.id.tv_progress_detail);
        progressVideo = view.findViewById(R.id.progress_video);
        btnSelectVideo = view.findViewById(R.id.btn_select_video);
        btnRunBenchmark = view.findViewById(R.id.btn_run_benchmark);

        // Initialize Dynamsoft CVR
        cvRouter = new CaptureVisionRouter(requireContext());
        if (BenchmarkConfig.USE_CUSTOM_TEMPLATE) {
            try {
                cvRouter.initSettings(BenchmarkConfig.DYNAMSOFT_TEMPLATE_JSON);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Initialize MLkit scanner
        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build();
        mlkitScanner = BarcodeScanning.getClient(options);

        btnSelectVideo.setOnClickListener(v -> videoPickerLauncher.launch("video/*"));

        btnRunBenchmark.setOnClickListener(v -> runBenchmark());
    }

    private void loadVideo(Uri uri) {
        selectedVideoUri = uri;
        try {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            retriever.setDataSource(requireContext(), uri);

            // Get duration
            String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            videoDurationMs = durationStr != null ? Long.parseLong(durationStr) : 0;
            
            // Calculate total frames to process
            totalFrames = (int) (videoDurationMs / FRAME_INTERVAL_MS);
            if (totalFrames == 0) totalFrames = 1;

            // Get thumbnail
            Bitmap thumbnail = retriever.getFrameAtTime(0);
            if (thumbnail != null) {
                ivVideoThumbnail.setImageBitmap(thumbnail);
            }

            retriever.release();

            // Update UI
            layoutPlaceholder.setVisibility(View.GONE);
            layoutVideoInfo.setVisibility(View.VISIBLE);
            
            String fileName = FileUtil.getFileName(requireContext(), uri);
            tvVideoName.setText(fileName);
            
            int seconds = (int) (videoDurationMs / 1000);
            int minutes = seconds / 60;
            seconds = seconds % 60;
            tvVideoDuration.setText(String.format("Duration: %d:%02d | ~%d frames to process", minutes, seconds, totalFrames));

            btnRunBenchmark.setEnabled(true);
            btnRunBenchmark.setAlpha(1.0f);

            viewModel.sourceFileUri = fileName;

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(requireContext(), "Error loading video: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void runBenchmark() {
        if (selectedVideoUri == null) {
            Toast.makeText(requireContext(), "Please select a video first", Toast.LENGTH_SHORT).show();
            return;
        }

        isCancelled = false;
        layoutProgress.setVisibility(View.VISIBLE);
        btnSelectVideo.setEnabled(false);
        btnRunBenchmark.setEnabled(false);

        viewModel.reset();
        viewModel.benchmarkMode = "video";

        executor.execute(() -> {
            try {
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                retriever.setDataSource(requireContext(), selectedVideoUri);

                // Extract frames and run benchmark
                List<Bitmap> frames = new ArrayList<>();
                for (int i = 0; i < totalFrames && !isCancelled; i++) {
                    long timeUs = i * FRAME_INTERVAL_MS * 1000; // Convert to microseconds
                    Bitmap frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST);
                    if (frame != null) {
                        frames.add(frame);
                    }

                    final int frameIndex = i + 1;
                    requireActivity().runOnUiThread(() -> {
                        progressVideo.setProgress((frameIndex * 50) / totalFrames); // First half is extraction
                        tvProgressStatus.setText("Extracting frames...");
                        tvProgressDetail.setText("Frame " + frameIndex + "/" + totalFrames);
                    });
                }

                retriever.release();

                if (isCancelled || frames.isEmpty()) {
                    resetUI();
                    return;
                }

                // Run Dynamsoft benchmark
                requireActivity().runOnUiThread(() -> {
                    tvProgressStatus.setText("Running Dynamsoft benchmark...");
                });
                MainViewModel.BenchmarkResult dynamsoftResult = runDynamsoftVideoBenchmark(frames);
                viewModel.dynamsoftResult = dynamsoftResult;

                // Run MLkit benchmark
                requireActivity().runOnUiThread(() -> {
                    tvProgressStatus.setText("Running MLkit benchmark...");
                    progressVideo.setProgress(75);
                });
                MainViewModel.BenchmarkResult mlkitResult = runMLkitVideoBenchmark(frames);
                viewModel.mlkitResult = mlkitResult;

                // Clean up bitmaps
                for (Bitmap frame : frames) {
                    frame.recycle();
                }

                // Navigate to results
                requireActivity().runOnUiThread(() -> {
                    resetUI();
                    if (getView() != null && !isCancelled) {
                        Navigation.findNavController(getView())
                                .navigate(R.id.action_videoBenchmark_to_result);
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), "Error processing video: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    resetUI();
                });
            }
        });
    }

    private void resetUI() {
        requireActivity().runOnUiThread(() -> {
            layoutProgress.setVisibility(View.GONE);
            btnSelectVideo.setEnabled(true);
            btnRunBenchmark.setEnabled(true);
            progressVideo.setProgress(0);
        });
    }

    private MainViewModel.BenchmarkResult runDynamsoftVideoBenchmark(List<Bitmap> frames) {
        MainViewModel.BenchmarkResult result = new MainViewModel.BenchmarkResult("Dynamsoft");
        result.framesProcessed = frames.size();
        
        Set<String> uniqueBarcodes = new HashSet<>();
        long totalTime = 0;

        for (int i = 0; i < frames.size() && !isCancelled; i++) {
            Bitmap frame = frames.get(i);
            
            final int frameIndex = i + 1;
            requireActivity().runOnUiThread(() -> {
                int progress = 50 + (frameIndex * 25) / frames.size();
                progressVideo.setProgress(progress);
                tvProgressDetail.setText("Dynamsoft: Frame " + frameIndex + "/" + frames.size());
            });

            try {
                long startTime = System.currentTimeMillis();
                
                CapturedResult capturedResult = cvRouter.capture(frame, EnumPresetTemplate.PT_READ_BARCODES);
                
                long endTime = System.currentTimeMillis();
                long decodeTime = endTime - startTime;
                totalTime += decodeTime;

                if (capturedResult != null) {
                    DecodedBarcodesResult barcodesResult = capturedResult.getDecodedBarcodesResult();
                    if (barcodesResult != null && barcodesResult.getItems() != null) {
                        for (BarcodeResultItem item : barcodesResult.getItems()) {
                            String key = item.getFormatString() + ":" + item.getText();
                            if (!uniqueBarcodes.contains(key)) {
                                uniqueBarcodes.add(key);
                                MainViewModel.BarcodeInfo info = new MainViewModel.BarcodeInfo(
                                        item.getFormatString(),
                                        item.getText(),
                                        decodeTime,
                                        i
                                );
                                result.barcodes.add(info);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        result.totalTimeMs = totalTime;
        return result;
    }

    private MainViewModel.BenchmarkResult runMLkitVideoBenchmark(List<Bitmap> frames) {
        MainViewModel.BenchmarkResult result = new MainViewModel.BenchmarkResult("MLkit");
        result.framesProcessed = frames.size();
        
        Set<String> uniqueBarcodes = new HashSet<>();
        long totalTime = 0;

        for (int i = 0; i < frames.size() && !isCancelled; i++) {
            Bitmap frame = frames.get(i);
            
            final int frameIndex = i + 1;
            requireActivity().runOnUiThread(() -> {
                int progress = 75 + (frameIndex * 25) / frames.size();
                progressVideo.setProgress(progress);
                tvProgressDetail.setText("MLkit: Frame " + frameIndex + "/" + frames.size());
            });

            try {
                InputImage image = InputImage.fromBitmap(frame, 0);
                
                long startTime = System.currentTimeMillis();
                
                List<Barcode> barcodes = com.google.android.gms.tasks.Tasks.await(mlkitScanner.process(image));
                
                long endTime = System.currentTimeMillis();
                long decodeTime = endTime - startTime;
                totalTime += decodeTime;

                if (barcodes != null) {
                    for (Barcode barcode : barcodes) {
                        String format = getBarcodeFormatName(barcode.getFormat());
                        String text = barcode.getRawValue() != null ? barcode.getRawValue() : "";
                        String key = format + ":" + text;
                        
                        if (!uniqueBarcodes.contains(key)) {
                            uniqueBarcodes.add(key);
                            MainViewModel.BarcodeInfo info = new MainViewModel.BarcodeInfo(
                                    format,
                                    text,
                                    decodeTime,
                                    i
                            );
                            result.barcodes.add(info);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        result.totalTimeMs = totalTime;
        return result;
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        isCancelled = true;
        if (executor != null) {
            executor.shutdown();
        }
        if (mlkitScanner != null) {
            mlkitScanner.close();
        }
    }
}
