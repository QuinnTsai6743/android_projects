package com.graystone.camerautil;

import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.util.Log;
import android.util.Range;

import androidx.annotation.NonNull;

import java.util.Locale;

public class AeStatistic {
    private final String mLogString;

    public AeStatistic(TotalCaptureResult result) {
        long frameNo = result.getFrameNumber();
        long exposureTime = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
        int iso = result.get(CaptureResult.SENSOR_SENSITIVITY);
        AeStateWrapper aeState = new AeStateWrapper(result.get(CaptureResult.CONTROL_AE_STATE));
        mLogString = String.format(Locale.US, "[%6d] Exp.-Time %.4f ms, Gain %.2f, (%s)", frameNo, exposureTime / 1000000.0, iso / 100.0, aeState);
    }

    public static void log(TotalCaptureResult result, String tag, long interval) {
        long frameNo = result.getFrameNumber();
        if (frameNo % interval == 0) {
            double exposureTime = result.get(CaptureResult.SENSOR_EXPOSURE_TIME) / 1000000.0;
            double gain = result.get(CaptureResult.SENSOR_SENSITIVITY) / 100.0;
            AeStateWrapper aeState = new AeStateWrapper(result.get(CaptureResult.CONTROL_AE_STATE));
            Log.d(tag, String.format(Locale.US, "[%d] %.4f ms, gain %.2f, %s", frameNo, exposureTime, gain, aeState));
            Range<Integer> aeFpsRange = result.get(CaptureResult.CONTROL_AE_TARGET_FPS_RANGE);
            if (aeFpsRange != null) {
                Log.d(tag, "FPS range " + aeFpsRange);
            }
            MeteringRectangle [] meteringRegions = result.get(CaptureResult.CONTROL_AE_REGIONS);
            if (meteringRegions != null) {
                Log.d(tag, "Num of AE regions: " + meteringRegions.length);
                if (meteringRegions.length > 0) {
                    Log.d(tag, meteringRegions[0].toString());
                }
            }
        }
    }

    @NonNull
    public String toString() {
        return mLogString;
    }
}
