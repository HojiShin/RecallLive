package com.example.recalllive;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;

public class VideoPlayerActivity extends AppCompatActivity {

    private static final String TAG = "VideoPlayerActivity";

    private VideoView videoView;
    private TextView tvVideoTitle;
    private TextView tvSubtitlesContent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_patientvideoopened);

        // Initialize views
        videoView = findViewById(R.id.video_view);
        tvVideoTitle = findViewById(R.id.tv_video_title);
        tvSubtitlesContent = findViewById(R.id.tv_subtitles_content);

        // Get video data from intent
        String videoUrl = getIntent().getStringExtra("video_url");
        String videoTitle = getIntent().getStringExtra("video_title");
        String locationName = getIntent().getStringExtra("location_name");
        String timeDescription = getIntent().getStringExtra("time_description");
        int photoCount = getIntent().getIntExtra("photo_count", 0);

        Log.d(TAG, "Opening video: " + videoUrl);

        // Set video title
        if (videoTitle != null && !videoTitle.isEmpty()) {
            tvVideoTitle.setText(videoTitle);
        } else if (locationName != null) {
            tvVideoTitle.setText(locationName + " - " + timeDescription);
        }

        // Set subtitle information
        String subtitleText = "Location: " + (locationName != null ? locationName : "Unknown") + "\n" +
                "Time: " + (timeDescription != null ? timeDescription : "Unknown") + "\n" +
                "Photos: " + photoCount;
        tvSubtitlesContent.setText(subtitleText);

        // Setup video player
        if (videoUrl != null && !videoUrl.isEmpty()) {
            try {
                Uri videoUri = Uri.parse(videoUrl);
                Log.d(TAG, "Setting video URI: " + videoUri);

                videoView.setVideoURI(videoUri);

                // Add media controller for play/pause controls
                MediaController mediaController = new MediaController(this);
                mediaController.setAnchorView(videoView);
                videoView.setMediaController(mediaController);

                // Show loading message
                tvSubtitlesContent.append("\n\nLoading video...");

                // Auto-play video when ready
                videoView.setOnPreparedListener(mp -> {
                    Log.d(TAG, "Video prepared, starting playback");
                    tvSubtitlesContent.setText(subtitleText); // Remove loading message
                    videoView.start();
                    Toast.makeText(this, "Playing video", Toast.LENGTH_SHORT).show();
                });

                // Handle video completion
                videoView.setOnCompletionListener(mp -> {
                    Log.d(TAG, "Video playback completed");
                    Toast.makeText(this, "Video finished", Toast.LENGTH_SHORT).show();
                });

                // Handle errors
                videoView.setOnErrorListener((mp, what, extra) -> {
                    String errorMsg = "Error playing video. Code: " + what + ", Extra: " + extra;
                    Log.e(TAG, errorMsg);
                    tvSubtitlesContent.setText(subtitleText + "\n\n" + errorMsg);
                    Toast.makeText(this, "Cannot play video", Toast.LENGTH_LONG).show();
                    return true;
                });

            } catch (Exception e) {
                Log.e(TAG, "Error setting up video player", e);
                tvSubtitlesContent.setText("Error: " + e.getMessage());
                Toast.makeText(this, "Failed to load video: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        } else {
            Log.e(TAG, "No video URL provided");
            tvSubtitlesContent.setText("Error: No video URL provided");
            Toast.makeText(this, "No video URL available", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (videoView != null && videoView.isPlaying()) {
            videoView.pause();
            Log.d(TAG, "Video paused");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (videoView != null && !videoView.isPlaying()) {
            videoView.resume();
            Log.d(TAG, "Video resumed");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (videoView != null) {
            videoView.stopPlayback();
            Log.d(TAG, "Video stopped");
        }
    }
}