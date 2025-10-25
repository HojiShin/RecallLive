//package com.example.recalllive;
//
//import android.content.Context;
//import android.util.Log;
//
//import androidx.work.BackoffPolicy;
//import androidx.work.Constraints;
//import androidx.work.Data;
//import androidx.work.ExistingWorkPolicy;
//import androidx.work.NetworkType;
//import androidx.work.OneTimeWorkRequest;
//import androidx.work.WorkInfo;
//import androidx.work.WorkManager;
//
//import com.example.recalllive.Memory;
//import com.example.recalllive.VideoSynthesisWorker;
//import com.google.common.util.concurrent.ListenableFuture;
//
//import java.util.List;
//import java.util.UUID;
//import java.util.concurrent.TimeUnit;
//
///**
// * Manager class for video synthesis operations
// * Handles WorkManager scheduling and monitoring
// */
//public class VideoSynthesisManager {
//    private static final String TAG = "VideoSynthesisManager";
//    private static final String WORK_TAG_PREFIX = "video_synthesis_";
//
//    private final Context context;
//    private final WorkManager workManager;
//
//    // Callback interfaces
//    public interface VideoSynthesisCallback {
//        void onSynthesisStarted(String workId);
//        void onSynthesisProgress(int progress);
//        void onSynthesisCompleted(String videoUrl);
//        void onSynthesisFailed(String error);
//    }
//
//    public VideoSynthesisManager(Context context) {
//        this.context = context;
//        this.workManager = WorkManager.getInstance(context);
//    }
//
//    /**
//     * Start video synthesis for a memory
//     */
//    public String startVideoSynthesis(Memory memory, VideoSynthesisCallback callback) {
//        if (memory == null || memory.getKeyImageUrls() == null ||
//                memory.getKeyImageUrls().isEmpty()) {
//            if (callback != null) {
//                callback.onSynthesisFailed("Invalid memory data");
//            }
//            return null;
//        }
//
//        // Prepare input data
//        Data.Builder inputDataBuilder = new Data.Builder()
//                .putString(VideoSynthesisWorker.KEY_MEMORY_ID, memory.getDocumentId())
//                .putStringArray(VideoSynthesisWorker.KEY_IMAGE_URLS,
//                        memory.getKeyImageUrls().toArray(new String[0]))
//                .putString(VideoSynthesisWorker.KEY_NARRATION_TEXT, memory.getScript());
//
//        // Add narration audio URL if available
//        // .putString(VideoSynthesisWorker.KEY_NARRATION_URL, narrationUrl);
//
//        Data inputData = inputDataBuilder.build();
//
//        // Create constraints - require network and battery not low
//        Constraints constraints = new Constraints.Builder()
//                .setRequiredNetworkType(NetworkType.CONNECTED)
//                .setRequiresBatteryNotLow(true)
//                .build();
//
//        // Create work request
//        String workTag = WORK_TAG_PREFIX + memory.getDocumentId();
//        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(VideoSynthesisWorker.class)
//                .setInputData(inputData)
//                .setConstraints(constraints)
//                .addTag(workTag)
//                .setBackoffCriteria(
//                        BackoffPolicy.EXPONENTIAL,
//                        OneTimeWorkRequest.MIN_BACKOFF_MILLIS,
//                        TimeUnit.MILLISECONDS)
//                .build();
//
//        // Enqueue work
//        workManager.enqueueUniqueWork(
//                workTag,
//                ExistingWorkPolicy.REPLACE,
//                workRequest);
//
//        String workId = workRequest.getId().toString();
//
//        // Monitor work progress
//        monitorWorkProgress(workRequest.getId(), callback);
//
//        if (callback != null) {
//            callback.onSynthesisStarted(workId);
//        }
//
//        return workId;
//    }
//
//    /**
//     * Monitor work progress
//     */
//    private void monitorWorkProgress(UUID workId, VideoSynthesisCallback callback) {
//        workManager.getWorkInfoByIdLiveData(workId).observeForever(workInfo -> {
//            if (workInfo != null) {
//                WorkInfo.State state = workInfo.getState();
//
//                Log.d(TAG, "Work state: " + state);
//
//                if (state == WorkInfo.State.RUNNING) {
//                    // Work is running - could extract progress from Data
//                    Data progress = workInfo.getProgress();
//                    int progressValue = progress.getInt("progress", 0);
//                    if (callback != null) {
//                        callback.onSynthesisProgress(progressValue);
//                    }
//
//                } else if (state == WorkInfo.State.SUCCEEDED) {
//                    // Work completed successfully
//                    Data outputData = workInfo.getOutputData();
//                    String videoUrl = outputData.getString("video_url");
//
//                    Log.d(TAG, "Video synthesis completed: " + videoUrl);
//
//                    if (callback != null) {
//                        callback.onSynthesisCompleted(videoUrl);
//                    }
//
//                } else if (state == WorkInfo.State.FAILED) {
//                    // Work failed
//                    Log.e(TAG, "Video synthesis failed");
//
//                    if (callback != null) {
//                        callback.onSynthesisFailed("Video synthesis failed");
//                    }
//                }
//            }
//        });
//    }
//
//    /**
//     * Cancel video synthesis
//     */
//    public void cancelVideoSynthesis(String memoryId) {
//        String workTag = WORK_TAG_PREFIX + memoryId;
//        workManager.cancelAllWorkByTag(workTag);
//    }
//
//    /**
//     * Check if video synthesis is running for a memory
//     */
//    public ListenableFuture<List<WorkInfo>> checkSynthesisStatus(String memoryId) {
//        String workTag = WORK_TAG_PREFIX + memoryId;
//        return workManager.getWorkInfosByTag(workTag);
//    }
//
//    /**
//     * Cancel all video synthesis work
//     */
//    public void cancelAllVideoSynthesis() {
//        workManager.cancelAllWorkByTag(WORK_TAG_PREFIX);
//    }
//
//    /**
//     * Get work info by ID
//     */
//    public ListenableFuture<WorkInfo> getWorkInfo(String workId) {
//        return workManager.getWorkInfoById(UUID.fromString(workId));
//    }
//}