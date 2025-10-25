package com.example.recalllive;

import java.util.Objects;

public class Video {
    private String documentId;
    private String title;
    private String videoUrl;
    private String thumbnailUrl;
    private String clusterId;
    private String locationName;
    private String timeDescription;
    private int photoCount;
    private long createdAt;
    private int duration;

    // Empty constructor for Firebase
    public Video() {}

    // Constructor for simple display (your current usage)
    public Video(String title, String thumbnailUrl) {
        this.title = title;
        this.thumbnailUrl = thumbnailUrl;
    }

    // --- Add these methods ---
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Video video = (Video) o;
        // Compare the fields that define if the content of two Video objects is the same
        return Objects.equals(title, video.title) &&
                Objects.equals(thumbnailUrl, video.thumbnailUrl);
    }

    @Override
    public int hashCode() {
        // Generate a hash code based on the same fields used in equals()
        return Objects.hash(title, thumbnailUrl);
    }

    // Full constructor
    public Video(String documentId, String title, String videoUrl, String thumbnailUrl,
                 String clusterId, String locationName, String timeDescription,
                 int photoCount, long createdAt, int duration) {
        this.documentId = documentId;
        this.title = title;
        this.videoUrl = videoUrl;
        this.thumbnailUrl = thumbnailUrl;
        this.clusterId = clusterId;
        this.locationName = locationName;
        this.timeDescription = timeDescription;
        this.photoCount = photoCount;
        this.createdAt = createdAt;
        this.duration = duration;
    }

    // Getters and Setters
    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }

    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }

    public String getClusterId() { return clusterId; }
    public void setClusterId(String clusterId) { this.clusterId = clusterId; }

    public String getLocationName() { return locationName; }
    public void setLocationName(String locationName) { this.locationName = locationName; }

    public String getTimeDescription() { return timeDescription; }
    public void setTimeDescription(String timeDescription) { this.timeDescription = timeDescription; }

    public int getPhotoCount() { return photoCount; }
    public void setPhotoCount(int photoCount) { this.photoCount = photoCount; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public int getDuration() { return duration; }
    public void setDuration(int duration) { this.duration = duration; }
}
