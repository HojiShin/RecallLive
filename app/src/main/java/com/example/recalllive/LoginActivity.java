package com.example.recalllive;
import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
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
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
public class LoginActivity extends AppCompatActivity {
    private static final String TAG = "LoginActivity";
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final String PREFS_NAME = "RecallLivePrefs";
    Button loginToPatientSignUpBtn;
    Boolean userType = null; // Initialize as null to catch unset state
    private FirebaseAuth firebaseAuth;
    private EditText editTextEmail, editTextPassword;
    private Button button;
    private DatabaseReference databaseReference;
    private AutoClusteringService autoClusteringService;
    private AutomaticVideoService automaticVideoService;
    private String currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);

        autoClusteringService = new AutoClusteringService(this);
        automaticVideoService = new AutomaticVideoService(this);

        loginToPatientSignUpBtn = findViewById(R.id.btn_loginToSignUp);
        loginToPatientSignUpBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v){
                Intent intent = new Intent(getApplicationContext(), PatientSignupActivity.class);
                startActivity(intent);
            }
        });

        firebaseAuth = FirebaseAuth.getInstance();
        editTextEmail = findViewById(R.id.et_email_phone);
        editTextPassword = findViewById(R.id.et_password);
        button = findViewById(R.id.btn_login);

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String email = editTextEmail.getText().toString();
                String password = editTextPassword.getText().toString();

                if (email.equals("") || password.equals("")) {
                    Toast.makeText(getApplicationContext(), "Please enter your email/phone and password", Toast.LENGTH_LONG).show();
                }
                else {
                    login(email, password);
                }
            }
        });
    }

    private void setUserType(String userId) {
        DatabaseReference databaseReference = FirebaseDatabase.getInstance().getReference();
        currentUserId = userId;

        Log.d(TAG, "╔═══════════════════════════════════════════╗");
        Log.d(TAG, "CHECKING USER TYPE FOR: " + userId);
        Log.d(TAG, "╚═══════════════════════════════════════════╝");

        // Check Patient first
        databaseReference.child("Patient").child(userId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Log.d(TAG, "📊 Patient check - snapshot.exists(): " + snapshot.exists());
                Log.d(TAG, "📊 Patient check - has children: " + snapshot.hasChildren());

                // Check if this is a REAL patient node (has email field) or just an empty/placeholder node
                boolean hasEmail = snapshot.child("email").exists();
                boolean hasAccountType = snapshot.child("accountType").exists() &&
                        "patient".equals(snapshot.child("accountType").getValue(String.class));

                Log.d(TAG, "📊 Patient node has email: " + hasEmail);
                Log.d(TAG, "📊 Patient node accountType: " + snapshot.child("accountType").getValue());

                if (snapshot.exists() && snapshot.hasChildren() && (hasEmail || hasAccountType)) {
                    // DEBUG: Print ALL data in Patient node
                    Log.d(TAG, "🔍 PATIENT NODE DATA:");
                    for (DataSnapshot child : snapshot.getChildren()) {
                        Log.d(TAG, "  - " + child.getKey() + " = " + child.getValue());
                    }

                    Log.d(TAG, "✓ User found as PATIENT (VALID DATA)");
                    userType = true; // Set BEFORE calling any other methods

                    SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                    prefs.edit()
                            .putString("user_type", "patient")
                            .putString("patient_uid", userId)
                            .apply();

                    Log.d(TAG, "✓ Saved to SharedPreferences: user_type=patient");

                    checkPatientClustersAndProceed(userId);
                } else {
                    Log.d(TAG, "❌ Not found in Patient database (or node is empty/invalid)");
                    Log.d(TAG, "🔍 Checking Guardian database...");

                    // Check Guardian
                    databaseReference.child("Guardian").child(userId).addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            Log.d(TAG, "📊 Guardian check - snapshot.exists(): " + snapshot.exists());
                            Log.d(TAG, "📊 Guardian check - has children: " + snapshot.hasChildren());

                            boolean hasEmail = snapshot.child("email").exists();
                            Log.d(TAG, "📊 Guardian node has email: " + hasEmail);

                            if (snapshot.exists() && snapshot.hasChildren() && hasEmail) {
                                // DEBUG: Print ALL data in Guardian node
                                Log.d(TAG, "🔍 GUARDIAN NODE DATA:");
                                for (DataSnapshot child : snapshot.getChildren()) {
                                    Log.d(TAG, "  - " + child.getKey() + " = " + child.getValue());
                                }

                                Log.d(TAG, "✓ User found as GUARDIAN (VALID DATA)");
                                userType = false; // Set BEFORE calling any other methods

                                SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                                prefs.edit()
                                        .putString("user_type", "guardian")
                                        .putString("guardian_uid", userId)
                                        .apply();

                                Log.d(TAG, "✓ Saved to SharedPreferences: user_type=guardian");

                                // Get linked patient UID
                                String patientUid = snapshot.child("patient-uid").getValue(String.class);
                                Log.d(TAG, "Guardian's linked patient UID: " + (patientUid != null ? patientUid : "NONE"));

                                if (patientUid != null && !patientUid.isEmpty()) {
                                    prefs.edit().putString("linked_patient_uid", patientUid).apply();
                                    Log.d(TAG, "✓ Saved linked_patient_uid to SharedPreferences: " + patientUid);
                                } else {
                                    Log.w(TAG, "⚠️ No linked patient for this guardian");
                                }

                                // Navigate to Guardian home
                                navigateToHome();
                            } else {
                                Log.e(TAG, "❌ User not found in Guardian database either");
                                Log.e(TAG, "❌❌❌ USER NOT FOUND IN EITHER DATABASE ❌❌❌");
                                userType = null;
                                Toast.makeText(getApplicationContext(), "User type not found in database", Toast.LENGTH_LONG).show();
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            Log.e(TAG, "❌ Guardian check error: " + error.getMessage());
                            Toast.makeText(getApplicationContext(), "Database error: " + error.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "❌ Patient check error: " + error.getMessage());
                Toast.makeText(getApplicationContext(), "Database error: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void checkPatientClustersAndProceed(String patientUid) {
        Log.d(TAG, "╔═══════════════════════════════════════════╗");
        Log.d(TAG, "CHECKING PATIENT CLUSTERS");
        Log.d(TAG, "╚═══════════════════════════════════════════╝");

        DatabaseReference clusterRef = FirebaseDatabase.getInstance().getReference()
                .child("Patient")
                .child(patientUid)
                .child("clusterSummary");

        clusterRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                boolean isFirstTime = !snapshot.exists();

                if (isFirstTime) {
                    Log.d(TAG, "⚠️ NO CLUSTERS - First time patient");
                    showFirstTimePatientSetup(patientUid);
                } else {
                    Long lastUpdated = snapshot.child("lastUpdated").getValue(Long.class);
                    Long totalClusters = snapshot.child("totalClusters").getValue(Long.class);

                    Log.d(TAG, "✓ CLUSTERS FOUND");
                    Log.d(TAG, "  Total clusters: " + totalClusters);
                    Log.d(TAG, "  Last updated: " + lastUpdated);

                    // FIXED: For existing patients logging in, always pass true for video generation
                    checkPermissionsAndStartServices(patientUid);
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e(TAG, "❌ Error checking clusters: " + error.getMessage());
                checkPermissionsAndStartServices(patientUid);
            }
        });
    }

    private void showFirstTimePatientSetup(String patientUid) {
        new AlertDialog.Builder(this)
                .setTitle("Welcome to RecallLive!")
                .setMessage("We'll organize your photos and create daily memory videos for you.\n\n" +
                        "Your photos will be analyzed privately on your device and " +
                        "a new memory video will be generated each day.")
                .setPositiveButton("Continue", (dialog, which) -> {
                    checkPermissionsAndStartServices(patientUid);
                })
                .setCancelable(false)
                .show();
    }

    private void checkPermissionsAndStartServices(String patientUid) {
        if (hasStoragePermission()) {
            // FIXED: Always treat login as "signup/login" trigger (true) for video generation
            startAutomaticServices(patientUid);
        } else {
            requestStoragePermission(patientUid);
        }
    }

    /**
     * FIXED: Removed isFirstTime parameter - login always generates 10 videos
     */
    private void startAutomaticServices(String patientUid) {
        Log.d(TAG, "╔═══════════════════════════════════════════╗");
        Log.d(TAG, "STARTING AUTOMATIC SERVICES (LOGIN)");
        Log.d(TAG, "Patient UID: " + patientUid);
        Log.d(TAG, "╚═══════════════════════════════════════════╝");

        // Start auto clustering
        Log.d(TAG, "▶️ Step 1: Starting auto clustering...");
        autoClusteringService.initializeForPatient(patientUid);
        Log.d(TAG, "✓ Auto clustering initialized");

        // CRITICAL FIX: Always pass TRUE for login to trigger:
        // 1. Cleanup of old videos
        // 2. Generation of 10 new videos
        Log.d(TAG, "▶️ Step 2: Starting automatic video service (LOGIN = 10 videos)...");
        automaticVideoService.initializeForPatient(patientUid, true);
        Log.d(TAG, "✓ Automatic video service initialized");

        // Verify video service is working
        SharedPreferences prefs = getSharedPreferences("RecallLiveVideoPrefs", MODE_PRIVATE);
        int count = prefs.getInt("daily_video_count", 0);
        String date = prefs.getString("last_video_date", "never");

        Log.d(TAG, "╔═══════════════════════════════════════════╗");
        Log.d(TAG, "VIDEO SERVICE STATUS");
        Log.d(TAG, "Current video count: " + count + "/10");
        Log.d(TAG, "Last video date: " + date);
        Log.d(TAG, "╚═══════════════════════════════════════════╝");

        // Show progress message
        Toast.makeText(this,
                "Generating 10 memory videos...",
                Toast.LENGTH_LONG).show();

        // Wait 2 seconds then navigate (give services time to start)
        new android.os.Handler().postDelayed(() -> {
            navigateToHome();
        }, 2000);
    }


    private boolean hasStoragePermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestStoragePermission(String patientUid) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putString("temp_patient_uid", patientUid).apply();

        String permission;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permission = Manifest.permission.READ_MEDIA_IMAGES;
        } else {
            permission = Manifest.permission.READ_EXTERNAL_STORAGE;
        }

        if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
            new AlertDialog.Builder(this)
                    .setTitle("Permission Needed")
                    .setMessage("RecallLive needs access to your photos to create memory videos. " +
                            "Your photos are processed locally and securely.")
                    .setPositiveButton("Grant Permission", (dialog, which) -> {
                        ActivityCompat.requestPermissions(this,
                                new String[]{permission},
                                PERMISSION_REQUEST_CODE);
                    })
                    .setNegativeButton("Later", (dialog, which) -> {
                        Toast.makeText(this, "You can enable photo access later in settings",
                                Toast.LENGTH_LONG).show();
                        navigateToHome();
                    })
                    .show();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{permission},
                    PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            String patientUid = prefs.getString("temp_patient_uid", "");

            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "✓ Storage permission granted");
                if (!patientUid.isEmpty()) {
                    startAutomaticServices(patientUid);
                }
            } else {
                Toast.makeText(this,
                        "Photo access will be needed to create memory videos",
                        Toast.LENGTH_LONG).show();
                navigateToHome();
            }

            prefs.edit().remove("temp_patient_uid").apply();
        }
    }

    private void navigateToHome() {
        // CRITICAL FIX: Check userType is actually set before navigating
        if (userType == null) {
            Log.e(TAG, "❌❌❌ CRITICAL ERROR: userType is NULL ❌❌❌");
            Toast.makeText(getApplicationContext(),
                    "Error: User type not determined. Please try logging in again.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        // Read back SharedPreferences to verify what was saved
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String savedUserType = prefs.getString("user_type", "NOT SET");
        String savedPatientUid = prefs.getString("patient_uid", "NOT SET");
        String savedGuardianUid = prefs.getString("guardian_uid", "NOT SET");
        String linkedPatientUid = prefs.getString("linked_patient_uid", "NOT SET");

        Log.d(TAG, "╔═══════════════════════════════════════════╗");
        Log.d(TAG, "NAVIGATING TO HOME");
        Log.d(TAG, "╚═══════════════════════════════════════════╝");
        Log.d(TAG, "userType boolean: " + (userType ? "TRUE (PATIENT)" : "FALSE (GUARDIAN)"));
        Log.d(TAG, "SharedPrefs user_type: " + savedUserType);
        Log.d(TAG, "SharedPrefs patient_uid: " + savedPatientUid);
        Log.d(TAG, "SharedPrefs guardian_uid: " + savedGuardianUid);
        Log.d(TAG, "SharedPrefs linked_patient_uid: " + linkedPatientUid);
        Log.d(TAG, "╚═══════════════════════════════════════════╝");

        Toast.makeText(getApplicationContext(), "Login successful as " + (userType ? "Patient" : "Guardian"), Toast.LENGTH_SHORT).show();

        Intent intent;
        if (userType) {
            Log.d(TAG, "🚀 LAUNCHING: PatientMainActivity");
            intent = new Intent(getApplicationContext(), PatientMainActivity.class);
        } else {
            Log.d(TAG, "🚀 LAUNCHING: GuardianMainActivity");
            intent = new Intent(getApplicationContext(), GuardianMainActivity.class);
        }

        startActivity(intent);
        finish();

        Log.d(TAG, "✓ Navigation complete");
    }

    public void login(String email, String password) {
        firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            String userId = firebaseAuth.getCurrentUser().getUid();
                            Log.d(TAG, "╔═══════════════════════════════════════════╗");
                            Log.d(TAG, "LOGIN SUCCESSFUL");
                            Log.d(TAG, "User ID: " + userId);
                            Log.d(TAG, "╚═══════════════════════════════════════════╝");

                            // Reset userType before checking
                            userType = null;

                            setUserType(userId);
                        }
                        else {
                            Toast.makeText(getApplicationContext(), "Login failed: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}