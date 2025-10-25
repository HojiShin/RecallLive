package com.example.recalllive;

import android.Manifest;
import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;

public class MainActivity extends AppCompatActivity {
//
//    private MainViewModel viewModel;
//    private ImageView imageView;
//    private Button findImageButton;
//    private ProgressBar progressBar;
//
//    // Launcher for permission request
//    private final ActivityResultLauncher<String> requestPermissionLauncher =
//            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
//                if (isGranted) {
//                    // Load image when permission is granted
//                    viewModel.loadRandomImage();
//                } else {
//                    Toast.makeText(this, "Storage access permission is required to retrieve images.", Toast.LENGTH_SHORT).show();
//                }
//            });
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_main); // Set to already prepared XML file
//
//        // Initialize ViewModel
//        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
//
//        // Initialize UI elements (need to change to actual IDs)
//        imageView = findViewById(R.id.imageViewRandom);
//        findImageButton = findViewById(R.id.buttonFindImage);
//        progressBar = findViewById(R.id.progressBarLoading);
//
//        // Set button click listener
//        findImageButton.setOnClickListener(v -> checkPermissionAndLoadImage());
//
//        // Observe ViewModel's LiveData
//        observeViewModel();
//    }
//
//    private void checkPermissionAndLoadImage() {
//        String permission;
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
//            permission = Manifest.permission.READ_MEDIA_IMAGES;
//        } else {
//            permission = Manifest.permission.READ_EXTERNAL_STORAGE;
//        }
//
//        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
//            // Load image directly if permission already exists
//            viewModel.loadRandomImage();
//        } else {
//            // Request permission if not granted
//            requestPermissionLauncher.launch(permission);
//        }
//    }
//
//    private void observeViewModel() {
//        viewModel.isLoading.observe(this, isLoading -> {
//            progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
//        });
//
//        viewModel.randomImageUri.observe(this, uri -> {
//            if (uri != null) {
//                // Load image using Glide library
//                Glide.with(this)
//                        .load(uri)
//                        .into(imageView);
//            }
//        });
//
//        viewModel.errorMessage.observe(this, error -> {
//            if (error != null) {
//                Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
//            }
//        });
//    }
}