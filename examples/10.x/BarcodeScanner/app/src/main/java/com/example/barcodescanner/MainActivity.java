package com.example.barcodescanner;

import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.dynamsoft.dbrbundle.ui.*;

public class MainActivity extends AppCompatActivity {
    private ActivityResultLauncher<BarcodeScannerConfig> launcher;
    private TextView textView;
    private final String LICENSE_KEY = "LICENSE-KEY";
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

        BarcodeScannerConfig config = new BarcodeScannerConfig();
        config.setLicense(LICENSE_KEY);

        launcher = registerForActivityResult(new BarcodeScannerActivity.ResultContract(), result -> {
            if (result.getResultStatus() == BarcodeScanResult.EnumResultStatus.RS_FINISHED && result.getBarcodes() != null) {
                String content = "Result: format: " + result.getBarcodes()[0].getFormatString() + "\n" + "content: "
                        + result.getBarcodes()[0].getText();
                textView.setText(content);
            } else if(result.getResultStatus() == BarcodeScanResult.EnumResultStatus.RS_CANCELED ){
                textView.setText("Scan canceled.");
            }
            if (result.getErrorString() != null && !result.getErrorString().isEmpty()) {
                textView.setText(result.getErrorString());
            }
        });
        findViewById(R.id.btn_navigate).setOnClickListener(v -> launcher.launch(config));
        textView = findViewById(R.id.tv_result);
    }
}