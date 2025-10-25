//package com.example.recalllive;
//
//import android.Manifest;
//import android.content.pm.PackageManager;
//import android.graphics.Bitmap;
//import android.graphics.Matrix;
//import android.os.Bundle;
//import android.util.Log;
//import android.view.View;
//import android.widget.ImageView;
//import android.widget.TextView;
//import android.widget.Toast;
//import android.widget.VideoView;
//
//import androidx.annotation.NonNull;
//import androidx.appcompat.app.AppCompatActivity;
//import androidx.camera.core.Camera;
//import androidx.camera.core.CameraSelector;
//import androidx.camera.core.ImageAnalysis;
//import androidx.camera.core.ImageProxy;
//import androidx.camera.core.Preview;
//import androidx.camera.lifecycle.ProcessCameraProvider;
//import androidx.camera.view.PreviewView;
//import androidx.core.app.ActivityCompat;
//import androidx.core.content.ContextCompat;
//
//import com.google.common.util.concurrent.ListenableFuture;
//
//import java.nio.ByteBuffer;
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
//
///**
// * Video player that monitors facial expressions while patient watches
// * Shows small camera preview and expression indicator
// */
//public class VideoPlayerWithExpressionActivity extends AppCompatActivity {
//    private static final String TAG = "VideoPlayerExpression";
//    private static final int CAMERA_PERMISSION_CODE = 101;
//
//    // UI Elements
//    private VideoView videoView;
//    private PreviewView cameraPreviewView;
//    private ImageView ivMonitoringIndicator;
//    private TextView tvCurrentExpression;
//
//    // Expression detection
//    private FacialExpressionDetector expressionDetector;
//    private ExecutorService cameraExecutor;
//    private Camera camera;
//
//    // Video info
//    private String videoUrl;
//    private String videoId;
//    private String patientUid;
//    private boolean monitoringEnabled = false;
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_videoplayerwithexpression);
//
//        // Get video info from intent
//        videoUrl = getIntent().getStringExtra("videoUrl");
//        videoId = getIntent().getStringExtra("videoId");
//        patientUid = getIntent().getStringExtra("patientUid");
//
//        // Initialize views
//        videoView = findViewById(R.id.video_view);
//        cameraPreviewView = findViewById(R.id.camera_preview);
//        ivMonitoringIndicator = findViewById(R.id.iv_monitoring_indicator);
//        tvCurrentExpression = findViewById(R.id.tv_current_expression);
//
//        // Initialize expression detector
//        expressionDetector = new FacialExpressionDetector(this, patientUid);
//        cameraExecutor = Executors.newSingleThreadExecutor();
//
//        // Check consent and permissions
//        checkConsentAndSetup();
//    }
//
//    /**
//     * Check if user has given consent for expression monitoring
//     */
//    private void checkConsentAndSetup() {
//        ExpressionConsentDialog consentDialog = new ExpressionConsentDialog(this, patientUid);
//
//        if (!consentDialog.hasConsent()) {
//            // Show consent dialog first time
//            consentDialog.showConsentDialog(new ExpressionConsentDialog.ConsentCallback() {
//                @Override
//                public void onConsentGiven() {
//                    checkCameraPermissionAndStart();
//                }
//
//                @Override
//                public void onConsentDenied() {
//                    // Play video without monitoring
//                    hideMonitoringUI();
//                    startVideoPlayback();
//                }
//            });
//        } else {
//            // Consent already given, check camera permission
//            checkCameraPermissionAndStart();
//        }
//    }
//
//    /**
//     * Check camera permission
//     */
//    private void checkCameraPermissionAndStart() {
//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
//                != PackageManager.PERMISSION_GRANTED) {
//            ActivityCompat.requestPermissions(this,
//                    new String[]{Manifest.permission.CAMERA},
//                    CAMERA_PERMISSION_CODE);
//        } else {
//            initializeExpressionMonitoring();
//        }
//    }
//
//    @Override
//    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
//                                           @NonNull int[] grantResults) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
//
//        if (requestCode == CAMERA_PERMISSION_CODE) {
//            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                initializeExpressionMonitoring();
//            } else {
//                Toast.makeText(this, "Camera permission required for expression monitoring",
//                        Toast.LENGTH_LONG).show();
//                hideMonitoringUI();
//                startVideoPlayback();
//            }
//        }
//    }
//
//    /**
//     * Initialize expression monitoring
//     */
//    private void initializeExpressionMonitoring() {
//        Log.d(TAG, "Initializing expression monitoring");
//
//        // Show monitoring UI
//        showMonitoringUI();
//
//        // Initialize detector
//        expressionDetector.initialize(new FacialExpressionDetector.FacialExpressionCallback() {
//            @Override
//            public void onInitialized() {
//                runOnUiThread(() -> {
//                    Log.d(TAG, "Expression detector initialized");
//                    startCamera();
//                    startVideoPlayback();
//                });
//            }
//
//            @Override
//            public void onError(String error) {
//                runOnUiThread(() -> {
//                    Log.e(TAG, "Failed to initialize detector: " + error);
//                    Toast.makeText(VideoPlayerWithExpressionActivity.this,
//                            "Expression monitoring unavailable", Toast.LENGTH_SHORT).show();
//                    hideMonitoringUI();
//                    startVideoPlayback();
//                });
//            }
//        });
//    }
//
//    /**
//     * Start camera for expression monitoring
//     */
//    private void startCamera() {
//        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
//                ProcessCameraProvider.getInstance(this);
//
//        cameraProviderFuture.addListener(() -> {
//            try {
//                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
//                bindCameraUseCases(cameraProvider);
//            } catch (Exception e) {
//                Log.e(TAG, "Error starting camera", e);
//            }
//        }, ContextCompat.getMainExecutor(this));
//    }
//
//    /**
//     * Bind camera use cases
//     */
//    private void bindCameraUseCases(ProcessCameraProvider cameraProvider) {
//        // Preview use case
//        Preview preview = new Preview.Builder().build();
//        preview.setSurfaceProvider(cameraPreviewView.getSurfaceProvider());
//
//        // Image analysis use case for expression detection
//        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
//                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
//                .build();
//
//        imageAnalysis.setAnalyzer(cameraExecutor, image -> {
//            analyzeImage(image);
//        });
//
//        // Select front camera
//        CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
//
//        try {
//            // Unbind all use cases before rebinding
//            cameraProvider.unbindAll();
//
//            // Bind use cases to camera
//            camera = cameraProvider.bindToLifecycle(
//                    this, cameraSelector, preview, imageAnalysis);
//
//            // Start monitoring
//            monitoringEnabled = true;
//            expressionDetector.startMonitoring(videoId);
//
//            Log.d(TAG, "Camera started with expression monitoring");
//
//        } catch (Exception e) {
//            Log.e(TAG, "Failed to bind camera use cases", e);
//        }
//    }
//
//    /**
//     * Analyze image for facial expressions
//     */
//    private void analyzeImage(ImageProxy image) {
//        if (!monitoringEnabled) {
//            image.close();
//            return;
//        }
//
//        try {
//            // Convert ImageProxy to Bitmap
//            Bitmap bitmap = imageProxyToBitmap(image);
//
//            if (bitmap != null) {
//                // Analyze the frame
//                expressionDetector.analyzeFrame(bitmap,
//                        new FacialExpressionDetector.FrameAnalysisCallback() {
//                            @Override
//                            public void onExpressionDetected(String expression) {
//                                runOnUiThread(() -> {
//                                    updateExpressionDisplay(expression);
//                                });
//                            }
//
//                            @Override
//                            public void onNoFaceDetected() {
//                                runOnUiThread(() -> {
//                                    tvCurrentExpression.setText("Looking away...");
//                                });
//                            }
//                        });
//
//                bitmap.recycle();
//            }
//
//        } catch (Exception e) {
//            Log.e(TAG, "Error analyzing image", e);
//        } finally {
//            image.close();
//        }
//    }
//
//    /**
//     * Convert ImageProxy to Bitmap
//     */
//    private Bitmap imageProxyToBitmap(ImageProxy image) {
//        ImageProxy.PlaneProxy[] planes = image.getPlanes();
//        ByteBuffer buffer = planes[0].getBuffer();
//        byte[] bytes = new byte[buffer.remaining()];
//        buffer.get(bytes);
//
//        // Create bitmap from YUV data
//        // Note: This is a simplified version. You may need to adjust based on your image format
//        int width = image.getWidth();
//        int height = image.getHeight();
//
//        // Convert to RGB bitmap (simplified - you may need a proper YUV to RGB converter)
//        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
//
//        // Rotate bitmap for front camera (usually needs 270 degree rotation)
//        Matrix matrix = new Matrix();
//        matrix.postRotate(image.getImageInfo().getRotationDegrees());
//        Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
//
//        return rotated;
//    }
//
//    /**
//     * Update expression display
//     */
//    private void updateExpressionDisplay(String expression) {
//        tvCurrentExpression.setText("Feeling: " + expression);
//
//        // Optionally change indicator color based on expression
//        int color = getColorForExpression(expression);
//        ivMonitoringIndicator.setColorFilter(color);
//    }
//
//    /**
//     * Get color for expression (for visual feedback)
//     */
//    private int getColorForExpression(String expression) {
//        switch (expression.toLowerCase()) {
//            case "happy":
//                return ContextCompat.getColor(this, android.R.color.holo_green_light);
//            case "sad":
//                return ContextCompat.getColor(this, android.R.color.holo_blue_light);
//            case "angry":
//                return ContextCompat.getColor(this, android.R.color.holo_red_light);
//            case "surprise":
//                return ContextCompat.getColor(this, android.R.color.holo_orange_light);
//            default:
//                return ContextCompat.getColor(this, android.R.color.white);
//        }
//    }
//
//    /**
//     * Start video playback
//     */
//    private void startVideoPlayback() {
//        if (videoUrl != null) {
//            videoView.setVideoPath(videoUrl);
//            videoView.setOnCompletionListener(mp -> {
//                onVideoCompleted();
//            });
//            videoView.start();
//        }
//    }
//
//    /**
//     * Called when video playback completes
//     */
//    private void onVideoCompleted() {
//        if (monitoringEnabled) {
//            expressionDetector.stopMonitoring(videoId);
//        }
//        finish();
//    }
//
//    /**
//     * Show monitoring UI elements
//     */
//    private void showMonitoringUI() {
//        cameraPreviewView.setVisibility(View.VISIBLE);
//        ivMonitoringIndicator.setVisibility(View.VISIBLE);
//        tvCurrentExpression.setVisibility(View.VISIBLE);
//    }
//
//    /**
//     * Hide monitoring UI elements
//     */
//    private void hideMonitoringUI() {
//        cameraPreviewView.setVisibility(View.GONE);
//        ivMonitoringIndicator.setVisibility(View.GONE);
//        tvCurrentExpression.setVisibility(View.GONE);
//    }
//
//    @Override
//    protected void onPause() {
//        super.onPause();
//        if (monitoringEnabled) {
//            expressionDetector.stopMonitoring(videoId);
//        }
//    }
//
//    @Override
//    protected void onDestroy() {
//        super.onDestroy();
//        expressionDetector.release();
//        if (cameraExecutor != null) {
//            cameraExecutor.shutdown();
//        }
//    }
//}