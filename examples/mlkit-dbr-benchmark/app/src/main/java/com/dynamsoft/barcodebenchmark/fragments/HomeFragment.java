package com.dynamsoft.barcodebenchmark.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.dynamsoft.barcodebenchmark.MainViewModel;
import com.dynamsoft.barcodebenchmark.R;

public class HomeFragment extends Fragment {

    private MainViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        viewModel.reset();

        // Resolution selection
        android.widget.RadioGroup radioGroup = view.findViewById(R.id.radio_group_resolution);
        if (viewModel.resolutionIndex == 0) {
            radioGroup.check(R.id.radio_720p);
        } else {
            radioGroup.check(R.id.radio_1080p);
        }

        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radio_720p) {
                viewModel.resolutionIndex = 0;
            } else if (checkedId == R.id.radio_1080p) {
                viewModel.resolutionIndex = 1;
            }
        });

        // Image benchmark card
        CardView cardImage = view.findViewById(R.id.card_image);
        cardImage.setOnClickListener(v -> {
            viewModel.benchmarkMode = "image";
            Navigation.findNavController(v).navigate(R.id.action_home_to_imageBenchmark);
        });

        // Video benchmark card
        CardView cardVideo = view.findViewById(R.id.card_video);
        cardVideo.setOnClickListener(v -> {
            viewModel.benchmarkMode = "video";
            Navigation.findNavController(v).navigate(R.id.action_home_to_videoBenchmark);
        });

        // Dynamsoft camera card
        CardView cardDynamsoft = view.findViewById(R.id.card_dynamsoft);
        cardDynamsoft.setOnClickListener(v -> {
            viewModel.benchmarkMode = "camera";
            Navigation.findNavController(v).navigate(R.id.action_home_to_dynamsoftScanner);
        });

        // MLkit camera card
        CardView cardGoogle = view.findViewById(R.id.card_google);
        cardGoogle.setOnClickListener(v -> {
            viewModel.benchmarkMode = "camera";
            Navigation.findNavController(v).navigate(R.id.action_home_to_mlkitScanner);
        });
    }
}
