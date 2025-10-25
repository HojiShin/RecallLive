//package com.example.recalllive;
//
//import android.content.Context;
//import android.net.Uri;
//import android.util.Log;
//
//import androidx.work.Constraints;
//import androidx.work.Data;
//import androidx.work.ExistingWorkPolicy;
//import androidx.work.NetworkType;
//import androidx.work.OneTimeWorkRequest;
//import androidx.work.WorkManager;
//
//import com.google.android.gms.tasks.Task;
//import com.google.android.gms.tasks.Tasks;
//import com.google.firebase.auth.FirebaseAuth;
//import com.google.firebase.storage.FirebaseStorage;
//import com.google.firebase.storage.StorageReference;
//import com.google.firebase.storage.UploadTask;
//
//import java.util.ArrayList;
//import java.util.Collections;
//import java.util.List;
//import java.util.UUID;
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
//
///**
// * Orchestrates creating a memory: selects keyframes, generates story, saves to Firestore,
// * and schedules the background video synthesis worker.
// */
//public class MemoryCreationService {
//    private static final String TAG = "MemoryCreationService";
//    private final Context context;
//    private final GeminiStoryGenerator storyGenerator;
//    private final MemoryRepository memoryRepository;
//    private final FirebaseStorage storage;
//    private final ExecutorService executor;
//
//    public interface MemoryCreationCallback {
//        void onSuccess(String memoryId);
//        void onFailure(String error);
//    }
//
//    public MemoryCreationService(Context context, String geminiApiKey) {
//        this.context = context;
//        this.storyGenerator = new GeminiStoryGenerator(context, geminiApiKey);
//        this.memoryRepository = MemoryRepository.getInstance();
//        this.storage = FirebaseStorage.getInstance();
//        this.executor = Executors.newFixedThreadPool(4);
//    }
//
//    public void createMemoryFromCluster(PhotoClusteringManager.PhotoCluster cluster, String userId, MemoryCreationCallback callback) {
//        // 1. Select Keyframes (3-4 representative photos)
//        List<PhotoData> keyframes = selectKeyframes(cluster.getPhotos());
//        if (keyframes.isEmpty()) {
//            callback.onFailure("Not enough photos to select keyframes.");
//            return;
//        }
//
//        // 2. Upload Keyframes to Firebase Storage
//        uploadKeyframes(keyframes, userId, new OnImagesUploadedCallback() {
//            @Override
//            public void onSuccess(List<String> downloadUrls) {
//                // 3. Generate Story with Gemini
//                storyGenerator.generateStory(downloadUrls, cluster, new GeminiStoryGenerator.StoryCallback() {
//                    @Override
//                    public void onSuccess(GeminiStoryGenerator.StoryResult result) {
//                        // 4. Create Firestore Document
//                        Memory memory = new Memory.Builder(userId, result.title, result.script)
//                                .withKeywords(result.keywords)
//                                .withKeyImageUrls(downloadUrls)
//                                .withClusterId(cluster.getClusterId())
//                                .build();
//
//                        memoryRepository.saveMemory(memory, new MemoryRepository.OnMemorySavedCallback() {
//                            @Override
//                            public void onSuccess(String documentId) {
//                                // 5. Schedule Background Video Synthesis
//                                scheduleVideoSynthesis(documentId, cluster, userId);
//                                callback.onSuccess(documentId);
//                            }
//
//                            @Override
//                            public void onFailure(String error) {
//                                callback.onFailure("Failed to save memory: " + error);
//                            }
//                        });
//                    }
//
//                    @Override
//                    public void onFailure(String error) {
//                        callback.onFailure("AI story generation failed: " + error);
//                    }
//                });
//            }
//
//            @Override
//            public void onFailure(String error) {
//                callback.onFailure("Failed to upload keyframes: " + error);
//            }
//        });
//    }
//
//    private List<PhotoData> selectKeyframes(List<PhotoData> photos) {
//        if (photos.size() <= 4) {
//            return new ArrayList<>(photos);
//        }
//        // Simple selection: first, last, and a couple from the middle
//        List<PhotoData> selected = new ArrayList<>();
//        selected.add(photos.get(0));
//        selected.add(photos.get(photos.size() / 2));
//        selected.add(photos.get(photos.size() - 1));
//        return selected;
//    }
//
//    private void uploadKeyframes(List<PhotoData> keyframes, String userId, OnImagesUploadedCallback callback) {
//        List<Task<Uri>> uploadTasks = new ArrayList<>();
//        StorageReference storageRef = storage.getReference().child("images/" + userId);
//
//        for (PhotoData photo : keyframes) {
//            Uri fileUri = Uri.parse(photo.getPhotoUri());
//            StorageReference imageRef = storageRef.child(System.currentTimeMillis() + "_" + fileUri.getLastPathSegment());
//            UploadTask uploadTask = imageRef.putFile(fileUri);
//
//            Task<Uri> urlTask = uploadTask.continueWithTask(task -> {
//                if (!task.isSuccessful()) {
//                    throw task.getException();
//                }
//                return imageRef.getDownloadUrl();
//            });
//            uploadTasks.add(urlTask);
//        }
//
//        Tasks.whenAllSuccess(uploadTasks).addOnSuccessListener(executor, results -> {
//            List<String> downloadUrls = new ArrayList<>();
//            for (Object result : results) {
//                downloadUrls.add(result.toString());
//            }
//            callback.onSuccess(downloadUrls);
//        }).addOnFailureListener(executor, e -> {
//            callback.onFailure(e.getMessage());
//        });
//    }
//
//    private void scheduleVideoSynthesis(String memoryId, PhotoClusteringManager.PhotoCluster cluster, String userId) {
//        List<String> allPhotoUris = new ArrayList<>();
//        for (PhotoData photo : cluster.getPhotos()) {
//            allPhotoUris.add(photo.getPhotoUri());
//        }
//
//        Data inputData = new Data.Builder()
//                .putString(VideoSynthesisWorker.KEY_MEMORY_ID, memoryId)
//                .putString(VideoSynthesisWorker.KEY_USER_ID, userId)
//                .putStringArray(VideoSynthesisWorker.KEY_ALL_IMAGE_URIS, allPhotoUris.toArray(new String[0]))
//                .build();
//
//        Constraints constraints = new Constraints.Builder()
//                .setRequiredNetworkType(NetworkType.CONNECTED)
//                .setRequiresStorageNotLow(true)
//                .build();
//
//        OneTimeWorkRequest videoWorkRequest = new OneTimeWorkRequest.Builder(VideoSynthesisWorker.class)
//                .setInputData(inputData)
//                .setConstraints(constraints)
//                .addTag("video_synthesis")
//                .build();
//
//        WorkManager.getInstance(context).enqueueUniqueWork(
//                "synthesis_" + memoryId,
//                ExistingWorkPolicy.REPLACE,
//                videoWorkRequest
//        );
//
//        Log.d(TAG, "Scheduled video synthesis worker for memory ID: " + memoryId);
//    }
//
//    private interface OnImagesUploadedCallback {
//        void onSuccess(List<String> downloadUrls);
//        void onFailure(String error);
//    }
//}