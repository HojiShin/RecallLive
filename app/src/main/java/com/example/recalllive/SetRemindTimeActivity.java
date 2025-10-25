package com.example.recalllive;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/**
 * FIXED: Complete implementation with working time picker and Firebase storage
 */
public class SetRemindTimeActivity extends AppCompatActivity {
    private static final String TAG = "SetRemindTimeActivity";
    private static final String PREFS_NAME = "RecallLivePrefs";
    private static final String KEY_REMINDER_HOUR = "reminder_hour";
    private static final String KEY_REMINDER_MINUTE = "reminder_minute";
    private static final String KEY_REMINDER_ENABLED = "reminder_enabled";
    private static final String CHANNEL_ID = "daily_reminder_channel";

    private TextView tvHourDisplay;
    private TextView tvMinuteDisplay;
    private TextView tvAm;
    private TextView tvPm;
    private TextView tvCurrentSetting;
    private Button btnCancel;
    private Button btnOk;
    private ImageView ivBack;
    private View timeDisplayContainer;

    private SharedPreferences prefs;
    private DatabaseReference databaseReference;
    private String userId;

    // Current selected time
    private int selectedHour = 9; // 9 AM default
    private int selectedMinute = 0;
    private boolean isPM = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setremindtime);

        // Initialize Firebase
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            userId = auth.getCurrentUser().getUid();
            databaseReference = FirebaseDatabase.getInstance().getReference()
                    .child("Patient")
                    .child(userId)
                    .child("reminderSettings");
        }

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Initialize views
        initializeViews();
        createNotificationChannel();

        // Load saved time if exists
        loadSavedTime();

        // Set up listeners
        setupListeners();
    }

    private void initializeViews() {
        tvHourDisplay = findViewById(R.id.tv_hour_display);
        tvMinuteDisplay = findViewById(R.id.tv_minute_display);
        tvAm = findViewById(R.id.tv_am);
        tvPm = findViewById(R.id.tv_pm);
        tvCurrentSetting = findViewById(R.id.tv_current_setting);
        btnCancel = findViewById(R.id.btn_cancel);
        btnOk = findViewById(R.id.btn_ok);
        ivBack = findViewById(R.id.iv_back);
        timeDisplayContainer = findViewById(R.id.time_display_container);
    }

    private void setupListeners() {
        // Time display - opens time picker
        timeDisplayContainer.setOnClickListener(v -> showTimePicker());

        // AM/PM toggle
        tvAm.setOnClickListener(v -> {
            isPM = false;
            updateAmPmDisplay();
            updateTimeDisplay();
        });

        tvPm.setOnClickListener(v -> {
            isPM = true;
            updateAmPmDisplay();
            updateTimeDisplay();
        });

        // Buttons
        btnCancel.setOnClickListener(v -> finish());
        btnOk.setOnClickListener(v -> saveReminderTime());
        ivBack.setOnClickListener(v -> finish());
    }

    /**
     * Show Android time picker dialog
     */
    private void showTimePicker() {
        // Convert to 24-hour format for picker
        int hour24 = selectedHour;
        if (isPM && selectedHour != 12) {
            hour24 = selectedHour + 12;
        } else if (!isPM && selectedHour == 12) {
            hour24 = 0;
        }

        TimePickerDialog timePickerDialog = new TimePickerDialog(
                this,
                (view, hourOfDay, minute) -> {
                    // Convert back to 12-hour format
                    if (hourOfDay >= 12) {
                        isPM = true;
                        selectedHour = (hourOfDay == 12) ? 12 : hourOfDay - 12;
                    } else {
                        isPM = false;
                        selectedHour = (hourOfDay == 0) ? 12 : hourOfDay;
                    }
                    selectedMinute = minute;

                    updateTimeDisplay();
                    updateAmPmDisplay();
                },
                hour24,
                selectedMinute,
                false // 12-hour format
        );

        timePickerDialog.setTitle("Select Reminder Time");
        timePickerDialog.show();
    }

    private void loadSavedTime() {
        int savedHour = prefs.getInt(KEY_REMINDER_HOUR, 9);
        int savedMinute = prefs.getInt(KEY_REMINDER_MINUTE, 0);
        boolean isEnabled = prefs.getBoolean(KEY_REMINDER_ENABLED, false);

        // Convert 24-hour to 12-hour format
        if (savedHour >= 12) {
            isPM = true;
            selectedHour = (savedHour == 12) ? 12 : savedHour - 12;
        } else {
            isPM = false;
            selectedHour = (savedHour == 0) ? 12 : savedHour;
        }
        selectedMinute = savedMinute;

        updateTimeDisplay();
        updateAmPmDisplay();
        updateCurrentSettingDisplay(isEnabled, selectedHour, selectedMinute, isPM);

        Log.d(TAG, "Loaded saved time: " + savedHour + ":" + savedMinute + " (enabled: " + isEnabled + ")");
    }

    private void updateTimeDisplay() {
        tvHourDisplay.setText(String.format("%02d", selectedHour));
        tvMinuteDisplay.setText(String.format("%02d", selectedMinute));
    }

    private void updateAmPmDisplay() {
        if (isPM) {
            // PM selected
            tvPm.setTextSize(24);
            tvPm.setTextColor(getResources().getColor(android.R.color.black));
            tvPm.setBackgroundResource(android.R.drawable.button_onoff_indicator_on);

            tvAm.setTextSize(20);
            tvAm.setTextColor(getResources().getColor(android.R.color.darker_gray));
            tvAm.setBackgroundResource(android.R.drawable.button_onoff_indicator_off);
        } else {
            // AM selected
            tvAm.setTextSize(24);
            tvAm.setTextColor(getResources().getColor(android.R.color.black));
            tvAm.setBackgroundResource(android.R.drawable.button_onoff_indicator_on);

            tvPm.setTextSize(20);
            tvPm.setTextColor(getResources().getColor(android.R.color.darker_gray));
            tvPm.setBackgroundResource(android.R.drawable.button_onoff_indicator_off);
        }
    }

    private void updateCurrentSettingDisplay(boolean enabled, int hour, int minute, boolean pm) {
        if (enabled) {
            String timeStr = String.format("%d:%02d %s", hour, minute, pm ? "PM" : "AM");
            tvCurrentSetting.setText("Currently: Daily reminder at " + timeStr);
        } else {
            tvCurrentSetting.setText("Currently: Not set");
        }
    }

    private void saveReminderTime() {
        // Convert to 24-hour format
        int hour24 = selectedHour;
        if (isPM && selectedHour != 12) {
            hour24 = selectedHour + 12;
        } else if (!isPM && selectedHour == 12) {
            hour24 = 0;
        }

        Log.d(TAG, "Saving reminder: " + selectedHour + ":" + selectedMinute + " " +
                (isPM ? "PM" : "AM") + " (24h: " + hour24 + ":" + selectedMinute + ")");

        // Save to SharedPreferences
        prefs.edit()
                .putInt(KEY_REMINDER_HOUR, hour24)
                .putInt(KEY_REMINDER_MINUTE, selectedMinute)
                .putBoolean(KEY_REMINDER_ENABLED, true)
                .apply();

        // Save to Firebase
        saveToFirebase(hour24, selectedMinute);

        // Schedule daily reminder
        scheduleDailyReminder(hour24, selectedMinute);

        Toast.makeText(this,
                String.format("✓ Daily reminder set for %d:%02d %s",
                        selectedHour, selectedMinute, isPM ? "PM" : "AM"),
                Toast.LENGTH_LONG).show();

        Log.d(TAG, "Reminder saved and scheduled successfully");

        finish();
    }

    private void saveToFirebase(int hour, int minute) {
        if (databaseReference == null) {
            Log.w(TAG, "Firebase reference is null, cannot save reminder");
            return;
        }

        Map<String, Object> reminderData = new HashMap<>();
        reminderData.put("hour", hour);
        reminderData.put("minute", minute);
        reminderData.put("enabled", true);
        reminderData.put("lastUpdated", System.currentTimeMillis());

        databaseReference.setValue(reminderData)
                .addOnSuccessListener(aVoid ->
                        Log.d(TAG, "✓ Reminder time saved to Firebase"))
                .addOnFailureListener(e ->
                        Log.e(TAG, "✗ Failed to save reminder to Firebase", e));
    }

    private void scheduleDailyReminder(int hour, int minute) {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            Log.e(TAG, "AlarmManager is null");
            Toast.makeText(this, "Failed to schedule reminder", Toast.LENGTH_SHORT).show();
            return;
        }

        // Create explicit broadcast intent
        Intent intent = new Intent(this, DailyReminderReceiver.class);
        intent.setAction("com.example.recalllive.DAILY_REMINDER");

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Set up calendar for the reminder time
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        // If the time has already passed today, schedule for tomorrow
        if (calendar.getTimeInMillis() < System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
            Log.d(TAG, "Time already passed today, scheduling for tomorrow");
        }

        Log.d(TAG, "Scheduling alarm for: " + calendar.getTime());

        try {
            // Cancel any existing alarms
            alarmManager.cancel(pendingIntent);

            // Schedule repeating alarm based on Android version
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ (API 31+)
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            calendar.getTimeInMillis(),
                            pendingIntent
                    );
                    Log.d(TAG, "✓ Exact alarm scheduled (API 31+)");

                    // Note: On API 31+, we need to reschedule after each alarm fires
                    // This is handled in DailyReminderReceiver
                } else {
                    Log.w(TAG, "Exact alarm permission not granted");
                    Toast.makeText(this,
                            "Please enable exact alarm permission in settings",
                            Toast.LENGTH_LONG).show();
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Android 6.0+ (API 23+)
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.getTimeInMillis(),
                        pendingIntent
                );
                Log.d(TAG, "✓ Exact alarm scheduled (API 23+)");
            } else {
                // Below Android 6.0
                alarmManager.setRepeating(
                        AlarmManager.RTC_WAKEUP,
                        calendar.getTimeInMillis(),
                        AlarmManager.INTERVAL_DAY,
                        pendingIntent
                );
                Log.d(TAG, "✓ Repeating alarm scheduled");
            }

        } catch (SecurityException e) {
            Log.e(TAG, "✗ Failed to schedule alarm - permission denied", e);
            Toast.makeText(this,
                    "Please enable alarm permission in settings",
                    Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.e(TAG, "✗ Failed to schedule alarm", e);
            Toast.makeText(this,
                    "Failed to schedule reminder: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Daily Reminders";
            String description = "Daily reminder to view memory videos";
            int importance = NotificationManager.IMPORTANCE_HIGH; // Changed to HIGH for better visibility

            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 500, 200, 500});

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
                Log.d(TAG, "✓ Notification channel created");
            }
        }
    }
}