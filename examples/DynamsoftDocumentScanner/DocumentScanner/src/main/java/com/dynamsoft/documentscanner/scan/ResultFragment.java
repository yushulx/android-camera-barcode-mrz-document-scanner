package com.dynamsoft.documentscanner.scan;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.dynamsoft.ddn.EnumImageColourMode;
import com.dynamsoft.documentscanner.R;
import com.dynamsoft.documentscanner.utils.FileUtils;
import com.google.android.material.button.MaterialButton;

import java.util.List;
import java.util.Locale;

public class ResultFragment extends Fragment {
    private DocumentScannerViewModel mViewModel;
    private ResultPagerAdapter mPagerAdapter;
    private ViewPager2 mViewPager;
    private TextView mTvPageIndicator;
    private ImageButton mBtnEdit;
    private MaterialButton mBtnFilterColor, mBtnFilterGrayscale, mBtnFilterBinary;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mViewModel = new ViewModelProvider(requireActivity()).get(DocumentScannerViewModel.class);
        mViewModel.actionBarTitle.setValue(requireContext().getString(R.string.result_page_title));
        return inflater.inflate(R.layout.fragment_result, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mTvPageIndicator = view.findViewById(R.id.tv_page_indicator);

        // ViewPager2
        mViewPager = view.findViewById(R.id.view_pager);
        mPagerAdapter = new ResultPagerAdapter();
        mViewPager.setAdapter(mPagerAdapter);

        mViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                mViewModel.selectedPageIndex.setValue(position);
                updatePageIndicator(position);
                updateFilterButtonStates(position);
                updateEditButtonState(position);
            }
        });

        // Top action bar
        ImageButton btnContinue = view.findViewById(R.id.btn_continue);
        ImageButton btnRetake = view.findViewById(R.id.btn_retake);
        mBtnEdit = view.findViewById(R.id.btn_edit);
        ImageButton btnRotate = view.findViewById(R.id.btn_rotate);
        ImageButton btnSort = view.findViewById(R.id.btn_sort);
        ImageButton btnSave = view.findViewById(R.id.btn_save);

        // Continue shooting — go back to scanner to add more images
        btnContinue.setOnClickListener(v -> {
            mViewModel.retakePageIndex.setValue(-1);
            requireActivity().getSupportFragmentManager().popBackStack();
        });

        // Retake — replace current page
        btnRetake.setOnClickListener(v -> {
            int currentIndex = mViewPager.getCurrentItem();
            mViewModel.retakePageIndex.setValue(currentIndex);
            requireActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container, new ScannerFragment())
                    .addToBackStack("RetakeFragment")
                    .commit();
        });

        // Edit quad
        mBtnEdit.setOnClickListener(v -> {
            int currentIndex = mViewPager.getCurrentItem();
            DocumentPage page = mViewModel.getPage(currentIndex);
            if (page != null && page.hasOriginalImage()) {
                requireActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.container, new EditFragment())
                        .addToBackStack("EditFragment")
                        .commit();
            }
        });

        btnRotate.setOnClickListener(v -> {
            int currentIndex = mViewPager.getCurrentItem();
            DocumentPage page = mViewModel.getPage(currentIndex);
            if (page != null) {
                page.rotate90();
                mPagerAdapter.notifyPageChanged(currentIndex);
            }
        });

        btnSort.setOnClickListener(v -> {
            requireActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container, new SortPagesFragment())
                    .addToBackStack("SortPagesFragment")
                    .commit();
        });

        btnSave.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(requireContext(), v);
            popup.getMenuInflater().inflate(R.menu.menu_save, popup.getMenu());
            popup.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == R.id.action_export_pdf) {
                    exportPdf();
                    return true;
                } else if (id == R.id.action_export_images) {
                    exportImages();
                    return true;
                }
                return false;
            });
            popup.show();
        });

        // Filter buttons
        mBtnFilterColor = view.findViewById(R.id.btn_filter_color);
        mBtnFilterGrayscale = view.findViewById(R.id.btn_filter_grayscale);
        mBtnFilterBinary = view.findViewById(R.id.btn_filter_binary);

        mBtnFilterColor.setOnClickListener(v -> applyFilter(EnumImageColourMode.ICM_COLOUR));
        mBtnFilterGrayscale.setOnClickListener(v -> applyFilter(EnumImageColourMode.ICM_GRAYSCALE));
        mBtnFilterBinary.setOnClickListener(v -> applyFilter(EnumImageColourMode.ICM_BINARY));

        // Observe pages
        mViewModel.pages.observe(getViewLifecycleOwner(), pages -> {
            mPagerAdapter.updatePages(pages);
            if (pages != null && !pages.isEmpty()) {
                Integer selectedIndex = mViewModel.selectedPageIndex.getValue();
                int index = (selectedIndex != null) ? selectedIndex : 0;
                if (index >= pages.size()) index = pages.size() - 1;
                mViewPager.setCurrentItem(index, false);
                updatePageIndicator(index);
                updateFilterButtonStates(index);
                updateEditButtonState(index);
            }
        });
    }

    private void updateEditButtonState(int position) {
        DocumentPage page = mViewModel.getPage(position);
        boolean canEdit = (page != null && page.hasOriginalImage());
        mBtnEdit.setEnabled(canEdit);
        mBtnEdit.setAlpha(canEdit ? 1.0f : 0.3f);
    }

    private void applyFilter(int colorMode) {
        int currentIndex = mViewPager.getCurrentItem();
        DocumentPage page = mViewModel.getPage(currentIndex);
        if (page != null) {
            page.setColorMode(colorMode);
            mPagerAdapter.notifyPageChanged(currentIndex);
            updateFilterButtonStates(currentIndex);
        }
    }

    private void updateFilterButtonStates(int position) {
        DocumentPage page = mViewModel.getPage(position);
        if (page == null) return;

        int colorMode = page.getColorMode();
        int selectedColor = getResources().getColor(R.color.dy_orange, null);
        int unselectedColor = getResources().getColor(R.color.dy_gray, null);

        mBtnFilterColor.setTextColor(colorMode == EnumImageColourMode.ICM_COLOUR ? selectedColor : unselectedColor);
        mBtnFilterGrayscale.setTextColor(colorMode == EnumImageColourMode.ICM_GRAYSCALE ? selectedColor : unselectedColor);
        mBtnFilterBinary.setTextColor(colorMode == EnumImageColourMode.ICM_BINARY ? selectedColor : unselectedColor);
    }

    private void updatePageIndicator(int position) {
        int total = mViewModel.getPageCount();
        mTvPageIndicator.setText(String.format(Locale.getDefault(),
                getString(R.string.page_indicator), position + 1, total));
    }

    private void exportPdf() {
        List<DocumentPage> pages = mViewModel.getPagesList();
        if (pages.isEmpty()) return;

        try {
            Uri pdfUri = FileUtils.exportToPdf(requireContext(), pages);
            if (pdfUri != null) {
                Toast.makeText(requireContext(), R.string.pdf_saved, Toast.LENGTH_SHORT).show();
                FileUtils.openPdf(requireContext(), pdfUri);
            } else {
                Toast.makeText(requireContext(), R.string.pdf_export_failed, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(requireContext(), R.string.pdf_export_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void exportImages() {
        List<DocumentPage> pages = mViewModel.getPagesList();
        if (pages.isEmpty()) return;

        try {
            int saved = FileUtils.exportToImages(requireContext(), pages);
            if (saved > 0) {
                Toast.makeText(requireContext(), R.string.images_saved, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(requireContext(), R.string.image_save_failed, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(requireContext(), R.string.image_save_failed, Toast.LENGTH_SHORT).show();
        }
    }
}