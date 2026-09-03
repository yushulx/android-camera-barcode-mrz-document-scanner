package com.dynamsoft.ionic.qrcodescanner;

import android.content.Intent;

/**
 * Contract shared between the scanner Activity and the Capacitor plugin.
 */
final class ScanResultContract {

    private ScanResultContract() {
    }

    /** JSON payload produced by a successful scan. */
    static final String EXTRA_RESULTS = "com.dynamsoft.ionic.qrcodescanner.RESULTS";
    /** Error message produced by a failed scan. */
    static final String EXTRA_ERROR = "com.dynamsoft.ionic.qrcodescanner.ERROR";

    static Intent ok(String resultsJson) {
        Intent intent = new Intent();
        intent.putExtra(EXTRA_RESULTS, resultsJson);
        return intent;
    }

    static Intent error(String message) {
        Intent intent = new Intent();
        intent.putExtra(EXTRA_ERROR, message);
        return intent;
    }
}
