//package com.example.recalllive;
//
//import android.Manifest;
//import android.content.Context;
//import android.content.SharedPreferences;
//import android.graphics.Bitmap;
//import android.graphics.BitmapFactory;
//import android.graphics.ImageFormat;
//import android.graphics.Rect;
//import android.graphics.YuvImage;
//import android.media.Image;
//import android.util.Log;
//
//import androidx.annotation.NonNull;
//import androidx.camera.core.CameraSelector;
//import androidx.camera.core.ImageAnalysis;
//import androidx.camera.core.ImageProxy;
//import androidx.camera.core.Preview;
//import androidx.camera.lifecycle.ProcessCameraProvider;
//import androidx.core.content.ContextCompat;
//import androidx.lifecycle.LifecycleOwner;
//
//import com.google.android.gms.tasks.OnSuccessListener;
//import com.google.common.util.concurrent.ListenableFuture;
//import com.google.firebase.firestore.DocumentReference;
//import com.google.firebase.firestore.FieldValue;
//import com.google.firebase.firestore.FirebaseFirestore;
//import com.google.mlkit.vision.common.InputImage;
//import com.google.mlkit.vision.face.Face;
//import com.google.mlkit.vision.face.FaceDetection;
//import com.google.mlkit.vision.face.FaceDetector;
//import com.google.mlkit.vision.face.FaceDetectorOptions;
//
//import org.tensorflow.lite.Interpreter;
//import org.tensorflow.lite.support.common.FileUtil;
//import org.tensorflow.lite.support.image.ImageProcessor;
//import org.tensorflow.lite.support.image.TensorImage;
//import org.tensorflow.lite.support.image.ops.ResizeOp;
//
//import java.io.ByteArrayOutputStream;
//import java.io.IOException;
//import java.nio.ByteBuffer;
//import java.nio.ByteOrder;
//import java.nio.MappedByteBuffer;
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
//
///**
// * Facial expression analyzer for therapeutic monitoring
// * Requires explicit consent and is used only for patient benefit
// */
//public class FacialExpressionAnalyzer {
//    private static final String TAG = "FacialExpressionAnalyzer";
//    private static final String PREFS_NAME = "EmotionMonitoringPrefs";
//    private static final String KEY_CONSENT_GIVEN = "emotion_monitoring_consent";
//    private static final String KEY_LAST_ANALYSIS = "last_emotion_analysis";
//
//    // Emotion categories from typical CNN models
//    private static final String[] EMOTION_LABELS = {
//            "Neutral", "Happy", "Sad", "Surprised", "Fearful", "Disgusted", "Angry"
//    };
//
//    // Analysis settings
//    private static final long ANALYSIS_INTERVAL_MS = 5000; // Analyze every 5 seconds
//    private static final float CONFIDENCE_THRESHOLD = 0.6f;
//    private static final int MODEL_INPUT_SIZE = 48; // Typical for emotion models
//
//    private final Context context;
//    private final String patientUid;
//    private final FirebaseFirestore firestore;
//    private final SharedPreferences prefs;
//    private final ExecutorService executor;
//
//    private Interpreter tfliteInterpreter;
//    private FaceDetector faceDetector;
//    private ImageAnalysis imageAnalysis;
//    private boolean isAnalyzing = false;
//    private long lastAnalysisTime = 0;
//
//    // Current session data
//    private String currentVideoId;
//    private List<EmotionDataPoint> sessionEmotions;
//
//    public interface EmotionAnalysisCallback {
//        void onEmotionDetected(String emotion, float confidence);
//        void onAnalysisError(String error);
//        void onConsentRequired();
//    }
//
//    public static class EmotionDataPoint {
//        public String emotion;
//        public float confidence;
//        public long timestamp;
//        public String videoId;
//        public String context; // What was showing when emotion detected
//
//        public EmotionDataPoint(String emotion, float confidence, String videoId) {
//            this.emotion = emotion;
//            this.confidence = confidence;
//            this.timestamp = System.currentTimeMillis();
//            this.videoId = videoId;
//        }
//    }
//
//    public FacialExpressionAnalyzer(Context context, String patientUid) {
//        this.context = context;
//        this.patientUid = patientUid;
//        this.firestore = FirebaseFirestore.getInstance();
//        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
//        this.executor = Executors.newSingleThreadExecutor();
//        this.sessionEmotions = new ArrayList<>();
//    }
//
//    /**
//     * Initialize the emotion detection system with consent check
//     */
//    public void initialize(EmotionAnalysisCallback callback) {
//        // Check for consent first
//        if (!hasUserConsent()) {
//            Log.w(TAG, "Emotion monitoring requires consent");
//            if (callback != null) {
//                callback.onConsentRequired();
//            }
//            return;
//        }
//
//        try {
//            // Load TFLite model
//            loadEmotionModel();
//
//            // Setup face detector
//            setupFaceDetector();
//
//            Log.d(TAG, "Facial expression analyzer initialized");
//
//        } catch (Exception e) {
//            Log.e(TAG, "Failed to initialize emotion detection", e);
//            if (callback != null) {
//                callback.onAnalysisError("Initialization failed: " + e.getMessage());
//            }
//        }
//    }
//
//    /**
//     * Check if user has given consent for emotion monitoring
//     */
//    public boolean hasUserConsent() {
//        return prefs.getBoolean(KEY_CONSENT_GIVEN, false);
//    }
//
//    /**
//     * Record user consent (should be called after showing proper consent dialog)
//     */
//    public void recordUserConsent(boolean consentGiven) {
//        prefs.edit()
//                .putBoolean(KEY_CONSENT_GIVEN, consentGiven)
//                .putLong("consent_timestamp", System.currentTimeMillis())
//                .apply();
//
//        // Log consent decision
//        Map<String, Object> consentLog = new HashMap<>();
//        consentLog.put("patientUid", patientUid);
//        consentLog.put("consentGiven", consentGiven);
//        consentLog.put("timestamp", FieldValue.serverTimestamp());
//        consentLog.put("type", "emotion_monitoring");
//
//        firestore.collection("consent_logs").add(consentLog);
//    }
//
//    /**
//     * Load the TFLite emotion detection model
//     */
//    private void loadEmotionModel() throws IOException {
//        // Load model from assets (you'll need to copy your .tflite file there)
//        MappedByteBuffer modelBuffer = FileUtil.loadMappedFile(context,
//                "model_filter.tflite");
//
//        Interpreter.Options options = new Interpreter.Options();
//        options.setNumThreads(2);
//
//        tfliteInterpreter = new Interpreter(modelBuffer, options);
//
//        Log.d(TAG, "TFLite model loaded successfully");
//    }
//
//    /**
//     * Setup ML Kit face detector
//     */
//    private void setupFaceDetector() {
//        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
//                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
//                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
//                .build();
//
//        faceDetector = FaceDetection.getClient(options);
//    }
//
//    /**
//     * Start emotion monitoring during video playback
//     */
//    public void startMonitoring(String videoId, LifecycleOwner lifecycleOwner,
//                                EmotionAnalysisCallback callback) {
//        if (!hasUserConsent()) {
//            if (callback != null) {
//                callback.onConsentRequired();
//            }
//            return;
//        }
//
//        this.currentVideoId = videoId;
//        this.isAnalyzing = true;
//        this.sessionEmotions.clear();
//
//        // Setup camera for face analysis
//        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
//                ProcessCameraProvider.getInstance(context);
//
//        cameraProviderFuture.addListener(() -> {
//            try {
//                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
//                bindCameraUseCases(cameraProvider, lifecycleOwner, callback);
//            } catch (Exception e) {
//                Log.e(TAG, "Failed to start camera", e);
//                if (callback != null) {
//                    callback.onAnalysisError("Camera initialization failed");
//                }
//            }
//        }, ContextCompat.getMainExecutor(context));
//    }
//
//    /**
//     * Bind camera use cases for analysis
//     */
//    private void bindCameraUseCases(ProcessCameraProvider cameraProvider,
//                                    LifecycleOwner lifecycleOwner,
//                                    EmotionAnalysisCallback callback) {
//        // Use front camera for facial analysis
//        CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
//
//        // Setup image analysis
//        imageAnalysis = new ImageAnalysis.Builder()
//                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
//                .build();
//
//        imageAnalysis.setAnalyzer(executor, new ImageAnalysis.Analyzer() {
//            @Override
//            public void analyze(@NonNull ImageProxy imageProxy) {
//                processImage(imageProxy, callback);
//            }
//        });
//
//        try {
//            cameraProvider.unbindAll();
//            cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, imageAnalysis);
//        } catch (Exception e) {
//            Log.e(TAG, "Failed to bind camera use cases", e);
//        }
//    }
//
//    /**
//     * Process camera frame for emotion detection
//     */
//    private void processImage(ImageProxy imageProxy, EmotionAnalysisCallback callback) {
//        if (!isAnalyzing) {
//            imageProxy.close();
//            return;
//        }
//
//        // Throttle analysis rate
//        long currentTime = System.currentTimeMillis();
//        if (currentTime - lastAnalysisTime < ANALYSIS_INTERVAL_MS) {
//            imageProxy.close();
//            return;
//        }
//        lastAnalysisTime = currentTime;
//
//        try {
//            // Convert ImageProxy to Bitmap
//            Bitmap bitmap = imageProxyToBitmap(imageProxy);
//
//            // Detect face
//            InputImage inputImage = InputImage.fromBitmap(bitmap, 0);
//
//            faceDetector.process(inputImage)
//                    .addOnSuccessListener(faces -> {
//                        if (!faces.isEmpty()) {
//                            Face face = faces.get(0);
//                            analyzeFaceEmotion(bitmap, face, callback);
//                        }
//                    })
//                    .addOnFailureListener(e -> {
//                        Log.e(TAG, "Face detection failed", e);
//                    })
//                    .addOnCompleteListener(task -> {
//                        imageProxy.close();
//                    });
//
//        } catch (Exception e) {
//            Log.e(TAG, "Image processing failed", e);
//            imageProxy.close();
//        }
//    }
//
//    /**
//     * Analyze emotion from detected face
//     */
//    private void analyzeFaceEmotion(Bitmap fullImage, Face face,
//                                    EmotionAnalysisCallback callback) {
//        try {
//            // Crop face region
//            Rect bounds = face.getBoundingBox();
//            Bitmap faceBitmap = Bitmap.createBitmap(fullImage,
//                    Math.max(0, bounds.left),
//                    Math.max(0, bounds.top),
//                    Math.min(bounds.width(), fullImage.getWidth() - bounds.left),
//                    Math.min(bounds.height(), fullImage.getHeight() - bounds.top));
//
//            // Preprocess for model (resize to 48x48, grayscale, normalize)
//            Bitmap processedBitmap = Bitmap.createScaledBitmap(faceBitmap,
//                    MODEL_INPUT_SIZE, MODEL_INPUT_SIZE, true);
//
//            // Convert to model input format
//            ByteBuffer inputBuffer = convertBitmapToByteBuffer(processedBitmap);
//
//            // Run inference
//            float[][] output = new float[1][EMOTION_LABELS.length];
//            tfliteInterpreter.run(inputBuffer, output);
//
//            // Find emotion with highest confidence
//            int maxIndex = 0;
//            float maxConfidence = output[0][0];
//            for (int i = 1; i < output[0].length; i++) {
//                if (output[0][i] > maxConfidence) {
//                    maxConfidence = output[0][i];
//                    maxIndex = i;
//                }
//            }
//
//            if (maxConfidence >= CONFIDENCE_THRESHOLD) {
//                String emotion = EMOTION_LABELS[maxIndex];
//
//                // Record emotion data point
//                EmotionDataPoint dataPoint = new EmotionDataPoint(emotion,
//                        maxConfidence, currentVideoId);
//                sessionEmotions.add(dataPoint);
//
//                // Notify callback
//                if (callback != null) {
//                    callback.onEmotionDetected(emotion, maxConfidence);
//                }
//
//                // Log significant emotions (for therapeutic insights)
//                if (isSignificantEmotion(emotion, maxConfidence)) {
//                    logEmotionEvent(dataPoint);
//                }
//            }
//
//            // Clean up
//            faceBitmap.recycle();
//            processedBitmap.recycle();
//
//        } catch (Exception e) {
//            Log.e(TAG, "Emotion analysis failed", e);
//        }
//    }
//
//    /**
//     * Convert bitmap to model input format
//     */
//    private ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
//        ByteBuffer buffer = ByteBuffer.allocateDirect(4 * MODEL_INPUT_SIZE * MODEL_INPUT_SIZE);
//        buffer.order(ByteOrder.nativeOrder());
//
//        int[] pixels = new int[MODEL_INPUT_SIZE * MODEL_INPUT_SIZE];
//        bitmap.getPixels(pixels, 0, MODEL_INPUT_SIZE, 0, 0,
//                MODEL_INPUT_SIZE, MODEL_INPUT_SIZE);
//
//        for (int pixel : pixels) {
//            // Convert to grayscale and normalize to [0, 1]
//            float r = ((pixel >> 16) & 0xFF) / 255.0f;
//            float g = ((pixel >> 8) & 0xFF) / 255.0f;
//            float b = (pixel & 0xFF) / 255.0f;
//            float gray = 0.299f * r + 0.587f * g + 0.114f * b;
//            buffer.putFloat(gray);
//        }
//
//        return buffer;
//    }
//
//    /**
//     * Stop emotion monitoring
//     */
//    public void stopMonitoring() {
//        isAnalyzing = false;
//
//        // Save session summary
//        if (!sessionEmotions.isEmpty()) {
//            saveSessionSummary();
//        }
//    }
//
//    /**
//     * Save emotion session summary for guardian review
//     */
//    private void saveSessionSummary() {
//        Map<String, Object> summary = new HashMap<>();
//        summary.put("patientUid", patientUid);
//        summary.put("videoId", currentVideoId);
//        summary.put("timestamp", FieldValue.serverTimestamp());
//        summary.put("emotionCount", sessionEmotions.size());
//
//        // Calculate emotion distribution
//        Map<String, Integer> emotionCounts = new HashMap<>();
//        for (EmotionDataPoint point : sessionEmotions) {
//            emotionCounts.put(point.emotion,
//                    emotionCounts.getOrDefault(point.emotion, 0) + 1);
//        }
//        summary.put("emotionDistribution", emotionCounts);
//
//        // Find dominant emotion
//        String dominantEmotion = findDominantEmotion(emotionCounts);
//        summary.put("dominantEmotion", dominantEmotion);
//
//        // Calculate engagement score (positive emotions)
//        float engagementScore = calculateEngagementScore();
//        summary.put("engagementScore", engagementScore);
//
//        // Save to Firestore for guardian access
//        firestore.collection("emotion_sessions")
//                .add(summary)
//                .addOnSuccessListener(ref -> {
//                    Log.d(TAG, "Emotion session saved: " + ref.getId());
//                })
//                .addOnFailureListener(e -> {
//                    Log.e(TAG, "Failed to save emotion session", e);
//                });
//    }
//
//    /**
//     * Check if emotion is therapeutically significant
//     */
//    private boolean isSignificantEmotion(String emotion, float confidence) {
//        // Happy expressions during memory viewing are positive indicators
//        // Sad or fearful expressions might indicate distressing content
//        return (emotion.equals("Happy") && confidence > 0.7f) ||
//                (emotion.equals("Sad") && confidence > 0.8f) ||
//                (emotion.equals("Fearful") && confidence > 0.8f);
//    }
//
//    /**
//     * Log significant emotion event
//     */
//    private void logEmotionEvent(EmotionDataPoint dataPoint) {
//        Map<String, Object> event = new HashMap<>();
//        event.put("patientUid", patientUid);
//        event.put("emotion", dataPoint.emotion);
//        event.put("confidence", dataPoint.confidence);
//        event.put("videoId", dataPoint.videoId);
//        event.put("timestamp", FieldValue.serverTimestamp());
//
//        firestore.collection("emotion_events").add(event);
//    }
//
//    /**
//     * Calculate engagement score based on positive emotions
//     */
//    private float calculateEngagementScore() {
//        if (sessionEmotions.isEmpty()) return 0;
//
//        int positiveCount = 0;
//        for (EmotionDataPoint point : sessionEmotions) {
//            if (point.emotion.equals("Happy") || point.emotion.equals("Surprised")) {
//                positiveCount++;
//            }
//        }
//
//        return (float) positiveCount / sessionEmotions.size();
//    }
//
//    /**
//     * Find dominant emotion from counts
//     */
//    private String findDominantEmotion(Map<String, Integer> emotionCounts) {
//        String dominant = "Neutral";
//        int maxCount = 0;
//
//        for (Map.Entry<String, Integer> entry : emotionCounts.entrySet()) {
//            if (entry.getValue() > maxCount) {
//                maxCount = entry.getValue();
//                dominant = entry.getKey();
//            }
//        }
//
//        return dominant;
//    }
//
//    /**
//     * Convert ImageProxy to Bitmap
//     */
//    private Bitmap imageProxyToBitmap(ImageProxy image) {
//        Image.Plane[] planes = image.getPlanes();
//        ByteBuffer yBuffer = planes[0].getBuffer();
//        ByteBuffer uBuffer = planes[1].getBuffer();
//        ByteBuffer vBuffer = planes[2].getBuffer();
//
//        int ySize = yBuffer.remaining();
//        int uSize = uBuffer.remaining();
//        int vSize = vBuffer.remaining();
//
//        byte[] nv21 = new byte[ySize + uSize + vSize];
//        yBuffer.get(nv21, 0, ySize);
//        vBuffer.get(nv21, ySize, vSize);
//        uBuffer.get(nv21, ySize + vSize, uSize);
//
//        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21,
//                image.getWidth(), image.getHeight(), null);
//        ByteArrayOutputStream out = new ByteArrayOutputStream();
//        yuvImage.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()),
//                75, out);
//
//        byte[] imageBytes = out.toByteArray();
//        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
//    }
//
//    /**
//     * Clean up resources
//     */
//    public void release() {
//        isAnalyzing = false;
//
//        if (tfliteInterpreter != null) {
//            tfliteInterpreter.close();
//            tfliteInterpreter = null;
//        }
//
//        if (faceDetector != null) {
//            faceDetector.close();
//            faceDetector = null;
//        }
//
//        executor.shutdown();
//    }
//}