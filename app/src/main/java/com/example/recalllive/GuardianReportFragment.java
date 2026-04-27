package com.example.recalllive;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.android.material.chip.Chip;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * FIXED: AI-Powered Report Fragment with proper Gemini integration
 */
public class GuardianReportFragment extends Fragment {

    private static final String TAG = "GuardianReportFragment";
    private static final String ARG_PATIENT_UID = "patient_uid";

    // FIXED: Use correct model name
    private static final String MODEL_NAME = "gemini-1.5-flash";

    private String linkedPatientUid;
    private String guardianUid;

    // UI Components
    private ImageButton btnBack;
    private TextView tvGeneratedDate;
    private TextView tvModelName;
    private TextView tvStatVideos;
    private TextView tvStatEmotions;
    private TextView tvStatPositive;
    private Chip chipStatus;
    private View layoutLoading;
    private TextView tvLoadingMessage;
    private ScrollView scrollReportContent;
    private TextView tvReportContent;
    private Button btnShareReport;
    private Button btnExportPdf;
    private Button btnRegenerate;

    // Services
    private GenerativeModelFutures model;
    private Executor executor;
    private EmotionData emotionData;

    public static GuardianReportFragment newInstance(String patientUid) {
        GuardianReportFragment fragment = new GuardianReportFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PATIENT_UID, patientUid);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            linkedPatientUid = getArguments().getString(ARG_PATIENT_UID);
        }

        if (linkedPatientUid == null) {
            linkedPatientUid = requireContext().getSharedPreferences("RecallLivePrefs",
                    Context.MODE_PRIVATE).getString("linked_patient_uid", null);
        }

        guardianUid = requireContext().getSharedPreferences("RecallLivePrefs",
                Context.MODE_PRIVATE).getString("guardian_uid", null);

        // FIXED: Initialize Gemini with error checking
        executor = Executors.newSingleThreadExecutor();

        try {
            String apiKey = BuildConfig.API_KEY;

            if (apiKey == null || apiKey.isEmpty() || apiKey.equals("YOUR_API_KEY_HERE")) {
                Log.e(TAG, "❌ GEMINI API KEY NOT CONFIGURED!");
                throw new IllegalStateException("Gemini API key not configured");
            }

            GenerativeModel gm = new GenerativeModel(MODEL_NAME, apiKey);
            model = GenerativeModelFutures.from(gm);

            Log.d(TAG, "✅ Gemini initialized: " + MODEL_NAME);
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to initialize Gemini", e);
            model = null;
        }

        Log.d(TAG, "GuardianReportFragment created");
        Log.d(TAG, "Patient UID: " + linkedPatientUid);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_guardian_report, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initializeViews(view);
        setupListeners();

        String currentDate = new SimpleDateFormat("MMMM dd, yyyy", Locale.US).format(new Date());
        tvGeneratedDate.setText("Generated: " + currentDate);
        tvModelName.setText("Gemini 1.5 Flash");

        if (model == null) {
            showError("Gemini API not configured. Please check your API key.");
            return;
        }

        if (linkedPatientUid != null) {
            loadEmotionDataAndGenerateReport();
        } else {
            showError("No patient linked");
        }
    }

    private void initializeViews(View view) {
        btnBack = view.findViewById(R.id.btn_back);
        tvGeneratedDate = view.findViewById(R.id.tv_generated_date);
        tvModelName = view.findViewById(R.id.tv_model_name);
        tvStatVideos = view.findViewById(R.id.tv_stat_videos);
        tvStatEmotions = view.findViewById(R.id.tv_stat_emotions);
        tvStatPositive = view.findViewById(R.id.tv_stat_positive);
        chipStatus = view.findViewById(R.id.chip_status);
        layoutLoading = view.findViewById(R.id.layout_loading);
        tvLoadingMessage = view.findViewById(R.id.tv_loading_message);
        scrollReportContent = view.findViewById(R.id.scroll_report_content);
        tvReportContent = view.findViewById(R.id.tv_report_content);
        btnShareReport = view.findViewById(R.id.btn_share_report);
        btnExportPdf = view.findViewById(R.id.btn_export_pdf);
        btnRegenerate = view.findViewById(R.id.btn_regenerate);
    }

    private void setupListeners() {
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                if (getParentFragmentManager().getBackStackEntryCount() > 0) {
                    getParentFragmentManager().popBackStack();
                } else {
                    GuardianHomeFragment homeFragment = new GuardianHomeFragment();
                    getParentFragmentManager().beginTransaction()
                            .replace(R.id.container, homeFragment)
                            .commit();
                }
            });
        }

        btnRegenerate.setOnClickListener(v -> loadEmotionDataAndGenerateReport());

        btnShareReport.setOnClickListener(v -> {
            if (tvReportContent.getText() != null && !tvReportContent.getText().toString().isEmpty()) {
                shareReport(tvReportContent.getText().toString());
            } else {
                Toast.makeText(getContext(), "No report to share", Toast.LENGTH_SHORT).show();
            }
        });

        btnExportPdf.setOnClickListener(v -> {
            Toast.makeText(getContext(), "PDF export coming soon!", Toast.LENGTH_SHORT).show();
        });
    }

    private void loadEmotionDataAndGenerateReport() {
        showLoading(true, "Loading emotion data...");
        chipStatus.setText("Loading");
        chipStatus.setChipBackgroundColorResource(android.R.color.darker_gray);

        DatabaseReference emotionRef = FirebaseDatabase.getInstance().getReference()
                .child("Patient")
                .child(linkedPatientUid)
                .child("videoEmotions");

        emotionRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    showError("No emotion data available yet. Patient needs to watch videos first.");
                    return;
                }

                emotionData = new EmotionData();
                for (DataSnapshot videoSnapshot : snapshot.getChildren()) {
                    emotionData.videoCount++;
                    if (videoSnapshot.child("totalEmotions").exists()) {
                        emotionData.totalEmotions += getIntValue(videoSnapshot, "totalEmotions");
                        emotionData.happy += getIntValue(videoSnapshot, "happy");
                        emotionData.sad += getIntValue(videoSnapshot, "sad");
                        emotionData.angry += getIntValue(videoSnapshot, "angry");
                        emotionData.neutral += getIntValue(videoSnapshot, "neutral");
                        emotionData.fear += getIntValue(videoSnapshot, "fear");
                        emotionData.disgust += getIntValue(videoSnapshot, "disgust");
                        emotionData.surprise += getIntValue(videoSnapshot, "surprise");
                    }
                }

                Log.d(TAG, "Loaded: " + emotionData.videoCount + " videos, " + emotionData.totalEmotions + " emotions");

                updateStats();
                generateAIReport();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                showError("Failed to load emotion data: " + error.getMessage());
            }
        });
    }

    private int getIntValue(DataSnapshot snapshot, String key) {
        Long value = snapshot.child(key).getValue(Long.class);
        return value != null ? value.intValue() : 0;
    }

    private void updateStats() {
        tvStatVideos.setText(String.valueOf(emotionData.videoCount));
        tvStatEmotions.setText(String.valueOf(emotionData.totalEmotions));

        if (emotionData.totalEmotions > 0) {
            int positive = emotionData.happy + emotionData.surprise;
            double positivePercent = (positive * 100.0) / emotionData.totalEmotions;
            tvStatPositive.setText(String.format("%.0f%%", positivePercent));
        } else {
            tvStatPositive.setText("0%");
        }
    }

    /**
     * FIXED: Generate AI report with proper error handling
     */
    private void generateAIReport() {
        if (model == null) {
            showError("Gemini API not initialized. Please check your API key configuration.");
            return;
        }

        showLoading(true, "Generating AI analysis...");
        chipStatus.setText("Analyzing");
        chipStatus.setChipBackgroundColorResource(android.R.color.holo_orange_light);

        executor.execute(() -> {
            try {
                String prompt = buildReportPrompt();
                Log.d(TAG, "Sending prompt to Gemini (length: " + prompt.length() + ")");

                // FIXED: Create content properly
                Content.Builder contentBuilder = new Content.Builder();
                contentBuilder.addText(prompt);
                Content content = contentBuilder.build();

                ListenableFuture<GenerateContentResponse> response = model.generateContent(content);

                Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
                    @Override
                    public void onSuccess(GenerateContentResponse result) {
                        try {
                            String reportText = result.getText();
                            Log.d(TAG, "✅ AI report generated (" + reportText.length() + " chars)");

                            if (getActivity() != null) {
                                getActivity().runOnUiThread(() -> displayReport(reportText));
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "❌ Error parsing response", e);
                            if (getActivity() != null) {
                                getActivity().runOnUiThread(() ->
                                        showError("Error processing response: " + e.getMessage()));
                            }
                        }
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        Log.e(TAG, "❌ AI report generation failed", t);

                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                String errorMsg = t.getMessage();
                                String displayError;

                                if (errorMsg != null) {
                                    if (errorMsg.contains("API key")) {
                                        displayError = "API key error. Please check your Gemini API key configuration in local.properties";
                                    } else if (errorMsg.contains("quota") || errorMsg.contains("limit")) {
                                        displayError = "API quota exceeded. Please try again tomorrow.";
                                    } else if (errorMsg.contains("network") || errorMsg.contains("connect")) {
                                        displayError = "Network error. Please check your internet connection.";
                                    } else if (errorMsg.contains("404")) {
                                        displayError = "Model not found. Please update the model name to a valid Gemini model.";
                                    } else {
                                        displayError = "Failed to generate report: " + errorMsg;
                                    }
                                } else {
                                    displayError = "Unknown error occurred. Please try again.";
                                }

                                showError(displayError);
                            });
                        }
                    }
                }, executor);

            } catch (Exception e) {
                Log.e(TAG, "❌ Exception generating report", e);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> showError("Error: " + e.getMessage()));
                }
            }
        });
    }

    private String buildReportPrompt() {
        StringBuilder prompt = new StringBuilder();

        prompt.append("You are an expert in Alzheimer's care and emotional analysis. ");
        prompt.append("Generate a comprehensive, professional report analyzing a patient's emotional responses to memory videos.\n\n");

        prompt.append("EMOTION DATA:\n");
        prompt.append("═══════════════════════════════════\n");
        prompt.append("Total Videos Watched: ").append(emotionData.videoCount).append("\n");
        prompt.append("Total Emotions Detected: ").append(emotionData.totalEmotions).append("\n\n");

        prompt.append("Emotion Breakdown:\n");
        prompt.append("  😊 Happy: ").append(emotionData.happy).append("\n");
        prompt.append("  😐 Neutral: ").append(emotionData.neutral).append("\n");
        prompt.append("  😢 Sad: ").append(emotionData.sad).append("\n");
        prompt.append("  😮 Surprise: ").append(emotionData.surprise).append("\n");
        prompt.append("  😠 Angry: ").append(emotionData.angry).append("\n");
        prompt.append("  😨 Fear: ").append(emotionData.fear).append("\n");
        prompt.append("  🤢 Disgust: ").append(emotionData.disgust).append("\n\n");

        if (emotionData.totalEmotions > 0) {
            int positive = emotionData.happy + emotionData.surprise;
            int negative = emotionData.sad + emotionData.angry + emotionData.fear;
            double positivePercent = (positive * 100.0) / emotionData.totalEmotions;
            double negativePercent = (negative * 100.0) / emotionData.totalEmotions;
            double neutralPercent = (emotionData.neutral * 100.0) / emotionData.totalEmotions;

            prompt.append("Emotional Balance:\n");
            prompt.append("  Positive: ").append(String.format("%.1f%%", positivePercent)).append("\n");
            prompt.append("  Negative: ").append(String.format("%.1f%%", negativePercent)).append("\n");
            prompt.append("  Neutral: ").append(String.format("%.1f%%", neutralPercent)).append("\n\n");
        }

        prompt.append("═══════════════════════════════════\n\n");

        prompt.append("Generate a detailed report with these sections:\n\n");
        prompt.append("1. EXECUTIVE SUMMARY (2-3 sentences)\n");
        prompt.append("2. DETAILED ANALYSIS (3-4 paragraphs)\n");
        prompt.append("3. KEY INSIGHTS (3-5 bullet points)\n");
        prompt.append("4. RECOMMENDATIONS (3-5 specific actions)\n");
        prompt.append("5. NEXT STEPS\n\n");

        prompt.append("Use clear headers, bullet points, and compassionate language. Be specific and actionable.");

        return prompt.toString();
    }

    private void displayReport(String reportText) {
        showLoading(false, null);
        chipStatus.setText("Ready");
        chipStatus.setChipBackgroundColorResource(android.R.color.holo_green_light);

        String formattedReport = formatReportText(reportText);
        tvReportContent.setText(formattedReport);
        scrollReportContent.setVisibility(View.VISIBLE);

        Log.d(TAG, "Report displayed successfully");
    }

    private String formatReportText(String reportText) {
        return reportText
                .replaceAll("(EXECUTIVE SUMMARY|DETAILED ANALYSIS|KEY INSIGHTS|RECOMMENDATIONS|NEXT STEPS)", "\n\n$1")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private void shareReport(String reportText) {
        String shareText = "📊 EMOTION ANALYSIS REPORT\n" +
                "Generated: " + new SimpleDateFormat("MMMM dd, yyyy", Locale.US).format(new Date()) + "\n\n" +
                "STATISTICS:\n" +
                "Videos: " + emotionData.videoCount + "\n" +
                "Emotions: " + emotionData.totalEmotions + "\n" +
                "Positive: " + tvStatPositive.getText() + "\n\n" +
                reportText;

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Emotion Analysis Report");
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
        startActivity(Intent.createChooser(shareIntent, "Share Report"));

        Log.d(TAG, "Report shared");
    }

    private void showLoading(boolean show, String message) {
        if (layoutLoading != null) {
            layoutLoading.setVisibility(show ? View.VISIBLE : View.GONE);
            if (message != null && tvLoadingMessage != null) {
                tvLoadingMessage.setText(message);
            }
        }
        if (scrollReportContent != null) {
            scrollReportContent.setVisibility(show ? View.GONE : View.VISIBLE);
        }
        if (btnRegenerate != null) {
            btnRegenerate.setEnabled(!show);
        }
        if (btnShareReport != null) {
            btnShareReport.setEnabled(!show);
        }
        if (btnExportPdf != null) {
            btnExportPdf.setEnabled(!show);
        }
    }

    private void showError(String error) {
        showLoading(false, null);
        chipStatus.setText("Error");
        chipStatus.setChipBackgroundColorResource(android.R.color.holo_red_light);

        tvReportContent.setText("❌ " + error + "\n\n" +
                "Please try the following:\n" +
                "• Check your internet connection\n" +
                "• Verify your Gemini API key in local.properties\n" +
                "• Ensure patient has watched videos\n" +
                "• Try regenerating the report\n\n" +
                "If the issue persists, check the logs for details.");

        scrollReportContent.setVisibility(View.VISIBLE);
        Toast.makeText(getContext(), error, Toast.LENGTH_LONG).show();
    }

    private static class EmotionData {
        int videoCount = 0;
        int totalEmotions = 0;
        int happy = 0;
        int sad = 0;
        int angry = 0;
        int neutral = 0;
        int fear = 0;
        int disgust = 0;
        int surprise = 0;
    }
}