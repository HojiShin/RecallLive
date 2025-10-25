package com.example.recalllive;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.util.Log;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Service to convert GPS coordinates to human-readable location names
 */
public class LocationGeocoderService {
    private static final String TAG = "LocationGeocoder";

    private final Context context;
    private final Geocoder geocoder;

    public interface GeocodeCallback {
        void onLocationResolved(String locationName);
        void onError(String error);
    }

    public LocationGeocoderService(Context context) {
        this.context = context;

        // Check if Geocoder is available before initializing
        if (Geocoder.isPresent()) {
            this.geocoder = new Geocoder(context, Locale.getDefault());
            Log.d(TAG, "Geocoder initialized successfully");
        } else {
            this.geocoder = null;
            Log.w(TAG, "Geocoder not available on this device");
        }
    }

    /**
     * Get location name from coordinates
     */
    public void getLocationName(double latitude, double longitude, GeocodeCallback callback) {
        if (geocoder == null || !Geocoder.isPresent()) {
            Log.w(TAG, "Geocoder not available");
            if (callback != null) {
                callback.onLocationResolved(formatFallbackLocation(latitude, longitude));
            }
            return;
        }

        new Thread(() -> {
            try {
                // Add retry logic for DeadObjectException
                List<Address> addresses = null;
                int retries = 3;

                while (retries > 0 && addresses == null) {
                    try {
                        addresses = geocoder.getFromLocation(latitude, longitude, 1);
                        break;
                    } catch (IOException e) {
                        if (e.getMessage() != null && e.getMessage().contains("DeadObjectException")) {
                            retries--;
                            if (retries > 0) {
                                Thread.sleep(500); // Wait before retry
                                Log.w(TAG, "Geocoding failed, retrying... (" + retries + " left)");
                            }
                        } else {
                            throw e;
                        }
                    }
                }

                if (addresses != null && !addresses.isEmpty()) {
                    Address address = addresses.get(0);
                    String locationName = formatLocationName(address);

                    Log.d(TAG, "Resolved location: " + locationName +
                            " for coords (" + latitude + ", " + longitude + ")");

                    if (callback != null) {
                        callback.onLocationResolved(locationName);
                    }
                } else {
                    Log.w(TAG, "No address found for coordinates");
                    if (callback != null) {
                        callback.onLocationResolved(
                                formatFallbackLocation(latitude, longitude));
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "Geocoding failed", e);
                if (callback != null) {
                    // Use fallback location instead of error
                    callback.onLocationResolved(
                            formatFallbackLocation(latitude, longitude));
                }
            }
        }).start();
    }

    /**
     * Format address into friendly location name
     */
    private String formatLocationName(Address address) {
        // Priority order for location naming:
        // 1. Feature name (e.g., "Central Park", "Starbucks")
        // 2. Locality (city)
        // 3. Sub-locality (neighborhood)
        // 4. Admin area (state/province)

        String featureName = address.getFeatureName();
        String locality = address.getLocality();
        String subLocality = address.getSubLocality();
        String adminArea = address.getAdminArea();

        StringBuilder locationName = new StringBuilder();

        // Use feature name if it's meaningful (not just a street number)
        if (featureName != null && !featureName.matches("\\d+")) {
            locationName.append(featureName);

            // Add city if available and different from feature
            if (locality != null && !locality.equals(featureName)) {
                locationName.append(", ").append(locality);
            }
        } else if (subLocality != null) {
            // Use neighborhood
            locationName.append(subLocality);
            if (locality != null) {
                locationName.append(", ").append(locality);
            }
        } else if (locality != null) {
            // Use city
            locationName.append(locality);
            if (adminArea != null) {
                locationName.append(", ").append(adminArea);
            }
        } else if (adminArea != null) {
            // Last resort: state/province
            locationName.append(adminArea);
        } else {
            // Very last resort: country
            String country = address.getCountryName();
            if (country != null) {
                locationName.append(country);
            } else {
                return "Unknown Location";
            }
        }

        return locationName.toString();
    }

    /**
     * Format fallback location name when geocoding fails
     */
    private String formatFallbackLocation(double latitude, double longitude) {
        // Provide a more friendly fallback than raw coordinates
        String latDirection = latitude >= 0 ? "N" : "S";
        String lonDirection = longitude >= 0 ? "E" : "W";

        return String.format(Locale.US, "Location %.2f°%s, %.2f°%s",
                Math.abs(latitude), latDirection,
                Math.abs(longitude), lonDirection);
    }

    /**
     * Batch geocode multiple clusters
     */
    public void geocodeClusters(List<PhotoClusteringManager.PhotoCluster> clusters,
                                ClusterGeocodeCallback callback) {
        new Thread(() -> {
            int total = clusters.size();
            int processed = 0;

            for (PhotoClusteringManager.PhotoCluster cluster : clusters) {
                // Check if cluster has valid location data
                if (cluster.getLatitude() != 0.0 || cluster.getLongitude() != 0.0) {
                    getLocationName(
                            cluster.getLatitude(),
                            cluster.getLongitude(),
                            new GeocodeCallback() {
                                @Override
                                public void onLocationResolved(String locationName) {
                                    cluster.setLocationName(locationName);
                                }

                                @Override
                                public void onError(String error) {
                                    Log.w(TAG, "Geocoding error for cluster: " + error);
                                    // Set fallback location on error
                                    cluster.setLocationName(formatFallbackLocation(
                                            cluster.getLatitude(),
                                            cluster.getLongitude()));
                                }
                            }
                    );

                    // Longer delay to avoid rate limiting and DeadObjectException
                    try {
                        Thread.sleep(300); // Increased from 100ms to 300ms
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                } else {
                    // No location data
                    cluster.setLocationName("Unknown Location");
                }

                processed++;
                if (callback != null) {
                    callback.onProgress(processed, total);
                }
            }

            if (callback != null) {
                callback.onComplete(clusters);
            }
        }).start();
    }

    public interface ClusterGeocodeCallback {
        void onProgress(int processed, int total);
        void onComplete(List<PhotoClusteringManager.PhotoCluster> clusters);
    }
}