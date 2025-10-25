package com.example.recalllive;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.recalllive.Memory;
import com.example.recalllive.MemoryRepository;

import java.util.List;

/**
 * ViewModel for Memory-related operations
 * Part of MVVM architecture
 */
public class MemoryViewModel extends AndroidViewModel {
    private static final String TAG = "MemoryViewModel";

    private final MemoryRepository repository;
    private final MutableLiveData<Boolean> isLoading;
    private final MutableLiveData<String> errorMessage;
    private final MutableLiveData<Memory> selectedMemory;
    private final LiveData<List<Memory>> userMemories;
    private final MutableLiveData<String> lastSavedMemoryId;

    public MemoryViewModel(@NonNull Application application) {
        super(application);
        repository = MemoryRepository.getInstance();
        isLoading = new MutableLiveData<>(false);
        errorMessage = new MutableLiveData<>();
        selectedMemory = new MutableLiveData<>();
        lastSavedMemoryId = new MutableLiveData<>();
        userMemories = repository.getUserMemoriesLiveData();
    }

    /**
     * Save a memory with Gemini-generated content
     */
    public void saveMemory(String title, String script, List<String> keywords,
                           List<String> imageUrls, String clusterId) {
        isLoading.setValue(true);

        // Create memory using builder pattern
        Memory memory = new Memory.Builder(null, title, script) // userId will be set in repository
                .withKeywords(keywords)
                .withKeyImageUrls(imageUrls)
                .withClusterId(clusterId)
                .withGeminiModel("gemini-veo-3")
                .build();

        repository.saveMemory(memory, new MemoryRepository.OnMemorySavedCallback() {
            @Override
            public void onSuccess(String documentId) {
                isLoading.setValue(false);
                lastSavedMemoryId.setValue(documentId);
                errorMessage.setValue(null);
            }

            @Override
            public void onFailure(String error) {
                isLoading.setValue(false);
                errorMessage.setValue(error);
            }
        });
    }

    /**
     * Save a memory with local image URIs (will upload to Storage first)
     */
    public void saveMemoryWithLocalImages(String title, String script, List<String> keywords,
                                          List<String> localImageUris, String clusterId) {
        isLoading.setValue(true);

        Memory memory = new Memory.Builder(null, title, script)
                .withKeywords(keywords)
                .withClusterId(clusterId)
                .withGeminiModel("gemini-veo-3")
                .build();

        repository.saveMemoryWithImages(memory, localImageUris,
                new MemoryRepository.OnMemorySavedCallback() {
                    @Override
                    public void onSuccess(String documentId) {
                        isLoading.setValue(false);
                        lastSavedMemoryId.setValue(documentId);
                        errorMessage.setValue(null);
                    }

                    @Override
                    public void onFailure(String error) {
                        isLoading.setValue(false);
                        errorMessage.setValue(error);
                    }
                });
    }

    /**
     * Load a specific memory by ID
     */
    public void loadMemory(String documentId) {
        isLoading.setValue(true);

        repository.getMemoryById(documentId, new MemoryRepository.OnMemoryLoadedCallback() {
            @Override
            public void onSuccess(Memory memory) {
                isLoading.setValue(false);
                selectedMemory.setValue(memory);
                errorMessage.setValue(null);
            }

            @Override
            public void onFailure(String error) {
                isLoading.setValue(false);
                errorMessage.setValue(error);
            }
        });
    }

    /**
     * Update video URL after video generation
     */
    public void updateVideoUrl(String documentId, String videoUrl) {
        repository.updateVideoUrl(documentId, videoUrl,
                new MemoryRepository.OnMemorySavedCallback() {
                    @Override
                    public void onSuccess(String documentId) {
                        // Refresh the selected memory if it matches
                        if (selectedMemory.getValue() != null &&
                                documentId.equals(selectedMemory.getValue().getDocumentId())) {
                            loadMemory(documentId);
                        }
                        errorMessage.setValue(null);
                    }

                    @Override
                    public void onFailure(String error) {
                        errorMessage.setValue(error);
                    }
                });
    }

    /**
     * Delete a memory
     */
    public void deleteMemory(String documentId) {
        isLoading.setValue(true);

        repository.deleteMemory(documentId, new MemoryRepository.OnMemoryDeletedCallback() {
            @Override
            public void onSuccess() {
                isLoading.setValue(false);
                errorMessage.setValue(null);
                // Clear selected memory if it was deleted
                if (selectedMemory.getValue() != null &&
                        documentId.equals(selectedMemory.getValue().getDocumentId())) {
                    selectedMemory.setValue(null);
                }
            }

            @Override
            public void onFailure(String error) {
                isLoading.setValue(false);
                errorMessage.setValue(error);
            }
        });
    }

    /**
     * Get memories for a specific cluster
     */
    public void loadMemoriesByCluster(String clusterId,
                                      MutableLiveData<List<Memory>> clusterMemories) {
        isLoading.setValue(true);

        repository.getMemoriesByClusterId(clusterId,
                new MemoryRepository.OnMemoriesLoadedCallback() {
                    @Override
                    public void onSuccess(List<Memory> memories) {
                        isLoading.setValue(false);
                        clusterMemories.setValue(memories);
                        errorMessage.setValue(null);
                    }

                    @Override
                    public void onFailure(String error) {
                        isLoading.setValue(false);
                        errorMessage.setValue(error);
                    }
                });
    }

    /**
     * Clear error message
     */
    public void clearError() {
        errorMessage.setValue(null);
    }

    // Getters for LiveData
    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public LiveData<Memory> getSelectedMemory() {
        return selectedMemory;
    }

    public LiveData<List<Memory>> getUserMemories() {
        return userMemories;
    }

    public LiveData<String> getLastSavedMemoryId() {
        return lastSavedMemoryId;
    }
}