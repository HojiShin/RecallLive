package com.example.recalllive;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * FIXED: Gemini chat service - NO CRASH version with proper error handling
 */
public class GeminiChatService {
    private static final String TAG = "GeminiChatService";

    // Try different model names based on your API access
    private static final String MODEL_NAME = "gemini-2.0-flash";  // Try this first
//     private static final String MODEL_NAME = "gemini-1.5-flash";   // Fallback option

    private final Context context;
    private GenerativeModelFutures model;
    private final Executor executor;
    private ChatMessage.EmotionContext currentEmotionContext;

    // Track initialization state
    private boolean isInitialized = false;
    private String initError = null;

    public interface ChatCallback {
        void onResponse(String response);
        void onError(String error);
    }

    public interface EmotionDataCallback {
        void onEmotionDataLoaded(ChatMessage.EmotionContext emotionContext);
        void onError(String error);
    }

    public GeminiChatService(Context context) {
        this.context = context;
        this.executor = Executors.newSingleThreadExecutor();

        // Initialize model safely - NO CRASH
        try {
            Log.d(TAG, "═══════════════════════════════════════");
            Log.d(TAG, "GEMINI INITIALIZATION");
            Log.d(TAG, "═══════════════════════════════════════");

            String apiKey = BuildConfig.API_KEY;

            // DETAILED LOGGING to see what's happening
            Log.d(TAG, "BuildConfig.API_KEY = '" + apiKey + "'");
            Log.d(TAG, "API Key length: " + (apiKey != null ? apiKey.length() : "null"));
            Log.d(TAG, "Expected length: 39");

            if (apiKey != null && apiKey.length() > 10) {
                Log.d(TAG, "First 10 chars: " + apiKey.substring(0, 10));
                Log.d(TAG, "Last 5 chars: " + apiKey.substring(apiKey.length() - 5));
            }

            // Validate API key
            if (apiKey == null || apiKey.isEmpty()) {
                initError = "API key is null or empty";
                Log.e(TAG, "❌ " + initError);
                return; // Don't throw - just return
            }

            if (apiKey.equals("YOUR_API_KEY_HERE")) {
                initError = "API key not configured (still default value)";
                Log.e(TAG, "❌ " + initError);
                Log.e(TAG, "");
                Log.e(TAG, "SOLUTION:");
                Log.e(TAG, "1. Open local.properties in project root");
                Log.e(TAG, "2. Change line to: GEMINI_API_KEY=AIzaSy...");
                Log.e(TAG, "3. File → Invalidate Caches → Invalidate and Restart");
                Log.e(TAG, "4. Build → Clean Project");
                Log.e(TAG, "5. Build → Rebuild Project");
                return; // Don't throw
            }

            if (!apiKey.startsWith("AIza")) {
                initError = "API key format invalid (should start with AIza)";
                Log.e(TAG, "❌ " + initError);
                Log.e(TAG, "Current value: " + apiKey);
                return; // Don't throw
            }

            if (apiKey.length() != 39) {
                Log.w(TAG, "⚠️ API key length unusual: " + apiKey.length() + " (expected 39)");
                Log.w(TAG, "Will try anyway...");
            }

            // Try to initialize model
            Log.d(TAG, "Initializing model: " + MODEL_NAME);
            GenerativeModel gm = new GenerativeModel(MODEL_NAME, apiKey);
            this.model = GenerativeModelFutures.from(gm);

            isInitialized = true;
            Log.d(TAG, "✅ GeminiChatService initialized successfully");
            Log.d(TAG, "═══════════════════════════════════════");

        } catch (Exception e) {
            initError = "Exception: " + e.getMessage();
            Log.e(TAG, "❌ Failed to initialize", e);
            Log.e(TAG, "═══════════════════════════════════════");
            // Don't throw - service will handle errors gracefully
        }
    }

    public void loadEmotionData(String patientUid, EmotionDataCallback callback) {
        Log.d(TAG, "Loading emotion data for patient: " + patientUid);

        DatabaseReference emotionRef = FirebaseDatabase.getInstance().getReference()
                .child("Patient")
                .child(patientUid)
                .child("videoEmotions");

        emotionRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    Log.d(TAG, "No emotion data found");
                    callback.onError("No emotion data available yet");
                    return;
                }

                int totalEmotions = 0;
                int happy = 0, sad = 0, angry = 0, neutral = 0;
                int fear = 0, disgust = 0, surprise = 0;
                int videoCount = 0;

                for (DataSnapshot videoSnapshot : snapshot.getChildren()) {
                    videoCount++;

                    if (videoSnapshot.child("happy").exists()) {
                        totalEmotions += getIntValue(videoSnapshot, "totalEmotions");
                        happy += getIntValue(videoSnapshot, "happy");
                        sad += getIntValue(videoSnapshot, "sad");
                        angry += getIntValue(videoSnapshot, "angry");
                        neutral += getIntValue(videoSnapshot, "neutral");
                        fear += getIntValue(videoSnapshot, "fear");
                        disgust += getIntValue(videoSnapshot, "disgust");
                        surprise += getIntValue(videoSnapshot, "surprise");
                    }
                }

                ChatMessage.EmotionContext emotionContext = new ChatMessage.EmotionContext(
                        totalEmotions, happy, sad, angry, neutral, fear, disgust, surprise, videoCount
                );

                currentEmotionContext = emotionContext;

                Log.d(TAG, "✅ Emotion data loaded:");
                Log.d(TAG, "  Videos: " + videoCount);
                Log.d(TAG, "  Total emotions: " + totalEmotions);

                callback.onEmotionDataLoaded(emotionContext);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load emotion data: " + error.getMessage());
                callback.onError("Failed to load emotion data: " + error.getMessage());
            }
        });
    }

    private int getIntValue(DataSnapshot snapshot, String key) {
        Long value = snapshot.child(key).getValue(Long.class);
        return value != null ? value.intValue() : 0;
    }

    public void sendMessage(String userMessage, String patientUid, ChatCallback callback) {
        // Check if initialized first
        if (!isInitialized) {
            String errorMsg = "⚠️ Gemini is not initialized\n\n";
            if (initError != null) {
                errorMsg += "Error: " + initError + "\n\n";
            }
            errorMsg += "TROUBLESHOOTING:\n\n";
            errorMsg += "1. Check local.properties file:\n";
            errorMsg += "   GEMINI_API_KEY=AIzaSy...\n\n";
            errorMsg += "2. In Android Studio:\n";
            errorMsg += "   File → Invalidate Caches\n";
            errorMsg += "   → Invalidate and Restart\n\n";
            errorMsg += "3. After restart:\n";
            errorMsg += "   Build → Clean Project\n";
            errorMsg += "   Build → Rebuild Project\n\n";
            errorMsg += "4. Check logcat for:\n";
            errorMsg += "   'BuildConfig.API_KEY = ...'\n\n";
            errorMsg += "The key should start with 'AIza' and be 39 characters long.";

            Log.e(TAG, "Cannot send message - not initialized");
            callback.onError(errorMsg);
            return;
        }

        if (currentEmotionContext == null) {
            loadEmotionData(patientUid, new EmotionDataCallback() {
                @Override
                public void onEmotionDataLoaded(ChatMessage.EmotionContext emotionContext) {
                    sendMessageWithContext(userMessage, callback);
                }

                @Override
                public void onError(String error) {
                    sendMessageWithContext(userMessage, callback);
                }
            });
        } else {
            sendMessageWithContext(userMessage, callback);
        }
    }

    private void sendMessageWithContext(String userMessage, ChatCallback callback) {
        executor.execute(() -> {
            try {
                Log.d(TAG, "═══════════════════════════════════════");
                Log.d(TAG, "SENDING MESSAGE TO GEMINI");
                Log.d(TAG, "═══════════════════════════════════════");

                String fullPrompt = buildContextPrompt(userMessage);
                Log.d(TAG, "Prompt length: " + fullPrompt.length() + " chars");

                Content content = new Content.Builder()
                        .addText(fullPrompt)
                        .build();

                ListenableFuture<GenerateContentResponse> response = model.generateContent(content);

                Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
                    @Override
                    public void onSuccess(GenerateContentResponse result) {
                        String responseText = result.getText();
                        Log.d(TAG, "✅ Gemini response received (" + responseText.length() + " chars)");
                        callback.onResponse(responseText);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        Log.e(TAG, "❌ Gemini request failed", t);

                        String errorMsg = "I'm having trouble connecting right now.";
                        String exceptionMsg = t.getMessage();

                        if (exceptionMsg != null) {
                            if (exceptionMsg.contains("API key") || exceptionMsg.contains("API_KEY")) {
                                errorMsg = "API key error. The key may be invalid or not accepted.\n\n" +
                                        "Try creating a new key at:\n" +
                                        "https://makersuite.google.com/app/apikey";
                            } else if (exceptionMsg.contains("403") || exceptionMsg.contains("PERMISSION_DENIED")) {
                                errorMsg = "Permission denied.\n\n" +
                                        "Enable the Generative Language API:\n" +
                                        "https://console.cloud.google.com/apis/library/generativelanguage.googleapis.com\n\n" +
                                        "Click ENABLE and wait 30 seconds.";
                            } else if (exceptionMsg.contains("404") || exceptionMsg.contains("not found")) {
                                errorMsg = "Model '" + MODEL_NAME + "' not found.\n\n" +
                                        "Try changing MODEL_NAME in GeminiChatService.java to:\n" +
                                        "gemini-1.5-flash";
                            } else if (exceptionMsg.contains("quota") || exceptionMsg.contains("limit")) {
                                errorMsg = "API quota exceeded. Please try again tomorrow.";
                            } else if (exceptionMsg.contains("network") || exceptionMsg.contains("connect")) {
                                errorMsg = "Network error. Please check your internet connection.";
                            } else {
                                errorMsg = "Error: " + exceptionMsg;
                            }
                        }

                        callback.onError(errorMsg);
                    }
                }, executor);

            } catch (Exception e) {
                Log.e(TAG, "❌ Exception sending message", e);
                callback.onError("Error: " + e.getMessage());
            }
        });
    }

    private String buildContextPrompt(String userMessage) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("You are a compassionate AI assistant helping a guardian understand their loved one's emotions during memory video viewing.\n\n");

        if (currentEmotionContext != null && currentEmotionContext.getTotalEmotions() > 0) {
            prompt.append("EMOTION DATA:\n");
            prompt.append("Videos: ").append(currentEmotionContext.getVideoCount()).append("\n");
            prompt.append("Total emotions: ").append(currentEmotionContext.getTotalEmotions()).append("\n\n");

            prompt.append("Breakdown:\n");
            prompt.append("😊 Happy: ").append(currentEmotionContext.getHappy()).append("\n");
            prompt.append("😐 Neutral: ").append(currentEmotionContext.getNeutral()).append("\n");
            prompt.append("😢 Sad: ").append(currentEmotionContext.getSad()).append("\n");
            prompt.append("😮 Surprise: ").append(currentEmotionContext.getSurprise()).append("\n");
            prompt.append("😠 Angry: ").append(currentEmotionContext.getAngry()).append("\n");
            prompt.append("😨 Fear: ").append(currentEmotionContext.getFear()).append("\n");
            // FIXED: getDisgust() instead of getDigust()
            prompt.append("🤢 Disgust: ").append(currentEmotionContext.getDisgust()).append("\n\n");

            int total = currentEmotionContext.getTotalEmotions();
            if (total > 0) {
                int positive = currentEmotionContext.getHappy() + currentEmotionContext.getSurprise();
                int negative = currentEmotionContext.getSad() + currentEmotionContext.getAngry() + currentEmotionContext.getFear();

                double positivePercent = (positive * 100.0) / total;
                double negativePercent = (negative * 100.0) / total;

                prompt.append("Balance:\n");
                prompt.append("Positive: ").append(String.format("%.1f%%", positivePercent)).append("\n");
                prompt.append("Negative: ").append(String.format("%.1f%%", negativePercent)).append("\n\n");
            }
        }

        prompt.append("QUESTION: ").append(userMessage).append("\n\n");
        prompt.append("Provide a compassionate, helpful response (2-3 paragraphs):");

        return prompt.toString();
    }

    public String[] getSuggestedQuestions() {
        if (currentEmotionContext == null || currentEmotionContext.getTotalEmotions() == 0) {
            return new String[]{
                    "How can memory videos help?",
                    "What should I look for?",
                    "How can I create better videos?",
                    "What activities can we do?"
            };
        }

        int total = currentEmotionContext.getTotalEmotions();
        int positive = currentEmotionContext.getHappy() + currentEmotionContext.getSurprise();
        int negative = currentEmotionContext.getSad() + currentEmotionContext.getAngry() + currentEmotionContext.getFear();

        double negativePercent = (negative * 100.0) / total;
        double positivePercent = (positive * 100.0) / total;

        if (negativePercent > 50) {
            return new String[]{
                    "Why are there more sad emotions?",
                    "How can I improve responses?",
                    "What videos should I avoid?",
                    "Are these emotions concerning?"
            };
        } else if (positivePercent > 60) {
            return new String[]{
                    "How can I maintain positive responses?",
                    "What makes these videos effective?",
                    "Should I create more like these?",
                    "What activities complement videos?"
            };
        } else {
            return new String[]{
                    "What do mixed emotions mean?",
                    "How should I respond?",
                    "How can I support better?",
                    "Any warning signs to watch?"
            };
        }
    }

    public void clearContext() {
        currentEmotionContext = null;
        Log.d(TAG, "Context cleared");
    }

    public ChatMessage.EmotionContext getCurrentEmotionContext() {
        return currentEmotionContext;
    }

    public boolean isInitialized() {
        return isInitialized;
    }

    public String getInitError() {
        return initError;
    }
}