package com.dynamsoft.mrzscanner;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import com.dynamsoft.core.basic_structures.CompletionListener;
import com.dynamsoft.core.basic_structures.EnumCapturedResultItemType;
import com.dynamsoft.cvr.CaptureVisionRouter;
import com.dynamsoft.cvr.CaptureVisionRouterException;
import com.dynamsoft.cvr.CapturedResultReceiver;
import com.dynamsoft.dce.CameraEnhancer;
import com.dynamsoft.dce.CameraEnhancerException;
import com.dynamsoft.dce.CameraView;
import com.dynamsoft.dce.DrawingLayer;
import com.dynamsoft.dce.EnumEnhancerFeatures;
import com.dynamsoft.dce.Feedback;
import com.dynamsoft.dce.utils.PermissionUtil;
import com.dynamsoft.dcp.ParsedResult;
import com.dynamsoft.dcp.ParsedResultItem;
import com.dynamsoft.dlr.RecognizedTextLinesResult;
import com.dynamsoft.dlr.TextLineResultItem;
import com.dynamsoft.license.LicenseManager;
import com.dynamsoft.utility.MultiFrameResultCrossFilter;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

public class MainActivity extends AppCompatActivity {
	private CameraEnhancer mCamera;
	private CameraView mCameraView;
	private CaptureVisionRouter mRouter;
	private String mText;
	private AlertDialog mAlertDialog;
	private boolean succeed = false;
	private boolean mBeepStatus;
	private TextView mTextResult;
	private int mBirthYear;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_scan);
		PermissionUtil.requestCameraPermission(this);
        // Initialize the license.
        // The license string here is a trial license. Note that network connection is required for this license to work.
        // You can request an extension via the following link: https://www.dynamsoft.com/customer/license/trialLicense?product=cvs&utm_source=samples&package=android
		LicenseManager.initLicense("DLS2eyJoYW5kc2hha2VDb2RlIjoiMjAwMDAxLTE2NDk4Mjk3OTI2MzUiLCJvcmdhbml6YXRpb25JRCI6IjIwMDAwMSIsInNlc3Npb25QYXNzd29yZCI6IndTcGR6Vm05WDJrcEQ5YUoifQ==",
				this,
				(isSuccess, error) -> {
					if (!isSuccess) {
						runOnUiThread(() -> {
							((TextView)findViewById(R.id.tv_message))
									.setText("License initialization failed: "+error.getMessage());
						});
						error.printStackTrace();
					}
				});
		mCameraView = findViewById(R.id.dce_camera_view);
		mTextResult = findViewById(R.id.tv_result);
		mCamera = new CameraEnhancer(mCameraView, this);

		try {
			mCamera.enableEnhancedFeatures(EnumEnhancerFeatures.EF_FRAME_FILTER);
		} catch (CameraEnhancerException e) {
			throw new RuntimeException(e);
		}

		mRouter = new CaptureVisionRouter(this);
		MultiFrameResultCrossFilter filter = new MultiFrameResultCrossFilter();
		filter.enableResultCrossVerification(EnumCapturedResultItemType.CRIT_TEXT_LINE, true);
		mRouter.addResultFilter(filter);
		try {
			mRouter.initSettingsFromFile("MRZScanner.json");
			mRouter.setInput(mCamera);
		} catch (CaptureVisionRouterException e) {
			throw new RuntimeException(e);
		}

		mRouter.addResultReceiver(new CapturedResultReceiver() {
			@Override
			// Implement this method to receive RecognizedTextLinesResult.
			public void onRecognizedTextLinesReceived(@NonNull RecognizedTextLinesResult result) {
				onLabelTextReceived(result);
			}

			@Override
			public void onParsedResultsReceived(@NonNull ParsedResult result) {
				if (!succeed) {
					onParsedResultReceived(result);
				}
			}
		});
	}

	private void saveBeepStatus() {
		SharedPreferences sp = getSharedPreferences("beep", Context.MODE_PRIVATE);
		SharedPreferences.Editor editor=sp.edit();
		editor.putBoolean("status", mBeepStatus);
		editor.apply();
	}

	@Override
	protected void onResume() {
		super.onResume();
		try {
			mCamera.open();
		} catch (CameraEnhancerException e) {
			e.printStackTrace();
		}
		mRouter.startCapturing("ReadMRZ", new CompletionListener() {
			@Override
			public void onSuccess() {
			}

			@Override
			public void onFailure(int errorCode, String errorString) {
				runOnUiThread(() -> showDialog("Error", String.format(Locale.getDefault(),
						"ErrorCode: %d %nErrorMessage: %s", errorCode, errorString)));
			}
		});
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
		mCameraView.getDrawingLayer(DrawingLayer.DLR_LAYER_ID).clearDrawingItems();
		super.onStop();
	}

	private void onLabelTextReceived(RecognizedTextLinesResult result) {
		if (result.getItems() == null) {
			return;
		}
		TextLineResultItem[] results = result.getItems();
		StringBuilder resultBuilder = new StringBuilder();
		if (results != null) {
			for (TextLineResultItem item : results) {
				resultBuilder.append(item.getText()).append("\n\n");
			}
		}
		mText = resultBuilder.toString();
	}

	private void onParsedResultReceived(ParsedResult result) {
		if (result.getItems() == null) {
			return;
		}
		if (result.getItems().length == 0) {
			runOnUiThread(() -> {
				if (!mText.isEmpty()) {
					String errorMsg = "error: Failed to parse the content. The MRZ text is " + mText;
					mTextResult.setText(errorMsg);
				}
			});
		} else {
			HashMap<String, String> labelMap = assembleMap(result.getItems()[0]);
			if (labelMap != null && !labelMap.isEmpty()) {
				succeed = true;
				Intent intent = new Intent(this, ResultActivity.class);
				intent.putExtra("labelMap", labelMap);
				startActivity(intent);
				runOnUiThread(() -> {
					mTextResult.setText("");
				});

			} else {
				runOnUiThread(() -> {
					if (!mText.isEmpty()) {
						String errorMsg = "error: Failed to parse the content. The MRZ text is " + mText;
						mTextResult.setText(errorMsg);
					}
				});
			}

		}
	}

	private HashMap<String, String> assembleMap(ParsedResultItem item) {
		HashMap<String, String> entry = item.getParsedFields();
		String mDocumentType = "";
		if (item.getCodeType().equals("MRTD_TD1_ID") || item.getCodeType().equals("MRTD_TD2_ID") || item.getCodeType().equals("MRTD_TD2_FRENCH_ID"))
		{
			mDocumentType = "ID";
		}else if(item.getCodeType().equals("MRTD_TD2_VISA") || item.getCodeType().equals("MRTD_TD3_VISA"))
		{
			mDocumentType = "VISA";
		}else
		{
			mDocumentType = "PASSPORT";
		}

		String number = entry.get("passportNumber") == null ? entry.get("documentNumber") == null
				? entry.get("idNumber") == null ? "" : entry.get("idNumber") : entry.get("documentNumber") : entry.get("passportNumber");

		String mFirstName = entry.get("secondaryIdentifier") == null ? entry.get("givenNames") == null ? "" : ", " + entry.get("givenNames") : ", " + entry.get("secondaryIdentifier");

		String mLastName = entry.get("primaryIdentifier") == null ? entry.get("lastName") == null ? "" : entry.get("lastName") : entry.get("primaryIdentifier");

		String mName = mLastName + mFirstName;

		String mNationality = entry.get("nationality") == null ? "France" : entry.get("nationality");

		int age = -1;
		int expiryYear = 0;
		try {
			int year = Integer.parseInt(entry.get("birthYear"));
			int month = Integer.parseInt(entry.get("birthMonth"));
			int day = Integer.parseInt(entry.get("birthDay"));
			expiryYear = Integer.parseInt(entry.get("expiryYear")) + 2000;
			age = calculateAge(year, month, day);
		} catch (Exception e) {
			e.printStackTrace();
		}
		HashMap<String, String> properties = new HashMap<>(9);
		properties.put("Document Type", mDocumentType);
		properties.put("Name", mName);
		properties.put("Sex", entry.get("sex"));
		properties.put("Age", age == -1 ? "Unknown" : age + "");
		properties.put("Document Number", number);
		properties.put("Issuing State", entry.get("issuingState"));
		properties.put("Nationality", mNationality);
		properties.put("Date of Birth(YYYY-MM-DD)", mBirthYear + "-" +
				entry.get("birthMonth") + "-" + entry.get("birthDay"));
		properties.put("Date of Expiry(YYYY-MM-DD)", expiryYear + "-" +
				entry.get("expiryMonth") + "-" + entry.get("expiryDay"));
		return properties;
	}

	private int calculateAge(int year, int month, int day) {
		Calendar calendar = Calendar.getInstance();
		int cYear = calendar.get(Calendar.YEAR);
		int cMonth = calendar.get(Calendar.MONTH) + 1;
		int cDay = calendar.get(Calendar.DAY_OF_MONTH);
		mBirthYear = 1900 + year;
		int diffYear = cYear - mBirthYear;
		int diffMonth = cMonth - month;
		int diffDay = cDay - day;
		int age = minusYear(diffYear, diffMonth, diffDay);
		if (age > 100) {
			mBirthYear = 2000 + year;
			diffYear = cYear - mBirthYear;
			age = minusYear(diffYear, diffMonth, diffDay);
		} else if (age < 0) {
			age = 0;
		}
		return age;
	}

	private int minusYear(int diffYear, int diffMonth, int diffDay) {
		int age = Math.max(diffYear, 0);
		if (diffMonth < 0) {
			age = age - 1;

		} else if (diffMonth == 0) {
			if (diffDay < 0) {
				age = age - 1;
			}
		}
		return age;
	}

	private void showDialog(String title, String message) {
		if (mAlertDialog == null) {
			mAlertDialog = new AlertDialog.Builder(this)
					.setCancelable(true)
					.setPositiveButton("OK", null)
					.create();
		}
		mAlertDialog.setTitle(title);
		mAlertDialog.setMessage(message);
		mAlertDialog.show();
	}
}
