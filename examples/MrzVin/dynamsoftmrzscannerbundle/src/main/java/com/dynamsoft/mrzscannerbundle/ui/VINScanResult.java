package com.dynamsoft.mrzscannerbundle.ui;

import static com.dynamsoft.mrzscannerbundle.ui.ScannerActivity.EXTRA_RESULT;
import static com.dynamsoft.mrzscannerbundle.ui.ScannerActivity.EXTRA_STATUS_CODE;

import android.content.Intent;

import java.util.HashMap;

public class VINScanResult extends CommonResult {
    private VINData vinData;

    public VINScanResult(int resultCode, Intent data, EnumDetectionType detectionType) {
        if (data != null) {
            super.resultStatus = data.getIntExtra(EXTRA_STATUS_CODE, resultCode);
            assembleMap((HashMap<String, String>) data.getSerializableExtra(EXTRA_RESULT));
            super.detectionType = detectionType;
        }
    }

    @Override
    public void assembleMap(HashMap<String, String> entry) {
        vinData = VINData.extractItem(entry);
    }

    public VINData getData() {
        return vinData;
    }
}
