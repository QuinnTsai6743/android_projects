package com.graystone.camera03;

import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraMetadata;

import java.util.HashMap;

public class CameraInfo {
    private final static HashMap<Integer, String> mImageFormatMap = new HashMap<Integer, String>() {{
        put(ImageFormat.DEPTH16, "DEPTH16");
        put(ImageFormat.JPEG, "JPEG");
        put(ImageFormat.DEPTH_JPEG, "DEPTH_JPEG");
        put(ImageFormat.DEPTH_POINT_CLOUD, "DEPTH_POINT_CLOUD");
        put(ImageFormat.FLEX_RGB_888, "FLEX_RGB_888");
        put(ImageFormat.FLEX_RGBA_8888, "FLEX_RGBA_8888");
        put(ImageFormat.HEIC, "HEIC");
        put(ImageFormat.NV16, "NV16");
        put(ImageFormat.NV21, "NV21");
        put(ImageFormat.PRIVATE, "PRIVATE");
        put(ImageFormat.RAW10, "RAW10");
        put(ImageFormat.RAW12, "RAW12");
        put(ImageFormat.RAW_PRIVATE, "RAW_PRIVATE");
        put(ImageFormat.RAW_SENSOR, "RAW_SENSOR");
        put(ImageFormat.RGB_565, "RGB_565");
        put(ImageFormat.UNKNOWN, "UNKNOWN");
        put(ImageFormat.Y8, "Y8");
//        put(ImageFormat.YCBCR_P010, "YCBCR_P010");
        put(ImageFormat.YUV_420_888, "YUV_420_888");
        put(ImageFormat.YUV_422_888, "YUV_422_888");
        put(ImageFormat.YUV_444_888, "YUV_444_888");
        put(ImageFormat.YUY2, "YUY2");
        put(ImageFormat.YV12, "YV12");
    }};

    private final static HashMap<Integer, String> mLensFacingMap = new HashMap<Integer, String>() {{
        put(CameraMetadata.LENS_FACING_FRONT, "LENS_FACING_FRONT");
        put(CameraMetadata.LENS_FACING_BACK, "LENS_FACING_BACK");
        put(CameraMetadata.LENS_FACING_EXTERNAL, "LENS_FACING_EXTERNAL");
    }};

    private final static HashMap<Integer, String> mEffectModeMap = new HashMap<Integer, String>() {{
        put(CameraMetadata.CONTROL_EFFECT_MODE_OFF, "OFF");
        put(CameraMetadata.CONTROL_EFFECT_MODE_MONO, "MONO");
        put(CameraMetadata.CONTROL_EFFECT_MODE_NEGATIVE, "NEGATIVE");
        put(CameraMetadata.CONTROL_EFFECT_MODE_SOLARIZE, "SOLARIZE");
        put(CameraMetadata.CONTROL_EFFECT_MODE_SEPIA, "SEPIA");
        put(CameraMetadata.CONTROL_EFFECT_MODE_POSTERIZE, "POSTERIZE");
        put(CameraMetadata.CONTROL_EFFECT_MODE_WHITEBOARD, "WHITEBOARD");
        put(CameraMetadata.CONTROL_EFFECT_MODE_BLACKBOARD, "BLACKBOARD");
        put(CameraMetadata.CONTROL_EFFECT_MODE_AQUA, "AQUA");
    }};

    private final static HashMap<Integer, String> mControlModeMap = new HashMap<Integer, String>() {{
        put(CameraMetadata.CONTROL_MODE_OFF, "OFF");
        put(CameraMetadata.CONTROL_MODE_AUTO, "AUTO");
        put(CameraMetadata.CONTROL_MODE_USE_SCENE_MODE, "USE_SCENE_MODE");
        put(CameraMetadata.CONTROL_MODE_OFF_KEEP_STATE, "OFF_KEEP_STATE");
//        put(CameraMetadata.CONTROL_MODE_USE_EXTENDED_SCENE_MODE, "USE_EXTENDED_SCENE_MODE");
    }};

    private final static HashMap<Integer, String> mSceneModeMap = new HashMap<Integer, String>() {{
        put(CameraMetadata.CONTROL_SCENE_MODE_DISABLED, "DISABLED");
        put(CameraMetadata.CONTROL_SCENE_MODE_FACE_PRIORITY, "FACE_PRIORITY");
        put(CameraMetadata.CONTROL_SCENE_MODE_ACTION, "ACTION");
        put(CameraMetadata.CONTROL_SCENE_MODE_PORTRAIT, "PORTRAIT");
        put(CameraMetadata.CONTROL_SCENE_MODE_LANDSCAPE, "LANDSCAPE");
        put(CameraMetadata.CONTROL_SCENE_MODE_NIGHT, "NIGHT");
        put(CameraMetadata.CONTROL_SCENE_MODE_NIGHT_PORTRAIT, "NIGHT_PORTRAIT");
        put(CameraMetadata.CONTROL_SCENE_MODE_THEATRE, "THEATRE");
        put(CameraMetadata.CONTROL_SCENE_MODE_BEACH, "BEACH");
        put(CameraMetadata.CONTROL_SCENE_MODE_SNOW, "SNOW");
        put(CameraMetadata.CONTROL_SCENE_MODE_SUNSET, "SUNSET");
        put(CameraMetadata.CONTROL_SCENE_MODE_STEADYPHOTO, "STEADYPHOTO");
        put(CameraMetadata.CONTROL_SCENE_MODE_FIREWORKS, "FIREWORKS");
        put(CameraMetadata.CONTROL_SCENE_MODE_SPORTS, "SPORTS");
        put(CameraMetadata.CONTROL_SCENE_MODE_PARTY, "PARTY");
        put(CameraMetadata.CONTROL_SCENE_MODE_CANDLELIGHT, "CANDLELIGHT");
        put(CameraMetadata.CONTROL_SCENE_MODE_BARCODE, "BARCODE");
//        put(CameraMetadata.CONTROL_SCENE_MODE_HIGH_SPEED_VIDEO, "HIGH_SPEED_VIDEO");
        put(CameraMetadata.CONTROL_SCENE_MODE_HDR, "HDR");
    }};

    private final static HashMap<Integer, String> mControlCaptureIntentMap = new HashMap<Integer, String>() {{
        put(CameraMetadata.CONTROL_CAPTURE_INTENT_CUSTOM, "CUSTOM");
        put(CameraMetadata.CONTROL_CAPTURE_INTENT_PREVIEW, "PREVIEW");
        put(CameraMetadata.CONTROL_CAPTURE_INTENT_STILL_CAPTURE, "STILL CAPTURE");
        put(CameraMetadata.CONTROL_CAPTURE_INTENT_VIDEO_RECORD, "VIDEO RECORD");
        put(CameraMetadata.CONTROL_CAPTURE_INTENT_VIDEO_SNAPSHOT, "VIDEO SNAPSHOT");
        put(CameraMetadata.CONTROL_CAPTURE_INTENT_ZERO_SHUTTER_LAG, "ZERO SHUTTER LAG");
        put(CameraMetadata.CONTROL_CAPTURE_INTENT_MANUAL, "MANUAL");
        put(CameraMetadata.CONTROL_CAPTURE_INTENT_MOTION_TRACKING, "MOTION TRACKING");
    }};

    private final static HashMap<Integer, String> mAwbModeMap = new HashMap<Integer, String>() {{
        put(CameraMetadata.CONTROL_AWB_MODE_OFF, "OFF");
        put(CameraMetadata.CONTROL_AWB_MODE_AUTO, "AUTO");
        put(CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT, "INCANDESCENT");
        put(CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT, "FLUORESCENT");
        put(CameraMetadata.CONTROL_AWB_MODE_WARM_FLUORESCENT, "WARM FLUORESCENT");
        put(CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT, "DAYLIGHT");
        put(CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT, "CLOUDY DAYLIGHT");
        put(CameraMetadata.CONTROL_AWB_MODE_TWILIGHT, "TWILIGHT");
        put(CameraMetadata.CONTROL_AWB_MODE_SHADE, "SHADE");
    }};

    private final static HashMap<Integer, String> mAwbStateMap = new HashMap<Integer, String>() {{
        put(CameraMetadata.CONTROL_AWB_STATE_INACTIVE, "INACTIVE");
        put(CameraMetadata.CONTROL_AWB_STATE_SEARCHING, "SEARCHING");
        put(CameraMetadata.CONTROL_AWB_STATE_CONVERGED, "CONVERGED");
        put(CameraMetadata.CONTROL_AWB_STATE_LOCKED, "LOCKED");
    }};

    private final static HashMap<CameraCharacteristics.Key<?>, HashMap<Integer, String>> mMainKeyMap = new HashMap<CameraCharacteristics.Key<?>, HashMap<Integer, String>>() {{
        put(CameraCharacteristics.LENS_FACING, mLensFacingMap);
        put(CameraCharacteristics.CONTROL_AVAILABLE_EFFECTS, mEffectModeMap);
        put(CameraCharacteristics.CONTROL_AVAILABLE_MODES, mControlModeMap);
        put(CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES, mSceneModeMap);
    }};

//    private static String getMapString(HashMap<Integer, String> map, Object key) {
//        return map.getOrDefault(key, "-----");
//    }

    private static String getMapStrings(HashMap<Integer, String> map, int [] keys) {
        StringBuilder builder = new StringBuilder("[ ");
        for (Integer key : keys) {
            builder.append(map.get(key));
            builder.append(" ");
        }
        builder.append("]");
        return builder.toString();
    }

    public static String valueToString(CameraCharacteristics.Key<?> mainKey, int [] keys) {
        if (mMainKeyMap.containsKey(mainKey)) {
            return getMapStrings(mMainKeyMap.get(mainKey), keys);
        }

        return "[]";
    }

    public static String valueToString(CameraCharacteristics.Key<?> mainKey, int key) {
        HashMap<Integer, String> map = mMainKeyMap.getOrDefault(mainKey, null);
        if (map != null) {
            return map.getOrDefault(key, "-----");
        }
        return "-----";
    }

    public static String ImageFormatToString(int format) {
        return mImageFormatMap.getOrDefault(format, "-----");
    }
    public static String SceneMode(int mode) {
        return mSceneModeMap.getOrDefault(mode, "-----");
    }
    public static String ControlMode(int mode) {
        return mControlModeMap.getOrDefault(mode, "-----");
    }
    public static String ControlCaptureIntent(int mode) {
        return mControlCaptureIntentMap.getOrDefault(mode, "-----");
    }
    public static String AwbMode(int mode) {
        return mAwbModeMap.getOrDefault(mode, "-----");
    }
    public static String AwbState(int state) {
        return mAwbStateMap.getOrDefault(state, "-----");
    }
}
