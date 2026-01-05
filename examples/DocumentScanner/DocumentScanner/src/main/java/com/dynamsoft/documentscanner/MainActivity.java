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

        findViewById(R.id.btn_start_capturing).setOnClickListener(v->{
            Intent intent = new Intent(this, DocumentScannerActivity.class);
            startActivity(intent);
        });
    }
}