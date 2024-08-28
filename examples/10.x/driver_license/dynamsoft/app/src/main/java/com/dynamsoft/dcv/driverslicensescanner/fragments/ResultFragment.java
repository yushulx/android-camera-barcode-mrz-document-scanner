package com.dynamsoft.dcv.driverslicensescanner.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.dynamsoft.dcv.driverslicensescanner.MainViewModel;
import com.dynamsoft.dcv.driverslicensescanner.databinding.FragmentResultBinding;

public class ResultFragment extends Fragment {
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        FragmentResultBinding binding = FragmentResultBinding.inflate(inflater, container, false);
        MainViewModel viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, viewModel.results);
        binding.lvResult.setAdapter(adapter);
        return binding.getRoot();
    }

}