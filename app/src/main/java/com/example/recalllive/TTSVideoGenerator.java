package com.example.recalllive;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
public class TTSVideoGenerator {
    private static final String TAG = "TTSVideoGenerator";
    private final Context context;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private final CountDownLatch ttsInitLatch = new CountDownLatch(1);

    public interface TTSGenerationCallback {
        void onAudioGenerated(String audioFilePath, int durationSeconds);

        void onError(String error);
    }

    public TTSVideoGenerator(Context context) {
        this.context = context;
        Log.d(TAG, "═══════════════════════════════════════════════════");
        Log.d(TAG, "INITIALIZING TTS VIDEO GENERATOR");
        Log.d(TAG, "═══════════════════════════════════════════════════");
        initializeTTS();
    }

    private void initializeTTS() {
        Log.d(TAG, "▶️ Starting TTS initialization...");

        tts = new TextToSpeech(context, status -> {
            Log.d(TAG, "TTS initialization callback - Status: " + status);

            if (status == TextToSpeech.SUCCESS) {
                Log.d(TAG, "✓ TTS engine started successfully");

                int langResult = tts.setLanguage(Locale.US);
                Log.d(TAG, "Language set result: " + langResult);

                if (langResult == TextToSpeech.LANG_MISSING_DATA) {
                    Log.e(TAG, "❌ Language data missing");
                    ttsReady = false;
                } else if (langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "❌ Language not supported");
                    ttsReady = false;
                } else {
                    tts.setSpeechRate(VideoConfiguration.TTS_SPEECH_RATE);
                    tts.setPitch(VideoConfiguration.TTS_PITCH);
                    ttsReady = true;

                    Log.d(TAG, "╔══════════════════════════════════════════════╗");
                    Log.d(TAG, "   ✓✓✓ TTS FULLY INITIALIZED ✓✓✓");
                    Log.d(TAG, "╚══════════════════════════════════════════════╝");
                    Log.d(TAG, "  Rate: " + VideoConfiguration.TTS_SPEECH_RATE);
                    Log.d(TAG, "  Pitch: " + VideoConfiguration.TTS_PITCH);
                    Log.d(TAG, "  Language: " + Locale.US);
                }
            } else {
                Log.e(TAG, "❌ TTS initialization failed with status: " + status);
                ttsReady = false;
            }

            ttsInitLatch.countDown();
        });
    }

    public String generateNarrationScript(PhotoClusteringManager.PhotoCluster cluster) {
        StringBuilder script = new StringBuilder();

        script.append("Let's take a moment to remember. ");

        String location = cluster.getLocationName();
        if (location != null && !location.contains("Unknown") && !location.contains("(")) {
            script.append("These cherished memories were captured at ").append(location).append(". ");
        } else {
            script.append("These are special moments from your life. ");
        }

        String timeDesc = cluster.getTimeDescription();
        if (timeDesc != null && !timeDesc.isEmpty()) {
            script.append("Looking back at ").append(timeDesc).append(", ");
        }

        int photoCount = cluster.getPhotoCount();
        if (photoCount > 0) {
            if (photoCount == 1) {
                script.append("this precious moment ");
            } else {
                script.append("these ").append(photoCount).append(" beautiful moments ");
            }
            script.append("captured a special time in your journey. ");
        }

        script.append("Take your time looking at each image. ");
        script.append("These memories are part of your story, preserved and cherished. ");
        script.append("Each photograph holds a piece of your life, always here for you to revisit.");

        return script.toString();
    }

    public void generateAudioFile(String script, TTSGenerationCallback callback) {
        new Thread(() -> {
            try {
                Log.d(TAG, "═══════════════════════════════════════════════════");
                Log.d(TAG, "GENERATING TTS AUDIO");
                Log.d(TAG, "═══════════════════════════════════════════════════");
                Log.d(TAG, "Script length: " + script.length() + " chars");
                Log.d(TAG, "Script words: " + script.split("\\s+").length);
                Log.d(TAG, "Script: " + script.substring(0, Math.min(100, script.length())) + "...");

                // Wait for TTS initialization
                Log.d(TAG, "⏳ Waiting for TTS to initialize...");
                if (!ttsInitLatch.await(15, TimeUnit.SECONDS)) {
                    Log.e(TAG, "❌ TTS initialization timeout (15 seconds)");
                    if (callback != null) callback.onError("TTS initialization timeout");
                    return;
                }

                Log.d(TAG, "TTS init complete. Ready status: " + ttsReady);

                if (!ttsReady) {
                    Log.e(TAG, "❌ TTS not ready after initialization");
                    if (callback != null) callback.onError("TTS not ready");
                    return;
                }

                // Create audio file
                File audioFile = new File(context.getCacheDir(), "tts_" + System.currentTimeMillis() + ".wav");
                Log.d(TAG, "✓ TTS ready, creating audio file");
                Log.d(TAG, "Output path: " + audioFile.getAbsolutePath());

                // Setup progress listener
                CountDownLatch audioLatch = new CountDownLatch(1);
                final String[] audioPath = {null};
                final String[] error = {null};
                final int[] duration = {0};

                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override
                    public void onStart(String utteranceId) {
                        Log.d(TAG, "▶️ TTS synthesis started for: " + utteranceId);
                    }

                    @Override
                    public void onDone(String utteranceId) {
                        Log.d(TAG, "✓ TTS synthesis completed for: " + utteranceId);

                        if (audioFile.exists()) {
                            long fileSize = audioFile.length();
                            Log.d(TAG, "✓ Audio file created");
                            Log.d(TAG, "  Path: " + audioFile.getAbsolutePath());
                            Log.d(TAG, "  Size: " + fileSize + " bytes");

                            if (fileSize > 0) {
                                audioPath[0] = audioFile.getAbsolutePath();
                                int wordCount = script.split("\\s+").length;
                                duration[0] = (int) Math.ceil((wordCount / 150.0) * 60);

                                Log.d(TAG, "  Estimated duration: " + duration[0] + "s");
                            } else {
                                Log.e(TAG, "❌ Audio file is empty (0 bytes)");
                                error[0] = "Audio file is empty";
                            }
                        } else {
                            Log.e(TAG, "❌ Audio file was not created");
                            error[0] = "Audio file not created";
                        }

                        audioLatch.countDown();
                    }

                    @Override
                    public void onError(String utteranceId) {
                        Log.e(TAG, "❌ TTS synthesis error for: " + utteranceId);
                        error[0] = "Synthesis failed";
                        audioLatch.countDown();
                    }

                    @Override
                    public void onError(String utteranceId, int errorCode) {
                        Log.e(TAG, "❌ TTS synthesis error code " + errorCode + " for: " + utteranceId);
                        error[0] = "Synthesis failed with code: " + errorCode;
                        audioLatch.countDown();
                    }
                });

                // Start synthesis
                Log.d(TAG, "🎙️ Starting audio synthesis...");
                int result;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    Bundle params = new Bundle();
                    params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "narration");
                    params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f);

                    result = tts.synthesizeToFile(script, params, audioFile, "narration");
                } else {
                    HashMap<String, String> params = new HashMap<>();
                    params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "narration");
                    params.put(TextToSpeech.Engine.KEY_PARAM_VOLUME, "1.0");

                    result = tts.synthesizeToFile(script, params, audioFile.getAbsolutePath());
                }

                Log.d(TAG, "Synthesis start result: " + (result == TextToSpeech.SUCCESS ? "SUCCESS" : "FAILED (" + result + ")"));

                if (result != TextToSpeech.SUCCESS) {
                    Log.e(TAG, "❌ Failed to start synthesis, result code: " + result);
                    if (callback != null) callback.onError("Failed to start synthesis");
                    return;
                }

                // Wait for completion
                Log.d(TAG, "⏳ Waiting for synthesis to complete (timeout: 60s)...");
                if (!audioLatch.await(60, TimeUnit.SECONDS)) {
                    Log.e(TAG, "❌ Synthesis timeout after 60 seconds");
                    if (callback != null) callback.onError("Synthesis timeout");
                    return;
                }

                // Return result
                if (error[0] != null) {
                    Log.e(TAG, "╔══════════════════════════════════════════════╗");
                    Log.e(TAG, "  ❌ TTS GENERATION FAILED");
                    Log.e(TAG, "╚══════════════════════════════════════════════╝");
                    Log.e(TAG, "Error: " + error[0]);
                    if (callback != null) callback.onError(error[0]);
                } else if (audioPath[0] != null && callback != null) {
                    Log.d(TAG, "╔══════════════════════════════════════════════╗");
                    Log.d(TAG, "  ✓✓✓ TTS AUDIO READY ✓✓✓");
                    Log.d(TAG, "╚══════════════════════════════════════════════╝");
                    Log.d(TAG, "Path: " + audioPath[0]);
                    Log.d(TAG, "Duration: " + duration[0] + "s");

                    callback.onAudioGenerated(audioPath[0], duration[0]);
                } else {
                    Log.e(TAG, "❌ Audio path is null");
                    if (callback != null) callback.onError("Audio path is null");
                }

            } catch (InterruptedException e) {
                Log.e(TAG, "❌ Thread interrupted", e);
                if (callback != null) callback.onError("Thread interrupted: " + e.getMessage());
            } catch (Exception e) {
                Log.e(TAG, "❌ Unexpected exception", e);
                if (callback != null) callback.onError("Exception: " + e.getMessage());
            }
        }).start();
    }

    public void generateCompleteNarration(PhotoClusteringManager.PhotoCluster cluster, TTSGenerationCallback callback) {
        Log.d(TAG, "═══════════════════════════════════════════════════");
        Log.d(TAG, "GENERATING COMPLETE NARRATION");
        Log.d(TAG, "Cluster: " + cluster.getClusterId());
        Log.d(TAG, "Location: " + cluster.getLocationName());
        Log.d(TAG, "Photos: " + cluster.getPhotoCount());
        Log.d(TAG, "═══════════════════════════════════════════════════");

        String script = generateNarrationScript(cluster);
        Log.d(TAG, "✓ Script generated (" + script.split("\\s+").length + " words)");

        generateAudioFile(script, callback);
    }

    public void release() {
        if (tts != null) {
            Log.d(TAG, "Releasing TTS engine...");
            tts.stop();
            tts.shutdown();
            tts = null;
            Log.d(TAG, "✓ TTS released");
        }
    }
}