package com.example.cnc;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNavigationView;

    private SimulatorFragment simulatorFragment;
    private TutorialsFragment tutorialsFragment;
    private AiAssistantFragment aiAssistantFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        simulatorFragment = new SimulatorFragment();
        tutorialsFragment = new TutorialsFragment();
        aiAssistantFragment = new AiAssistantFragment();

        bottomNavigationView = findViewById(R.id.bottom_navigation);

        // Load initial fragment (Simulator)
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, simulatorFragment)
                    .commit();
        }

        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            Fragment selectedFragment = null;

            if (itemId == R.id.nav_simulator) {
                selectedFragment = simulatorFragment;
            } else if (itemId == R.id.nav_tutorials) {
                selectedFragment = tutorialsFragment;
            } else if (itemId == R.id.nav_ai) {
                selectedFragment = aiAssistantFragment;
            }

            if (selectedFragment != null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, selectedFragment)
                        .commit();
                return true;
            }
            return false;
        });
    }

    public void loadGcodeToSimulator(String gcode) {
        simulatorFragment.setPendingGcode(gcode);
        bottomNavigationView.setSelectedItemId(R.id.nav_simulator);
    }
}
