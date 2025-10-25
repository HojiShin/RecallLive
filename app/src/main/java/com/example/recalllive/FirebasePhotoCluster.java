package com.example.recalllive;

import com.example.recalllive.PhotoClusteringManager;
import com.example.recalllive.PhotoData;
import com.google.firebase.database.IgnoreExtraProperties;
import com.google.firebase.database.PropertyName;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Firebase model for photo clusters
 */
@IgnoreExtraProperties
public class FirebasePhotoCluster {

    @PropertyName("cluster_id")
    private String clusterId;

    @PropertyName("user_id")
    private String userId;  // To identify which user/patient this belongs to

    @PropertyName("photo_uris")
    private List<String> photoUris;

    @PropertyName("latitude")
    private double latitude;

    @PropertyName("longitude")
    private double longitude;

    @PropertyName("start_time")
    private long startTime;

    @PropertyName("end_time")
    private long endTime;

    @PropertyName("location_name")
    private String locationName;

    @PropertyName("time_description")
    private String timeDescription;

    @PropertyName("photo_count")
    private int photoCount;

    @PropertyName("created_at")
    private long createdAt;

    @PropertyName("updated_at")
    private long updatedAt;

    @PropertyName("is_processed")
    private boolean isProcessed;  // Whether video has been generated

    @PropertyName("video_url")
    private String videoUrl;  // URL of generated video if exists

    @PropertyName("metadata")
    private Map<String, Object> metadata;  // Additional metadata

    // Default constructor required for Firebase
    public FirebasePhotoCluster() {
        this.photoUris = new ArrayList<>();
        this.metadata = new HashMap<>();
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
        this.isProcessed = false;
    }

    // Constructor from PhotoCluster
    public FirebasePhotoCluster(String userId, PhotoClusteringManager.PhotoCluster cluster) {
        this();
        this.userId = userId;
        this.clusterId = cluster.getClusterId();
        this.latitude = cluster.getLatitude();
        this.longitude = cluster.getLongitude();
        this.startTime = cluster.getStartTime();
        this.endTime = cluster.getEndTime();
        this.locationName = cluster.getLocationName();
        this.timeDescription = cluster.getTimeDescription();
        this.photoCount = cluster.getPhotoCount();

        // Extract photo URIs
        if (cluster.getPhotos() != null) {
            for (PhotoData photo : cluster.getPhotos()) {
                this.photoUris.add(photo.getPhotoUri());
            }
        }
    }

    // Getters and Setters
    public String getClusterId() {
        return clusterId;
    }

    public void setClusterId(String clusterId) {
        this.clusterId = clusterId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public List<String> getPhotoUris() {
        return photoUris;
    }

    public void setPhotoUris(List<String> photoUris) {
        this.photoUris = photoUris;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public String getLocationName() {
        return locationName;
    }

    public void setLocationName(String locationName) {
        this.locationName = locationName;
    }

    public String getTimeDescription() {
        return timeDescription;
    }

    public void setTimeDescription(String timeDescription) {
        this.timeDescription = timeDescription;
    }

    public int getPhotoCount() {
        return photoCount;
    }

    public void setPhotoCount(int photoCount) {
        this.photoCount = photoCount;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean isProcessed() {
        return isProcessed;
    }

    public void setProcessed(boolean processed) {
        isProcessed = processed;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}