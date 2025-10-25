package com.example.recalllive;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Adapter for displaying watch history grouped by date
 */
public class HistoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_ITEM = 1;

    private final List<HistoryActivity.HistoryGroup> historyGroups;
    private final OnHistoryItemClickListener listener;

    public interface OnHistoryItemClickListener {
        void onHistoryItemClick(HistoryActivity.WatchHistoryItem item);
    }

    public HistoryAdapter(List<HistoryActivity.HistoryGroup> historyGroups,
                          OnHistoryItemClickListener listener) {
        this.historyGroups = historyGroups;
        this.listener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        // Calculate if this position is a header or item
        int count = 0;
        for (HistoryActivity.HistoryGroup group : historyGroups) {
            if (position == count) {
                return VIEW_TYPE_HEADER;
            }
            count++; // Header
            if (position < count + group.items.size()) {
                return VIEW_TYPE_ITEM;
            }
            count += group.items.size();
        }
        return VIEW_TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_HEADER) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_history_header, parent, false);
            return new HeaderViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_history_video, parent, false);
            return new VideoViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderViewHolder) {
            HistoryActivity.HistoryGroup group = getGroupForPosition(position);
            if (group != null) {
                ((HeaderViewHolder) holder).bind(group.dateLabel);
            }
        } else if (holder instanceof VideoViewHolder) {
            HistoryActivity.WatchHistoryItem item = getItemForPosition(position);
            if (item != null) {
                ((VideoViewHolder) holder).bind(item, listener);
            }
        }
    }

    @Override
    public int getItemCount() {
        int count = 0;
        for (HistoryActivity.HistoryGroup group : historyGroups) {
            count++; // Header
            count += group.items.size(); // Items
        }
        return count;
    }

    private HistoryActivity.HistoryGroup getGroupForPosition(int position) {
        int count = 0;
        for (HistoryActivity.HistoryGroup group : historyGroups) {
            if (position == count) {
                return group;
            }
            count += 1 + group.items.size();
        }
        return null;
    }

    private HistoryActivity.WatchHistoryItem getItemForPosition(int position) {
        int count = 0;
        for (HistoryActivity.HistoryGroup group : historyGroups) {
            count++; // Skip header
            for (HistoryActivity.WatchHistoryItem item : group.items) {
                if (position == count) {
                    return item;
                }
                count++;
            }
        }
        return null;
    }

    // Header ViewHolder
    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvHeaderLabel;

        public HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvHeaderLabel = itemView.findViewById(R.id.tv_header_label);
        }

        public void bind(String label) {
            tvHeaderLabel.setText(label);
        }
    }

    // Video Item ViewHolder
    static class VideoViewHolder extends RecyclerView.ViewHolder {
        private final ImageView ivThumbnail;
        private final TextView tvTitle;
        private final TextView tvTimeAgo;

        public VideoViewHolder(@NonNull View itemView) {
            super(itemView);
            ivThumbnail = itemView.findViewById(R.id.iv_video_thumbnail);
            tvTitle = itemView.findViewById(R.id.tv_video_title);
            tvTimeAgo = itemView.findViewById(R.id.tv_time_ago);
        }

        public void bind(HistoryActivity.WatchHistoryItem item, OnHistoryItemClickListener listener) {
            tvTitle.setText(item.title);
            tvTimeAgo.setText(formatTimeAgo(item.watchedAt));

            // Load thumbnail
            if (item.videoUrl != null && !item.videoUrl.isEmpty()) {
                Glide.with(itemView.getContext())
                        .load(item.videoUrl)
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .error(android.R.drawable.ic_menu_gallery)
                        .centerCrop()
                        .into(ivThumbnail);
            } else {
                ivThumbnail.setImageResource(android.R.drawable.ic_menu_gallery);
            }

            // Click listener
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onHistoryItemClick(item);
                }
            });
        }

        private String formatTimeAgo(long timestamp) {
            long diff = System.currentTimeMillis() - timestamp;

            long seconds = diff / 1000;
            long minutes = seconds / 60;
            long hours = minutes / 60;
            long days = hours / 24;

            if (days > 0) {
                return days + " day" + (days > 1 ? "s" : "") + " ago";
            } else if (hours > 0) {
                return hours + " hour" + (hours > 1 ? "s" : "") + " ago";
            } else if (minutes > 0) {
                return minutes + " minute" + (minutes > 1 ? "s" : "") + " ago";
            } else {
                return "Just now";
            }
        }
    }
}