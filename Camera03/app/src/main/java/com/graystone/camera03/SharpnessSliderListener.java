package com.graystone.camera03;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.material.slider.Slider;

public class SharpnessSliderListener implements Slider.OnSliderTouchListener, Slider.OnChangeListener {
    private static final String TAG = "Camera03";

    @Override
    public void onStartTrackingTouch(@NonNull Slider slider) {
        Log.d(TAG, ">>>>> (onStartTrackingTouch) >>>>>");
    }

    @Override
    public void onStopTrackingTouch(@NonNull Slider slider) {
        Log.d(TAG, "<<<<< (onStopTrackingTouch) <<<<<");
    }

    @Override
    public void onValueChange(@NonNull Slider slider, float value, boolean fromUser) {
        int sharpnessLv = Float.valueOf(value).intValue();
        Log.d(TAG, "[sharpness] value=" + sharpnessLv);
        try {
            String ret = System.setProperty("persist.vendor.camera.sharpness", Integer.valueOf(sharpnessLv).toString());
            Log.d(TAG, "return string: " + ret);
        }
        catch (SecurityException e) {
            Log.e(TAG, "SecurityException");
        }
    }
}
