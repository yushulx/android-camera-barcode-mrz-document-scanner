package com.example.barcodescanner;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.dynamsoft.dbr.BarcodeResultItem;
import com.dynamsoft.dbrbundle.ui.*;

public class MainActivity extends AppCompatActivity {
    private ActivityResultLauncher<BarcodeScannerConfig> launcher;
    private BarcodeScannerConfig config = new BarcodeScannerConfig();
    private TextView textView;
    private final String LICENSE_KEY = "DLS2eyJoYW5kc2hha2VDb2RlIjoiMjAwMDAxLTE2NDk4Mjk3OTI2MzUiLCJvcmdhbml6YXRpb25JRCI6IjIwMDAwMSIsInNlc3Npb25QYXNzd29yZCI6IndTcGR6Vm05WDJrcEQ5YUoifQ==";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        config.setLicense(LICENSE_KEY);

        launcher = registerForActivityResult(new BarcodeScannerActivity.ResultContract(), result -> {
            if (result.getResultStatus() == BarcodeScanResult.EnumResultStatus.RS_FINISHED
                    && result.getBarcodes() != null) {
                textView.setText("");
                for (int i = 0; i < result.getBarcodes().length; i++) {
                    BarcodeResultItem barcode = result.getBarcodes()[i];
                    String content = String.format("Result %d:\nFormat: %s\nContent: %s\n\n", i,
                            barcode.getFormatString(),
                            barcode.getText());

                    textView.append(content);
                }
            } else if (result.getResultStatus() == BarcodeScanResult.EnumResultStatus.RS_CANCELED) {
                textView.setText("Scan canceled.");
            }
            if (result.getErrorString() != null && !result.getErrorString().isEmpty()) {
                textView.setText(result.getErrorString());
            }
        });
        findViewById(R.id.btn_single).setOnClickListener(this::handleButtonClick);
        findViewById(R.id.btn_multi).setOnClickListener(this::handleButtonClick);

        textView = findViewById(R.id.tv_result);
    }

    private void handleButtonClick(View v) {
        int id = v.getId();
        if (id == R.id.btn_single) {
            config.setScanningMode(EnumScanningMode.SM_SINGLE);
        } else if (id == R.id.btn_multi) {
            config.setScanningMode(EnumScanningMode.SM_MULTIPLE);
        }
        launcher.launch(config);
    }
}