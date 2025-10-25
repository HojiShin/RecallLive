package com.example.recalllive;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * History Activity - Shows previously watched videos organized by date
 */
public class HistoryActivity extends AppCompatActivity {
    private static final String TAG = "HistoryActivity";

    private ImageView ivBack;
    private RecyclerView rvHistory;
    private TextView tvEmptyState;
    private View progressBar;

    private HistoryAdapter historyAdapter;
    private List<HistoryGroup> historyGroups;
    private String patientUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_history);

        // Initialize views
        ivBack = findViewById(R.id.iv_back);
        rvHistory = findViewById(R.id.rv_history);
        tvEmptyState = findViewById(R.id.tv_empty_state);
        progressBar = findViewById(R.id.progress_bar);

        // Get patient UID
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            patientUid = auth.getCurrentUser().getUid();
        } else {
            Toast.makeText(this, "Please log in to view history", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Setup back button
        ivBack.setOnClickListener(v -> finish());

        // Setup RecyclerView
        historyGroups = new ArrayList<>();
        historyAdapter = new HistoryAdapter(historyGroups, this::onVideoClick);
        rvHistory.setLayoutManager(new LinearLayoutManager(this));
        rvHistory.setAdapter(historyAdapter);

        // Load watch history
        loadWatchHistory();
    }

    private void loadWatchHistory() {
        showLoading();

        DatabaseReference historyRef = FirebaseDatabase.getInstance().getReference()
                .child("Patient")
                .child(patientUid)
                .child("watchHistory");

        historyRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    Log.d(TAG, "No watch history found");
                    showEmptyState();
                    return;
                }

                List<WatchHistoryItem> allItems = new ArrayList<>();

                for (DataSnapshot videoSnapshot : snapshot.getChildren()) {
                    try {
                        String videoUrl = videoSnapshot.child("videoUrl").getValue(String.class);
                        String title = videoSnapshot.child("title").getValue(String.class);
                        Long watchedAt = videoSnapshot.child("watchedAt").getValue(Long.class);
                        String documentId = videoSnapshot.child("documentId").getValue(String.class);
                        String locationName = videoSnapshot.child("locationName").getValue(String.class);

                        if (videoUrl != null && watchedAt != null) {
                            WatchHistoryItem item = new WatchHistoryItem();
                            item.videoUrl = videoUrl;
                            item.title = title != null ? title : "Memory Video";
                            item.watchedAt = watchedAt;
                            item.documentId = documentId;
                            item.locationName = locationName != null ? locationName : "Unknown Location";

                            allItems.add(item);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing history item", e);
                    }
                }

                if (allItems.isEmpty()) {
                    showEmptyState();
                    return;
                }

                // Sort by most recent first
                Collections.sort(allItems, (a, b) -> Long.compare(b.watchedAt, a.watchedAt));

                // Group by date
                groupHistoryByDate(allItems);
                hideLoading();
                historyAdapter.notifyDataSetChanged();

                Log.d(TAG, "Loaded " + allItems.size() + " history items in " +
                        historyGroups.size() + " groups");
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e(TAG, "Failed to load history", error.toException());
                hideLoading();
                Toast.makeText(HistoryActivity.this,
                        "Failed to load history", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void groupHistoryByDate(List<WatchHistoryItem> items) {
        historyGroups.clear();

        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);

        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        yesterday.set(Calendar.HOUR_OF_DAY, 0);
        yesterday.set(Calendar.MINUTE, 0);
        yesterday.set(Calendar.SECOND, 0);
        yesterday.set(Calendar.MILLISECOND, 0);

        List<WatchHistoryItem> todayItems = new ArrayList<>();
        List<WatchHistoryItem> yesterdayItems = new ArrayList<>();
        List<WatchHistoryItem> olderItems = new ArrayList<>();

        for (WatchHistoryItem item : items) {
            Calendar itemDate = Calendar.getInstance();
            itemDate.setTimeInMillis(item.watchedAt);

            if (itemDate.after(today)) {
                todayItems.add(item);
            } else if (itemDate.after(yesterday)) {
                yesterdayItems.add(item);
            } else {
                olderItems.add(item);
            }
        }

        // Add groups
        if (!todayItems.isEmpty()) {
            historyGroups.add(new HistoryGroup("Today", todayItems));
        }
        if (!yesterdayItems.isEmpty()) {
            historyGroups.add(new HistoryGroup("Yesterday", yesterdayItems));
        }
        if (!olderItems.isEmpty()) {
            historyGroups.add(new HistoryGroup("Older", olderItems));
        }
    }

    private void onVideoClick(WatchHistoryItem item) {
        // Open video player
        PatientVideoOpenedFragment videoFragment = PatientVideoOpenedFragment.newInstance(
                item.videoUrl,
                item.title,
                item.locationName,
                formatDate(item.watchedAt),
                0,
                item.documentId
        );

        // Since this is an Activity, we need to use a different approach
        // For now, just show a toast
        Toast.makeText(this, "Playing: " + item.title, Toast.LENGTH_SHORT).show();

        // TODO: You may want to convert this to a fragment-based approach
        // or start PatientMainActivity and pass the video data
    }

    private String formatDate(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
        return sdf.format(new Date(timestamp));
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

    private void showLoading() {
        if (progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
        }
        rvHistory.setVisibility(View.GONE);
        tvEmptyState.setVisibility(View.GONE);
    }

    private void hideLoading() {
        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }
        rvHistory.setVisibility(View.VISIBLE);
    }

    private void showEmptyState() {
        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }
        rvHistory.setVisibility(View.GONE);
        tvEmptyState.setVisibility(View.VISIBLE);
        tvEmptyState.setText("No watch history yet.\n\nVideos you watch will appear here.");
    }

    // Data classes
    static class WatchHistoryItem {
        String videoUrl;
        String title;
        long watchedAt;
        String documentId;
        String locationName;
    }

    static class HistoryGroup {
        String dateLabel;
        List<WatchHistoryItem> items;

        HistoryGroup(String dateLabel, List<WatchHistoryItem> items) {
            this.dateLabel = dateLabel;
            this.items = items;
        }
    }
}