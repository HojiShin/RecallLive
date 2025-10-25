package com.example.recalllive;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Centralized thread pool manager to prevent thread exhaustion
 * Singleton pattern ensures only one set of thread pools exists
 */
public class AppExecutors {

    private static final String TAG = "AppExecutors";
    private static final int CORE_POOL_SIZE = 3;
    private static final int MAX_POOL_SIZE = 5;
    private static final int KEEP_ALIVE_TIME = 60;

    private static volatile AppExecutors sInstance;

    private final Executor diskIO;
    private final Executor networkIO;
    private final Executor mainThread;
    private final ExecutorService lightweightExecutor;

    private AppExecutors(Executor diskIO, Executor networkIO, Executor mainThread,
                         ExecutorService lightweightExecutor) {
        this.diskIO = diskIO;
        this.networkIO = networkIO;
        this.mainThread = mainThread;
        this.lightweightExecutor = lightweightExecutor;
    }

    public static AppExecutors getInstance() {
        if (sInstance == null) {
            synchronized (AppExecutors.class) {
                if (sInstance == null) {
                    sInstance = new AppExecutors(
                            // Disk I/O operations (database, file operations)
                            new ThreadPoolExecutor(
                                    CORE_POOL_SIZE,
                                    MAX_POOL_SIZE,
                                    KEEP_ALIVE_TIME,
                                    TimeUnit.SECONDS,
                                    new LinkedBlockingQueue<>()
                            ),
                            // Network I/O operations (Firebase, API calls)
                            Executors.newFixedThreadPool(3),
                            // Main thread executor for UI updates
                            new MainThreadExecutor(),
                            // Lightweight executor for quick background tasks
                            Executors.newSingleThreadExecutor()
                    );
                }
            }
        }
        return sInstance;
    }

    public Executor diskIO() {
        return diskIO;
    }

    public Executor networkIO() {
        return networkIO;
    }

    public Executor mainThread() {
        return mainThread;
    }

    public ExecutorService lightweightExecutor() {
        return lightweightExecutor;
    }

    /**
     * Shutdown all executors (call in Application.onTerminate() if needed)
     */
    public static void shutdown() {
        if (sInstance != null) {
            if (sInstance.diskIO instanceof ExecutorService) {
                ((ExecutorService) sInstance.diskIO).shutdown();
            }
            if (sInstance.networkIO instanceof ExecutorService) {
                ((ExecutorService) sInstance.networkIO).shutdown();
            }
            if (sInstance.lightweightExecutor != null) {
                sInstance.lightweightExecutor.shutdown();
            }
        }
    }

    /**
     * Main thread executor for running tasks on UI thread
     */
    private static class MainThreadExecutor implements Executor {
        private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

        @Override
        public void execute(@NonNull Runnable command) {
            mainThreadHandler.post(command);
        }
    }
}