//package com.example.recalllive;
//
//import android.app.Application;
//import android.net.Uri;
//
//import androidx.annotation.NonNull;
//import androidx.lifecycle.AndroidViewModel;
//import androidx.lifecycle.LiveData;
//import androidx.lifecycle.MutableLiveData;
//
//public class MainViewModel extends AndroidViewModel {
//
//    private final ImageRepository imageRepository;
//
//    private final MutableLiveData<Uri> _randomImageUri = new MutableLiveData<>();
//    public final LiveData<Uri> randomImageUri = _randomImageUri;
//
//    private final MutableLiveData<String> _errorMessage = new MutableLiveData<>();
//    public final LiveData<String> errorMessage = _errorMessage;
//
//    private final MutableLiveData<Boolean> _isLoading = new MutableLiveData<>();
//    public final LiveData<Boolean> isLoading = _isLoading;
//
//    public MainViewModel(@NonNull Application application) {
//        super(application);
//        this.imageRepository = new ImageRepository();
//    }
//
//    public void loadRandomImage() {
//        _isLoading.setValue(true);
//        imageRepository.findRandomImage(getApplication().getApplicationContext(), new ImageRepository.OnImageFoundListener() {
//            @Override
//            public void onImageFound(Uri imageUri) {
//                _randomImageUri.postValue(imageUri);
//                _isLoading.postValue(false);
//            }
//
//            @Override
//            public void onError(String message) {
//                _errorMessage.postValue(message);
//                _isLoading.postValue(false);
//            }
//
//            @Override
//            public void onEmpty() {
//                _errorMessage.postValue("기기에 이미지가 없습니다.");
//                _isLoading.postValue(false);
//            }
//        });
//    }
//}
