package com.example.recalllive;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class PatientAccountInfoActivity extends AppCompatActivity {

    private static final String TAG = "PatientAccountInfo";

    private TextView tvEmailValue, tvEmailReveal;
    private TextView tvPasswordValue, tvPasswordReveal;
    private TextView tvGuardianEmailValue, tvGuardianEmailReveal;
    private TextView tvChangeEmail, tvChangePassword, tvChangeGuardianEmail;
    private ImageView ivBack;

    private FirebaseAuth firebaseAuth;
    private DatabaseReference databaseReference;
    private String currentUserEmail = "";
    private String currentGuardianEmail = "";
    private String currentGuardianUid = "";
    private boolean emailRevealed = false;
    private boolean passwordRevealed = false;
    private boolean guardianEmailRevealed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_patientaccountinfo);

        // Initialize Firebase
        firebaseAuth = FirebaseAuth.getInstance();
        databaseReference = FirebaseDatabase.getInstance().getReference();

        // Initialize views
        initializeViews();

        // Set up click listeners
        setupClickListeners();

        // Load user data from Firebase
        loadUserData();
    }

    private void initializeViews() {
        // Email views
        tvEmailValue = findViewById(R.id.tv_email_value);
        tvEmailReveal = findViewById(R.id.tv_email_reveal);
        tvChangeEmail = findViewById(R.id.tv_change_email);

        // Password views
        tvPasswordValue = findViewById(R.id.tv_password_value);
        tvPasswordReveal = findViewById(R.id.tv_password_reveal);
        tvChangePassword = findViewById(R.id.tv_change_password);

        // Guardian email views
        tvGuardianEmailValue = findViewById(R.id.tv_guardian_email_value);
        tvGuardianEmailReveal = findViewById(R.id.tv_guardian_email_reveal);
        tvChangeGuardianEmail = findViewById(R.id.tv_change_guardian_email);

        // Back button
        ivBack = findViewById(R.id.iv_back);
    }

    private void setupClickListeners() {
        // Back button
        if (ivBack != null) {
            ivBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish(); // Return to previous screen
                }
            });
        }

        // Email reveal
        tvEmailReveal.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (emailRevealed) {
                    tvEmailValue.setText(maskEmail(currentUserEmail));
                    tvEmailReveal.setText("Reveal");
                    emailRevealed = false;
                } else {
                    tvEmailValue.setText(currentUserEmail);
                    tvEmailReveal.setText("Hide");
                    emailRevealed = true;
                }
            }
        });

        // Password reveal
        tvPasswordReveal.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (passwordRevealed) {
                    tvPasswordValue.setText("*****************");
                    tvPasswordReveal.setText("Reveal");
                    passwordRevealed = false;
                } else {
                    tvPasswordValue.setText("Password cannot be displayed");
                    tvPasswordReveal.setText("Hide");
                    passwordRevealed = true;
                    Toast.makeText(PatientAccountInfoActivity.this,
                            "For security, passwords are encrypted and cannot be shown",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });

        // Guardian email reveal
        tvGuardianEmailReveal.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!currentGuardianEmail.isEmpty()) {
                    if (guardianEmailRevealed) {
                        tvGuardianEmailValue.setText(maskEmail(currentGuardianEmail));
                        tvGuardianEmailReveal.setText("Reveal");
                        guardianEmailRevealed = false;
                    } else {
                        tvGuardianEmailValue.setText(currentGuardianEmail);
                        tvGuardianEmailReveal.setText("Hide");
                        guardianEmailRevealed = true;
                    }
                }
            }
        });

        // Change email
        tvChangeEmail.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showChangeEmailDialog();
            }
        });

        // Change password
        tvChangePassword.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showChangePasswordDialog();
            }
        });

        // Change guardian email
        tvChangeGuardianEmail.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showChangeGuardianEmailDialog();
            }
        });
    }

    private void loadUserData() {
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser != null) {
            // Get email from Firebase Auth
            currentUserEmail = currentUser.getEmail();
            if (currentUserEmail != null) {
                tvEmailValue.setText(maskEmail(currentUserEmail));
            }

            String userId = currentUser.getUid();
            Log.d(TAG, "Loading patient data for UID: " + userId);

            // Load patient data from Firebase Database
            databaseReference.child("Patient").child(userId).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    Log.d(TAG, "Patient snapshot exists: " + snapshot.exists());

                    if (snapshot.exists()) {
                        // Log all children to see what fields exist
                        Log.d(TAG, "Available fields in Patient node:");
                        for (DataSnapshot child : snapshot.getChildren()) {
                            Log.d(TAG, "  - " + child.getKey() + " = " + child.getValue());
                        }

                        // Check ALL possible naming conventions
                        String guardianEmail = null;
                        String guardianUid = null;

                        // Try camelCase first (from signup)
                        if (snapshot.hasChild("guardianEmail")) {
                            guardianEmail = snapshot.child("guardianEmail").getValue(String.class);
                            Log.d(TAG, "Found guardianEmail (camelCase): " + guardianEmail);
                        }

                        // Try with hyphen
                        if ((guardianEmail == null || guardianEmail.isEmpty()) && snapshot.hasChild("guardian-email")) {
                            guardianEmail = snapshot.child("guardian-email").getValue(String.class);
                            Log.d(TAG, "Found guardian-email (with hyphen): " + guardianEmail);
                        }

                        // Try camelCase for UID
                        if (snapshot.hasChild("guardianUid")) {
                            guardianUid = snapshot.child("guardianUid").getValue(String.class);
                            Log.d(TAG, "Found guardianUid (camelCase): " + guardianUid);
                        }

                        // Try with hyphen for UID
                        if ((guardianUid == null || guardianUid.isEmpty()) && snapshot.hasChild("guardian-uid")) {
                            guardianUid = snapshot.child("guardian-uid").getValue(String.class);
                            Log.d(TAG, "Found guardian-uid (with hyphen): " + guardianUid);
                        }

                        Log.d(TAG, "Final guardianEmail: " + guardianEmail);
                        Log.d(TAG, "Final guardianUid: " + guardianUid);

                        if (guardianEmail != null && !guardianEmail.isEmpty()) {
                            currentGuardianEmail = guardianEmail;
                            currentGuardianUid = guardianUid != null ? guardianUid : "";
                            tvGuardianEmailValue.setText(maskEmail(guardianEmail));
                            tvGuardianEmailReveal.setVisibility(View.VISIBLE);
                            Log.d(TAG, "✓ Guardian displayed: " + maskEmail(guardianEmail));
                        } else {
                            currentGuardianEmail = "";
                            currentGuardianUid = "";
                            tvGuardianEmailValue.setText("No guardian assigned");
                            tvGuardianEmailReveal.setVisibility(View.GONE);
                            Log.d(TAG, "✗ No guardian found");
                        }
                    } else {
                        Log.e(TAG, "Patient snapshot does not exist!");
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e(TAG, "Database error: " + error.getMessage());
                    Toast.makeText(PatientAccountInfoActivity.this,
                            "Failed to load user data: " + error.getMessage(),
                            Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            Log.e(TAG, "Current user is null!");
        }
    }

    private void showChangeEmailDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Change Email");
        builder.setMessage("This will change your login email. You'll need to verify your password first.");

        // Create layout for multiple inputs
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        final EditText currentPasswordInput = new EditText(this);
        currentPasswordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        currentPasswordInput.setHint("Enter your current password");
        layout.addView(currentPasswordInput);

        final EditText newEmailInput = new EditText(this);
        newEmailInput.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        newEmailInput.setHint("Enter new email");
        layout.addView(newEmailInput);

        builder.setView(layout);

        builder.setPositiveButton("Update", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String password = currentPasswordInput.getText().toString().trim();
                String newEmail = newEmailInput.getText().toString().trim();

                if (password.isEmpty()) {
                    Toast.makeText(PatientAccountInfoActivity.this,
                            "Please enter your current password", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (!newEmail.isEmpty() && android.util.Patterns.EMAIL_ADDRESS.matcher(newEmail).matches()) {
                    reauthenticateAndUpdateEmail(password, newEmail);
                } else {
                    Toast.makeText(PatientAccountInfoActivity.this,
                            "Please enter a valid email", Toast.LENGTH_SHORT).show();
                }
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void reauthenticateAndUpdateEmail(String password, String newEmail) {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user != null && user.getEmail() != null) {
            AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), password);

            user.reauthenticate(credential).addOnCompleteListener(new OnCompleteListener<Void>() {
                @Override
                public void onComplete(@NonNull Task<Void> task) {
                    if (task.isSuccessful()) {
                        updateLoginEmail(newEmail);
                    } else {
                        Toast.makeText(PatientAccountInfoActivity.this,
                                "Authentication failed. Please check your password.",
                                Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
    }

    private void updateLoginEmail(String newEmail) {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user != null) {
            String oldEmail = currentUserEmail;

            user.updateEmail(newEmail).addOnCompleteListener(new OnCompleteListener<Void>() {
                @Override
                public void onComplete(@NonNull Task<Void> task) {
                    if (task.isSuccessful()) {
                        updateEmailInDatabase(newEmail, oldEmail);
                    } else {
                        String errorMessage = "Failed to update email";
                        if (task.getException() != null) {
                            errorMessage = task.getException().getMessage();
                        }
                        Toast.makeText(PatientAccountInfoActivity.this,
                                errorMessage, Toast.LENGTH_LONG).show();
                    }
                }
            });
        }
    }

    private void updateEmailInDatabase(String newEmail, String oldEmail) {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user != null) {
            String userId = user.getUid();

            Map<String, Object> updates = new HashMap<>();
            updates.put("email", newEmail);

            databaseReference.child("Patient").child(userId).updateChildren(updates)
                    .addOnCompleteListener(new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {
                            if (task.isSuccessful()) {
                                // Update guardian records if needed
                                if (!currentGuardianUid.isEmpty()) {
                                    updateGuardianPatientEmail(currentGuardianUid, newEmail);
                                }

                                currentUserEmail = newEmail;
                                tvEmailValue.setText(maskEmail(newEmail));
                                emailRevealed = false;
                                tvEmailReveal.setText("Reveal");

                                Toast.makeText(PatientAccountInfoActivity.this,
                                        "Email updated successfully. Please use your new email to login next time.",
                                        Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(PatientAccountInfoActivity.this,
                                        "Failed to update email in database",
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
        }
    }

    private void updateGuardianPatientEmail(String guardianUid, String newPatientEmail) {
        if (guardianUid != null && !guardianUid.isEmpty()) {
            Map<String, Object> guardianUpdate = new HashMap<>();
            guardianUpdate.put("patient-email", newPatientEmail);
            databaseReference.child("Guardian").child(guardianUid).updateChildren(guardianUpdate)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "✓ Updated guardian's patient email");
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "✗ Failed to update guardian's patient email: " + e.getMessage());
                    });
        }
    }

    private void showChangePasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Change Password");
        builder.setMessage("Enter your current password and new password.");

        // Create layout for multiple inputs
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        final EditText currentPasswordInput = new EditText(this);
        currentPasswordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        currentPasswordInput.setHint("Current password");
        layout.addView(currentPasswordInput);

        final EditText newPasswordInput = new EditText(this);
        newPasswordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        newPasswordInput.setHint("New password (min 6 characters)");
        layout.addView(newPasswordInput);

        final EditText confirmPasswordInput = new EditText(this);
        confirmPasswordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        confirmPasswordInput.setHint("Confirm new password");
        layout.addView(confirmPasswordInput);

        builder.setView(layout);

        builder.setPositiveButton("Update", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String currentPassword = currentPasswordInput.getText().toString();
                String newPassword = newPasswordInput.getText().toString();
                String confirmPassword = confirmPasswordInput.getText().toString();

                if (currentPassword.isEmpty() || newPassword.isEmpty() || confirmPassword.isEmpty()) {
                    Toast.makeText(PatientAccountInfoActivity.this,
                            "Please fill all fields", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (newPassword.length() < 6) {
                    Toast.makeText(PatientAccountInfoActivity.this,
                            "New password must be at least 6 characters", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (!newPassword.equals(confirmPassword)) {
                    Toast.makeText(PatientAccountInfoActivity.this,
                            "New passwords don't match", Toast.LENGTH_SHORT).show();
                    return;
                }

                reauthenticateAndUpdatePassword(currentPassword, newPassword);
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void reauthenticateAndUpdatePassword(String currentPassword, String newPassword) {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user != null && user.getEmail() != null) {
            // Create credential for re-authentication
            AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), currentPassword);

            // Re-authenticate user
            user.reauthenticate(credential).addOnCompleteListener(new OnCompleteListener<Void>() {
                @Override
                public void onComplete(@NonNull Task<Void> task) {
                    if (task.isSuccessful()) {
                        // Now update the password
                        user.updatePassword(newPassword).addOnCompleteListener(new OnCompleteListener<Void>() {
                            @Override
                            public void onComplete(@NonNull Task<Void> task) {
                                if (task.isSuccessful()) {
                                    Toast.makeText(PatientAccountInfoActivity.this,
                                            "Password updated successfully", Toast.LENGTH_SHORT).show();
                                } else {
                                    String errorMessage = "Failed to update password";
                                    if (task.getException() != null) {
                                        errorMessage = task.getException().getMessage();
                                    }
                                    Toast.makeText(PatientAccountInfoActivity.this,
                                            errorMessage, Toast.LENGTH_LONG).show();
                                }
                            }
                        });
                    } else {
                        Toast.makeText(PatientAccountInfoActivity.this,
                                "Authentication failed. Please check your current password.",
                                Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
    }

    private void showChangeGuardianEmailDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Change Guardian Email");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        input.setHint("Enter guardian email (or leave empty to remove)");
        if (!currentGuardianEmail.isEmpty()) {
            input.setText(currentGuardianEmail);
        }
        builder.setView(input);

        builder.setPositiveButton("Update", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String newGuardianEmail = input.getText().toString().trim();
                if (!newGuardianEmail.isEmpty()) {
                    if (android.util.Patterns.EMAIL_ADDRESS.matcher(newGuardianEmail).matches()) {
                        updateGuardianEmail(newGuardianEmail);
                    } else {
                        Toast.makeText(PatientAccountInfoActivity.this,
                                "Please enter a valid email", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    removeGuardian();
                }
            }
        });

        if (!currentGuardianEmail.isEmpty()) {
            builder.setNeutralButton("Remove Guardian", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    removeGuardian();
                }
            });
        }

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void updateGuardianEmail(String newGuardianEmail) {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user != null) {
            String patientUid = user.getUid();
            String patientEmail = currentUserEmail;

            Log.d(TAG, "===========================================");
            Log.d(TAG, "UPDATING GUARDIAN");
            Log.d(TAG, "===========================================");
            Log.d(TAG, "Patient UID: " + patientUid);
            Log.d(TAG, "Patient Email: " + patientEmail);
            Log.d(TAG, "Old Guardian Email: " + currentGuardianEmail);
            Log.d(TAG, "Old Guardian UID: " + currentGuardianUid);
            Log.d(TAG, "New Guardian Email: " + newGuardianEmail);

            // CRITICAL: Clear old guardian's patient info FIRST, with proper completion handling
            if (!currentGuardianUid.isEmpty()) {
                Log.d(TAG, "Step 1: Clearing old guardian's patient info...");
                clearGuardianPatientInfo(currentGuardianUid, () -> {
                    // Only proceed after old guardian is cleared
                    Log.d(TAG, "Step 2: Old guardian cleared, now searching for new guardian...");
                    searchAndLinkNewGuardian(newGuardianEmail, patientUid, patientEmail);
                });
            } else {
                // No old guardian, proceed directly
                Log.d(TAG, "No old guardian to clear, searching for new guardian...");
                searchAndLinkNewGuardian(newGuardianEmail, patientUid, patientEmail);
            }
        }
    }

    private void searchAndLinkNewGuardian(String newGuardianEmail, String patientUid, String patientEmail) {
        // Search for the new guardian by email
        databaseReference.child("Guardian").orderByChild("email").equalTo(newGuardianEmail)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String newGuardianUid = "";
                        boolean guardianFound = false;

                        for (DataSnapshot guardianSnapshot : snapshot.getChildren()) {
                            guardianFound = true;
                            newGuardianUid = guardianSnapshot.getKey();
                            Log.d(TAG, "Found new guardian with UID: " + newGuardianUid);

                            // Check if this guardian already has a different patient
                            String existingPatientUid = guardianSnapshot.child("patient-uid").getValue(String.class);
                            if (existingPatientUid != null && !existingPatientUid.isEmpty() && !existingPatientUid.equals(patientUid)) {
                                Log.w(TAG, "Guardian already has a different patient: " + existingPatientUid);
                                Toast.makeText(PatientAccountInfoActivity.this,
                                        "This guardian already has a different patient assigned",
                                        Toast.LENGTH_SHORT).show();
                                return;
                            }
                            break;
                        }

                        final String finalGuardianUid = newGuardianUid;
                        final boolean finalGuardianFound = guardianFound;

                        Log.d(TAG, "Step 3: Updating patient record...");

                        // Update patient record with new guardian info
                        Map<String, Object> patientUpdates = new HashMap<>();
                        patientUpdates.put("guardianEmail", newGuardianEmail);
                        patientUpdates.put("guardianUid", finalGuardianUid);
                        patientUpdates.put("hasGuardian", true);

                        databaseReference.child("Patient").child(patientUid).updateChildren(patientUpdates)
                                .addOnCompleteListener(new OnCompleteListener<Void>() {
                                    @Override
                                    public void onComplete(@NonNull Task<Void> task) {
                                        if (task.isSuccessful()) {
                                            Log.d(TAG, "✓ Patient record updated successfully");

                                            // If guardian exists, update their patient info
                                            if (finalGuardianFound && !finalGuardianUid.isEmpty()) {
                                                Log.d(TAG, "Step 4: Updating new guardian's record...");

                                                Map<String, Object> guardianUpdates = new HashMap<>();
                                                guardianUpdates.put("patient-email", patientEmail);
                                                guardianUpdates.put("patient-uid", patientUid);

                                                databaseReference.child("Guardian").child(finalGuardianUid).updateChildren(guardianUpdates)
                                                        .addOnCompleteListener(new OnCompleteListener<Void>() {
                                                            @Override
                                                            public void onComplete(@NonNull Task<Void> task) {
                                                                if (task.isSuccessful()) {
                                                                    Log.d(TAG, "✓ New guardian record updated successfully");
                                                                    Log.d(TAG, "===========================================");
                                                                    Log.d(TAG, "GUARDIAN UPDATE COMPLETE");
                                                                    Log.d(TAG, "===========================================");
                                                                } else {
                                                                    Log.e(TAG, "✗ Failed to update new guardian record: " + task.getException());
                                                                }
                                                            }
                                                        });
                                            }

                                            // Update local variables and UI
                                            currentGuardianEmail = newGuardianEmail;
                                            currentGuardianUid = finalGuardianUid;
                                            tvGuardianEmailValue.setText(maskEmail(newGuardianEmail));
                                            tvGuardianEmailReveal.setVisibility(View.VISIBLE);
                                            guardianEmailRevealed = false;
                                            tvGuardianEmailReveal.setText("Reveal");

                                            String message = finalGuardianFound ?
                                                    "Guardian linked successfully" :
                                                    "Guardian email saved (guardian account not found yet)";
                                            Toast.makeText(PatientAccountInfoActivity.this, message, Toast.LENGTH_SHORT).show();
                                        } else {
                                            Log.e(TAG, "✗ Failed to update patient record: " + task.getException());
                                            Toast.makeText(PatientAccountInfoActivity.this,
                                                    "Failed to update guardian email",
                                                    Toast.LENGTH_SHORT).show();
                                        }
                                    }
                                });
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Database error: " + error.getMessage());
                        Toast.makeText(PatientAccountInfoActivity.this,
                                "Error: " + error.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // Callback interface for async operation
    private interface ClearCallback {
        void onComplete();
    }

    private void clearGuardianPatientInfo(String guardianUid, ClearCallback callback) {
        if (guardianUid != null && !guardianUid.isEmpty()) {
            Log.d(TAG, "Clearing guardian patient info for UID: " + guardianUid);

            Map<String, Object> clearPatient = new HashMap<>();
            clearPatient.put("patient-email", "");
            clearPatient.put("patient-uid", "");

            databaseReference.child("Guardian").child(guardianUid).updateChildren(clearPatient)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "✓ Successfully cleared guardian's patient info");
                        if (callback != null) {
                            callback.onComplete();
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "✗ Failed to clear guardian patient info: " + e.getMessage());
                        // Still proceed even if clear fails
                        if (callback != null) {
                            callback.onComplete();
                        }
                    });
        } else {
            Log.d(TAG, "No guardian UID provided, skipping clear");
            if (callback != null) {
                callback.onComplete();
            }
        }
    }

    private void removeGuardian() {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user != null) {
            String patientUid = user.getUid();

            Log.d(TAG, "===========================================");
            Log.d(TAG, "REMOVING GUARDIAN");
            Log.d(TAG, "===========================================");
            Log.d(TAG, "Patient UID: " + patientUid);
            Log.d(TAG, "Guardian to remove: " + currentGuardianEmail);
            Log.d(TAG, "Guardian UID: " + currentGuardianUid);

            // Clear patient info from current guardian first
            if (!currentGuardianUid.isEmpty()) {
                Log.d(TAG, "Step 1: Clearing guardian's patient info...");
                clearGuardianPatientInfo(currentGuardianUid, () -> {
                    // Only proceed after guardian is cleared
                    Log.d(TAG, "Step 2: Clearing patient's guardian info...");
                    clearPatientGuardianInfo(patientUid);
                });
            } else {
                Log.d(TAG, "No guardian to clear, clearing patient's guardian info...");
                clearPatientGuardianInfo(patientUid);
            }
        }
    }

    private void clearPatientGuardianInfo(String patientUid) {
        // Use consistent naming (without hyphens)
        Map<String, Object> patientUpdates = new HashMap<>();
        patientUpdates.put("guardianEmail", "");
        patientUpdates.put("guardianUid", "");
        patientUpdates.put("hasGuardian", false);

        databaseReference.child("Patient").child(patientUid).updateChildren(patientUpdates)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "✓ Guardian removed from patient record");
                            Log.d(TAG, "===========================================");
                            Log.d(TAG, "GUARDIAN REMOVAL COMPLETE");
                            Log.d(TAG, "===========================================");

                            currentGuardianEmail = "";
                            currentGuardianUid = "";
                            tvGuardianEmailValue.setText("No guardian assigned");
                            tvGuardianEmailReveal.setVisibility(View.GONE);
                            guardianEmailRevealed = false;
                            Toast.makeText(PatientAccountInfoActivity.this,
                                    "Guardian removed successfully",
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            Log.e(TAG, "✗ Failed to remove guardian: " + task.getException());
                            Toast.makeText(PatientAccountInfoActivity.this,
                                    "Failed to remove guardian",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private String maskEmail(String email) {
        if (email == null || email.isEmpty()) {
            return "";
        }

        int atIndex = email.indexOf("@");
        if (atIndex <= 0) {
            return email;
        }

        String localPart = email.substring(0, atIndex);
        String domain = email.substring(atIndex);

        if (localPart.length() <= 2) {
            return "*".repeat(localPart.length()) + domain;
        } else {
            return localPart.substring(0, 2) + "*".repeat(localPart.length() - 2) + domain;
        }
    }
}