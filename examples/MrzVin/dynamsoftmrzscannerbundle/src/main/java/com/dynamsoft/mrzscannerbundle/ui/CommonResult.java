package com.dynamsoft.mrzscannerbundle.ui;

import java.util.HashMap;

public class CommonResult {
    protected EnumDetectionType detectionType;
    @EnumResultStatus
    protected int resultStatus;
    protected String errorString;
    protected int errorCode;
    public void assembleMap(HashMap<String, String> entry) {}

    public EnumDetectionType getDetectionType() {
        return detectionType;
    }

    public @interface EnumResultStatus {
        int RS_FINISHED = 0;
        int RS_CANCELED = 1;
        int RS_EXCEPTION = 2;
    }

    @EnumResultStatus
    public int getResultStatus() {
        return resultStatus;
    }

    public String getErrorString() {
        return errorString;
    }

    public int getErrorCode() {
        return errorCode;
    }
}
