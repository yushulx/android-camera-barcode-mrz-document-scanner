package com.dynamsoft.documentscanner.scan;

import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.dynamsoft.core.basic_structures.CoreException;
import com.dynamsoft.documentscanner.R;

import java.util.ArrayList;
import java.util.List;

public class ResultPagerAdapter extends RecyclerView.Adapter<ResultPagerAdapter.ViewHolder> {

    private final List<DocumentPage> pages = new ArrayList<>();

    public void updatePages(List<DocumentPage> newPages) {
        pages.clear();
        if (newPages != null) {
            pages.addAll(newPages);
        }
        notifyDataSetChanged();
    }

    public void notifyPageChanged(int position) {
        if (position >= 0 && position < pages.size()) {
            notifyItemChanged(position);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_result_page, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DocumentPage page = pages.get(position);
        try {
            Bitmap bitmap = page.getDisplayBitmap();
            if (bitmap != null) {
                holder.ivPage.setImageBitmap(bitmap);
            }
        } catch (CoreException e) {
            e.printStackTrace();
        }
    }

    @Override
    public int getItemCount() {
        return pages.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivPage;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivPage = itemView.findViewById(R.id.iv_page);
        }
    }
}
