package com.example.recalllive;

import android.content.Context;
import android.util.Log;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Debug helper to test video generation manually
 * Use this to verify your video generation pipeline works
 */
public class VideoGenerationDebugger {
    private static final String TAG = "VideoGenDebug";

    private final Context context;

    public VideoGenerationDebugger(Context context) {
        this.context = context;
    }

    /**
     * Test the complete video generation pipeline
     * Call this from your main activity to debug
     */
    public void testVideoGeneration() {
        Log.d(TAG, "=== Starting Video Generation Debug ===");

        // Step 1: Check Firebase Auth
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.e(TAG, "❌ User not logged in - video generation won't work");
            return;
        }

        String patientUid = user.getUid();
        Log.d(TAG, "✅ User logged in: " + patientUid);

        // Step 2: Test cluster retrieval
        Log.d(TAG, "📂 Testing cluster retrieval...");
        FirebaseClusterManager clusterManager = new FirebaseClusterManager(context, patientUid);

        clusterManager.getClusters(new FirebaseClusterManager.OnClustersRetrievedCallback() {
            @Override
            public void onClustersRetrieved(java.util.List<PhotoClusteringManager.PhotoCluster> clusters) {
                Log.d(TAG, "✅ Retrieved " + clusters.size() + " clusters");

                if (clusters.isEmpty()) {
                    Log.e(TAG, "❌ No clusters found - need to run photo clustering first");
                    // Try to run clustering
                    runPhotoClustering(patientUid);
                    return;
                }

                // Test video generation with first cluster
                PhotoClusteringManager.PhotoCluster testCluster = clusters.get(0);
                Log.d(TAG, "🎬 Testing video generation with cluster: " + testCluster.getClusterId());
                testVideoGenerationWithCluster(testCluster, patientUid);
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "❌ Failed to retrieve clusters: " + error);
                // Clusters might not exist yet - try creating them
                runPhotoClustering(patientUid);
            }
        });
    }

    /**
     * Test photo clustering first
     */
    private void runPhotoClustering(String patientUid) {
        Log.d(TAG, "📸 Running photo clustering first...");

        PhotoProcessingService processingService = new PhotoProcessingService(context, patientUid);

        processingService.processAllPhotos(new PhotoProcessingService.ProcessingCallback() {
            @Override
            public void onProcessingStarted() {
                Log.d(TAG, "✅ Photo processing started");
            }

            @Override
            public void onProgressUpdate(int processed, int total) {
                Log.d(TAG, "📊 Processing progress: " + processed + "/" + total);
            }

            @Override
            public void onProcessingComplete(java.util.List<PhotoClusteringManager.PhotoCluster> clusters) {
                Log.d(TAG, "✅ Photo processing complete! Created " + clusters.size() + " clusters");

                if (clusters != null && !clusters.isEmpty()) {
                    // Now test video generation
                    PhotoClusteringManager.PhotoCluster testCluster = clusters.get(0);
                    testVideoGenerationWithCluster(testCluster, patientUid);
                } else {
                    Log.e(TAG, "❌ No clusters created - check if device has photos");
                }
            }

            @Override
            public void onProcessingError(String error) {
                Log.e(TAG, "❌ Photo processing failed: " + error);
            }
        });
    }

    /**
     * Test video generation with a specific cluster
     */
    private void testVideoGenerationWithCluster(PhotoClusteringManager.PhotoCluster cluster, String patientUid) {
        Log.d(TAG, "🎬 Testing video generation...");
        Log.d(TAG, "   Cluster ID: " + cluster.getClusterId());
        Log.d(TAG, "   Photo count: " + cluster.getPhotoCount());
        Log.d(TAG, "   Location: " + cluster.getLocationName());

        if (cluster.getPhotos() == null || cluster.getPhotos().isEmpty()) {
            Log.e(TAG, "❌ Cluster has no photos");
            return;
        }

        // Log first few photo URIs for debugging
        for (int i = 0; i < Math.min(3, cluster.getPhotos().size()); i++) {
            PhotoData photo = cluster.getPhotos().get(i);
            Log.d(TAG, "   Photo " + i + ": " + photo.getPhotoUri());
        }

        Media3VideoGenerator generator = new Media3VideoGenerator(context, patientUid);

        generator.generateVideoFromCluster(cluster, new Media3VideoGenerator.VideoGenerationCallback() {
            @Override
            public void onSuccess(String videoUrl, String documentId) {
                Log.d(TAG, "🎉 SUCCESS! Video generation completed");
                Log.d(TAG, "   Document ID: " + documentId);
                Log.d(TAG, "   Video URL: " + videoUrl);
                Log.d(TAG, "   Check Firestore collection 'memory_videos' for the entry");
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "❌ Video generation failed: " + error);
            }
        });
    }

    /**
     * Check if automatic video service is set up correctly
     */
    public void checkAutomaticVideoService() {
        Log.d(TAG, "=== Checking Automatic Video Service ===");

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.e(TAG, "❌ User not logged in");
            return;
        }

        AutomaticVideoService videoService = new AutomaticVideoService(context);

        // This should be called after patient login
        Log.d(TAG, "🔄 Initializing automatic video service...");
        videoService.initializeForPatient(user.getUid(), false);

        Log.d(TAG, "✅ Automatic video service initialized");
        Log.d(TAG, "   This should have triggered video generation");
        Log.d(TAG, "   Check logs above for video generation results");
    }

    /**
     * Test Firestore connection directly
     */
    public void testFirestoreConnection() {
        Log.d(TAG, "=== Testing Firestore Connection ===");

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.e(TAG, "❌ User not logged in");
            return;
        }

        // Test writing to Firestore directly
        java.util.Map<String, Object> testData = new java.util.HashMap<>();
        testData.put("test", "debug_entry");
        testData.put("patientUid", user.getUid());
        testData.put("timestamp", com.google.firebase.firestore.FieldValue.serverTimestamp());

        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("debug_test")
                .add(testData)
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "✅ Firestore connection working - test document created: " + documentReference.getId());
                    Log.d(TAG, "   You should see this in Firestore console under 'debug_test' collection");

                    // Clean up test document
                    documentReference.delete();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Firestore connection failed: " + e.getMessage());
                });
    }
}