package com.dynamsoft.mrzscannerbundle.ui;

import android.content.Intent;

import java.util.Calendar;
import java.util.HashMap;

import androidx.annotation.IntDef;

import static com.dynamsoft.mrzscannerbundle.ui.MRZScanResult.EnumResultStatus.RS_CANCELED;
import static com.dynamsoft.mrzscannerbundle.ui.MRZScanResult.EnumResultStatus.RS_EXCEPTION;
import static com.dynamsoft.mrzscannerbundle.ui.MRZScanResult.EnumResultStatus.RS_FINISHED;
import static com.dynamsoft.mrzscannerbundle.ui.MRZScannerActivity.EXTRA_DOC_TYPE;
import static com.dynamsoft.mrzscannerbundle.ui.MRZScannerActivity.EXTRA_ERROR_CODE;
import static com.dynamsoft.mrzscannerbundle.ui.MRZScannerActivity.EXTRA_ERROR_STRING;
import static com.dynamsoft.mrzscannerbundle.ui.MRZScannerActivity.EXTRA_ISSUING_STATE;
import static com.dynamsoft.mrzscannerbundle.ui.MRZScannerActivity.EXTRA_NATIONALITY;
import static com.dynamsoft.mrzscannerbundle.ui.MRZScannerActivity.EXTRA_NUMBER;
import static com.dynamsoft.mrzscannerbundle.ui.MRZScannerActivity.EXTRA_RESULT;
import static com.dynamsoft.mrzscannerbundle.ui.MRZScannerActivity.EXTRA_STATUS_CODE;

/**
 * @author: dynamsoft
 * Time: 2024/12/2
 * Description:
 */
public final class MRZScanResult {
	private int mBirthYear;
	private String docType;
	private String firstName;
	private String lastName;
	private String sex;
	private String issuingState;
	private String nationality;
	private String dateOfBirth;
	private String dateOfExpire;
	private String documentNumber;
	private int age;
	private String mrzText = "";
	@EnumResultStatus
	private int resultStatus;
	private int errorCode;
	private String errorString;
	private MRZData mrzData;

	@IntDef(value = {RS_FINISHED, RS_CANCELED, RS_EXCEPTION})
	public @interface EnumResultStatus {
		int RS_FINISHED = 0;
		int RS_CANCELED = 1;
		int RS_EXCEPTION = 2;
	}

	public MRZScanResult(int resultCode, Intent data) {
		if (data != null) {
			docType = data.getStringExtra(EXTRA_DOC_TYPE);
			resultStatus = data.getIntExtra(EXTRA_STATUS_CODE, 0);
			errorCode = data.getIntExtra(EXTRA_ERROR_CODE, 0);
			errorString = data.getStringExtra(EXTRA_ERROR_STRING);
			nationality = data.getStringExtra(EXTRA_NATIONALITY);
			issuingState = data.getStringExtra(EXTRA_ISSUING_STATE);
			documentNumber = data.getStringExtra(EXTRA_NUMBER);
			assembleMap((HashMap<String, String>) data.getSerializableExtra(EXTRA_RESULT));
			mrzData = new MRZData(firstName, lastName, sex, issuingState, nationality, dateOfBirth, dateOfExpire,
					documentNumber, age, mrzText, docType);
		}
	}

	public MRZData getData() {
		return mrzData;
	}

	@EnumResultStatus
	public int getResultStatus() {
		return resultStatus;
	}

	public int getErrorCode() {
		return errorCode;
	}

	public String getErrorString() {
		return errorString;
	}


	private void assembleMap(HashMap<String, String> entry) {
		// Parsed fields are stored in a HashMap with field name as the key and field value as the value.
		// The following code shows how to get the parsed field values.
		if (entry == null) {
			return;
		}
		firstName = entry.get("secondaryIdentifier") == null ? "" : entry.get("secondaryIdentifier");
		lastName = entry.get("primaryIdentifier") == null ? "" : " " + entry.get("primaryIdentifier");

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
		sex = entry.get("sex");
		dateOfBirth = mBirthYear + "-" + entry.get("birthMonth") + "-" + entry.get("birthDay");
		dateOfExpire = expiryYear + "-" + entry.get("expiryMonth") + "-" + entry.get("expiryDay");
		String line1 = entry.get("line1");
		String line2 = entry.get("line2");
		String line3 = entry.get("line3");
		if (line1 != null) {
			mrzText += line1 + "\n";
		}
		if (line2 != null) {
			mrzText += line2 + "\n";
		}
		if (line3 != null) {
			mrzText += line3;
		}
	}

	// Age information is not directly obtained from the MRZ but you can calculate it based on the date of birth.
	// The following 2 methods are used to calculate the age.
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
}
