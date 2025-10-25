package com.example.recalllive;

import android.content.Context;
import android.util.Log;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FirebaseClusterManager {
    private static final String TAG = "FirebaseClusterManager";

    private final DatabaseReference database;
    private final String patientUid;
    private final Context context;

    public FirebaseClusterManager(Context context, String patientUid) {
        this.context = context;
        this.patientUid = patientUid;
        this.database = FirebaseDatabase.getInstance().getReference();
    }

    /**
     * Store clusters in Firebase under Patient/{uid}/clusters
     */
    public void storeClusters(List<PhotoClusteringManager.PhotoCluster> clusters,
                              OnClusterStorageCallback callback) {
        if (clusters == null || clusters.isEmpty()) {
            if (callback != null) {
                callback.onError("No clusters to store");
            }
            return;
        }

        DatabaseReference clustersRef = database.child("Patient")
                .child(patientUid)
                .child("clusters");

        Map<String, Object> clusterUpdates = new HashMap<>();

        for (PhotoClusteringManager.PhotoCluster cluster : clusters) {
            Map<String, Object> clusterData = new HashMap<>();

            // Store cluster metadata
            clusterData.put("clusterId", cluster.getClusterId());
            clusterData.put("latitude", cluster.getLatitude());
            clusterData.put("longitude", cluster.getLongitude());
            clusterData.put("startTime", cluster.getStartTime());
            clusterData.put("endTime", cluster.getEndTime());
            clusterData.put("locationName", cluster.getLocationName());
            clusterData.put("timeDescription", cluster.getTimeDescription());
            clusterData.put("photoCount", cluster.getPhotoCount());
            clusterData.put("createdAt", System.currentTimeMillis());

            // Store photo URIs in the cluster
            List<String> photoUris = new ArrayList<>();
            Map<String, Object> photosData = new HashMap<>();

            for (PhotoData photo : cluster.getPhotos()) {
                photoUris.add(photo.getPhotoUri());

                // Store individual photo metadata
                Map<String, Object> photoMeta = new HashMap<>();
                photoMeta.put("uri", photo.getPhotoUri());
                photoMeta.put("dateTaken", photo.getDateTaken());
                photoMeta.put("latitude", photo.getLatitude());
                photoMeta.put("longitude", photo.getLongitude());
                photoMeta.put("timeCluster", photo.getTimeCluster());

                photosData.put(sanitizeKey(photo.getPhotoUri()), photoMeta);
            }

            clusterData.put("photoUris", photoUris);
            clusterData.put("photos", photosData);

            // Add to updates map
            clusterUpdates.put(cluster.getClusterId(), clusterData);
        }

        // Store all clusters in one batch update
        clustersRef.updateChildren(clusterUpdates)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(Task<Void> task) {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "Successfully stored " + clusters.size() + " clusters");
                            // Also update cluster summary
                            updateClusterSummary(clusters);
                            if (callback != null) {
                                callback.onSuccess(clusters.size());
                            }
                        } else {
                            Log.e(TAG, "Failed to store clusters", task.getException());
                            if (callback != null) {
                                callback.onError(task.getException().getMessage());
                            }
                        }
                    }
                });
    }

    /**
     * Update cluster summary statistics in Firebase
     */
    private void updateClusterSummary(List<PhotoClusteringManager.PhotoCluster> clusters) {
        DatabaseReference summaryRef = database.child("Patient")
                .child(patientUid)
                .child("clusterSummary");

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalClusters", clusters.size());
        summary.put("lastUpdated", System.currentTimeMillis());

        // Count photos by time cluster
        Map<String, Integer> timeDistribution = new HashMap<>();
        timeDistribution.put("Morning", 0);
        timeDistribution.put("Afternoon", 0);
        timeDistribution.put("Evening", 0);
        timeDistribution.put("Night", 0);

        int totalPhotos = 0;
        int clustersWithLocation = 0;

        for (PhotoClusteringManager.PhotoCluster cluster : clusters) {
            totalPhotos += cluster.getPhotoCount();

            if (cluster.getLatitude() != 0 || cluster.getLongitude() != 0) {
                clustersWithLocation++;
            }

            // Count time distribution
            for (PhotoData photo : cluster.getPhotos()) {
                String timeCluster = photo.getTimeCluster();
                if (timeCluster != null && timeDistribution.containsKey(timeCluster)) {
                    timeDistribution.put(timeCluster,
                            timeDistribution.get(timeCluster) + 1);
                }
            }
        }

        summary.put("totalPhotos", totalPhotos);
        summary.put("clustersWithLocation", clustersWithLocation);
        summary.put("timeDistribution", timeDistribution);

        summaryRef.setValue(summary);
    }

    /**
     * Retrieve clusters from Firebase
     */
    public void getClusters(OnClustersRetrievedCallback callback) {
        DatabaseReference clustersRef = database.child("Patient")
                .child(patientUid)
                .child("clusters");

        clustersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                List<PhotoClusteringManager.PhotoCluster> clusters = new ArrayList<>();

                for (DataSnapshot clusterSnapshot : dataSnapshot.getChildren()) {
                    try {
                        String clusterId = clusterSnapshot.child("clusterId").getValue(String.class);
                        PhotoClusteringManager.PhotoCluster cluster =
                                new PhotoClusteringManager.PhotoCluster(clusterId);

                        // Retrieve cluster metadata
                        cluster.setLatitude(clusterSnapshot.child("latitude").getValue(Double.class));
                        cluster.setLongitude(clusterSnapshot.child("longitude").getValue(Double.class));
                        cluster.setStartTime(clusterSnapshot.child("startTime").getValue(Long.class));
                        cluster.setEndTime(clusterSnapshot.child("endTime").getValue(Long.class));
                        cluster.setLocationName(clusterSnapshot.child("locationName").getValue(String.class));
                        cluster.setTimeDescription(clusterSnapshot.child("timeDescription").getValue(String.class));

                        // Retrieve photos
                        List<PhotoData> photos = new ArrayList<>();
                        DataSnapshot photosSnapshot = clusterSnapshot.child("photos");

                        for (DataSnapshot photoSnapshot : photosSnapshot.getChildren()) {
                            String uri = photoSnapshot.child("uri").getValue(String.class);
                            PhotoData photo = new PhotoData(uri);

                            photo.setDateTaken(photoSnapshot.child("dateTaken").getValue(Long.class));
                            photo.setLatitude(photoSnapshot.child("latitude").getValue(Double.class));
                            photo.setLongitude(photoSnapshot.child("longitude").getValue(Double.class));
                            photo.setTimeCluster(photoSnapshot.child("timeCluster").getValue(String.class));
                            photo.setClusterId(clusterId);

                            photos.add(photo);
                        }

                        cluster.setPhotos(photos);
                        clusters.add(cluster);

                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing cluster: " + e.getMessage());
                    }
                }

                if (callback != null) {
                    callback.onClustersRetrieved(clusters);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(TAG, "Failed to retrieve clusters: " + databaseError.getMessage());
                if (callback != null) {
                    callback.onError(databaseError.getMessage());
                }
            }
        });
    }

    /**
     * Delete all clusters from Firebase
     */
    public void deleteAllClusters(OnClusterDeletionCallback callback) {
        DatabaseReference clustersRef = database.child("Patient")
                .child(patientUid)
                .child("clusters");

        clustersRef.removeValue()
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(Task<Void> task) {
                        if (task.isSuccessful()) {
                            // Also clear summary
                            database.child("Patient")
                                    .child(patientUid)
                                    .child("clusterSummary")
                                    .removeValue();

                            if (callback != null) {
                                callback.onSuccess();
                            }
                        } else {
                            if (callback != null) {
                                callback.onError(task.getException().getMessage());
                            }
                        }
                    }
                });
    }

    /**
     * Get cluster by ID from Firebase
     */
    public void getClusterById(String clusterId, OnSingleClusterRetrievedCallback callback) {
        DatabaseReference clusterRef = database.child("Patient")
                .child(patientUid)
                .child("clusters")
                .child(clusterId);

        clusterRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    try {
                        PhotoClusteringManager.PhotoCluster cluster =
                                parseClusterFromSnapshot(dataSnapshot);

                        if (callback != null) {
                            callback.onClusterRetrieved(cluster);
                        }
                    } catch (Exception e) {
                        if (callback != null) {
                            callback.onError("Error parsing cluster: " + e.getMessage());
                        }
                    }
                } else {
                    if (callback != null) {
                        callback.onError("Cluster not found");
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                if (callback != null) {
                    callback.onError(databaseError.getMessage());
                }
            }
        });
    }

    /**
     * Helper method to parse cluster from DataSnapshot
     */
    private PhotoClusteringManager.PhotoCluster parseClusterFromSnapshot(DataSnapshot snapshot) {
        String clusterId = snapshot.child("clusterId").getValue(String.class);
        PhotoClusteringManager.PhotoCluster cluster =
                new PhotoClusteringManager.PhotoCluster(clusterId);

        cluster.setLatitude(snapshot.child("latitude").getValue(Double.class));
        cluster.setLongitude(snapshot.child("longitude").getValue(Double.class));
        cluster.setStartTime(snapshot.child("startTime").getValue(Long.class));
        cluster.setEndTime(snapshot.child("endTime").getValue(Long.class));
        cluster.setLocationName(snapshot.child("locationName").getValue(String.class));
        cluster.setTimeDescription(snapshot.child("timeDescription").getValue(String.class));

        List<PhotoData> photos = new ArrayList<>();
        DataSnapshot photosSnapshot = snapshot.child("photos");

        for (DataSnapshot photoSnapshot : photosSnapshot.getChildren()) {
            String uri = photoSnapshot.child("uri").getValue(String.class);
            PhotoData photo = new PhotoData(uri);

            photo.setDateTaken(photoSnapshot.child("dateTaken").getValue(Long.class));
            photo.setLatitude(photoSnapshot.child("latitude").getValue(Double.class));
            photo.setLongitude(photoSnapshot.child("longitude").getValue(Double.class));
            photo.setTimeCluster(photoSnapshot.child("timeCluster").getValue(String.class));
            photo.setClusterId(clusterId);

            photos.add(photo);
        }

        cluster.setPhotos(photos);
        return cluster;
    }

    /**
     * Sanitize Firebase key (remove invalid characters)
     */
    private String sanitizeKey(String key) {
        return key.replaceAll("[.#$\\[\\]/]", "_");
    }

    // Callback interfaces
    public interface OnClusterStorageCallback {
        void onSuccess(int clusterCount);
        void onError(String error);
    }

    public interface OnClustersRetrievedCallback {
        void onClustersRetrieved(List<PhotoClusteringManager.PhotoCluster> clusters);
        void onError(String error);
    }

    public interface OnSingleClusterRetrievedCallback {
        void onClusterRetrieved(PhotoClusteringManager.PhotoCluster cluster);
        void onError(String error);
    }

    public interface OnClusterDeletionCallback {
        void onSuccess();
        void onError(String error);
    }
}