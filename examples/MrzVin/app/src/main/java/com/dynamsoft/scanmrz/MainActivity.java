package com.dynamsoft.scanmrz;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
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

import java.util.HashMap;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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

		findViewById(R.id.btn_camerax_scan).setOnClickListener(v -> {
			launchCameraXActivity();
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

	private void launchCameraXActivity() {
		Intent intent = new Intent(this, CameraXActivity.class);
		intent.putExtra("scanner_config", config);
		cameraXLauncher.launch(intent);
	}

	private void displayFaceImage(String faceImageBase64) {
		try {
			// Decode Base64 string to bitmap
			byte[] decodedBytes = Base64.decode(faceImageBase64, Base64.DEFAULT);
			Bitmap faceBitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);

			if (faceBitmap != null) {
				// Create a new ImageView since content.removeAllViews() removed the original one
				ImageView faceImageView = new ImageView(this);
				faceImageView.setId(R.id.iv_face);

				// Set layout parameters to match the original ImageView
				LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
					(int) (120 * getResources().getDisplayMetrics().density), // 120dp to pixels
					(int) (120 * getResources().getDisplayMetrics().density)  // 120dp to pixels
				);
				params.gravity = android.view.Gravity.CENTER_HORIZONTAL;
				params.setMargins(0, 0, 0, (int) (16 * getResources().getDisplayMetrics().density)); // 16dp bottom margin

				faceImageView.setLayoutParams(params);
				faceImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
				faceImageView.setBackground(ContextCompat.getDrawable(this, android.R.drawable.gallery_thumb));
				faceImageView.setImageBitmap(faceBitmap);

				// Add the ImageView at the beginning of the content layout
				content.addView(faceImageView, 0);
			}
		} catch (Exception e) {
			// If face image display fails, just continue without showing it
		}
	}

	private final ActivityResultLauncher<Intent> cameraXLauncher = registerForActivityResult(
			new ActivityResultContracts.StartActivityForResult(),
			result -> {
				if (result.getResultCode() == RESULT_OK && result.getData() != null) {
					Intent data = result.getData();
					int statusCode = data.getIntExtra("status_code", 0);

					if (statusCode == 1) { // Success
						String docType = data.getStringExtra("doc_type");
						HashMap<String, String> resultData = (HashMap<String, String>) data.getSerializableExtra("result");

						content.removeAllViews();

						if ("VIN".equals(docType)) {
							// Handle VIN results
							content.addView(childView("VIN:", resultData.get("vinString")));
							content.addView(childView("WMI:", resultData.get("wmi")));
							content.addView(childView("Region:", resultData.get("region")));
							content.addView(childView("VDS:", resultData.get("vds")));
							content.addView(childView("Check Digit:", resultData.get("checkDigit")));
							content.addView(childView("Model Year:", resultData.get("modelYear")));
							content.addView(childView("Plant Code:", resultData.get("plantCode")));
							content.addView(childView("Serial Number:", resultData.get("serialNumber")));
						} else {
							// Handle MRZ results
							String firstName = resultData.get("firstName") != null ? resultData.get("firstName") : "";
							String lastName = resultData.get("lastName") != null ? resultData.get("lastName") : "";
							String sex = resultData.get("sex");
							String age = resultData.get("age");
							String documentType = resultData.get("documentType");
							String documentNumber = data.getStringExtra("number");
							String issuingState = data.getStringExtra("issuing_state");
							String nationality = data.getStringExtra("nationality");
							String dateOfBirth = resultData.get("dateOfBirth");
							String dateOfExpiry = resultData.get("dateOfExpiry");

							// Check if face image is available and display it
							String faceImageBase64 = data.getStringExtra("face_image");
							if (faceImageBase64 != null && !faceImageBase64.isEmpty()) {
								displayFaceImage(faceImageBase64);
							}

							content.addView(childView("Name:", firstName + " " + lastName));
							content.addView(childView("Sex:", sex != null && !sex.isEmpty() ?
									sex.substring(0, 1).toUpperCase() + sex.substring(1) : ""));
							content.addView(childView("Age:", age != null ? age : ""));
							content.addView(childView("Document Type:", documentType != null ? documentType : ""));
							content.addView(childView("Document Number:", documentNumber != null ? documentNumber : ""));
							content.addView(childView("Issuing State:", issuingState != null ? issuingState : ""));
							content.addView(childView("Nationality:", nationality != null ? nationality : ""));
							content.addView(childView("Date of Birth(YYYY-MM-DD):", dateOfBirth != null ? dateOfBirth : ""));
							content.addView(childView("Date of Expiry(YYYY-MM-DD):", dateOfExpiry != null ? dateOfExpiry : ""));
						}
					}
				} else {
					content.removeAllViews();
					content.addView(childView("CameraX scan canceled.", ""));
				}
			});
}