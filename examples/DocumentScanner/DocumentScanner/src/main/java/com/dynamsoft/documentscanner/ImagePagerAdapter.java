package com.dynamsoft.documentscanner;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.InputStream;
import java.util.List;

public class ImagePagerAdapter extends RecyclerView.Adapter<ImagePagerAdapter.ImageViewHolder> {

    private final Context context;
    private List<Uri> imageUris;

    public ImagePagerAdapter(Context context, List<Uri> imageUris) {
        this.context = context;
        this.imageUris = imageUris;
    }

    @NonNull
    @Override
    public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_scanned_image, parent, false);
        return new ImageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
        Uri uri = imageUris.get(position);
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(uri);
            if (inputStream != null) {
                // Decode with sample size to avoid OOM
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 2;
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream, null, options);
                holder.imageView.setImageBitmap(bitmap);
                inputStream.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public int getItemCount() {
        return imageUris.size();
    }

    public void updateImages(List<Uri> newUris) {
        this.imageUris = newUris;
        notifyDataSetChanged();
    }

    static class ImageViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;

        ImageViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.iv_scanned_image);
        }
    }
}
