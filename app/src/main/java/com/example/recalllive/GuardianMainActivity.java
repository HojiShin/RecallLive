package com.example.recalllive;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class GuardianMainActivity extends AppCompatActivity {

    com.example.recalllive.GuardianHomeFragment fragment1;
    GuardianSettingsFragment fragment2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_guardianmain);


        fragment1 = new com.example.recalllive.GuardianHomeFragment();
        fragment2 = new GuardianSettingsFragment();
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
                            getSupportFragmentManager().beginTransaction().replace(R.id.container, fragment2).commit();

                            return true;
                        }

                        return false;
                    }
                }
        );
    }
}