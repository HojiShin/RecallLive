//package com.example.recalllive;
//
//import android.content.Context;
//import android.graphics.Bitmap;
//import android.graphics.BitmapFactory;
//import android.net.Uri;
//import android.util.Log;
//
//import androidx.annotation.NonNull;
//
//import com.google.ai.client.generativeai.GenerativeModel;
//import com.google.ai.client.generativeai.java.GenerativeModelFutures;
//import com.google.ai.client.generativeai.type.Content;
//import com.google.ai.client.generativeai.type.GenerateContentResponse;
//import com.google.ai.client.generativeai.type.ImagePart;
//import com.google.ai.client.generativeai.type.Part;
//import com.google.ai.client.generativeai.type.TextPart;
//import com.google.common.util.concurrent.FutureCallback;
//import com.google.common.util.concurrent.Futures;
//import com.google.common.util.concurrent.ListenableFuture;
//
//import org.json.JSONArray;
//import org.json.JSONObject;
//
//import java.io.InputStream;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.concurrent.Executor;
//import java.util.concurrent.Executors;
//
///**
// * Generates a narrative story from images using the Gemini API.
// */
//public class GeminiStoryGenerator {
//    private static final String TAG = "GeminiStoryGenerator";
//    private final GenerativeModelFutures generativeModel;
//    private final Executor executor;
//    private final Context context;
//
//    public static class StoryResult {
//        public final String title;
//        public final String script;
//        public final List<String> keywords;
//
//        StoryResult(String title, String script, List<String> keywords) {
//            this.title = title;
//            this.script = script;
//            this.keywords = keywords;
//        }
//    }
//
//    public interface StoryCallback {
//        void onSuccess(StoryResult result);
//        void onFailure(String error);
//    }
//
//    public GeminiStoryGenerator(Context context, String apiKey) {
//        this.context = context;
//        // Use a model that supports both text and image inputs, like gemini-pro-vision
//        GenerativeModel gm = new GenerativeModel("gemini-pro-vision", apiKey);
//        this.generativeModel = GenerativeModelFutures.from(gm);
//        this.executor = Executors.newSingleThreadExecutor();
//    }
//
//    public void generateStory(List<String> imageUrls, PhotoClusteringManager.PhotoCluster cluster, StoryCallback callback) {
//        executor.execute(() -> {
//            try {
//                List<Part> parts = new ArrayList<>();
//
//                // 1. Prepare and add the text prompt
//                String prompt = createPrompt(cluster);
//                parts.add(new TextPart(prompt));
//
//                // 2. Convert image URLs to Bitmaps and add as ImagePart objects
//                for (String url : imageUrls) {
//                    try (InputStream inputStream = context.getContentResolver().openInputStream(Uri.parse(url))) {
//                        Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
//                        if (bitmap != null) {
//                            parts.add(new ImagePart(bitmap));
//                        } else {
//                            Log.w(TAG, "Failed to decode bitmap from URI: " + url);
//                        }
//                    } catch (Exception e) {
//                        Log.e(TAG, "Failed to load bitmap from URI: " + url, e);
//                    }
//                }
//
//                // Safety check: ensure at least one image was loaded
//                if (parts.size() <= 1) {
//                    callback.onFailure("Could not load any valid images for AI generation.");
//                    return;
//                }
//
//                // --- START OF CORRECTION ---
//                // Build the content object by adding parts one by one
//                Content.Builder contentBuilder = new Content.Builder();
//                for (Part part : parts) {
//                    contentBuilder.addPart(part);
//                }
//                Content content = contentBuilder.build();
//                // --- END OF CORRECTION ---
//
//                // 3. Call the Gemini API
//                ListenableFuture<GenerateContentResponse> response = generativeModel.generateContent(content);
//                Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
//                    @Override
//                    public void onSuccess(GenerateContentResponse result) {
//                        String textResponse = result.getText();
//                        if (textResponse == null || textResponse.isEmpty()) {
//                            Log.e(TAG, "Gemini API returned an empty response.");
//                            callback.onFailure("AI returned an empty response.");
//                            return;
//                        }
//
//                        Log.d(TAG, "Gemini API Response: " + textResponse);
//                        try {
//                            // 4. Parse the structured response
//                            JSONObject jsonResponse = new JSONObject(textResponse.replace("```json", "").replace("```", "").trim());
//                            String title = jsonResponse.getString("title");
//                            String script = jsonResponse.getString("script");
//                            JSONArray keywordsArray = jsonResponse.getJSONArray("keywords");
//                            List<String> keywords = new ArrayList<>();
//                            for (int i = 0; i < keywordsArray.length(); i++) {
//                                keywords.add(keywordsArray.getString(i));
//                            }
//                            callback.onSuccess(new StoryResult(title, script, keywords));
//                        } catch (Exception e) {
//                            Log.e(TAG, "Failed to parse Gemini JSON response", e);
//                            callback.onFailure("Failed to parse AI response.");
//                        }
//                    }
//
//                    @Override
//                    public void onFailure(@NonNull Throwable t) {
//                        Log.e(TAG, "Error generating story with Gemini", t);
//                        callback.onFailure("AI content generation failed: " + t.getMessage());
//                    }
//                }, executor);
//
//            } catch (Exception e) {
//                Log.e(TAG, "Error during story generation setup", e);
//                callback.onFailure("Failed to prepare request for AI.");
//            }
//        });
//    }
//
//    private String createPrompt(PhotoClusteringManager.PhotoCluster cluster) {
//        String locationInfo = (cluster.getLocationName() != null && !"Unknown Location".equals(cluster.getLocationName()))
//                ? " in " + cluster.getLocationName()
//                : "";
//        String timeInfo = (cluster.getTimeDescription() != null)
//                ? " around " + cluster.getTimeDescription()
//                : "";
//
//        return "You are a creative assistant for an app called Recalllive, which helps people with Alzheimer's remember their past. " +
//                "Based on the following images" + locationInfo + timeInfo + ", generate a short, gentle, and heartwarming story. " +
//                "The tone should be nostalgic and reassuring. The narration script should be around 3-4 sentences long. " +
//                "Provide a response in a strict JSON format like this: " +
//                "{\"title\": \"A short, evocative title\", \"script\": \"The narration script.\", \"keywords\": [\"keyword1\", \"keyword2\", \"keyword3\"]}";
//    }
//}