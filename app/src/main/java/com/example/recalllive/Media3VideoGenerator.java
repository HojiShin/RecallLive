package com.example.recalllive;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaExtractor;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;

import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@OptIn(markerClass = UnstableApi.class)
public class Media3VideoGenerator {
    private static final String TAG = "Media3VideoGenerator";

    // Video configuration
    private static final int VIDEO_WIDTH = 1280;
    private static final int VIDEO_HEIGHT = 720;
    private static final int VIDEO_BITRATE = 2_000_000;
    private static final int FRAME_RATE = 1;
    private static final String MIME_TYPE = "video/avc";
    private static final int IFRAME_INTERVAL = 1;
    private static final int TIMEOUT_US = 10000;

    private static final int PHOTOS_PER_VIDEO = 4;

    private final Context context;
    private final FirebaseFirestore firestore;
    private final FirebaseStorage storage;
    private final String patientUid;
    private final Random random;

    public Media3VideoGenerator(Context context, String patientUid) {
        this.context = context;
        this.firestore = FirebaseFirestore.getInstance();
        this.storage = FirebaseStorage.getInstance();
        this.patientUid = patientUid;
        this.random = new Random();
    }

    /**
     * Generate a video from a photo cluster with default duration
     */
    public void generateVideoFromCluster(PhotoClusteringManager.PhotoCluster cluster,
                                         VideoGenerationCallback callback) {
        // Default: 4 seconds per image
        generateVideoFromCluster(cluster, 0, callback);
    }

    /**
     * ADDED: Generate a video from a photo cluster with custom TTS duration
     */
    public void generateVideoFromCluster(PhotoClusteringManager.PhotoCluster cluster,
                                         int ttsDurationSeconds,
                                         VideoGenerationCallback callback) {
        Log.d(TAG, "Starting video generation for cluster: " + cluster.getClusterId());
        Log.d(TAG, "TTS duration: " + ttsDurationSeconds + " seconds");

        List<PhotoData> photos = cluster.getPhotos();

        if (photos == null || photos.isEmpty()) {
            callback.onError("No photos in cluster");
            return;
        }

        // Select up to 4 random photos
        int photosToUse = Math.min(photos.size(), PHOTOS_PER_VIDEO);
        List<PhotoData> selectedPhotos = selectRandomPhotos(photos, photosToUse);

        Log.d(TAG, "Selected " + selectedPhotos.size() + " photos from cluster of " +
                photos.size() + " photos");

        // Convert photos to video with custom duration
        createVideoFromPhotos(selectedPhotos, cluster, ttsDurationSeconds, callback);
    }

    /**
     * Select random photos from the cluster
     */
    private List<PhotoData> selectRandomPhotos(List<PhotoData> photos, int count) {
        List<PhotoData> selected = new ArrayList<>();

        if (photos.size() <= count) {
            return new ArrayList<>(photos);
        }

        List<PhotoData> shuffled = new ArrayList<>(photos);

        // Fisher-Yates shuffle
        for (int i = shuffled.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            PhotoData temp = shuffled.get(i);
            shuffled.set(i, shuffled.get(j));
            shuffled.set(j, temp);
        }

        for (int i = 0; i < count; i++) {
            selected.add(shuffled.get(i));
        }

        return selected;
    }

    /**
     * UPDATED: Create video from photos with configurable duration
     */
    private void createVideoFromPhotos(List<PhotoData> photos,
                                       PhotoClusteringManager.PhotoCluster cluster,
                                       int ttsDurationSeconds,
                                       VideoGenerationCallback callback) {
        try {
            Log.d(TAG, "Creating video from " + photos.size() + " photos");
            Log.d(TAG, "TTS duration: " + ttsDurationSeconds + " seconds");

            File outputFile = new File(context.getCacheDir(),
                    "memory_video_" + System.currentTimeMillis() + ".mp4");

            List<Bitmap> bitmaps = new ArrayList<>();
            int failedCount = 0;

            for (PhotoData photo : photos) {
                Bitmap bitmap = loadAndResizeBitmap(photo.getPhotoUri());
                if (bitmap != null) {
                    bitmaps.add(bitmap);
                } else {
                    failedCount++;
                    Log.w(TAG, "Failed to load bitmap for: " + photo.getPhotoUri());
                }
            }

            // Need at least 1 photo to make a video
            if (bitmaps.isEmpty()) {
                Log.e(TAG, "Failed to load ANY images - all photos missing or inaccessible");
                callback.onError("All photos are missing or inaccessible");
                return;
            }

            if (failedCount > 0) {
                Log.w(TAG, "⚠️ " + failedCount + " photo(s) were missing/inaccessible");
                Log.d(TAG, "✓ Continuing with " + bitmaps.size() + " available photos");
            }

            Log.d(TAG, "Loaded " + bitmaps.size() + " bitmaps successfully");

            // Calculate image duration based on TTS
            int imageDurationSeconds = calculateImageDurationSeconds(ttsDurationSeconds, bitmaps.size());
            Log.d(TAG, "═══════════════════════════════════════");
            Log.d(TAG, "VIDEO GENERATION PARAMETERS");
            Log.d(TAG, "TTS Duration Input: " + ttsDurationSeconds + " seconds");
            Log.d(TAG, "Image Duration: " + imageDurationSeconds + " seconds per image");
            Log.d(TAG, "Number of Images: " + bitmaps.size());
            Log.d(TAG, "Total Video Duration: " + (imageDurationSeconds * bitmaps.size()) + " seconds");
            Log.d(TAG, "═══════════════════════════════════════");
            Log.d(TAG, "Image duration: " + imageDurationSeconds + " seconds per image");
            Log.d(TAG, "Total video duration: " + (imageDurationSeconds * bitmaps.size()) + " seconds");

            boolean success = createVideoFile(bitmaps, outputFile, imageDurationSeconds);

            for (Bitmap bitmap : bitmaps) {
                if (bitmap != null && !bitmap.isRecycled()) {
                    bitmap.recycle();
                }
            }

            if (success && outputFile.exists()) {
                Log.d(TAG, "Video file created successfully: " + outputFile.length() + " bytes");
                int totalDuration = imageDurationSeconds * bitmaps.size();
                uploadVideoToFirebase(outputFile, cluster, totalDuration, callback);
            } else {
                callback.onError("Failed to create video file");
            }

        } catch (Exception e) {
            Log.e(TAG, "Error creating video", e);
            callback.onError("Failed to create video: " + e.getMessage());
        }
    }

    /**
     * ADDED: Calculate image duration based on TTS narration length
     */
    private int calculateImageDurationSeconds(int ttsDurationSeconds, int photoCount) {
        if (ttsDurationSeconds <= 0 || photoCount <= 0) {
            return 4; // Default 4 seconds per image
        }

        // Distribute TTS duration evenly across photos, minimum 3 seconds per image
        int durationPerImage = Math.max(3, (int) Math.ceil((double) ttsDurationSeconds / photoCount));

        // Cap at 10 seconds per image
        return Math.min(10, durationPerImage);
    }

    /**
     * UPDATED: Create video file with configurable image duration
     */
    private boolean createVideoFile(List<Bitmap> bitmaps, File outputFile, int imageDurationSeconds) {
        MediaCodec encoder = null;
        MediaMuxer muxer = null;

        try {
            Log.d(TAG, "=== Starting Video Encoding ===");
            Log.d(TAG, "Output file: " + outputFile.getAbsolutePath());
            Log.d(TAG, "Number of bitmaps: " + bitmaps.size());
            Log.d(TAG, "Image duration: " + imageDurationSeconds + " seconds");

            // Verify all bitmaps
            for (int i = 0; i < bitmaps.size(); i++) {
                Bitmap bmp = bitmaps.get(i);
                if (bmp == null) {
                    Log.e(TAG, "Bitmap " + i + " is null!");
                    return false;
                }
                if (bmp.getWidth() != VIDEO_WIDTH || bmp.getHeight() != VIDEO_HEIGHT) {
                    Log.e(TAG, "Bitmap " + i + " has wrong size: " +
                            bmp.getWidth() + "x" + bmp.getHeight());
                    return false;
                }
            }

            int colorFormat = findSupportedColorFormat();
            if (colorFormat == -1) {
                Log.e(TAG, "No supported color format found");
                return false;
            }

            MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, VIDEO_WIDTH, VIDEO_HEIGHT);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
            format.setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BITRATE);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);

            encoder = MediaCodec.createEncoderByType(MIME_TYPE);
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            encoder.start();

            muxer = new MediaMuxer(outputFile.getAbsolutePath(),
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            boolean muxerStarted = false;
            int trackIndex = -1;
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            long presentationTimeUs = 0;

            int framesPerImage = imageDurationSeconds * FRAME_RATE;

            for (int i = 0; i < bitmaps.size(); i++) {
                Bitmap bitmap = bitmaps.get(i);

                for (int frame = 0; frame < framesPerImage; frame++) {
                    byte[] input = convertBitmapToYUV420(bitmap, VIDEO_WIDTH, VIDEO_HEIGHT);

                    if (input == null || input.length == 0) {
                        Log.e(TAG, "Failed to convert bitmap to YUV");
                        return false;
                    }

                    int inputBufferIndex = encoder.dequeueInputBuffer(TIMEOUT_US);
                    if (inputBufferIndex >= 0) {
                        ByteBuffer inputBuffer = encoder.getInputBuffer(inputBufferIndex);
                        if (inputBuffer != null) {
                            inputBuffer.clear();
                            inputBuffer.put(input);
                            encoder.queueInputBuffer(inputBufferIndex, 0, input.length,
                                    presentationTimeUs, 0);
                            presentationTimeUs += 1000000L;
                        }
                    }

                    // Process encoder output
                    int outputIndex = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US);

                    if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        MediaFormat newFormat = encoder.getOutputFormat();
                        trackIndex = muxer.addTrack(newFormat);
                        muxer.start();
                        muxerStarted = true;
                    } else if (outputIndex >= 0) {
                        ByteBuffer outputBuffer = encoder.getOutputBuffer(outputIndex);

                        if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                                && bufferInfo.size != 0 && muxerStarted) {
                            outputBuffer.position(bufferInfo.offset);
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
                            muxer.writeSampleData(trackIndex, outputBuffer, bufferInfo);
                        }

                        encoder.releaseOutputBuffer(outputIndex, false);
                    }
                }
            }

            // Send end-of-stream
            int inputBufferIndex = encoder.dequeueInputBuffer(TIMEOUT_US);
            if (inputBufferIndex >= 0) {
                encoder.queueInputBuffer(inputBufferIndex, 0, 0, presentationTimeUs,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            }

            // Drain remaining output
            boolean done = false;
            while (!done) {
                int outputIndex = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US);

                if (outputIndex >= 0) {
                    ByteBuffer outputBuffer = encoder.getOutputBuffer(outputIndex);

                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                            && bufferInfo.size != 0 && muxerStarted) {
                        outputBuffer.position(bufferInfo.offset);
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
                        muxer.writeSampleData(trackIndex, outputBuffer, bufferInfo);
                    }

                    encoder.releaseOutputBuffer(outputIndex, false);

                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        done = true;
                    }
                }
            }

            Log.d(TAG, "Video encoding completed successfully");
            return outputFile.exists() && outputFile.length() > 0;

        } catch (Exception e) {
            Log.e(TAG, "Error in video encoding", e);
            e.printStackTrace();
            return false;

        } finally {
            try {
                if (encoder != null) {
                    encoder.stop();
                    encoder.release();
                }
                if (muxer != null) {
                    muxer.stop();
                    muxer.release();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error releasing resources", e);
            }
        }
    }

    private int findSupportedColorFormat() {
        try {
            MediaCodec codec = MediaCodec.createEncoderByType(MIME_TYPE);
            MediaCodecInfo.CodecCapabilities capabilities =
                    codec.getCodecInfo().getCapabilitiesForType(MIME_TYPE);
            codec.release();

            int[] preferredFormats = {
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar
            };

            for (int format : preferredFormats) {
                for (int supportedFormat : capabilities.colorFormats) {
                    if (supportedFormat == format) {
                        return format;
                    }
                }
            }

            if (capabilities.colorFormats.length > 0) {
                return capabilities.colorFormats[0];
            }

        } catch (Exception e) {
            Log.e(TAG, "Error finding color format", e);
        }

        return -1;
    }

    private byte[] convertBitmapToYUV420(Bitmap bitmap, int width, int height) {
        if (bitmap.getWidth() != width || bitmap.getHeight() != height) {
            Bitmap temp = Bitmap.createScaledBitmap(bitmap, width, height, true);
            if (temp != bitmap) {
                bitmap.recycle();
            }
            bitmap = temp;
        }

        if (bitmap.getConfig() != Bitmap.Config.ARGB_8888) {
            Bitmap temp = bitmap.copy(Bitmap.Config.ARGB_8888, false);
            if (temp != null) {
                if (temp != bitmap) {
                    bitmap.recycle();
                }
                bitmap = temp;
            }
        }

        int[] pixels = new int[width * height];
        try {
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        } catch (Exception e) {
            Log.e(TAG, "Error getting pixels from bitmap", e);
            return null;
        }

        byte[] yuv = new byte[width * height * 3 / 2];
        int yIndex = 0;
        int uvIndex = width * height;

        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                int pixelIndex = j * width + i;
                int pixel = pixels[pixelIndex];

                int r = (pixel >> 16) & 0xff;
                int g = (pixel >> 8) & 0xff;
                int b = pixel & 0xff;

                int y = ((66 * r + 129 * g + 25 * b + 128) >> 8) + 16;
                yuv[yIndex++] = (byte) Math.max(0, Math.min(255, y));

                if (j % 2 == 0 && i % 2 == 0) {
                    int u = ((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128;
                    int v = ((112 * r - 94 * g - 18 * b + 128) >> 8) + 128;

                    yuv[uvIndex++] = (byte) Math.max(0, Math.min(255, v));
                    yuv[uvIndex++] = (byte) Math.max(0, Math.min(255, u));
                }
            }
        }

        return yuv;
    }

    private Bitmap loadAndResizeBitmap(String photoUri) {
        try {
            Uri uri = Uri.parse(photoUri);
            InputStream input = context.getContentResolver().openInputStream(uri);

            if (input == null) {
                return null;
            }

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(input, null, options);
            input.close();

            int inSampleSize = calculateInSampleSize(
                    options.outWidth, options.outHeight, VIDEO_WIDTH, VIDEO_HEIGHT);

            input = context.getContentResolver().openInputStream(uri);
            options = new BitmapFactory.Options();
            options.inSampleSize = inSampleSize;
            options.inJustDecodeBounds = false;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;

            Bitmap bitmap = BitmapFactory.decodeStream(input, null, options);
            input.close();

            if (bitmap == null) {
                return null;
            }

            Bitmap scaled = Bitmap.createScaledBitmap(
                    bitmap, VIDEO_WIDTH, VIDEO_HEIGHT, true);

            if (bitmap != scaled) {
                bitmap.recycle();
            }

            return scaled;

        } catch (Exception e) {
            Log.e(TAG, "Error loading bitmap: " + photoUri, e);
            return null;
        }
    }

    private int calculateInSampleSize(int width, int height, int reqWidth, int reqHeight) {
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    private void uploadVideoToFirebase(File videoFile,
                                       PhotoClusteringManager.PhotoCluster cluster,
                                       int totalDurationSeconds,
                                       VideoGenerationCallback callback) {
        Log.d(TAG, "Uploading video to Firebase Storage");

        String fileName = "videos/" + patientUid + "/" +
                cluster.getClusterId() + "_" + System.currentTimeMillis() + ".mp4";

        StorageReference videoRef = storage.getReference().child(fileName);

        videoRef.putFile(Uri.fromFile(videoFile))
                .addOnProgressListener(taskSnapshot -> {
                    double progress = (100.0 * taskSnapshot.getBytesTransferred()) / taskSnapshot.getTotalByteCount();
                    Log.d(TAG, "Upload progress: " + progress + "%");
                })
                .addOnSuccessListener(taskSnapshot -> {
                    Log.d(TAG, "Video uploaded successfully");

                    videoRef.getDownloadUrl().addOnSuccessListener(uri -> {
                        String videoUrl = uri.toString();
                        Log.d(TAG, "Video download URL: " + videoUrl);

                        saveVideoToFirestore(videoUrl, cluster, totalDurationSeconds, callback);

                        if (videoFile.exists()) {
                            videoFile.delete();
                        }
                    }).addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to get download URL", e);
                        callback.onError("Failed to get video URL: " + e.getMessage());
                        if (videoFile.exists()) {
                            videoFile.delete();
                        }
                    });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to upload video", e);
                    callback.onError("Failed to upload video: " + e.getMessage());
                    if (videoFile.exists()) {
                        videoFile.delete();
                    }
                });
    }

    private void saveVideoToFirestore(String videoUrl,
                                      PhotoClusteringManager.PhotoCluster cluster,
                                      int durationSeconds,
                                      VideoGenerationCallback callback) {
        Log.d(TAG, "Saving video metadata to Firestore");

        Map<String, Object> videoData = new HashMap<>();
        videoData.put("patientUid", patientUid);
        videoData.put("clusterId", cluster.getClusterId());
        videoData.put("videoUrl", videoUrl);
        videoData.put("createdAt", FieldValue.serverTimestamp());
        videoData.put("photoCount", PHOTOS_PER_VIDEO);
        videoData.put("locationName", cluster.getLocationName());
        videoData.put("timeDescription", cluster.getTimeDescription());
        videoData.put("type", "daily_memory");
        videoData.put("duration", durationSeconds);

        firestore.collection("memory_videos")
                .add(videoData)
                .addOnSuccessListener(documentRef -> {
                    Log.d(TAG, "Video metadata saved to Firestore with ID: " + documentRef.getId());
                    callback.onSuccess(videoUrl, documentRef.getId());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to save video metadata to Firestore", e);
                    callback.onError("Failed to save video metadata: " + e.getMessage());
                });
    }

    public interface VideoGenerationCallback {
        void onSuccess(String videoUrl, String documentId);
        void onError(String error);
    }
}