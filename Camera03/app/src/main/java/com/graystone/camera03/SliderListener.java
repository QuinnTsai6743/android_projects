package com.graystone.camera03;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.material.slider.Slider;

import java.util.HashMap;

public class SliderListener implements Slider.OnSliderTouchListener, Slider.OnChangeListener {
    private static final String TAG = "Camera03";

    interface HandleFunction {
        void onValueChange(float value);
    }

    private final HashMap<String, HandleFunction> mHandleFuncMap = new HashMap<>();

    public void addHandleFunction(String sliderName, HandleFunction handleFunction) {
        mHandleFuncMap.put(sliderName, handleFunction);
    }

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
        String entryName = slider.getResources().getResourceEntryName(slider.getId());
        Log.i(TAG, "[Slider] entry name = " + entryName);
        if (mHandleFuncMap.containsKey(entryName)) {
            HandleFunction handleFunc = mHandleFuncMap.get(entryName);
            if (handleFunc != null)
                handleFunc.onValueChange(value);
        }
    }
}
