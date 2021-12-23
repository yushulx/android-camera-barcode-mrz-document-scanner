package com.example.qrcodescanner;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.dynamsoft.dbr.BarcodeReaderException;
import com.dynamsoft.dbr.TextResult;
import com.dynamsoft.dce.*;

import com.dynamsoft.dbr.*;

public class DceActivity extends AppCompatActivity implements DCEFrameListener {
    public final static String TAG = "DCE";
    private DCECameraView previewView;
    private TextView resultView;
    private CameraEnhancer cameraEnhancer;
    private BarcodeReader reader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dce_main);
        previewView = findViewById(R.id.dce_viewFinder);
        resultView = findViewById(R.id.dce_result);

        try {
            reader = new BarcodeReader();
            reader.initLicense("LICENSE-KEY"); // Get a license key from https://www.dynamsoft.com/customer/license/trialLicense?product=dbr
        } catch (BarcodeReaderException e) {
            e.printStackTrace();
        }

        cameraEnhancer = new CameraEnhancer(this);
        cameraEnhancer.setCameraView(previewView);
        cameraEnhancer.addListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            cameraEnhancer.open();
        } catch (CameraEnhancerException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            cameraEnhancer.close();
        } catch (CameraEnhancerException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void frameOutputCallback(DCEFrame dceFrame, long l) {
        // image processing
        Log.i(TAG, "image processing.................");
        TextResult[] results = null;
        try {
            results = reader.decodeBufferedImage(dceFrame.toBitmap(), "");
        } catch (BarcodeReaderException e) {
            e.printStackTrace();
        }
        String output = "No barcode found!";
        if (results != null && results.length > 0) {
            output = "Found " + results.length + " barcodes.";
            for (int i = 0; i < results.length; i++) {
                output += "Index: " + i;
                output += "Format: " + results[i].barcodeFormatString + "\n";
                output += "Text: " + results[i].barcodeText + "\n\n";
            }
        }

        final String result = output;
        runOnUiThread(()->{resultView.setText(result);});
        // image processing
    }
}
