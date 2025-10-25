package com.example.recalllive;
import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.util.Log;
import androidx.annotation.NonNull;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.storage.FileDownloadTask;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import java.io.File;
import java.nio.ByteBuffer;
public class VideoAudioMerger {
    private static final String TAG = "VideoAudioMerger";
    public interface MergeCallback {
        void onMergeComplete(String mergedVideoUrl);
        void onMergeError(String error);
    }

    public static void mergeVideoWithAudio(Context context, String firebaseVideoUrl, File audioFile, String patientUid, MergeCallback callback) {
        Log.d(TAG, "═══════════════════════════════════════════════════");
        Log.d(TAG, "MERGING VIDEO + AUDIO");
        Log.d(TAG, "═══════════════════════════════════════════════════");
        Log.d(TAG, "Video URL: " + firebaseVideoUrl);
        Log.d(TAG, "Audio File: " + audioFile.getAbsolutePath());
        Log.d(TAG, "Audio exists: " + audioFile.exists());
        Log.d(TAG, "Audio size: " + audioFile.length() + " bytes");

        if (!audioFile.exists() || audioFile.length() == 0) {
            Log.e(TAG, "❌ Audio file is missing or empty");
            if (callback != null) callback.onMergeError("Audio file invalid");
            return;
        }

        new Thread(() -> {
            try {
                // Step 1: Download video from Firebase
                Log.d(TAG, "⬇️ Step 1: Downloading video from Firebase...");
                StorageReference videoRef = FirebaseStorage.getInstance().getReferenceFromUrl(firebaseVideoUrl);
                File tempVideoFile = new File(context.getCacheDir(), "temp_video_" + System.currentTimeMillis() + ".mp4");

                // Download synchronously on background thread
                try {
                    videoRef.getFile(tempVideoFile).addOnSuccessListener(taskSnapshot -> {
                        Log.d(TAG, "✓ Video downloaded: " + tempVideoFile.length() + " bytes");

                        // Step 2: Merge locally
                        Log.d(TAG, "🔗 Step 2: Merging video + audio locally...");
                        File mergedFile = new File(context.getCacheDir(), "merged_" + System.currentTimeMillis() + ".mp4");

                        boolean success = mergeFiles(tempVideoFile, audioFile, mergedFile);

                        if (success && mergedFile.exists() && mergedFile.length() > 0) {
                            Log.d(TAG, "✓ Merge successful: " + mergedFile.length() + " bytes");

                            // Step 3: Upload merged video
                            Log.d(TAG, "⬆️ Step 3: Uploading merged video...");
                            uploadMergedVideo(context, mergedFile, patientUid, new UploadCallback() {
                                @Override
                                public void onSuccess(String downloadUrl) {
                                    Log.d(TAG, "╔══════════════════════════════════════════════╗");
                                    Log.d(TAG, "  ✓✓✓ VIDEO WITH AUDIO READY ✓✓✓");
                                    Log.d(TAG, "╚══════════════════════════════════════════════╝");
                                    Log.d(TAG, "URL: " + downloadUrl);

                                    // Cleanup temp files
                                    tempVideoFile.delete();
                                    mergedFile.delete();

                                    if (callback != null) {
                                        callback.onMergeComplete(downloadUrl);
                                    }
                                }

                                @Override
                                public void onFailure(String error) {
                                    Log.e(TAG, "❌ Upload failed: " + error);
                                    tempVideoFile.delete();
                                    mergedFile.delete();
                                    if (callback != null) {
                                        callback.onMergeError("Upload failed: " + error);
                                    }
                                }
                            });
                        } else {
                            Log.e(TAG, "❌ Merge failed or output file is empty");
                            tempVideoFile.delete();
                            if (callback != null) {
                                callback.onMergeError("Merge failed");
                            }
                        }
                    }).addOnFailureListener(e -> {
                        Log.e(TAG, "❌ Failed to download video: " + e.getMessage());
                        if (callback != null) {
                            callback.onMergeError("Download failed: " + e.getMessage());
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "❌ Download exception: " + e.getMessage(), e);
                    if (callback != null) {
                        callback.onMergeError("Download exception: " + e.getMessage());
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "❌ Error in merge process", e);
                if (callback != null) {
                    callback.onMergeError(e.getMessage());
                }
            }
        }).start();
    }

    private static boolean mergeFiles(File videoFile, File audioFile, File outputFile) {
        MediaExtractor videoExtractor = null;
        MediaExtractor audioExtractor = null;
        MediaMuxer muxer = null;

        try {
            Log.d(TAG, "  Setting up extractors...");
            Log.d(TAG, "  Video: " + videoFile.getAbsolutePath() + " (" + videoFile.length() + " bytes)");
            Log.d(TAG, "  Audio: " + audioFile.getAbsolutePath() + " (" + audioFile.length() + " bytes)");

            // Setup video extractor
            videoExtractor = new MediaExtractor();
            videoExtractor.setDataSource(videoFile.getAbsolutePath());

            // Setup audio extractor
            audioExtractor = new MediaExtractor();
            audioExtractor.setDataSource(audioFile.getAbsolutePath());

            // Setup muxer
            muxer = new MediaMuxer(outputFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            // Find and add video track
            int videoTrackIndex = -1;
            for (int i = 0; i < videoExtractor.getTrackCount(); i++) {
                MediaFormat format = videoExtractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith("video/")) {
                    videoExtractor.selectTrack(i);
                    videoTrackIndex = muxer.addTrack(format);
                    Log.d(TAG, "  ✓ Video track: " + mime);
                    break;
                }
            }

            // Find and add audio track
            int audioTrackIndex = -1;
            for (int i = 0; i < audioExtractor.getTrackCount(); i++) {
                MediaFormat format = audioExtractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith("audio/")) {
                    audioExtractor.selectTrack(i);
                    audioTrackIndex = muxer.addTrack(format);
                    Log.d(TAG, "  ✓ Audio track: " + mime);
                    break;
                }
            }

            if (videoTrackIndex == -1) {
                Log.e(TAG, "  ❌ No video track found");
                return false;
            }

            if (audioTrackIndex == -1) {
                Log.e(TAG, "  ❌ No audio track found");
                return false;
            }

            // Start muxing
            Log.d(TAG, "  Starting muxer...");
            muxer.start();

            // Mux video track
            Log.d(TAG, "  Muxing video...");
            ByteBuffer buffer = ByteBuffer.allocate(1024 * 1024);
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

            videoExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            int videoFrames = 0;
            while (true) {
                int sampleSize = videoExtractor.readSampleData(buffer, 0);
                if (sampleSize < 0) break;

                bufferInfo.offset = 0;
                bufferInfo.size = sampleSize;
                bufferInfo.presentationTimeUs = videoExtractor.getSampleTime();
                bufferInfo.flags = videoExtractor.getSampleFlags();

                muxer.writeSampleData(videoTrackIndex, buffer, bufferInfo);
                videoExtractor.advance();
                videoFrames++;
            }
            Log.d(TAG, "  ✓ Video track muxed (" + videoFrames + " frames)");

            // Mux audio track
            Log.d(TAG, "  Muxing audio...");
            audioExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            int audioSamples = 0;
            while (true) {
                int sampleSize = audioExtractor.readSampleData(buffer, 0);
                if (sampleSize < 0) break;

                bufferInfo.offset = 0;
                bufferInfo.size = sampleSize;
                bufferInfo.presentationTimeUs = audioExtractor.getSampleTime();
                bufferInfo.flags = audioExtractor.getSampleFlags();

                muxer.writeSampleData(audioTrackIndex, buffer, bufferInfo);
                audioExtractor.advance();
                audioSamples++;
            }
            Log.d(TAG, "  ✓ Audio track muxed (" + audioSamples + " samples)");

            Log.d(TAG, "╔══════════════════════════════════════════════╗");
            Log.d(TAG, "  ✓ LOCAL MERGE COMPLETE");
            Log.d(TAG, "╚══════════════════════════════════════════════╝");
            Log.d(TAG, "Output: " + outputFile.getAbsolutePath());
            Log.d(TAG, "Size: " + outputFile.length() + " bytes");

            return true;

        } catch (Exception e) {
            Log.e(TAG, "❌ Error merging files", e);
            e.printStackTrace();
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

    private interface UploadCallback {
        void onSuccess(String downloadUrl);
        void onFailure(String error);
    }

    private static void uploadMergedVideo(Context context, File mergedFile, String patientUid, UploadCallback callback) {
        String fileName = "videos/" + patientUid + "/merged_" + System.currentTimeMillis() + ".mp4";
        StorageReference storageRef = FirebaseStorage.getInstance().getReference().child(fileName);

        Uri fileUri = Uri.fromFile(mergedFile);

        Log.d(TAG, "  Uploading: " + mergedFile.length() + " bytes");

        storageRef.putFile(fileUri)
                .addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                        Log.d(TAG, "  ✓ Merged video uploaded");

                        storageRef.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                            @Override
                            public void onSuccess(Uri uri) {
                                String downloadUrl = uri.toString();
                                Log.d(TAG, "  ✓ Download URL retrieved");
                                callback.onSuccess(downloadUrl);
                            }
                        }).addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Log.e(TAG, "  ❌ Failed to get download URL: " + e.getMessage());
                                callback.onFailure("Failed to get URL: " + e.getMessage());
                            }
                        });
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e(TAG, "  ❌ Failed to upload merged video: " + e.getMessage());
                        callback.onFailure("Upload failed: " + e.getMessage());
                    }
                })
                .addOnProgressListener(snapshot -> {
                    double progress = (100.0 * snapshot.getBytesTransferred()) / snapshot.getTotalByteCount();
                    if ((int)progress % 20 == 0) { // Log every 20%
                        Log.d(TAG, "    Upload progress: " + (int)progress + "%");
                    }
                });
    }
}