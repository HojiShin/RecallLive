package com.example.recalllive;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Calendar;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Automatic clustering service that runs for PATIENT accounts only
 */
public class AutoClusteringService {

    // MAKE IT SO IT DOESNT AUTO FALL BACK ONTO CURRENT DATE
    private static final String TAG = "AutoClusteringService";
    private static final String PREFS_NAME = "RecallLivePrefs";
    private static final String KEY_LAST_CLUSTER_TIME = "last_cluster_time";
    private static final String KEY_USER_TYPE = "user_type";
    private static final String CHANNEL_ID = "photo_clustering_channel";
    private static final int NOTIFICATION_ID = 1001;

    // Clustering intervals
    private static final long INITIAL_DELAY_MILLIS = 30 * 1000; // 30 seconds after first login
    private static final long DAILY_INTERVAL_MILLIS = 24 * 60 * 60 * 1000; // Daily
    private static final long WEEKLY_INTERVAL_MILLIS = 7 * 24 * 60 * 60 * 1000; // Weekly

    private final Context context;
    private final SharedPreferences prefs;

    public AutoClusteringService(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Initialize automatic clustering after patient login
     */
    public void initializeForPatient(String patientUid) {
        Log.d(TAG, "Initializing auto-clustering for patient: " + patientUid);

        // Store user type
        prefs.edit().putString(KEY_USER_TYPE, "patient").apply();

        // Check if this is first time setup
        checkAndStartClustering(patientUid);

        // Schedule periodic clustering using WorkManager
        schedulePeriodicClustering();
    }

    /**
     * Check if clustering is needed and start if necessary
     */
    private void checkAndStartClustering(String patientUid) {
        DatabaseReference clusterRef = FirebaseDatabase.getInstance().getReference()
                .child("Patient")
                .child(patientUid)
                .child("clusterSummary");

        clusterRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    // First time - no clusters exist
                    Log.d(TAG, "No existing clusters found. Starting initial clustering...");
                    performClustering(patientUid, true);
                } else {
                    // Check last update time
                    Long lastUpdated = snapshot.child("lastUpdated").getValue(Long.class);
                    if (shouldUpdateClusters(lastUpdated)) {
                        Log.d(TAG, "Clusters are outdated. Starting update...");
                        performClustering(patientUid, false);
                    } else {
                        Log.d(TAG, "Clusters are up to date");
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e(TAG, "Failed to check cluster status: " + error.getMessage());
            }
        });
    }

    /**
     * Determine if clusters need updating
     */
    private boolean shouldUpdateClusters(Long lastUpdated) {
        if (lastUpdated == null) return true;

        long daysSinceUpdate = (System.currentTimeMillis() - lastUpdated) / (24 * 60 * 60 * 1000);

        // Update if older than 7 days
        return daysSinceUpdate >= 7;
    }

    /**
     * Perform the actual clustering
     */
    private void performClustering(String patientUid, boolean isInitial) {
        Log.d(TAG, "Starting clustering process...");

        // Show notification
        showClusteringNotification("Processing your photos...");

        // Create processing service
        PhotoProcessingService processingService = new PhotoProcessingService(context, patientUid);

        processingService.processAllPhotos(new PhotoProcessingService.ProcessingCallback() {
            @Override
            public void onProcessingStarted() {
                Log.d(TAG, "Clustering started");
                updateNotification("Analyzing your photo memories...");
            }

            @Override
            public void onProgressUpdate(int processed, int total) {
                String message = String.format("Processing photos: %d/%d", processed, total);
                updateNotification(message);
            }

            @Override
            public void onProcessingComplete(List<PhotoClusteringManager.PhotoCluster> clusters) {
                Log.d(TAG, "Clustering complete! Created " + clusters.size() + " clusters");

                // Update last cluster time
                prefs.edit().putLong(KEY_LAST_CLUSTER_TIME, System.currentTimeMillis()).apply();

                // Show completion notification
                showCompletionNotification("Photo organization complete!",
                        "Created " + clusters.size() + " memory clusters");

                // If initial setup, notify that daily videos are ready
                if (isInitial) {
                    notifyVideosReady();
                }
            }

            @Override
            public void onProcessingError(String error) {
                Log.e(TAG, "Clustering failed: " + error);
                showErrorNotification("Failed to organize photos", error);
            }
        });
    }

    /**
     * Schedule periodic clustering using WorkManager
     */
    private void schedulePeriodicClustering() {
        // Build constraints - only run when connected to network and not low battery
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build();

        // Create periodic work request (runs daily)
        PeriodicWorkRequest clusteringWork = new PeriodicWorkRequest.Builder(
                ClusteringWorker.class,
                24, TimeUnit.HOURS) // Run daily
                .setConstraints(constraints)
                .setInitialDelay(24, TimeUnit.HOURS) // First run after 24 hours
                .addTag("photo_clustering")
                .build();

        // Schedule the work
        WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                        "photo_clustering_work",
                        ExistingPeriodicWorkPolicy.REPLACE,
                        clusteringWork
                );

        Log.d(TAG, "Scheduled periodic clustering");
    }

    /**
     * Create notification channel (required for Android O+)
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Photo Clustering",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Notifications about photo organization");

            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * Show clustering progress notification
     */
    private void showClusteringNotification(String message) {
        createNotificationChannel();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_gallery)
                .setContentTitle("Organizing Photos")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true) // Can't be dismissed
                .setProgress(100, 0, true); // Indeterminate progress

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, builder.build());
        } else {
            Log.e(TAG, "NotificationManager is null, cannot show notification");
        }
    }

    /**
     * Update notification with progress
     */
    private void updateNotification(String message) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_gallery)
                .setContentTitle("Organizing Photos")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, builder.build());
        } else {
            Log.e(TAG, "NotificationManager is null, cannot update notification");
        }
    }

    /**
     * Show completion notification
     */
    private void showCompletionNotification(String title, String message) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_gallery)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID + 1, builder.build());
            // Cancel progress notification
            manager.cancel(NOTIFICATION_ID);
        } else {
            Log.e(TAG, "NotificationManager is null, cannot show completion notification");
        }
    }

    /**
     * Show error notification
     */
    private void showErrorNotification(String title, String message) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID + 2, builder.build());
            // Cancel progress notification
            manager.cancel(NOTIFICATION_ID);
        } else {
            Log.e(TAG, "NotificationManager is null, cannot show error notification");
        }
    }

    /**
     * Notify that videos are ready (for initial setup)
     */
    private void notifyVideosReady() {
        // This would trigger your video generation logic
        Log.d(TAG, "Photos are organized. Ready for video generation!");
    }

    /**
     * Worker class for periodic clustering
     */
    public static class ClusteringWorker extends Worker {

        public ClusteringWorker(Context context, WorkerParameters params) {
            super(context, params);
        }

        @Override
        public Result doWork() {
            Log.d(TAG, "ClusteringWorker started");

            // Check if user is logged in as patient
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) {
                Log.d(TAG, "No user logged in, skipping clustering");
                return Result.success();
            }

            // Check user type
            SharedPreferences prefs = getApplicationContext()
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String userType = prefs.getString(KEY_USER_TYPE, "");

            if (!"patient".equals(userType)) {
                Log.d(TAG, "User is not a patient, skipping clustering");
                return Result.success();
            }

            // Perform clustering
            AutoClusteringService service = new AutoClusteringService(getApplicationContext());
            service.performClustering(user.getUid(), false);

            return Result.success();
        }
    }

    /**
     * Stop automatic clustering (when patient logs out)
     */
    public void stopAutoClustering() {
        // Cancel scheduled work
        WorkManager.getInstance(context).cancelAllWorkByTag("photo_clustering");

        // Clear user type
        prefs.edit().remove(KEY_USER_TYPE).apply();

        Log.d(TAG, "Stopped automatic clustering");
    }
}