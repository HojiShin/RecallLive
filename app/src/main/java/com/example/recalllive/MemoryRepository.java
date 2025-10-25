package com.example.recalllive;

import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.recalllive.Memory;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Repository for managing Memory documents in Firestore
 * Follows MVVM architecture pattern
 */
public class MemoryRepository {
    private static final String TAG = "MemoryRepository";
    private static final String COLLECTION_MEMORIES = "memories";
    private static final String STORAGE_FOLDER_IMAGES = "memory_images";
    private static final String STORAGE_FOLDER_VIDEOS = "memory_videos";

    private final FirebaseFirestore firestore;
    private final FirebaseAuth auth;
    private final FirebaseStorage storage;
    private final CollectionReference memoriesCollection;
    private final ExecutorService executor;

    // Singleton instance
    private static MemoryRepository instance;

    // Repository callback interfaces
    public interface OnMemorySavedCallback {
        void onSuccess(String documentId);
        void onFailure(String error);
    }

    public interface OnMemoryLoadedCallback {
        void onSuccess(Memory memory);
        void onFailure(String error);
    }

    public interface OnMemoriesLoadedCallback {
        void onSuccess(List<Memory> memories);
        void onFailure(String error);
    }

    public interface OnMemoryDeletedCallback {
        void onSuccess();
        void onFailure(String error);
    }

    public interface OnImageUploadCallback {
        void onSuccess(String downloadUrl);
        void onProgress(int progress);
        void onFailure(String error);
    }

    // Private constructor for singleton
    private MemoryRepository() {
        this.firestore = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
        this.storage = FirebaseStorage.getInstance();
        this.memoriesCollection = firestore.collection(COLLECTION_MEMORIES);
        this.executor = Executors.newSingleThreadExecutor();
    }

    // Singleton getInstance
    public static synchronized MemoryRepository getInstance() {
        if (instance == null) {
            instance = new MemoryRepository();
        }
        return instance;
    }

    /**
     * Get current user's UID
     */
    private String getCurrentUserId() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    /**
     * Save a new memory to Firestore
     */
    public void saveMemory(Memory memory, OnMemorySavedCallback callback) {
        String userId = getCurrentUserId();
        if (userId == null) {
            if (callback != null) {
                callback.onFailure("User not authenticated");
            }
            return;
        }

        // Set the userId
        memory.setUserId(userId);

        // Add to Firestore
        memoriesCollection.add(memory)
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "Memory saved with ID: " + documentReference.getId());
                    if (callback != null) {
                        callback.onSuccess(documentReference.getId());
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error saving memory", e);
                    if (callback != null) {
                        callback.onFailure(e.getMessage());
                    }
                });
    }

    /**
     * Upload images to Firebase Storage and save memory with URLs
     */
    public void saveMemoryWithImages(Memory memory, List<String> localImageUris,
                                     OnMemorySavedCallback callback) {
        String userId = getCurrentUserId();
        if (userId == null) {
            if (callback != null) {
                callback.onFailure("User not authenticated");
            }
            return;
        }

        memory.setUserId(userId);

        // Upload images first
        uploadImagesToStorage(localImageUris, new OnImagesUploadedCallback() {
            @Override
            public void onSuccess(List<String> downloadUrls) {
                // Set the Firebase Storage URLs in the memory
                memory.setKeyImageUrls(downloadUrls);

                // Now save the memory
                saveMemory(memory, callback);
            }

            @Override
            public void onFailure(String error) {
                if (callback != null) {
                    callback.onFailure("Failed to upload images: " + error);
                }
            }
        });
    }

    /**
     * Upload multiple images to Firebase Storage
     */
    private void uploadImagesToStorage(List<String> localUris, OnImagesUploadedCallback callback) {
        String userId = getCurrentUserId();
        if (userId == null) {
            callback.onFailure("User not authenticated");
            return;
        }

        List<Task<Uri>> uploadTasks = new ArrayList<>();

        for (int i = 0; i < localUris.size(); i++) {
            String localUri = localUris.get(i);
            String fileName = userId + "_" + System.currentTimeMillis() + "_" + i + ".jpg";
            StorageReference imageRef = storage.getReference()
                    .child(STORAGE_FOLDER_IMAGES)
                    .child(userId)
                    .child(fileName);

            Uri fileUri = Uri.parse(localUri);
            UploadTask uploadTask = imageRef.putFile(fileUri);

            // Get download URL after upload
            Task<Uri> urlTask = uploadTask.continueWithTask(task -> {
                if (!task.isSuccessful()) {
                    throw task.getException();
                }
                return imageRef.getDownloadUrl();
            });

            uploadTasks.add(urlTask);
        }

        // Wait for all uploads to complete
        Tasks.whenAllSuccess(uploadTasks)
                .addOnSuccessListener(results -> {
                    List<String> downloadUrls = new ArrayList<>();
                    for (Object result : results) {
                        downloadUrls.add(result.toString());
                    }
                    callback.onSuccess(downloadUrls);
                })
                .addOnFailureListener(e -> {
                    callback.onFailure(e.getMessage());
                });
    }

    /**
     * Get a single memory by document ID
     */
    public void getMemoryById(String documentId, OnMemoryLoadedCallback callback) {
        String userId = getCurrentUserId();
        if (userId == null) {
            if (callback != null) {
                callback.onFailure("User not authenticated");
            }
            return;
        }

        memoriesCollection.document(documentId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Memory memory = documentSnapshot.toObject(Memory.class);
                        if (memory != null && userId.equals(memory.getUserId())) {
                            if (callback != null) {
                                callback.onSuccess(memory);
                            }
                        } else {
                            if (callback != null) {
                                callback.onFailure("Unauthorized access");
                            }
                        }
                    } else {
                        if (callback != null) {
                            callback.onFailure("Memory not found");
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    if (callback != null) {
                        callback.onFailure(e.getMessage());
                    }
                });
    }

    /**
     * Get all memories for current user
     */
    public void getAllUserMemories(OnMemoriesLoadedCallback callback) {
        String userId = getCurrentUserId();
        if (userId == null) {
            if (callback != null) {
                callback.onFailure("User not authenticated");
            }
            return;
        }

        memoriesCollection
                .whereEqualTo("userId", userId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Memory> memories = new ArrayList<>();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        Memory memory = document.toObject(Memory.class);
                        memories.add(memory);
                    }
                    if (callback != null) {
                        callback.onSuccess(memories);
                    }
                })
                .addOnFailureListener(e -> {
                    if (callback != null) {
                        callback.onFailure(e.getMessage());
                    }
                });
    }

    /**
     * Get memories as LiveData for observation in ViewModels
     */
    public LiveData<List<Memory>> getUserMemoriesLiveData() {
        MutableLiveData<List<Memory>> memoriesLiveData = new MutableLiveData<>();
        String userId = getCurrentUserId();

        if (userId == null) {
            memoriesLiveData.setValue(new ArrayList<>());
            return memoriesLiveData;
        }

        memoriesCollection
                .whereEqualTo("userId", userId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Listen failed.", error);
                        memoriesLiveData.setValue(new ArrayList<>());
                        return;
                    }

                    List<Memory> memories = new ArrayList<>();
                    if (value != null) {
                        for (QueryDocumentSnapshot doc : value) {
                            Memory memory = doc.toObject(Memory.class);
                            memories.add(memory);
                        }
                    }
                    memoriesLiveData.setValue(memories);
                });

        return memoriesLiveData;
    }

    /**
     * Update an existing memory
     */
    public void updateMemory(String documentId, Memory updatedMemory,
                             OnMemorySavedCallback callback) {
        String userId = getCurrentUserId();
        if (userId == null) {
            if (callback != null) {
                callback.onFailure("User not authenticated");
            }
            return;
        }

        // Ensure userId remains unchanged
        updatedMemory.setUserId(userId);

        memoriesCollection.document(documentId)
                .set(updatedMemory)
                .addOnSuccessListener(aVoid -> {
                    if (callback != null) {
                        callback.onSuccess(documentId);
                    }
                })
                .addOnFailureListener(e -> {
                    if (callback != null) {
                        callback.onFailure(e.getMessage());
                    }
                });
    }

    /**
     * Update video URL for a memory
     */
    public void updateVideoUrl(String documentId, String videoUrl,
                               OnMemorySavedCallback callback) {
        memoriesCollection.document(documentId)
                .update("videoUrl", videoUrl, "isProcessed", true)
                .addOnSuccessListener(aVoid -> {
                    if (callback != null) {
                        callback.onSuccess(documentId);
                    }
                })
                .addOnFailureListener(e -> {
                    if (callback != null) {
                        callback.onFailure(e.getMessage());
                    }
                });
    }

    /**
     * Delete a memory
     */
    public void deleteMemory(String documentId, OnMemoryDeletedCallback callback) {
        memoriesCollection.document(documentId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    if (callback != null) {
                        callback.onSuccess();
                    }
                })
                .addOnFailureListener(e -> {
                    if (callback != null) {
                        callback.onFailure(e.getMessage());
                    }
                });
    }

    /**
     * Get memories by cluster ID
     */
    public void getMemoriesByClusterId(String clusterId, OnMemoriesLoadedCallback callback) {
        String userId = getCurrentUserId();
        if (userId == null) {
            if (callback != null) {
                callback.onFailure("User not authenticated");
            }
            return;
        }

        memoriesCollection
                .whereEqualTo("userId", userId)
                .whereEqualTo("clusterId", clusterId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Memory> memories = new ArrayList<>();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        Memory memory = document.toObject(Memory.class);
                        memories.add(memory);
                    }
                    if (callback != null) {
                        callback.onSuccess(memories);
                    }
                })
                .addOnFailureListener(e -> {
                    if (callback != null) {
                        callback.onFailure(e.getMessage());
                    }
                });
    }

    // Helper callback for multiple image uploads
    private interface OnImagesUploadedCallback {
        void onSuccess(List<String> downloadUrls);
        void onFailure(String error);
    }
}