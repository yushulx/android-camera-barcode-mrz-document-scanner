package com.dynamsoft.documentscanner.scan;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.dynamsoft.documentscanner.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SortPagesFragment extends Fragment {
    private DocumentScannerViewModel mViewModel;
    private SortPagesAdapter mAdapter;
    private List<DocumentPage> mWorkingList;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mViewModel = new ViewModelProvider(requireActivity()).get(DocumentScannerViewModel.class);
        mViewModel.actionBarTitle.setValue(requireContext().getString(R.string.sort_page_title));
        return inflater.inflate(R.layout.fragment_sort_pages, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mWorkingList = new ArrayList<>(mViewModel.getPagesList());

        RecyclerView recyclerView = view.findViewById(R.id.rv_sort_pages);
        recyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 3));

        mAdapter = new SortPagesAdapter(mWorkingList);
        recyclerView.setAdapter(mAdapter);

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN | ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT,
                0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                int from = viewHolder.getAdapterPosition();
                int to = target.getAdapterPosition();
                Collections.swap(mWorkingList, from, to);
                mAdapter.notifyItemMoved(from, to);
                mAdapter.notifyItemChanged(from);
                mAdapter.notifyItemChanged(to);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                // No swipe action
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return true;
            }
        });
        itemTouchHelper.attachToRecyclerView(recyclerView);

        // Done button
        view.findViewById(R.id.btn_done).setOnClickListener(v -> {
            mViewModel.reorderPages(mWorkingList);
            requireActivity().getSupportFragmentManager().popBackStack();
        });
    }
}
