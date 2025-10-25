package com.example.recalllive;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

@Entity(tableName = "photos")
public class PhotoData {

    // MAKE IT SO IT DOESNT AUTO FALL BACK ONTO CURRENT DATE
    @PrimaryKey
    @NonNull
    private String photoUri;
    private long dateTaken;
    private double latitude;
    private double longitude;
    private String clusterId;
    private String timeCluster; // morning, afternoon, evening, night
    private String locationName; // friendly name for location cluster

    public PhotoData(@NonNull String photoUri) {
        this.photoUri = photoUri;
    }

    // Getters and Setters
    @NonNull
    public String getPhotoUri() {
        return photoUri;
    }

    public void setPhotoUri(@NonNull String photoUri) {
        this.photoUri = photoUri;
    }

    public long getDateTaken() {
        return dateTaken;
    }

    public void setDateTaken(long dateTaken) {
        this.dateTaken = dateTaken;
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

    public String getClusterId() {
        return clusterId;
    }

    public void setClusterId(String clusterId) {
        this.clusterId = clusterId;
    }

    public String getTimeCluster() {
        return timeCluster;
    }

    public void setTimeCluster(String timeCluster) {
        this.timeCluster = timeCluster;
    }

    public String getLocationName() {
        return locationName;
    }

    public void setLocationName(String locationName) {
        this.locationName = locationName;
    }

    public boolean hasLocation() {
        return latitude != 0.0 || longitude != 0.0;
    }
}