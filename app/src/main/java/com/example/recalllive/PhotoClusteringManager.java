package com.example.recalllive;

import android.location.Location;
import android.util.Log;

import com.example.recalllive.PhotoData;
import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.clustering.algo.NonHierarchicalDistanceBasedAlgorithm;
import com.google.maps.android.clustering.ClusterItem;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class PhotoClusteringManager {
    private static final String TAG = "PhotoClusteringManager";

    // Clustering parameters
    private static final double LOCATION_CLUSTER_RADIUS_METERS = 100.0; // 100 meters radius for location clustering
    private static final long TIME_CLUSTER_WINDOW_MILLIS = 3 * 60 * 60 * 1000; // 3 hours for time clustering

    /**
     * Cluster photos by both time and location
     */
    public List<PhotoCluster> clusterPhotos(List<PhotoData> photos) {
        Map<String, PhotoCluster> clusters = new HashMap<>();

        // First, cluster by location
        Map<String, List<PhotoData>> locationClusters = clusterByLocation(photos);

        // Then, within each location cluster, cluster by time
        for (Map.Entry<String, List<PhotoData>> entry : locationClusters.entrySet()) {
            String locationId = entry.getKey();
            List<PhotoData> locationPhotos = entry.getValue();

            Map<String, List<PhotoData>> timeClusters = clusterByTime(locationPhotos);

            for (Map.Entry<String, List<PhotoData>> timeEntry : timeClusters.entrySet()) {
                String timeId = timeEntry.getKey();
                List<PhotoData> clusterPhotos = timeEntry.getValue();

                String clusterId = locationId + "_" + timeId;
                PhotoCluster cluster = new PhotoCluster(clusterId);
                cluster.setPhotos(clusterPhotos);

                // Update cluster ID in each photo
                for (PhotoData photo : clusterPhotos) {
                    photo.setClusterId(clusterId);
                }

                // Set cluster metadata
                if (!clusterPhotos.isEmpty()) {
                    PhotoData firstPhoto = clusterPhotos.get(0);
                    cluster.setLatitude(firstPhoto.getLatitude());
                    cluster.setLongitude(firstPhoto.getLongitude());
                    cluster.setStartTime(getEarliestTime(clusterPhotos));
                    cluster.setEndTime(getLatestTime(clusterPhotos));
                    cluster.setLocationName(generateLocationName(firstPhoto));
                    cluster.setTimeDescription(generateTimeDescription(cluster.getStartTime(), cluster.getEndTime()));
                }

                clusters.put(clusterId, cluster);
            }
        }

        return new ArrayList<>(clusters.values());
    }

    /**
     * Cluster photos by location using DBSCAN-like algorithm
     */
    private Map<String, List<PhotoData>> clusterByLocation(List<PhotoData> photos) {
        Map<String, List<PhotoData>> clusters = new HashMap<>();
        List<PhotoData> visited = new ArrayList<>();

        for (PhotoData photo : photos) {
            if (visited.contains(photo)) {
                continue;
            }

            visited.add(photo);
            List<PhotoData> neighbors = getLocationNeighbors(photo, photos);

            if (neighbors.size() > 0) {
                String clusterId = UUID.randomUUID().toString();
                List<PhotoData> cluster = new ArrayList<>();
                cluster.add(photo);
                cluster.addAll(neighbors);

                // Mark all neighbors as visited
                visited.addAll(neighbors);

                // Set location name for all photos in cluster
                String locationName = generateLocationName(photo);
                for (PhotoData p : cluster) {
                    p.setLocationName(locationName);
                }

                clusters.put(clusterId, cluster);
            } else {
                // Photo doesn't belong to any location cluster
                String clusterId = "no_location_" + UUID.randomUUID().toString();
                List<PhotoData> cluster = new ArrayList<>();
                cluster.add(photo);
                clusters.put(clusterId, cluster);
            }
        }

        return clusters;
    }

    /**
     * Find photos within location radius
     */
    private List<PhotoData> getLocationNeighbors(PhotoData centerPhoto, List<PhotoData> allPhotos) {
        List<PhotoData> neighbors = new ArrayList<>();

        if (!centerPhoto.hasLocation()) {
            return neighbors;
        }

        for (PhotoData photo : allPhotos) {
            if (photo.equals(centerPhoto) || !photo.hasLocation()) {
                continue;
            }

            float distance = calculateDistance(
                    centerPhoto.getLatitude(), centerPhoto.getLongitude(),
                    photo.getLatitude(), photo.getLongitude()
            );

            if (distance <= LOCATION_CLUSTER_RADIUS_METERS) {
                neighbors.add(photo);
            }
        }

        return neighbors;
    }

    /**
     * Cluster photos by time
     */
    private Map<String, List<PhotoData>> clusterByTime(List<PhotoData> photos) {
        Map<String, List<PhotoData>> clusters = new HashMap<>();
        List<PhotoData> sorted = new ArrayList<>(photos);

        // Sort by date taken
        sorted.sort((a, b) -> Long.compare(a.getDateTaken(), b.getDateTaken()));

        List<PhotoData> currentCluster = new ArrayList<>();
        long lastTime = 0;

        for (PhotoData photo : sorted) {
            if (currentCluster.isEmpty()) {
                currentCluster.add(photo);
                lastTime = photo.getDateTaken();
            } else {
                long timeDiff = photo.getDateTaken() - lastTime;

                if (timeDiff <= TIME_CLUSTER_WINDOW_MILLIS) {
                    currentCluster.add(photo);
                    lastTime = photo.getDateTaken();
                } else {
                    // Start new cluster
                    String clusterId = "time_" + UUID.randomUUID().toString();
                    clusters.put(clusterId, new ArrayList<>(currentCluster));

                    currentCluster.clear();
                    currentCluster.add(photo);
                    lastTime = photo.getDateTaken();
                }
            }
        }

        // Add last cluster
        if (!currentCluster.isEmpty()) {
            String clusterId = "time_" + UUID.randomUUID().toString();
            clusters.put(clusterId, currentCluster);
        }

        return clusters;
    }

    /**
     * Calculate distance between two GPS coordinates in meters
     */
    private float calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        Location loc1 = new Location("");
        loc1.setLatitude(lat1);
        loc1.setLongitude(lon1);

        Location loc2 = new Location("");
        loc2.setLatitude(lat2);
        loc2.setLongitude(lon2);

        return loc1.distanceTo(loc2);
    }

    /**
     * Generate friendly location name
     */
    private String generateLocationName(PhotoData photo) {
        if (!photo.hasLocation()) {
            return "Unknown Location";
        }

        // In a real app, you would use Geocoding API to get actual address
        // For now, return a generic name based on coordinates
        return String.format(Locale.US, "Location (%.2f, %.2f)",
                photo.getLatitude(), photo.getLongitude());
    }

    /**
     * Generate time description for cluster
     */
    private String generateTimeDescription(long startTime, long endTime) {
        Calendar startCal = Calendar.getInstance();
        startCal.setTimeInMillis(startTime);

        Calendar endCal = Calendar.getInstance();
        endCal.setTimeInMillis(endTime);

        if (isSameDay(startCal, endCal)) {
            return String.format(Locale.US, "%1$tB %1$td, %1$tY", startCal);
        } else {
            return String.format(Locale.US, "%1$tB %1$td - %2$tB %2$td, %2$tY",
                    startCal, endCal);
        }
    }

    private boolean isSameDay(Calendar cal1, Calendar cal2) {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }

    private long getEarliestTime(List<PhotoData> photos) {
        long earliest = Long.MAX_VALUE;
        for (PhotoData photo : photos) {
            if (photo.getDateTaken() < earliest) {
                earliest = photo.getDateTaken();
            }
        }
        return earliest;
    }

    private long getLatestTime(List<PhotoData> photos) {
        long latest = Long.MIN_VALUE;
        for (PhotoData photo : photos) {
            if (photo.getDateTaken() > latest) {
                latest = photo.getDateTaken();
            }
        }
        return latest;
    }

    /**
     * Photo Cluster class
     */
    public static class PhotoCluster {
        private String clusterId;
        private List<PhotoData> photos;
        private double latitude;
        private double longitude;
        private long startTime;
        private long endTime;
        private String locationName;
        private String timeDescription;

        public PhotoCluster(String clusterId) {
            this.clusterId = clusterId;
            this.photos = new ArrayList<>();
        }

        // Getters and Setters
        public String getClusterId() {
            return clusterId;
        }

        public void setClusterId(String clusterId) {
            this.clusterId = clusterId;
        }

        public List<PhotoData> getPhotos() {
            return photos;
        }

        public void setPhotos(List<PhotoData> photos) {
            this.photos = photos;
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
            return photos != null ? photos.size() : 0;
        }
    }
}