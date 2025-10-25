package com.example.recalllive;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import java.util.Calendar;

/**
 * Restores daily reminder alarms after device reboot
 */
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";
    private static final String PREFS_NAME = "RecallLivePrefs";
    private static final String KEY_REMINDER_HOUR = "reminder_hour";
    private static final String KEY_REMINDER_MINUTE = "reminder_minute";
    private static final String KEY_REMINDER_ENABLED = "reminder_enabled";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) ||
                "android.intent.action.QUICKBOOT_POWERON".equals(intent.getAction())) {

            Log.d(TAG, "═══════════════════════════════════════");
            Log.d(TAG, "Device booted - restoring reminders");
            Log.d(TAG, "═══════════════════════════════════════");

            restoreReminder(context);
        }
    }

    private void restoreReminder(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        boolean isEnabled = prefs.getBoolean(KEY_REMINDER_ENABLED, false);
        if (!isEnabled) {
            Log.d(TAG, "No reminder enabled, skipping restoration");
            return;
        }

        int hour = prefs.getInt(KEY_REMINDER_HOUR, 9);
        int minute = prefs.getInt(KEY_REMINDER_MINUTE, 0);

        Log.d(TAG, "Restoring reminder for " + hour + ":" + minute);

        scheduleReminder(context, hour, minute);
    }

    private void scheduleReminder(Context context, int hour, int minute) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            Log.e(TAG, "✗ AlarmManager is null");
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

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        // If time has passed today, schedule for tomorrow
        if (calendar.getTimeInMillis() < System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            calendar.getTimeInMillis(),
                            pendingIntent
                    );
                    Log.d(TAG, "✓ Reminder restored for: " + calendar.getTime());
                } else {
                    Log.w(TAG, "⚠️ Cannot schedule exact alarms");
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.getTimeInMillis(),
                        pendingIntent
                );
                Log.d(TAG, "✓ Reminder restored for: " + calendar.getTime());
            } else {
                alarmManager.setRepeating(
                        AlarmManager.RTC_WAKEUP,
                        calendar.getTimeInMillis(),
                        AlarmManager.INTERVAL_DAY,
                        pendingIntent
                );
                Log.d(TAG, "✓ Reminder restored for: " + calendar.getTime());
            }
        } catch (Exception e) {
            Log.e(TAG, "✗ Failed to restore reminder", e);
        }
    }
}