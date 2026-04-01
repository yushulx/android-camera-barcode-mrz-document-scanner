package com.dynamsoft.documentscanner.scan;

import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.dynamsoft.core.basic_structures.CoreException;
import com.dynamsoft.documentscanner.R;

import java.util.List;

public class SortPagesAdapter extends RecyclerView.Adapter<SortPagesAdapter.ViewHolder> {

    private final List<DocumentPage> pages;

    public SortPagesAdapter(List<DocumentPage> pages) {
        this.pages = pages;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_sort_page, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DocumentPage page = pages.get(position);
        holder.tvPageNumber.setText(String.valueOf(position + 1));

        try {
            Bitmap thumbnail = page.getThumbnail(300);
            if (thumbnail != null) {
                holder.ivThumbnail.setImageBitmap(thumbnail);
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
        ImageView ivThumbnail;
        TextView tvPageNumber;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivThumbnail = itemView.findViewById(R.id.iv_sort_thumbnail);
            tvPageNumber = itemView.findViewById(R.id.tv_page_number);
        }
    }
}
