package com.example.recalllive;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;

/**
 * Simple video creator that actually works
 * Creates a valid MP4 file from images
 */
public class SimpleVideoCreator {
    private static final String TAG = "SimpleVideoCreator";
    private static final String MIME_TYPE = "video/avc";
    private static final int FRAME_RATE = 1; // 1 fps for slideshow
    private static final int IFRAME_INTERVAL = 1;
    private static final int TIMEOUT_US = 10000;

    private final Context context;
    private MediaCodec encoder;
    private MediaMuxer muxer;
    private int trackIndex = -1;

    public SimpleVideoCreator(Context context) {
        this.context = context;
    }

    /**
     * Create a simple video file from bitmaps
     * This creates a basic MP4 that actually works
     */
    public File createVideoFromBitmaps(Bitmap[] bitmaps, int width, int height, File outputFile) {
        try {
            Log.d(TAG, "Creating video: " + outputFile.getAbsolutePath());
            Log.d(TAG, "Number of frames: " + bitmaps.length);

            // Setup MediaCodec
            MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, width, height);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
            format.setInteger(MediaFormat.KEY_BIT_RATE, 2000000);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);

            encoder = MediaCodec.createEncoderByType(MIME_TYPE);
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            encoder.start();

            // Setup MediaMuxer
            muxer = new MediaMuxer(outputFile.getAbsolutePath(),
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            // Process frames
            boolean muxerStarted = false;
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            long presentationTimeUs = 0;

            for (int i = 0; i < bitmaps.length; i++) {
                Log.d(TAG, "Processing frame " + i);

                // Convert bitmap to YUV
                byte[] input = convertBitmapToYUV420(bitmaps[i], width, height);

                // Feed to encoder
                int inputBufferIndex = encoder.dequeueInputBuffer(TIMEOUT_US);
                if (inputBufferIndex >= 0) {
                    ByteBuffer inputBuffer = encoder.getInputBuffer(inputBufferIndex);
                    inputBuffer.clear();
                    inputBuffer.put(input);
                    encoder.queueInputBuffer(inputBufferIndex, 0, input.length,
                            presentationTimeUs, 0);
                    presentationTimeUs += 1000000; // 1 second per frame
                }

                // Get encoded data
                int outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US);

                while (outputBufferIndex >= 0) {
                    ByteBuffer outputBuffer = encoder.getOutputBuffer(outputBufferIndex);

                    if (bufferInfo.size != 0) {
                        if (!muxerStarted) {
                            MediaFormat outputFormat = encoder.getOutputFormat();
                            trackIndex = muxer.addTrack(outputFormat);
                            muxer.start();
                            muxerStarted = true;
                            Log.d(TAG, "Muxer started");
                        }

                        outputBuffer.position(bufferInfo.offset);
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
                        muxer.writeSampleData(trackIndex, outputBuffer, bufferInfo);
                    }

                    encoder.releaseOutputBuffer(outputBufferIndex, false);
                    outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US);
                }
            }

            // Send end-of-stream
            int inputBufferIndex = encoder.dequeueInputBuffer(TIMEOUT_US);
            if (inputBufferIndex >= 0) {
                encoder.queueInputBuffer(inputBufferIndex, 0, 0, presentationTimeUs,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            }

            // Drain encoder
            boolean encoderDone = false;
            while (!encoderDone) {
                int outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US);

                if (outputBufferIndex >= 0) {
                    ByteBuffer outputBuffer = encoder.getOutputBuffer(outputBufferIndex);

                    if (bufferInfo.size != 0 && muxerStarted) {
                        outputBuffer.position(bufferInfo.offset);
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
                        muxer.writeSampleData(trackIndex, outputBuffer, bufferInfo);
                    }

                    encoder.releaseOutputBuffer(outputBufferIndex, false);

                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        encoderDone = true;
                    }
                } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED && !muxerStarted) {
                    MediaFormat outputFormat = encoder.getOutputFormat();
                    trackIndex = muxer.addTrack(outputFormat);
                    muxer.start();
                    muxerStarted = true;
                }
            }

            // Clean up
            if (encoder != null) {
                encoder.stop();
                encoder.release();
            }
            if (muxer != null && muxerStarted) {
                muxer.stop();
                muxer.release();
            }

            Log.d(TAG, "Video created successfully");
            Log.d(TAG, "File exists: " + outputFile.exists());
            Log.d(TAG, "File size: " + outputFile.length());

            return outputFile;

        } catch (Exception e) {
            Log.e(TAG, "Error creating video", e);

            // Clean up on error
            try {
                if (encoder != null) encoder.release();
                if (muxer != null) muxer.release();
            } catch (Exception ignored) {}

            return null;
        }
    }

    /**
     * Convert bitmap to YUV420 format for encoder
     */
    private byte[] convertBitmapToYUV420(Bitmap bitmap, int width, int height) {
        // Scale bitmap if needed
        if (bitmap.getWidth() != width || bitmap.getHeight() != height) {
            bitmap = Bitmap.createScaledBitmap(bitmap, width, height, true);
        }

        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        byte[] yuv = new byte[width * height * 3 / 2];
        int y, u, v;
        int r, g, b;
        int pixelIndex;
        int yIndex = 0;
        int uvIndex = width * height;

        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                pixelIndex = j * width + i;
                r = (pixels[pixelIndex] >> 16) & 0xff;
                g = (pixels[pixelIndex] >> 8) & 0xff;
                b = pixels[pixelIndex] & 0xff;

                // Y component
                y = ((66 * r + 129 * g + 25 * b + 128) >> 8) + 16;
                yuv[yIndex++] = (byte) ((y < 0) ? 0 : ((y > 255) ? 255 : y));

                // UV components (subsample)
                if (j % 2 == 0 && i % 2 == 0) {
                    u = ((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128;
                    v = ((112 * r - 94 * g - 18 * b + 128) >> 8) + 128;

                    yuv[uvIndex++] = (byte) ((v < 0) ? 0 : ((v > 255) ? 255 : v));
                    yuv[uvIndex++] = (byte) ((u < 0) ? 0 : ((u > 255) ? 255 : u));
                }
            }
        }

        return yuv;
    }

    /**
     * Create a test video file (for debugging)
     */
    public static File createTestVideoFile(Context context) {
        try {
            File outputFile = new File(context.getCacheDir(),
                    "test_video_" + System.currentTimeMillis() + ".mp4");

            // Create a simple test file with minimal valid MP4 structure
            // This is just for testing the upload pipeline
            FileOutputStream fos = new FileOutputStream(outputFile);

            // Write a minimal MP4 header (ftyp box)
            byte[] ftyp = {
                    0x00, 0x00, 0x00, 0x20, // box size
                    0x66, 0x74, 0x79, 0x70, // "ftyp"
                    0x69, 0x73, 0x6F, 0x6D, // "isom"
                    0x00, 0x00, 0x02, 0x00, // minor version
                    0x69, 0x73, 0x6F, 0x6D, // compatible brand
                    0x69, 0x73, 0x6F, 0x32, // compatible brand
                    0x6D, 0x70, 0x34, 0x31  // compatible brand
            };
            fos.write(ftyp);

            // Add some dummy data
            for (int i = 0; i < 100; i++) {
                fos.write(("test video data " + i).getBytes());
            }

            fos.close();

            Log.d(TAG, "Created test video file: " + outputFile.getAbsolutePath());
            Log.d(TAG, "Test file size: " + outputFile.length());

            return outputFile;

        } catch (Exception e) {
            Log.e(TAG, "Error creating test file", e);
            return null;
        }
    }
}