package com.dynamsoft.dcv.driverslicensescanner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.dynamsoft.dcp.ParsedResultItem;

import java.util.ArrayList;

public class ParseUtil {
    private static final String TAG = "ParseUtil";

    @Nullable
    public static String[] parsedItemToDisplayStrings(@Nullable ParsedResultItem item) {
        if (item == null || item.getParsedFields().isEmpty()) {
            return null;
        }
        ArrayList<String> list = new ArrayList<>();
        String codeType = item.getCodeType();

        boolean hasName;
        boolean hasLicenseNumber;
        switch (codeType) {
            case "AAMVA_DL_ID":
                list.add("Document Type: " + item.getCodeType());

                boolean hasFullName = addFieldToStringList(list, item, "Full Name", "fullName");
                boolean hasLastName = addFieldToStringList(list, item, "Last Name", "lastName");
                boolean hasGivenName = addFieldToStringList(list, item, "Given Name", "givenName");
                boolean hasFirstName = addFieldToStringList(list, item, "First Name", "firstName");
                hasName = hasFullName || hasLastName || hasGivenName || hasFirstName;
                String street1 = item.getFieldValue("street_1");
                String street2 = item.getFieldValue("street_2");
                if (street1 != null) {
                    list.add("Street: " + street1 + " " + (street2 != null ? street2 : ""));
                }

                addFieldToStringList(list, item, "City", "city");
                addFieldToStringList(list, item, "State", "jurisdictionCode");
                hasLicenseNumber = addFieldToStringList(list, item, "License Number", "licenseNumber");

                addFieldToStringList(list, item, "Issue Date", "issuedDate");
                addFieldToStringList(list, item, "Expiration Date", "expirationDate");
                addFieldToStringList(list, item, "Date of Birth", "birthDate");
                addFieldToStringList(list, item, "Height", "height");
                addFieldToStringList(list, item, "Sex", "sex");
                addFieldToStringList(list, item, "Issued Country", "issuingCountry");
                addFieldToStringList(list, item, "Vehicle Class", "vehicleClass");
                break;
            case "AAMVA_DL_ID_WITH_MAG_STRIPE":
                list.add("Document Type: " + item.getCodeType());
                hasName = addFieldToStringList(list, item, "Full Name", "name");
                addFieldToStringList(list, item, "Address", "address");
                addFieldToStringList(list, item, "City", "city");
                addFieldToStringList(list, item, "State or Province", "stateOrProvince");
                hasLicenseNumber = addFieldToStringList(list, item, "License Number", "DLorID_Number");
                addFieldToStringList(list, item, "Expiration Date", "expirationDate");
                addFieldToStringList(list, item, "Date of Birth", "birthDate");
                addFieldToStringList(list, item, "Height", "height");
                addFieldToStringList(list, item, "Sex", "sex");
                break;
            case "SOUTH_AFRICA_DL":
                list.add("Document Type: " + item.getCodeType());
                hasName = addFieldToStringList(list, item, "Surname", "surname");
                hasLicenseNumber = addFieldToStringList(list, item, "ID Number", "idNumber");
                addFieldToStringList(list, item, "ID Number Type", "idNumberType");
                addFieldToStringList(list, item, "Initials", "initials");
                addFieldToStringList(list, item, "License Issue Number", "licenseIssueNumber");
                addFieldToStringList(list, item, "License Number", "licenseNumber");
                addFieldToStringList(list, item, "Validity from", "licenseValidityFrom");
                addFieldToStringList(list, item, "Validity to", "licenseValidityTo");
                addFieldToStringList(list, item, "Date of Birth", "birthDate");
                addFieldToStringList(list, item, "Gender", "gender");
                addFieldToStringList(list, item, "ID Issued Country", "idIssuedCountry");
                addFieldToStringList(list, item, "Driver Restriction Codes", "driverRestrictionCodes");
                break;
            default:
                return null;
        }
        if (!hasName || !hasLicenseNumber) {
            //Lack of key information, therefore results will not be displayed.
            return null;
        }
        return list.toArray(new String[0]);
    }


    /**
     * Adds the display message of a specified field from a parsed result item to the string list.
     *
     * @param list       the string list to add the field value to
     * @param item       the parsed result item to get the field value from
     * @param displayKey the display key to use for the field value
     * @param fieldName  the name of the field to get the value from
     * @return true if the field value was added to the list, false otherwise
     */
    private static boolean addFieldToStringList(ArrayList<String> list, @NonNull ParsedResultItem item, String displayKey, String fieldName) {
        String fieldValue = item.getFieldValue(fieldName);
        if (fieldValue != null) {
            list.add(String.format("%s: %s", displayKey, fieldValue));
            return true;
        } else {
            return false;
        }
    }

}
