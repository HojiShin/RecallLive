//package com.example.recalllive;
//
//
//import android.content.ContentResolver;
//import android.content.ContentUris;
//import android.content.Context;
//import android.database.Cursor;
//import android.net.Uri;
//import android.os.Build;
//import android.provider.MediaStore;
//import android.util.Log;
//
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Random;
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
//
//public class ImageRepository {
//
//    private static final String TAG = "ImageRepository";
//    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
//
//    // 결과를 비동기적으로 전달하기 위한 콜백 인터페이스
//    public interface OnImageFoundListener {
//        void onImageFound(Uri imageUri);
//        void onError(String message);
//        void onEmpty();
//    }
//
//    public void findRandomImage(Context context, OnImageFoundListener listener) {
//        // 백그라운드 스레드에서 이미지 스캔 실행
//        executorService.execute(() -> {
//            List<Uri> imageUris = new ArrayList<>();
//            ContentResolver contentResolver = context.getContentResolver();
//
//            Uri collection;
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//                collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
//            } else {
//                collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
//            }
//
//            // 가져올 데이터 컬럼 정의 (이미지의 고유 ID만 필요)
//            String[] projection = new String[]{
//                    MediaStore.Images.Media
//                            ._ID};
//
//            // 정렬 순서 (최신 날짜 순)
//            String sortOrder = MediaStore.Images.Media.DATE_MODIFIED + " DESC";
//
//            try (Cursor cursor = contentResolver.query(collection, projection, null, null, sortOrder)) {
//                if (cursor != null && cursor.moveToFirst()) {
//                    int idColumn = cursor.getColumnIndexOrThrow(
//                            MediaStore.Images.Media
//                                    ._ID);
//                    do {
//                        long id = cursor.getLong(idColumn);
//                        Uri contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
//                        imageUris.add(contentUri);
//                    } while (cursor.moveToNext());
//                }
//            } catch (Exception e) {
//                Log.e(TAG, "Error finding images", e);
//                // 메인 스레드에서 에러 콜백 호출
//                postToMainThread(() -> listener.onError("이미지를 가져오는 중 오류가 발생했습니다."));
//                return;
//            }
//
//            if (imageUris.isEmpty()) {
//                // 이미지가 없을 경우
//                postToMainThread(listener::onEmpty);
//            } else {
//                // 무작위 이미지 선택
//                Random random = new Random();
//                Uri randomImageUri = imageUris.get(random.nextInt(imageUris.size()));
//                // 메인 스레드에서 성공 콜백 호출
//                postToMainThread(() -> listener.onImageFound(randomImageUri));
//            }
//        });
//    }
//
//    // 결과를 UI 스레드로 전달하기 위한 헬퍼 메소드
//    private void postToMainThread(Runnable runnable) {
//        new android.os.Handler(android.os.Looper.getMainLooper()).post(runnable);
//    }
//}
