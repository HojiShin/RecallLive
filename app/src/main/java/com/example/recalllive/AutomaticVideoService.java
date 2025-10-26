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

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

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
     * Initialize for patient
     * @param isSignupOrLogin: true if signup/login (generate 10 videos), false if background (generate 1)
     */
    public void initializeForPatient(String patientUid, boolean isSignupOrLogin) {
        Log.d(TAG, "╔═══════════════════════════════════════════╗");
        Log.d(TAG, "  INITIALIZING VIDEO SERVICE");
        Log.d(TAG, "  Patient: " + patientUid);
        Log.d(TAG, "  Trigger: " + (isSignupOrLogin ? "SIGNUP/LOGIN" : "BACKGROUND"));
        Log.d(TAG, "  TTS Enabled: " + ENABLE_TTS);
        Log.d(TAG, "  Max videos/day: " + MAX_VIDEOS_PER_DAY);
        Log.d(TAG, "  Auto cleanup enabled: " + ENABLE_AUTO_CLEANUP);
        Log.d(TAG, "╚═══════════════════════════════════════════╝");

        // Check if new day
        String today = getTodayDateString();
        String lastDate = getLastVideoDate();

        if (!today.equals(lastDate)) {
            Log.d(TAG, "🔄 New day detected - resetting counters");
            resetDailyCount();
            clearGeneratedClusters();
        }

        // CRITICAL: On login/signup, delete ALL videos and generate fresh ones
        if (isSignupOrLogin) {
            Log.d(TAG, "╔═══════════════════════════════════════════╗");
            Log.d(TAG, "  🧹 FORCED CLEANUP ON LOGIN/SIGNUP");
            Log.d(TAG, "  Deleting ALL existing videos");
            Log.d(TAG, "╚═══════════════════════════════════════════╝");
            Log.d(TAG, "🔄 Resetting cluster tracking for fresh generation...");
            clearGeneratedClusters(); // Clear before cleanup to start fresh

            // Delete ALL videos for this patient (not just old ones)
            cleanupAllVideosWithCallback(patientUid, () -> {
                Log.d(TAG, "✓ Cleanup callback completed, proceeding to verification...");
                // After cleanup completes, continue with verification
                verifyAndSyncVideoCount(patientUid, isSignupOrLogin);
            });
        } else {
            // For background tasks, only cleanup if enabled
            if (ENABLE_AUTO_CLEANUP) {
                Log.d(TAG, "🧹 Background cleanup enabled - deleting old videos only");
                cleanupOldVideosWithCallback(patientUid, () -> {
                    verifyAndSyncVideoCount(patientUid, isSignupOrLogin);
                });
            } else {
                Log.d(TAG, "⏭️ Skipping cleanup for background task");
                verifyAndSyncVideoCount(patientUid, isSignupOrLogin);
            }
        }
    }

    private void verifyAndSyncVideoCount(String patientUid, boolean isSignupOrLogin) {
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

                    Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                    Log.d(TAG, "VIDEO COUNT VERIFICATION");
                    Log.d(TAG, "Stored count: " + storedCount);
                    Log.d(TAG, "Actual Firestore count: " + actualCount);
                    Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

                    if (actualCount != storedCount) {
                        Log.d(TAG, "⚠️ COUNT MISMATCH - Syncing to actual: " + actualCount);
                        prefs.edit()
                                .putString(KEY_LAST_VIDEO_DATE, today)
                                .putInt(KEY_DAILY_VIDEO_COUNT, actualCount)
                                .apply();
                    }

                    continueInitialization(patientUid, isSignupOrLogin, actualCount);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to verify video count: " + e.getMessage());
                    Log.d(TAG, "⚠️ Resetting count to 0 due to verification failure");

                    prefs.edit()
                            .putString(KEY_LAST_VIDEO_DATE, today)
                            .putInt(KEY_DAILY_VIDEO_COUNT, 0)
                            .apply();

                    continueInitialization(patientUid, isSignupOrLogin, 0);
                });
    }

    private void continueInitialization(String patientUid, boolean isSignupOrLogin, int verifiedCount) {
        Log.d(TAG, "📊 Continuing with verified count: " + verifiedCount + "/" + MAX_VIDEOS_PER_DAY);

        if (isSignupOrLogin) {
            // SIGNUP/LOGIN: Always generate 10 videos (cleanup already happened)
            Log.d(TAG, "🎬 SIGNUP/LOGIN: Generating 10 videos");
            generateMultipleVideosForPatient(patientUid, 10, "signup_login");
        } else {
            // BACKGROUND: Check limit first
            if (verifiedCount >= MAX_VIDEOS_PER_DAY) {
                Log.d(TAG, "⚠️ Daily video limit reached (" + verifiedCount + "/" + MAX_VIDEOS_PER_DAY + ")");
                Log.d(TAG, "Scheduling daily generation for tomorrow");
                scheduleDailyVideoGeneration(patientUid);
                return;
            }

            // BACKGROUND: Generate 1 video only
            Log.d(TAG, "🎬 BACKGROUND: Generating 1 video");
            generateMultipleVideosForPatient(patientUid, 1, "background");
        }

        scheduleDailyVideoGeneration(patientUid);
    }

    /**
     * CRITICAL METHOD: Generate MULTIPLE videos at once
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

                List<PhotoClusteringManager.PhotoCluster> availableClusters = getAvailableClusters(geocodedClusters);

                if (availableClusters.isEmpty()) {
                    Log.w(TAG, "⚠️ No available clusters - resetting and using all");
                    clearGeneratedClusters();
                    availableClusters = new ArrayList<>(geocodedClusters);
                }

                int videosToCreate = Math.min(videosToGenerate, availableClusters.size());
                Log.d(TAG, "Creating " + videosToCreate + " videos from " + availableClusters.size() + " available clusters");

                // CRITICAL: Track clusters used in THIS generation session to prevent duplicates
                Set<String> usedInThisSession = new HashSet<>();

                AtomicInteger completedVideos = new AtomicInteger(0);
                AtomicInteger failedVideos = new AtomicInteger(0);

                // Generate videos sequentially
                for (int i = 0; i < videosToCreate; i++) {
                    // Select cluster that hasn't been used in this session
                    PhotoClusteringManager.PhotoCluster selectedCluster = selectUniqueCluster(availableClusters, usedInThisSession);

                    if (selectedCluster == null) {
                        Log.w(TAG, "⚠️ No more unique clusters available");
                        break;
                    }

                    // Mark as used in this session
                    usedInThisSession.add(selectedCluster.getClusterId());
                    availableClusters.remove(selectedCluster);

                    final int videoNumber = i + 1;
                    Log.d(TAG, "┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓");
                    Log.d(TAG, "🎥 VIDEO " + videoNumber + "/" + videosToCreate);
                    Log.d(TAG, "Cluster: " + selectedCluster.getClusterId());
                    Log.d(TAG, "Location: " + selectedCluster.getLocationName());
                    Log.d(TAG, "Photos: " + selectedCluster.getPhotoCount());
                    Log.d(TAG, "┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛");

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

                    // Small delay between videos
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

        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        Log.d(TAG, "CLUSTER AVAILABILITY CHECK");
        Log.d(TAG, "Total clusters: " + allClusters.size());
        Log.d(TAG, "Previously used clusters: " + usedClusters.size());

        for (PhotoClusteringManager.PhotoCluster cluster : allClusters) {
            if (cluster.getPhotoCount() >= 1 && !usedClusters.contains(cluster.getClusterId())) {
                available.add(cluster);
            }
        }

        Log.d(TAG, "Available clusters: " + available.size());
        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        return available;
    }

    private PhotoClusteringManager.PhotoCluster selectRandomCluster(
            List<PhotoClusteringManager.PhotoCluster> clusters) {
        if (clusters.isEmpty()) return null;

        clusters.sort((a, b) -> b.getPhotoCount() - a.getPhotoCount());

        Random random = new Random();
        int topChoices = Math.min(5, clusters.size());
        return clusters.get(random.nextInt(topChoices));
    }

    /**
     * Select a unique cluster that hasn't been used in this generation session
     */
    private PhotoClusteringManager.PhotoCluster selectUniqueCluster(
            List<PhotoClusteringManager.PhotoCluster> clusters, Set<String> usedInSession) {
        if (clusters.isEmpty()) return null;

        // Filter out clusters already used in this session
        List<PhotoClusteringManager.PhotoCluster> uniqueClusters = new ArrayList<>();
        for (PhotoClusteringManager.PhotoCluster cluster : clusters) {
            if (!usedInSession.contains(cluster.getClusterId())) {
                uniqueClusters.add(cluster);
            }
        }

        if (uniqueClusters.isEmpty()) {
            Log.w(TAG, "⚠️ All clusters already used in this session");
            return null;
        }

        // Sort by photo count (prefer clusters with more photos)
        uniqueClusters.sort((a, b) -> b.getPhotoCount() - a.getPhotoCount());

        // Select randomly from top 5 clusters
        Random random = new Random();
        int topChoices = Math.min(5, uniqueClusters.size());
        PhotoClusteringManager.PhotoCluster selected = uniqueClusters.get(random.nextInt(topChoices));

        Log.d(TAG, "Selected unique cluster: " + selected.getClusterId() +
                " (not in session set of " + usedInSession.size() + " used clusters)");

        return selected;
    }

    private void generateVideoWithTTS(PhotoClusteringManager.PhotoCluster cluster, String patientUid,
                                      String triggerType, VideoCompletionCallback callback) {
        Log.d(TAG, "═══════════════════════════════════════════");
        Log.d(TAG, "GENERATING VIDEO WITH TTS");
        Log.d(TAG, "═══════════════════════════════════════════");

        ttsGenerator.generateCompleteNarration(cluster, new TTSVideoGenerator.TTSGenerationCallback() {
            @Override
            public void onAudioGenerated(String audioFilePath, int durationSeconds) {
                final File audioFile = new File(audioFilePath);
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
                                                Log.e(TAG, "❌ Merge failed: " + error);
                                                completeVideoGeneration(patientUid, documentId, triggerType,
                                                        cluster.getClusterId(), videoUrl);
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
        Log.d(TAG, "═══════════════════════════════════════════");
        Log.d(TAG, "GENERATING SILENT VIDEO");
        Log.d(TAG, "═══════════════════════════════════════════");

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

    private void completeVideoGeneration(String patientUid, String documentId, String triggerType,
                                         String clusterId, String videoUrl) {
        recordVideoGeneration(patientUid, documentId, triggerType, clusterId);
        incrementDailyCount();
        markClusterAsGenerated(clusterId);

        int currentCount = getTodayVideoCount();
        Log.d(TAG, "✓ Video complete (" + currentCount + "/" + MAX_VIDEOS_PER_DAY + ")");
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

        Log.d(TAG, "✓ Daily generation scheduled for: " + targetTime.getTime());
    }

    /**
     * Public method to cleanup old videos (keeps today's videos)
     */
    public void cleanupOldVideos(String patientUid) {
        cleanupOldVideosWithCallback(patientUid, null);
    }

    /**
     * Public method to cleanup ALL videos
     */
    public void cleanupAllVideos(String patientUid) {
        cleanupAllVideosWithCallback(patientUid, null);
    }

    /**
     * Delete ALL videos for this patient (used on login/signup)
     */
    private void cleanupAllVideosWithCallback(String patientUid, Runnable onComplete) {
        Log.d(TAG, "═══════════════════════════════════════════");
        Log.d(TAG, "DELETING ALL VIDEOS FOR PATIENT");
        Log.d(TAG, "═══════════════════════════════════════════");

        Log.d(TAG, "Querying all videos for patient: " + patientUid);

        firestore.collection("memory_videos")
                .whereEqualTo("patientUid", patientUid)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    int totalVideos = querySnapshot.size();

                    Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                    Log.d(TAG, "CLEANUP ALL - QUERY RESULTS");
                    Log.d(TAG, "Total videos found: " + totalVideos);
                    Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

                    if (totalVideos == 0) {
                        Log.d(TAG, "✓ No videos to clean");
                        if (onComplete != null) {
                            Log.d(TAG, "✓ Calling completion callback");
                            onComplete.run();
                        }
                        return;
                    }

                    final int[] deletedSoFar = {0};

                    // Delete ALL videos
                    for (com.google.firebase.firestore.QueryDocumentSnapshot doc : querySnapshot) {
                        String videoUrl = doc.getString("videoUrl");
                        String docId = doc.getId();
                        com.google.firebase.Timestamp createdAt = doc.getTimestamp("createdAt");

                        Log.d(TAG, "🗑️ Deleting video: " + docId +
                                (createdAt != null ? " (created: " + createdAt.toDate() + ")" : ""));

                        if (videoUrl != null && !videoUrl.isEmpty()) {
                            try {
                                com.google.firebase.storage.FirebaseStorage.getInstance()
                                        .getReferenceFromUrl(videoUrl)
                                        .delete()
                                        .addOnSuccessListener(aVoid -> Log.d(TAG, "  ✓ Deleted storage: " + docId))
                                        .addOnFailureListener(e -> Log.w(TAG, "  ⚠️ Storage delete failed: " + docId + " - " + e.getMessage()));
                            } catch (Exception e) {
                                Log.w(TAG, "  ⚠️ Storage error for " + docId, e);
                            }
                        }

                        doc.getReference().delete()
                                .addOnSuccessListener(aVoid -> {
                                    Log.d(TAG, "  ✓ Deleted firestore: " + docId);
                                    deletedSoFar[0]++;
                                    Log.d(TAG, "  Progress: " + deletedSoFar[0] + "/" + totalVideos);

                                    if (deletedSoFar[0] >= totalVideos) {
                                        Log.d(TAG, "═══════════════════════════════════════════");
                                        Log.d(TAG, "✓ ALL VIDEOS DELETED: " + deletedSoFar[0] + " videos removed");
                                        Log.d(TAG, "═══════════════════════════════════════════");
                                        if (onComplete != null) {
                                            Log.d(TAG, "✓ Calling completion callback");
                                            onComplete.run();
                                        }
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "  ❌ Firestore delete failed: " + docId + " - " + e.getMessage());
                                    deletedSoFar[0]++;
                                    Log.d(TAG, "  Progress: " + deletedSoFar[0] + "/" + totalVideos);

                                    if (deletedSoFar[0] >= totalVideos) {
                                        Log.d(TAG, "═══════════════════════════════════════════");
                                        Log.d(TAG, "✓ CLEANUP COMPLETE (with errors): " + deletedSoFar[0] + " processed");
                                        Log.d(TAG, "═══════════════════════════════════════════");
                                        if (onComplete != null) {
                                            Log.d(TAG, "✓ Calling completion callback");
                                            onComplete.run();
                                        }
                                    }
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Cleanup query failed: " + e.getMessage(), e);
                    if (onComplete != null) {
                        Log.d(TAG, "⚠️ Calling completion callback despite error");
                        onComplete.run();
                    }
                });
    }

    /**
     * Delete only OLD videos (before today) - used by daily worker
     */
    private void cleanupOldVideosWithCallback(String patientUid, Runnable onComplete) {
        Log.d(TAG, "═══════════════════════════════════════════");
        Log.d(TAG, "CLEANING UP OLD VIDEOS (Keeping only today's)");
        Log.d(TAG, "═══════════════════════════════════════════");

        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        long todayStartTime = today.getTimeInMillis();

        Log.d(TAG, "Today's date: " + new Date(todayStartTime));
        Log.d(TAG, "Querying all videos for patient: " + patientUid);

        firestore.collection("memory_videos")
                .whereEqualTo("patientUid", patientUid)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                    Log.d(TAG, "QUERY RESULTS");
                    Log.d(TAG, "Total videos found: " + querySnapshot.size());

                    int todayCount = 0;
                    int oldCount = 0;
                    int totalToDelete = 0;

                    // First pass: count and log
                    for (com.google.firebase.firestore.QueryDocumentSnapshot doc : querySnapshot) {
                        com.google.firebase.Timestamp createdAt = doc.getTimestamp("createdAt");
                        if (createdAt != null) {
                            Date videoDate = createdAt.toDate();
                            boolean isOld = videoDate.getTime() < todayStartTime;

                            if (isOld) {
                                oldCount++;
                                totalToDelete++;
                                Log.d(TAG, "  📅 OLD: " + doc.getId() + " - " + videoDate);
                            } else {
                                todayCount++;
                                Log.d(TAG, "  📅 TODAY: " + doc.getId() + " - " + videoDate);
                            }
                        } else {
                            Log.w(TAG, "  ⚠️ NO DATE: " + doc.getId());
                        }
                    }

                    Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                    Log.d(TAG, "CLEANUP SUMMARY");
                    Log.d(TAG, "Videos from today: " + todayCount);
                    Log.d(TAG, "Old videos to delete: " + oldCount);
                    Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

                    if (totalToDelete == 0) {
                        Log.d(TAG, "✓ No old videos to clean");
                        if (onComplete != null) {
                            Log.d(TAG, "✓ Calling completion callback");
                            onComplete.run();
                        }
                        return;
                    }

                    final int[] deletedSoFar = {0};

                    // Second pass: delete old videos
                    for (com.google.firebase.firestore.QueryDocumentSnapshot doc : querySnapshot) {
                        com.google.firebase.Timestamp createdAt = doc.getTimestamp("createdAt");

                        if (createdAt != null && createdAt.toDate().getTime() < todayStartTime) {
                            String videoUrl = doc.getString("videoUrl");
                            String docId = doc.getId();

                            Log.d(TAG, "🗑️ Deleting old video: " + docId);

                            if (videoUrl != null && !videoUrl.isEmpty()) {
                                try {
                                    com.google.firebase.storage.FirebaseStorage.getInstance()
                                            .getReferenceFromUrl(videoUrl)
                                            .delete()
                                            .addOnSuccessListener(aVoid -> Log.d(TAG, "  ✓ Deleted storage: " + docId))
                                            .addOnFailureListener(e -> Log.w(TAG, "  ⚠️ Storage delete failed: " + docId + " - " + e.getMessage()));
                                } catch (Exception e) {
                                    Log.w(TAG, "  ⚠️ Storage error for " + docId, e);
                                }
                            }

                            final int expectedTotal = totalToDelete;
                            doc.getReference().delete()
                                    .addOnSuccessListener(aVoid -> {
                                        Log.d(TAG, "  ✓ Deleted firestore: " + docId);
                                        deletedSoFar[0]++;
                                        Log.d(TAG, "  Progress: " + deletedSoFar[0] + "/" + expectedTotal);

                                        if (deletedSoFar[0] >= expectedTotal) {
                                            Log.d(TAG, "═══════════════════════════════════════════");
                                            Log.d(TAG, "✓ CLEANUP COMPLETE: " + deletedSoFar[0] + " old videos deleted");
                                            Log.d(TAG, "═══════════════════════════════════════════");
                                            if (onComplete != null) {
                                                Log.d(TAG, "✓ Calling completion callback");
                                                onComplete.run();
                                            }
                                        }
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e(TAG, "  ❌ Firestore delete failed: " + docId + " - " + e.getMessage());
                                        deletedSoFar[0]++;
                                        Log.d(TAG, "  Progress: " + deletedSoFar[0] + "/" + expectedTotal);

                                        if (deletedSoFar[0] >= expectedTotal) {
                                            Log.d(TAG, "═══════════════════════════════════════════");
                                            Log.d(TAG, "✓ CLEANUP COMPLETE (with errors): " + deletedSoFar[0] + " processed");
                                            Log.d(TAG, "═══════════════════════════════════════════");
                                            if (onComplete != null) {
                                                Log.d(TAG, "✓ Calling completion callback");
                                                onComplete.run();
                                            }
                                        }
                                    });
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Cleanup query failed: " + e.getMessage(), e);
                    if (onComplete != null) {
                        Log.d(TAG, "⚠️ Calling completion callback despite error");
                        onComplete.run();
                    }
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
     * UPDATED: Daily worker now generates 10 new videos at 12am and cleans up old ones
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

                // Clean up ONLY yesterday's videos (keep nothing, since we're generating fresh)
                // Actually, at midnight we want to delete ALL old videos
                if (ENABLE_AUTO_CLEANUP) {
                    Log.d(TAG, "🧹 Cleaning up all old videos at midnight...");
                    service.cleanupOldVideos(patientUid);
                }

                // Generate 10 new videos for today
                Log.d(TAG, "🎬 Generating 10 videos at midnight");
                service.generateMultipleVideosForPatient(patientUid, 10, "daily_midnight");

                Log.d(TAG, "✓ DAILY WORKER COMPLETE");
                return Result.success();
            } catch (Exception e) {
                Log.e(TAG, "❌ Daily worker failed", e);
                return Result.failure();
            }
        }
    }
}