package com.dynamsoft.ionic.qrcodescanner;

import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Register the in-app Dynamsoft Capture Vision plugin before the bridge is built.
        registerPlugin(BarcodeScannerNativePlugin.class);
        super.onCreate(savedInstanceState);
    }
}
