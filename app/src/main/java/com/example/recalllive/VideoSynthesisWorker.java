//package com.example.recalllive;
//
//import android.content.Context;
//import android.graphics.Bitmap;
//import android.graphics.BitmapFactory;
//import android.media.MediaCodec;
//import android.media.MediaCodecInfo;
//import android.media.MediaExtractor;
//import android.media.MediaFormat;
//import android.media.MediaMuxer;
//import android.net.Uri;
//import android.util.Log;
//
//import androidx.annotation.NonNull;
//import androidx.work.Worker;
//import androidx.work.WorkerParameters;
//
//import com.google.api.gax.rpc.ApiException;
//import com.google.cloud.texttospeech.v1.AudioConfig;
//import com.google.cloud.texttospeech.v1.AudioEncoding;
//import com.google.cloud.texttospeech.v1.SynthesisInput;
//import com.google.cloud.texttospeech.v1.SynthesizeSpeechResponse;
//import com.google.cloud.texttospeech.v1.TextToSpeechClient;
//import com.google.cloud.texttospeech.v1.TextToSpeechSettings;
//import com.google.cloud.texttospeech.v1.VoiceSelectionParams;
//import com.google.firebase.storage.FirebaseStorage;
//import com.google.firebase.storage.StorageReference;
//import com.google.protobuf.ByteString;
//
//
//import java.io.File;
//import java.io.FileOutputStream;
//import java.io.IOException;
//import java.io.InputStream;
//import java.nio.ByteBuffer;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.concurrent.CountDownLatch;
//import java.util.concurrent.atomic.AtomicReference;
//
///**
// * Background worker to synthesize a video with narration from a cluster of photos.
// */
//public class VideoSynthesisWorker extends Worker {
//    private static final String TAG = "VideoSynthesisWorker";
//
//    private static final String TTS_API_KEY = "YOUR_GOOGLE_CLOUD_API_KEY";
//
//    public static final String KEY_MEMORY_ID = "memoryId";
//    public static final String KEY_USER_ID = "userId";
//    public static final String KEY_ALL_IMAGE_URIS = "allImageUris";
//
//    private static final int VIDEO_WIDTH = 1280;
//    private static final int VIDEO_HEIGHT = 720;
//    private static final int VIDEO_BITRATE = 2_000_000;
//    private static final long IMAGE_DURATION_SECONDS = 4;
//    private static final int FRAME_RATE = 30;
//    private static final String MIME_TYPE = "video/avc";
//    private static final int I_FRAME_INTERVAL = 1;
//    private static final int TIMEOUT_US = 10000;
//
//    public VideoSynthesisWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
//        super(context, workerParams);
//    }
//
//    @NonNull
//    @Override
//    public Result doWork() {
//        String memoryId = getInputData().getString(KEY_MEMORY_ID);
//        String userId = getInputData().getString(KEY_USER_ID);
//        String[] allImageUris = getInputData().getStringArray(KEY_ALL_IMAGE_URIS);
//
//        if (memoryId == null || userId == null || allImageUris == null || allImageUris.length == 0) {
//            Log.e(TAG, "Missing input data for video synthesis.");
//            return Result.failure();
//        }
//
//        Log.d(TAG, "Starting video synthesis for " + allImageUris.length + " photos.");
//        File narrationFile = null;
//        File outputFile = null;
//
//        try {
//            String script = fetchScriptFromFirestore(memoryId);
//            if (script == null || script.isEmpty()) {
//                Log.e(TAG, "Script is empty or could not be fetched. Aborting.");
//                return Result.failure();
//            }
//
//            narrationFile = generateNarrationAudio(script);
//            if (narrationFile == null) {
//                Log.e(TAG, "Failed to generate narration audio file.");
//                return Result.failure();
//            }
//
//            List<Bitmap> bitmaps = new ArrayList<>();
//            for (String uriString : allImageUris) {
//                Bitmap bitmap = loadAndResizeBitmap(uriString);
//                if (bitmap != null) {
//                    bitmaps.add(bitmap);
//                }
//            }
//
//            if (bitmaps.isEmpty()) {
//                Log.e(TAG, "Failed to load any bitmaps for video creation.");
//                return Result.failure();
//            }
//
//            outputFile = new File(getApplicationContext().getCacheDir(), "video_" + memoryId + ".mp4");
//            boolean videoCreated = createVideoFile(bitmaps, narrationFile, outputFile);
//
//            for (Bitmap bitmap : bitmaps) {
//                bitmap.recycle();
//            }
//
//            if (!videoCreated || !outputFile.exists()) {
//                Log.e(TAG, "Video file creation failed.");
//                return Result.failure();
//            }
//
//            String videoUrl = uploadVideoToFirebase(outputFile, userId, memoryId);
//
//            if (videoUrl == null) {
//                Log.e(TAG, "Failed to upload video to Firebase Storage.");
//                return Result.failure();
//            }
//
//            updateFirestoreDocument(memoryId, videoUrl);
//
//            Log.d(TAG, "Video synthesis completed successfully. URL: " + videoUrl);
//            return Result.success();
//
//        } catch (Exception e) {
//            Log.e(TAG, "An unhandled exception occurred during video synthesis.", e);
//            return Result.failure();
//        } finally {
//            if (narrationFile != null && narrationFile.exists()) {
//                narrationFile.delete();
//            }
//            if (outputFile != null && outputFile.exists()) {
//                outputFile.delete();
//            }
//        }
//    }
//
//    private String fetchScriptFromFirestore(String memoryId) {
//        AtomicReference<String> scriptRef = new AtomicReference<>();
//        CountDownLatch latch = new CountDownLatch(1);
//
//        MemoryRepository.getInstance().getMemoryById(memoryId, new MemoryRepository.OnMemoryLoadedCallback() {
//            @Override
//            public void onSuccess(Memory memory) {
//                scriptRef.set(memory.getScript());
//                latch.countDown();
//            }
//
//            @Override
//            public void onFailure(String error) {
//                Log.e(TAG, "Failed to fetch memory document: " + error);
//                latch.countDown();
//            }
//        });
//
//        try {
//            latch.await();
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//            return null;
//        }
//        return scriptRef.get();
//    }
//
//    private File generateNarrationAudio(String text) {
//        try {
//            TextToSpeechSettings settings = TextToSpeechSettings.newBuilder()
//                    .setEndpoint("texttospeech.googleapis.com:443")
//                    .setHeaderProvider(() -> java.util.Collections.singletonMap("X-Goog-Api-Key", TTS_API_KEY))
//                    .build();
//
//            try (TextToSpeechClient textToSpeechClient = TextToSpeechClient.create(settings)) {
//                SynthesisInput input = SynthesisInput.newBuilder().setText(text).build();
//                VoiceSelectionParams voice = VoiceSelectionParams.newBuilder()
//                        .setLanguageCode("en-US")
//                        .setName("en-US-Wavenet-D")
//                        .build();
//                AudioConfig audioConfig = AudioConfig.newBuilder()
//                        .setAudioEncoding(AudioEncoding.MP3)
//                        .build();
//
//                SynthesizeSpeechResponse response = textToSpeechClient.synthesizeSpeech(input, voice, audioConfig);
//                ByteString audioContents = response.getAudioContent();
//
//                File outputFile = new File(getApplicationContext().getCacheDir(), "narration_" + System.currentTimeMillis() + ".mp3");
//                try (FileOutputStream out = new FileOutputStream(outputFile)) {
//                    out.write(audioContents.toByteArray());
//                    Log.d(TAG, "Narration audio file created: " + outputFile.getAbsolutePath());
//                    return outputFile;
//                }
//            }
//        } catch (ApiException e) {
//            Log.e(TAG, "Google Cloud TTS API Error: " + e.getStatusCode().getCode() + " - " + e.getLocalizedMessage());
//            return null;
//        } catch (IOException e) {
//            Log.e(TAG, "Failed to create TTS client or write audio file.", e);
//            return null;
//        }
//    }
//
//    private boolean createVideoFile(List<Bitmap> bitmaps, File audioFile, File outputFile) {
//        MediaCodec encoder = null;
//        MediaMuxer muxer = null;
//        try {
//            MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, VIDEO_WIDTH, VIDEO_HEIGHT);
//            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
//            format.setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BITRATE);
//            format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
//            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL);
//
//            encoder = MediaCodec.createEncoderByType(MIME_TYPE);
//            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
//            encoder.start();
//
//            muxer = new MediaMuxer(outputFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
//
//            drainEncoder(encoder, muxer, bitmaps);
//            addAudioTrack(muxer, audioFile);
//
//            return true;
//        } catch (Exception e) {
//            Log.e(TAG, "Error during video file creation", e);
//            return false;
//        } finally {
//            try {
//                if (encoder != null) {
//                    encoder.stop();
//                    encoder.release();
//                }
//                if (muxer != null) {
//                    muxer.stop();
//                    muxer.release();
//                }
//            } catch (Exception e) {
//                Log.e(TAG, "Error releasing resources", e);
//            }
//        }
//    }
//
//    private void drainEncoder(MediaCodec encoder, MediaMuxer muxer, List<Bitmap> bitmaps) {
//        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
//        long presentationTimeUs = 0;
//        int trackIndex = -1;
//        boolean muxerStarted = false;
//
//        for (Bitmap bitmap : bitmaps) {
//            int framesPerImage = (int) (IMAGE_DURATION_SECONDS * FRAME_RATE);
//            for (int frame = 0; frame < framesPerImage; frame++) {
//                byte[] input = convertBitmapToYUV420(bitmap);
//                int inputBufferIndex = encoder.dequeueInputBuffer(TIMEOUT_US);
//                if (inputBufferIndex >= 0) {
//                    ByteBuffer inputBuffer = encoder.getInputBuffer(inputBufferIndex);
//                    inputBuffer.clear();
//                    inputBuffer.put(input);
//                    encoder.queueInputBuffer(inputBufferIndex, 0, input.length, presentationTimeUs, 0);
//                    presentationTimeUs += 1_000_000L / FRAME_RATE;
//                }
//
//                int outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US);
//                while (outputBufferIndex >= 0) {
//                    ByteBuffer outputBuffer = encoder.getOutputBuffer(outputBufferIndex);
//                    if (!muxerStarted) {
//                        trackIndex = muxer.addTrack(encoder.getOutputFormat());
//                        muxer.start();
//                        muxerStarted = true;
//                    }
//                    muxer.writeSampleData(trackIndex, outputBuffer, bufferInfo);
//                    encoder.releaseOutputBuffer(outputBufferIndex, false);
//                    outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US);
//                }
//            }
//        }
//        encoder.signalEndOfInputStream();
//    }
//
//    private void addAudioTrack(MediaMuxer muxer, File audioFile) throws IOException {
//        MediaExtractor extractor = new MediaExtractor();
//        extractor.setDataSource(audioFile.getAbsolutePath());
//        int trackCount = extractor.getTrackCount();
//        int audioTrackIndex = -1;
//        for (int i = 0; i < trackCount; i++) {
//            MediaFormat format = extractor.getTrackFormat(i);
//            String mime = format.getString(MediaFormat.KEY_MIME);
//            if (mime != null && mime.startsWith("audio/")) {
//                extractor.selectTrack(i);
//                audioTrackIndex = muxer.addTrack(format);
//                break;
//            }
//        }
//
//        if (audioTrackIndex == -1) {
//            Log.e(TAG, "No audio track found in the generated file.");
//            extractor.release();
//            return;
//        }
//
//        ByteBuffer buffer = ByteBuffer.allocate(1024 * 1024);
//        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
//
//        while (true) {
//            int sampleSize = extractor.readSampleData(buffer, 0);
//            if (sampleSize < 0) {
//                break;
//            }
//
//            bufferInfo.offset = 0;
//            bufferInfo.size = sampleSize;
//            bufferInfo.presentationTimeUs = extractor.getSampleTime();
//
//            // --- START OF CORRECTION ---
//            // Translate MediaExtractor flags to MediaCodec flags for the muxer.
//            int extractorFlags = extractor.getSampleFlags();
//            int muxerFlags = 0;
//            if ((extractorFlags & MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
//                muxerFlags |= MediaCodec.BUFFER_FLAG_KEY_FRAME;
//            }
//            bufferInfo.flags = muxerFlags;
//            // --- END OF CORRECTION ---
//
//            muxer.writeSampleData(audioTrackIndex, buffer, bufferInfo);
//            extractor.advance();
//        }
//
//        extractor.release();
//        Log.d(TAG, "Audio track has been successfully added to the muxer.");
//    }
//
//    private Bitmap loadAndResizeBitmap(String photoUri) {
//        try (InputStream input = getApplicationContext().getContentResolver().openInputStream(Uri.parse(photoUri))) {
//            if (input == null) return null;
//            BitmapFactory.Options options = new BitmapFactory.Options();
//            options.inJustDecodeBounds = true;
//            BitmapFactory.decodeStream(input, null, options);
//            input.close();
//
//            int inSampleSize = 1;
//            if (options.outHeight > VIDEO_HEIGHT || options.outWidth > VIDEO_WIDTH) {
//                final int halfHeight = options.outHeight / 2;
//                final int halfWidth = options.outWidth / 2;
//                while ((halfHeight / inSampleSize) >= VIDEO_HEIGHT && (halfWidth / inSampleSize) >= VIDEO_WIDTH) {
//                    inSampleSize *= 2;
//                }
//            }
//
//            try (InputStream sizedInput = getApplicationContext().getContentResolver().openInputStream(Uri.parse(photoUri))) {
//                options.inSampleSize = inSampleSize;
//                options.inJustDecodeBounds = false;
//                Bitmap bitmap = BitmapFactory.decodeStream(sizedInput, null, options);
//                Bitmap scaled = Bitmap.createScaledBitmap(bitmap, VIDEO_WIDTH, VIDEO_HEIGHT, true);
//                if (bitmap != scaled) bitmap.recycle();
//                return scaled;
//            }
//        } catch (Exception e) {
//            Log.e(TAG, "Error loading bitmap: " + photoUri, e);
//            return null;
//        }
//    }
//
//    private byte[] convertBitmapToYUV420(Bitmap bitmap) {
//        int width = bitmap.getWidth();
//        int height = bitmap.getHeight();
//        int[] pixels = new int[width * height];
//        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
//        byte[] yuv = new byte[width * height * 3 / 2];
//        int yIndex = 0;
//        int uvIndex = width * height;
//        for (int j = 0; j < height; j++) {
//            for (int i = 0; i < width; i++) {
//                int R = (pixels[j * width + i] >> 16) & 0xff;
//                int G = (pixels[j * width + i] >> 8) & 0xff;
//                int B = pixels[j * width + i] & 0xff;
//                int Y = ((66 * R + 129 * G + 25 * B + 128) >> 8) + 16;
//                yuv[yIndex++] = (byte) Math.max(0, Math.min(255, Y));
//                if (j % 2 == 0 && i % 2 == 0) {
//                    int U = ((-38 * R - 74 * G + 112 * B + 128) >> 8) + 128;
//                    int V = ((112 * R - 94 * G - 18 * B + 128) >> 8) + 128;
//                    yuv[uvIndex++] = (byte) Math.max(0, Math.min(255, V));
//                    yuv[uvIndex++] = (byte) Math.max(0, Math.min(255, U));
//                }
//            }
//        }
//        return yuv;
//    }
//
//    private String uploadVideoToFirebase(File videoFile, String userId, String memoryId) {
//        CountDownLatch latch = new CountDownLatch(1);
//        AtomicReference<String> downloadUrlRef = new AtomicReference<>();
//        StorageReference videoRef = FirebaseStorage.getInstance().getReference()
//                .child("videos/" + userId + "/" + memoryId + ".mp4");
//
//        videoRef.putFile(Uri.fromFile(videoFile)).addOnSuccessListener(taskSnapshot ->
//                videoRef.getDownloadUrl().addOnSuccessListener(uri -> {
//                    downloadUrlRef.set(uri.toString());
//                    latch.countDown();
//                }).addOnFailureListener(e -> latch.countDown())
//        ).addOnFailureListener(e -> latch.countDown());
//
//        try {
//            latch.await();
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//            return null;
//        }
//        return downloadUrlRef.get();
//    }
//
//    private void updateFirestoreDocument(String memoryId, String videoUrl) {
//        MemoryRepository.getInstance().updateVideoUrl(memoryId, videoUrl, new MemoryRepository.OnMemorySavedCallback() {
//            @Override
//            public void onSuccess(String documentId) {
//                Log.d(TAG, "Successfully updated Firestore for memory: " + documentId);
//            }
//            @Override
//            public void onFailure(String error) {
//                Log.e(TAG, "Failed to update Firestore for memory: " + memoryId + " Error: " + error);
//            }
//        });
//    }
//}