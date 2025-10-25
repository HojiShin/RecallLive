package com.example.recalllive;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Random;

/**
 * Generate test photo data with controlled metadata to test clustering
 */
public class TestDataGenerator {
    private static final String TAG = "TestDataGenerator";

    /**
     * Generate test photos with controlled scenarios
     * Expected result: 5 distinct clusters
     */
    public static List<PhotoData> generateTestPhotos() {
        List<PhotoData> testPhotos = new ArrayList<>();

        // CLUSTER 1: Home Morning - September 13, 9:00-10:00 AM (10 photos)
        double homeLat = 37.4419;
        double homeLon = -122.1430;
        long sep13Morning = getTimestamp(2024, 9, 13, 9, 0);

        for (int i = 0; i < 10; i++) {
            PhotoData photo = new PhotoData("content://media/external/images/media/100" + i);
            photo.setLatitude(homeLat + (Math.random() * 0.0001)); // Slight GPS variation
            photo.setLongitude(homeLon + (Math.random() * 0.0001));
            photo.setDateTaken(sep13Morning + (i * 5 * 60 * 1000)); // 5 min intervals
            photo.setTimeCluster("Morning");
            testPhotos.add(photo);

            Log.d(TAG, "Photo " + i + " - Home Morning: " +
                    new java.util.Date(photo.getDateTaken()));
        }

        // CLUSTER 2: Park Afternoon - September 13, 2:00-3:00 PM (8 photos)
        double parkLat = 37.4425;  // ~70 meters from home
        double parkLon = -122.1425;
        long sep13Afternoon = getTimestamp(2024, 9, 13, 14, 0);

        for (int i = 0; i < 8; i++) {
            PhotoData photo = new PhotoData("content://media/external/images/media/101" + i);
            photo.setLatitude(parkLat + (Math.random() * 0.0001));
            photo.setLongitude(parkLon + (Math.random() * 0.0001));
            photo.setDateTaken(sep13Afternoon + (i * 7 * 60 * 1000)); // 7 min intervals
            photo.setTimeCluster("Afternoon");
            testPhotos.add(photo);

            Log.d(TAG, "Photo " + (10+i) + " - Park Afternoon: " +
                    new java.util.Date(photo.getDateTaken()));
        }

        // CLUSTER 3: Restaurant Evening - September 13, 7:00-8:00 PM (6 photos)
        double restaurantLat = 37.4450;  // >100m from other locations
        double restaurantLon = -122.1460;
        long sep13Evening = getTimestamp(2024, 9, 13, 19, 0);

        for (int i = 0; i < 6; i++) {
            PhotoData photo = new PhotoData("content://media/external/images/media/102" + i);
            photo.setLatitude(restaurantLat + (Math.random() * 0.0001));
            photo.setLongitude(restaurantLon + (Math.random() * 0.0001));
            photo.setDateTaken(sep13Evening + (i * 10 * 60 * 1000)); // 10 min intervals
            photo.setTimeCluster("Evening");
            testPhotos.add(photo);

            Log.d(TAG, "Photo " + (18+i) + " - Restaurant Evening: " +
                    new java.util.Date(photo.getDateTaken()));
        }

        // CLUSTER 4: Different Day - September 7, Night (5 photos)
        long sep7Night = getTimestamp(2024, 9, 7, 22, 0);

        for (int i = 0; i < 5; i++) {
            PhotoData photo = new PhotoData("content://media/external/images/media/103" + i);
            photo.setLatitude(homeLat + (Math.random() * 0.0001));
            photo.setLongitude(homeLon + (Math.random() * 0.0001));
            photo.setDateTaken(sep7Night + (i * 15 * 60 * 1000)); // 15 min intervals
            photo.setTimeCluster("Night");
            testPhotos.add(photo);

            Log.d(TAG, "Photo " + (24+i) + " - Sep 7 Night: " +
                    new java.util.Date(photo.getDateTaken()));
        }

        // CLUSTER 5: Old photos - May 10 (3 photos, no location)
        long may10 = getTimestamp(2024, 5, 10, 15, 0);

        for (int i = 0; i < 3; i++) {
            PhotoData photo = new PhotoData("content://media/external/images/media/104" + i);
            // No location data - simulating screenshots or downloaded images
            photo.setLatitude(0.0);
            photo.setLongitude(0.0);
            photo.setDateTaken(may10 + (i * 30 * 60 * 1000)); // 30 min intervals
            photo.setTimeCluster("Afternoon");
            testPhotos.add(photo);

            Log.d(TAG, "Photo " + (29+i) + " - May 10 No Location: " +
                    new java.util.Date(photo.getDateTaken()));
        }

        // Edge cases to test clustering logic

        // Edge Case 1: Photo just outside location radius (should be separate cluster)
        PhotoData edgePhoto1 = new PhotoData("content://media/external/images/media/105");
        edgePhoto1.setLatitude(homeLat + 0.001); // ~110m from home
        edgePhoto1.setLongitude(homeLon);
        edgePhoto1.setDateTaken(sep13Morning + (30 * 60 * 1000)); // During morning time
        edgePhoto1.setTimeCluster("Morning");
        testPhotos.add(edgePhoto1);
        Log.d(TAG, "Edge Case - Outside radius: " + new java.util.Date(edgePhoto1.getDateTaken()));

        // Edge Case 2: Photo just outside time window (should be separate cluster)
        PhotoData edgePhoto2 = new PhotoData("content://media/external/images/media/106");
        edgePhoto2.setLatitude(homeLat);
        edgePhoto2.setLongitude(homeLon);
        edgePhoto2.setDateTaken(sep13Morning + (4 * 60 * 60 * 1000)); // 4 hours later
        edgePhoto2.setTimeCluster("Afternoon");
        testPhotos.add(edgePhoto2);
        Log.d(TAG, "Edge Case - Outside time window: " + new java.util.Date(edgePhoto2.getDateTaken()));

        return testPhotos;
    }

    /**
     * Create timestamp for specific date/time
     */
    private static long getTimestamp(int year, int month, int day, int hour, int minute) {
        Calendar cal = Calendar.getInstance();
        cal.set(year, month - 1, day, hour, minute, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    /**
     * Test the clustering with generated data
     */
    public static void testClustering(Context context) {
        Log.d(TAG, "=== STARTING CLUSTERING TEST ===");

        // Generate test data
        List<PhotoData> testPhotos = generateTestPhotos();
        Log.d(TAG, "Generated " + testPhotos.size() + " test photos");

        // Run clustering
        PhotoClusteringManager clusteringManager = new PhotoClusteringManager();
        List<PhotoClusteringManager.PhotoCluster> clusters =
                clusteringManager.clusterPhotos(testPhotos);

        // Analyze results
        Log.d(TAG, "=== CLUSTERING RESULTS ===");
        Log.d(TAG, "Total clusters created: " + clusters.size());

        for (int i = 0; i < clusters.size(); i++) {
            PhotoClusteringManager.PhotoCluster cluster = clusters.get(i);
            Log.d(TAG, "Cluster " + (i+1) + ":");
            Log.d(TAG, "  - Photos: " + cluster.getPhotoCount());
            Log.d(TAG, "  - Location: " + cluster.getLocationName());
            Log.d(TAG, "  - Time: " + cluster.getTimeDescription());
            Log.d(TAG, "  - Lat/Lon: " + cluster.getLatitude() + ", " + cluster.getLongitude());
        }

        // Verify expected results
        Log.d(TAG, "=== VERIFICATION ===");
        if (clusters.size() >= 5 && clusters.size() <= 7) {
            Log.d(TAG, "✓ Cluster count is correct (expected 5-7, got " + clusters.size() + ")");
        } else {
            Log.e(TAG, "✗ Unexpected cluster count (expected 5-7, got " + clusters.size() + ")");
        }

        // Check total photos
        int totalPhotos = 0;
        for (PhotoClusteringManager.PhotoCluster cluster : clusters) {
            totalPhotos += cluster.getPhotoCount();
        }

        if (totalPhotos == testPhotos.size()) {
            Log.d(TAG, "✓ All photos accounted for (" + totalPhotos + ")");
        } else {
            Log.e(TAG, "✗ Photo count mismatch (expected " + testPhotos.size() +
                    ", got " + totalPhotos + ")");
        }
    }
}