package com.example.recalllive;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.IgnoreExtraProperties;
import com.google.firebase.firestore.PropertyName;
import com.google.firebase.firestore.ServerTimestamp;

/**
 * Model class for chat messages between Guardian and Gemini AI
 */
@IgnoreExtraProperties
public class ChatMessage {

    public enum MessageType {
        USER,      // Message from guardian
        AI,        // Response from Gemini
        SYSTEM     // System messages (e.g., "Analyzing emotion data...")
    }

    @DocumentId
    private String messageId;

    @PropertyName("guardianUid")
    private String guardianUid;

    @PropertyName("patientUid")
    private String patientUid;

    @PropertyName("content")
    private String content;

    @PropertyName("type")
    private String type; // USER, AI, or SYSTEM

    @ServerTimestamp
    @PropertyName("timestamp")
    private Timestamp timestamp;

    @PropertyName("emotionContext")
    private EmotionContext emotionContext; // Optional: emotion data that informed this message

    // Default constructor for Firebase
    public ChatMessage() {
    }

    public ChatMessage(String content, MessageType type) {
        this.content = content;
        this.type = type.name();
    }

    public ChatMessage(String guardianUid, String patientUid, String content, MessageType type) {
        this.guardianUid = guardianUid;
        this.patientUid = patientUid;
        this.content = content;
        this.type = type.name();
    }

    // Getters and Setters
    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getGuardianUid() {
        return guardianUid;
    }

    public void setGuardianUid(String guardianUid) {
        this.guardianUid = guardianUid;
    }

    public String getPatientUid() {
        return patientUid;
    }

    public void setPatientUid(String patientUid) {
        this.patientUid = patientUid;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public MessageType getMessageType() {
        try {
            return MessageType.valueOf(type);
        } catch (Exception e) {
            return MessageType.SYSTEM;
        }
    }

    public void setMessageType(MessageType messageType) {
        this.type = messageType.name();
    }

    public Timestamp getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Timestamp timestamp) {
        this.timestamp = timestamp;
    }

    public EmotionContext getEmotionContext() {
        return emotionContext;
    }

    public void setEmotionContext(EmotionContext emotionContext) {
        this.emotionContext = emotionContext;
    }

    /**
     * Nested class for emotion context data
     */
    @IgnoreExtraProperties
    public static class EmotionContext {
        @PropertyName("totalEmotions")
        private int totalEmotions;

        @PropertyName("happy")
        private int happy;

        @PropertyName("sad")
        private int sad;

        @PropertyName("angry")
        private int angry;

        @PropertyName("neutral")
        private int neutral;

        @PropertyName("fear")
        private int fear;

        @PropertyName("disgust")
        private int disgust;

        @PropertyName("surprise")
        private int surprise;

        @PropertyName("videoCount")
        private int videoCount;

        public EmotionContext() {
        }

        public EmotionContext(int totalEmotions, int happy, int sad, int angry,
                              int neutral, int fear, int disgust, int surprise, int videoCount) {
            this.totalEmotions = totalEmotions;
            this.happy = happy;
            this.sad = sad;
            this.angry = angry;
            this.neutral = neutral;
            this.fear = fear;
            this.disgust = disgust;
            this.surprise = surprise;
            this.videoCount = videoCount;
        }

        // Getters and Setters
        public int getTotalEmotions() { return totalEmotions; }
        public void setTotalEmotions(int totalEmotions) { this.totalEmotions = totalEmotions; }

        public int getHappy() { return happy; }
        public void setHappy(int happy) { this.happy = happy; }

        public int getSad() { return sad; }
        public void setSad(int sad) { this.sad = sad; }

        public int getAngry() { return angry; }
        public void setAngry(int angry) { this.angry = angry; }

        public int getNeutral() { return neutral; }
        public void setNeutral(int neutral) { this.neutral = neutral; }

        public int getFear() { return fear; }
        public void setFear(int fear) { this.fear = fear; }

        public int getDisgust() { return disgust; }
        public void setDisgust(int disgust) { this.disgust = disgust; }

        public int getSurprise() { return surprise; }
        public void setSurprise(int surprise) { this.surprise = surprise; }

        public int getVideoCount() { return videoCount; }
        public void setVideoCount(int videoCount) { this.videoCount = videoCount; }
    }
}