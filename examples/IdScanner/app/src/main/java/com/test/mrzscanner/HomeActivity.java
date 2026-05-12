package com.test.mrzscanner;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Home Activity - Main entry point for the MRZ Scanner app.
 * Provides live camera scanning with Dynamsoft SDK.
 */
public class HomeActivity extends AppCompatActivity {

    private static final String TAG = "HomeActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        initViews();
    }

    private void initViews() {
        findViewById(R.id.btn_live_scan).setOnClickListener(v -> {
            navigateToLiveScan();
        });
    }

    private void navigateToLiveScan() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
    }
}
