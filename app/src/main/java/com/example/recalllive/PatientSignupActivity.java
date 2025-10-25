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
import java.util.Map;
import java.util.regex.Pattern;

/**
 * UPDATED: Patient signup with automatic video service initialization and cleanup
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

        // Initialize services
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

        // If guardian email provided, find guardian FIRST before creating patient
        if (!TextUtils.isEmpty(guardianEmail)) {
            Log.d(TAG, "╔═══════════════════════════════════════╗");
            Log.d(TAG, "║  GUARDIAN EMAIL PROVIDED - SEARCHING   ║");
            Log.d(TAG, "╚═══════════════════════════════════════╝");
            Log.d(TAG, "Guardian Email: " + guardianEmail);

            searchForGuardian(guardianEmail, email, password);
        } else {
            Log.d(TAG, "No guardian email provided - proceeding with patient creation");
            createPatientAccount(email, password, null);
        }
    }

    private void searchForGuardian(String guardianEmail, String patientEmailToCreate, String password) {
        Log.d(TAG, "Searching Guardian database for email: " + guardianEmail);

        mDatabase.child("Guardian")
                .orderByChild("email")
                .equalTo(guardianEmail)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        Log.d(TAG, "Search result - exists: " + snapshot.exists());
                        Log.d(TAG, "Search result - children: " + snapshot.getChildrenCount());

                        if (snapshot.exists() && snapshot.getChildrenCount() > 0) {
                            for (DataSnapshot guardianSnapshot : snapshot.getChildren()) {
                                foundGuardianUid = guardianSnapshot.getKey();
                                Log.d(TAG, "╔═══════════════════════════════════════╗");
                                Log.d(TAG, "║   ✓✓✓ GUARDIAN FOUND ✓✓✓              ║");
                                Log.d(TAG, "╚═══════════════════════════════════════╝");
                                Log.d(TAG, "Guardian UID: " + foundGuardianUid);
                                Log.d(TAG, "Guardian Email: " + guardianEmail);

                                // Now create patient account with guardian link
                                createPatientAccount(patientEmailToCreate, password, foundGuardianUid);
                                return;
                            }
                        } else {
                            Log.w(TAG, "╔═══════════════════════════════════════╗");
                            Log.w(TAG, "║   ✗ GUARDIAN NOT FOUND ✗              ║");
                            Log.w(TAG, "╚═══════════════════════════════════════╝");
                            Log.w(TAG, "No guardian with email: " + guardianEmail);

                            Toast.makeText(PatientSignupActivity.this,
                                    "Guardian not found. Creating account without guardian link.",
                                    Toast.LENGTH_LONG).show();

                            // Create patient without guardian
                            createPatientAccount(patientEmailToCreate, password, null);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Database error during guardian search: " + error.getMessage());
                        Toast.makeText(PatientSignupActivity.this,
                                "Error searching for guardian. Creating account without link.",
                                Toast.LENGTH_LONG).show();

                        createPatientAccount(patientEmailToCreate, password, null);
                    }
                });
    }

    private void createPatientAccount(String email, String password, String guardianUid) {
        Log.d(TAG, "Creating Firebase Auth account for patient...");

        firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            FirebaseUser currentUser = firebaseAuth.getCurrentUser();
                            if (currentUser != null) {
                                newPatientUid = currentUser.getUid();
                                Log.d(TAG, "✓ Patient auth account created: " + newPatientUid);

                                // Save patient data with or without guardian link
                                savePatientAndLinkGuardian(email, newPatientUid, guardianUid);
                            }
                        } else {
                            Log.e(TAG, "✗ Failed to create patient auth account");
                            Toast.makeText(getApplicationContext(),
                                    "Sign up failed: " + task.getException().getMessage(),
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private void savePatientAndLinkGuardian(String email, String patientUid, String guardianUid) {
        Log.d(TAG, "╔═══════════════════════════════════════╗");
        Log.d(TAG, "║  SAVING PATIENT & LINKING GUARDIAN     ║");
        Log.d(TAG, "╚═══════════════════════════════════════╝");
        Log.d(TAG, "Patient UID: " + patientUid);
        Log.d(TAG, "Patient Email: " + email);
        Log.d(TAG, "Guardian UID: " + (guardianUid != null ? guardianUid : "NONE"));

        // Get guardian email from the EditText to save it
        String guardianEmail = editTextGuardianEmail.getText().toString().trim();

        // Save patient data
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
                    Log.d(TAG, "✓✓✓ PATIENT DATA SAVED ✓✓✓");

                    // If guardian exists, update guardian's record
                    if (guardianUid != null && !guardianUid.isEmpty()) {
                        updateGuardianWithPatientInfo(guardianUid, patientUid, email);
                    } else {
                        // No guardian, proceed to setup
                        savePreferencesAndContinue(patientUid, null);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "✗✗✗ FAILED TO SAVE PATIENT ✗✗✗", e);
                    Toast.makeText(PatientSignupActivity.this,
                            "Failed to save patient data: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    private void updateGuardianWithPatientInfo(String guardianUid, String patientUid, String patientEmail) {
        Log.d(TAG, "╔═══════════════════════════════════════╗");
        Log.d(TAG, "║  UPDATING GUARDIAN WITH PATIENT INFO   ║");
        Log.d(TAG, "╚═══════════════════════════════════════╝");
        Log.d(TAG, "Guardian UID: " + guardianUid);
        Log.d(TAG, "Patient UID: " + patientUid);
        Log.d(TAG, "Patient Email: " + patientEmail);

        DatabaseReference guardianRef = mDatabase.child("Guardian").child(guardianUid);

        Map<String, Object> guardianUpdates = new HashMap<>();
        guardianUpdates.put("patient-uid", patientUid);
        guardianUpdates.put("patient-email", patientEmail);

        guardianRef.updateChildren(guardianUpdates)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "╔═══════════════════════════════════════╗");
                    Log.d(TAG, "║  ✓✓✓ GUARDIAN UPDATED ✓✓✓             ║");
                    Log.d(TAG, "╚═══════════════════════════════════════╝");
                    Log.d(TAG, "Guardian/" + guardianUid + "/patient-uid = " + patientUid);
                    Log.d(TAG, "Guardian/" + guardianUid + "/patient-email = " + patientEmail);

                    Toast.makeText(PatientSignupActivity.this,
                            "Successfully linked to guardian!",
                            Toast.LENGTH_LONG).show();

                    // Continue to setup
                    savePreferencesAndContinue(patientUid, guardianUid);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "╔═══════════════════════════════════════╗");
                    Log.e(TAG, "║  ✗✗✗ GUARDIAN UPDATE FAILED ✗✗✗       ║");
                    Log.e(TAG, "╚═══════════════════════════════════════╝");
                    Log.e(TAG, "Error: " + e.getMessage(), e);

                    Toast.makeText(PatientSignupActivity.this,
                            "Patient created but guardian link failed: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();

                    // Continue anyway - patient is created
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
     * UPDATED: Initialize both clustering and video services
     * Now includes automatic cleanup on signup
     */
    private void startSetupProcess() {
        Toast.makeText(this, "Setting up your account...", Toast.LENGTH_SHORT).show();

        Log.d(TAG, "╔═══════════════════════════════════════╗");
        Log.d(TAG, "║  STARTING SETUP PROCESS               ║");
        Log.d(TAG, "╚═══════════════════════════════════════╝");
        Log.d(TAG, "Patient UID: " + newPatientUid);

        // Initialize photo clustering service
        Log.d(TAG, "▶️ Initializing photo clustering...");
        autoClusteringService.initializeForPatient(newPatientUid);

        // Initialize automatic video service with signup flag
        // This will:
        // 1. Clean up any old/test videos (runs cleanup immediately)
        // 2. Generate first video
        // 3. Schedule daily video generation at midnight
        // 4. Schedule daily cleanup at midnight
        Log.d(TAG, "▶️ Initializing video service (with cleanup)...");
        automaticVideoService.initializeForPatient(newPatientUid, true); // true = isSignup

        // Mark setup as complete
        DatabaseReference patientRef = mDatabase.child("Patient").child(newPatientUid);
        patientRef.child("hasCompletedSetup").setValue(true);

        Log.d(TAG, "✓ Setup services initialized");

        showSetupProgressDialog();
    }

    private void showSetupProgressDialog() {
        AlertDialog progressDialog = new AlertDialog.Builder(this)
                .setTitle("Creating Your First Memory Video")
                .setMessage("We're organizing your photos and creating your first memory video. " +
                        "This may take a few moments...\n\n" +
                        "• Analyzing your photos\n" +
                        "• Grouping by location and time\n" +
                        "• Generating your first video\n" +
                        "• Setting up daily reminders")
                .setCancelable(false)
                .create();

        progressDialog.show();

        // Give time for services to initialize
        new android.os.Handler().postDelayed(() -> {
            progressDialog.dismiss();
            Toast.makeText(this,
                    "Setup complete! Your first memory video is being created.\n" +
                            "New videos will be generated daily at midnight.",
                    Toast.LENGTH_LONG).show();
            navigateToMainActivity();
        }, 4000); // Increased to 4 seconds to account for cleanup
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
        // Note: Don't stop services here as they need to run in background
        // Services will manage their own lifecycle
    }
}