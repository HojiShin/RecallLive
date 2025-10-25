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

    public void initializeForPatient(String patientUid, boolean isSignup) {
        Log.d(TAG, "╔══════════════════════════════════════════════════╗");
        Log.d(TAG, "  INITIALIZING VIDEO SERVICE");
        Log.d(TAG, "  Patient: " + patientUid);
        Log.d(TAG, "  Trigger: " + (isSignup ? "SIGNUP" : "LOGIN"));
        Log.d(TAG, "  TTS Enabled: " + ENABLE_TTS);
        Log.d(TAG, "  Max videos/day: " + MAX_VIDEOS_PER_DAY);
        Log.d(TAG, "╚══════════════════════════════════════════════════╝");

        // Check if new day first
        String today = getTodayDateString();
        String lastDate = getLastVideoDate();

        if (!today.equals(lastDate)) {
            Log.d(TAG, "🔄 New day detected - resetting counters BEFORE verification");
            resetDailyCount();
            clearGeneratedClusters();
        }

        // CRITICAL FIX: Verify actual video count in Firestore BEFORE checking limit
        verifyAndSyncVideoCount(patientUid, isSignup);
    }

    private void verifyAndSyncVideoCount(String patientUid, boolean isSignup) {
        String today = getTodayDateString();
        Log.d(TAG, "🔍 Verifying actual video count for today: " + today);

        Calendar todayStart = Calendar.getInstance();
        todayStart.set(Calendar.HOUR_OF_DAY, 0);
        todayStart.set(Calendar.MINUTE, 0);
        todayStart.set(Calendar.SECOND, 0);
        todayStart.set(Calendar.MILLISECOND, 0);

        firestore.collection("memory_videos")
                .whereEqualTo("patientUid", patientUid)
                .whereGreaterThanOrEqualTo("createdAt", new com.google.firebase.Timestamp(todayStart.getTimeInMillis() / 1000, 0))
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    int actualCount = querySnapshot.size();
                    int storedCount = prefs.getInt(KEY_DAILY_VIDEO_COUNT, 0);

                    Log.d(TAG, "═══════════════════════════════════════════════");
                    Log.d(TAG, "VIDEO COUNT VERIFICATION");
                    Log.d(TAG, "Stored count: " + storedCount);
                    Log.d(TAG, "Actual Firestore count: " + actualCount);
                    Log.d(TAG, "═══════════════════════════════════════════════");

                    // CRITICAL: Update the stored count to match reality
                    if (actualCount != storedCount) {
                        Log.d(TAG, "⚠️ COUNT MISMATCH - Syncing to actual: " + actualCount);
                        prefs.edit()
                                .putString(KEY_LAST_VIDEO_DATE, today)
                                .putInt(KEY_DAILY_VIDEO_COUNT, actualCount)
                                .apply();
                    }

                    // Continue with VERIFIED count
                    continueInitialization(patientUid, isSignup, actualCount);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to verify video count: " + e.getMessage());
                    Log.d(TAG, "⚠️ Resetting count to 0 due to verification failure");

                    // On failure, reset to 0 to be safe
                    prefs.edit()
                            .putString(KEY_LAST_VIDEO_DATE, getTodayDateString())
                            .putInt(KEY_DAILY_VIDEO_COUNT, 0)
                            .apply();

                    continueInitialization(patientUid, isSignup, 0);
                });
    }

    private void continueInitialization(String patientUid, boolean isSignup, int verifiedCount) {
        Log.d(TAG, "📊 Continuing with verified count: " + verifiedCount + "/" + MAX_VIDEOS_PER_DAY);

        if (verifiedCount >= MAX_VIDEOS_PER_DAY) {
            Log.d(TAG, "⚠️ Daily video limit reached (" + verifiedCount + "/" + MAX_VIDEOS_PER_DAY + ")");
            Log.d(TAG, "Scheduling daily generation for tomorrow");
            scheduleDailyVideoGeneration(patientUid);
            return;
        }

        Log.d(TAG, "✓ Proceeding with video generation");

        AppExecutors.getInstance().networkIO().execute(() -> {
            try {
                generateVideoForPatient(patientUid, isSignup ? "signup" : "login");
            } catch (Exception e) {
                Log.e(TAG, "❌ Error in video generation", e);
            }
        });

        scheduleDailyVideoGeneration(patientUid);
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

    public void generateVideoForPatient(String patientUid, String triggerType) {
        Log.d(TAG, "═══════════════════════════════════════════════════");
        Log.d(TAG, "GENERATING VIDEO FOR PATIENT");
        Log.d(TAG, "Patient: " + patientUid);
        Log.d(TAG, "Trigger: " + triggerType);
        Log.d(TAG, "═══════════════════════════════════════════════════");

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
                    geocodeClustersAndGenerate(clusters, patientUid, triggerType);
                }

                @Override
                public void onError(String error) {
                    Log.e(TAG, "❌ Failed to load clusters: " + error);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "❌ Exception in generateVideoForPatient", e);
        }
    }

    private void geocodeClustersAndGenerate(List<PhotoClusteringManager.PhotoCluster> clusters, String patientUid, String triggerType) {
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
                Log.d(TAG, "🎯 Step 3: Selecting cluster for video...");

                PhotoClusteringManager.PhotoCluster selectedCluster = selectClusterForVideo(geocodedClusters);

                if (selectedCluster == null || selectedCluster.getPhotoCount() < 1) {
                    Log.e(TAG, "❌ No suitable cluster found");
                    return;
                }

                Log.d(TAG, "✓ Selected: " + selectedCluster.getClusterId());
                Log.d(TAG, "  Location: " + selectedCluster.getLocationName());
                Log.d(TAG, "  Photos: " + selectedCluster.getPhotoCount());

                if (ENABLE_TTS) {
                    Log.d(TAG, "🎤 Step 4: Generating video WITH TTS...");
                    generateVideoWithTTS(selectedCluster, patientUid, triggerType);
                } else {
                    Log.d(TAG, "🎬 Step 4: Generating SILENT video...");
                    generateSilentVideo(selectedCluster, patientUid, triggerType);
                }
            }
        });
    }

    private void generateVideoWithTTS(PhotoClusteringManager.PhotoCluster cluster, String patientUid, String triggerType) {
        Log.d(TAG, "═══════════════════════════════════════════════════");
        Log.d(TAG, "VIDEO GENERATION WITH TTS AUDIO");
        Log.d(TAG, "═══════════════════════════════════════════════════");

        ttsGenerator.generateCompleteNarration(cluster, new TTSVideoGenerator.TTSGenerationCallback() {
            @Override
            public void onAudioGenerated(String audioFilePath, int durationSeconds) {
                Log.d(TAG, "╔══════════════════════════════════════════════╗");
                Log.d(TAG, "   ✓ TTS AUDIO GENERATED");
                Log.d(TAG, "╚══════════════════════════════════════════════╝");
                Log.d(TAG, "Audio: " + audioFilePath);
                Log.d(TAG, "Duration: " + durationSeconds + "s");

                final File audioFile = new File(audioFilePath);

                Log.d(TAG, "🎬 Generating silent video...");
                Media3VideoGenerator videoGen = new Media3VideoGenerator(context, patientUid);

                videoGen.generateVideoFromCluster(cluster, durationSeconds, new Media3VideoGenerator.VideoGenerationCallback() {
                    @Override
                    public void onSuccess(String videoUrl, String documentId) {
                        Log.d(TAG, "✓ Silent video generated");
                        Log.d(TAG, "🔗 Merging audio + video...");

                        VideoAudioMerger.mergeVideoWithAudio(context, videoUrl, audioFile, patientUid, new VideoAudioMerger.MergeCallback() {
                            @Override
                            public void onMergeComplete(String mergedVideoUrl) {
                                Log.d(TAG, "╔══════════════════════════════════════════════╗");
                                Log.d(TAG, "  ✓✓✓ VIDEO WITH AUDIO COMPLETE ✓✓✓");
                                Log.d(TAG, "╚══════════════════════════════════════════════╝");
                                updateVideoUrl(documentId, mergedVideoUrl);
                                completeVideoGeneration(patientUid, documentId, triggerType, cluster.getClusterId(), mergedVideoUrl);
                                audioFile.delete();
                            }

                            @Override
                            public void onMergeError(String error) {
                                Log.e(TAG, "❌ Merge failed: " + error);
                                Log.d(TAG, "⚠️ Using silent video");
                                completeVideoGeneration(patientUid, documentId, triggerType, cluster.getClusterId(), videoUrl);
                                audioFile.delete();
                            }
                        });
                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "❌ Video generation failed: " + error);
                        recordVideoGenerationFailure(patientUid, error, triggerType);
                        audioFile.delete();
                    }
                });
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "❌ TTS failed: " + error);
                Log.d(TAG, "⚠️ Falling back to silent video");
                generateSilentVideo(cluster, patientUid, triggerType);
            }
        });
    }

    private void generateSilentVideo(PhotoClusteringManager.PhotoCluster cluster, String patientUid, String triggerType) {
        Log.d(TAG, "═══════════════════════════════════════════════════");
        Log.d(TAG, "GENERATING SILENT VIDEO");
        Log.d(TAG, "═══════════════════════════════════════════════════");

        Media3VideoGenerator generator = new Media3VideoGenerator(context, patientUid);

        generator.generateVideoFromCluster(cluster, 0, new Media3VideoGenerator.VideoGenerationCallback() {
            @Override
            public void onSuccess(String videoUrl, String documentId) {
                Log.d(TAG, "╔══════════════════════════════════════════════╗");
                Log.d(TAG, "   ✓ SILENT VIDEO COMPLETE");
                Log.d(TAG, "╚══════════════════════════════════════════════╝");
                completeVideoGeneration(patientUid, documentId, triggerType, cluster.getClusterId(), videoUrl);
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "╔══════════════════════════════════════════════╗");
                Log.e(TAG, "  ❌ VIDEO GENERATION FAILED");
                Log.e(TAG, "╚══════════════════════════════════════════════╝");
                Log.e(TAG, "Error: " + error);
                recordVideoGenerationFailure(patientUid, error, triggerType);
            }
        });
    }

    private void updateVideoUrl(String documentId, String newVideoUrl) {
        firestore.collection("memory_videos").document(documentId).update("videoUrl", newVideoUrl)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "✓ Video URL updated"))
                .addOnFailureListener(e -> Log.e(TAG, "❌ Failed to update URL", e));
    }

    private void completeVideoGeneration(String patientUid, String documentId, String triggerType, String clusterId, String videoUrl) {
        Log.d(TAG, "═══════════════════════════════════════════════════");
        Log.d(TAG, "FINALIZING VIDEO");
        Log.d(TAG, "Document: " + documentId);
        Log.d(TAG, "URL: " + videoUrl);
        Log.d(TAG, "═══════════════════════════════════════════════════");

        recordVideoGeneration(patientUid, documentId, triggerType, clusterId);
        incrementDailyCount();
        markClusterAsGenerated(clusterId);
        notifyPatientOfNewVideo(patientUid, videoUrl);

        Log.d(TAG, "╔══════════════════════════════════════════════╗");
        Log.d(TAG, "  ✓✓✓ ALL COMPLETE ✓✓✓");
        Log.d(TAG, "╚══════════════════════════════════════════════╝");
    }

    private PhotoClusteringManager.PhotoCluster selectClusterForVideo(List<PhotoClusteringManager.PhotoCluster> clusters) {
        if (clusters == null || clusters.isEmpty()) return null;

        Set<String> generatedClusters = getGeneratedClusters();
        List<PhotoClusteringManager.PhotoCluster> availableClusters = new ArrayList<>();

        for (PhotoClusteringManager.PhotoCluster cluster : clusters) {
            if (cluster.getPhotoCount() >= 1 && !generatedClusters.contains(cluster.getClusterId())) {
                availableClusters.add(cluster);
            }
        }

        if (availableClusters.isEmpty()) {
            for (PhotoClusteringManager.PhotoCluster cluster : clusters) {
                if (cluster.getPhotoCount() >= 1) {
                    availableClusters.add(cluster);
                }
            }
        }

        if (availableClusters.isEmpty()) return null;

        availableClusters.sort((a, b) -> {
            int countDiff = b.getPhotoCount() - a.getPhotoCount();
            if (countDiff != 0) return countDiff;
            return Long.compare(b.getEndTime(), a.getEndTime());
        });

        Random random = new Random();
        int topChoices = Math.min(3, availableClusters.size());
        return availableClusters.get(random.nextInt(topChoices));
    }

    public void cleanupOldVideos(String patientUid) {
        Log.d(TAG, "═══════════════════════════════════════════════════");
        Log.d(TAG, "CLEANING UP OLD VIDEOS");
        Log.d(TAG, "═══════════════════════════════════════════════════");

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

                        if (createdAt != null && createdAt.toDate().getTime() < todayStartTime) {
                            deleteCount++;
                            String videoUrl = doc.getString("videoUrl");
                            String docId = doc.getId();

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

                            doc.getReference().delete()
                                    .addOnSuccessListener(aVoid -> Log.d(TAG, "  ✓ Deleted firestore: " + docId))
                                    .addOnFailureListener(e -> Log.e(TAG, "  ❌ Firestore delete failed: " + docId));
                        }
                    }

                    if (deleteCount == 0) {
                        Log.d(TAG, "✓ No old videos to clean");
                    } else {
                        Log.d(TAG, "✓ CLEANUP COMPLETE: " + deleteCount + " videos");
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

        firestore.collection("video_generation_log").add(record)
                .addOnSuccessListener(ref -> Log.d(TAG, "✓ Generation logged"))
                .addOnFailureListener(e -> Log.e(TAG, "❌ Log failed", e));
    }

    private void recordVideoGenerationFailure(String patientUid, String error, String triggerType) {
        Map<String, Object> record = new HashMap<>();
        record.put("patientUid", patientUid);
        record.put("error", error);
        record.put("triggerType", triggerType);
        record.put("timestamp", FieldValue.serverTimestamp());
        record.put("success", false);

        firestore.collection("video_generation_log").add(record)
                .addOnSuccessListener(ref -> Log.d(TAG, "✓ Failure logged"))
                .addOnFailureListener(e -> Log.e(TAG, "❌ Log failed", e));
    }

    private void notifyPatientOfNewVideo(String patientUid, String videoUrl) {
        DatabaseReference patientRef = FirebaseDatabase.getInstance().getReference().child("Patient").child(patientUid);
        Map<String, Object> updates = new HashMap<>();
        updates.put("latestVideoUrl", videoUrl);
        updates.put("latestVideoTimestamp", System.currentTimeMillis());
        updates.put("hasNewVideo", true);

        patientRef.updateChildren(updates)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "✓ Patient notified"))
                .addOnFailureListener(e -> Log.e(TAG, "❌ Notify failed", e));
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
        Log.d(TAG, "✓ Count: " + count + "/" + MAX_VIDEOS_PER_DAY);
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
        Log.d(TAG, "✓ Cluster marked as generated: " + clusterId);
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

            Log.d(TAG, "═══════════════════════════════════════════════════");
            Log.d(TAG, "DAILY VIDEO WORKER");
            Log.d(TAG, "Patient: " + patientUid);
            Log.d(TAG, "═══════════════════════════════════════════════════");

            try {
                AutomaticVideoService service = new AutomaticVideoService(getApplicationContext());
                service.resetDailyCount();

                if (ENABLE_AUTO_CLEANUP) {
                    service.cleanupOldVideos(patientUid);
                }

                service.generateVideoForPatient(patientUid, "daily");
                Log.d(TAG, "✓ DAILY WORKER COMPLETE");
                return Result.success();
            } catch (Exception e) {
                Log.e(TAG, "❌ Daily worker failed", e);
                return Result.failure();
            }
        }
    }
}

