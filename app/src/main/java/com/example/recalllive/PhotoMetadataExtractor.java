package com.example.recalllive;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import androidx.exifinterface.media.ExifInterface;

import com.example.recalllive.PhotoData;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PhotoMetadataExtractor {
    private static final String TAG = "PhotoMetadataExtractor";
    private final Context context;
    private final SimpleDateFormat exifDateFormat = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US);

    public PhotoMetadataExtractor(Context context) {
        this.context = context;
    }

    /**
     * Extract all photos from device with metadata
     */
    public List<PhotoData> extractAllPhotos() {
        List<PhotoData> photoList = new ArrayList<>();

        String[] projection = {
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.DISPLAY_NAME
        };

        ContentResolver contentResolver = context.getContentResolver();
        Uri imagesUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;

        try (Cursor cursor = contentResolver.query(
                imagesUri,
                projection,
                null,
                null,
                MediaStore.Images.Media.DATE_TAKEN + " DESC")) {

            if (cursor != null) {
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
                int pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                int dateTakenColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN);

                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idColumn);
                    String path = cursor.getString(pathColumn);
                    long dateTaken = cursor.getLong(dateTakenColumn);

                    Uri contentUri = Uri.withAppendedPath(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            String.valueOf(id)
                    );

                    PhotoData photo = extractPhotoMetadata(contentUri.toString(), path, dateTaken);
                    if (photo != null) {
                        photoList.add(photo);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error extracting photos: " + e.getMessage());
        }

        return photoList;
    }

    /**
     * Extract metadata from a single photo
     */
    private PhotoData extractPhotoMetadata(String uri, String path, long dateTaken) {
        PhotoData photo = new PhotoData(uri);

        // Set date taken from MediaStore
        photo.setDateTaken(dateTaken > 0 ? dateTaken : System.currentTimeMillis());

        // Extract EXIF data for location and more precise date
        try {
            ExifInterface exif = new ExifInterface(path);

            // Extract GPS location
            float[] latLong = new float[2];
            if (exif.getLatLong(latLong)) {
                photo.setLatitude(latLong[0]);
                photo.setLongitude(latLong[1]);
            }

            // Extract date from EXIF if MediaStore date is not available
            if (dateTaken <= 0) {
                String dateTime = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL);
                if (dateTime == null) {
                    dateTime = exif.getAttribute(ExifInterface.TAG_DATETIME);
                }

                if (dateTime != null) {
                    try {
                        Date date = exifDateFormat.parse(dateTime);
                        if (date != null) {
                            photo.setDateTaken(date.getTime());
                        }
                    } catch (ParseException e) {
                        Log.e(TAG, "Error parsing EXIF date: " + e.getMessage());
                    }
                }
            }

            // Determine time cluster (morning, afternoon, evening, night)
            photo.setTimeCluster(getTimeCluster(photo.getDateTaken()));

        } catch (IOException e) {
            Log.e(TAG, "Error reading EXIF data: " + e.getMessage());
        }

        return photo;
    }

    /**
     * Determine time cluster based on hour of day
     */
    private String getTimeCluster(long timestamp) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        int hour = cal.get(Calendar.HOUR_OF_DAY);

        if (hour >= 5 && hour < 12) {
            return "Morning";
        } else if (hour >= 12 && hour < 17) {
            return "Afternoon";
        } else if (hour >= 17 && hour < 21) {
            return "Evening";
        } else {
            return "Night";
        }
    }

    /**
     * Extract metadata from content URI (for photos picked from gallery)
     */
    public PhotoData extractFromContentUri(Uri contentUri) {
        PhotoData photo = new PhotoData(contentUri.toString());

        try {
            InputStream inputStream = context.getContentResolver().openInputStream(contentUri);
            if (inputStream != null) {
                ExifInterface exif = new ExifInterface(inputStream);

                // Extract GPS location
                float[] latLong = new float[2];
                if (exif.getLatLong(latLong)) {
                    photo.setLatitude(latLong[0]);
                    photo.setLongitude(latLong[1]);
                }

                // Extract date
                String dateTime = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL);
                if (dateTime == null) {
                    dateTime = exif.getAttribute(ExifInterface.TAG_DATETIME);
                }

                if (dateTime != null) {
                    try {
                        Date date = exifDateFormat.parse(dateTime);
                        if (date != null) {
                            photo.setDateTaken(date.getTime());
                        }
                    } catch (ParseException e) {
                        photo.setDateTaken(System.currentTimeMillis());
                    }
                } else {
                    photo.setDateTaken(System.currentTimeMillis());
                }

                photo.setTimeCluster(getTimeCluster(photo.getDateTaken()));
                inputStream.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error extracting from content URI: " + e.getMessage());
        }

        return photo;
    }
}
