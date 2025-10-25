package com.example.recalllive;

import android.content.Context;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.List;
import java.util.concurrent.Executor;

public class PhotoProcessingService {
    private static final String TAG = "PhotoProcessingService";

    private final Context context;
    private final PhotoMetadataExtractor metadataExtractor;
    private final PhotoClusteringManager clusteringManager;
    private final PhotoDatabase database;
    private final FirebaseClusterManager firebaseClusterManager;
    private final Executor executor; // CHANGED: Use shared executor
    private String patientUid;

    public interface ProcessingCallback {
        void onProcessingStarted();
        void onProgressUpdate(int processed, int total);
        void onProcessingComplete(List<PhotoClusteringManager.PhotoCluster> clusters);
        void onProcessingError(String error);
    }

    public PhotoProcessingService(Context context) {
        this.context = context;
        this.metadataExtractor = new PhotoMetadataExtractor(context);
        this.clusteringManager = new PhotoClusteringManager();
        this.database = PhotoDatabase.getInstance(context);
        this.executor = AppExecutors.getInstance().diskIO(); // CHANGED: Use shared executor

        this.patientUid = getPatientUid();
        if (patientUid != null) {
            this.firebaseClusterManager = new FirebaseClusterManager(context, patientUid);
        } else {
            this.firebaseClusterManager = null;
            Log.e(TAG, "Patient UID not available");
        }
    }

    public PhotoProcessingService(Context context, String patientUid) {
        this.context = context;
        this.metadataExtractor = new PhotoMetadataExtractor(context);
        this.clusteringManager = new PhotoClusteringManager();
        this.database = PhotoDatabase.getInstance(context);
        this.executor = AppExecutors.getInstance().diskIO(); // CHANGED: Use shared executor
        this.patientUid = patientUid;
        this.firebaseClusterManager = new FirebaseClusterManager(context, patientUid);
    }

    private String getPatientUid() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            return currentUser.getUid();
        }

        return context.getSharedPreferences("RecallLive", Context.MODE_PRIVATE)
                .getString("patient_uid", null);
    }

    public void processAllPhotos(ProcessingCallback callback) {
        executor.execute(() -> {
            try {
                if (callback != null) {
                    // Post to main thread
                    AppExecutors.getInstance().mainThread().execute(callback::onProcessingStarted);
                }

                Log.d(TAG, "Extracting photo metadata...");
                List<PhotoData> photos = metadataExtractor.extractAllPhotos();
                Log.d(TAG, "Found " + photos.size() + " photos");

                if (photos.isEmpty()) {
                    if (callback != null) {
                        AppExecutors.getInstance().mainThread().execute(() ->
                                callback.onProcessingComplete(null));
                    }
                    return;
                }

                Log.d(TAG, "Clustering photos...");
                List<PhotoClusteringManager.PhotoCluster> clusters = clusteringManager.clusterPhotos(photos);
                Log.d(TAG, "Created " + clusters.size() + " clusters");

                Log.d(TAG, "Storing in local database...");
                storePhotosInDatabase(photos, callback);

                Log.d(TAG, "Storing clusters in Firebase...");
                if (firebaseClusterManager != null) {
                    storeClustersInFirebase(clusters, callback);
                } else {
                    Log.e(TAG, "Firebase cluster manager not initialized");
                }

                if (callback != null) {
                    List<PhotoClusteringManager.PhotoCluster> finalClusters = clusters;
                    AppExecutors.getInstance().mainThread().execute(() ->
                            callback.onProcessingComplete(finalClusters));
                }

            } catch (Exception e) {
                Log.e(TAG, "Error processing photos: " + e.getMessage());
                if (callback != null) {
                    String error = e.getMessage();
                    AppExecutors.getInstance().mainThread().execute(() ->
                            callback.onProcessingError(error));
                }
            }
        });
    }

    private void storeClustersInFirebase(List<PhotoClusteringManager.PhotoCluster> clusters,
                                         ProcessingCallback callback) {
        if (firebaseClusterManager == null) {
            Log.e(TAG, "Firebase cluster manager not initialized");
            return;
        }

        firebaseClusterManager.storeClusters(clusters,
                new FirebaseClusterManager.OnClusterStorageCallback() {
                    @Override
                    public void onSuccess(int clusterCount) {
                        Log.d(TAG, "Successfully stored " + clusterCount + " clusters in Firebase");
                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "Failed to store clusters in Firebase: " + error);
                    }
                });
    }

    private void storePhotosInDatabase(List<PhotoData> photos, ProcessingCallback callback) {
        int total = photos.size();
        int batchSize = 50;

        for (int i = 0; i < total; i += batchSize) {
            int end = Math.min(i + batchSize, total);
            List<PhotoData> batch = photos.subList(i, end);

            database.photoDao().insertPhotos(batch);

            if (callback != null) {
                int processed = end;
                AppExecutors.getInstance().mainThread().execute(() ->
                        callback.onProgressUpdate(processed, total));
            }
        }
    }

    // Rest of the methods remain the same...
    public void getClustersFromFirebase(OnClustersLoadedCallback callback) {
        if (firebaseClusterManager == null) {
            if (callback != null) {
                callback.onError("Firebase cluster manager not initialized");
            }
            return;
        }

        firebaseClusterManager.getClusters(
                new FirebaseClusterManager.OnClustersRetrievedCallback() {
                    @Override
                    public void onClustersRetrieved(List<PhotoClusteringManager.PhotoCluster> clusters) {
                        if (callback != null) {
                            callback.onClustersLoaded(clusters);
                        }
                    }

                    @Override
                    public void onError(String error) {
                        if (callback != null) {
                            callback.onError(error);
                        }
                    }
                });
    }

    public void getClustersFromLocal(OnClustersLoadedCallback callback) {
        executor.execute(() -> {
            try {
                List<String> clusterIds = database.photoDao().getAllClusterIds();
                List<PhotoClusteringManager.PhotoCluster> clusters = new java.util.ArrayList<>();

                for (String clusterId : clusterIds) {
                    List<PhotoData> photos = database.photoDao().getPhotosByCluster(clusterId);
                    if (!photos.isEmpty()) {
                        PhotoClusteringManager.PhotoCluster cluster =
                                new PhotoClusteringManager.PhotoCluster(clusterId);
                        cluster.setPhotos(photos);

                        PhotoData first = photos.get(0);
                        cluster.setLocationName(first.getLocationName());
                        cluster.setLatitude(first.getLatitude());
                        cluster.setLongitude(first.getLongitude());

                        clusters.add(cluster);
                    }
                }

                if (callback != null) {
                    callback.onClustersLoaded(clusters);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading clusters: " + e.getMessage());
                if (callback != null) {
                    callback.onError(e.getMessage());
                }
            }
        });
    }

    public interface OnClustersLoadedCallback {
        void onClustersLoaded(List<PhotoClusteringManager.PhotoCluster> clusters);
        void onError(String error);
    }

    public interface OnPhotosLoadedCallback {
        void onPhotosLoaded(List<PhotoData> photos);
        void onError(String error);
    }
}