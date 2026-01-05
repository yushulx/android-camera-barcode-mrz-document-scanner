package com.test.mrzscanner;

import android.util.Log;

import com.dynamsoft.dcp.ParsedResultItem;
import com.dynamsoft.license.LicenseManager;
import com.dynamsoft.license.LicenseVerificationListener;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class MrzUtils {
    private static final String TAG = "MrzUtils";
    private static final String LICENSE_KEY = "DLS2eyJoYW5kc2hha2VDb2RlIjoiMjAwMDAxLTE2NDk4Mjk3OTI2MzUiLCJvcmdhbml6YXRpb25JRCI6IjIwMDAwMSIsInNlc3Npb25QYXNzd29yZCI6IndTcGR6Vm05WDJrcEQ5YUoifQ==";

    public static void initLicense() {
        LicenseManager.initLicense(LICENSE_KEY, (isSuccess, error) -> {
            if (!isSuccess) {
                Log.e(TAG, "License initialization failed: " + error.getMessage());
            }
        });
    }

    public static HashMap<String, String> parseDynamsoftResult(ParsedResultItem item) {
        HashMap<String, String> entry = item.getParsedFields();
        HashMap<String, String> properties = new HashMap<>();

        // Determine document type
        String codeType = item.getCodeType();
        String docType = "PASSPORT";
        if (codeType.contains("TD1") || codeType.contains("ID")) {
            docType = "ID";
        } else if (codeType.contains("VISA")) {
            docType = "VISA";
        }

        // Extract fields
        String number = getFirstNonNull(entry, "passportNumber", "documentNumber", "idNumber");
        String firstName = getFirstNonNull(entry, "secondaryIdentifier", "givenNames");
        String lastName = getFirstNonNull(entry, "primaryIdentifier", "lastName");
        String nationality = entry.get("nationality") != null ? entry.get("nationality") : "Unknown";
        String issuingState = entry.get("issuingState") != null ? entry.get("issuingState") : "Unknown";
        String sex = entry.get("sex") != null ? entry.get("sex") : "Unknown";

        // Format Name
        String fullName = lastName;
        if (firstName != null && !firstName.isEmpty()) {
            if (fullName != null && !fullName.isEmpty()) fullName += ", ";
            fullName += firstName;
        }
        if (fullName == null || fullName.isEmpty()) fullName = "—";

        // Calculate age
        int age = -1;
        try {
            String birthYearStr = entry.get("birthYear");
            String birthMonthStr = entry.get("birthMonth");
            String birthDayStr = entry.get("birthDay");
            
            if (birthYearStr != null && birthMonthStr != null && birthDayStr != null) {
                int year = Integer.parseInt(birthYearStr);
                int month = Integer.parseInt(birthMonthStr);
                int day = Integer.parseInt(birthDayStr);
                age = calculateAge(year, month, day);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error calculating age", e);
        }

        // Format Dates
        String birthDate = formatDate(entry.get("birthYear"), entry.get("birthMonth"), entry.get("birthDay"));
        String expiryDate = formatDate(entry.get("expiryYear"), entry.get("expiryMonth"), entry.get("expiryDay"));

        properties.put("Document Type", docType);
        properties.put("Name", fullName);
        properties.put("Sex", formatSex(sex));
        properties.put("Age", age >= 0 ? String.valueOf(age) : "—");
        properties.put("Document Number", number.isEmpty() ? "—" : number);
        properties.put("Issuing State", issuingState);
        properties.put("Nationality", nationality);
        properties.put("Date of Birth(YYYY-MM-DD)", birthDate.isEmpty() ? "—" : birthDate);
        properties.put("Date of Expiry(YYYY-MM-DD)", expiryDate.isEmpty() ? "—" : expiryDate);

        return properties;
    }

    public static String getFirstNonNull(Map<String, String> map, String... keys) {
        for (String key : keys) {
            String value = map.get(key);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return null;
    }

    public static String formatSex(String sex) {
        if (sex == null || sex.isEmpty()) return "—";
        switch (sex.toUpperCase().charAt(0)) {
            case 'M': return "Male";
            case 'F': return "Female";
            default: return sex;
        }
    }

    public static String formatDate(String year, String month, String day) {
        if (year == null || month == null || day == null) return "";
        try {
            int y = Integer.parseInt(year);
            if (y < 100) y += (y <= 30 ? 2000 : 1900);
            return String.format("%04d-%s-%s", y, month, day);
        } catch (Exception e) {
            return "";
        }
    }

    public static int calculateAge(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance();
        int cYear = calendar.get(Calendar.YEAR);
        int cMonth = calendar.get(Calendar.MONTH) + 1;
        int cDay = calendar.get(Calendar.DAY_OF_MONTH);

        int birthYear = (year < 100) ? (year + 1900) : year;
        // Adjust for 2-digit years that might be 2000+
        if (cYear - birthYear > 100) {
             birthYear = (year < 100) ? (year + 2000) : year;
        }

        int diffYear = cYear - birthYear;
        int diffMonth = cMonth - month;
        int diffDay = cDay - day;
        
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
