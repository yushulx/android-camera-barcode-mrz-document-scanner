package com.dynamsoft.documentscanner;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.dynamsoft.documentscanner.scan.DocumentScannerActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Dynamsoft Scanner Card
        findViewById(R.id.card_dynamsoft).setOnClickListener(v -> {
            Intent intent = new Intent(this, DocumentScannerActivity.class);
            startActivity(intent);
        });

        // Google ML Kit Scanner Card
        findViewById(R.id.card_google_mlkit).setOnClickListener(v -> {
            Intent intent = new Intent(this, GoogleDocScannerActivity.class);
            startActivity(intent);
        });
    }
}