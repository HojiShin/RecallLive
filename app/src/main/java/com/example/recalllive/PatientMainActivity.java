package com.example.recalllive;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class PatientMainActivity extends AppCompatActivity {

    PatientHomeFragment fragment1;
        PatientQuizFragment fragment2;
    PatientSettingsFragment fragment3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_patientmain);

        fragment1 = new PatientHomeFragment();
        fragment2 = new PatientQuizFragment();
        fragment3 = new PatientSettingsFragment();
        getSupportFragmentManager().beginTransaction().replace(R.id.container, fragment1).commit();

        BottomNavigationView bottomNavigation = findViewById(R.id.bottom_navigation);
        bottomNavigation.setOnNavigationItemSelectedListener(
                new BottomNavigationView.OnNavigationItemSelectedListener() {
                    @Override
                    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                        int itemId = item.getItemId();
                        if (itemId == R.id.nav_home) {
                            getSupportFragmentManager().beginTransaction().replace(R.id.container, fragment1).commit();

                            return true;
                        }else if (itemId == R.id.nav_settings){
                            getSupportFragmentManager().beginTransaction().replace(R.id.container, fragment3).commit();

                            return true;
                        }else if (itemId == R.id.nav_quiz){
                            getSupportFragmentManager().beginTransaction().replace(R.id.container, fragment2).commit();

                            return true;

                        }

                        return false;
                    }
                }
        );
    }


}