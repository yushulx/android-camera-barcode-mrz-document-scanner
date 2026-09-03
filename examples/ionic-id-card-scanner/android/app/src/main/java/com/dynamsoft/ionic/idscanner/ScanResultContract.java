package com.dynamsoft.ionic.idscanner;

import android.content.Intent;

/** Contract used between the Capacitor plugin and the native scan activity. */
public final class ScanResultContract {

    public static final String EXTRA_JSON = "extra_id_scan_json";
    public static final String EXTRA_ERROR = "extra_id_scan_error";

    private ScanResultContract() {
    }

    public static Intent ok(String json) {
        return new Intent().putExtra(EXTRA_JSON, json);
    }

    public static Intent error(String message) {
        return new Intent().putExtra(EXTRA_ERROR, message);
    }
}
