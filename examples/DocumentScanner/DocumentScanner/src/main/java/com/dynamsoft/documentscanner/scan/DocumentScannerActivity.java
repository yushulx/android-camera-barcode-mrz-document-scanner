package com.dynamsoft.documentscanner.scan;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.dynamsoft.documentscanner.R;
import com.google.android.material.appbar.MaterialToolbar;

public class DocumentScannerActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_document_scanner);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        TextView tvStartCapturingError = findViewById(R.id.tv_start_capturing_error);

        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        DocumentScannerViewModel viewModel = new ViewModelProvider(this).get(DocumentScannerViewModel.class);
        viewModel.actionBarTitle.observe(this, title -> getSupportActionBar().setTitle(!title.isBlank() ? title : getText(R.string.scan_page_title)));

        viewModel.startCapturingError.observe(this, error -> setTextAndVisible(tvStartCapturingError, error));

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container, new ScannerFragment())
                    .commitNow();
        }
    }

    private void setTextAndVisible(@NonNull TextView textView, @Nullable String text) {
        if (text != null && !text.isBlank()) {
            textView.setVisibility(View.VISIBLE);
            textView.setText(text);
        }
    }
}