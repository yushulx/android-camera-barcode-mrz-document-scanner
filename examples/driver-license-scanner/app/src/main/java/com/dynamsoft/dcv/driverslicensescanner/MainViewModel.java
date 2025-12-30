package com.dynamsoft.dcv.driverslicensescanner;

import androidx.lifecycle.ViewModel;

public class MainViewModel extends ViewModel {
    public String[] results;

    public String parsedText;
    
    // 0: Low (640x480), 1: Medium (1280x720), 2: High (1920x1080)
    public int resolutionIndex = 1;

    public void reset() {
        results = null;
        parsedText = null;
    }
}
