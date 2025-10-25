package com.example.recalllive;

import android.graphics.Bitmap;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.os.Build;
import android.util.Log;
import android.view.Surface;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Helper class for encoding images to video using Surface input
 * Uses OpenGL ES for rendering images to encoder surface
 */
public class VideoEncoderHelper {
    private static final String TAG = "VideoEncoderHelper";

    // Video settings
    private final int width;
    private final int height;
    private final int bitRate;
    private final int frameRate;
    private final File outputFile;

    // Media components
    private MediaCodec encoder;
    private MediaMuxer muxer;
    private Surface inputSurface;
    private int trackIndex = -1;
    private boolean muxerStarted = false;

    // OpenGL components
    private EGLDisplay eglDisplay;
    private EGLContext eglContext;
    private EGLSurface eglSurface;
    private int program;
    private int positionHandle;
    private int textureHandle;

    // OpenGL shaders
    private static final String VERTEX_SHADER =
            "attribute vec4 position;\n" +
                    "attribute vec2 texCoord;\n" +
                    "varying vec2 vTexCoord;\n" +
                    "uniform mat4 uMVPMatrix;\n" +
                    "void main() {\n" +
                    "    gl_Position = uMVPMatrix * position;\n" +
                    "    vTexCoord = texCoord;\n" +
                    "}";

    private static final String FRAGMENT_SHADER =
            "precision mediump float;\n" +
                    "varying vec2 vTexCoord;\n" +
                    "uniform sampler2D texture;\n" +
                    "uniform float alpha;\n" +
                    "void main() {\n" +
                    "    vec4 color = texture2D(texture, vTexCoord);\n" +
                    "    gl_FragColor = vec4(color.rgb, color.a * alpha);\n" +
                    "}";

    public VideoEncoderHelper(int width, int height, int bitRate, int frameRate, File outputFile) {
        this.width = width;
        this.height = height;
        this.bitRate = bitRate;
        this.frameRate = frameRate;
        this.outputFile = outputFile;
    }

    /**
     * Initialize encoder and OpenGL context
     */
    public void prepare() throws IOException {
        // Configure MediaCodec
        MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

        encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        inputSurface = encoder.createInputSurface();
        encoder.start();

        // Create MediaMuxer
        muxer = new MediaMuxer(outputFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

        // Setup OpenGL
        setupOpenGL();
    }

    /**
     * Setup OpenGL ES context and shaders
     */
    private void setupOpenGL() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        int[] version = new int[2];
        EGL14.eglInitialize(eglDisplay, version, 0, version, 1);

        // Configure EGL
        int[] attribList = {
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_NONE
        };

        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, configs.length, numConfigs, 0);

        // Create EGL context
        int[] contextAttribs = {
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
        };
        eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, contextAttribs, 0);

        // Create surface
        int[] surfaceAttribs = {
                EGL14.EGL_NONE
        };
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, configs[0], inputSurface, surfaceAttribs, 0);

        EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext);

        // Setup shaders
        setupShaders();
    }

    /**
     * Compile and link shaders
     */
    private void setupShaders() {
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER);
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);

        program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);
        GLES20.glLinkProgram(program);

        positionHandle = GLES20.glGetAttribLocation(program, "position");
        textureHandle = GLES20.glGetAttribLocation(program, "texCoord");
    }

    /**
     * Load shader from string
     */
    private int loadShader(int type, String shaderCode) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);
        return shader;
    }

    /**
     * Encode a bitmap as a video frame
     */
    public void encodeFrame(Bitmap bitmap, long presentationTimeUs) {
        // Make EGL context current
        EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext);

        // Clear the surface
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        // Draw bitmap to surface
        drawBitmap(bitmap);

        // Set presentation time and swap buffers
        // Note: We pass presentation time to the encoder through drainEncoder
        this.currentPresentationTimeUs = presentationTimeUs;
        EGL14.eglSwapBuffers(eglDisplay, eglSurface);

        // Drain encoder
        drainEncoder(false);
    }

    private long currentPresentationTimeUs = 0;

    /**
     * Draw bitmap using OpenGL
     */
    private void drawBitmap(Bitmap bitmap) {
        // Create texture from bitmap
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);

        // Use shader program
        GLES20.glUseProgram(program);

        // Set vertices
        float[] vertices = {
                -1.0f, -1.0f, 0.0f,  // Bottom left
                1.0f, -1.0f, 0.0f,  // Bottom right
                -1.0f,  1.0f, 0.0f,  // Top left
                1.0f,  1.0f, 0.0f   // Top right
        };

        FloatBuffer vertexBuffer = ByteBuffer.allocateDirect(vertices.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        vertexBuffer.put(vertices).position(0);

        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer);
        GLES20.glEnableVertexAttribArray(positionHandle);

        // Set texture coordinates
        float[] texCoords = {
                0.0f, 1.0f,
                1.0f, 1.0f,
                0.0f, 0.0f,
                1.0f, 0.0f
        };

        FloatBuffer texBuffer = ByteBuffer.allocateDirect(texCoords.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        texBuffer.put(texCoords).position(0);

        GLES20.glVertexAttribPointer(textureHandle, 2, GLES20.GL_FLOAT, false, 0, texBuffer);
        GLES20.glEnableVertexAttribArray(textureHandle);

        // Set MVP matrix (identity for now)
        float[] mvpMatrix = new float[16];
        Matrix.setIdentityM(mvpMatrix, 0);
        int mvpHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix");
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0);

        // Set alpha
        int alphaHandle = GLES20.glGetUniformLocation(program, "alpha");
        GLES20.glUniform1f(alphaHandle, 1.0f);

        // Draw
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        // Cleanup
        GLES20.glDeleteTextures(1, textures, 0);
    }

    /**
     * Drain encoder output
     */
    private void drainEncoder(boolean endOfStream) {
        if (endOfStream) {
            encoder.signalEndOfInputStream();
        }

        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

        while (true) {
            int encoderStatus = encoder.dequeueOutputBuffer(bufferInfo, 0);

            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) {
                    break;
                }
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (muxerStarted) {
                    throw new RuntimeException("Format changed twice");
                }

                MediaFormat newFormat = encoder.getOutputFormat();
                trackIndex = muxer.addTrack(newFormat);
                muxer.start();
                muxerStarted = true;

            } else if (encoderStatus < 0) {
                Log.w(TAG, "Unexpected encoder status: " + encoderStatus);

            } else {
                ByteBuffer encodedData = encoder.getOutputBuffer(encoderStatus);

                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    bufferInfo.size = 0;
                }

                if (bufferInfo.size != 0) {
                    if (!muxerStarted) {
                        throw new RuntimeException("Muxer not started");
                    }

                    encodedData.position(bufferInfo.offset);
                    encodedData.limit(bufferInfo.offset + bufferInfo.size);
                    muxer.writeSampleData(trackIndex, encodedData, bufferInfo);
                }

                encoder.releaseOutputBuffer(encoderStatus, false);

                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break;
                }
            }
        }
    }

    /**
     * Finish encoding
     */
    public void finish() {
        drainEncoder(true);
        release();
    }

    /**
     * Release all resources
     */
    public void release() {
        if (encoder != null) {
            encoder.stop();
            encoder.release();
            encoder = null;
        }

        if (muxer != null && muxerStarted) {
            muxer.stop();
            muxer.release();
            muxer = null;
        }

        if (eglDisplay != null) {
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
            EGL14.eglDestroySurface(eglDisplay, eglSurface);
            EGL14.eglDestroyContext(eglDisplay, eglContext);
            EGL14.eglReleaseThread();
            EGL14.eglTerminate(eglDisplay);
        }

        if (inputSurface != null) {
            inputSurface.release();
            inputSurface = null;
        }
    }
}