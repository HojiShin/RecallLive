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
    Boolean userType;
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

        Log.d(TAG, "═══════════════════════════════════════════════");
        Log.d(TAG, "CHECKING USER TYPE FOR: " + userId);
        Log.d(TAG, "═══════════════════════════════════════════════");

        databaseReference.child("Patient").child(userId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Log.d(TAG, "✓ User found as PATIENT");
                    userType = true;

                    SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                    prefs.edit()
                            .putString("user_type", "patient")
                            .putString("patient_uid", userId)
                            .apply();

                    checkPatientClustersAndProceed(userId);
                } else {
                    Log.d(TAG, "Not found in Patient, checking Guardian");

                    databaseReference.child("Guardian").child(userId).addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            if (snapshot.exists()) {
                                Log.d(TAG, "✓ User found as GUARDIAN");
                                userType = false;

                                SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                                prefs.edit()
                                        .putString("user_type", "guardian")
                                        .putString("guardian_uid", userId)
                                        .apply();

                                String patientUid = snapshot.child("patient-uid").getValue(String.class);
                                if (patientUid != null && !patientUid.isEmpty()) {
                                    prefs.edit().putString("linked_patient_uid", patientUid).apply();
                                }

                                navigateToHome();
                            } else {
                                Log.d(TAG, "User not found in Guardian either");
                                Toast.makeText(getApplicationContext(), "User type not found", Toast.LENGTH_LONG).show();
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            Log.e(TAG, "Guardian check error: " + error.getMessage());
                            Toast.makeText(getApplicationContext(), "Error: " + error.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Patient check error: " + error.getMessage());
                Toast.makeText(getApplicationContext(), "Error: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void checkPatientClustersAndProceed(String patientUid) {
        Log.d(TAG, "═══════════════════════════════════════════════");
        Log.d(TAG, "CHECKING PATIENT CLUSTERS");
        Log.d(TAG, "═══════════════════════════════════════════════");

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
                    showFirstTimePatientSetup(patientUid, true);
                } else {
                    Long lastUpdated = snapshot.child("lastUpdated").getValue(Long.class);
                    Long totalClusters = snapshot.child("totalClusters").getValue(Long.class);

                    Log.d(TAG, "✓ CLUSTERS FOUND");
                    Log.d(TAG, "  Total clusters: " + totalClusters);
                    Log.d(TAG, "  Last updated: " + lastUpdated);

                    checkPermissionsAndStartServices(patientUid, false);
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e(TAG, "❌ Error checking clusters: " + error.getMessage());
                checkPermissionsAndStartServices(patientUid, false);
            }
        });
    }

    private void showFirstTimePatientSetup(String patientUid, boolean isFirstTime) {
        new AlertDialog.Builder(this)
                .setTitle("Welcome to RecallLive!")
                .setMessage("We'll organize your photos and create daily memory videos for you.\n\n" +
                        "Your photos will be analyzed privately on your device and " +
                        "a new memory video will be generated each day.")
                .setPositiveButton("Continue", (dialog, which) -> {
                    checkPermissionsAndStartServices(patientUid, isFirstTime);
                })
                .setCancelable(false)
                .show();
    }

    private void checkPermissionsAndStartServices(String patientUid, boolean isFirstTime) {
        if (hasStoragePermission()) {
            startAutomaticServices(patientUid, isFirstTime);
        } else if (isFirstTime) {
            requestStoragePermission(patientUid);
        } else {
            Toast.makeText(this,
                    "Photo access needed for memory videos. Enable in settings.",
                    Toast.LENGTH_SHORT).show();
            navigateToHome();
        }
    }

    private void startAutomaticServices(String patientUid, boolean isFirstTime) {
        Log.d(TAG, "═══════════════════════════════════════════════");
        Log.d(TAG, "STARTING AUTOMATIC SERVICES");
        Log.d(TAG, "Patient UID: " + patientUid);
        Log.d(TAG, "Is First Time: " + isFirstTime);
        Log.d(TAG, "═══════════════════════════════════════════════");

        // Start auto clustering
        Log.d(TAG, "▶️ Step 1: Starting auto clustering...");
        autoClusteringService.initializeForPatient(patientUid);
        Log.d(TAG, "✓ Auto clustering initialized");

        // CRITICAL: Start automatic video generation
        Log.d(TAG, "▶️ Step 2: Starting automatic video service...");
        automaticVideoService.initializeForPatient(patientUid, isFirstTime);
        Log.d(TAG, "✓ Automatic video service initialized");

        // Verify video service is working
        SharedPreferences prefs = getSharedPreferences("RecallLiveVideoPrefs", MODE_PRIVATE);
        int count = prefs.getInt("daily_video_count", 0);
        String date = prefs.getString("last_video_date", "never");

        Log.d(TAG, "═══════════════════════════════════════════════");
        Log.d(TAG, "VIDEO SERVICE STATUS");
        Log.d(TAG, "Current video count: " + count + "/10");
        Log.d(TAG, "Last video date: " + date);
        Log.d(TAG, "═══════════════════════════════════════════════");

        // Show progress message
        Toast.makeText(this,
                "Generating your memory video...",
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
                    startAutomaticServices(patientUid, true);
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
        Toast.makeText(getApplicationContext(), "Login successful", Toast.LENGTH_LONG).show();
        Intent intent;
        if (userType) {
            intent = new Intent(getApplicationContext(), PatientMainActivity.class);
        } else {
            intent = new Intent(getApplicationContext(), GuardianMainActivity.class);
        }
        startActivity(intent);
        finish();
    }

    public void login(String email, String password) {
        firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            String userId = firebaseAuth.getCurrentUser().getUid();
                            Log.d(TAG, "═══════════════════════════════════════════════");
                            Log.d(TAG, "LOGIN SUCCESSFUL");
                            Log.d(TAG, "User ID: " + userId);
                            Log.d(TAG, "═══════════════════════════════════════════════");
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