package com.med.app.wbcalibration;

import android.hardware.camera2.CaptureResult;

import java.util.HashMap;

public class CaptureResultWrapper {
    private final CaptureResult mCaptureResult;

    CaptureResultWrapper(CaptureResult result) {
        mCaptureResult = result;
    }

    private final HashMap<Integer, String> mControlAwbModeMap = new HashMap<Integer, String>() {{
        put(CaptureResult.CONTROL_AWB_MODE_OFF, "OFF");
        put(CaptureResult.CONTROL_AWB_MODE_AUTO, "AUTO");
        put(CaptureResult.CONTROL_AWB_MODE_INCANDESCENT, "INCANDESCENT");
        put(CaptureResult.CONTROL_AWB_MODE_FLUORESCENT, "FLUORESCENT");
        put(CaptureResult.CONTROL_AWB_MODE_WARM_FLUORESCENT, "WARM FLUORESCENT");
        put(CaptureResult.CONTROL_AWB_MODE_DAYLIGHT, "DAYLIGHT");
        put(CaptureResult.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT, "CLOUDY DAYLIGHT");
        put(CaptureResult.CONTROL_AWB_MODE_TWILIGHT, "TWILIGHT");
        put(CaptureResult.CONTROL_AWB_MODE_SHADE, "SHADE");
    }};
    public String getControlAwbMode() {
        String strAwbMode;
        int awbMode = mCaptureResult.get(CaptureResult.CONTROL_AWB_MODE);
        strAwbMode = mControlAwbModeMap.get(awbMode);
        if (strAwbMode == null) {
            return "????";
        }
        return strAwbMode;
    }
}
