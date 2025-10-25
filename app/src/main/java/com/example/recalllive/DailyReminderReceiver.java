package com.example.recalllive;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * FIXED: Broadcast Receiver for daily reminder notifications with rescheduling support
 */
public class DailyReminderReceiver extends BroadcastReceiver {
    private static final String TAG = "DailyReminderReceiver";
    private static final String CHANNEL_ID = "daily_reminder_channel";
    private static final int NOTIFICATION_ID = 2001;
    private static final String PREFS_NAME = "RecallLivePrefs";
    private static final String KEY_REMINDER_HOUR = "reminder_hour";
    private static final String KEY_REMINDER_MINUTE = "reminder_minute";
    private static final String KEY_REMINDER_ENABLED = "reminder_enabled";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "═══════════════════════════════════════");
        Log.d(TAG, "Daily reminder triggered!");
        Log.d(TAG, "Time: " + new Date().toString());
        Log.d(TAG, "═══════════════════════════════════════");

        // Log reminder to Firebase
        logReminderToFirebase();

        // Show notification
        showReminderNotification(context);

        // Reschedule for tomorrow (required for Android 12+)
        rescheduleReminder(context);
    }

    private void logReminderToFirebase() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            Log.w(TAG, "No user logged in, skipping Firebase log");
            return;
        }

        String userId = auth.getCurrentUser().getUid();
        DatabaseReference reminderLogRef = FirebaseDatabase.getInstance()
                .getReference()
                .child("Patient")
                .child(userId)
                .child("reminderLog")
                .push();

        String dateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        long timestamp = System.currentTimeMillis();

        Map<String, Object> logData = new HashMap<>();
        logData.put("date", dateStr);
        logData.put("timestamp", timestamp);
        logData.put("type", "daily_reminder");
        logData.put("shown", true);

        reminderLogRef.setValue(logData)
                .addOnSuccessListener(aVoid ->
                        Log.d(TAG, "✓ Reminder logged to Firebase for date: " + dateStr))
                .addOnFailureListener(e ->
                        Log.e(TAG, "✗ Failed to log reminder to Firebase", e));
    }

    private void showReminderNotification(Context context) {
        Intent notificationIntent = new Intent(context, PatientMainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_today)
                .setContentTitle("🎬 Time for Your Daily Memory Video!")
                .setContentText("Tap to watch today's memory video")
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText("Your daily memory video is ready! Tap here to relive your cherished moments."))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setVibrate(new long[]{0, 500, 200, 500})
                .setDefaults(NotificationCompat.DEFAULT_SOUND);

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, builder.build());
            Log.d(TAG, "✓ Reminder notification shown");
        } else {
            Log.e(TAG, "✗ NotificationManager is null");
        }
    }

    /**
     * Reschedule the reminder for tomorrow (required for Android 12+)
     */
    private void rescheduleReminder(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        boolean isEnabled = prefs.getBoolean(KEY_REMINDER_ENABLED, false);
        if (!isEnabled) {
            Log.d(TAG, "Reminder is disabled, not rescheduling");
            return;
        }

        int hour = prefs.getInt(KEY_REMINDER_HOUR, 9);
        int minute = prefs.getInt(KEY_REMINDER_MINUTE, 0);

        Log.d(TAG, "Rescheduling reminder for tomorrow at " + hour + ":" + minute);

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            Log.e(TAG, "✗ AlarmManager is null, cannot reschedule");
            return;
        }

        Intent intent = new Intent(context, DailyReminderReceiver.class);
        intent.setAction("com.example.recalllive.DAILY_REMINDER");

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Set up calendar for tomorrow at the same time
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, 1); // Tomorrow
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            calendar.getTimeInMillis(),
                            pendingIntent
                    );
                    Log.d(TAG, "✓ Reminder rescheduled for: " + calendar.getTime());
                } else {
                    Log.w(TAG, "⚠️ Cannot schedule exact alarms - permission not granted");
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Android 6.0+
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.getTimeInMillis(),
                        pendingIntent
                );
                Log.d(TAG, "✓ Reminder rescheduled for: " + calendar.getTime());
            }
        } catch (Exception e) {
            Log.e(TAG, "✗ Failed to reschedule reminder", e);
        }
    }
}