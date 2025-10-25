package com.example.recalllive;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import java.io.File;
import java.nio.ByteBuffer;

/**
 * Video encoder that supports adding TTS audio track
 */
public class TTSVideoEncoder {
    private static final String TAG = "TTSVideoEncoder";

    private static final int VIDEO_WIDTH = 1280;
    private static final int VIDEO_HEIGHT = 720;
    private static final int VIDEO_BITRATE = 2_000_000;
    private static final int FRAME_RATE = 1;
    private static final String VIDEO_MIME = "video/avc";
    private static final int TIMEOUT_US = 10000;

    /**
     * Merge video frames with TTS audio
     */
    public static boolean mergeVideoWithAudio(File videoFile, File audioFile, File outputFile) {
        MediaMuxer muxer = null;
        MediaExtractor videoExtractor = null;
        MediaExtractor audioExtractor = null;

        try {
            Log.d(TAG, "═══════════════════════════════════════");
            Log.d(TAG, "MERGING VIDEO + AUDIO");
            Log.d(TAG, "Video: " + videoFile.getAbsolutePath());
            Log.d(TAG, "Audio: " + audioFile.getAbsolutePath());
            Log.d(TAG, "Output: " + outputFile.getAbsolutePath());
            Log.d(TAG, "═══════════════════════════════════════");

            // Setup extractors
            videoExtractor = new MediaExtractor();
            videoExtractor.setDataSource(videoFile.getAbsolutePath());

            audioExtractor = new MediaExtractor();
            audioExtractor.setDataSource(audioFile.getAbsolutePath());

            // Setup muxer
            muxer = new MediaMuxer(outputFile.getAbsolutePath(),
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            // Add video track
            int videoTrackIndex = -1;
            for (int i = 0; i < videoExtractor.getTrackCount(); i++) {
                MediaFormat format = videoExtractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith("video/")) {
                    videoExtractor.selectTrack(i);
                    videoTrackIndex = muxer.addTrack(format);
                    Log.d(TAG, "✓ Video track added: " + mime);
                    break;
                }
            }

            // Add audio track
            int audioTrackIndex = -1;
            for (int i = 0; i < audioExtractor.getTrackCount(); i++) {
                MediaFormat format = audioExtractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith("audio/")) {
                    audioExtractor.selectTrack(i);
                    audioTrackIndex = muxer.addTrack(format);
                    Log.d(TAG, "✓ Audio track added: " + mime);
                    break;
                }
            }

            if (videoTrackIndex == -1) {
                Log.e(TAG, "❌ No video track found");
                return false;
            }

            if (audioTrackIndex == -1) {
                Log.e(TAG, "❌ No audio track found");
                return false;
            }

            // Start muxing
            muxer.start();

            // Mux video
            Log.d(TAG, "Muxing video track...");
            ByteBuffer buffer = ByteBuffer.allocate(1024 * 1024);
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

            videoExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            while (true) {
                int sampleSize = videoExtractor.readSampleData(buffer, 0);
                if (sampleSize < 0) break;

                bufferInfo.offset = 0;
                bufferInfo.size = sampleSize;
                bufferInfo.presentationTimeUs = videoExtractor.getSampleTime();
                bufferInfo.flags = videoExtractor.getSampleFlags();

                muxer.writeSampleData(videoTrackIndex, buffer, bufferInfo);
                videoExtractor.advance();
            }
            Log.d(TAG, "✓ Video track muxed");

            // Mux audio
            Log.d(TAG, "Muxing audio track...");
            audioExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            while (true) {
                int sampleSize = audioExtractor.readSampleData(buffer, 0);
                if (sampleSize < 0) break;

                bufferInfo.offset = 0;
                bufferInfo.size = sampleSize;
                bufferInfo.presentationTimeUs = audioExtractor.getSampleTime();
                bufferInfo.flags = audioExtractor.getSampleFlags();

                muxer.writeSampleData(audioTrackIndex, buffer, bufferInfo);
                audioExtractor.advance();
            }
            Log.d(TAG, "✓ Audio track muxed");

            Log.d(TAG, "╔═══════════════════════════════════════╗");
            Log.d(TAG, "║  ✓✓✓ VIDEO + AUDIO MERGED ✓✓✓     ║");
            Log.d(TAG, "╚═══════════════════════════════════════╝");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "❌ Error merging video and audio", e);
            return false;

        } finally {
            try {
                if (videoExtractor != null) videoExtractor.release();
                if (audioExtractor != null) audioExtractor.release();
                if (muxer != null) {
                    muxer.stop();
                    muxer.release();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error releasing resources", e);
            }
        }
    }
}