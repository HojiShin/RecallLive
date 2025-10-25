package com.example.recalllive;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class VideoEmotionAdapter extends RecyclerView.Adapter<VideoEmotionAdapter.ViewHolder> {

    private final List<GuardianHomeFragment.VideoEmotionData> videos;
    private final OnVideoClickListener listener;

    public interface OnVideoClickListener {
        void onVideoClick(GuardianHomeFragment.VideoEmotionData video);
    }

    public VideoEmotionAdapter(List<GuardianHomeFragment.VideoEmotionData> videos, OnVideoClickListener listener) {
        this.videos = videos;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_video_emotion, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        GuardianHomeFragment.VideoEmotionData video = videos.get(position);
        holder.bind(video, listener);
    }

    @Override
    public int getItemCount() {
        return videos.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvTitle;
        private final TextView tvDate;
        private final TextView tvTopEmotion;
        private final TextView tvEmotionCount;
        private final TextView tvEmotionBreakdown;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_video_title);
            tvDate = itemView.findViewById(R.id.tv_video_date);
            tvTopEmotion = itemView.findViewById(R.id.tv_top_emotion);
            tvEmotionCount = itemView.findViewById(R.id.tv_emotion_count);
            tvEmotionBreakdown = itemView.findViewById(R.id.tv_emotion_breakdown);
        }

        public void bind(GuardianHomeFragment.VideoEmotionData video, OnVideoClickListener listener) {
            tvTitle.setText(video.title);
            tvDate.setText(video.getFormattedDate());

            if (video.hasEmotionData && video.totalEmotions > 0) {
                String topEmotion = video.getTopEmotion();
                tvTopEmotion.setText(getEmotionEmoji(topEmotion) + " " + topEmotion);
                tvTopEmotion.setTextColor(getEmotionColor(topEmotion));

                tvEmotionCount.setText(video.totalEmotions + " emotions detected");
                tvEmotionCount.setVisibility(View.VISIBLE);

                // Show breakdown
                StringBuilder breakdown = new StringBuilder();
                if (video.happy > 0) breakdown.append("😊 ").append(video.happy).append(" ");
                if (video.sad > 0) breakdown.append("😢 ").append(video.sad).append(" ");
                if (video.neutral > 0) breakdown.append("😐 ").append(video.neutral).append(" ");
                if (video.surprise > 0) breakdown.append("😲 ").append(video.surprise).append(" ");
                if (video.angry > 0) breakdown.append("😠 ").append(video.angry).append(" ");
                if (video.fear > 0) breakdown.append("😨 ").append(video.fear).append(" ");
                if (video.disgust > 0) breakdown.append("🤢 ").append(video.disgust).append(" ");

                tvEmotionBreakdown.setText(breakdown.toString().trim());
                tvEmotionBreakdown.setVisibility(View.VISIBLE);
            } else {
                tvTopEmotion.setText("No emotion data");
                tvTopEmotion.setTextColor(Color.GRAY);
                tvEmotionCount.setVisibility(View.GONE);
                tvEmotionBreakdown.setVisibility(View.GONE);
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onVideoClick(video);
                }
            });
        }

        private String getEmotionEmoji(String emotion) {
            switch (emotion) {
                case "Happy": return "😊";
                case "Sad": return "😢";
                case "Angry": return "😠";
                case "Neutral": return "😐";
                case "Fear": return "😨";
                case "Disgust": return "🤢";
                case "Surprise": return "😲";
                default: return "❓";
            }
        }

        private int getEmotionColor(String emotion) {
            switch (emotion) {
                case "Happy": return Color.rgb(76, 175, 80);   // Green
                case "Sad": return Color.rgb(33, 150, 243);    // Blue
                case "Angry": return Color.rgb(244, 67, 54);   // Red
                case "Neutral": return Color.rgb(158, 158, 158); // Gray
                case "Fear": return Color.rgb(156, 39, 176);   // Purple
                case "Disgust": return Color.rgb(121, 85, 72); // Brown
                case "Surprise": return Color.rgb(255, 193, 7); // Yellow
                default: return Color.GRAY;
            }
        }
    }
}