package com.example.recalllive;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

/**
 * Dialog for obtaining explicit consent for facial expression monitoring
 * CRITICAL: Must be shown before any monitoring begins
 */
public class ExpressionConsentDialog {
    private static final String PREFS_NAME = "ExpressionMonitoring";
    private static final String KEY_CONSENT_GIVEN = "consent_given";
    private static final String KEY_CONSENT_TIMESTAMP = "consent_timestamp";

    private final Context context;
    private final String patientUid;

    public interface ConsentCallback {
        void onConsentGiven();
        void onConsentDenied();
    }

    public ExpressionConsentDialog(Context context, String patientUid) {
        this.context = context;
        this.patientUid = patientUid;
    }

    /**
     * Check if user has already given consent
     */
    public boolean hasConsent() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_CONSENT_GIVEN + "_" + patientUid, false);
    }

    /**
     * Show consent dialog
     */
    public void showConsentDialog(ConsentCallback callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);

        // Inflate custom layout
        View dialogView = LayoutInflater.from(context).inflate(
                android.R.layout.simple_list_item_2, null);

        AlertDialog dialog = builder
                .setTitle("Facial Expression Monitoring")
                .setMessage(getConsentMessage())
                .setPositiveButton("I Agree", (d, which) -> {
                    saveConsent(true);
                    if (callback != null) {
                        callback.onConsentGiven();
                    }
                })
                .setNegativeButton("No Thanks", (d, which) -> {
                    saveConsent(false);
                    if (callback != null) {
                        callback.onConsentDenied();
                    }
                })
                .setCancelable(false)
                .create();

        dialog.show();
    }

    /**
     * Get detailed consent message
     */
    private String getConsentMessage() {
        return "RecallLive can analyze your facial expressions while you watch memory videos " +
                "to help your guardian understand how you're responding to the content.\n\n" +

                "What This Means:\n" +
                "• Your front camera will be used during video playback\n" +
                "• A small indicator will show when monitoring is active\n" +
                "• Only emotion percentages are shared (Happy: 60%, Neutral: 30%, etc.)\n" +
                "• No photos or videos of you are saved or shared\n" +
                "• Data is only visible to your linked guardian\n\n" +

                "Your Rights:\n" +
                "• You can disable this feature at any time in Settings\n" +
                "• You can view what data is collected\n" +
                "• You can request deletion of all collected data\n\n" +

                "Do you consent to facial expression monitoring?";
    }

    /**
     * Save consent decision
     */
    private void saveConsent(boolean granted) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putBoolean(KEY_CONSENT_GIVEN + "_" + patientUid, granted)
                .putLong(KEY_CONSENT_TIMESTAMP + "_" + patientUid, System.currentTimeMillis())
                .apply();
    }

    /**
     * Revoke consent (for settings page)
     */
    public void revokeConsent() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putBoolean(KEY_CONSENT_GIVEN + "_" + patientUid, false)
                .apply();
    }
}