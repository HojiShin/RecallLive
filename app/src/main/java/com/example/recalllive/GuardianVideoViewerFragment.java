package com.example.recalllive;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Fragment for Guardians to view patient videos with emotion tracking data
 */
public class GuardianVideoViewerFragment extends Fragment {

    private static final String TAG = "GuardianVideoViewer";
    private static final String ARG_VIDEO_URL = "video_url";
    private static final String ARG_VIDEO_TITLE = "video_title";
    private static final String ARG_DOCUMENT_ID = "document_id";
    private static final String ARG_PATIENT_UID = "patient_uid";
    private static final String ARG_LOCATION = "location_name";
    private static final String ARG_TIME = "time_description";
    private static final String ARG_PHOTO_COUNT = "photo_count";

    private VideoView videoView;
    private TextView tvVideoTitle;
    private TextView tvEmotionSummary;
    private RecyclerView rvEmotionTimeline;
    private View loadingView;
    private View noDataView;

    private String videoUrl;
    private String videoDocumentId;
    private String patientUid;
    private EmotionTimelineAdapter emotionAdapter;

    public static GuardianVideoViewerFragment newInstance(String videoUrl, String title,
                                                          String location, String time,
                                                          int photoCount, String documentId,
                                                          String patientUid) {
        GuardianVideoViewerFragment fragment = new GuardianVideoViewerFragment();
        Bundle args = new Bundle();
        args.putString(ARG_VIDEO_URL, videoUrl);
        args.putString(ARG_VIDEO_TITLE, title);
        args.putString(ARG_LOCATION, location);
        args.putString(ARG_TIME, time);
        args.putInt(ARG_PHOTO_COUNT, photoCount);
        args.putString(ARG_DOCUMENT_ID, documentId);
        args.putString(ARG_PATIENT_UID, patientUid);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_guardianvideoviewer, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize views
        videoView = view.findViewById(R.id.video_view);
        tvVideoTitle = view.findViewById(R.id.tv_video_title);
        tvEmotionSummary = view.findViewById(R.id.tv_emotion_summary);
        rvEmotionTimeline = view.findViewById(R.id.rv_emotion_timeline);
        loadingView = view.findViewById(R.id.loading_view);
        noDataView = view.findViewById(R.id.no_data_view);

        // Setup RecyclerView
        rvEmotionTimeline.setLayoutManager(new LinearLayoutManager(getContext()));
        emotionAdapter = new EmotionTimelineAdapter();
        rvEmotionTimeline.setAdapter(emotionAdapter);

        // Get arguments
        if (getArguments() != null) {
            videoUrl = getArguments().getString(ARG_VIDEO_URL);
            videoDocumentId = getArguments().getString(ARG_DOCUMENT_ID);
            patientUid = getArguments().getString(ARG_PATIENT_UID);
            String videoTitle = getArguments().getString(ARG_VIDEO_TITLE);
            String locationName = getArguments().getString(ARG_LOCATION);
            String timeDescription = getArguments().getString(ARG_TIME);
            int photoCount = getArguments().getInt(ARG_PHOTO_COUNT, 0);

            Log.d(TAG, "Video URL: " + videoUrl);
            Log.d(TAG, "Document ID: " + videoDocumentId);
            Log.d(TAG, "Patient UID: " + patientUid);

            tvVideoTitle.setText(videoTitle != null ? videoTitle : "Memory Video");

            setupVideo();
            loadEmotionData();
        }
    }

    private void setupVideo() {
        if (videoUrl != null && !videoUrl.isEmpty()) {
            try {
                Uri videoUri = Uri.parse(videoUrl);
                videoView.setVideoURI(videoUri);

                MediaController mediaController = new MediaController(getContext());
                mediaController.setAnchorView(videoView);
                videoView.setMediaController(mediaController);

                videoView.setOnPreparedListener(mp -> {
                    Log.d(TAG, "Video prepared");
                    Toast.makeText(getContext(), "Video ready", Toast.LENGTH_SHORT).show();
                });

                videoView.setOnCompletionListener(mp -> {
                    Log.d(TAG, "Video playback completed");
                });

                videoView.setOnErrorListener((mp, what, extra) -> {
                    Log.e(TAG, "Error playing video. Code: " + what);
                    Toast.makeText(getContext(), "Cannot play video", Toast.LENGTH_LONG).show();
                    return true;
                });

            } catch (Exception e) {
                Log.e(TAG, "Error setting up video", e);
                Toast.makeText(getContext(), "Failed to load video", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void loadEmotionData() {
        if (videoDocumentId == null || patientUid == null) {
            Log.w(TAG, "Missing documentId or patientUid - cannot load emotions");
            showNoData();
            return;
        }

        loadingView.setVisibility(View.VISIBLE);
        noDataView.setVisibility(View.GONE);

        DatabaseReference emotionRef = FirebaseDatabase.getInstance().getReference()
                .child("Patient")
                .child(patientUid)
                .child("videoEmotions")
                .child(videoDocumentId);

        emotionRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                loadingView.setVisibility(View.GONE);

                if (!snapshot.exists()) {
                    Log.d(TAG, "No emotion data found for this video");
                    showNoData();
                    return;
                }

                try {
                    // Get the emotions ArrayList
                    List<String> emotionsList = new ArrayList<>();
                    if (snapshot.child("emotions").exists()) {
                        for (DataSnapshot emotionSnap : snapshot.child("emotions").getChildren()) {
                            String emotion = emotionSnap.getValue(String.class);
                            if (emotion != null) {
                                emotionsList.add(emotion);
                            }
                        }
                    }

                    // Get summary counts
                    Map<String, Integer> emotionCounts = new HashMap<>();
                    emotionCounts.put("Happy", getIntValue(snapshot, "happy"));
                    emotionCounts.put("Sad", getIntValue(snapshot, "sad"));
                    emotionCounts.put("Angry", getIntValue(snapshot, "angry"));
                    emotionCounts.put("Neutral", getIntValue(snapshot, "neutral"));
                    emotionCounts.put("Fear", getIntValue(snapshot, "fear"));
                    emotionCounts.put("Disgust", getIntValue(snapshot, "disgust"));
                    emotionCounts.put("Surprise", getIntValue(snapshot, "surprise"));

                    int totalEmotions = getIntValue(snapshot, "totalEmotions");
                    Long recordedAt = snapshot.child("recordedAt").getValue(Long.class);

                    Log.d(TAG, "Loaded " + emotionsList.size() + " emotions");
                    displayEmotionData(emotionsList, emotionCounts, totalEmotions, recordedAt);

                } catch (Exception e) {
                    Log.e(TAG, "Error parsing emotion data", e);
                    showNoData();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                loadingView.setVisibility(View.GONE);
                Log.e(TAG, "Failed to load emotion data: " + error.getMessage());
                showNoData();
            }
        });
    }

    private int getIntValue(DataSnapshot snapshot, String key) {
        Long value = snapshot.child(key).getValue(Long.class);
        return value != null ? value.intValue() : 0;
    }

    private void displayEmotionData(List<String> emotionsList, Map<String, Integer> emotionCounts,
                                    int totalEmotions, Long recordedAt) {
        // Display summary
        StringBuilder summary = new StringBuilder();
        summary.append("📊 Emotion Analysis\n\n");
        summary.append("Total detections: ").append(totalEmotions).append("\n");

        if (recordedAt != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US);
            summary.append("Recorded: ").append(sdf.format(new Date(recordedAt))).append("\n");
        }

        summary.append("\n🎭 Breakdown:\n");
        for (Map.Entry<String, Integer> entry : emotionCounts.entrySet()) {
            if (entry.getValue() > 0) {
                String emoji = getEmotionEmoji(entry.getKey());
                summary.append(emoji).append(" ").append(entry.getKey()).append(": ")
                        .append(entry.getValue()).append("\n");
            }
        }

        tvEmotionSummary.setText(summary.toString());

        // Display timeline
        emotionAdapter.setEmotions(emotionsList);
        rvEmotionTimeline.setVisibility(View.VISIBLE);
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

    private void showNoData() {
        noDataView.setVisibility(View.VISIBLE);
        rvEmotionTimeline.setVisibility(View.GONE);
        tvEmotionSummary.setText("No emotion data available for this video.\n\nEmotion tracking happens when the patient watches videos.");
    }

    @Override
    public void onPause() {
        super.onPause();
        if (videoView != null && videoView.isPlaying()) {
            videoView.pause();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (videoView != null) {
            videoView.stopPlayback();
        }
    }

    /**
     * Adapter for displaying emotion timeline
     */
    private static class EmotionTimelineAdapter extends RecyclerView.Adapter<EmotionTimelineAdapter.EmotionViewHolder> {

        private List<String> emotions = new ArrayList<>();

        public void setEmotions(List<String> emotions) {
            this.emotions = emotions;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public EmotionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_emotion_timeline, parent, false);
            return new EmotionViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull EmotionViewHolder holder, int position) {
            String emotion = emotions.get(position);
            holder.bind(emotion, position);
        }

        @Override
        public int getItemCount() {
            return emotions.size();
        }

        static class EmotionViewHolder extends RecyclerView.ViewHolder {
            private final TextView tvEmotionIndex;
            private final TextView tvEmotionName;

            public EmotionViewHolder(@NonNull View itemView) {
                super(itemView);
                tvEmotionIndex = itemView.findViewById(R.id.tv_emotion_index);
                tvEmotionName = itemView.findViewById(R.id.tv_emotion_name);
            }

            public void bind(String emotion, int position) {
                tvEmotionIndex.setText("#" + (position + 1));

                String emoji = getEmotionEmoji(emotion);
                tvEmotionName.setText(emoji + " " + emotion);

                // Set background color based on emotion
                int color = getEmotionColor(emotion);
                itemView.setBackgroundColor(color);
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
                    case "Happy": return 0xFFE8F5E9; // Light green
                    case "Sad": return 0xFFE3F2FD; // Light blue
                    case "Angry": return 0xFFFFEBEE; // Light red
                    case "Neutral": return 0xFFF5F5F5; // Light gray
                    case "Fear": return 0xFFF3E5F5; // Light purple
                    case "Disgust": return 0xFFFFF3E0; // Light orange
                    case "Surprise": return 0xFFFFFDE7; // Light yellow
                    default: return 0xFFFFFFFF; // White
                }
            }
        }
    }
}