package com.dynamsoft.ionic.idscanner;

import android.util.Log;

import com.dynamsoft.dcp.ParsedResultItem;

import org.json.JSONObject;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/**
 * Turns a {@link com.dynamsoft.dcp.ParsedResultItem} produced by the native
 * {@code ReadPassportAndId} template into the friendly field set shown by the
 * Ionic UI. The logic mirrors the reference IdScanner example.
 */
public final class IdResultFormatter {

    private static final String TAG = "IdResultFormatter";

    private IdResultFormatter() {
    }

    /**
     * Extract the parsed MRZ fields into a display-ready map.
     */
    public static Map<String, String> toFieldMap(ParsedResultItem item) {
        HashMap<String, String> entry = item.getParsedFields();
        HashMap<String, String> out = new HashMap<>();

        String codeType = item.getCodeType();
        String docType = "PASSPORT";
        if (codeType != null) {
            if (codeType.contains("TD1") || codeType.contains("ID")) {
                docType = "ID";
            } else if (codeType.contains("VISA")) {
                docType = "VISA";
            }
        }
        out.put("documentType", docType);

        String number = firstNonNull(entry, "passportNumber", "documentNumber", "idNumber");
        String firstName = firstNonNull(entry, "secondaryIdentifier", "givenNames");
        String lastName = firstNonNull(entry, "primaryIdentifier", "lastName");
        String nationality = entry.get("nationality");
        String issuingState = entry.get("issuingState");
        String sex = entry.get("sex");

        String fullName = lastName;
        if (firstName != null && !firstName.isEmpty()) {
            fullName = (fullName == null || fullName.isEmpty()) ? firstName : fullName + ", " + firstName;
        }
        if (fullName == null || fullName.isEmpty()) {
            fullName = "—";
        }

        out.put("name", fullName);
        out.put("sex", formatSex(sex));
        out.put("documentNumber", number.isEmpty() ? "—" : number);
        out.put("issuingState", nationalityOfState(issuingState));
        out.put("nationality", nationalityOfState(nationality));
        out.put("dateOfBirth", formatDate(entry.get("birthYear"), entry.get("birthMonth"), entry.get("birthDay")));
        out.put("dateOfExpiry", formatDate(entry.get("expiryYear"), entry.get("expiryMonth"), entry.get("expiryDay")));
        out.put("age", formatAge(entry.get("birthYear"), entry.get("birthMonth"), entry.get("birthDay")));
        return out;
    }

    public static JSONObject toJson(ParsedResultItem item) throws Exception {
        return new JSONObject(toFieldMap(item));
    }

    private static String firstNonNull(Map<String, String> map, String... keys) {
        for (String key : keys) {
            String value = map.get(key);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private static String nationalityOfState(String value) {
        if (value == null || value.isEmpty()) {
            return "—";
        }
        // The native parser already expands country codes to names; guard just in case.
        return value;
    }

    private static String formatSex(String sex) {
        if (sex == null || sex.isEmpty()) {
            return "—";
        }
        switch (sex.toUpperCase().charAt(0)) {
            case 'M':
                return "Male";
            case 'F':
                return "Female";
            default:
                return sex;
        }
    }

    private static String formatDate(String year, String month, String day) {
        if (year == null || month == null || day == null) {
            return "—";
        }
        return year + "-" + month + "-" + day;
    }

    private static String formatAge(String year, String month, String day) {
        if (year == null || month == null || day == null) {
            return "—";
        }
        try {
            int age = calculateAge(Integer.parseInt(year), Integer.parseInt(month), Integer.parseInt(day));
            return age >= 0 ? String.valueOf(age) : "—";
        } catch (Exception e) {
            Log.e(TAG, "Failed to compute age", e);
            return "—";
        }
    }

    private static int calculateAge(int birthYear, int birthMonth, int birthDay) {
        Calendar dob = Calendar.getInstance();
        dob.set(birthYear, birthMonth - 1, birthDay);
        Calendar today = Calendar.getInstance();
        int age = today.get(Calendar.YEAR) - dob.get(Calendar.YEAR);
        if (today.get(Calendar.DAY_OF_YEAR) < dob.get(Calendar.DAY_OF_YEAR)) {
            age--;
        }
        return age;
    }
}
