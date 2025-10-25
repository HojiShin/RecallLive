package com.example.recalllive;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.IgnoreExtraProperties;
import com.google.firebase.firestore.PropertyName;
import com.google.firebase.firestore.ServerTimestamp;

import java.util.ArrayList;
import java.util.List;

/**
 * POJO model for Memory documents in Firestore
 * Represents a generated memory with title, script, and associated media
 */
@IgnoreExtraProperties
public class Memory {

    @DocumentId
    private String documentId;

    @PropertyName("userId")
    private String userId;

    @PropertyName("title")
    private String title;

    @PropertyName("script")
    private String script;

    @PropertyName("keywords")
    private List<String> keywords;

    @ServerTimestamp
    @PropertyName("createdAt")
    private Timestamp createdAt;

    @PropertyName("keyImageUrls")
    private List<String> keyImageUrls;

    @PropertyName("videoUrl")
    private String videoUrl;

    @PropertyName("clusterId")
    private String clusterId; // Optional: link to photo cluster

    @PropertyName("duration")
    private Integer duration; // Video duration in seconds

    @PropertyName("isProcessed")
    private boolean isProcessed; // Whether video generation is complete

    @PropertyName("geminiModel")
    private String geminiModel; // Track which Gemini model was used

    // Default constructor required for Firestore
    public Memory() {
        this.keywords = new ArrayList<>();
        this.keyImageUrls = new ArrayList<>();
        this.isProcessed = false;
    }

    // Constructor with required fields
    public Memory(String userId, String title, String script) {
        this();
        this.userId = userId;
        this.title = title;
        this.script = script;
    }

    // Full constructor
    public Memory(String userId, String title, String script,
                  List<String> keywords, List<String> keyImageUrls) {
        this.userId = userId;
        this.title = title;
        this.script = script;
        this.keywords = keywords != null ? keywords : new ArrayList<>();
        this.keyImageUrls = keyImageUrls != null ? keyImageUrls : new ArrayList<>();
        this.isProcessed = false;
    }

    // Getters and Setters
    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getScript() {
        return script;
    }

    public void setScript(String script) {
        this.script = script;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public void setKeywords(List<String> keywords) {
        this.keywords = keywords;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public List<String> getKeyImageUrls() {
        return keyImageUrls;
    }

    public void setKeyImageUrls(List<String> keyImageUrls) {
        this.keyImageUrls = keyImageUrls;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public String getClusterId() {
        return clusterId;
    }

    public void setClusterId(String clusterId) {
        this.clusterId = clusterId;
    }

    public Integer getDuration() {
        return duration;
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
    }

    public boolean isProcessed() {
        return isProcessed;
    }

    public void setProcessed(boolean processed) {
        isProcessed = processed;
    }

    public String getGeminiModel() {
        return geminiModel;
    }

    public void setGeminiModel(String geminiModel) {
        this.geminiModel = geminiModel;
    }

    // Builder pattern for easier object creation
    public static class Builder {
        private String userId;
        private String title;
        private String script;
        private List<String> keywords = new ArrayList<>();
        private List<String> keyImageUrls = new ArrayList<>();
        private String clusterId;
        private String geminiModel;

        public Builder(String userId, String title, String script) {
            this.userId = userId;
            this.title = title;
            this.script = script;
        }

        public Builder withKeywords(List<String> keywords) {
            this.keywords = keywords;
            return this;
        }

        public Builder withKeyImageUrls(List<String> keyImageUrls) {
            this.keyImageUrls = keyImageUrls;
            return this;
        }

        public Builder withClusterId(String clusterId) {
            this.clusterId = clusterId;
            return this;
        }

        public Builder withGeminiModel(String geminiModel) {
            this.geminiModel = geminiModel;
            return this;
        }

        public Memory build() {
            Memory memory = new Memory(userId, title, script, keywords, keyImageUrls);
            memory.setClusterId(clusterId);
            memory.setGeminiModel(geminiModel);
            return memory;
        }
    }
}