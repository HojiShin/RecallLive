package com.example.recalllive;

/**
 * Configuration class for video generation and management
 * Adjust these settings to control video behavior
 */
public class VideoConfiguration {

    /**
     * UPDATED: Videos are now deleted DAILY (only keep today's videos)
     * Each day gets a fresh batch of videos
     *
     * This parameter is kept for compatibility but videos are deleted
     * based on day boundary (midnight) rather than number of days
     */
    public static final int VIDEO_RETENTION_DAYS = 1; // Keep only today's videos

    /**
     * Maximum number of videos to generate per day
     * Default: 10 videos per day
     */
    public static final int MAX_VIDEOS_PER_DAY = 10;

    /**
     * Hour (24-hour format) to generate daily videos
     * Default: 0 (midnight/12 AM)
     *
     * Options:
     * - 0: Midnight (12 AM)
     * - 6: 6 AM
     * - 12: Noon (12 PM)
     * - 18: 6 PM
     */
    public static final int DAILY_GENERATION_HOUR = 0;

    /**
     * Whether to enable TTS narration
     * Default: true (videos have voice narration)
     *
     * Set to false for silent videos only
     */
    public static final boolean ENABLE_TTS_NARRATION = true;

    /**
     * Whether to enable automatic video cleanup
     * Default: true (old videos are deleted automatically)
     *
     * Set to false to keep all videos forever
     */
    public static final boolean ENABLE_AUTO_CLEANUP = true;

    /**
     * Number of photos per video
     * Default: 4 photos per video
     *
     * Options:
     * - 3: Shorter videos
     * - 4: Standard (recommended)
     * - 5: Longer videos
     */
    public static final int PHOTOS_PER_VIDEO = 4;

    /**
     * Default duration per image (seconds) when no TTS
     * Default: 4 seconds per image
     */
    public static final int DEFAULT_IMAGE_DURATION_SECONDS = 4;

    /**
     * Minimum duration per image (seconds) with TTS
     * Default: 3 seconds minimum
     */
    public static final int MIN_IMAGE_DURATION_SECONDS = 3;

    /**
     * Maximum duration per image (seconds) with TTS
     * Default: 10 seconds maximum
     */
    public static final int MAX_IMAGE_DURATION_SECONDS = 10;

    /**
     * TTS speech rate
     * Default: 0.85f (slightly slower than normal)
     *
     * Options:
     * - 0.5f: Very slow (easier for memory issues)
     * - 0.75f: Slow
     * - 0.85f: Slightly slow (recommended)
     * - 1.0f: Normal speed
     * - 1.2f: Fast
     */
    public static final float TTS_SPEECH_RATE = 0.85f;

    /**
     * TTS pitch
     * Default: 1.0f (normal pitch)
     *
     * Options:
     * - 0.8f: Lower, warmer voice
     * - 1.0f: Normal pitch (recommended)
     * - 1.2f: Higher pitch
     */
    public static final float TTS_PITCH = 1.0f;
}