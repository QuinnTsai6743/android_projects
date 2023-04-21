package com.graystone.camerautil;

import android.hardware.camera2.CameraCharacteristics;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Locale;

public class AeStateWrapper {
    private final int mAeState;
    private final String mErrStr;

    private static final HashMap<Integer, String> mStateNameMap = new HashMap<Integer, String>() {{
        put(CameraCharacteristics.CONTROL_AE_STATE_INACTIVE, "INACTIVE");
        put(CameraCharacteristics.CONTROL_AE_STATE_SEARCHING, "SEARCHING");
        put(CameraCharacteristics.CONTROL_AE_STATE_CONVERGED, "CONVERGED");
        put(CameraCharacteristics.CONTROL_AE_STATE_LOCKED, "LOCKED");
        put(CameraCharacteristics.CONTROL_AE_STATE_FLASH_REQUIRED, "FLASH_REQUIRED");
        put(CameraCharacteristics.CONTROL_AE_STATE_PRECAPTURE, "PRECAPTURE");
    }};

    public AeStateWrapper(int aeState) {
        mAeState = aeState;
        mErrStr = String.format(Locale.US, "??? (%d)", mAeState);
    }

    @Override
    @NonNull
    public String toString() {
        String ret = mStateNameMap.getOrDefault(mAeState, mErrStr);
        return (ret != null)? ret : mErrStr;
    }
}
