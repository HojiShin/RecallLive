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

public class GuardianAccountInfoActivity extends AppCompatActivity {

    private static final String TAG = "GuardianAccountInfo";

    private TextView tvEmailValue, tvEmailReveal;
    private TextView tvPasswordValue, tvPasswordReveal;
    private TextView tvPatientEmailValue, tvPatientEmailReveal;
    private TextView tvChangeEmail, tvChangePassword, tvChangePatientEmail;
    private ImageView ivBack;

    private FirebaseAuth firebaseAuth;
    private DatabaseReference databaseReference;
    private String currentUserEmail = "";
    private String currentUserPassword = "";
    private String currentPatientEmail = "";
    private String currentPatientUid = "";
    private boolean emailRevealed = false;
    private boolean passwordRevealed = false;
    private boolean patientEmailRevealed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_guardianaccountinfo);

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

        // Patient email views
        tvPatientEmailValue = findViewById(R.id.tv_patient_email_value);
        tvPatientEmailReveal = findViewById(R.id.tv_patient_email_reveal);
        tvChangePatientEmail = findViewById(R.id.tv_change_patient_email);

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
                    Toast.makeText(GuardianAccountInfoActivity.this,
                            "For security, passwords are encrypted and cannot be shown",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });

        // Patient email reveal
        tvPatientEmailReveal.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!currentPatientEmail.isEmpty()) {
                    if (patientEmailRevealed) {
                        tvPatientEmailValue.setText(maskEmail(currentPatientEmail));
                        tvPatientEmailReveal.setText("Reveal");
                        patientEmailRevealed = false;
                    } else {
                        tvPatientEmailValue.setText(currentPatientEmail);
                        tvPatientEmailReveal.setText("Hide");
                        patientEmailRevealed = true;
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

        // Change patient email
        tvChangePatientEmail.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showChangePatientEmailDialog();
            }
        });
    }

    private void loadUserData() {
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser != null) {
            // Get email from Firebase Auth (actual login email)
            currentUserEmail = currentUser.getEmail();
            if (currentUserEmail != null) {
                tvEmailValue.setText(maskEmail(currentUserEmail));
            }

            String userId = currentUser.getUid();

            // Load guardian data from Firebase Database
            databaseReference.child("Guardian").child(userId).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        // Get patient email and UID
                        String patientEmail = snapshot.child("patient-email").getValue(String.class);
                        String patientUid = snapshot.child("patient-uid").getValue(String.class);

                        if (patientEmail != null && !patientEmail.isEmpty()) {
                            currentPatientEmail = patientEmail;
                            currentPatientUid = patientUid != null ? patientUid : "";
                            tvPatientEmailValue.setText(maskEmail(patientEmail));
                            tvPatientEmailReveal.setVisibility(View.VISIBLE);
                        } else {
                            currentPatientEmail = "";
                            currentPatientUid = "";
                            tvPatientEmailValue.setText("No patient assigned");
                            tvPatientEmailReveal.setVisibility(View.GONE);
                        }
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Toast.makeText(GuardianAccountInfoActivity.this,
                            "Failed to load user data: " + error.getMessage(),
                            Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void showChangeEmailDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Change Email");
        builder.setMessage("This will change your login email. You'll need to verify your password first.\n\n" +
                "Note: If this fails due to security settings, we'll provide alternatives.");

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
                    Toast.makeText(GuardianAccountInfoActivity.this,
                            "Please enter your current password", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (!newEmail.isEmpty() && android.util.Patterns.EMAIL_ADDRESS.matcher(newEmail).matches()) {
                    reauthenticateAndUpdateEmail(password, newEmail);
                } else {
                    Toast.makeText(GuardianAccountInfoActivity.this,
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
            // Create credential for re-authentication
            AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), password);

            // Re-authenticate user
            user.reauthenticate(credential).addOnCompleteListener(new OnCompleteListener<Void>() {
                @Override
                public void onComplete(@NonNull Task<Void> task) {
                    if (task.isSuccessful()) {
                        // Now update the email
                        updateLoginEmail(newEmail);
                    } else {
                        Toast.makeText(GuardianAccountInfoActivity.this,
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

            // Update authentication email
            user.updateEmail(newEmail).addOnCompleteListener(new OnCompleteListener<Void>() {
                @Override
                public void onComplete(@NonNull Task<Void> task) {
                    if (task.isSuccessful()) {
                        // Automatically update email in database (display email)
                        updateEmailInDatabase(newEmail, oldEmail);
                    } else {
                        String errorMessage = "Failed to update email";
                        if (task.getException() != null) {
                            errorMessage = task.getException().getMessage();
                        }
                        Toast.makeText(GuardianAccountInfoActivity.this,
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

            // Update email in database
            Map<String, Object> updates = new HashMap<>();
            updates.put("email", newEmail);

            databaseReference.child("Guardian").child(userId).updateChildren(updates)
                    .addOnCompleteListener(new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {
                            if (task.isSuccessful()) {
                                // Update patient records if needed
                                if (!currentPatientUid.isEmpty()) {
                                    updatePatientGuardianEmail(currentPatientUid, newEmail);
                                }

                                currentUserEmail = newEmail;
                                tvEmailValue.setText(maskEmail(newEmail));
                                emailRevealed = false;
                                tvEmailReveal.setText("Reveal");

                                Toast.makeText(GuardianAccountInfoActivity.this,
                                        "Email updated successfully. Please use your new email to login next time.",
                                        Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(GuardianAccountInfoActivity.this,
                                        "Failed to update email in database",
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
        }
    }

    private void updatePatientGuardianEmail(String patientUid, String newGuardianEmail) {
        if (patientUid != null && !patientUid.isEmpty()) {
            // FIXED: Use consistent naming (without hyphens to match signup)
            Map<String, Object> patientUpdate = new HashMap<>();
            patientUpdate.put("guardianEmail", newGuardianEmail);
            databaseReference.child("Patient").child(patientUid).updateChildren(patientUpdate)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "✓ Updated patient's guardian email");
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "✗ Failed to update patient guardian email: " + e.getMessage());
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
                    Toast.makeText(GuardianAccountInfoActivity.this,
                            "Please fill all fields", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (newPassword.length() < 6) {
                    Toast.makeText(GuardianAccountInfoActivity.this,
                            "New password must be at least 6 characters", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (!newPassword.equals(confirmPassword)) {
                    Toast.makeText(GuardianAccountInfoActivity.this,
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
                                    Toast.makeText(GuardianAccountInfoActivity.this,
                                            "Password updated successfully", Toast.LENGTH_SHORT).show();
                                } else {
                                    String errorMessage = "Failed to update password";
                                    if (task.getException() != null) {
                                        errorMessage = task.getException().getMessage();
                                    }
                                    Toast.makeText(GuardianAccountInfoActivity.this,
                                            errorMessage, Toast.LENGTH_LONG).show();
                                }
                            }
                        });
                    } else {
                        Toast.makeText(GuardianAccountInfoActivity.this,
                                "Authentication failed. Please check your current password.",
                                Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
    }

    private void showChangePatientEmailDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Change Patient Email");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        input.setHint("Enter patient email (or leave empty to remove)");
        if (!currentPatientEmail.isEmpty()) {
            input.setText(currentPatientEmail);
        }
        builder.setView(input);

        builder.setPositiveButton("Update", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String newPatientEmail = input.getText().toString().trim();
                if (!newPatientEmail.isEmpty()) {
                    if (android.util.Patterns.EMAIL_ADDRESS.matcher(newPatientEmail).matches()) {
                        updatePatientEmail(newPatientEmail);
                    } else {
                        Toast.makeText(GuardianAccountInfoActivity.this, "Please enter a valid email", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    removePatient();
                }
            }
        });

        if (!currentPatientEmail.isEmpty()) {
            builder.setNeutralButton("Remove Patient", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    removePatient();
                }
            });
        }

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void updatePatientEmail(String newPatientEmail) {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user != null) {
            String guardianUid = user.getUid();
            String guardianEmail = currentUserEmail;

            Log.d(TAG, "===========================================");
            Log.d(TAG, "UPDATING PATIENT");
            Log.d(TAG, "===========================================");
            Log.d(TAG, "Guardian UID: " + guardianUid);
            Log.d(TAG, "Guardian Email: " + guardianEmail);
            Log.d(TAG, "Old Patient Email: " + currentPatientEmail);
            Log.d(TAG, "Old Patient UID: " + currentPatientUid);
            Log.d(TAG, "New Patient Email: " + newPatientEmail);

            // CRITICAL: Clear old patient's guardian info FIRST, with proper completion handling
            if (!currentPatientUid.isEmpty() && !currentPatientEmail.equals(newPatientEmail)) {
                Log.d(TAG, "Step 1: Clearing old patient's guardian info...");
                clearPatientGuardianInfo(currentPatientUid, () -> {
                    // Only proceed after old patient is cleared
                    Log.d(TAG, "Step 2: Old patient cleared, now searching for new patient...");
                    searchAndLinkNewPatient(newPatientEmail, guardianUid, guardianEmail);
                });
            } else {
                // No old patient, proceed directly
                Log.d(TAG, "No old patient to clear, searching for new patient...");
                searchAndLinkNewPatient(newPatientEmail, guardianUid, guardianEmail);
            }
        }
    }

    private void searchAndLinkNewPatient(String newPatientEmail, String guardianUid, String guardianEmail) {
        // Search for the new patient by email
        databaseReference.child("Patient").orderByChild("email").equalTo(newPatientEmail)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String newPatientUid = "";
                        boolean patientFound = false;

                        for (DataSnapshot patientSnapshot : snapshot.getChildren()) {
                            patientFound = true;
                            newPatientUid = patientSnapshot.getKey();
                            Log.d(TAG, "Found new patient with UID: " + newPatientUid);

                            // Check if this patient already has a different guardian
                            String existingGuardianUid = patientSnapshot.child("guardianUid").getValue(String.class);
                            if (existingGuardianUid == null || existingGuardianUid.isEmpty()) {
                                existingGuardianUid = patientSnapshot.child("guardian-uid").getValue(String.class);
                            }

                            if (existingGuardianUid != null && !existingGuardianUid.isEmpty() && !existingGuardianUid.equals(guardianUid)) {
                                Log.w(TAG, "Patient already has a different guardian: " + existingGuardianUid);
                                Toast.makeText(GuardianAccountInfoActivity.this,
                                        "This patient already has a different guardian assigned",
                                        Toast.LENGTH_SHORT).show();
                                return;
                            }
                            break;
                        }

                        final String finalPatientUid = newPatientUid;
                        final boolean finalPatientFound = patientFound;

                        Log.d(TAG, "Step 3: Updating guardian record...");

                        // Update guardian record with new patient info
                        Map<String, Object> guardianUpdates = new HashMap<>();
                        guardianUpdates.put("patient-email", newPatientEmail);
                        guardianUpdates.put("patient-uid", finalPatientUid);

                        databaseReference.child("Guardian").child(guardianUid).updateChildren(guardianUpdates)
                                .addOnCompleteListener(new OnCompleteListener<Void>() {
                                    @Override
                                    public void onComplete(@NonNull Task<Void> task) {
                                        if (task.isSuccessful()) {
                                            Log.d(TAG, "✓ Guardian record updated successfully");

                                            // If patient exists, update their guardian info
                                            if (finalPatientFound && !finalPatientUid.isEmpty()) {
                                                Log.d(TAG, "Step 4: Updating new patient's record...");

                                                // FIXED: Use consistent naming
                                                Map<String, Object> patientUpdates = new HashMap<>();
                                                patientUpdates.put("guardianEmail", guardianEmail);
                                                patientUpdates.put("guardianUid", guardianUid);
                                                patientUpdates.put("hasGuardian", true);

                                                databaseReference.child("Patient").child(finalPatientUid).updateChildren(patientUpdates)
                                                        .addOnCompleteListener(new OnCompleteListener<Void>() {
                                                            @Override
                                                            public void onComplete(@NonNull Task<Void> task) {
                                                                if (task.isSuccessful()) {
                                                                    Log.d(TAG, "✓ New patient record updated successfully");
                                                                    Log.d(TAG, "===========================================");
                                                                    Log.d(TAG, "PATIENT UPDATE COMPLETE");
                                                                    Log.d(TAG, "===========================================");
                                                                } else {
                                                                    Log.e(TAG, "✗ Failed to update new patient record: " + task.getException());
                                                                }
                                                            }
                                                        });
                                            }

                                            // Update local variables and UI
                                            currentPatientEmail = newPatientEmail;
                                            currentPatientUid = finalPatientUid;
                                            tvPatientEmailValue.setText(maskEmail(newPatientEmail));
                                            tvPatientEmailReveal.setVisibility(View.VISIBLE);
                                            patientEmailRevealed = false;
                                            tvPatientEmailReveal.setText("Reveal");

                                            String message = finalPatientFound ?
                                                    "Patient linked successfully" :
                                                    "Patient email saved (patient account not found yet)";
                                            Toast.makeText(GuardianAccountInfoActivity.this, message, Toast.LENGTH_SHORT).show();
                                        } else {
                                            Log.e(TAG, "✗ Failed to update guardian record: " + task.getException());
                                            Toast.makeText(GuardianAccountInfoActivity.this,
                                                    "Failed to update patient email",
                                                    Toast.LENGTH_SHORT).show();
                                        }
                                    }
                                });
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Database error: " + error.getMessage());
                        Toast.makeText(GuardianAccountInfoActivity.this,
                                "Error: " + error.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // Callback interface for async operation
    private interface ClearCallback {
        void onComplete();
    }

    private void clearPatientGuardianInfo(String patientUid, ClearCallback callback) {
        if (patientUid != null && !patientUid.isEmpty()) {
            Log.d(TAG, "Clearing patient guardian info for UID: " + patientUid);

            // FIXED: Use consistent naming
            Map<String, Object> clearGuardian = new HashMap<>();
            clearGuardian.put("guardianEmail", "");
            clearGuardian.put("guardianUid", "");
            clearGuardian.put("hasGuardian", false);

            databaseReference.child("Patient").child(patientUid).updateChildren(clearGuardian)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "✓ Successfully cleared patient's guardian info");
                        if (callback != null) {
                            callback.onComplete();
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "✗ Failed to clear patient guardian info: " + e.getMessage());
                        // Still proceed even if clear fails
                        if (callback != null) {
                            callback.onComplete();
                        }
                    });
        } else {
            Log.d(TAG, "No patient UID provided, skipping clear");
            if (callback != null) {
                callback.onComplete();
            }
        }
    }

    private void removePatient() {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user != null) {
            String guardianUid = user.getUid();

            Log.d(TAG, "===========================================");
            Log.d(TAG, "REMOVING PATIENT");
            Log.d(TAG, "===========================================");
            Log.d(TAG, "Guardian UID: " + guardianUid);
            Log.d(TAG, "Patient to remove: " + currentPatientEmail);
            Log.d(TAG, "Patient UID: " + currentPatientUid);

            // Clear guardian info from current patient first
            if (!currentPatientUid.isEmpty()) {
                Log.d(TAG, "Step 1: Clearing patient's guardian info...");
                clearPatientGuardianInfo(currentPatientUid, () -> {
                    // Only proceed after patient is cleared
                    Log.d(TAG, "Step 2: Clearing guardian's patient info...");
                    clearGuardianPatientInfo(guardianUid);
                });
            } else {
                Log.d(TAG, "No patient to clear, clearing guardian's patient info...");
                clearGuardianPatientInfo(guardianUid);
            }
        }
    }

    private void clearGuardianPatientInfo(String guardianUid) {
        // Clear patient info from guardian
        Map<String, Object> guardianUpdates = new HashMap<>();
        guardianUpdates.put("patient-email", "");
        guardianUpdates.put("patient-uid", "");

        databaseReference.child("Guardian").child(guardianUid).updateChildren(guardianUpdates)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "✓ Patient removed from guardian record");
                            Log.d(TAG, "===========================================");
                            Log.d(TAG, "PATIENT REMOVAL COMPLETE");
                            Log.d(TAG, "===========================================");

                            currentPatientEmail = "";
                            currentPatientUid = "";
                            tvPatientEmailValue.setText("No patient assigned");
                            tvPatientEmailReveal.setVisibility(View.GONE);
                            patientEmailRevealed = false;
                            Toast.makeText(GuardianAccountInfoActivity.this,
                                    "Patient removed successfully",
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            Log.e(TAG, "✗ Failed to remove patient: " + task.getException());
                            Toast.makeText(GuardianAccountInfoActivity.this,
                                    "Failed to remove patient",
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