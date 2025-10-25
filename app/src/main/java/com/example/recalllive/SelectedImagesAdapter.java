//package com.example.recalllive;
//
//import android.net.Uri;
//import android.view.LayoutInflater;
//import android.view.View;
//import android.view.ViewGroup;
//import android.widget.ImageButton;
//import android.widget.ImageView;
//
//import androidx.annotation.NonNull;
//import androidx.recyclerview.widget.RecyclerView;
//
//import com.bumptech.glide.Glide;
//
//import java.util.List;
//
//public class SelectedImagesAdapter extends RecyclerView.Adapter<SelectedImagesAdapter.ImageViewHolder> {
//
//    public interface OnImageClickListener {
//        void onImageClick(Uri imageUri);
//        void onDeleteClick(Uri imageUri);
//    }
//
//    private List<Uri> imageUris;
//    private final OnImageClickListener listener;
//
//    public SelectedImagesAdapter(List<Uri> imageUris, OnImageClickListener listener) {
//        this.imageUris = imageUris;
//        this.listener = listener;
//    }
//
//    @NonNull
//    @Override
//    public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
//        View view = LayoutInflater.from(parent.getContext())
//                .inflate(R.layout.item_selected_image, parent, false);
//        return new ImageViewHolder(view);
//    }
//
//    @Override
//    public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
//        Uri imageUri = imageUris.get(position);
//
//        Glide.with(holder.imageView.getContext())
//                .load(imageUri)
//                .centerCrop()
//                .into(holder.imageView);
//
//        holder.imageView.setOnClickListener(v -> listener.onImageClick(imageUri));
//        holder.deleteButton.setOnClickListener(v -> listener.onDeleteClick(imageUri));
//    }
//
//    @Override
//    public int getItemCount() {
//        return imageUris.size();
//    }
//
//    public void updateImages(List<Uri> newImages) {
//        this.imageUris = newImages;
//        notifyDataSetChanged();
//    }
//
//    static class ImageViewHolder extends RecyclerView.ViewHolder {
//        ImageView imageView;
//        ImageButton deleteButton;
//
//        ImageViewHolder(@NonNull View itemView) {
//            super(itemView);
//            imageView = itemView.findViewById(R.id.imageViewThumbnail);
//            deleteButton = itemView.findViewById(R.id.buttonDelete);
//        }
//    }
//}