package com.med.app.wbcalibration;

import android.graphics.Rect;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraMetadata;

import java.util.HashMap;

public class CameraCharacteristicsWrapper {
    //private static final String TAG = "WBCalibration";

    private final CameraCharacteristics mCharacteristics;

    CameraCharacteristicsWrapper(CameraCharacteristics characteristics) {
        mCharacteristics = characteristics;
    }

    private static final HashMap<Integer, String> LensFacingMap = new HashMap<Integer, String>() {{
        put(CameraMetadata.LENS_FACING_FRONT, "LENS_FACING_FRONT");
        put(CameraMetadata.LENS_FACING_BACK, "LENS_FACING_BACK");
        put(CameraMetadata.LENS_FACING_EXTERNAL, "LENS_FACING_EXTERNAL");
    }};
    public String getFacingName() {
        String strFacing;
        int facing = mCharacteristics.get(CameraCharacteristics.LENS_FACING);
        strFacing = LensFacingMap.get(facing);
        if (strFacing == null) {
            return "????";
        }
        return strFacing;
    }

    public Rect getSensorActiveArraySize() {
        return mCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
    }

    private static final HashMap<Integer, String> ColorFilterMap = new HashMap<Integer, String>() {{
        put(CameraMetadata.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGGB, "RGGB");
        put(CameraMetadata.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GRBG, "GRBG");
        put(CameraMetadata.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GBRG, "GBRG");
        put(CameraMetadata.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_BGGR, "BGGR");
        put(CameraMetadata.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGB, "RGB");
        put(CameraMetadata.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_MONO, "MONO");
        put(CameraMetadata.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_NIR, "NIR");
    }};
    public String getSensorColorFilterName() {
        String strColorFilter;
        int colorFilter = mCharacteristics.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT);
        strColorFilter = ColorFilterMap.get(colorFilter);
        if (strColorFilter == null) {
            return "????";
        }
        return strColorFilter;
    }
    public int getSensorColorFilter() {
        return mCharacteristics.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT);
    }
}
