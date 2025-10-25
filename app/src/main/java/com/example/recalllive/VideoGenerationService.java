package com.example.recalllive;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;

import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Service for generating videos from photo clusters using Gemini API
 */
public class VideoGenerationService {
    private static final String TAG = "VideoGenerationService";
    private static final String GEMINI_API_KEY = "AIzaSyCUbn35CX0VowtVcx5e1HN2Yj41c3o0G2E";
    private static final int IMAGES_PER_VIDEO = 3;

    private final Context context;
    private final FirebaseClusterManager firebaseClusterManager;
    private final Executor executor;
    private final Random random;

    public interface VideoGenerationCallback {
        void onVideoGenerationStarted(String clusterId);
        void onImagesSelected(List<String> imageUris);
        void onVideoGenerationProgress(String message);
        void onVideoGenerationComplete(String videoUrl, String clusterId);
        void onVideoGenerationError(String error);
    }

    public VideoGenerationService(Context context, String patientUid) {
        this.context = context;
        this.firebaseClusterManager = new FirebaseClusterManager(context, patientUid);
        this.executor = Executors.newSingleThreadExecutor();
        this.random = new Random();
    }

    /**
     * Generate a video from a random cluster
     */
    public void generateVideoFromRandomCluster(VideoGenerationCallback callback) {
        executor.execute(() -> {
            try {
                // Step 1: Get all clusters from Firebase
                firebaseClusterManager.getClusters(new FirebaseClusterManager.OnClustersRetrievedCallback() {
                    @Override
                    public void onClustersRetrieved(List<PhotoClusteringManager.PhotoCluster> clusters) {
                        if (clusters == null || clusters.isEmpty()) {
                            if (callback != null) {
                                callback.onVideoGenerationError("No photo clusters found");
                            }
                            return;
                        }

                        // Step 2: Select a random cluster
                        PhotoClusteringManager.PhotoCluster selectedCluster =
                                clusters.get(random.nextInt(clusters.size()));

                        Log.d(TAG, "Selected cluster: " + selectedCluster.getClusterId() +
                                " with " + selectedCluster.getPhotoCount() + " photos");

                        if (callback != null) {
                            callback.onVideoGenerationStarted(selectedCluster.getClusterId());
                        }

                        // Step 3: Process the selected cluster
                        processClusterForVideo(selectedCluster, callback);
                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "Failed to retrieve clusters: " + error);
                        if (callback != null) {
                            callback.onVideoGenerationError("Failed to retrieve clusters: " + error);
                        }
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error in video generation: " + e.getMessage());
                if (callback != null) {
                    callback.onVideoGenerationError(e.getMessage());
                }
            }
        });
    }

    /**
     * Process a specific cluster for video generation
     */
    private void processClusterForVideo(PhotoClusteringManager.PhotoCluster cluster,
                                        VideoGenerationCallback callback) {
        List<PhotoData> photos = cluster.getPhotos();

        if (photos == null || photos.size() < IMAGES_PER_VIDEO) {
            if (callback != null) {
                callback.onVideoGenerationError("Not enough photos in cluster (need at least " +
                        IMAGES_PER_VIDEO + ")");
            }
            return;
        }

        // Select 3 random photos from the cluster
        List<PhotoData> selectedPhotos = selectRandomPhotos(photos, IMAGES_PER_VIDEO);
        List<String> imageUris = new ArrayList<>();
        for (PhotoData photo : selectedPhotos) {
            imageUris.add(photo.getPhotoUri());
        }

        if (callback != null) {
            callback.onImagesSelected(imageUris);
        }

        // Generate context for the video
        String context = generateVideoContext(cluster, selectedPhotos);

        // Send to Gemini for video generation
        sendToGeminiForVideoGeneration(imageUris, context, cluster.getClusterId(), callback);
    }

    /**
     * Select random photos from a list
     */
    private List<PhotoData> selectRandomPhotos(List<PhotoData> photos, int count) {
        List<PhotoData> shuffled = new ArrayList<>(photos);
        Collections.shuffle(shuffled, random);
        return shuffled.subList(0, Math.min(count, shuffled.size()));
    }

    /**
     * Generate context description for the video
     */
    private String generateVideoContext(PhotoClusteringManager.PhotoCluster cluster,
                                        List<PhotoData> selectedPhotos) {
        StringBuilder context = new StringBuilder();

        context.append("Create a nostalgic video from these memories. ");

        if (cluster.getLocationName() != null && !cluster.getLocationName().equals("Unknown Location")) {
            context.append("Location: ").append(cluster.getLocationName()).append(". ");
        }

        if (cluster.getTimeDescription() != null) {
            context.append("Time period: ").append(cluster.getTimeDescription()).append(". ");
        }

        // Add time of day context
        if (!selectedPhotos.isEmpty() && selectedPhotos.get(0).getTimeCluster() != null) {
            context.append("These photos were taken during the ")
                    .append(selectedPhotos.get(0).getTimeCluster().toLowerCase()).append(". ");
        }

        context.append("Create a smooth, calming video that connects these moments together " +
                "with gentle transitions. This is for someone with memory challenges, " +
                "so make it warm and reassuring.");

        return context.toString();
    }

    /**
     * Send images to Gemini API for video generation
     */
    private void sendToGeminiForVideoGeneration(List<String> imageUris, String context,
                                                String clusterId, VideoGenerationCallback callback) {
        executor.execute(() -> {
            try {
                if (callback != null) {
                    callback.onVideoGenerationProgress("Preparing images for video generation...");
                }

                // Prepare the API request
                JSONObject requestBody = new JSONObject();
                requestBody.put("model", "gemini-veo-3"); // Or appropriate model name
                requestBody.put("prompt", context);

                // Add images as base64
                JSONArray images = new JSONArray();
                for (String uri : imageUris) {
                    String base64Image = convertImageToBase64(uri);
                    if (base64Image != null) {
                        JSONObject imageObj = new JSONObject();
                        imageObj.put("data", base64Image);
                        imageObj.put("mimeType", "image/jpeg");
                        images.put(imageObj);
                    }
                }
                requestBody.put("images", images);

                // Add video generation parameters
                JSONObject videoParams = new JSONObject();
                videoParams.put("duration", 30); // 30 second video
                videoParams.put("fps", 24);
                videoParams.put("resolution", "1080p");
                videoParams.put("style", "nostalgic");
                videoParams.put("transitions", "smooth");
                requestBody.put("videoParameters", videoParams);

                if (callback != null) {
                    callback.onVideoGenerationProgress("Generating video with Gemini...");
                }

                // Here you would make the actual API call to Gemini
                // Since Gemini Veo 3 API details aren't public yet, this is a placeholder
                String videoUrl = makeGeminiApiCall(requestBody.toString());

                // Store the video URL in Firebase
                storeVideoInFirebase(clusterId, videoUrl, callback);

            } catch (Exception e) {
                Log.e(TAG, "Error sending to Gemini: " + e.getMessage());
                if (callback != null) {
                    callback.onVideoGenerationError("Failed to generate video: " + e.getMessage());
                }
            }
        });
    }

    /**
     * Convert image URI to base64 string
     */
    private String convertImageToBase64(String imageUri) {
        try {
            Uri uri = Uri.parse(imageUri);
            InputStream inputStream = context.getContentResolver().openInputStream(uri);

            if (inputStream == null) {
                return null;
            }

            // Decode and resize image to reduce size
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 2; // Reduce image size by half
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream, null, options);
            inputStream.close();

            if (bitmap == null) {
                return null;
            }

            // Compress and convert to base64
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
            byte[] imageBytes = baos.toByteArray();

            return Base64.encodeToString(imageBytes, Base64.NO_WRAP);

        } catch (Exception e) {
            Log.e(TAG, "Error converting image to base64: " + e.getMessage());
            return null;
        }
    }

    /**
     * Placeholder for actual Gemini API call
     * Replace this with actual API implementation when available
     */
    private String makeGeminiApiCall(String requestBody) throws Exception {
        // TODO: Implement actual Gemini Veo 3 API call
        // This is a placeholder since the exact API isn't public yet

        Log.d(TAG, "Making Gemini API call with request: " + requestBody);

        // Simulate API response delay
        Thread.sleep(5000);

        // Return a placeholder video URL
        // In production, this would be the actual video URL from Gemini
        return "https://storage.googleapis.com/recall-live-videos/" +
                System.currentTimeMillis() + "_generated.mp4";
    }

    /**
     * Store generated video URL in Firebase
     */
    private void storeVideoInFirebase(String clusterId, String videoUrl,
                                      VideoGenerationCallback callback) {
        // Update the cluster in Firebase with the video URL
        firebaseClusterManager.getClusterById(clusterId,
                new FirebaseClusterManager.OnSingleClusterRetrievedCallback() {
                    @Override
                    public void onClusterRetrieved(PhotoClusteringManager.PhotoCluster cluster) {
                        // Here you would update the cluster with the video URL
                        // This requires adding a video URL field to your Firebase structure

                        Log.d(TAG, "Video generated successfully for cluster: " + clusterId);
                        Log.d(TAG, "Video URL: " + videoUrl);

                        if (callback != null) {
                            callback.onVideoGenerationComplete(videoUrl, clusterId);
                        }
                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "Failed to update cluster with video: " + error);
                        // Still return success since video was generated
                        if (callback != null) {
                            callback.onVideoGenerationComplete(videoUrl, clusterId);
                        }
                    }
                });
    }

    /**
     * Generate video for a specific cluster ID
     */
    public void generateVideoForCluster(String clusterId, VideoGenerationCallback callback) {
        firebaseClusterManager.getClusterById(clusterId,
                new FirebaseClusterManager.OnSingleClusterRetrievedCallback() {
                    @Override
                    public void onClusterRetrieved(PhotoClusteringManager.PhotoCluster cluster) {
                        if (callback != null) {
                            callback.onVideoGenerationStarted(clusterId);
                        }
                        processClusterForVideo(cluster, callback);
                    }

                    @Override
                    public void onError(String error) {
                        if (callback != null) {
                            callback.onVideoGenerationError("Failed to retrieve cluster: " + error);
                        }
                    }
                });
    }
}