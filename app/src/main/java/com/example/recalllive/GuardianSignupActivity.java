package com.example.recalllive;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;


public class GuardianSignupActivity extends AppCompatActivity {
    Button guardianToPatientSignUpBtn;
    Button guardianSignUpToLoginBtn;
    private FirebaseAuth firebaseAuth;
    private EditText editTextEmail, editTextPassword;
    private Button button;
    FirebaseDatabase database = FirebaseDatabase.getInstance();
    DatabaseReference myRef = database.getReference().child("Data");
    private DatabaseReference mDatabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_guardiansignup);

        guardianToPatientSignUpBtn = findViewById(R.id.btnPatient);
        guardianToPatientSignUpBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getApplicationContext(), PatientSignupActivity.class);
                startActivity(intent);
            }
        });

        guardianSignUpToLoginBtn = findViewById(R.id.btnAlreadyHaveAccountGuardian);
        guardianSignUpToLoginBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getApplicationContext(), LoginActivity.class);
                startActivity(intent);
            }
        });

        firebaseAuth = FirebaseAuth.getInstance();

        editTextEmail = findViewById(R.id.etEmailPhone);
        editTextPassword = findViewById(R.id.etPassword);
        button = findViewById(R.id.btnSignUp);

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String email = editTextEmail.getText().toString();
                String password = editTextPassword.getText().toString();
                Pattern pattern = Patterns.EMAIL_ADDRESS;

                if (email.equals("") || password.equals("")) {
                    Toast.makeText(getApplicationContext(), "Please enter an email and password.", Toast.LENGTH_LONG).show();
                } else if (!pattern.matcher(email).matches()) {
                    Toast.makeText(getApplicationContext(), "The email format is not correct.", Toast.LENGTH_LONG).show();
                } else if (password.length() < 6) {
                    Toast.makeText(getApplicationContext(), "Please enter a password that is atleast 6 characters.", Toast.LENGTH_LONG).show();
                } else {
                    // Call register with email - the database write will happen inside register method
                    register(email, password);
                }
            }
        });
    }

    void register(String email, String password) {
        firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            // Now we can get the current user after successful registration
                            FirebaseUser currentUser = firebaseAuth.getCurrentUser();
                            if (currentUser != null) {
                                // Save guardian data to database
                                DatabaseReference mDatabase = FirebaseDatabase.getInstance().getReference();
                                DatabaseReference guardianRef = mDatabase.child("Guardian").child(currentUser.getUid());

                                Map<String, String> guardianInfo = new HashMap<>();
                                guardianInfo.put("email", email);
                                guardianInfo.put("uid", currentUser.getUid());
                                guardianInfo.put("patient-email", "");
                                guardianInfo.put("patient-uid", "");

                                guardianRef.setValue(guardianInfo)
                                        .addOnCompleteListener(new OnCompleteListener<Void>() {
                                            @Override
                                            public void onComplete(@NonNull Task<Void> task) {
                                                if (task.isSuccessful()) {
                                                    Toast.makeText(getApplicationContext(), "Sign up successful.", Toast.LENGTH_LONG).show();
                                                    Intent intent = new Intent(getApplicationContext(), GuardianMainActivity.class);
                                                    startActivity(intent);
                                                    finish();
                                                } else {
                                                    Toast.makeText(getApplicationContext(), "Failed to save guardian data.", Toast.LENGTH_LONG).show();
                                                }
                                            }
                                        });
                            }
                        } else {
                            Toast.makeText(getApplicationContext(), "Sign up failed: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }
}