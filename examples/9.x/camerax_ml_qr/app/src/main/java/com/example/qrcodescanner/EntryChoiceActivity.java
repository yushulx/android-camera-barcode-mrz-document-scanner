package com.example.qrcodescanner;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

public class EntryChoiceActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.entry_choice);

        findViewById(R.id.camerax_entry_point).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(EntryChoiceActivity.this, CameraXActivity.class);
                startActivity(intent);
            }
        });

        findViewById(R.id.dce_entry_point).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(EntryChoiceActivity.this, DceActivity.class);
                startActivity(intent);
            }
        });
    }
}
