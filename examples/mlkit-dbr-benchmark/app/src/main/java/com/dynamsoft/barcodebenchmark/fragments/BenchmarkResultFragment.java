package com.dynamsoft.barcodebenchmark.fragments;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.dynamsoft.barcodebenchmark.BenchmarkConfig;
import com.dynamsoft.barcodebenchmark.MainViewModel;
import com.dynamsoft.barcodebenchmark.R;

public class BenchmarkResultFragment extends Fragment {

    private MainViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_benchmark_result, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        // Handle back button
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        NavHostFragment.findNavController(BenchmarkResultFragment.this)
                                .popBackStack(R.id.HomeFragment, false);
                    }
                });

        // Source info
        TextView tvSourceInfo = view.findViewById(R.id.tv_source_info);
        String sourceInfo = viewModel.benchmarkMode.equals("video") ? "Video: " : "Image: ";
        sourceInfo += viewModel.sourceFileUri != null ? viewModel.sourceFileUri : "Unknown";
        tvSourceInfo.setText(sourceInfo);

        // Time results card
        View timeCard = view.findViewById(R.id.card_time_comparison);
        TextView tvDynamsoftTime = view.findViewById(R.id.tv_dynamsoft_time);
        TextView tvMlkitTime = view.findViewById(R.id.tv_mlkit_time);
        ProgressBar progressDynamsoftTime = view.findViewById(R.id.progress_dynamsoft_time);
        ProgressBar progressMlkitTime = view.findViewById(R.id.progress_mlkit_time);

        if (BenchmarkConfig.SHOW_BENCHMARK_TIME) {
            timeCard.setVisibility(View.VISIBLE);
            
            long dynamsoftTime = viewModel.dynamsoftResult != null ? viewModel.dynamsoftResult.totalTimeMs : 0;
            long mlkitTime = viewModel.mlkitResult != null ? viewModel.mlkitResult.totalTimeMs : 0;
            
            if (viewModel.benchmarkMode.equals("video") && viewModel.dynamsoftResult != null && viewModel.mlkitResult != null) {
                // Show average time for video
                double dynamsoftAvg = viewModel.dynamsoftResult.getAvgTimePerFrame();
                double mlkitAvg = viewModel.mlkitResult.getAvgTimePerFrame();
                tvDynamsoftTime.setText(String.format("%.1f ms/frame (total: %d ms)", dynamsoftAvg, dynamsoftTime));
                tvMlkitTime.setText(String.format("%.1f ms/frame (total: %d ms)", mlkitAvg, mlkitTime));
            } else {
                tvDynamsoftTime.setText(dynamsoftTime + " ms");
                tvMlkitTime.setText(mlkitTime + " ms");
            }

            // Calculate progress bar values (winner gets 100%, loser gets proportional)
            long maxTime = Math.max(dynamsoftTime, mlkitTime);
            if (maxTime > 0) {
                progressDynamsoftTime.setProgress((int) (dynamsoftTime * 100 / maxTime));
                progressMlkitTime.setProgress((int) (mlkitTime * 100 / maxTime));
            }
        } else {
            // Hide entire time card when benchmark time is disabled
            timeCard.setVisibility(View.GONE);
        }

        // Detection count
        TextView tvDynamsoftCount = view.findViewById(R.id.tv_dynamsoft_count);
        TextView tvMlkitCount = view.findViewById(R.id.tv_mlkit_count);

        int dynamsoftCount = viewModel.dynamsoftResult != null ? viewModel.dynamsoftResult.barcodes.size() : 0;
        int mlkitCount = viewModel.mlkitResult != null ? viewModel.mlkitResult.barcodes.size() : 0;

        tvDynamsoftCount.setText(String.valueOf(dynamsoftCount));
        tvMlkitCount.setText(String.valueOf(mlkitCount));

        // Video stats
        LinearLayout layoutVideoStats = view.findViewById(R.id.layout_video_stats);
        TextView tvFramesProcessed = view.findViewById(R.id.tv_frames_processed);
        
        if (viewModel.benchmarkMode.equals("video")) {
            layoutVideoStats.setVisibility(View.VISIBLE);
            int frames = viewModel.dynamsoftResult != null ? viewModel.dynamsoftResult.framesProcessed : 0;
            tvFramesProcessed.setText(String.valueOf(frames));
        }

        // Populate barcode lists
        LinearLayout layoutDynamsoftBarcodes = view.findViewById(R.id.layout_dynamsoft_barcodes);
        LinearLayout layoutMlkitBarcodes = view.findViewById(R.id.layout_mlkit_barcodes);

        // Clear default "no barcodes" text if we have results
        if (dynamsoftCount > 0) {
            layoutDynamsoftBarcodes.removeAllViews();
            for (MainViewModel.BarcodeInfo info : viewModel.dynamsoftResult.barcodes) {
                addBarcodeView(layoutDynamsoftBarcodes, info, "#1976D2");
            }
        }

        if (mlkitCount > 0) {
            layoutMlkitBarcodes.removeAllViews();
            for (MainViewModel.BarcodeInfo info : viewModel.mlkitResult.barcodes) {
                addBarcodeView(layoutMlkitBarcodes, info, "#4CAF50");
            }
        }

        // Back button
        Button btnBack = view.findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> 
                NavHostFragment.findNavController(this).popBackStack(R.id.HomeFragment, false));
    }

    private void addBarcodeView(LinearLayout parent, MainViewModel.BarcodeInfo info, String accentColor) {
        LinearLayout itemLayout = new LinearLayout(requireContext());
        itemLayout.setOrientation(LinearLayout.VERTICAL);
        itemLayout.setBackgroundColor(0xFFF5F5F5);
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = (int) (8 * getResources().getDisplayMetrics().density);
        itemLayout.setLayoutParams(params);
        itemLayout.setPadding(
                (int) (12 * getResources().getDisplayMetrics().density),
                (int) (10 * getResources().getDisplayMetrics().density),
                (int) (12 * getResources().getDisplayMetrics().density),
                (int) (10 * getResources().getDisplayMetrics().density)
        );

        // Format label
        TextView formatView = new TextView(requireContext());
        formatView.setText(info.format);
        formatView.setTextSize(12);
        formatView.setTextColor(android.graphics.Color.parseColor(accentColor));
        formatView.setTypeface(null, Typeface.BOLD);

        // Text value
        TextView textView = new TextView(requireContext());
        String displayText = info.text;
        if (displayText.length() > 100) {
            displayText = displayText.substring(0, 100) + "...";
        }
        textView.setText(displayText);
        textView.setTextSize(14);
        textView.setTextColor(0xFF212121);
        
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        textParams.topMargin = (int) (4 * getResources().getDisplayMetrics().density);
        textView.setLayoutParams(textParams);

        itemLayout.addView(formatView);
        itemLayout.addView(textView);

        parent.addView(itemLayout);
    }
}
