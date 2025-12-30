package com.dynamsoft.dcv.driverslicensescanner;

import androidx.lifecycle.ViewModel;

public class MainViewModel extends ViewModel {
    public String[] results;

    public String parsedText;
    
    // 0: 720P (1280x720), 1: 1080P (1920x1080)
    public int resolutionIndex = 0;

    public void reset() {
        results = null;
        parsedText = null;
    }
}
