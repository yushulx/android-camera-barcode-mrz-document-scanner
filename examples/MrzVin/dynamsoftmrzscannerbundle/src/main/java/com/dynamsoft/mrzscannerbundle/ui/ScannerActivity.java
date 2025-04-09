package com.dynamsoft.mrzscannerbundle.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;

import com.dynamsoft.core.basic_structures.CompletionListener;
import com.dynamsoft.core.basic_structures.DSRect;
import com.dynamsoft.core.basic_structures.EnumCapturedResultItemType;
import com.dynamsoft.cvr.CaptureVisionRouter;
import com.dynamsoft.cvr.CaptureVisionRouterException;
import com.dynamsoft.cvr.CapturedResultReceiver;
import com.dynamsoft.dce.CameraEnhancer;
import com.dynamsoft.dce.CameraEnhancerException;
import com.dynamsoft.dce.CameraView;
import com.dynamsoft.dce.DrawingLayer;
import com.dynamsoft.dce.EnumCameraPosition;
import com.dynamsoft.dce.EnumEnhancerFeatures;
import com.dynamsoft.dce.Feedback;
import com.dynamsoft.dce.utils.PermissionUtil;
import com.dynamsoft.dcp.EnumValidationStatus;
import com.dynamsoft.dcp.ParsedResult;
import com.dynamsoft.dcp.ParsedResultItem;
import com.dynamsoft.dlr.RecognizedTextLinesResult;
import com.dynamsoft.license.LicenseManager;
import com.dynamsoft.mrzscannerbundle.R;
import com.dynamsoft.utility.MultiFrameResultCrossFilter;

import java.util.HashMap;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class ScannerActivity extends AppCompatActivity {
	public final static String EXTRA_SCANNER_CONFIG = "scanner_config";
	public final static String EXTRA_STATUS_CODE = "extra_status_code";
	public final static String EXTRA_ERROR_CODE = "extra_error_code";
	public final static String EXTRA_ERROR_STRING = "extra_error_string";
	public final static String EXTRA_RESULT = "extra_result";
	public final static String EXTRA_DOC_TYPE = "extra_doc_type";
	public final static String EXTRA_NATIONALITY = "extra_nationality";
	public final static String EXTRA_ISSUING_STATE = "extra_issuing_state";
	public final static String EXTRA_NUMBER = "extra_number";
	private CameraEnhancer mCamera;
	private CameraView mCameraView;
	private Button btnToggle;
	private Button btnTorch;
	private CaptureVisionRouter mRouter;
	private boolean succeed = false;
	private String mCurrentTemplate = "ReadPassportAndId";
	private ScannerConfig configuration;
	private String number;
	private boolean isTorchOn;
	private boolean useBackCamera = true;
	private boolean enableCapture = false;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_scanner);

		findViewById(R.id.btn_capture).setOnClickListener(v -> {
			enableCapture = true;
		});

		PermissionUtil.requestCameraPermission(this);

		Intent requestIntent = getIntent();
		if (requestIntent != null) {
			configuration = (ScannerConfig) requestIntent.getSerializableExtra(EXTRA_SCANNER_CONFIG);
		}

		if (configuration.getLicense() != null) {
			LicenseManager.initLicense(configuration.getLicense(), this, (isSuccess, error) -> {
				if (!isSuccess) {
					error.printStackTrace();
				}
			});
		}
		btnToggle = findViewById(R.id.btn_toggle);
		btnTorch = findViewById(R.id.btn_torch);

		boolean isCloseButtonVisible = configuration.isCloseButtonVisible();
		ImageView closeButton = findViewById(R.id.iv_back);
		closeButton.setVisibility(isCloseButtonVisible ? View.VISIBLE : View.GONE);

		boolean isGuideFrameVisible = configuration.isGuideFrameVisible();
		ImageView guideFrame = findViewById(R.id.iv_guide_frame);

		mCameraView = findViewById(R.id.dce_camera_view);

		findViewById(R.id.iv_back).setOnClickListener((v) -> {
			resultOK(CommonResult.EnumResultStatus.RS_CANCELED, null);
			finish();
		});

		// CameraEnhancer is the class for controlling the camera and obtaining high-quality video input.
		mCamera = new CameraEnhancer(mCameraView, this);

		switch (configuration.getDetectionType()) {
			case MRZ:
			{
				guideFrame.setVisibility(isGuideFrameVisible ? View.VISIBLE : View.GONE);
				// Enable the frame filter feature. It will improve the accuracy of the MRZ scanning.
				try {
					mCamera.enableEnhancedFeatures(EnumEnhancerFeatures.EF_FRAME_FILTER);
				} catch (CameraEnhancerException e) {
					throw new RuntimeException(e);
				}
			}
				break;
			case VIN: {
				guideFrame.setVisibility(View.GONE);
				try {
					mCamera.setScanRegion(new DSRect(0.1f, 0.4f, 0.9f, 0.6f, true));
				} catch (CameraEnhancerException e) {
					e.printStackTrace();
				}
			}
				break;
			default:
				break;
		}
	}

	private void configCVR() {
		mRouter = new CaptureVisionRouter(this);
		try {
			if (configuration.getTemplateFile() != null && !configuration.getTemplateFile().isEmpty()) {
				String template = configuration.getTemplateFile();
				mCurrentTemplate = "";
				if (template.startsWith("{") || template.startsWith("[")) {
					mRouter.initSettings(template);
				} else {
					mRouter.initSettingsFromFile(template);
				}
			} else if (configuration.getTemplateFilePath() != null && !configuration.getTemplateFilePath().isEmpty()) {
				mCurrentTemplate = "";
				mRouter.initSettingsFromFile(configuration.getTemplateFilePath());
			} else {
				switch (configuration.getDetectionType()) {
					case MRZ:
					{
						if (configuration.getDocumentType() != null) {
							switch (configuration.getDocumentType()) {
								case DT_ALL:
									mCurrentTemplate = "ReadPassportAndId";
									break;
								case DT_ID:
									mCurrentTemplate = "ReadId";
									break;
								case DT_PASSPORT:
									mCurrentTemplate = "ReadPassport";
									break;
							}

						}
					}
						break;
					case VIN:
						mCurrentTemplate = "ReadVINText";
						break;
				}
			}
		} catch (CaptureVisionRouterException e) {
			e.printStackTrace();
			resultError(e.getErrorCode(), e.getMessage());
			finish();
		}
	}

	// The implementation of restartCapture().
	private void restartCapture(String template) {
		mRouter.startCapturing(template, new CompletionListener() {
			@Override
			public void onSuccess() {
				runOnUiThread(() -> {
					initTorchButton();
					initToggleButton();
				});
			}

			@Override
			public void onFailure(int errorCode, String errorString) {
				runOnUiThread(() -> {
					resultError(errorCode, errorString);
					finish();
				});
			}
		});
	}

	private void turnOnTorch() {
		try {
			mCamera.turnOnTorch();
			btnTorch.setBackground(ContextCompat.getDrawable(this, R.drawable.icon_flash_on));
		} catch (CameraEnhancerException e) {
			e.printStackTrace();
		}
	}

	private void turnOffTorch() {
		try {
			mCamera.turnOffTorch();
			btnTorch.setBackground(ContextCompat.getDrawable(this, R.drawable.icon_flash_off));
		} catch (CameraEnhancerException e) {
			e.printStackTrace();
		}
	}

	private void initTorchButton() {
		btnTorch.setVisibility(configuration.isTorchButtonVisible() ? View.VISIBLE : View.GONE);
		btnTorch.setOnClickListener(v -> {
			isTorchOn = !isTorchOn;
			if (isTorchOn) {
				turnOnTorch();
			} else {
				turnOffTorch();
			}
		});
	}

	private void resetToggleButton(int margin) {
		ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) btnToggle.getLayoutParams();
		params.setMarginStart(margin);
		btnToggle.setLayoutParams(params);
	}

	public int dpToPx(int dp) {
		float density = getResources().getDisplayMetrics().density;
		return Math.round(dp * density);
	}

	private void initToggleButton() {
		btnToggle.setVisibility(configuration.isCameraToggleButtonVisible() ? View.VISIBLE : View.GONE);
		if (!configuration.isTorchButtonVisible() && configuration.isCameraToggleButtonVisible()) {
			resetToggleButton(0);
		}
		btnToggle.setOnClickListener(v -> {
			try {
				useBackCamera = !useBackCamera;
				mCamera.selectCamera(useBackCamera ? EnumCameraPosition.CP_BACK : EnumCameraPosition.CP_FRONT);
				if (configuration.isTorchButtonVisible()) {
					btnTorch.setVisibility(useBackCamera ? View.VISIBLE : View.GONE);
					resetToggleButton(useBackCamera ? dpToPx(50) : 0);
					if (!useBackCamera) {
						isTorchOn = false;
						turnOffTorch();
					}
				}
			} catch (CameraEnhancerException e) {
				e.printStackTrace();
			}
		});
	}

	@Override
	protected void onResume() {
		super.onResume();
		configCVR();

		// Enable the multi-frame cross verification feature. It will improve the accuracy of the MRZ scanning.
		MultiFrameResultCrossFilter filter = new MultiFrameResultCrossFilter();
		filter.enableResultCrossVerification(EnumCapturedResultItemType.CRIT_TEXT_LINE, true);
		mRouter.addResultFilter(filter);

		try {
			// Set the input.
			mRouter.setInput(mCamera);
		} catch (CaptureVisionRouterException e) {
			throw new RuntimeException(e);
		}


		mRouter.addResultReceiver(new CapturedResultReceiver() {

			// Implement this method to receive raw MRZ recognized results. It includes the string only.
			@Override
			public void onRecognizedTextLinesReceived(@NonNull RecognizedTextLinesResult result) {
			}

			// Implement this method to receive parsed MRZ results. It includes the detailed information.
			@Override
			public void onParsedResultsReceived(@NonNull ParsedResult result) {
				if (!succeed) {
					onParsedResultReceived(result);
				}
			}
		});
		try {
			mCamera.open();
		} catch (CameraEnhancerException e) {
			e.printStackTrace();
		}
		restartCapture(mCurrentTemplate);

	}

	@Override
	protected void onPause() {
		super.onPause();
		succeed = false;
		try {
			mCamera.close();

		} catch (CameraEnhancerException e) {
			e.printStackTrace();
		}
		mRouter.stopCapturing();
	}

	@Override
	protected void onStop() {
		// DrawingItem in this sample is the green quadrilateral that highlights the recognized text.
		// Clear the DrawingItem before you leave the camera page.
		mCameraView.getDrawingLayer(DrawingLayer.DLR_LAYER_ID).clearDrawingItems();
		super.onStop();
	}

	@Override
	public void onBackPressed() {
		resultOK(CommonResult.EnumResultStatus.RS_CANCELED, null);
		super.onBackPressed();
	}

	private void onParsedResultReceived(ParsedResult result) {
		if (result.getItems() == null || !enableCapture) {
			return;
		}

		if (result.getItems().length != 0) {
			ParsedResultItem item = result.getItems()[0];
			if (item.getCodeType().equals("VIN") || formerFilter(item)) {
				if (configuration.isBeepEnabled()) {
					Feedback.beep(this);
				}
				succeed = true;
				resultOK(CommonResult.EnumResultStatus.RS_FINISHED, item);
				finish();
			}
		}
	}

	private boolean formerFilter(ParsedResultItem item) {
		HashMap<String, String> entry = item.getParsedFields();
		String codeType = item.getCodeType();

		number = entry.get("passportNumber") == null ? entry.get("documentNumber") == null
				? entry.get("longDocumentNumber") == null ? "" : entry.get("longDocumentNumber") :
				entry.get("documentNumber") : entry.get("passportNumber");

		return number != null &&
				entry.get("sex") != null &&
				entry.get("issuingState") != null &&
				entry.get("nationality") != null &&
				entry.get("dateOfBirth") != null &&
				entry.get("dateOfExpiry") != null && getValid(item);
	}

	private void resultOK(int statusCode, ParsedResultItem item) {
		Intent intent = new Intent();
		intent.putExtra(EXTRA_STATUS_CODE, statusCode);
		if (item != null) {
			intent.putExtra(EXTRA_DOC_TYPE, item.getCodeType());
			if (!item.getCodeType().equals("VIN")) {
				intent.putExtra(EXTRA_NATIONALITY, item.getFieldRawValue("nationality"));
				intent.putExtra(EXTRA_ISSUING_STATE, item.getFieldRawValue("issuingState"));
				intent.putExtra(EXTRA_NUMBER, number);
			}

			intent.putExtra(EXTRA_RESULT, item.getParsedFields());
		}
		setResult(RESULT_OK, intent);
	}

	private boolean getValid(ParsedResultItem item) {
		boolean isValid;
		if (item.getCodeType().equals("MRTD_TD1_ID")) {
			isValid = item.getFieldValidationStatus("line1") != EnumValidationStatus.VS_FAILED
					&& item.getFieldValidationStatus("line2") != EnumValidationStatus.VS_FAILED
					&& item.getFieldValidationStatus("line3") != EnumValidationStatus.VS_FAILED;
		} else {
			isValid = item.getFieldValidationStatus("line1") != EnumValidationStatus.VS_FAILED
					&& item.getFieldValidationStatus("line2") != EnumValidationStatus.VS_FAILED;
		}
		return isValid;
	}

	private void resultError(int errorCode, String errorString) {
		Intent intent = new Intent();
		intent.putExtra(EXTRA_STATUS_CODE, CommonResult.EnumResultStatus.RS_EXCEPTION);
		intent.putExtra(EXTRA_ERROR_CODE, errorCode);
		intent.putExtra(EXTRA_ERROR_STRING, errorString);
		setResult(RESULT_OK, intent);
	}

	public static final class ResultContract extends ActivityResultContract<ScannerConfig, CommonResult> {
		private EnumDetectionType detectionType;
		private ScannerConfig config;

		@NonNull
		@Override
		public Intent createIntent(@NonNull Context context, ScannerConfig scannerConfig) {
			Intent intent = new Intent(context, ScannerActivity.class);
			intent.putExtra(ScannerActivity.EXTRA_SCANNER_CONFIG, scannerConfig);
			config = scannerConfig;
			return intent;
		}

		@Override
		public CommonResult parseResult(int i, @Nullable Intent intent) {
			switch (config.getDetectionType()) {
				case MRZ: {
					return new MRZScanResult(i, intent, config.getDetectionType());
				}

				case VIN:
				{
					return new VINScanResult(i, intent, config.getDetectionType());
				}

			}

			return null;
		}
	}
}
