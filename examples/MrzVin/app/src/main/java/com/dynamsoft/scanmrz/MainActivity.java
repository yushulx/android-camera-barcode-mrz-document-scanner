package com.dynamsoft.scanmrz;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.dynamsoft.mrzscannerbundle.ui.CommonResult;
import com.dynamsoft.mrzscannerbundle.ui.EnumDetectionType;
import com.dynamsoft.mrzscannerbundle.ui.EnumScanMode;
import com.dynamsoft.mrzscannerbundle.ui.MRZData;
import com.dynamsoft.mrzscannerbundle.ui.MRZScanResult;
import com.dynamsoft.mrzscannerbundle.ui.ScannerActivity;
import com.dynamsoft.mrzscannerbundle.ui.ScannerConfig;
import com.dynamsoft.mrzscannerbundle.ui.VINData;
import com.dynamsoft.mrzscannerbundle.ui.VINScanResult;
import com.google.android.material.button.MaterialButtonToggleGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
	private ActivityResultLauncher<ScannerConfig> launcher;
	private LinearLayout content;
	private MaterialButtonToggleGroup toggleGroup;
	private RadioGroup radioGroup;
	ScannerConfig config;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		content = findViewById(R.id.ll_content);

		//optional
		config = new ScannerConfig();
		config.setLicense("DLS2eyJoYW5kc2hha2VDb2RlIjoiMjAwMDAxLTE2NDk4Mjk3OTI2MzUiLCJvcmdhbml6YXRpb25JRCI6IjIwMDAwMSIsInNlc3Npb25QYXNzd29yZCI6IndTcGR6Vm05WDJrcEQ5YUoifQ==");
		config.setTorchButtonVisible(true);
		config.setCloseButtonVisible(true);
		//optional

		//must call
		launcher = registerForActivityResult(new ScannerActivity.ResultContract(), commonResult -> {
			if (commonResult == null) return;

			if (commonResult.getResultStatus() == CommonResult.EnumResultStatus.RS_FINISHED) {
				switch (commonResult.getDetectionType()) {
					case MRZ: {
						MRZScanResult result = (MRZScanResult) commonResult;
						if (result.getData() != null) {
							MRZData data = result.getData();
							content.removeAllViews();
							content.addView(childView("Name:", data.getFirstName() + " " + data.getLastName()));
							content.addView(childView("Sex:", data.getSex() == null ? ""
									: data.getSex().substring(0, 1).toUpperCase() + data.getSex().substring(1)));
							content.addView(childView("Age:", data.getAge() + ""));
							content.addView(childView("Document Type:", data.getDocumentType()));
							content.addView(childView("Document Number:", data.getDocumentNumber()));
							content.addView(childView("Issuing State:", data.getIssuingState()));
							content.addView(childView("Nationality:", data.getNationality()));
							content.addView(childView("Date of Birth(YYYY-MM-DD):", data.getDateOfBirth()));
							content.addView(childView("Date of Expiry(YYYY-MM-DD):", data.getDateOfExpire()));
						}
					}
					break;
					case VIN: {
						VINScanResult result = (VINScanResult) commonResult;
						if (result.getData() != null) {
							VINData data = result.getData();
							content.removeAllViews();
							content.addView(childView("VIN:", data.vinString));
							content.addView(childView("WMI:", data.wmi));
							content.addView(childView("Region:", data.region));
							content.addView(childView("VDS:", data.vds));
							content.addView(childView("Check Digit:", data.checkDigit));
							content.addView(childView("Model Year:", data.modelYear));
							content.addView(childView("Plant Code:", data.plantCode));
							content.addView(childView("Serial Number:", data.serialNumber));
						}
					}
					break;
				}


			} else if (commonResult.getResultStatus() == CommonResult.EnumResultStatus.RS_CANCELED) {
				content.removeAllViews();
				content.addView(childView("Scan canceled.", ""));
			}
			if (commonResult.getErrorString() != null && !commonResult.getErrorString().isEmpty()) {
				content.removeAllViews();
				content.addView(childView("Error:", commonResult.getErrorString()));
			}
		});

		findViewById(R.id.btn_camera_scan).setOnClickListener(v -> {
			config.setScanMode(EnumScanMode.CAMERA);
			launcher.launch(config);
		});

		findViewById(R.id.btn_picture_scan).setOnClickListener(v -> {
			config.setScanMode(EnumScanMode.PICTURE);
			launcher.launch(config);
		});

		radioGroup = findViewById(R.id.radio_group_mode);

		radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
			if (checkedId == R.id.radio_mrz) {
				config.setDetectionType(EnumDetectionType.MRZ);
			} else if (checkedId == R.id.radio_vin) {
				config.setDetectionType(EnumDetectionType.VIN);
			}
		});
	}

	@NonNull
	private View childView(String label, String labelText) {
		LinearLayout layout = new LinearLayout(this);
		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.WRAP_CONTENT);
		params.setMargins(0, 30, 0, 0);
		layout.setLayoutParams(params);
		layout.setOrientation(LinearLayout.VERTICAL);
		TextView labelView = new TextView(this);
		labelView.setPadding(0, 30, 0, 0);
		labelView.setTextColor(ContextCompat.getColor(this, R.color.dy_grey_AA));
		labelView.setTextSize(16);
		labelView.setText(label);
		TextView textView = new TextView(this);
		textView.setTextSize(16);
		textView.setText(labelText);
		layout.addView(labelView);
		layout.addView(textView);
		return layout;
	}
}