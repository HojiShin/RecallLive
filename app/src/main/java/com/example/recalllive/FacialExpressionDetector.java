//package com.example.recalllive;
//
//import android.Manifest;
//import android.content.Context;
//import android.content.pm.PackageManager;
//import android.graphics.Bitmap;
//import android.graphics.Canvas;
//import android.graphics.Matrix;
//import android.graphics.Rect;
//import android.util.Log;
//
//import androidx.camera.core.CameraSelector;
//import androidx.camera.core.ImageAnalysis;
//import androidx.camera.core.ImageProxy;
//import androidx.camera.lifecycle.ProcessCameraProvider;
//import androidx.core.content.ContextCompat;
//import androidx.lifecycle.LifecycleOwner;
//
//import com.google.common.util.concurrent.ListenableFuture;
//import com.google.firebase.firestore.FieldValue;
//import com.google.firebase.firestore.FirebaseFirestore;
//import com.google.mlkit.vision.common.InputImage;
//import com.google.mlkit.vision.face.Face;
//import com.google.mlkit.vision.face.FaceDetection;
//import com.google.mlkit.vision.face.FaceDetector;
//import com.google.mlkit.vision.face.FaceDetectorOptions;
//
//import org.tensorflow.lite.Interpreter;
//
//import java.io.File;
//import java.io.FileInputStream;
//import java.io.IOException;
//import java.nio.ByteBuffer;
//import java.nio.ByteOrder;
//import java.nio.MappedByteBuffer;
//import java.nio.channels.FileChannel;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
//
///**
// * Facial Expression Detection Service
// *
// * IMPORTANT ETHICAL CONSIDERATIONS:
// * - Only active when user explicitly enables it
// * - Shows clear on-screen indicator when monitoring
// * - Requires camera permission and explicit consent
// * - Data is anonymized and aggregated
// * - Can be disabled at any time by patient
// * - Should be reviewed by medical ethics board before deployment
// */
//public class FacialExpressionDetector {
//    private static final String TAG = "FacialExpressionDetector";
//
//    // Model constants
//    private static final int INPUT_SIZE = 48; // Adjust based on your model
//    private static final String MODEL_FILE = "model_filter.tflite";
//
//    // Expression labels (adjust based on your model's output)
//    private static final String[] EXPRESSIONS = {
//            "Angry", "Disgust", "Fear", "Happy", "Sad", "Surprise", "Neutral"
//    };
//
//    private final Context context;
//    private final String patientUid;
//    private final FirebaseFirestore firestore;
//    private Interpreter tflite;
//    private FaceDetector faceDetector;
//    private ExecutorService executorService;
//    private boolean isMonitoring = false;
//
//    // Analytics data
//    private Map<String, Integer> expressionCounts = new HashMap<>();
//    private long sessionStartTime;
//    private int totalFramesAnalyzed = 0;
//
//    public FacialExpressionDetector(Context context, String patientUid) {
//        this.context = context;
//        this.patientUid = patientUid;
//        this.firestore = FirebaseFirestore.getInstance();
//        this.executorService = Executors.newSingleThreadExecutor();
//
//        // Initialize expression counts
//        for (String expression : EXPRESSIONS) {
//            expressionCounts.put(expression, 0);
//        }
//    }
//
//    /**
//     * Initialize the facial expression detector
//     * Must be called before starting monitoring
//     */
//    public void initialize(FacialExpressionCallback callback) {
//        executorService.execute(() -> {
//            try {
//                // Load TFLite model
//                tflite = new Interpreter(loadModelFile());
//                Log.d(TAG, "TFLite model loaded successfully");
//
//                // Initialize ML Kit Face Detector
//                FaceDetectorOptions options = new FaceDetectorOptions.Builder()
//                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
//                        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
//                        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
//                        .build();
//
//                faceDetector = FaceDetection.getClient(options);
//                Log.d(TAG, "Face detector initialized");
//
//                if (callback != null) {
//                    callback.onInitialized();
//                }
//
//            } catch (Exception e) {
//                Log.e(TAG, "Failed to initialize detector", e);
//                if (callback != null) {
//                    callback.onError("Failed to initialize: " + e.getMessage());
//                }
//            }
//        });
//    }
//
//    /**
//     * Load the TFLite model from assets or file
//     */
//    private MappedByteBuffer loadModelFile() throws IOException {
//        // First try to load from app's files directory (where you should place model_filter.tflite)
//        File modelFile = new File(context.getFilesDir(), MODEL_FILE);
//
//        if (!modelFile.exists()) {
//            // Fallback: try to load from assets
//            try {
//                return loadModelFileFromAssets();
//            } catch (Exception e) {
//                throw new IOException("Model file not found. Please place model_filter.tflite in app's files directory", e);
//            }
//        }
//
//        FileInputStream inputStream = new FileInputStream(modelFile);
//        FileChannel fileChannel = inputStream.getChannel();
//        long startOffset = 0;
//        long declaredLength = fileChannel.size();
//        MappedByteBuffer buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
//        inputStream.close();
//        return buffer;
//    }
//
//    /**
//     * Load model from assets folder
//     */
//    private MappedByteBuffer loadModelFileFromAssets() throws IOException {
//        android.content.res.AssetFileDescriptor fileDescriptor =
//                context.getAssets().openFd(MODEL_FILE);
//        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
//        FileChannel fileChannel = inputStream.getChannel();
//        long startOffset = fileDescriptor.getStartOffset();
//        long declaredLength = fileDescriptor.getDeclaredLength();
//        MappedByteBuffer buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
//        inputStream.close();
//        return buffer;
//    }
//
//    /**
//     * Start monitoring facial expressions during video viewing
//     * Only call this when user has given explicit consent
//     */
//    public void startMonitoring(String videoId) {
//        if (!hasRequiredPermissions()) {
//            Log.e(TAG, "Camera permission not granted");
//            return;
//        }
//
//        if (tflite == null || faceDetector == null) {
//            Log.e(TAG, "Detector not initialized. Call initialize() first");
//            return;
//        }
//
//        isMonitoring = true;
//        sessionStartTime = System.currentTimeMillis();
//        totalFramesAnalyzed = 0;
//
//        // Reset counts for new session
//        for (String expression : EXPRESSIONS) {
//            expressionCounts.put(expression, 0);
//        }
//
//        Log.d(TAG, "Started monitoring facial expressions for video: " + videoId);
//    }
//
//    /**
//     * Stop monitoring and save aggregated data
//     */
//    public void stopMonitoring(String videoId) {
//        if (!isMonitoring) {
//            return;
//        }
//
//        isMonitoring = false;
//        long sessionDuration = System.currentTimeMillis() - sessionStartTime;
//
//        // Save aggregated session data
//        saveSessionData(videoId, sessionDuration);
//
//        Log.d(TAG, "Stopped monitoring. Total frames analyzed: " + totalFramesAnalyzed);
//    }
//
//    /**
//     * Analyze a single frame for facial expressions
//     */
//    public void analyzeFrame(Bitmap bitmap, FrameAnalysisCallback callback) {
//        if (!isMonitoring || bitmap == null) {
//            return;
//        }
//
//        executorService.execute(() -> {
//            try {
//                // Step 1: Detect face in the frame
//                InputImage image = InputImage.fromBitmap(bitmap, 0);
//                faceDetector.process(image)
//                        .addOnSuccessListener(faces -> {
//                            if (faces.isEmpty()) {
//                                // No face detected
//                                if (callback != null) {
//                                    callback.onNoFaceDetected();
//                                }
//                                return;
//                            }
//
//                            // Use the first detected face
//                            Face face = faces.get(0);
//                            Rect boundingBox = face.getBoundingBox();
//
//                            // Step 2: Crop face from bitmap
//                            Bitmap faceBitmap = cropFace(bitmap, boundingBox);
//                            if (faceBitmap == null) {
//                                return;
//                            }
//
//                            // Step 3: Classify expression
//                            String expression = classifyExpression(faceBitmap);
//
//                            // Step 4: Update counts
//                            if (expression != null) {
//                                expressionCounts.put(expression,
//                                        expressionCounts.getOrDefault(expression, 0) + 1);
//                                totalFramesAnalyzed++;
//
//                                if (callback != null) {
//                                    callback.onExpressionDetected(expression);
//                                }
//                            }
//
//                            faceBitmap.recycle();
//                        })
//                        .addOnFailureListener(e -> {
//                            Log.e(TAG, "Face detection failed", e);
//                        });
//
//            } catch (Exception e) {
//                Log.e(TAG, "Frame analysis error", e);
//            }
//        });
//    }
//
//    /**
//     * Crop face from bitmap using bounding box
//     */
//    private Bitmap cropFace(Bitmap original, Rect boundingBox) {
//        try {
//            // Ensure bounding box is within bitmap bounds
//            int x = Math.max(0, boundingBox.left);
//            int y = Math.max(0, boundingBox.top);
//            int width = Math.min(boundingBox.width(), original.getWidth() - x);
//            int height = Math.min(boundingBox.height(), original.getHeight() - y);
//
//            if (width <= 0 || height <= 0) {
//                return null;
//            }
//
//            Bitmap cropped = Bitmap.createBitmap(original, x, y, width, height);
//
//            // Resize to model input size
//            return Bitmap.createScaledBitmap(cropped, INPUT_SIZE, INPUT_SIZE, true);
//
//        } catch (Exception e) {
//            Log.e(TAG, "Error cropping face", e);
//            return null;
//        }
//    }
//
//    /**
//     * Classify facial expression using TFLite model
//     */
//    private String classifyExpression(Bitmap faceBitmap) {
//        try {
//            // Prepare input
//            ByteBuffer input = convertBitmapToByteBuffer(faceBitmap);
//
//            // Prepare output
//            float[][] output = new float[1][EXPRESSIONS.length];
//
//            // Run inference
//            tflite.run(input, output);
//
//            // Find highest probability
//            int maxIndex = 0;
//            float maxProb = output[0][0];
//            for (int i = 1; i < EXPRESSIONS.length; i++) {
//                if (output[0][i] > maxProb) {
//                    maxProb = output[0][i];
//                    maxIndex = i;
//                }
//            }
//
//            // Only return if confidence is above threshold
//            if (maxProb > 0.5f) {
//                return EXPRESSIONS[maxIndex];
//            }
//
//            return "Neutral"; // Default to neutral if low confidence
//
//        } catch (Exception e) {
//            Log.e(TAG, "Classification error", e);
//            return null;
//        }
//    }
//
//    /**
//     * Convert bitmap to ByteBuffer for model input
//     */
//    private ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
//        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 1);
//        byteBuffer.order(ByteOrder.nativeOrder());
//
//        int[] pixels = new int[INPUT_SIZE * INPUT_SIZE];
//        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);
//
//        for (int pixel : pixels) {
//            // Convert to grayscale and normalize
//            int r = (pixel >> 16) & 0xFF;
//            int g = (pixel >> 8) & 0xFF;
//            int b = pixel & 0xFF;
//            float gray = (r + g + b) / 3.0f / 255.0f;
//            byteBuffer.putFloat(gray);
//        }
//
//        return byteBuffer;
//    }
//
//    /**
//     * Save aggregated session data to Firestore
//     * Only saves percentages and aggregated data, not individual frames
//     */
//    private void saveSessionData(String videoId, long durationMs) {
//        if (totalFramesAnalyzed == 0) {
//            Log.d(TAG, "No frames analyzed, skipping data save");
//            return;
//        }
//
//        // Calculate percentages
//        Map<String, Object> sessionData = new HashMap<>();
//        sessionData.put("patientUid", patientUid);
//        sessionData.put("videoId", videoId);
//        sessionData.put("timestamp", FieldValue.serverTimestamp());
//        sessionData.put("durationMs", durationMs);
//        sessionData.put("totalFrames", totalFramesAnalyzed);
//
//        // Add expression percentages
//        Map<String, Double> percentages = new HashMap<>();
//        for (Map.Entry<String, Integer> entry : expressionCounts.entrySet()) {
//            double percentage = (entry.getValue() * 100.0) / totalFramesAnalyzed;
//            percentages.put(entry.getKey(), percentage);
//        }
//        sessionData.put("expressionPercentages", percentages);
//
//        // Save to Firestore
//        firestore.collection("expression_analytics")
//                .add(sessionData)
//                .addOnSuccessListener(ref -> {
//                    Log.d(TAG, "Session data saved: " + ref.getId());
//                })
//                .addOnFailureListener(e -> {
//                    Log.e(TAG, "Failed to save session data", e);
//                });
//    }
//
//    /**
//     * Check if app has required permissions
//     */
//    private boolean hasRequiredPermissions() {
//        return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
//                == PackageManager.PERMISSION_GRANTED;
//    }
//
//    /**
//     * Release resources
//     */
//    public void release() {
//        isMonitoring = false;
//
//        if (tflite != null) {
//            tflite.close();
//            tflite = null;
//        }
//
//        if (faceDetector != null) {
//            faceDetector.close();
//        }
//
//        if (executorService != null) {
//            executorService.shutdown();
//        }
//    }
//
//    // Callback interfaces
//    public interface FacialExpressionCallback {
//        void onInitialized();
//        void onError(String error);
//    }
//
//    public interface FrameAnalysisCallback {
//        void onExpressionDetected(String expression);
//        void onNoFaceDetected();
//    }
//}