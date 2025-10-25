package com.example.recalllive;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.util.Log;

import java.io.File;
import java.nio.ByteBuffer;

/**
 * Merges TTS audio with silent video to create final video with narration
 */
public class AudioVideoMerger {
    private static final String TAG = "AudioVideoMerger";
    private static final int TIMEOUT_US = 10000;

    public interface MergeCallback {
        void onMergeComplete(String outputPath);
        void onMergeError(String error);
    }

    /**
     * Merge audio and video files
     */
    public static void mergeAudioVideo(Context context, String videoPath, String audioPath,
                                       MergeCallback callback) {
        new Thread(() -> {
            try {
                Log.d(TAG, "═══════════════════════════════════════");
                Log.d(TAG, "Starting audio/video merge");
                Log.d(TAG, "Video: " + videoPath);
                Log.d(TAG, "Audio: " + audioPath);
                Log.d(TAG, "═══════════════════════════════════════");

                // Verify files exist
                File videoFile = new File(videoPath);
                File audioFile = new File(audioPath);

                if (!videoFile.exists()) {
                    callback.onMergeError("Video file does not exist: " + videoPath);
                    return;
                }

                if (!audioFile.exists()) {
                    callback.onMergeError("Audio file does not exist: " + audioPath);
                    return;
                }

                Log.d(TAG, "Video file size: " + videoFile.length() + " bytes");
                Log.d(TAG, "Audio file size: " + audioFile.length() + " bytes");

                // Create output file
                File outputFile = new File(context.getCacheDir(),
                        "merged_video_" + System.currentTimeMillis() + ".mp4");

                // Perform merge
                boolean success = merge(videoPath, audioPath, outputFile.getAbsolutePath());

                if (success && outputFile.exists() && outputFile.length() > 0) {
                    Log.d(TAG, "✓ Merge successful!");
                    Log.d(TAG, "Output file: " + outputFile.getAbsolutePath());
                    Log.d(TAG, "Output size: " + outputFile.length() + " bytes");
                    callback.onMergeComplete(outputFile.getAbsolutePath());
                } else {
                    Log.e(TAG, "✗ Merge failed or output file is empty");
                    callback.onMergeError("Merge failed");
                }

            } catch (Exception e) {
                Log.e(TAG, "✗ Error merging audio/video", e);
                callback.onMergeError("Merge error: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Perform the actual audio/video merge using MediaExtractor and MediaMuxer
     */
    private static boolean merge(String videoPath, String audioPath, String outputPath) {
        MediaExtractor videoExtractor = new MediaExtractor();
        MediaExtractor audioExtractor = new MediaExtractor();
        MediaMuxer muxer = null;

        try {
            // Setup video extractor
            videoExtractor.setDataSource(videoPath);

            // Setup audio extractor
            audioExtractor.setDataSource(audioPath);

            // Setup muxer
            muxer = new MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            // Add video track
            int videoTrackIndex = -1;
            int videoSourceTrack = -1;
            for (int i = 0; i < videoExtractor.getTrackCount(); i++) {
                MediaFormat format = videoExtractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith("video/")) {
                    videoExtractor.selectTrack(i);
                    videoTrackIndex = muxer.addTrack(format);
                    videoSourceTrack = i;
                    Log.d(TAG, "Video track added: " + mime);
                    break;
                }
            }

            // Add audio track
            int audioTrackIndex = -1;
            int audioSourceTrack = -1;
            for (int i = 0; i < audioExtractor.getTrackCount(); i++) {
                MediaFormat format = audioExtractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith("audio/")) {
                    audioExtractor.selectTrack(i);
                    audioTrackIndex = muxer.addTrack(format);
                    audioSourceTrack = i;
                    Log.d(TAG, "Audio track added: " + mime);
                    break;
                }
            }

            if (videoTrackIndex == -1) {
                Log.e(TAG, "No video track found");
                return false;
            }

            if (audioTrackIndex == -1) {
                Log.e(TAG, "No audio track found");
                return false;
            }

            // Start muxing
            muxer.start();

            // Mux video
            Log.d(TAG, "Muxing video track...");
            ByteBuffer buffer = ByteBuffer.allocate(1024 * 1024); // 1MB buffer
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

            videoExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            while (true) {
                int sampleSize = videoExtractor.readSampleData(buffer, 0);
                if (sampleSize < 0) {
                    break;
                }

                bufferInfo.offset = 0;
                bufferInfo.size = sampleSize;
                bufferInfo.presentationTimeUs = videoExtractor.getSampleTime();

                // Get sample flags properly
                int sampleFlags = videoExtractor.getSampleFlags();
                bufferInfo.flags = 0;
                if ((sampleFlags & MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                    bufferInfo.flags = MediaCodec.BUFFER_FLAG_KEY_FRAME;
                }

                muxer.writeSampleData(videoTrackIndex, buffer, bufferInfo);
                videoExtractor.advance();
            }
            Log.d(TAG, "✓ Video track muxed");

            // Mux audio
            Log.d(TAG, "Muxing audio track...");
            audioExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            while (true) {
                int sampleSize = audioExtractor.readSampleData(buffer, 0);
                if (sampleSize < 0) {
                    break;
                }

                bufferInfo.offset = 0;
                bufferInfo.size = sampleSize;
                bufferInfo.presentationTimeUs = audioExtractor.getSampleTime();

                // Get sample flags properly
                int sampleFlags = audioExtractor.getSampleFlags();
                bufferInfo.flags = 0;
                if ((sampleFlags & MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                    bufferInfo.flags = MediaCodec.BUFFER_FLAG_KEY_FRAME;
                }

                muxer.writeSampleData(audioTrackIndex, buffer, bufferInfo);
                audioExtractor.advance();
            }
            Log.d(TAG, "✓ Audio track muxed");

            Log.d(TAG, "✓ Merge complete");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Error during merge", e);
            e.printStackTrace();
            return false;

        } finally {
            try {
                videoExtractor.release();
                audioExtractor.release();
                if (muxer != null) {
                    muxer.stop();
                    muxer.release();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error releasing resources", e);
            }
        }
    }

    /**
     * Get video duration in milliseconds
     */
    public static long getVideoDuration(String videoPath) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(videoPath);
            String time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return Long.parseLong(time);
        } catch (Exception e) {
            Log.e(TAG, "Error getting video duration", e);
            return 0;
        } finally {
            try {
                retriever.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing retriever", e);
            }
        }
    }
}