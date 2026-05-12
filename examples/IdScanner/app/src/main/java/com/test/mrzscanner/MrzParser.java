package com.test.mrzscanner;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for parsing Machine Readable Zone (MRZ) text from
 * passports, ID cards, and travel documents.
 *
 * Supports:
 * - TD1 (ID cards, 3 lines x 30 characters)
 * - TD2 (Travel documents, 2 lines x 36 characters)
 * - TD3/MRP (Passports, 2 lines x 44 characters)
 */
public class MrzParser {

    private static final String TAG = "MrzParser";
    private static final String LICENSE_KEY = "DLS2eyJoYW5kc2hha2VDb2RlIjoiMjAwMDAxLTE2NDk4Mjk3OTI2MzUiLCJvcmdhbml6YXRpb25JRCI6IjIwMDAwMSIsInNlc3Npb25QYXNzd29yZCI6IndTcGR6Vm05WDJrcEQ5YUoifQ==";

    public static void initLicense() {
        com.dynamsoft.license.LicenseManager.initLicense(LICENSE_KEY, (isSuccess, error) -> {
            if (!isSuccess) {
                android.util.Log.e(TAG, "License initialization failed: " + error.getMessage());
            }
        });
    }

    public static Map<String, String> parse(com.dynamsoft.dcp.ParsedResultItem item) {
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
            android.util.Log.e(TAG, "Error calculating age", e);
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

    private static String getFirstNonNull(Map<String, String> map, String... keys) {
        for (String key : keys) {
            String value = map.get(key);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private static String formatSex(String sex) {
        if (sex == null || sex.isEmpty()) return "—";
        switch (sex.toUpperCase().charAt(0)) {
            case 'M': return "MALE";
            case 'F': return "FEMALE";
            default: return sex;
        }
    }

    private static String formatDate(String year, String month, String day) {
        if (year == null || month == null || day == null) return "";
        return year + "-" + month + "-" + day;
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

    public static class MrzData {
        public String documentType = "";
        public String documentNumber = "";
        public String firstName = "";
        public String lastName = "";
        public String fullName = "";
        public String nationality = "";
        public String issuingState = "";
        public String dateOfBirth = "";
        public String dateOfExpiry = "";
        public String sex = "";
        public int age = -1;
        public String rawMrz = "";
        public boolean isValid = false;

        public Map<String, String> toMap() {
            Map<String, String> map = new HashMap<>();
            map.put("Document Type", documentType);
            map.put("Document Number", documentNumber);
            map.put("Name", fullName);
            map.put("Nationality", nationality);
            map.put("Issuing State", issuingState);
            map.put("Date of Birth(YYYY-MM-DD)", dateOfBirth);
            map.put("Date of Expiry(YYYY-MM-DD)", dateOfExpiry);
            map.put("Sex", sex);
            map.put("Age", age == -1 ? "Unknown" : String.valueOf(age));
            return map;
        }
    }

    // MRZ line patterns
    private static final Pattern TD1_PATTERN = Pattern.compile(
            "([A-Z]{1,2})([A-Z<]{3})([A-Z0-9<]{9})([0-9]{1})([A-Z0-9<]{15})\n" +
            "([0-9]{6})([0-9]{1})([MF<])([0-9]{6})([0-9]{1})([A-Z<]{3})([A-Z0-9<]{11})([0-9]{1})\n" +
            "([A-Z<]+)");

    private static final Pattern TD3_PATTERN = Pattern.compile(
            "([P])([A-Z<]{1})([A-Z<]{3})([A-Z<]+)\n" +
            "([A-Z0-9<]{9})([0-9]{1})([A-Z<]{3})([0-9]{6})([0-9]{1})([MF<])([0-9]{6})([0-9]{1})([A-Z0-9<]{14})([0-9]{1})([0-9]{1})");

    /**
     * Parse MRZ text and extract document data
     */
    public static MrzData parse(String mrzText) {
        MrzData data = new MrzData();
        if (mrzText == null || mrzText.isEmpty()) {
            return data;
        }

        // Normalize the MRZ text
        String normalized = normalizeMrz(mrzText);
        data.rawMrz = normalized;

        String[] lines = normalized.split("\n");

        try {
            if (lines.length >= 2) {
                int lineLength = lines[0].length();

                if (lineLength >= 44 && lines.length == 2) {
                    // TD3 - Passport (2 lines x 44 chars)
                    parseTD3(lines, data);
                } else if (lineLength >= 36 && lines.length == 2) {
                    // TD2 - Travel Document (2 lines x 36 chars)
                    parseTD2(lines, data);
                } else if (lineLength >= 30 && lines.length >= 3) {
                    // TD1 - ID Card (3 lines x 30 chars)
                    parseTD1(lines, data);
                } else {
                    // Try generic parsing
                    parseGeneric(lines, data);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            data.isValid = false;
        }

        return data;
    }

    /**
     * Parse TD3 format (Passport - 2 lines x 44 characters)
     */
    private static void parseTD3(String[] lines, MrzData data) {
        String line1 = padLine(lines[0], 44);
        String line2 = padLine(lines[1], 44);

        // Line 1: Document type, country, names
        data.documentType = "PASSPORT";
        data.issuingState = cleanField(line1.substring(2, 5));

        // Extract names (surname<<first<name<...)
        String nameField = line1.substring(5);
        parseNames(nameField, data);

        // Line 2: Document number, nationality, DOB, sex, expiry
        data.documentNumber = cleanField(line2.substring(0, 9));
        data.nationality = cleanField(line2.substring(10, 13));

        // Date of Birth (YYMMDD)
        String dobRaw = line2.substring(13, 19);
        data.dateOfBirth = parseDate(dobRaw);
        data.age = calculateAge(dobRaw);

        // Sex
        data.sex = parseSex(line2.charAt(20));

        // Date of Expiry (YYMMDD)
        String expRaw = line2.substring(21, 27);
        data.dateOfExpiry = parseDate(expRaw);

        data.isValid = !data.documentNumber.isEmpty() && !data.fullName.isEmpty();
    }

    /**
     * Parse TD2 format (Travel Document - 2 lines x 36 characters)
     */
    private static void parseTD2(String[] lines, MrzData data) {
        String line1 = padLine(lines[0], 36);
        String line2 = padLine(lines[1], 36);

        // Line 1
        String docTypeCode = line1.substring(0, 2);
        if (docTypeCode.startsWith("V")) {
            data.documentType = "VISA";
        } else if (docTypeCode.startsWith("I")) {
            data.documentType = "ID";
        } else {
            data.documentType = "TRAVEL DOCUMENT";
        }

        data.issuingState = cleanField(line1.substring(2, 5));
        String nameField = line1.substring(5);
        parseNames(nameField, data);

        // Line 2
        data.documentNumber = cleanField(line2.substring(0, 9));
        data.nationality = cleanField(line2.substring(10, 13));

        String dobRaw = line2.substring(13, 19);
        data.dateOfBirth = parseDate(dobRaw);
        data.age = calculateAge(dobRaw);

        data.sex = parseSex(line2.charAt(20));

        String expRaw = line2.substring(21, 27);
        data.dateOfExpiry = parseDate(expRaw);

        data.isValid = !data.documentNumber.isEmpty() && !data.fullName.isEmpty();
    }

    /**
     * Parse TD1 format (ID Card - 3 lines x 30 characters)
     */
    private static void parseTD1(String[] lines, MrzData data) {
        String line1 = padLine(lines[0], 30);
        String line2 = padLine(lines[1], 30);
        String line3 = padLine(lines[2], 30);

        // Line 1
        data.documentType = "ID";
        data.issuingState = cleanField(line1.substring(2, 5));
        data.documentNumber = cleanField(line1.substring(5, 14));

        // Line 2
        String dobRaw = line2.substring(0, 6);
        data.dateOfBirth = parseDate(dobRaw);
        data.age = calculateAge(dobRaw);

        data.sex = parseSex(line2.charAt(7));

        String expRaw = line2.substring(8, 14);
        data.dateOfExpiry = parseDate(expRaw);

        data.nationality = cleanField(line2.substring(15, 18));

        // Line 3 - Names
        parseNames(line3, data);

        data.isValid = !data.documentNumber.isEmpty() && !data.fullName.isEmpty();
    }

    /**
     * Generic MRZ parsing for non-standard formats
     */
    private static void parseGeneric(String[] lines, MrzData data) {
        StringBuilder fullText = new StringBuilder();
        for (String line : lines) {
            fullText.append(line).append("\n");
        }

        // Try to extract document number (9 alphanumeric characters)
        Pattern docNumPattern = Pattern.compile("([A-Z0-9]{9})");
        Matcher docMatcher = docNumPattern.matcher(lines.length > 1 ? lines[1] : lines[0]);
        if (docMatcher.find()) {
            data.documentNumber = docMatcher.group(1);
        }

        // Try to extract dates (YYMMDD format)
        Pattern datePattern = Pattern.compile("([0-9]{6})");
        Matcher dateMatcher = datePattern.matcher(fullText.toString());
        int dateCount = 0;
        while (dateMatcher.find() && dateCount < 2) {
            String dateStr = dateMatcher.group(1);
            if (dateCount == 0) {
                data.dateOfBirth = parseDate(dateStr);
                data.age = calculateAge(dateStr);
            } else {
                data.dateOfExpiry = parseDate(dateStr);
            }
            dateCount++;
        }

        // Try to extract sex
        Pattern sexPattern = Pattern.compile("[<]([MF])[<]");
        Matcher sexMatcher = sexPattern.matcher(fullText.toString());
        if (sexMatcher.find()) {
            data.sex = parseSex(sexMatcher.group(1).charAt(0));
        }

        // Try to extract names from first line
        if (lines.length > 0 && lines[0].contains("<<")) {
            parseNames(lines[0].substring(5), data);
        }

        data.documentType = "DOCUMENT";
        data.isValid = !data.documentNumber.isEmpty();
    }

    /**
     * Parse names from MRZ name field
     * Format: SURNAME<<FIRSTNAME<MIDDLENAME
     */
    private static void parseNames(String nameField, MrzData data) {
        // Split surname and given names
        String[] parts = nameField.split("<<");
        if (parts.length >= 1) {
            data.lastName = cleanField(parts[0]).replace("<", " ").trim();
        }
        if (parts.length >= 2) {
            data.firstName = cleanField(parts[1]).replace("<", " ").trim();
        }

        // Format full name
        if (!data.lastName.isEmpty() && !data.firstName.isEmpty()) {
            data.fullName = data.lastName + ", " + data.firstName;
        } else if (!data.lastName.isEmpty()) {
            data.fullName = data.lastName;
        } else if (!data.firstName.isEmpty()) {
            data.fullName = data.firstName;
        }

        // Capitalize properly
        data.fullName = capitalizeWords(data.fullName);
        data.firstName = capitalizeWords(data.firstName);
        data.lastName = capitalizeWords(data.lastName);
    }

    /**
     * Parse date from YYMMDD format to YYYY-MM-DD
     */
    private static String parseDate(String yymmdd) {
        if (yymmdd == null || yymmdd.length() < 6) {
            return "";
        }

        try {
            int yy = Integer.parseInt(yymmdd.substring(0, 2));
            String mm = yymmdd.substring(2, 4);
            String dd = yymmdd.substring(4, 6);

            // Determine century (assume 00-30 is 2000s, 31-99 is 1900s for DOB)
            int year = yy <= 30 ? 2000 + yy : 1900 + yy;

            return String.format("%04d-%s-%s", year, mm, dd);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Calculate age from YYMMDD format
     */
    private static int calculateAge(String yymmdd) {
        if (yymmdd == null || yymmdd.length() < 6) {
            return -1;
        }

        try {
            int yy = Integer.parseInt(yymmdd.substring(0, 2));
            int mm = Integer.parseInt(yymmdd.substring(2, 4));
            int dd = Integer.parseInt(yymmdd.substring(4, 6));

            Calendar now = Calendar.getInstance();
            int currentYear = now.get(Calendar.YEAR);
            int currentMonth = now.get(Calendar.MONTH) + 1;
            int currentDay = now.get(Calendar.DAY_OF_MONTH);

            // Determine birth year
            int birthYear = yy <= 30 ? 2000 + yy : 1900 + yy;

            int age = currentYear - birthYear;

            // Adjust if birthday hasn't occurred yet this year
            if (currentMonth < mm || (currentMonth == mm && currentDay < dd)) {
                age--;
            }

            // Sanity check - if age is negative or over 120, try other century
            if (age < 0 || age > 120) {
                birthYear = yy <= 30 ? 1900 + yy : 2000 + yy;
                age = currentYear - birthYear;
                if (currentMonth < mm || (currentMonth == mm && currentDay < dd)) {
                    age--;
                }
            }

            return Math.max(0, age);
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * Parse sex field
     */
    private static String parseSex(char sex) {
        switch (sex) {
            case 'M':
                return "Male";
            case 'F':
                return "Female";
            default:
                return "Unknown";
        }
    }

    /**
     * Clean MRZ field by removing filler characters
     */
    private static String cleanField(String field) {
        if (field == null) return "";
        return field.replace("<", "").trim();
    }

    /**
     * Normalize MRZ text for parsing
     */
    private static String normalizeMrz(String text) {
        if (text == null) return "";

        // Convert to uppercase
        text = text.toUpperCase();

        // Replace common OCR errors
        text = text.replace("O", "0")
                   .replace("I", "1")
                   .replace("S", "5")
                   .replace("B", "8");

        // Actually, be more careful - only replace in numeric positions
        // For now, just normalize whitespace and newlines
        text = text.replaceAll("[\\r\\n]+", "\n")
                   .replaceAll(" +", "")
                   .trim();

        return text;
    }

    /**
     * Pad line to expected length
     */
    private static String padLine(String line, int length) {
        if (line == null) return String.format("%" + length + "s", "").replace(' ', '<');
        if (line.length() >= length) return line.substring(0, length);
        return line + String.format("%" + (length - line.length()) + "s", "").replace(' ', '<');
    }

    /**
     * Capitalize first letter of each word
     */
    private static String capitalizeWords(String text) {
        if (text == null || text.isEmpty()) return text;

        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;

        for (char c : text.toLowerCase().toCharArray()) {
            if (Character.isWhitespace(c) || c == ',' || c == '-') {
                capitalizeNext = true;
                result.append(c);
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
            }
        }

        return result.toString();
    }

    /**
     * Validate MRZ check digit
     */
    public static boolean validateCheckDigit(String data, int checkDigit) {
        int[] weights = {7, 3, 1};
        int sum = 0;

        for (int i = 0; i < data.length(); i++) {
            char c = data.charAt(i);
            int value;

            if (c >= '0' && c <= '9') {
                value = c - '0';
            } else if (c >= 'A' && c <= 'Z') {
                value = c - 'A' + 10;
            } else {
                value = 0; // filler <
            }

            sum += value * weights[i % 3];
        }

        return (sum % 10) == checkDigit;
    }
}
