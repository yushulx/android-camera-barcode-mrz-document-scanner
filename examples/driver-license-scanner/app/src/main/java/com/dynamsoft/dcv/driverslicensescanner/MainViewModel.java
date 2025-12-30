package com.dynamsoft.dcv.driverslicensescanner;

import androidx.lifecycle.ViewModel;

public class MainViewModel extends ViewModel {
    public String[] results;

    public String parsedText;

    public void reset() {
        results = null;
        parsedText = null;
    }
}
