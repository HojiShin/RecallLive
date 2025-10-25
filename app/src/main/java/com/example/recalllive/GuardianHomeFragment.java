package com.example.recalllive;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class GuardianHomeFragment extends Fragment {

    private static final String TAG = "GuardianHomeFragment";
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    private String mParam1;
    private String mParam2;

    // UI Elements
    private RecyclerView videoRecyclerView;
    private VideoEmotionAdapter videoEmotionAdapter;
    private CardView cardFacialExpression;
    private BarChart emotionBarChart;
    private TextView tvExplanation;

    private String linkedPatientUid;
    private List<VideoEmotionData> videoEmotionList;

    public GuardianHomeFragment() {
        // Required empty public constructor
    }

    public static GuardianHomeFragment newInstance(String param1, String param2) {
        GuardianHomeFragment fragment = new GuardianHomeFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }

        // Get linked patient UID
        linkedPatientUid = requireContext().getSharedPreferences("RecallLivePrefs",
                        android.content.Context.MODE_PRIVATE)
                .getString("linked_patient_uid", null);

        videoEmotionList = new ArrayList<>();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_guardianhome, container, false);

        videoRecyclerView = view.findViewById(R.id.rv_videos_emotions);
        cardFacialExpression = view.findViewById(R.id.card_quiz_score);
        tvExplanation = view.findViewById(R.id.tv_explanation);

        // Setup video list
        setupVideoRecyclerView();

        // Setup emotion chart
        setupEmotionChart(view);

        // Load data
        if (linkedPatientUid != null) {
            loadPatientVideosWithEmotions();
            loadPatientEmotionData();
        } else {
            Log.e(TAG, "No linked patient UID found");
            Toast.makeText(getContext(), "No patient linked", Toast.LENGTH_SHORT).show();
        }

        return view;
    }

    private void setupVideoRecyclerView() {
        videoRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        videoEmotionAdapter = new VideoEmotionAdapter(videoEmotionList, this::onVideoClick);
        videoRecyclerView.setAdapter(videoEmotionAdapter);
    }

    private void setupEmotionChart(View view) {
        ViewGroup cardContent = (ViewGroup) cardFacialExpression.getChildAt(0);
        TextView placeholder = cardContent.findViewById(R.id.tv_quiz_placeholder);
        if (placeholder != null) {
            placeholder.setVisibility(View.GONE);
        }

        emotionBarChart = new BarChart(requireContext());
        emotionBarChart.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        emotionBarChart.setPadding(8, 8, 8, 8);
        emotionBarChart.getDescription().setEnabled(false);
        emotionBarChart.setDrawGridBackground(false);
        emotionBarChart.setTouchEnabled(true);
        emotionBarChart.setDragEnabled(true);
        emotionBarChart.setScaleEnabled(true);

        cardContent.addView(emotionBarChart);
    }

    private void loadPatientVideosWithEmotions() {
        Log.d(TAG, "Loading videos with emotions for patient: " + linkedPatientUid);

        // Load videos from Firestore
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();

        firestore.collection("memory_videos")
                .whereEqualTo("patientUid", linkedPatientUid)
                .get()
                .addOnSuccessListener(snapshots -> {
                    videoEmotionList.clear();

                    for (QueryDocumentSnapshot doc : snapshots) {
                        String videoUrl = doc.getString("videoUrl");
                        String locationName = doc.getString("locationName");
                        String timeDescription = doc.getString("timeDescription");
                        com.google.firebase.Timestamp createdAt = doc.getTimestamp("createdAt");

                        VideoEmotionData videoData = new VideoEmotionData();
                        videoData.documentId = doc.getId();
                        videoData.videoUrl = videoUrl;
                        videoData.title = locationName + " - " + timeDescription;
                        videoData.createdAt = createdAt != null ? createdAt.toDate().getTime() : 0;

                        videoEmotionList.add(videoData);
                    }

                    // Now load emotion data for each video
                    loadEmotionsForVideos();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load videos", e);
                });
    }

    private void loadEmotionsForVideos() {
        DatabaseReference emotionRef = FirebaseDatabase.getInstance().getReference()
                .child("Patient")
                .child(linkedPatientUid)
                .child("videoEmotions");

        emotionRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (VideoEmotionData video : videoEmotionList) {
                    DataSnapshot videoEmotions = snapshot.child(video.documentId);

                    if (videoEmotions.exists()) {
                        video.hasEmotionData = true;
                        video.totalEmotions = getIntValue(videoEmotions, "totalEmotions");
                        video.happy = getIntValue(videoEmotions, "happy");
                        video.sad = getIntValue(videoEmotions, "sad");
                        video.angry = getIntValue(videoEmotions, "angry");
                        video.neutral = getIntValue(videoEmotions, "neutral");
                        video.fear = getIntValue(videoEmotions, "fear");
                        video.disgust = getIntValue(videoEmotions, "disgust");
                        video.surprise = getIntValue(videoEmotions, "surprise");
                    }
                }

                // Sort by creation date (newest first)
                videoEmotionList.sort((a, b) -> Long.compare(b.createdAt, a.createdAt));

                videoEmotionAdapter.notifyDataSetChanged();
                Log.d(TAG, "Loaded emotions for " + videoEmotionList.size() + " videos");
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load emotions", error.toException());
            }
        });
    }

    private int getIntValue(DataSnapshot snapshot, String key) {
        Long value = snapshot.child(key).getValue(Long.class);
        return value != null ? value.intValue() : 0;
    }

    private void loadPatientEmotionData() {
        Log.d(TAG, "Loading aggregate emotion data for patient: " + linkedPatientUid);

        DatabaseReference emotionRef = FirebaseDatabase.getInstance().getReference()
                .child("Patient")
                .child(linkedPatientUid)
                .child("videoEmotions");

        emotionRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    Log.d(TAG, "No emotion data found");
                    updateChartWithPlaceholder();
                    return;
                }

                Map<String, Integer> totalEmotions = new HashMap<>();
                totalEmotions.put("Happy", 0);
                totalEmotions.put("Sad", 0);
                totalEmotions.put("Angry", 0);
                totalEmotions.put("Neutral", 0);
                totalEmotions.put("Fear", 0);
                totalEmotions.put("Disgust", 0);
                totalEmotions.put("Surprise", 0);

                int videoCount = 0;

                for (DataSnapshot videoSnapshot : snapshot.getChildren()) {
                    videoCount++;

                    if (videoSnapshot.child("happy").exists()) {
                        totalEmotions.put("Happy", totalEmotions.get("Happy") + getIntValue(videoSnapshot, "happy"));
                        totalEmotions.put("Sad", totalEmotions.get("Sad") + getIntValue(videoSnapshot, "sad"));
                        totalEmotions.put("Angry", totalEmotions.get("Angry") + getIntValue(videoSnapshot, "angry"));
                        totalEmotions.put("Neutral", totalEmotions.get("Neutral") + getIntValue(videoSnapshot, "neutral"));
                        totalEmotions.put("Fear", totalEmotions.get("Fear") + getIntValue(videoSnapshot, "fear"));
                        totalEmotions.put("Disgust", totalEmotions.get("Disgust") + getIntValue(videoSnapshot, "disgust"));
                        totalEmotions.put("Surprise", totalEmotions.get("Surprise") + getIntValue(videoSnapshot, "surprise"));
                    }
                }

                Log.d(TAG, "Loaded emotion data from " + videoCount + " videos");
                updateEmotionChart(totalEmotions);
                updateExplanation(totalEmotions, videoCount);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load emotion data: " + error.getMessage());
                updateChartWithPlaceholder();
            }
        });
    }

    private void updateEmotionChart(Map<String, Integer> emotions) {
        List<BarEntry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        String[] emotionOrder = {"Happy", "Neutral", "Surprise", "Sad", "Angry", "Fear", "Disgust"};

        for (int i = 0; i < emotionOrder.length; i++) {
            String emotion = emotionOrder[i];
            int count = emotions.getOrDefault(emotion, 0);
            entries.add(new BarEntry(i, count));
            labels.add(emotion);
        }

        BarDataSet dataSet = new BarDataSet(entries, "Facial Expressions During Videos");

        List<Integer> colors = new ArrayList<>();
        colors.add(Color.rgb(76, 175, 80));   // Happy - Green
        colors.add(Color.rgb(158, 158, 158)); // Neutral - Gray
        colors.add(Color.rgb(255, 193, 7));   // Surprise - Yellow
        colors.add(Color.rgb(33, 150, 243));  // Sad - Blue
        colors.add(Color.rgb(244, 67, 54));   // Angry - Red
        colors.add(Color.rgb(156, 39, 176));  // Fear - Purple
        colors.add(Color.rgb(121, 85, 72));   // Disgust - Brown

        dataSet.setColors(colors);
        dataSet.setValueTextSize(12f);
        dataSet.setValueTextColor(Color.BLACK);

        BarData barData = new BarData(dataSet);
        barData.setBarWidth(0.8f);

        emotionBarChart.setData(barData);

        XAxis xAxis = emotionBarChart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setGranularityEnabled(true);
        xAxis.setDrawGridLines(false);
        xAxis.setTextSize(10f);

        emotionBarChart.getAxisLeft().setAxisMinimum(0f);
        emotionBarChart.getAxisRight().setEnabled(false);

        emotionBarChart.invalidate();

        Log.d(TAG, "Chart updated with emotion data");
    }

    private void updateChartWithPlaceholder() {
        List<BarEntry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        String[] emotionOrder = {"Happy", "Neutral", "Surprise", "Sad", "Angry", "Fear", "Disgust"};

        for (int i = 0; i < emotionOrder.length; i++) {
            entries.add(new BarEntry(i, 0));
            labels.add(emotionOrder[i]);
        }

        BarDataSet dataSet = new BarDataSet(entries, "No Data Yet");
        dataSet.setColor(Color.LTGRAY);

        BarData barData = new BarData(dataSet);
        emotionBarChart.setData(barData);

        XAxis xAxis = emotionBarChart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);

        emotionBarChart.invalidate();
    }

    private void updateExplanation(Map<String, Integer> emotions, int videoCount) {
        int totalEmotions = 0;
        for (int count : emotions.values()) {
            totalEmotions += count;
        }

        String mostCommonEmotion = "";
        int maxCount = 0;
        for (Map.Entry<String, Integer> entry : emotions.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                mostCommonEmotion = entry.getKey();
            }
        }

        int positiveEmotions = emotions.get("Happy") + emotions.get("Surprise");
        int negativeEmotions = emotions.get("Sad") + emotions.get("Angry") + emotions.get("Fear");

        double positivePercent = totalEmotions > 0 ? (positiveEmotions * 100.0 / totalEmotions) : 0;
        double negativePercent = totalEmotions > 0 ? (negativeEmotions * 100.0 / totalEmotions) : 0;
        double neutralPercent = totalEmotions > 0 ? (emotions.get("Neutral") * 100.0 / totalEmotions) : 0;

        String explanation = "Facial Expression Analysis\n\n" +
                "📊 Data from " + videoCount + " videos\n" +
                "🎭 Total expressions detected: " + totalEmotions + "\n\n" +
                "Most common: " + mostCommonEmotion + " (" + maxCount + " times)\n\n" +
                "😊 Positive: " + positiveEmotions + " (" + String.format("%.1f%%", positivePercent) + ")\n" +
                "😢 Negative: " + negativeEmotions + " (" + String.format("%.1f%%", negativePercent) + ")\n" +
                "😐 Neutral: " + emotions.get("Neutral") + " (" + String.format("%.1f%%", neutralPercent) + ")\n\n" +
                "💡 Insights:\n";

        if (positivePercent > 60) {
            explanation += "✓ Predominantly positive reactions\n";
        } else if (positivePercent > 40) {
            explanation += "✓ Good emotional engagement\n";
        } else if (negativePercent > 50) {
            explanation += "⚠ Higher negative reactions detected\n";
        }

        if (totalEmotions > 0) {
            double avgPerVideo = (double) totalEmotions / videoCount;
            explanation += "✓ Average " + String.format("%.1f", avgPerVideo) + " emotions per video\n";
        }

        explanation += "\n📝 Click any video below to see detailed emotions";

        tvExplanation.setText(explanation);
    }

    private void onVideoClick(VideoEmotionData video) {
        // Open video viewer fragment
        GuardianVideoViewerFragment viewerFragment = GuardianVideoViewerFragment.newInstance(
                video.videoUrl,
                video.title,
                "",  // location
                "",  // time
                0,   // photo count
                video.documentId,
                linkedPatientUid
        );

        getParentFragmentManager().beginTransaction()
                .replace(R.id.container, viewerFragment)
                .addToBackStack(null)
                .commit();
    }

    // Data class for video + emotion info
    public static class VideoEmotionData {
        String documentId;
        String videoUrl;
        String title;
        long createdAt;
        boolean hasEmotionData = false;
        int totalEmotions = 0;
        int happy = 0;
        int sad = 0;
        int angry = 0;
        int neutral = 0;
        int fear = 0;
        int disgust = 0;
        int surprise = 0;

        public String getTopEmotion() {
            int max = Math.max(happy, Math.max(sad, Math.max(angry, Math.max(neutral,
                    Math.max(fear, Math.max(disgust, surprise))))));

            if (max == 0) return "No data";
            if (max == happy) return "Happy";
            if (max == sad) return "Sad";
            if (max == angry) return "Angry";
            if (max == neutral) return "Neutral";
            if (max == fear) return "Fear";
            if (max == disgust) return "Disgust";
            return "Surprise";
        }

        public String getFormattedDate() {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
            return sdf.format(new Date(createdAt));
        }
    }
}