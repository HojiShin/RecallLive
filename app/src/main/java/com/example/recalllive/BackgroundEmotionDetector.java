package com.example.recalllive;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.util.Log;

import androidx.camera.core.ImageProxy;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import org.tensorflow.lite.Interpreter;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

public class BackgroundEmotionDetector {
    private static final String TAG = "BackgroundEmotionDetector";
    private static final long DETECTION_INTERVAL_MS = 2000; // Detect every 2 seconds
    private static final String MODEL_FILE_NAME = "model_filter.tflite";

    // TFLite configuration
    private static final int INPUT_IMAGE_SIZE = 48;
    private static final int NUM_CHANNELS = 1;
    private static final int NUM_EMOTIONS = 7;
    private final String[] EMOTION_LABELS = {"Angry", "Disgust", "Fear", "Happy", "Neutral", "Sad", "Surprise"};

    private final FaceDetector faceDetector;
    private Interpreter tflite;
    private final List<EmotionRecord> emotionTimeline;
    private final EmotionSummary summary;
    private long sessionStartTime;
    private long lastDetectionTime;

    private ByteBuffer imgData;
    private float[][] emotionProbArray;

    // Debug counters
    private int detectionAttempts = 0;
    private int facesFound = 0;
    private int emotionsRecorded = 0;
    private boolean modelLoaded = false;

    /**
     * Callback interface for emotion detection results
     */
    public interface EmotionCallback {
        void onEmotionDetected(String emotion, float confidence, long timestamp);
    }

    public BackgroundEmotionDetector(Context context) {
        Log.d(TAG, "╔═══════════════════════════════════════╗");
        Log.d(TAG, "║  INITIALIZING EMOTION DETECTOR        ║");
        Log.d(TAG, "╚═══════════════════════════════════════╝");

        // Configure face detector
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .setMinFaceSize(0.1f) // Very sensitive - detect small faces
                .enableTracking()
                .build();

        this.faceDetector = FaceDetection.getClient(options);
        this.emotionTimeline = new ArrayList<>();
        this.summary = new EmotionSummary();
        this.lastDetectionTime = 0;

        Log.d(TAG, "✓ Face detector initialized (min face size: 10%)");

        // Initialize TFLite
        try {
            Interpreter.Options tfliteOptions = new Interpreter.Options();
            tfliteOptions.setNumThreads(2);

            MappedByteBuffer modelBuffer = loadModelFile(context);
            this.tflite = new Interpreter(modelBuffer, tfliteOptions);

            // Allocate buffers
            imgData = ByteBuffer.allocateDirect(INPUT_IMAGE_SIZE * INPUT_IMAGE_SIZE * NUM_CHANNELS * 4);
            imgData.order(ByteOrder.nativeOrder());
            emotionProbArray = new float[1][NUM_EMOTIONS];

            modelLoaded = true;
            Log.d(TAG, "✓ TFLite model loaded: " + MODEL_FILE_NAME);
            Log.d(TAG, "  Input size: 48x48 grayscale");
            Log.d(TAG, "  Output: 7 emotions");
            Log.d(TAG, "✓✓✓ DETECTOR READY ✓✓✓");

        } catch (Exception e) {
            Log.e(TAG, "✗✗✗ FAILED TO LOAD TFLITE MODEL ✗✗✗", e);
            Log.e(TAG, "Model file: " + MODEL_FILE_NAME);
            Log.e(TAG, "Expected location: app/src/main/assets/" + MODEL_FILE_NAME);
            modelLoaded = false;
            // Don't throw - continue without TFLite (will use simple detection)
        }
    }

    private MappedByteBuffer loadModelFile(Context context) throws IOException {
        FileInputStream fileInputStream = new FileInputStream(
                context.getAssets().openFd(MODEL_FILE_NAME).getFileDescriptor());
        FileChannel fileChannel = fileInputStream.getChannel();
        long startOffset = context.getAssets().openFd(MODEL_FILE_NAME).getStartOffset();
        long declaredLength = context.getAssets().openFd(MODEL_FILE_NAME).getDeclaredLength();

        Log.d(TAG, "Model file size: " + declaredLength + " bytes");
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    /**
     * Start a new video session
     */
    public void startVideoSession() {
        sessionStartTime = System.currentTimeMillis();
        emotionTimeline.clear();
        summary.reset();
        lastDetectionTime = 0;
        detectionAttempts = 0;
        facesFound = 0;
        emotionsRecorded = 0;

        Log.d(TAG, "═══════════════════════════════════════");
        Log.d(TAG, "STARTED NEW EMOTION TRACKING SESSION");
        Log.d(TAG, "Model loaded: " + modelLoaded);
        Log.d(TAG, "═══════════════════════════════════════");
    }

    /**
     * Detect emotion from ImageProxy (RECOMMENDED)
     */
    @androidx.camera.core.ExperimentalGetImage
    public void detectEmotionFromImageProxy(ImageProxy imageProxy, EmotionCallback callback) {
        if (imageProxy == null || imageProxy.getImage() == null) {
            Log.w(TAG, "ImageProxy is null!");
            imageProxy.close(); // Close if invalid
            return;
        }

        detectionAttempts++;

        // Throttle detection
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastDetectionTime < DETECTION_INTERVAL_MS) {
            imageProxy.close(); // Close if skipping
            return;
        }
        lastDetectionTime = currentTime;

        // Log every 5 attempts
        if (detectionAttempts % 5 == 0) {
            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            Log.d(TAG, "Detection attempt #" + detectionAttempts);
            Log.d(TAG, "Faces found so far: " + facesFound);
            Log.d(TAG, "Emotions recorded: " + emotionsRecorded);
            Log.d(TAG, "Image size: " + imageProxy.getWidth() + "x" + imageProxy.getHeight());
        }

        try {
            @SuppressLint("UnsafeOptInUsageError")
            Image mediaImage = imageProxy.getImage();

            if (mediaImage != null) {
                InputImage image = InputImage.fromMediaImage(
                        mediaImage,
                        imageProxy.getImageInfo().getRotationDegrees()
                );

                faceDetector.process(image)
                        .addOnSuccessListener(faces -> {
                            try {
                                if (detectionAttempts % 5 == 0) {
                                    Log.d(TAG, "ML Kit result: " + faces.size() + " face(s) detected");
                                }

                                if (!faces.isEmpty()) {
                                    facesFound++;
                                    Face face = faces.get(0);

                                    if (detectionAttempts % 5 == 0) {
                                        Rect bounds = face.getBoundingBox();
                                        Log.d(TAG, "★ FACE FOUND ★");
                                        Log.d(TAG, "  Bounds: " + bounds.width() + "x" + bounds.height());
                                    }

                                    // Convert ImageProxy to Bitmap
                                    Bitmap bitmap = imageProxyToBitmap(imageProxy);
                                    if (bitmap != null) {
                                        if (detectionAttempts % 5 == 0) {
                                            Log.d(TAG, "  Bitmap created: " + bitmap.getWidth() + "x" + bitmap.getHeight());
                                        }

                                        processEmotionWithTFLite(bitmap, face, callback);
                                        bitmap.recycle();
                                    } else {
                                        Log.e(TAG, "Failed to convert ImageProxy to Bitmap!");
                                    }
                                } else {
                                    if (detectionAttempts % 5 == 0) {
                                        Log.w(TAG, "No face detected in this frame");
                                    }
                                }
                            } finally {
                                // CRITICAL: Always close ImageProxy after processing
                                imageProxy.close();
                            }
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Face detection FAILED", e);
                            // CRITICAL: Close on failure too
                            imageProxy.close();
                        });
            } else {
                imageProxy.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in detectEmotionFromImageProxy", e);
            imageProxy.close(); // Close on exception
        }
    }

    /**
     * Process emotion using TFLite model
     */
    private void processEmotionWithTFLite(Bitmap bitmap, Face face, EmotionCallback callback) {
        if (!modelLoaded || tflite == null) {
            Log.e(TAG, "TFLite model not loaded - using fallback detection");
            // Fallback to simple detection
            String emotion = "Neutral";
            float confidence = 0.5f;
            recordEmotion(emotion, confidence, callback);
            return;
        }

        try {
            // Crop face from bitmap
            Bitmap faceBitmap = cropFace(bitmap, face);
            if (faceBitmap == null) {
                Log.w(TAG, "Failed to crop face from bitmap");
                return;
            }

            if (detectionAttempts % 5 == 0) {
                Log.d(TAG, "  Face cropped: " + faceBitmap.getWidth() + "x" + faceBitmap.getHeight());
            }

            // Preprocess for TFLite
            preprocessBitmapForTFLite(faceBitmap);
            faceBitmap.recycle();

            // Run TFLite inference
            long startTime = System.currentTimeMillis();
            tflite.run(imgData, emotionProbArray);
            long inferenceTime = System.currentTimeMillis() - startTime;

            // Get emotion result
            String emotion = getTopEmotion(emotionProbArray[0]);
            float confidence = getTopEmotionConfidence(emotionProbArray[0]);

            if (detectionAttempts % 5 == 0) {
                Log.d(TAG, "  TFLite inference: " + inferenceTime + "ms");
                Log.d(TAG, "  Result: " + emotion + " (" + (int)(confidence * 100) + "%)");
                logAllEmotionProbabilities(emotionProbArray[0]);
            }

            recordEmotion(emotion, confidence, callback);

        } catch (Exception e) {
            Log.e(TAG, "Error in processEmotionWithTFLite", e);
            e.printStackTrace();
        }
    }

    private void recordEmotion(String emotion, float confidence, EmotionCallback callback) {
        long timestamp = System.currentTimeMillis() - sessionStartTime;

        // Record emotion
        EmotionRecord record = new EmotionRecord(emotion, confidence, timestamp);
        emotionTimeline.add(record);
        updateSummary(emotion);
        emotionsRecorded++;

        Log.d(TAG, "✓ EMOTION RECORDED: " + emotion + " (Total: " + emotionsRecorded + ")");

        if (callback != null) {
            callback.onEmotionDetected(emotion, confidence, timestamp);
        }
    }

    private void logAllEmotionProbabilities(float[] probs) {
        Log.d(TAG, "  All probabilities:");
        for (int i = 0; i < probs.length && i < EMOTION_LABELS.length; i++) {
            Log.d(TAG, "    " + EMOTION_LABELS[i] + ": " + String.format("%.3f", probs[i]));
        }
    }

    /**
     * Crop face from bitmap
     */
    private Bitmap cropFace(Bitmap originalBitmap, Face face) {
        try {
            Rect bounds = face.getBoundingBox();
            int x = bounds.left;
            int y = bounds.top;
            int width = bounds.width();
            int height = bounds.height();

            // Add padding
            int padding = (int) (width * 0.2f);
            x = Math.max(0, x - padding);
            y = Math.max(0, y - padding);
            width = Math.min(originalBitmap.getWidth() - x, width + 2 * padding);
            height = Math.min(originalBitmap.getHeight() - y, height + 2 * padding);

            if (width <= 0 || height <= 0 || x < 0 || y < 0) {
                Log.e(TAG, "Invalid crop dimensions!");
                return null;
            }

            Bitmap croppedFace = Bitmap.createBitmap(originalBitmap, x, y, width, height);
            Bitmap scaledFace = Bitmap.createScaledBitmap(croppedFace, INPUT_IMAGE_SIZE, INPUT_IMAGE_SIZE, true);

            if (croppedFace != scaledFace) {
                croppedFace.recycle();
            }

            return scaledFace;
        } catch (Exception e) {
            Log.e(TAG, "Error cropping face", e);
            return null;
        }
    }

    /**
     * Preprocess bitmap for TFLite model
     */
    private void preprocessBitmapForTFLite(Bitmap faceBitmap) {
        imgData.rewind();

        int[] intValues = new int[INPUT_IMAGE_SIZE * INPUT_IMAGE_SIZE];
        faceBitmap.getPixels(intValues, 0, faceBitmap.getWidth(), 0, 0,
                faceBitmap.getWidth(), faceBitmap.getHeight());

        for (int i = 0; i < INPUT_IMAGE_SIZE; ++i) {
            for (int j = 0; j < INPUT_IMAGE_SIZE; ++j) {
                int pixel = intValues[i * INPUT_IMAGE_SIZE + j];

                float r = ((pixel >> 16) & 0xFF);
                float g = ((pixel >> 8) & 0xFF);
                float b = (pixel & 0xFF);

                // Grayscale conversion
                float grayscalePixel = (0.299f * r + 0.587f * g + 0.114f * b);

                // Normalize to [0, 1]
                float normalizedPixel = grayscalePixel / 255.0f;

                imgData.putFloat(normalizedPixel);
            }
        }
    }

    /**
     * Get top emotion from probabilities
     */
    private String getTopEmotion(float[] emotionProbabilities) {
        int maxIndex = 0;
        float maxProb = emotionProbabilities[0];

        for (int i = 1; i < emotionProbabilities.length; ++i) {
            if (emotionProbabilities[i] > maxProb) {
                maxProb = emotionProbabilities[i];
                maxIndex = i;
            }
        }

        return EMOTION_LABELS[maxIndex];
    }

    /**
     * Get confidence of top emotion
     */
    private float getTopEmotionConfidence(float[] emotionProbabilities) {
        float maxProb = 0.0f;
        for (float prob : emotionProbabilities) {
            if (prob > maxProb) {
                maxProb = prob;
            }
        }
        return maxProb;
    }

    /**
     * Convert ImageProxy to Bitmap
     */
    @androidx.camera.core.ExperimentalGetImage
    private Bitmap imageProxyToBitmap(ImageProxy imageProxy) {
        try {
            Image image = imageProxy.getImage();
            if (image == null) return null;

            Image.Plane[] planes = image.getPlanes();
            ByteBuffer yBuffer = planes[0].getBuffer();
            ByteBuffer uBuffer = planes[1].getBuffer();
            ByteBuffer vBuffer = planes[2].getBuffer();

            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();

            byte[] nv21 = new byte[ySize + uSize + vSize];
            yBuffer.get(nv21, 0, ySize);
            vBuffer.get(nv21, ySize, vSize);
            uBuffer.get(nv21, ySize + vSize, uSize);

            YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21,
                    imageProxy.getWidth(), imageProxy.getHeight(), null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new Rect(0, 0, imageProxy.getWidth(), imageProxy.getHeight()), 90, out);
            byte[] imageBytes = out.toByteArray();

            return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
        } catch (Exception e) {
            Log.e(TAG, "Error converting ImageProxy to Bitmap", e);
            return null;
        }
    }

    /**
     * Update emotion summary counts
     */
    private void updateSummary(String emotion) {
        summary.incrementTotal();
        switch (emotion) {
            case "Happy":
                summary.incrementHappy();
                break;
            case "Sad":
                summary.incrementSad();
                break;
            case "Angry":
                summary.incrementAngry();
                break;
            case "Neutral":
                summary.incrementNeutral();
                break;
            case "Fear":
                summary.incrementFear();
                break;
            case "Disgust":
                summary.incrementDisgust();
                break;
            case "Surprise":
                summary.incrementSurprise();
                break;
        }
    }

    /**
     * Get timeline of all detected emotions
     */
    public List<EmotionRecord> getEmotionTimeline() {
        Log.d(TAG, "═══════════════════════════════════════");
        Log.d(TAG, "FINAL SESSION STATS:");
        Log.d(TAG, "Total detection attempts: " + detectionAttempts);
        Log.d(TAG, "Faces found: " + facesFound);
        Log.d(TAG, "Emotions recorded: " + emotionsRecorded);
        Log.d(TAG, "Timeline size: " + emotionTimeline.size());
        Log.d(TAG, "═══════════════════════════════════════");
        return new ArrayList<>(emotionTimeline);
    }

    /**
     * Get summary of emotion counts
     */
    public EmotionSummary getEmotionSummary() {
        return summary;
    }

    /**
     * Release resources
     */
    public void release() {
        Log.d(TAG, "Releasing detector resources...");
        if (faceDetector != null) {
            faceDetector.close();
        }
        if (tflite != null) {
            tflite.close();
        }
        emotionTimeline.clear();
        Log.d(TAG, "Detector released");
    }

    /**
     * Data class for emotion record
     */
    public static class EmotionRecord {
        public final String emotion;
        public final float confidence;
        public final long timestamp;

        public EmotionRecord(String emotion, float confidence, long timestamp) {
            this.emotion = emotion;
            this.confidence = confidence;
            this.timestamp = timestamp;
        }
    }

    /**
     * Data class for emotion summary
     */
    public static class EmotionSummary {
        private int total = 0;
        private int happy = 0;
        private int sad = 0;
        private int angry = 0;
        private int neutral = 0;
        private int fear = 0;
        private int disgust = 0;
        private int surprise = 0;

        public void reset() {
            total = happy = sad = angry = neutral = fear = disgust = surprise = 0;
        }

        public void incrementTotal() { total++; }
        public void incrementHappy() { happy++; }
        public void incrementSad() { sad++; }
        public void incrementAngry() { angry++; }
        public void incrementNeutral() { neutral++; }
        public void incrementFear() { fear++; }
        public void incrementDisgust() { disgust++; }
        public void incrementSurprise() { surprise++; }

        // Getters
        public int getTotal() { return total; }
        public int getHappy() { return happy; }
        public int getSad() { return sad; }
        public int getAngry() { return angry; }
        public int getNeutral() { return neutral; }
        public int getFear() { return fear; }
        public int getDigust() { return disgust; }
        public int getSurprise() { return surprise; }
    }
}