package com.example.recalllive;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * FIXED: Wait for photo clustering to complete before starting video generation
 */
public class PatientSignupActivity extends AppCompatActivity {
    private static final String TAG = "PatientSignupActivity";
    private static final int PERMISSION_REQUEST_CODE = 101;
    private static final String PREFS_NAME = "RecallLivePrefs";

    private FirebaseAuth firebaseAuth;
    private EditText editTextEmail, editTextPassword, editTextGuardianEmail;
    private Button signUpButton;
    private Button toGuardianSignupButton;
    private Button toLoginButton;
    private DatabaseReference mDatabase;

    private AutoClusteringService autoClusteringService;
    private AutomaticVideoService automaticVideoService;
    private String newPatientUid;
    private String patientEmail;
    private String foundGuardianUid = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_patientsignup);

        autoClusteringService = new AutoClusteringService(this);
        automaticVideoService = new AutomaticVideoService(this);

        firebaseAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        editTextEmail = findViewById(R.id.etEmailPhone);
        editTextPassword = findViewById(R.id.etPassword);
        editTextGuardianEmail = findViewById(R.id.etGuardianEmailPhone);
        signUpButton = findViewById(R.id.btnSignUp);
        toGuardianSignupButton = findViewById(R.id.btnGuardian);
        toLoginButton = findViewById(R.id.btnAlreadyHaveAccountPatient);

        signUpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String email = editTextEmail.getText().toString().trim();
                String password = editTextPassword.getText().toString();
                String guardianEmail = editTextGuardianEmail.getText().toString().trim();
                Pattern pattern = Patterns.EMAIL_ADDRESS;

                if (email.equals("") || password.equals("")) {
                    Toast.makeText(getApplicationContext(),
                            "Please enter an email and password.", Toast.LENGTH_LONG).show();
                } else if (!pattern.matcher(email).matches()) {
                    Toast.makeText(getApplicationContext(),
                            "The email format is not correct.", Toast.LENGTH_LONG).show();
                } else if (password.length() < 6) {
                    Toast.makeText(getApplicationContext(),
                            "Please enter a password that is at least 6 characters.", Toast.LENGTH_LONG).show();
                } else if (!guardianEmail.equals("") && !pattern.matcher(guardianEmail).matches()) {
                    Toast.makeText(getApplicationContext(),
                            "The guardian email format is not correct.", Toast.LENGTH_LONG).show();
                } else {
                    registerPatient(email, password, guardianEmail);
                }
            }
        });

        toGuardianSignupButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getApplicationContext(), GuardianSignupActivity.class);
                startActivity(intent);
            }
        });

        toLoginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getApplicationContext(), LoginActivity.class);
                startActivity(intent);
            }
        });
    }

    private void registerPatient(String email, String password, String guardianEmail) {
        Toast.makeText(this, "Creating your account...", Toast.LENGTH_SHORT).show();
        patientEmail = email;

        if (!TextUtils.isEmpty(guardianEmail)) {
            Log.d(TAG, "╔═══════════════════════════════════════╗");
            Log.d(TAG, "║  GUARDIAN EMAIL PROVIDED - SEARCHING   ║");
            Log.d(TAG, "╚═══════════════════════════════════════╝");
            searchForGuardian(guardianEmail, email, password);
        } else {
            createPatientAccount(email, password, null);
        }
    }

    private void searchForGuardian(String guardianEmail, String patientEmailToCreate, String password) {
        mDatabase.child("Guardian")
                .orderByChild("email")
                .equalTo(guardianEmail)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists() && snapshot.getChildrenCount() > 0) {
                            for (DataSnapshot guardianSnapshot : snapshot.getChildren()) {
                                foundGuardianUid = guardianSnapshot.getKey();
                                Log.d(TAG, "✓ GUARDIAN FOUND: " + foundGuardianUid);
                                createPatientAccount(patientEmailToCreate, password, foundGuardianUid);
                                return;
                            }
                        } else {
                            Toast.makeText(PatientSignupActivity.this,
                                    "Guardian not found. Creating account without guardian link.",
                                    Toast.LENGTH_LONG).show();
                            createPatientAccount(patientEmailToCreate, password, null);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        createPatientAccount(patientEmailToCreate, password, null);
                    }
                });
    }

    private void createPatientAccount(String email, String password, String guardianUid) {
        firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            FirebaseUser currentUser = firebaseAuth.getCurrentUser();
                            if (currentUser != null) {
                                newPatientUid = currentUser.getUid();
                                savePatientAndLinkGuardian(email, newPatientUid, guardianUid);
                            }
                        } else {
                            Toast.makeText(getApplicationContext(),
                                    "Sign up failed: " + task.getException().getMessage(),
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private void savePatientAndLinkGuardian(String email, String patientUid, String guardianUid) {
        String guardianEmail = editTextGuardianEmail.getText().toString().trim();

        DatabaseReference patientRef = mDatabase.child("Patient").child(patientUid);

        Map<String, Object> patientInfo = new HashMap<>();
        patientInfo.put("email", email);
        patientInfo.put("uid", patientUid);
        patientInfo.put("accountType", "patient");
        patientInfo.put("createdAt", System.currentTimeMillis());
        patientInfo.put("hasCompletedSetup", false);

        if (guardianUid != null && !guardianUid.isEmpty()) {
            patientInfo.put("guardianUid", guardianUid);
            patientInfo.put("guardianEmail", guardianEmail);
            patientInfo.put("hasGuardian", true);
        } else {
            patientInfo.put("guardianUid", "");
            patientInfo.put("guardianEmail", "");
            patientInfo.put("hasGuardian", false);
        }

        patientRef.setValue(patientInfo)
                .addOnSuccessListener(aVoid -> {
                    if (guardianUid != null && !guardianUid.isEmpty()) {
                        updateGuardianWithPatientInfo(guardianUid, patientUid, email);
                    } else {
                        savePreferencesAndContinue(patientUid, null);
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(PatientSignupActivity.this,
                            "Failed to save patient data: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    private void updateGuardianWithPatientInfo(String guardianUid, String patientUid, String patientEmail) {
        DatabaseReference guardianRef = mDatabase.child("Guardian").child(guardianUid);

        Map<String, Object> guardianUpdates = new HashMap<>();
        guardianUpdates.put("patient-uid", patientUid);
        guardianUpdates.put("patient-email", patientEmail);

        guardianRef.updateChildren(guardianUpdates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(PatientSignupActivity.this,
                            "Successfully linked to guardian!",
                            Toast.LENGTH_LONG).show();
                    savePreferencesAndContinue(patientUid, guardianUid);
                })
                .addOnFailureListener(e -> {
                    savePreferencesAndContinue(patientUid, guardianUid);
                });
    }

    private void savePreferencesAndContinue(String patientUid, String guardianUid) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("user_type", "patient");
        editor.putString("patient_uid", patientUid);
        editor.putBoolean("is_first_time", true);

        if (guardianUid != null && !guardianUid.isEmpty()) {
            editor.putString("guardian_uid", guardianUid);
        }

        editor.apply();

        showWelcomeAndRequestPermissions();
    }

    private void showWelcomeAndRequestPermissions() {
        new AlertDialog.Builder(this)
                .setTitle("Welcome to RecallLive!")
                .setMessage("RecallLive will help you preserve your memories by:\n\n" +
                        "• Organizing your photos automatically\n" +
                        "• Creating daily memory videos\n" +
                        "• Sharing special moments with loved ones\n\n" +
                        "To get started, we need permission to access your photos.")
                .setPositiveButton("Grant Access", (dialog, which) -> {
                    requestPermissions();
                })
                .setCancelable(false)
                .show();
    }

    private void requestPermissions() {
        String permission;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permission = Manifest.permission.READ_MEDIA_IMAGES;
        } else {
            permission = Manifest.permission.READ_EXTERNAL_STORAGE;
        }

        if (ContextCompat.checkSelfPermission(this, permission)
                == PackageManager.PERMISSION_GRANTED) {
            startSetupProcess();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{permission},
                    PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startSetupProcess();
            } else {
                Toast.makeText(this,
                        "Photo access is required for RecallLive to work properly",
                        Toast.LENGTH_LONG).show();
                navigateToMainActivity();
            }
        }
    }

    /**
     * CRITICAL FIX: Start clustering FIRST, then wait for completion before starting videos
     */
    private void startSetupProcess() {
        Toast.makeText(this, "Setting up your account...", Toast.LENGTH_SHORT).show();

        Log.d(TAG, "╔═══════════════════════════════════════╗");
        Log.d(TAG, "║  STARTING SETUP PROCESS               ║");
        Log.d(TAG, "╚═══════════════════════════════════════╝");

        DatabaseReference patientRef = mDatabase.child("Patient").child(newPatientUid);
        patientRef.child("hasCompletedSetup").setValue(true);

        AlertDialog progressDialog = new AlertDialog.Builder(this)
                .setTitle("Organizing Your Photos")
                .setMessage("Please wait while we organize your photos...\n\n" +
                        "• Analyzing photo metadata\n" +
                        "• Grouping by location and time\n" +
                        "• Preparing memory clusters\n\n" +
                        "This may take 30-60 seconds.")
                .setCancelable(false)
                .create();

        progressDialog.show();

        // STEP 1: Start clustering WITH callback
        Log.d(TAG, "▶️ Step 1: Starting photo clustering...");

        PhotoProcessingService processingService = new PhotoProcessingService(this, newPatientUid);
        processingService.processAllPhotos(new PhotoProcessingService.ProcessingCallback() {
            @Override
            public void onProcessingStarted() {
                Log.d(TAG, "✓ Photo clustering started");
            }

            @Override
            public void onProgressUpdate(int processed, int total) {
                runOnUiThread(() -> {
                    progressDialog.setMessage("Organizing photos: " + processed + "/" + total + "\n\n" +
                            "Please wait...");
                });
            }

            @Override
            public void onProcessingComplete(List<PhotoClusteringManager.PhotoCluster> clusters) {
                Log.d(TAG, "╔═══════════════════════════════════════╗");
                Log.d(TAG, "✓✓✓ CLUSTERING COMPLETE ✓✓✓");
                Log.d(TAG, "Created " + (clusters != null ? clusters.size() : 0) + " clusters");
                Log.d(TAG, "╚═══════════════════════════════════════╝");

                // STEP 2: NOW start video generation (clusters exist!)
                runOnUiThread(() -> {
                    progressDialog.setMessage("Photos organized!\n\n" +
                            "Now creating your first memory videos...\n\n" +
                            "This will take another 30-60 seconds.");
                });

                Log.d(TAG, "▶️ Step 2: Starting video generation (clusters ready!)...");
                automaticVideoService.initializeForPatient(newPatientUid, true);

                // Wait a bit then navigate
                new android.os.Handler().postDelayed(() -> {
                    runOnUiThread(() -> {
                        progressDialog.dismiss();
                        Toast.makeText(PatientSignupActivity.this,
                                "Setup complete! Your memory videos are being created in the background.",
                                Toast.LENGTH_LONG).show();
                        navigateToMainActivity();
                    });
                }, 5000); // 5 seconds to let video generation start
            }

            @Override
            public void onProcessingError(String error) {
                Log.e(TAG, "❌ Clustering failed: " + error);
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(PatientSignupActivity.this,
                            "Setup complete! You may need to add photos with location data.",
                            Toast.LENGTH_LONG).show();
                    navigateToMainActivity();
                });
            }
        });
    }

    private void navigateToMainActivity() {
        Intent intent = new Intent(getApplicationContext(), PatientMainActivity.class);
        intent.putExtra("is_first_time", true);
        intent.putExtra("patient_uid", newPatientUid);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}