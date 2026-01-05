package com.dynamsoft.documentscanner.scan;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.dynamsoft.core.basic_structures.CoreException;
import com.dynamsoft.core.basic_structures.ImageData;
import com.dynamsoft.ddn.EnumImageColourMode;
import com.dynamsoft.documentscanner.R;
import com.dynamsoft.documentscanner.utils.FileUtils;
import com.dynamsoft.utility.ImageProcessor;
import com.dynamsoft.utility.UtilityException;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.io.IOException;

public class ResultFragment extends Fragment {
    private DocumentScannerViewModel mViewModel;
    private PopupMenu mSwitchColorMenu;
    private ImageView mIvNormalized;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mViewModel = new ViewModelProvider(requireActivity()).get(DocumentScannerViewModel.class);
        mViewModel.actionBarTitle.setValue(requireContext().getString(R.string.result_page_title));

        return inflater.inflate(R.layout.fragment_result, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mIvNormalized = view.findViewById(R.id.iv_normalized);

        try {
            assert mViewModel.normalizedResultImage != null;
            mViewModel.showingImage = mViewModel.normalizedResultImage;
            mIvNormalized.setImageBitmap(mViewModel.showingImage.toBitmap());
        } catch (CoreException e) {
            throw new RuntimeException(e);
        }

        initSwitchColorMenu(view.findViewById(R.id.anchor_view));

        BottomNavigationView bottomView = view.findViewById(R.id.bottom_view);
        bottomView.setItemIconTintList(null);
        bottomView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.back_to_edit) {
                goToEditFragment();
            } else if (itemId == R.id.switch_colour_mode) {
                mSwitchColorMenu.show();
            } else if (itemId == R.id.export) {
                try {
                    FileUtils.saveImageToGallery(requireContext(), mViewModel.showingImage);
                } catch (IOException | CoreException | UtilityException e) {
                    throw new RuntimeException(e);
                }
            }
            return true;
        });
    }

    private void goToEditFragment() {
        requireActivity().runOnUiThread(() ->
                requireActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.container, new EditFragment())
                        .addToBackStack("EditFragment")
                        .commit());
    }

    private void initSwitchColorMenu(View anchorView) {
        mSwitchColorMenu = new PopupMenu(requireContext(), anchorView);
        mSwitchColorMenu.getMenuInflater().inflate(R.menu.color_selector, mSwitchColorMenu.getMenu());
        mSwitchColorMenu.setOnMenuItemClickListener(item -> {
            item.setChecked(true);
            int colorMode = item.getItemId() == R.id.item_colour ? EnumImageColourMode.ICM_COLOUR :
                    item.getItemId() == R.id.item_grayscale ? EnumImageColourMode.ICM_GRAYSCALE : EnumImageColourMode.ICM_BINARY;
            mViewModel.showingImage = changeImageColorMode(mViewModel.normalizedResultImage, colorMode);
            try {
                if (mViewModel.showingImage != null) {
                    mIvNormalized.setImageBitmap(mViewModel.showingImage.toBitmap());
                }
            } catch (CoreException e) {
                throw new RuntimeException(e);
            }
            return true;
        });
    }

    private ImageData changeImageColorMode(ImageData imageData, @EnumImageColourMode int colorMode) {
        if(imageData == null || colorMode == EnumImageColourMode.ICM_COLOUR) {
            return imageData;
        }
        ImageProcessor processor = new ImageProcessor();
        if(colorMode == EnumImageColourMode.ICM_GRAYSCALE) {
            return processor.convertToGray(imageData);
        } else { //colorMode == EnumImageColourMode.ICM_BINARY
            return processor.convertToBinaryLocal(imageData, 0, 15, false);
        }
    }

}