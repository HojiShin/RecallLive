package com.example.recalllive;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.recalllive.AppExecutors;
import com.example.recalllive.FirebaseClusterManager;
import com.example.recalllive.LocationGeocoderService;
import com.example.recalllive.Media3VideoGenerator;
import com.example.recalllive.PhotoClusteringManager;
import com.example.recalllive.TTSVideoGenerator;
import com.example.recalllive.VideoAudioMerger;
import com.example.recalllive.VideoConfiguration;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class AutomaticVideoService {
    private static final String TAG = "AutomaticVideoService";
    private static final String PREFS_NAME = "RecallLiveVideoPrefs";
    private static final String KEY_LAST_VIDEO_DATE = "last_video_date";
    private static final String KEY_DAILY_VIDEO_COUNT = "daily_video_count";
    private static final String KEY_GENERATED_CLUSTERS = "generated_clusters";
    private static final String WORK_TAG_DAILY = "daily_video_generation";
    private static final int MAX_VIDEOS_PER_DAY = VideoConfiguration.MAX_VIDEOS_PER_DAY;
    private static final boolean ENABLE_AUTO_CLEANUP = VideoConfiguration.ENABLE_AUTO_CLEANUP;
    private static final boolean ENABLE_TTS = VideoConfiguration.ENABLE_TTS_NARRATION;

    private final Context context;
    private final SharedPreferences prefs;
    private final WorkManager workManager;
    private final FirebaseFirestore firestore;
    private final LocationGeocoderService geocoder;
    private final TTSVideoGenerator ttsGenerator;

    public AutomaticVideoService(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.workManager = WorkManager.getInstance(context);
        this.firestore = FirebaseFirestore.getInstance();
        this.geocoder = new LocationGeocoderService(context);
        this.ttsGenerator = new TTSVideoGenerator(context);
    }

    public void initializeForPatient(String patientUid, boolean isSignupOrLogin) {
        Log.d(TAG, "╔═══════════════════════════════════════════╗");
        Log.d(TAG, "  INITIALIZING VIDEO SERVICE");
        Log.d(TAG, "  Patient: " + patientUid);
        Log.d(TAG, "  Trigger: " + (isSignupOrLogin ? "SIGNUP/LOGIN" : "BACKGROUND"));
        Log.d(TAG, "  TTS Enabled: " + ENABLE_TTS);
        Log.d(TAG, "  Max videos/day: " + MAX_VIDEOS_PER_DAY);
        Log.d(TAG, "╚═══════════════════════════════════════════╝");

        // Check if new day first
        String today = getTodayDateString();
        String lastDate = getLastVideoDate();

        if (!today.equals(lastDate)) {
            Log.d(TAG, "🔄 New day detected - cleaning up and resetting");

            // CLEANUP OLD VIDEOS FIRST
            if (ENABLE_AUTO_CLEANUP) {
                cleanupOldVideos(patientUid);
            }

            // Reset counters
            resetDailyCount();
            clearGeneratedClusters();
        }

        // GENERATE 10 VIDEOS on signup/login
        if (isSignupOrLogin) {
            Log.d(TAG, "🎬 SIGNUP/LOGIN DETECTED - Generating " + MAX_VIDEOS_PER_DAY + " videos");
            generateMultipleVideosForPatient(patientUid, MAX_VIDEOS_PER_DAY, "signup_login");
        }

        // Schedule daily video generation at midnight
        scheduleDailyVideoGeneration(patientUid);
    }

    /**
     * UPDATED: Generate multiple videos at once
     */
    private void generateMultipleVideosForPatient(String patientUid, int videosToGenerate, String triggerType) {
        Log.d(TAG, "═══════════════════════════════════════════");
        Log.d(TAG, "GENERATING " + videosToGenerate + " VIDEOS");
        Log.d(TAG, "Patient: " + patientUid);
        Log.d(TAG, "Trigger: " + triggerType);
        Log.d(TAG, "═══════════════════════════════════════════");

        AppExecutors.getInstance().networkIO().execute(() -> {
            try {
                Log.d(TAG, "📂 Step 1: Loading photo clusters...");
                FirebaseClusterManager clusterManager = new FirebaseClusterManager(context, patientUid);

                clusterManager.getClusters(new FirebaseClusterManager.OnClustersRetrievedCallback() {
                    @Override
                    public void onClustersRetrieved(List<PhotoClusteringManager.PhotoCluster> clusters) {
                        if (clusters == null || clusters.isEmpty()) {
                            Log.e(TAG, "❌ No clusters found");
                            return;
                        }
                        Log.d(TAG, "✓ Found " + clusters.size() + " clusters");
                        Log.d(TAG, "🌍 Step 2: Geocoding locations...");

                        geocodeClustersAndGenerateMultiple(clusters, patientUid, triggerType, videosToGenerate);
                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "❌ Failed to load clusters: " + error);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "❌ Exception in generateMultipleVideosForPatient", e);
            }
        });
    }

    private void geocodeClustersAndGenerateMultiple(List<PhotoClusteringManager.PhotoCluster> clusters,
                                                    String patientUid, String triggerType, int videosToGenerate) {
        geocoder.geocodeClusters(clusters, new LocationGeocoderService.ClusterGeocodeCallback() {
            @Override
            public void onProgress(int processed, int total) {
                if (processed % 5 == 0) {
                    Log.d(TAG, "  Geocoding: " + processed + "/" + total);
                }
            }

            @Override
            public void onComplete(List<PhotoClusteringManager.PhotoCluster> geocodedClusters) {
                Log.d(TAG, "✓ Geocoding complete");
                Log.d(TAG, "🎯 Step 3: Generating " + videosToGenerate + " videos...");

                // Get available clusters (not already used today)
                List<PhotoClusteringManager.PhotoCluster> availableClusters = getAvailableClusters(geocodedClusters);

                if (availableClusters.isEmpty()) {
                    Log.w(TAG, "⚠️ No available clusters - resetting and using all clusters");
                    clearGeneratedClusters();
                    availableClusters = new ArrayList<>(geocodedClusters);
                }

                int videosToCreate = Math.min(videosToGenerate, availableClusters.size());
                Log.d(TAG, "Creating " + videosToCreate + " videos from " + availableClusters.size() + " available clusters");

                AtomicInteger completedVideos = new AtomicInteger(0);
                AtomicInteger failedVideos = new AtomicInteger(0);

                // Generate videos sequentially to avoid overwhelming the system
                for (int i = 0; i < videosToCreate; i++) {
                    PhotoClusteringManager.PhotoCluster selectedCluster = selectRandomCluster(availableClusters);

                    if (selectedCluster == null) {
                        Log.w(TAG, "⚠️ No more clusters available");
                        break;
                    }

                    // Remove from available list so we don't use it again
                    availableClusters.remove(selectedCluster);

                    final int videoNumber = i + 1;
                    Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                    Log.d(TAG, "📹 VIDEO " + videoNumber + "/" + videosToCreate);
                    Log.d(TAG, "Cluster: " + selectedCluster.getClusterId());
                    Log.d(TAG, "Location: " + selectedCluster.getLocationName());
                    Log.d(TAG, "Photos: " + selectedCluster.getPhotoCount());
                    Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

                    if (ENABLE_TTS) {
                        generateVideoWithTTS(selectedCluster, patientUid, triggerType, new VideoCompletionCallback() {
                            @Override
                            public void onComplete(boolean success) {
                                if (success) {
                                    completedVideos.incrementAndGet();
                                } else {
                                    failedVideos.incrementAndGet();
                                }

                                int total = completedVideos.get() + failedVideos.get();
                                Log.d(TAG, "Progress: " + total + "/" + videosToCreate +
                                        " (✓ " + completedVideos.get() + " | ✗ " + failedVideos.get() + ")");
                            }
                        });
                    } else {
                        generateSilentVideo(selectedCluster, patientUid, triggerType, new VideoCompletionCallback() {
                            @Override
                            public void onComplete(boolean success) {
                                if (success) {
                                    completedVideos.incrementAndGet();
                                } else {
                                    failedVideos.incrementAndGet();
                                }

                                int total = completedVideos.get() + failedVideos.get();
                                Log.d(TAG, "Progress: " + total + "/" + videosToCreate +
                                        " (✓ " + completedVideos.get() + " | ✗ " + failedVideos.get() + ")");
                            }
                        });
                    }

                    // Add small delay between videos to prevent overwhelming the system
                    try {
                        Thread.sleep(1000); // 1 second delay
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private interface VideoCompletionCallback {
        void onComplete(boolean success);
    }

    private List<PhotoClusteringManager.PhotoCluster> getAvailableClusters(List<PhotoClusteringManager.PhotoCluster> allClusters) {
        Set<String> usedClusters = getGeneratedClusters();
        List<PhotoClusteringManager.PhotoCluster> available = new ArrayList<>();

        for (PhotoClusteringManager.PhotoCluster cluster : allClusters) {
            if (cluster.getPhotoCount() >= 1 && !usedClusters.contains(cluster.getClusterId())) {
                available.add(cluster);
            }
        }

        return available;
    }

    private PhotoClusteringManager.PhotoCluster selectRandomCluster(List<PhotoClusteringManager.PhotoCluster> clusters) {
        if (clusters.isEmpty()) return null;

        // Sort by photo count (prefer clusters with more photos)
        clusters.sort((a, b) -> b.getPhotoCount() - a.getPhotoCount());

        // Randomly select from top 5 clusters (or all if less than 5)
        Random random = new Random();
        int topChoices = Math.min(5, clusters.size());
        return clusters.get(random.nextInt(topChoices));
    }

    private void scheduleDailyVideoGeneration(String patientUid) {
        Calendar calendar = Calendar.getInstance();
        Calendar targetTime = Calendar.getInstance();
        targetTime.set(Calendar.HOUR_OF_DAY, VideoConfiguration.DAILY_GENERATION_HOUR);
        targetTime.set(Calendar.MINUTE, 0);
        targetTime.set(Calendar.SECOND, 0);
        targetTime.set(Calendar.MILLISECOND, 0);

        if (calendar.after(targetTime)) {
            targetTime.add(Calendar.DAY_OF_MONTH, 1);
        }

        long initialDelay = targetTime.getTimeInMillis() - calendar.getTimeInMillis();

        Data inputData = new Data.Builder().putString("patient_uid", patientUid).build();
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build();

        PeriodicWorkRequest dailyWork = new PeriodicWorkRequest.Builder(DailyVideoWorker.class, 24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .setInputData(inputData)
                .addTag(WORK_TAG_DAILY)
                .build();

        workManager.enqueueUniquePeriodicWork(WORK_TAG_DAILY + "_" + patientUid, ExistingPeriodicWorkPolicy.REPLACE, dailyWork);
        Log.d(TAG, "✓ Daily generation scheduled for: " + targetTime.getTime());
    }

    private void generateVideoWithTTS(PhotoClusteringManager.PhotoCluster cluster, String patientUid,
                                      String triggerType, VideoCompletionCallback callback) {
        ttsGenerator.generateCompleteNarration(cluster, new TTSVideoGenerator.TTSGenerationCallback() {
            @Override
            public void onAudioGenerated(String audioFilePath, int durationSeconds) {
                final File audioFile = new File(audioFilePath);
                Media3VideoGenerator videoGen = new Media3VideoGenerator(context, patientUid);

                videoGen.generateVideoFromCluster(cluster, durationSeconds, new Media3VideoGenerator.VideoGenerationCallback() {
                    @Override
                    public void onSuccess(String videoUrl, String documentId) {
                        VideoAudioMerger.mergeVideoWithAudio(context, videoUrl, audioFile, patientUid, new VideoAudioMerger.MergeCallback() {
                            @Override
                            public void onMergeComplete(String mergedVideoUrl) {
                                updateVideoUrl(documentId, mergedVideoUrl);
                                completeVideoGeneration(patientUid, documentId, triggerType, cluster.getClusterId(), mergedVideoUrl);
                                audioFile.delete();
                                if (callback != null) callback.onComplete(true);
                            }

                            @Override
                            public void onMergeError(String error) {
                                Log.e(TAG, "❌ Merge failed: " + error);
                                completeVideoGeneration(patientUid, documentId, triggerType, cluster.getClusterId(), videoUrl);
                                audioFile.delete();
                                if (callback != null) callback.onComplete(true);
                            }
                        });
                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "❌ Video generation failed: " + error);
                        recordVideoGenerationFailure(patientUid, error, triggerType);
                        audioFile.delete();
                        if (callback != null) callback.onComplete(false);
                    }
                });
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "❌ TTS failed: " + error);
                generateSilentVideo(cluster, patientUid, triggerType, callback);
            }
        });
    }

    private void generateSilentVideo(PhotoClusteringManager.PhotoCluster cluster, String patientUid,
                                     String triggerType, VideoCompletionCallback callback) {
        Media3VideoGenerator generator = new Media3VideoGenerator(context, patientUid);

        generator.generateVideoFromCluster(cluster, 0, new Media3VideoGenerator.VideoGenerationCallback() {
            @Override
            public void onSuccess(String videoUrl, String documentId) {
                completeVideoGeneration(patientUid, documentId, triggerType, cluster.getClusterId(), videoUrl);
                if (callback != null) callback.onComplete(true);
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "❌ Video generation failed: " + error);
                recordVideoGenerationFailure(patientUid, error, triggerType);
                if (callback != null) callback.onComplete(false);
            }
        });
    }

    private void updateVideoUrl(String documentId, String newVideoUrl) {
        firestore.collection("memory_videos").document(documentId).update("videoUrl", newVideoUrl)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "✓ Video URL updated"))
                .addOnFailureListener(e -> Log.e(TAG, "❌ Failed to update URL", e));
    }

    private void completeVideoGeneration(String patientUid, String documentId, String triggerType, String clusterId, String videoUrl) {
        recordVideoGeneration(patientUid, documentId, triggerType, clusterId);
        incrementDailyCount();
        markClusterAsGenerated(clusterId);

        int currentCount = getTodayVideoCount();
        Log.d(TAG, "✓ Video complete (" + currentCount + "/" + MAX_VIDEOS_PER_DAY + ")");
    }

    /**
     * UPDATED: Delete ALL videos from previous days, keep only today's
     */
    public void cleanupOldVideos(String patientUid) {
        Log.d(TAG, "═══════════════════════════════════════════");
        Log.d(TAG, "CLEANING UP OLD VIDEOS (Keeping only today's)");
        Log.d(TAG, "═══════════════════════════════════════════");

        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        long todayStartTime = today.getTimeInMillis();

        firestore.collection("memory_videos")
                .whereEqualTo("patientUid", patientUid)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    int deleteCount = 0;

                    for (com.google.firebase.firestore.QueryDocumentSnapshot doc : querySnapshot) {
                        com.google.firebase.Timestamp createdAt = doc.getTimestamp("createdAt");

                        // Delete if created BEFORE today
                        if (createdAt != null && createdAt.toDate().getTime() < todayStartTime) {
                            deleteCount++;
                            String videoUrl = doc.getString("videoUrl");
                            String docId = doc.getId();

                            // Delete from Storage
                            if (videoUrl != null && !videoUrl.isEmpty()) {
                                try {
                                    com.google.firebase.storage.FirebaseStorage.getInstance()
                                            .getReferenceFromUrl(videoUrl)
                                            .delete()
                                            .addOnSuccessListener(aVoid -> Log.d(TAG, "  ✓ Deleted storage: " + docId))
                                            .addOnFailureListener(e -> Log.w(TAG, "  ⚠️ Storage delete failed: " + docId));
                                } catch (Exception e) {
                                    Log.w(TAG, "  ⚠️ Storage error", e);
                                }
                            }

                            // Delete from Firestore
                            doc.getReference().delete()
                                    .addOnSuccessListener(aVoid -> Log.d(TAG, "  ✓ Deleted firestore: " + docId))
                                    .addOnFailureListener(e -> Log.e(TAG, "  ❌ Firestore delete failed: " + docId));
                        }
                    }

                    if (deleteCount == 0) {
                        Log.d(TAG, "✓ No old videos to clean");
                    } else {
                        Log.d(TAG, "✓ CLEANUP COMPLETE: " + deleteCount + " old videos deleted");
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "❌ Cleanup failed: " + e.getMessage()));
    }

    private void recordVideoGeneration(String patientUid, String videoId, String triggerType, String clusterId) {
        Map<String, Object> record = new HashMap<>();
        record.put("patientUid", patientUid);
        record.put("videoId", videoId);
        record.put("clusterId", clusterId);
        record.put("triggerType", triggerType);
        record.put("timestamp", FieldValue.serverTimestamp());
        record.put("success", true);

        firestore.collection("video_generation_log").add(record);
    }

    private void recordVideoGenerationFailure(String patientUid, String error, String triggerType) {
        Map<String, Object> record = new HashMap<>();
        record.put("patientUid", patientUid);
        record.put("error", error);
        record.put("triggerType", triggerType);
        record.put("timestamp", FieldValue.serverTimestamp());
        record.put("success", false);

        firestore.collection("video_generation_log").add(record);
    }

    private String getTodayDateString() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private String getLastVideoDate() {
        return prefs.getString(KEY_LAST_VIDEO_DATE, "");
    }

    private int getTodayVideoCount() {
        String today = getTodayDateString();
        String lastDate = getLastVideoDate();
        if (today.equals(lastDate)) {
            return prefs.getInt(KEY_DAILY_VIDEO_COUNT, 0);
        }
        return 0;
    }

    private void incrementDailyCount() {
        String today = getTodayDateString();
        int count = getTodayVideoCount() + 1;
        prefs.edit().putString(KEY_LAST_VIDEO_DATE, today).putInt(KEY_DAILY_VIDEO_COUNT, count).apply();
    }

    public void resetDailyCount() {
        prefs.edit()
                .putString(KEY_LAST_VIDEO_DATE, getTodayDateString())
                .putInt(KEY_DAILY_VIDEO_COUNT, 0)
                .apply();
        Log.d(TAG, "✓ Count reset to 0");
    }

    private Set<String> getGeneratedClusters() {
        Set<String> clusters = prefs.getStringSet(KEY_GENERATED_CLUSTERS, new HashSet<>());
        return new HashSet<>(clusters);
    }

    private void markClusterAsGenerated(String clusterId) {
        Set<String> clusters = getGeneratedClusters();
        clusters.add(clusterId);
        prefs.edit().putStringSet(KEY_GENERATED_CLUSTERS, clusters).apply();
    }

    private void clearGeneratedClusters() {
        prefs.edit().remove(KEY_GENERATED_CLUSTERS).apply();
        Log.d(TAG, "✓ Generated clusters cleared");
    }

    public void stopForPatient(String patientUid) {
        Log.d(TAG, "Stopping service for: " + patientUid);
        workManager.cancelAllWorkByTag(WORK_TAG_DAILY + "_" + patientUid);
        ttsGenerator.release();
        Log.d(TAG, "✓ Service stopped");
    }

    /**
     * UPDATED: Daily worker now generates 10 videos and cleans up old ones
     */
    public static class DailyVideoWorker extends Worker {
        private static final String TAG = "DailyVideoWorker";

        public DailyVideoWorker(@NonNull Context context, @NonNull WorkerParameters params) {
            super(context, params);
        }

        @NonNull
        @Override
        public Result doWork() {
            String patientUid = getInputData().getString("patient_uid");
            if (patientUid == null) {
                Log.e(TAG, "❌ No patient UID");
                return Result.failure();
            }

            Log.d(TAG, "═══════════════════════════════════════════");
            Log.d(TAG, "DAILY VIDEO WORKER - MIDNIGHT GENERATION");
            Log.d(TAG, "Patient: " + patientUid);
            Log.d(TAG, "═══════════════════════════════════════════");

            try {
                AutomaticVideoService service = new AutomaticVideoService(getApplicationContext());

                // Reset counters for new day
                service.resetDailyCount();
                service.clearGeneratedClusters();

                // Clean up yesterday's videos
                if (ENABLE_AUTO_CLEANUP) {
                    service.cleanupOldVideos(patientUid);
                }

                // Generate 10 new videos for today
                service.generateMultipleVideosForPatient(patientUid, MAX_VIDEOS_PER_DAY, "daily_midnight");

                Log.d(TAG, "✓ DAILY WORKER COMPLETE");
                return Result.success();
            } catch (Exception e) {
                Log.e(TAG, "❌ Daily worker failed", e);
                return Result.failure();
            }
        }
    }
}