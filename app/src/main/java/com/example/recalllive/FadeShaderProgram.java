package com.example.recalllive;

import android.content.Context;
import android.opengl.GLES20;

import androidx.annotation.OptIn;
import androidx.media3.common.util.GlProgram;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.Size;
import androidx.media3.common.util.UnstableApi;

import java.nio.FloatBuffer;

/**
 * Custom shader program for fade in/out effects
 * Simplified implementation for Media3 compatibility
 */
@OptIn(markerClass = UnstableApi.class)
public class FadeShaderProgram {

    private static final String VERTEX_SHADER =
            "attribute vec4 aPosition;\n" +
                    "attribute vec4 aTexCoord;\n" +
                    "varying vec2 vTexCoord;\n" +
                    "void main() {\n" +
                    "  gl_Position = aPosition;\n" +
                    "  vTexCoord = aTexCoord.xy;\n" +
                    "}";

    private static final String FRAGMENT_SHADER_TEMPLATE =
            "precision mediump float;\n" +
                    "uniform sampler2D uTexture;\n" +
                    "uniform float uAlpha;\n" +
                    "varying vec2 vTexCoord;\n" +
                    "void main() {\n" +
                    "  vec4 color = texture2D(uTexture, vTexCoord);\n" +
                    "  gl_FragColor = vec4(color.rgb, color.a * uAlpha);\n" +
                    "}";

    private final boolean isFadeIn;
    private final long durationUs;
    private int program;
    private int alphaLocation;
    private int positionLocation;
    private int texCoordLocation;
    private long currentTimeUs = 0;

    public FadeShaderProgram(boolean isFadeIn, long durationUs) {
        this.isFadeIn = isFadeIn;
        this.durationUs = durationUs;
    }

    /**
     * Initialize the shader program
     */
    public void initialize() throws GlUtil.GlException {
        // Create and compile shaders
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER);
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_TEMPLATE);

        // Create program and link shaders
        program = GLES20.glCreateProgram();
        GlUtil.checkGlError();

        GLES20.glAttachShader(program, vertexShader);
        GlUtil.checkGlError();

        GLES20.glAttachShader(program, fragmentShader);
        GlUtil.checkGlError();

        GLES20.glLinkProgram(program);
        GlUtil.checkGlError();

        // Check link status
        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] != GLES20.GL_TRUE) {
            String error = GLES20.glGetProgramInfoLog(program);
            throw new GlUtil.GlException("Program link failed: " + error);
        }

        // Get uniform and attribute locations
        alphaLocation = GLES20.glGetUniformLocation(program, "uAlpha");
        positionLocation = GLES20.glGetAttribLocation(program, "aPosition");
        texCoordLocation = GLES20.glGetAttribLocation(program, "aTexCoord");

        // Clean up shaders (they're now part of the program)
        GLES20.glDeleteShader(vertexShader);
        GLES20.glDeleteShader(fragmentShader);
    }

    /**
     * Draw frame with fade effect
     */
    public void drawFrame(long presentationTimeUs, int textureId, FloatBuffer vertexBuffer, FloatBuffer texCoordBuffer) {
        // Calculate alpha based on time
        float progress = Math.min(1.0f, (float) presentationTimeUs / durationUs);
        float alpha = isFadeIn ? progress : (1.0f - progress);

        // Use shader program
        GLES20.glUseProgram(program);

        // Set alpha uniform
        GLES20.glUniform1f(alphaLocation, alpha);

        // Bind texture
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);

        // Set vertex attributes
        GLES20.glEnableVertexAttribArray(positionLocation);
        GLES20.glVertexAttribPointer(positionLocation, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer);

        GLES20.glEnableVertexAttribArray(texCoordLocation);
        GLES20.glVertexAttribPointer(texCoordLocation, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer);

        // Draw
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        // Disable vertex arrays
        GLES20.glDisableVertexAttribArray(positionLocation);
        GLES20.glDisableVertexAttribArray(texCoordLocation);

        currentTimeUs = presentationTimeUs;
    }

    /**
     * Release resources
     */
    public void release() {
        if (program != 0) {
            GLES20.glDeleteProgram(program);
            program = 0;
        }
    }

    /**
     * Load and compile a shader
     */
    private int loadShader(int type, String shaderCode) throws GlUtil.GlException {
        int shader = GLES20.glCreateShader(type);
        GlUtil.checkGlError();

        GLES20.glShaderSource(shader, shaderCode);
        GlUtil.checkGlError();

        GLES20.glCompileShader(shader);
        GlUtil.checkGlError();

        // Check compilation
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);

        if (compiled[0] == 0) {
            String error = GLES20.glGetShaderInfoLog(shader);
            GLES20.glDeleteShader(shader);
            throw new GlUtil.GlException("Failed to compile shader: " + error);
        }

        return shader;
    }

    /**
     * Get current alpha value (for debugging)
     */
    public float getCurrentAlpha() {
        float progress = Math.min(1.0f, (float) currentTimeUs / durationUs);
        return isFadeIn ? progress : (1.0f - progress);
    }
}