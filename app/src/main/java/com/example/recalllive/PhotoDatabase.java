package com.example.recalllive;

import android.content.Context;

import androidx.room.Dao;
import androidx.room.Database;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.Update;

import com.example.recalllive.PhotoData;

import java.util.List;

@Database(entities = {PhotoData.class}, version = 1, exportSchema = false)
public abstract class PhotoDatabase extends RoomDatabase {

    private static PhotoDatabase INSTANCE;
    private static final String DATABASE_NAME = "recall_live_db";

    public abstract PhotoDao photoDao();

    public static synchronized PhotoDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            PhotoDatabase.class,
                            DATABASE_NAME)
                    .fallbackToDestructiveMigration()
                    .build();
        }
        return INSTANCE;
    }

    @Dao
    public interface PhotoDao {

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        void insertPhoto(PhotoData photo);

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        void insertPhotos(List<PhotoData> photos);

        @Update
        void updatePhoto(PhotoData photo);

        @Delete
        void deletePhoto(PhotoData photo);

        @Query("SELECT * FROM photos ORDER BY dateTaken DESC")
        List<PhotoData> getAllPhotos();

        @Query("SELECT * FROM photos WHERE clusterId = :clusterId ORDER BY dateTaken")
        List<PhotoData> getPhotosByCluster(String clusterId);

        @Query("SELECT * FROM photos WHERE timeCluster = :timeCluster ORDER BY dateTaken DESC")
        List<PhotoData> getPhotosByTimeCluster(String timeCluster);

        @Query("SELECT * FROM photos WHERE locationName = :locationName ORDER BY dateTaken DESC")
        List<PhotoData> getPhotosByLocation(String locationName);

        @Query("SELECT * FROM photos WHERE dateTaken BETWEEN :startTime AND :endTime ORDER BY dateTaken")
        List<PhotoData> getPhotosBetweenDates(long startTime, long endTime);

        @Query("SELECT DISTINCT clusterId FROM photos WHERE clusterId IS NOT NULL")
        List<String> getAllClusterIds();

        @Query("SELECT DISTINCT locationName FROM photos WHERE locationName IS NOT NULL")
        List<String> getAllLocationNames();

        @Query("SELECT COUNT(*) FROM photos")
        int getPhotoCount();

        @Query("SELECT COUNT(DISTINCT clusterId) FROM photos WHERE clusterId IS NOT NULL")
        int getClusterCount();

        @Query("DELETE FROM photos")
        void deleteAllPhotos();

        @Query("SELECT * FROM photos WHERE photoUri = :uri LIMIT 1")
        PhotoData getPhotoByUri(String uri);

        // Get photos with location data
        @Query("SELECT * FROM photos WHERE latitude != 0.0 OR longitude != 0.0 ORDER BY dateTaken DESC")
        List<PhotoData> getPhotosWithLocation();

        // Get recent photos (last 7 days)
        @Query("SELECT * FROM photos WHERE dateTaken > :sevenDaysAgo ORDER BY dateTaken DESC")
        List<PhotoData> getRecentPhotos(long sevenDaysAgo);
    }
}