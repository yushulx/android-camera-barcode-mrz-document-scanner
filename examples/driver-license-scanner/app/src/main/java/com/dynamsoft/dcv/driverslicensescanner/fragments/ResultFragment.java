package com.dynamsoft.dcv.driverslicensescanner.fragments;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.dynamsoft.dcv.driverslicensescanner.MainViewModel;
import com.dynamsoft.dcv.driverslicensescanner.R;
import com.dynamsoft.dcv.driverslicensescanner.databinding.FragmentResultBinding;

public class ResultFragment extends Fragment {
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        FragmentResultBinding binding = FragmentResultBinding.inflate(inflater, container, false);
        MainViewModel viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        // Handle back button to go to home
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        NavHostFragment.findNavController(ResultFragment.this)
                                .popBackStack(R.id.SelectionFragment, false);
                    }
                });

        // Populate the card sections with driver license data
        LinearLayout personalInfoLayout = binding.layoutPersonalInfo;
        LinearLayout addressInfoLayout = binding.layoutAddressInfo;
        LinearLayout licenseInfoLayout = binding.layoutLicenseInfo;

        // Parse and add fields to appropriate sections
        for (String item : viewModel.results) {
            String[] parts = item.split(":", 2);
            if (parts.length == 2) {
                String label = parts[0].trim();
                String value = parts[1].trim();

                // Categorize fields into appropriate sections
                if (isPersonalInfo(label)) {
                    addField(personalInfoLayout, label, value);
                } else if (isAddressInfo(label)) {
                    addField(addressInfoLayout, label, value);
                } else {
                    addField(licenseInfoLayout, label, value);
                }
            }
        }

        return binding.getRoot();
    }

    private boolean isPersonalInfo(String label) {
        return label.contains("Name") || label.contains("Gender") ||
                label.contains("Birth") || label.contains("Sex");
    }

    private boolean isAddressInfo(String label) {
        return label.contains("Address") || label.contains("Street") ||
                label.contains("City") || label.contains("State") || label.contains("Zip");
    }

    private void addField(LinearLayout parent, String label, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }

        LinearLayout fieldLayout = new LinearLayout(requireContext());
        fieldLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = (int) (12 * getResources().getDisplayMetrics().density);
        fieldLayout.setLayoutParams(params);

        TextView labelView = new TextView(requireContext());
        labelView.setText(label);
        labelView.setTextSize(12);
        labelView.setTextColor(0xFF757575);
        labelView.setTypeface(null, Typeface.BOLD);

        TextView valueView = new TextView(requireContext());
        valueView.setText(value);
        valueView.setTextSize(16);
        valueView.setTextColor(0xFF212121);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        valueParams.topMargin = (int) (4 * getResources().getDisplayMetrics().density);
        valueView.setLayoutParams(valueParams);

        fieldLayout.addView(labelView);
        fieldLayout.addView(valueView);
        parent.addView(fieldLayout);
    }

}