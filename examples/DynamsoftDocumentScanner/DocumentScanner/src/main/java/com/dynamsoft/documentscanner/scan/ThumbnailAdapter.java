package com.dynamsoft.documentscanner.scan;

import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.dynamsoft.core.basic_structures.CoreException;
import com.dynamsoft.documentscanner.R;

import java.util.ArrayList;
import java.util.List;

public class ThumbnailAdapter extends RecyclerView.Adapter<ThumbnailAdapter.ViewHolder> {

    public interface OnThumbnailActionListener {
        void onRemove(int position);
    }

    private final List<DocumentPage> pages = new ArrayList<>();
    private OnThumbnailActionListener listener;

    public void setListener(OnThumbnailActionListener listener) {
        this.listener = listener;
    }

    public void updatePages(List<DocumentPage> newPages) {
        pages.clear();
        if (newPages != null) {
            pages.addAll(newPages);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_thumbnail, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DocumentPage page = pages.get(position);
        try {
            Bitmap thumbnail = page.getThumbnail(150);
            if (thumbnail != null) {
                holder.ivThumbnail.setImageBitmap(thumbnail);
            }
        } catch (CoreException e) {
            e.printStackTrace();
        }

        holder.btnRemove.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && listener != null) {
                listener.onRemove(pos);
            }
        });
    }

    @Override
    public int getItemCount() {
        return pages.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivThumbnail;
        ImageButton btnRemove;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivThumbnail = itemView.findViewById(R.id.iv_thumbnail);
            btnRemove = itemView.findViewById(R.id.btn_remove);
        }
    }
}
