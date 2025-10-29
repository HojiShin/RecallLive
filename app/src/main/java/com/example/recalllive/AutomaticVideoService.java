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

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * FIXED: Automatic Video Service with proper signup vs login handling
 */
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

    /**
     * FIXED: Initialize for patient with proper signup vs login handling
     * @param isSignup: true = SIGNUP (cleanup + generate 10), false = LOGIN (check + generate if needed)
     */
    public void initializeForPatient(String patientUid, boolean isSignup) {
        Log.d(TAG, "╔═══════════════════════════════════════╗");
        Log.d(TAG, "  INITIALIZING VIDEO SERVICE");
        Log.d(TAG, "  Patient: " + patientUid);
        Log.d(TAG, "  Trigger: " + (isSignup ? "SIGNUP (cleanup+generate)" : "LOGIN (check+generate)"));
        Log.d(TAG, "  TTS Enabled: " + ENABLE_TTS);
        Log.d(TAG, "  Max videos/day: " + MAX_VIDEOS_PER_DAY);
        Log.d(TAG, "╚═══════════════════════════════════════╝");

        String today = getTodayDateString();
        String lastDate = getLastVideoDate();

        if (!today.equals(lastDate)) {
            Log.d(TAG, "🔄 New day detected - resetting counters");
            resetDailyCount();
            clearGeneratedClusters();
        }

        if (isSignup) {
            Log.d(TAG, "╔═══════════════════════════════════════╗");
            Log.d(TAG, "  🆕 SIGNUP - Cleanup and Generate Fresh");
            Log.d(TAG, "╚═══════════════════════════════════════╝");
            clearGeneratedClusters();

            cleanupAllVideosWithCallback(patientUid, () -> {
                Log.d(TAG, "✓ Cleanup complete, generating 10 videos...");
                verifyAndSyncVideoCount(patientUid, true);
            });
        } else {
            Log.d(TAG, "╔═══════════════════════════════════════╗");
            Log.d(TAG, "  🔑 LOGIN - Check Existing Videos");
            Log.d(TAG, "╚═══════════════════════════════════════╝");

            Calendar todayStart = Calendar.getInstance();
            todayStart.set(Calendar.HOUR_OF_DAY, 0);
            todayStart.set(Calendar.MINUTE, 0);
            todayStart.set(Calendar.SECOND, 0);
            todayStart.set(Calendar.MILLISECOND, 0);

            firestore.collection("memory_videos")
                    .whereEqualTo("patientUid", patientUid)
                    .whereGreaterThanOrEqualTo("createdAt",
                            new com.google.firebase.Timestamp(todayStart.getTimeInMillis() / 1000, 0))
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        int todayCount = querySnapshot.size();

                        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                        Log.d(TAG, "VIDEO COUNT CHECK (LOGIN)");
                        Log.d(TAG, "Videos from today: " + todayCount + "/" + MAX_VIDEOS_PER_DAY);
                        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

                        if (todayCount >= MAX_VIDEOS_PER_DAY) {
                            Log.d(TAG, "✓ Enough videos exist for today");
                            prefs.edit()
                                    .putString(KEY_LAST_VIDEO_DATE, today)
                                    .putInt(KEY_DAILY_VIDEO_COUNT, todayCount)
                                    .apply();
                        } else {
                            int needed = MAX_VIDEOS_PER_DAY - todayCount;
                            Log.d(TAG, "🎬 Need to generate " + needed + " more videos");

                            prefs.edit()
                                    .putString(KEY_LAST_VIDEO_DATE, today)
                                    .putInt(KEY_DAILY_VIDEO_COUNT, todayCount)
                                    .apply();

                            generateMultipleVideosForPatient(patientUid, needed, "login_supplement");
                        }

                        scheduleDailyVideoGeneration(patientUid);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "⚠️ Could not check videos: " + e.getMessage());
                        verifyAndSyncVideoCount(patientUid, false);
                    });
        }
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
                .whereGreaterThanOrEqualTo("createdAt",
                        new com.google.firebase.Timestamp(todayStart.getTimeInMillis() / 1000, 0))
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    int actualCount = querySnapshot.size();
                    int storedCount = prefs.getInt(KEY_DAILY_VIDEO_COUNT, 0);

                    Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                    Log.d(TAG, "VIDEO COUNT VERIFICATION");
                    Log.d(TAG, "Stored count: " + storedCount);
                    Log.d(TAG, "Actual Firestore count: " + actualCount);
                    Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

                    if (actualCount != storedCount) {
                        Log.d(TAG, "⚠️ COUNT MISMATCH - Syncing to actual: " + actualCount);
                        prefs.edit()
                                .putString(KEY_LAST_VIDEO_DATE, today)
                                .putInt(KEY_DAILY_VIDEO_COUNT, actualCount)
                                .apply();
                    }

                    continueInitialization(patientUid, isSignup, actualCount);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to verify video count: " + e.getMessage());
                    prefs.edit()
                            .putString(KEY_LAST_VIDEO_DATE, today)
                            .putInt(KEY_DAILY_VIDEO_COUNT, 0)
                            .apply();

                    continueInitialization(patientUid, isSignup, 0);
                });
    }

    private void continueInitialization(String patientUid, boolean isSignup, int verifiedCount) {
        Log.d(TAG, "📊 Continuing with verified count: " + verifiedCount + "/" + MAX_VIDEOS_PER_DAY);

        if (isSignup) {
            Log.d(TAG, "🎬 SIGNUP: Generating 10 videos");
            generateMultipleVideosForPatient(patientUid, 10, "signup");
        } else {
            if (verifiedCount >= MAX_VIDEOS_PER_DAY) {
                Log.d(TAG, "⚠️ Daily video limit reached (" + verifiedCount + "/" + MAX_VIDEOS_PER_DAY + ")");
                scheduleDailyVideoGeneration(patientUid);
                return;
            }

            int needed = MAX_VIDEOS_PER_DAY - verifiedCount;
            Log.d(TAG, "🎬 BACKGROUND: Generating " + needed + " video(s)");
            generateMultipleVideosForPatient(patientUid, needed, "background");
        }

        scheduleDailyVideoGeneration(patientUid);
    }

    private void generateMultipleVideosForPatient(String patientUid, int videosToGenerate, String triggerType) {
        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        Log.d(TAG, "GENERATING " + videosToGenerate + " VIDEOS");
        Log.d(TAG, "Patient: " + patientUid);
        Log.d(TAG, "Trigger: " + triggerType);
        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

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

                List<PhotoClusteringManager.PhotoCluster> availableClusters = getAvailableClusters(geocodedClusters);

                if (availableClusters.isEmpty()) {
                    Log.w(TAG, "⚠️ No available clusters - resetting");
                    clearGeneratedClusters();
                    availableClusters = new ArrayList<>(geocodedClusters);
                }

                int videosToCreate = Math.min(videosToGenerate, availableClusters.size());
                Set<String> usedInThisSession = new HashSet<>();
                AtomicInteger completedVideos = new AtomicInteger(0);
                AtomicInteger failedVideos = new AtomicInteger(0);

                for (int i = 0; i < videosToCreate; i++) {
                    PhotoClusteringManager.PhotoCluster selectedCluster = selectUniqueCluster(availableClusters, usedInThisSession);

                    if (selectedCluster == null) {
                        break;
                    }

                    usedInThisSession.add(selectedCluster.getClusterId());
                    availableClusters.remove(selectedCluster);

                    final int videoNumber = i + 1;
                    Log.d(TAG, "🎥 VIDEO " + videoNumber + "/" + videosToCreate);

                    if (ENABLE_TTS) {
                        generateVideoWithTTS(selectedCluster, patientUid, triggerType, success -> {
                            if (success) completedVideos.incrementAndGet();
                            else failedVideos.incrementAndGet();
                        });
                    } else {
                        generateSilentVideo(selectedCluster, patientUid, triggerType, success -> {
                            if (success) completedVideos.incrementAndGet();
                            else failedVideos.incrementAndGet();
                        });
                    }

                    try {
                        Thread.sleep(1000);
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

    private List<PhotoClusteringManager.PhotoCluster> getAvailableClusters(
            List<PhotoClusteringManager.PhotoCluster> allClusters) {
        Set<String> usedClusters = getGeneratedClusters();
        List<PhotoClusteringManager.PhotoCluster> available = new ArrayList<>();

        for (PhotoClusteringManager.PhotoCluster cluster : allClusters) {
            if (cluster.getPhotoCount() >= 1 && !usedClusters.contains(cluster.getClusterId())) {
                available.add(cluster);
            }
        }

        return available;
    }

    private PhotoClusteringManager.PhotoCluster selectUniqueCluster(
            List<PhotoClusteringManager.PhotoCluster> clusters, Set<String> usedInSession) {
        if (clusters.isEmpty()) return null;

        List<PhotoClusteringManager.PhotoCluster> uniqueClusters = new ArrayList<>();
        for (PhotoClusteringManager.PhotoCluster cluster : clusters) {
            if (!usedInSession.contains(cluster.getClusterId())) {
                uniqueClusters.add(cluster);
            }
        }

        if (uniqueClusters.isEmpty()) return null;

        uniqueClusters.sort((a, b) -> b.getPhotoCount() - a.getPhotoCount());

        Random random = new Random();
        int topChoices = Math.min(5, uniqueClusters.size());
        return uniqueClusters.get(random.nextInt(topChoices));
    }

    private void generateVideoWithTTS(PhotoClusteringManager.PhotoCluster cluster, String patientUid,
                                      String triggerType, VideoCompletionCallback callback) {
        ttsGenerator.generateCompleteNarration(cluster, new TTSVideoGenerator.TTSGenerationCallback() {
            @Override
            public void onAudioGenerated(String audioFilePath, int durationSeconds) {
                java.io.File audioFile = new java.io.File(audioFilePath);
                Media3VideoGenerator videoGen = new Media3VideoGenerator(context, patientUid);

                videoGen.generateVideoFromCluster(cluster, durationSeconds,
                        new Media3VideoGenerator.VideoGenerationCallback() {
                            @Override
                            public void onSuccess(String videoUrl, String documentId) {
                                VideoAudioMerger.mergeVideoWithAudio(context, videoUrl, audioFile, patientUid,
                                        new VideoAudioMerger.MergeCallback() {
                                            @Override
                                            public void onMergeComplete(String mergedVideoUrl) {
                                                updateVideoUrl(documentId, mergedVideoUrl);
                                                completeVideoGeneration(patientUid, documentId, triggerType,
                                                        cluster.getClusterId(), mergedVideoUrl);
                                                audioFile.delete();
                                                if (callback != null) callback.onComplete(true);
                                            }

                                            @Override
                                            public void onMergeError(String error) {
                                                completeVideoGeneration(patientUid, documentId, triggerType,
                                                        cluster.getClusterId(), videoUrl);
                                                audioFile.delete();
                                                if (callback != null) callback.onComplete(true);
                                            }
                                        });
                            }

                            @Override
                            public void onError(String error) {
                                recordVideoGenerationFailure(patientUid, error, triggerType);
                                audioFile.delete();
                                if (callback != null) callback.onComplete(false);
                            }
                        });
            }

            @Override
            public void onError(String error) {
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
                recordVideoGenerationFailure(patientUid, error, triggerType);
                if (callback != null) callback.onComplete(false);
            }
        });
    }

    private void updateVideoUrl(String documentId, String newVideoUrl) {
        firestore.collection("memory_videos").document(documentId).update("videoUrl", newVideoUrl);
    }

    private void completeVideoGeneration(String patientUid, String documentId, String triggerType,
                                         String clusterId, String videoUrl) {
        recordVideoGeneration(patientUid, documentId, triggerType, clusterId);
        incrementDailyCount();
        markClusterAsGenerated(clusterId);
    }

    private void scheduleDailyVideoGeneration(String patientUid) {
        Calendar targetTime = Calendar.getInstance();
        targetTime.set(Calendar.HOUR_OF_DAY, VideoConfiguration.DAILY_GENERATION_HOUR);
        targetTime.set(Calendar.MINUTE, 0);
        targetTime.set(Calendar.SECOND, 0);
        targetTime.set(Calendar.MILLISECOND, 0);

        if (Calendar.getInstance().after(targetTime)) {
            targetTime.add(Calendar.DAY_OF_MONTH, 1);
        }

        long initialDelay = targetTime.getTimeInMillis() - System.currentTimeMillis();

        Data inputData = new Data.Builder().putString("patient_uid", patientUid).build();
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build();

        PeriodicWorkRequest dailyWork = new PeriodicWorkRequest.Builder(
                DailyVideoWorker.class, 24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .setInputData(inputData)
                .addTag(WORK_TAG_DAILY)
                .build();

        workManager.enqueueUniquePeriodicWork(
                WORK_TAG_DAILY + "_" + patientUid,
                ExistingPeriodicWorkPolicy.REPLACE,
                dailyWork
        );

        Log.d(TAG, "✓ Daily generation scheduled");
    }

    public void cleanupAllVideos(String patientUid) {
        cleanupAllVideosWithCallback(patientUid, null);
    }

    private void cleanupAllVideosWithCallback(String patientUid, Runnable onComplete) {
        Log.d(TAG, "DELETING ALL VIDEOS FOR PATIENT");

        firestore.collection("memory_videos")
                .whereEqualTo("patientUid", patientUid)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    int totalVideos = querySnapshot.size();

                    if (totalVideos == 0) {
                        if (onComplete != null) onComplete.run();
                        return;
                    }

                    final int[] deletedCount = {0};

                    for (com.google.firebase.firestore.QueryDocumentSnapshot doc : querySnapshot) {
                        String videoUrl = doc.getString("videoUrl");

                        if (videoUrl != null && !videoUrl.isEmpty()) {
                            try {
                                com.google.firebase.storage.FirebaseStorage.getInstance()
                                        .getReferenceFromUrl(videoUrl)
                                        .delete();
                            } catch (Exception e) {
                                Log.w(TAG, "Storage delete failed: " + e.getMessage());
                            }
                        }

                        doc.getReference().delete()
                                .addOnSuccessListener(aVoid -> {
                                    deletedCount[0]++;
                                    if (deletedCount[0] >= totalVideos) {
                                        Log.d(TAG, "✓ ALL VIDEOS DELETED");
                                        if (onComplete != null) onComplete.run();
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    deletedCount[0]++;
                                    if (deletedCount[0] >= totalVideos) {
                                        if (onComplete != null) onComplete.run();
                                    }
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Cleanup query failed: " + e.getMessage());
                    if (onComplete != null) onComplete.run();
                });
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
    }

    public void stopForPatient(String patientUid) {
        workManager.cancelAllWorkByTag(WORK_TAG_DAILY + "_" + patientUid);
        ttsGenerator.release();
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
                return Result.failure();
            }

            try {
                AutomaticVideoService service = new AutomaticVideoService(getApplicationContext());
                service.resetDailyCount();
                service.clearGeneratedClusters();
                service.generateMultipleVideosForPatient(patientUid, 10, "daily_midnight");
                return Result.success();
            } catch (Exception e) {
                Log.e(TAG, "Daily worker failed", e);
                return Result.failure();
            }
        }
    }
}