package com.dynamsoft.dcv.driverslicensescanner.fragments;

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

import com.dynamsoft.dcv.driverslicensescanner.MainViewModel;
import com.dynamsoft.dcv.driverslicensescanner.R;

public class SelectionFragment extends Fragment {

    private MainViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_selection, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        android.widget.RadioGroup radioGroup = view.findViewById(R.id.radio_group_resolution);
        if (viewModel.resolutionIndex == 0) {
            radioGroup.check(R.id.radio_low);
        } else if (viewModel.resolutionIndex == 1) {
            radioGroup.check(R.id.radio_medium);
        } else {
            radioGroup.check(R.id.radio_high);
        }

        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radio_low) {
                viewModel.resolutionIndex = 0;
            } else if (checkedId == R.id.radio_medium) {
                viewModel.resolutionIndex = 1;
            } else if (checkedId == R.id.radio_high) {
                viewModel.resolutionIndex = 2;
            }
        });

        CardView cardDynamsoft = view.findViewById(R.id.card_dynamsoft);
        CardView cardGoogle = view.findViewById(R.id.card_google);

        cardDynamsoft.setOnClickListener(v -> {
            Navigation.findNavController(v).navigate(R.id.action_SelectionFragment_to_ScannerFragment);
        });

        cardGoogle.setOnClickListener(v -> {
            Navigation.findNavController(v).navigate(R.id.action_SelectionFragment_to_GoogleScannerFragment);
        });
    }
}
