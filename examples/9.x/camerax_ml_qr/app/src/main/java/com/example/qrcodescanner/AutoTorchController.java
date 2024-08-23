package com.example.qrcodescanner;

import static android.content.Context.SENSOR_SERVICE;

import android.app.Activity;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class AutoTorchController implements SensorEventListener {
    public final static String TAG = "AutoTorchController";
    private SensorManager sensorManager;
    private TorchStatus torchStatus;

    public interface TorchStatus {
        void onTorchChange(boolean status);
    }

    public AutoTorchController(Activity activity) {
        sensorManager = (SensorManager)activity.getSystemService(SENSOR_SERVICE);
    }

    public void onStart() {
        Sensor lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        if(lightSensor != null){
            sensorManager.registerListener(
                    this,
                    lightSensor,
                    SensorManager.SENSOR_DELAY_NORMAL);

        }
    }

    public void onStop() {
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if(event.sensor.getType() == Sensor.TYPE_LIGHT){
//            Log.i(TAG, "LIGHT: " + event.values[0] + "...........................");
            if (event.values[0] < 20) {
                if (torchStatus != null) torchStatus.onTorchChange(true);
            }
            else {
                if (torchStatus != null) torchStatus.onTorchChange(false);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    public void addListener(TorchStatus torchStatus) {
        this.torchStatus = torchStatus;
    }
}
