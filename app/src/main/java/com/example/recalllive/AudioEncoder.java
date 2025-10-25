package com.example.recalllive;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;

/**
 * Encodes raw PCM audio (from TTS WAV) to AAC format for video merging
 */
public class AudioEncoder {
    private static final String TAG = "AudioEncoder";
    private static final String MIME_TYPE = "audio/mp4a-latm"; // AAC
    private static final int SAMPLE_RATE = 16000; // TTS default
    private static final int CHANNEL_COUNT = 1; // Mono
    private static final int BIT_RATE = 64000; // 64kbps

    public interface EncodeCallback {
        void onEncodeComplete(String aacFilePath);
        void onEncodeError(String error);
    }

    /**
     * Convert TTS WAV file to AAC format
     */
    public static void convertWavToAAC(String wavPath, EncodeCallback callback) {
        new Thread(() -> {
            try {
                Log.d(TAG, "Converting WAV to AAC: " + wavPath);

                File wavFile = new File(wavPath);
                if (!wavFile.exists()) {
                    callback.onEncodeError("WAV file does not exist");
                    return;
                }

                // Create output AAC file
                File aacFile = new File(wavFile.getParent(),
                        "audio_" + System.currentTimeMillis() + ".aac");

                // Read WAV file (skip 44-byte header)
                FileInputStream fis = new FileInputStream(wavFile);
                byte[] header = new byte[44];
                fis.read(header); // Skip WAV header

                // Setup encoder
                MediaFormat format = MediaFormat.createAudioFormat(MIME_TYPE, SAMPLE_RATE, CHANNEL_COUNT);
                format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
                format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
                format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);

                MediaCodec encoder = MediaCodec.createEncoderByType(MIME_TYPE);
                encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                encoder.start();

                // Output file
                FileOutputStream fos = new FileOutputStream(aacFile);

                // Encode
                boolean inputDone = false;
                boolean outputDone = false;
                byte[] inputBuffer = new byte[4096];
                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

                while (!outputDone) {
                    // Feed input
                    if (!inputDone) {
                        int inputBufferId = encoder.dequeueInputBuffer(10000);
                        if (inputBufferId >= 0) {
                            ByteBuffer inputByteBuffer = encoder.getInputBuffer(inputBufferId);
                            if (inputByteBuffer != null) {
                                int bytesRead = fis.read(inputBuffer);
                                if (bytesRead > 0) {
                                    inputByteBuffer.clear();
                                    inputByteBuffer.put(inputBuffer, 0, bytesRead);
                                    encoder.queueInputBuffer(inputBufferId, 0, bytesRead, 0, 0);
                                } else {
                                    encoder.queueInputBuffer(inputBufferId, 0, 0, 0,
                                            MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                    inputDone = true;
                                }
                            }
                        }
                    }

                    // Get output
                    int outputBufferId = encoder.dequeueOutputBuffer(bufferInfo, 10000);
                    if (outputBufferId >= 0) {
                        ByteBuffer outputBuffer = encoder.getOutputBuffer(outputBufferId);
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            byte[] outData = new byte[bufferInfo.size];
                            outputBuffer.get(outData);
                            fos.write(outData);
                        }

                        encoder.releaseOutputBuffer(outputBufferId, false);

                        if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true;
                        }
                    }
                }

                // Cleanup
                fis.close();
                fos.close();
                encoder.stop();
                encoder.release();

                Log.d(TAG, "✓ WAV converted to AAC: " + aacFile.getAbsolutePath());
                callback.onEncodeComplete(aacFile.getAbsolutePath());

            } catch (Exception e) {
                Log.e(TAG, "Error encoding audio", e);
                callback.onEncodeError("Encoding failed: " + e.getMessage());
            }
        }).start();
    }
}