package com.dynamsoft.barcodebenchmark.fragments;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

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

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ImageBenchmarkFragment extends Fragment {

    private MainViewModel viewModel;
    private ImageView ivPreview;
    private LinearLayout layoutPlaceholder;
    private FrameLayout layoutLoading;
    private TextView tvLoadingStatus;
    private Button btnSelectImage;
    private Button btnRunBenchmark;
    
    private Bitmap selectedBitmap;
    private Uri selectedImageUri;
    private ExecutorService executor;
    private CaptureVisionRouter cvRouter;
    private BarcodeScanner mlkitScanner;

    private final ActivityResultLauncher<String> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    loadImage(uri);
                }
            }
    );

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_image_benchmark, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        executor = Executors.newSingleThreadExecutor();

        // Initialize views
        ivPreview = view.findViewById(R.id.iv_preview);
        layoutPlaceholder = view.findViewById(R.id.layout_placeholder);
        layoutLoading = view.findViewById(R.id.layout_loading);
        tvLoadingStatus = view.findViewById(R.id.tv_loading_status);
        btnSelectImage = view.findViewById(R.id.btn_select_image);
        btnRunBenchmark = view.findViewById(R.id.btn_run_benchmark);

        // Initialize Dynamsoft CVR
        cvRouter = new CaptureVisionRouter(requireContext());

        // Initialize MLkit scanner
        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build();
        mlkitScanner = BarcodeScanning.getClient(options);

        btnSelectImage.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));

        btnRunBenchmark.setOnClickListener(v -> runBenchmark());
    }

    private void loadImage(Uri uri) {
        selectedImageUri = uri;
        try {
            InputStream inputStream = requireContext().getContentResolver().openInputStream(uri);
            selectedBitmap = BitmapFactory.decodeStream(inputStream);
            if (inputStream != null) {
                inputStream.close();
            }

            if (selectedBitmap != null) {
                ivPreview.setImageBitmap(selectedBitmap);
                layoutPlaceholder.setVisibility(View.GONE);
                btnRunBenchmark.setEnabled(true);
                btnRunBenchmark.setAlpha(1.0f);
                
                viewModel.sourceFileUri = FileUtil.getFileName(requireContext(), uri);
            } else {
                Toast.makeText(requireContext(), "Failed to load image", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(requireContext(), "Error loading image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void runBenchmark() {
        if (selectedBitmap == null) {
            Toast.makeText(requireContext(), "Please select an image first", Toast.LENGTH_SHORT).show();
            return;
        }

        layoutLoading.setVisibility(View.VISIBLE);
        btnSelectImage.setEnabled(false);
        btnRunBenchmark.setEnabled(false);

        viewModel.reset();
        viewModel.benchmarkMode = "image";
        viewModel.sourceFileUri = FileUtil.getFileName(requireContext(), selectedImageUri);

        executor.execute(() -> {
            // Run Dynamsoft benchmark
            requireActivity().runOnUiThread(() -> tvLoadingStatus.setText("Processing with Dynamsoft..."));
            MainViewModel.BenchmarkResult dynamsoftResult = runDynamsoftBenchmark(selectedBitmap);
            viewModel.dynamsoftResult = dynamsoftResult;

            // Run MLkit benchmark
            requireActivity().runOnUiThread(() -> tvLoadingStatus.setText("Processing with MLkit..."));
            MainViewModel.BenchmarkResult mlkitResult = runMLkitBenchmark(selectedBitmap);
            viewModel.mlkitResult = mlkitResult;

            // Navigate to results
            requireActivity().runOnUiThread(() -> {
                layoutLoading.setVisibility(View.GONE);
                btnSelectImage.setEnabled(true);
                btnRunBenchmark.setEnabled(true);
                
                if (getView() != null) {
                    Navigation.findNavController(getView())
                            .navigate(R.id.action_imageBenchmark_to_result);
                }
            });
        });
    }

    private MainViewModel.BenchmarkResult runDynamsoftBenchmark(Bitmap bitmap) {
        MainViewModel.BenchmarkResult result = new MainViewModel.BenchmarkResult("Dynamsoft");
        result.framesProcessed = 1;

        try {
            long startTime = System.currentTimeMillis();
            
            CapturedResult capturedResult = cvRouter.capture(bitmap, EnumPresetTemplate.PT_READ_BARCODES);
            
            long endTime = System.currentTimeMillis();
            result.totalTimeMs = endTime - startTime;

            if (capturedResult != null) {
                DecodedBarcodesResult barcodesResult = capturedResult.getDecodedBarcodesResult();
                if (barcodesResult != null && barcodesResult.getItems() != null) {
                    for (BarcodeResultItem item : barcodesResult.getItems()) {
                        MainViewModel.BarcodeInfo info = new MainViewModel.BarcodeInfo(
                                item.getFormatString(),
                                item.getText(),
                                result.totalTimeMs
                        );
                        result.barcodes.add(info);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }

    private MainViewModel.BenchmarkResult runMLkitBenchmark(Bitmap bitmap) {
        MainViewModel.BenchmarkResult result = new MainViewModel.BenchmarkResult("MLkit");
        result.framesProcessed = 1;

        try {
            InputImage image = InputImage.fromBitmap(bitmap, 0);
            
            long startTime = System.currentTimeMillis();
            
            List<Barcode> barcodes = com.google.android.gms.tasks.Tasks.await(mlkitScanner.process(image));
            
            long endTime = System.currentTimeMillis();
            result.totalTimeMs = endTime - startTime;

            if (barcodes != null) {
                for (Barcode barcode : barcodes) {
                    String format = getBarcodeFormatName(barcode.getFormat());
                    MainViewModel.BarcodeInfo info = new MainViewModel.BarcodeInfo(
                            format,
                            barcode.getRawValue() != null ? barcode.getRawValue() : "",
                            result.totalTimeMs
                    );
                    result.barcodes.add(info);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

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
        if (executor != null) {
            executor.shutdown();
        }
        if (mlkitScanner != null) {
            mlkitScanner.close();
        }
    }
}
