package com.example.recalllive;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * PatientVideoOpenedFragment - Complete implementation
 * Features:
 * - Video player with proper sizing (fits between top bar and bottom navigation)
 * - Swipe-down gesture to close
 * - Close button (X)
 * - Emotion detection tracking
 * - Watch history tracking
 */
public class PatientVideoOpenedFragment extends Fragment {

    private static final String TAG = "VideoOpened";
    private static final int CAMERA_PERMISSION_CODE = 200;
    private static final int SWIPE_THRESHOLD = 100;
    private static final int SWIPE_VELOCITY_THRESHOLD = 100;

    private VideoView videoView;
    private TextView tvVideoTitle;
    private TextView tvSubtitlesContent;
    private ImageButton btnClose;
    private View rootView;

    private String videoUrl;
    private String videoDocumentId;

    // Emotion detection
    private BackgroundEmotionDetector emotionDetector;
    private ExecutorService cameraExecutor;
    private ProcessCameraProvider cameraProvider;
    private boolean emotionDetectionActive = false;

    // Gesture detection
    private GestureDetector gestureDetector;

    public static PatientVideoOpenedFragment newInstance(String videoUrl, String title,
                                                         String location, String time,
                                                         int count, String documentId) {
        PatientVideoOpenedFragment fragment = new PatientVideoOpenedFragment();
        Bundle args = new Bundle();
        args.putString("video_url", videoUrl);
        args.putString("video_title", title);
        args.putString("location_name", location);
        args.putString("time_description", time);
        args.putInt("photo_count", count);
        args.putString("document_id", documentId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_patientvideoopened, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rootView = view;
        videoView = view.findViewById(R.id.video_view);
        tvVideoTitle = view.findViewById(R.id.tv_video_title);
        tvSubtitlesContent = view.findViewById(R.id.tv_subtitles_content);
        btnClose = view.findViewById(R.id.btn_close);

        // Setup close button
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> closeVideoPlayer());
        }

        // Setup swipe-down gesture
        setupSwipeGesture();

        if (getArguments() != null) {
            videoUrl = getArguments().getString("video_url");
            videoDocumentId = getArguments().getString("document_id");
            String videoTitle = getArguments().getString("video_title");
            String locationName = getArguments().getString("location_name");
            String timeDescription = getArguments().getString("time_description");
            int photoCount = getArguments().getInt("photo_count", 0);

            Log.d(TAG, "Video URL: " + videoUrl);
            Log.d(TAG, "Document ID: " + videoDocumentId);

            tvVideoTitle.setText(videoTitle != null ? videoTitle : "Video Player");

            String subtitleText = "Location: " + (locationName != null ? locationName : "Unknown") + "\n" +
                    "Time: " + (timeDescription != null ? timeDescription : "Unknown") + "\n" +
                    "Photos: " + photoCount;
            tvSubtitlesContent.setText(subtitleText);

            setupVideo(subtitleText);
        }

        // Initialize emotion detection
        cameraExecutor = Executors.newSingleThreadExecutor();
        initializeEmotionDetection();
    }

    /**
     * Setup swipe-down gesture to close video
     */
    private void setupSwipeGesture() {
        gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                try {
                    float diffY = e2.getY() - e1.getY();
                    float diffX = e2.getX() - e1.getX();

                    // Check if swipe is primarily vertical (Y movement > X movement)
                    if (Math.abs(diffY) > Math.abs(diffX)) {
                        // Swipe down (positive Y direction)
                        if (diffY > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                            Log.d(TAG, "Swipe down detected - closing video");
                            closeVideoPlayer();
                            return true;
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error detecting swipe", e);
                }
                return false;
            }

            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }
        });

        // Apply touch listener to root view
        rootView.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            return false; // Allow other touch events to work (video controls)
        });
    }

    /**
     * Close video player and return to PatientHomeFragment
     */
    private void closeVideoPlayer() {
        Log.d(TAG, "Closing video player");

        // Stop video playback
        if (videoView != null && videoView.isPlaying()) {
            videoView.stopPlayback();
        }

        // Stop emotion detection
        emotionDetectionActive = false;
        if (emotionDetector != null) {
            saveEmotionData();
        }

        // Return to PatientHomeFragment
        if (getParentFragmentManager().getBackStackEntryCount() > 0) {
            getParentFragmentManager().popBackStack();
        } else {
            // Fallback: create new PatientHomeFragment
            PatientHomeFragment homeFragment = new PatientHomeFragment();
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.container, homeFragment)
                    .commit();
        }
    }

    private void initializeEmotionDetection() {
        if (videoDocumentId == null || videoDocumentId.isEmpty()) {
            Log.w(TAG, "No document ID provided - emotion tracking disabled");
            return;
        }

        try {
            emotionDetector = new BackgroundEmotionDetector(requireContext());
            Log.d(TAG, "Emotion detector initialized");

            if (hasCameraPermission()) {
                startEmotionDetection();
            } else {
                requestCameraPermission();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize emotion detector", e);
        }
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(requireActivity(),
                new String[]{Manifest.permission.CAMERA},
                CAMERA_PERMISSION_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startEmotionDetection();
            } else {
                Log.w(TAG, "Camera permission denied - emotion tracking disabled");
                Toast.makeText(getContext(),
                        "Camera access needed for emotion tracking",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startEmotionDetection() {
        Log.d(TAG, "Starting emotion detection");

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(requireContext());

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraForEmotionDetection();
            } catch (Exception e) {
                Log.e(TAG, "Failed to start camera for emotion detection", e);
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    @androidx.annotation.OptIn(markerClass = androidx.camera.core.ExperimentalGetImage.class)
    private void bindCameraForEmotionDetection() {
        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetResolution(new Size(640, 480))
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> {
            if (emotionDetectionActive && emotionDetector != null) {
                try {
                    emotionDetector.detectEmotionFromImageProxy(imageProxy,
                            (emotion, confidence, timestamp) -> {
                                Log.d(TAG, "Detected: " + emotion + " (" +
                                        String.format("%.0f%%", confidence * 100) + ")");
                            });
                } catch (Exception e) {
                    Log.e(TAG, "Error in emotion detection", e);
                    imageProxy.close();
                }
            } else {
                imageProxy.close();
            }
        });

        CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;

        try {
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis);
            Log.d(TAG, "Camera bound for emotion detection (invisible to user)");
        } catch (Exception e) {
            Log.e(TAG, "Failed to bind camera", e);
        }
    }

    private void setupVideo(String subtitleText) {
        if (videoUrl != null && !videoUrl.isEmpty()) {
            try {
                Uri videoUri = Uri.parse(videoUrl);
                Log.d(TAG, "Setting video URI: " + videoUri);

                videoView.setVideoURI(videoUri);

                MediaController mediaController = new MediaController(getContext());
                mediaController.setAnchorView(videoView);
                videoView.setMediaController(mediaController);

                tvSubtitlesContent.append("\n\nLoading video...");

                videoView.setOnPreparedListener(mp -> {
                    Log.d(TAG, "Video prepared, starting playback");
                    tvSubtitlesContent.setText(subtitleText);

                    emotionDetectionActive = true;
                    if (emotionDetector != null) {
                        emotionDetector.startVideoSession();
                        Log.d(TAG, "Emotion tracking STARTED");
                    }

                    // Save to watch history
                    saveToWatchHistory();

                    videoView.start();
                    Toast.makeText(getContext(), "Playing video", Toast.LENGTH_SHORT).show();
                });

                videoView.setOnCompletionListener(mp -> {
                    Log.d(TAG, "Video playback completed");

                    emotionDetectionActive = false;
                    saveEmotionData();

                    Toast.makeText(getContext(), "Video finished", Toast.LENGTH_SHORT).show();
                });

                videoView.setOnErrorListener((mp, what, extra) -> {
                    String errorMsg = "Error playing video. Code: " + what + ", Extra: " + extra;
                    Log.e(TAG, errorMsg);
                    emotionDetectionActive = false;
                    tvSubtitlesContent.setText(subtitleText + "\n\n" + errorMsg);
                    Toast.makeText(getContext(), "Cannot play video", Toast.LENGTH_LONG).show();
                    return true;
                });

                videoView.requestFocus();
            } catch (Exception e) {
                Log.e(TAG, "Exception setting up video", e);
                tvSubtitlesContent.append("\n\nError: " + e.getMessage());
                Toast.makeText(getContext(), "Failed to load video", Toast.LENGTH_LONG).show();
            }
        } else {
            Log.e(TAG, "No video URL provided");
            tvSubtitlesContent.append("\n\nNo video URL available");
            Toast.makeText(getContext(), "No video URL", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveEmotionData() {
        if (emotionDetector == null || videoDocumentId == null || videoDocumentId.isEmpty()) {
            Log.w(TAG, "Cannot save emotion data - detector or documentId is null");
            return;
        }

        List<BackgroundEmotionDetector.EmotionRecord> timeline = emotionDetector.getEmotionTimeline();
        BackgroundEmotionDetector.EmotionSummary summary = emotionDetector.getEmotionSummary();

        Log.d(TAG, "Saving emotion data: " + timeline.size() + " records");

        List<String> emotionsList = new ArrayList<>();
        for (BackgroundEmotionDetector.EmotionRecord record : timeline) {
            emotionsList.add(record.emotion);
        }

        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference emotionRef = FirebaseDatabase.getInstance().getReference()
                .child("Patient")
                .child(userId)
                .child("videoEmotions")
                .child(videoDocumentId);

        Map<String, Object> emotionData = new HashMap<>();
        emotionData.put("emotions", emotionsList);
        emotionData.put("videoUrl", videoUrl);
        emotionData.put("recordedAt", System.currentTimeMillis());
        emotionData.put("totalEmotions", summary.getTotal());

        emotionData.put("happy", summary.getHappy());
        emotionData.put("sad", summary.getSad());
        emotionData.put("angry", summary.getAngry());
        emotionData.put("neutral", summary.getNeutral());
        emotionData.put("fear", summary.getFear());
        emotionData.put("disgust", summary.getDigust());
        emotionData.put("surprise", summary.getSurprise());

        emotionRef.setValue(emotionData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "✓ Emotion data saved successfully to Firebase");
                    Log.d(TAG, "  Total emotions detected: " + summary.getTotal());
                    Log.d(TAG, "  Emotions array: " + emotionsList.toString());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "✗ Failed to save emotion data to Firebase", e);
                });
    }

    /**
     * Save video to watch history
     */
    private void saveToWatchHistory() {
        if (videoUrl == null || videoUrl.isEmpty()) {
            return;
        }

        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        if (userId == null) {
            return;
        }

        DatabaseReference historyRef = FirebaseDatabase.getInstance().getReference()
                .child("Patient")
                .child(userId)
                .child("watchHistory")
                .push();

        Map<String, Object> historyData = new HashMap<>();
        historyData.put("videoUrl", videoUrl);
        historyData.put("title", tvVideoTitle.getText().toString());
        historyData.put("watchedAt", System.currentTimeMillis());
        historyData.put("documentId", videoDocumentId);

        if (getArguments() != null) {
            String locationName = getArguments().getString("location_name");
            historyData.put("locationName", locationName);
        }

        historyRef.setValue(historyData)
                .addOnSuccessListener(aVoid ->
                        Log.d(TAG, "✓ Video saved to watch history"))
                .addOnFailureListener(e ->
                        Log.e(TAG, "✗ Failed to save to watch history", e));
    }

    @Override
    public void onPause() {
        super.onPause();
        if (videoView != null && videoView.isPlaying()) {
            videoView.pause();
            emotionDetectionActive = false;
            Log.d(TAG, "Video paused - emotion detection paused");
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (videoView != null && !videoView.isPlaying()) {
            videoView.resume();
            if (emotionDetector != null) {
                emotionDetectionActive = true;
                Log.d(TAG, "Video resumed - emotion detection resumed");
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        Log.d(TAG, "Fragment destroyed - cleaning up");

        emotionDetectionActive = false;

        if (videoView != null) {
            videoView.stopPlayback();
            Log.d(TAG, "Video stopped");
        }

        if (emotionDetector != null) {
            emotionDetector.release();
            emotionDetector = null;
            Log.d(TAG, "Emotion detector released");
        }

        if (cameraProvider != null) {
            cameraProvider.unbindAll();
            Log.d(TAG, "Camera unbound");
        }

        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
            Log.d(TAG, "Camera executor shutdown");
        }
    }
}