package com.dynamsoft.mrzscannerbundle.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;

public class VINData {

    public String vinString;
    public String wmi;
    public String region;
    public String vds;
    public String checkDigit;
    public String modelYear;
    public String plantCode;
    public String serialNumber;

    public static VINData extractItem(@Nullable HashMap<String, String> item) {
        if  (item == null) return null;

        VINData data = new VINData();
        data.vinString = item.get("vinString") == null ? "" : item.get("vinString");
        data.wmi = item.get("WMI") == null ? "" : item.get("WMI");
        data.region = item.get("region") == null ? "" : item.get("region");
        data.vds = item.get("VDS") == null ? "" : item.get("VDS");
        data.checkDigit = item.get("checkDigit") == null ? "" : item.get("checkDigit");
        data.modelYear = item.get("modelYear") == null ? "" : item.get("modelYear");
        data.plantCode = item.get("plantCode") == null ? "" : item.get("plantCode");
        data.serialNumber = item.get("serialNumber") == null ? "" : item.get("serialNumber");

        return data;
    }

    @NonNull
    public String toString() {
        return "VIN String: " + vinString + "\n" +
                "WMI: " + wmi + "\n" +
                "Region: " + region + "\n" +
                "VDS: " + vds + "\n" +
                "Check Digit: " + checkDigit + "\n" +
                "Model Year: " + modelYear + "\n" +
                "Manufacturer plant: " + plantCode + "\n" +
                "Serial Number: " + serialNumber;
    }
}
