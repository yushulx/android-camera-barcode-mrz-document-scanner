package com.dynamsoft.ionic.idscanner;

import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Register the in-app Dynamsoft MRZ Scanner plugin before the bridge is built.
        registerPlugin(IdScannerNativePlugin.class);
        super.onCreate(savedInstanceState);
    }
}
