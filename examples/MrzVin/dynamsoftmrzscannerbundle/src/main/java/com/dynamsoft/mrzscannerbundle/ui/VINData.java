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
        VINData data = new VINData();
        data.vinString = item.get("vinString");
        data.wmi = item.get("WMI");
        data.region = item.get("region");
        data.vds = item.get("VDS");
        data.checkDigit = item.get("checkDigit");
        data.modelYear = item.get("modelYear");
        data.plantCode = item.get("plantCode");
        data.serialNumber = item.get("serialNumber");

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
