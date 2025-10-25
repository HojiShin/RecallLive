package com.example.recalllive;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

/**
 * UPDATED: No manual buttons - all video generation is automatic
 */
public class PatientHomeFragment extends Fragment {
    private static final String TAG = "PatientHomeFragment";

    // UI Components
    private RecyclerView recyclerView;
    private VideoAdapter videoAdapter;
    private TextView emptyStateText;
    private ProgressBar progressBar;

    // Data
    private String patientUid;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_patienthome, container, false);

        // Initialize
        FirebaseAuth auth = FirebaseAuth.getInstance();
        patientUid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;

        // Find views
        recyclerView = view.findViewById(R.id.rv_video_list);
        emptyStateText = view.findViewById(R.id.tv_empty_state);
        progressBar = view.findViewById(R.id.progress_bar);

        // Note: test_controls removed - all automatic now

        // Setup RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        videoAdapter = new VideoAdapter();
        recyclerView.setAdapter(videoAdapter);

        videoAdapter.setOnVideoClickListener(this::openVideoPlayer);

        // Load videos
        loadVideosFromFirestore();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Refresh videos when returning to fragment
        loadVideosFromFirestore();
    }

    /**
     * Load videos from Firestore
     */
    private void loadVideosFromFirestore() {
        if (patientUid == null) {
            Log.e(TAG, "Patient UID is null");
            showEmptyState("Please log in to view videos");
            return;
        }

        FirebaseFirestore firestore = FirebaseFirestore.getInstance();

        Log.d(TAG, "Loading videos from Firestore for patient: " + patientUid);
        showLoading();

        firestore.collection("memory_videos")
                .whereEqualTo("patientUid", patientUid)
                .get()
                .addOnSuccessListener(snapshots -> {
                    List<Video> videos = new ArrayList<>();

                    Log.d(TAG, "Found " + snapshots.size() + " video documents");

                    for (com.google.firebase.firestore.QueryDocumentSnapshot doc : snapshots) {
                        try {
                            String url = doc.getString("videoUrl");
                            String location = doc.getString("locationName");
                            String time = doc.getString("timeDescription");
                            Long count = doc.getLong("photoCount");
                            String clusterId = doc.getString("clusterId");
                            com.google.firebase.Timestamp createdAt = doc.getTimestamp("createdAt");

                            if (url == null || url.isEmpty()) {
                                Log.e(TAG, "Skipping video - no URL");
                                continue;
                            }

                            Video v = new Video();
                            v.setDocumentId(doc.getId());
                            v.setVideoUrl(url);
                            v.setLocationName(location != null ? location : "Unknown Location");
                            v.setTimeDescription(time != null ? time : "Unknown Time");
                            v.setTitle(v.getLocationName() + " - " + v.getTimeDescription());
                            v.setThumbnailUrl(url);
                            v.setPhotoCount(count != null ? count.intValue() : 1);
                            v.setClusterId(clusterId);

                            if (createdAt != null) {
                                v.setCreatedAt(createdAt.toDate().getTime());
                            }

                            videos.add(v);
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing video: " + doc.getId(), e);
                        }
                    }

                    // Sort by creation date (newest first)
                    videos.sort((a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));

                    hideLoading();

                    if (!videos.isEmpty()) {
                        updateVideoList(videos);
                    } else {
                        showEmptyState("Your daily memory videos will appear here.\n\n" +
                                "New videos are automatically created every day at midnight!");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load videos", e);
                    hideLoading();

                    String errorMsg = e.getMessage();
                    if (errorMsg != null && errorMsg.contains("PERMISSION")) {
                        showEmptyState("Permission denied. Please check your account settings.");
                    } else {
                        showEmptyState("Unable to load videos. Please try again later.");
                    }
                });
    }

    /**
     * Open video player fragment
     */
    private void openVideoPlayer(Video video) {
        if (video == null) {
            Toast.makeText(getContext(), "Video error", Toast.LENGTH_SHORT).show();
            return;
        }

        String url = video.getVideoUrl();
        if (url == null || url.isEmpty()) {
            Toast.makeText(getContext(), "Video URL not available", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "Opening video: " + video.getTitle());
        Log.d(TAG, "Document ID: " + video.getDocumentId());

        // Open video player fragment with close button
        PatientVideoOpenedFragment videoFragment = PatientVideoOpenedFragment.newInstance(
                url,
                video.getTitle() != null ? video.getTitle() : "Memory Video",
                video.getLocationName() != null ? video.getLocationName() : "Unknown Location",
                video.getTimeDescription() != null ? video.getTimeDescription() : "Unknown Time",
                video.getPhotoCount(),
                video.getDocumentId()
        );

        getParentFragmentManager().beginTransaction()
                .replace(R.id.container, videoFragment)
                .addToBackStack(null)
                .commit();
    }

    /**
     * Update video list in UI
     */
    private void updateVideoList(List<Video> videos) {
        if (getActivity() == null) return;

        getActivity().runOnUiThread(() -> {
            if (videos == null || videos.isEmpty()) {
                recyclerView.setVisibility(View.GONE);
                emptyStateText.setVisibility(View.VISIBLE);
            } else {
                recyclerView.setVisibility(View.VISIBLE);
                emptyStateText.setVisibility(View.GONE);
                videoAdapter.submitList(new ArrayList<>(videos));
            }
        });
    }

    /**
     * Show empty state message
     */
    private void showEmptyState(String message) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                recyclerView.setVisibility(View.GONE);
                emptyStateText.setVisibility(View.VISIBLE);
                emptyStateText.setText(message);
            });
        }
    }

    /**
     * Show loading indicator
     */
    private void showLoading() {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                progressBar.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
                emptyStateText.setVisibility(View.GONE);
            });
        }
    }

    /**
     * Hide loading indicator
     */
    private void hideLoading() {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
            });
        }
    }
}