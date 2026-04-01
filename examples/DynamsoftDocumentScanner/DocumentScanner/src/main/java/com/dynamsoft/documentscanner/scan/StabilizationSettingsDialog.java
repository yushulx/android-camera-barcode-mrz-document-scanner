package com.dynamsoft.documentscanner.scan;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.dynamsoft.documentscanner.R;
import com.dynamsoft.documentscanner.utils.QuadStabilizer;
import com.google.android.material.slider.Slider;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.Locale;

public class StabilizationSettingsDialog extends DialogFragment {

    public interface OnSettingsChangedListener {
        void onSettingsChanged();
    }

    private OnSettingsChangedListener settingsChangedListener;

    public void setOnSettingsChangedListener(OnSettingsChangedListener listener) {
        this.settingsChangedListener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_stabilization_settings, null);

        QuadStabilizer stabilizer = new QuadStabilizer(requireContext());

        SwitchMaterial switchAutoCapture = view.findViewById(R.id.switch_auto_capture);
        Slider sliderIou = view.findViewById(R.id.slider_iou);
        Slider sliderAreaDelta = view.findViewById(R.id.slider_area_delta);
        Slider sliderFrameCount = view.findViewById(R.id.slider_frame_count);
        TextView tvIouValue = view.findViewById(R.id.tv_iou_value);
        TextView tvAreaDeltaValue = view.findViewById(R.id.tv_area_delta_value);
        TextView tvFrameCountValue = view.findViewById(R.id.tv_frame_count_value);

        // Set current values
        switchAutoCapture.setChecked(stabilizer.isAutoCaptureEnabled());
        sliderIou.setValue(stabilizer.getIouThreshold());
        sliderAreaDelta.setValue(stabilizer.getAreaDeltaThreshold());
        sliderFrameCount.setValue(stabilizer.getStableFrameCount());

        tvIouValue.setText(String.format(Locale.US, "%.2f", stabilizer.getIouThreshold()));
        tvAreaDeltaValue.setText(String.format(Locale.US, "%.2f", stabilizer.getAreaDeltaThreshold()));
        tvFrameCountValue.setText(String.valueOf(stabilizer.getStableFrameCount()));

        sliderIou.addOnChangeListener((slider, value, fromUser) ->
                tvIouValue.setText(String.format(Locale.US, "%.2f", value)));
        sliderAreaDelta.addOnChangeListener((slider, value, fromUser) ->
                tvAreaDeltaValue.setText(String.format(Locale.US, "%.2f", value)));
        sliderFrameCount.addOnChangeListener((slider, value, fromUser) ->
                tvFrameCountValue.setText(String.valueOf((int) value)));

        return new AlertDialog.Builder(requireContext(), com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                .setTitle(R.string.stabilization_settings)
                .setView(view)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    QuadStabilizer.saveSettings(
                            requireContext(),
                            sliderIou.getValue(),
                            sliderAreaDelta.getValue(),
                            (int) sliderFrameCount.getValue(),
                            switchAutoCapture.isChecked()
                    );
                    if (settingsChangedListener != null) {
                        settingsChangedListener.onSettingsChanged();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
    }
}
