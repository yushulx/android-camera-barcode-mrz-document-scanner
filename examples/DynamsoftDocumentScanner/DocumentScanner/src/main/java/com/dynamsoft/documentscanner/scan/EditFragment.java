package com.dynamsoft.documentscanner.scan;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.dynamsoft.core.basic_structures.CoreException;
import com.dynamsoft.core.basic_structures.Quadrilateral;
import com.dynamsoft.dce.DrawingItem;
import com.dynamsoft.dce.DrawingLayer;
import com.dynamsoft.dce.ImageEditorView;
import com.dynamsoft.dce.QuadDrawingItem;
import com.dynamsoft.documentscanner.R;

import java.util.ArrayList;
import java.util.List;

public class EditFragment extends Fragment {
    private ImageEditorView mEditorView;
    private DocumentScannerViewModel mViewModel;
    private int mPageIndex;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mViewModel = new ViewModelProvider(requireActivity()).get(DocumentScannerViewModel.class);
        mViewModel.actionBarTitle.setValue(getString(R.string.edit_page_title));
        return inflater.inflate(R.layout.fragment_edit, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mEditorView = view.findViewById(R.id.imageEditorView);

        Integer selectedIndex = mViewModel.selectedPageIndex.getValue();
        mPageIndex = (selectedIndex != null) ? selectedIndex : 0;
        DocumentPage page = mViewModel.getPage(mPageIndex);

        if (page != null && page.hasOriginalImage()) {
            mEditorView.setOriginalImage(page.getOriginalImage());

            if (page.getQuad() != null) {
                DrawingLayer layer = mEditorView.getDrawingLayer(DrawingLayer.DDN_LAYER_ID);
                QuadDrawingItem quadItem = new QuadDrawingItem(page.getQuad());
                ArrayList<DrawingItem> items = new ArrayList<>();
                items.add(quadItem);
                layer.setDrawingItems(items);
            }
        }

        view.findViewById(R.id.btn_apply).setOnClickListener(v -> applyEdit());
        view.findViewById(R.id.btn_cancel).setOnClickListener(v ->
                requireActivity().getSupportFragmentManager().popBackStack());
    }

    private void applyEdit() {
        DocumentPage page = mViewModel.getPage(mPageIndex);
        if (page == null || !page.hasOriginalImage()) {
            requireActivity().getSupportFragmentManager().popBackStack();
            return;
        }

        DrawingLayer layer = mEditorView.getDrawingLayer(DrawingLayer.DDN_LAYER_ID);
        List<DrawingItem> items = layer.getDrawingItems();

        Quadrilateral newQuad = null;
        for (DrawingItem item : items) {
            if (item instanceof QuadDrawingItem) {
                newQuad = ((QuadDrawingItem) item).getQuad();
                break;
            }
        }

        if (newQuad == null) {
            requireActivity().getSupportFragmentManager().popBackStack();
            return;
        }

        try {
            Bitmap originalBitmap = page.getOriginalImage().toBitmap();
            Bitmap deskewed = DocumentPage.perspectiveTransform(originalBitmap, newQuad);
            page.updateFromQuadEdit(deskewed, newQuad);
            mViewModel.notifyPagesChanged();
        } catch (CoreException e) {
            e.printStackTrace();
            Toast.makeText(requireContext(), "Failed to apply edit", Toast.LENGTH_SHORT).show();
        }

        requireActivity().getSupportFragmentManager().popBackStack();
    }
}
