package com.example.recalllive;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import java.util.List;
import java.util.Locale;

public class VideoAdapter extends ListAdapter<Video, VideoAdapter.VideoViewHolder> {

    private OnVideoClickListener listener;

    public interface OnVideoClickListener {
        void onVideoClick(Video video);
    }

    public VideoAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setOnVideoClickListener(OnVideoClickListener listener) {
        this.listener = listener;
    }

    private static final DiffUtil.ItemCallback<Video> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<Video>() {
                @Override
                public boolean areItemsTheSame(@NonNull Video oldItem, @NonNull Video newItem) {
                    // Use documentId if available, otherwise fall back to title
                    if (oldItem.getDocumentId() != null && newItem.getDocumentId() != null) {
                        return oldItem.getDocumentId().equals(newItem.getDocumentId());
                    }
                    return oldItem.getTitle().equals(newItem.getTitle());
                }

                @Override
                public boolean areContentsTheSame(@NonNull Video oldItem, @NonNull Video newItem) {
                    return oldItem.getTitle().equals(newItem.getTitle()) &&
                            ((oldItem.getThumbnailUrl() == null && newItem.getThumbnailUrl() == null) ||
                                    (oldItem.getThumbnailUrl() != null && oldItem.getThumbnailUrl().equals(newItem.getThumbnailUrl())));
                }
            };

    @NonNull
    @Override
    public VideoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_video, parent, false);
        return new VideoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VideoViewHolder holder, int position) {
        Video video = getItem(position);
        holder.bind(video, listener);
    }

    public List<Video> getCurrentList() {
        return super.getCurrentList();
    }

    static class VideoViewHolder extends RecyclerView.ViewHolder {
        private final ImageView thumbnail;
        private final TextView title;

        public VideoViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.iv_video_thumbnail);
            title = itemView.findViewById(R.id.tv_video_title);
        }

        public void bind(Video video, OnVideoClickListener listener) {
            // Set title
            if (video.getLocationName() != null && !video.getLocationName().isEmpty()) {
                title.setText(video.getLocationName());
            } else {
                title.setText(video.getTitle());
            }

            // Load thumbnail using Glide
            if (video.getThumbnailUrl() != null && !video.getThumbnailUrl().isEmpty()) {
                Glide.with(itemView.getContext())
                        .load(video.getThumbnailUrl())
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .error(android.R.drawable.ic_dialog_alert)
                        .centerCrop()
                        .into(thumbnail);
            } else {
                thumbnail.setImageResource(android.R.drawable.ic_menu_gallery);
            }

            // Set click listener
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onVideoClick(video);
                }
            });
        }
    }
}