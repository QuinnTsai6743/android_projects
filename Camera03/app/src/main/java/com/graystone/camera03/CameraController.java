package com.graystone.camera03;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.ColorSpaceTransform;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.RggbChannelVector;
import android.hardware.camera2.params.SessionConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.graystone.camerautil.AeStatistic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;

public class CameraController implements Executor, IEdgeMode, INoiseReduction, ICameraControl {
    public interface StateCallback {
        void onCameraOpened();
        void onCameraError();
        void onSessionConfigured();
        void onSessionConfigureFailed();
        void onPermissionDenied();
        void onUpdateExposureInfo(CapResult capResult);
        void onCaptureComplete(Long exposureTime, Integer ISO, Long frameNo);
    }

    public static class CameraAttrib {
        private final String mCameraId;
        private final CameraCharacteristics mCharacteristics;

        CameraAttrib(String cameraId, CameraCharacteristics characteristics) {
            mCameraId = cameraId;
            if (characteristics == null) {
                Log.e(TAG, "CameraCharacteristics is null!");
            }
            mCharacteristics = characteristics;
        }

        public String getCameraId() {
            return mCameraId;
        }

        public String getLensFacing() {
            if (mCharacteristics != null) {
                Object obj = mCharacteristics.get(CameraCharacteristics.LENS_FACING);
                if (obj != null) {
                    Integer lensFacing = (Integer) obj;
//                    Log.d(TAG, "lens facing = " + lensFacing);
                    return CameraInfo.valueToString(CameraCharacteristics.LENS_FACING, lensFacing);
                }
            }
            return "null object";
        }

        public Rect getSensorActiveArraySize() {
            return mCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        }

        public int getSensorColorFilter() {
            return mCharacteristics.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT);
        }

        public StreamConfigurationMap getStreamConfigurationMap() {
            return mCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        }

        public Size maximumSize(int format, @NonNull Size defaultSize) {
            StreamConfigurationMap streamMap = mCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (streamMap != null) {
                int[] formats = streamMap.getOutputFormats();
                for (int f : formats) {
                    if (f == format) {
                        Size[] sizes = streamMap.getOutputSizes(format);
                        return sizes[0];
                    }
                }
            }
            return defaultSize;
        }

        public void dumpAllSupportedSize() {
            StreamConfigurationMap streamMap = mCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (streamMap != null) {
                Log.i(TAG, "Supported formats and sizes:");
                int[] formats = streamMap.getOutputFormats();
                for (int f : formats) {
                    Log.i(TAG, "  " + CameraInfo.ImageFormatToString(f));
                    Size[] sizes = streamMap.getOutputSizes(f);
                    for (Size s : sizes) {
                        Log.i(TAG, String.format("    %d x %d", s.getWidth(), s.getHeight()));
                    }
                }
            }
        }

        public void dumpExposureMeteringModes() {
            int[] modes = mCharacteristics.get(mExposureMeteringKey);
            if (modes != null) {
                Log.d(TAG, String.format("Num of Metering Modes: %d", modes.length));
                StringBuilder builder = new StringBuilder("Metering Modes: ");
                for (int m : modes) {
                    builder.append(String.format(Locale.US, "%d ", m));
                }
                Log.d(TAG, builder.toString());
            }
        }

        public Range<Integer> getSensitivityRange() {
            return mCharacteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
        }

        public Range<Long> getExposureTimeRange() {
            return mCharacteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
        }

        public Integer getMaxRegionsAwb() {
            return mCharacteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AWB);
        }

        public Integer getMaxRegionsAe() {
            return mCharacteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE);
        }

        public Boolean isFlashAvailable() {
            return mCharacteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
        }

        public void dumpDistortionCorrectionMode() {
            int [] modes = mCharacteristics.get(CameraCharacteristics.DISTORTION_CORRECTION_AVAILABLE_MODES);
            if (modes == null) {
                Log.i(TAG, "No Distortion Correction");
            }
            else {
                Log.i(TAG, String.format("Supported Distortion Correction mode (%d)", modes.length));
                for (int ii : modes) {
                    Log.i(TAG, String.format("  %d", ii));
                }
            }
        }
    }

    private static final String TAG = "Camera03";
    private static final String OneShotTag = "OneShot";
    private static final String LiveViewTag = "Live-View";
    private static final String SnapShotTag = "SnapShot";
    private static final long CAMERA_CLOSE_TIMEOUT = 2000; // ms
    private final Handler mCameraHandler;
    private CameraManager mCameraManager = null;
    private CameraDevice mCameraDevice = null;
    private String mCameraId;
    private CameraCaptureSession mCameraSession;

    private boolean mAeOn = true;
    private int mIso = 100;
    private long mExposureTime = 30000000L;  // 15 millisecond
    private MeteringRectangle mAeRegions[] = new MeteringRectangle[] {
//            new MeteringRectangle(179, 179, 360, 360, 1000),
            new MeteringRectangle(309, 309, 100, 100, MeteringRectangle.METERING_WEIGHT_MAX)
    };

    private final ConditionVariable mCloseWaiter = new ConditionVariable();
    private final StateCallback mCallback;

    private Integer mEdgeMode = CaptureRequest.EDGE_MODE_OFF;
    private Integer mNoiseReductionMode = CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY;

    private boolean mFixedWBGains = false;
    private RggbChannelVector mWbGains;

    private boolean mSceneMode = false;

    private boolean mDumpCaptureRequestTags = false;

    private int [] mColorMatrixElements = null;
    private final ColorCorrectionController mColorCorrectionController = new ColorCorrectionController();

    private final ToneCurveController mToneMapCurveController = new ToneCurveController();
    private int mBrightnessLevel = 0;
    private int mContrastLevel = 0;

    private int mSharpnessLevel = 0;
    private static final CaptureRequest.Key<int[]> mSharpnessStrengthKey = new CaptureRequest.Key<>("org.codeaurora.qcamera3.sharpness.strength", int[].class);

    private int mSaturationLevel = 5;
    private static final CaptureRequest.Key<int[]> mUseSaturationKey = new CaptureRequest.Key<>("org.codeaurora.qcamera3.saturation.use_saturation", int[].class);
    private static final CaptureRequest.Key<int[]> mSaturationRangeKey = new CaptureRequest.Key<>("org.codeaurora.qcamera3.saturation.range", int[].class);

    private static final CaptureRequest.Key<int[]> mContrastLevelKey = new CaptureRequest.Key<>("org.codeaurora.qcamera3.contrast.level", int[].class);

    private static final CameraCharacteristics.Key<int[]> mExposureMeteringKey = new CameraCharacteristics.Key<>("org.codeaurora.qcamera3.exposure_metering.available_modes", int[].class);

    private static final CaptureRequest.Key<int[]> mExposureMeteringModeKey = new CaptureRequest.Key<>("org.codeaurora.qcamera3.exposure_metering.exposure_metering_mode", int[].class);

    private float mAnrIntensity = 0.0f;
    private static final CaptureRequest.Key<float[]> mAnrIntensityKey = new CaptureRequest.Key<>("org.quic.camera.anr_tuning.anr_intensity", float[].class);
    private float mAnrMotionSensitivity = 0.0f;
    private static final CaptureRequest.Key<float[]> mAnrMotionSensitivityKey = new CaptureRequest.Key<>("org.quic.camera.anr_tuning.anr_motion_sensitivity", float[].class);


    CameraController(StateCallback callback) {
        HandlerThread cameraThread = new HandlerThread("cameraThread");
        cameraThread.start();
        mCameraHandler = new Handler(cameraThread.getLooper());
        mCallback = callback;
    }

    private boolean checkCameraPermission(Context context) {
        int permissionState = ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA);
        if (permissionState != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "CAMERA permission has NOT been granted.");
            return false;
        }
        else {
            return true;
        }
    }

    public ArrayList<CameraAttrib> findCamera(Context context) {
        ArrayList<CameraAttrib> arrayList = new ArrayList<CameraAttrib>();

        if (checkCameraPermission(context)) {
            mCameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            if (mCameraManager != null) {
                try {
                    String[] cameraIds = mCameraManager.getCameraIdList();
                    for (String id : cameraIds) {
                        arrayList.add(new CameraAttrib(id, mCameraManager.getCameraCharacteristics(id)));
                    }
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                    mCallback.onPermissionDenied();
                }
            }
        }
        return arrayList;
    }

    public void openCamera(String cameraId) throws SecurityException {
        Log.d(TAG, "openCamera()");
        mCameraId = cameraId;
        mCameraHandler.post(() -> {
            if (mCameraDevice != null) {
                throw new IllegalStateException("Camera already open");
            }
            try {
                mCameraManager.openCamera(mCameraId, mCameraDeviceListener, mCameraHandler);
            }
            catch (CameraAccessException e) {
                e.printStackTrace();
                mCallback.onPermissionDenied();
            }
        });
    }

    public boolean isCameraOpened() {
        return (mCameraDevice != null);
    }

    private long toNanosecond(long ms) {
        return ms * 1000000L;
    }

    public void lockAE(boolean lock) {
        mAeOn = !lock;
        buildAndSendLiveViewRequest();
    }

    /**
     * ICameraControl
     * Get the AE status.
     * @return Ture if AE is enabled.
     */
    @Override
    public boolean autoExposureEnabled() {
        return mAeOn;
    }

    /**
     * ICameraControl
     * Set the ISO value
     * @param iso ISO value.
     */
    @Override
    public void setIso(int iso) {
        if (iso != mIso) {
            mIso = iso;
            buildAndSendLiveViewRequest();
        }
    }

    /**
     * ICameraControl
     * Get the current ISO value.
     * @return ISO value.
     */
    @Override
    public int getIso() {
        return mIso;
    }

    @Override
    public void setExposureTime(long nanosecond) {
        if (mExposureTime != nanosecond) {
            mExposureTime = nanosecond;
            buildAndSendLiveViewRequest();
        }
    }

    /**
     * ICameraControl
     * Get the current exposure time. (unit is nanoseconds)
     * @return The exposure time.
     */
    @Override
    public long getExposureTime() {
        return mExposureTime;
    }

    /**
     * ICameraControl
     * @param level The new saturation level.
     */
    @Override
    public void setSaturationLevel(int level) {
        Log.d(TAG, "[Camera] set saturation: " + level);
        mSaturationLevel = level;
        buildAndSendLiveViewRequest();
    }

    /**
     * ICameraControl
     * @param level The new brightness level.
     */
    @Override
    public void setBrightnessLevel(int level) {
//        Log.d(TAG, String.format("[Camera] brightness = %d", level));
//        mBrightnessLevel = level;
        Log.d(TAG, String.format("[Camera] shaprness = %d", level));
        mSharpnessLevel = level;
        buildAndSendLiveViewRequest();
    }

    /**
     * ICameraControl
     * @param level The new contrast level.
     */
    @Override
    public void setContrastLevel(int level) {
        Log.d(TAG, String.format("[Camera] contrast = %d", level));
        mContrastLevel = level;
        buildAndSendLiveViewRequest();
    }

    /**
     * ICameraControl
     * @param brightness The brightness level.
     * @param contrast The contrast level.
     */
    @Override
    public void setBrightnessAndContrast(int brightness, int contrast) {
        Log.d(TAG, String.format("[Camera] brightness = %d, contrast = %d", brightness, contrast));
        mBrightnessLevel = brightness;
        mContrastLevel = contrast;
        buildAndSendLiveViewRequest();
    }

    /**
     * Close the camera and wait for the close callback to be called in the camera thread.
     * Times out after @{value CAMERA_CLOSE_TIMEOUT} ms.
     */
    public void closeCameraAndWait() {
        Log.d(TAG, "closeCameraAndWait()");
        mCloseWaiter.close();
        mCameraHandler.post(new Runnable() {
            public void run() {
                if (mCameraDevice != null) {
                    mCameraDevice.close();
                }
                mCameraDevice = null;
                mCameraSession = null;
            }
        });
        boolean closed = mCloseWaiter.block(CAMERA_CLOSE_TIMEOUT);
        if (!closed) {
            Log.e(TAG, "Timeout closing camera");
        }
    }

    public void startCameraSession(ArrayList<OutputConfiguration> outputList) {
        Log.d(TAG, "startCameraSession()");
        if (mCameraDevice == null) {
            Log.d(TAG, "mCameraDevice is NULL.");
            return;
        }

        try {
            SessionConfiguration config = new SessionConfiguration(
                    SessionConfiguration.SESSION_REGULAR,
                    outputList,
                    this,
                    mCameraSessionListener);

            // Test EIS and LDC
            CaptureRequest.Builder builder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

//            CaptureRequest.Key<int[]> EisEnableKey = new CaptureRequest.Key<>("org.codeaurora.qcamera3.sessionParameters.EisEnable", int[].class);
//            builder.set(EisEnableKey, new int [] {1});

//            CaptureRequest.Key<int []> LdcEnableKey = new CaptureRequest.Key<>("org.codeaurora.qcamera3.sessionParameters.LDCEnable", int[].class);
//            builder.set(LdcEnableKey, new int [] {1});

            config.setSessionParameters(builder.build());

            mCameraDevice.createCaptureSession(config);
        }
        catch (CameraAccessException e) {
            e.printStackTrace();
            mCameraDevice.close();
            mCameraDevice = null;
            mCallback.onPermissionDenied();
        }
    }

    public void abortCaptures() {
        Log.d(TAG, "----- abortCaptures() -----");
        try {
            mCameraSession.abortCaptures();
        }
        catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void capture(Surface surface) {
        Log.i(TAG, "-- capture() --");
        try {
            CaptureRequest.Builder builder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(surface);
            builder.setTag("Live-view");
            builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
            builder.set(CaptureRequest.SENSOR_SENSITIVITY, 400);    // ISO 400
            builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, 30*1000000000L);   // 30 milliseconds
            builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO);
            mCameraSession.capture(builder.build(), mCaptureCallback, mCameraHandler);
        }
        catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void applyLiveViewRequest(CaptureRequest.Builder builder) {
        if (mSceneMode) {
            builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_USE_SCENE_MODE);
            builder.set(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_NIGHT);
            Log.d(TAG, "set scene mode to NIGHT");
        }
        else {
//                    builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_USE_SCENE_MODE);
//                    builder.set(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_DISABLED);
//                    mCameraSession.setRepeatingRequest(builder.build(), mCaptureCallback, mCameraHandler);
//                    try {
//                        Thread.sleep(1000);
//                    }
//                    catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
            Log.d(TAG, "Control Mode: Auto");
            builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
        }
        if (mAeOn) {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            builder.set(CaptureRequest.CONTROL_AE_REGIONS, mAeRegions);
        }
        else {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
            builder.set(CaptureRequest.SENSOR_SENSITIVITY, mIso);
            builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, mExposureTime);
        }
        // awb mode
        if (!mFixedWBGains) {
            builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO);
        }
        else {
            builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF);
            builder.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX);
            builder.set(CaptureRequest.COLOR_CORRECTION_GAINS, mWbGains);
//            if (mColorMatrixElements != null) {
//                float saturation = 1.0f + (mSaturationLevel / 5.0f);
//                ColorSpaceTransform colorSpaceTransform = new ColorSpaceTransform(mColorCorrectionController.getAdaptedColorSpaceTransform(mColorMatrixElements, saturation));
////                builder.set(CaptureRequest.COLOR_CORRECTION_TRANSFORM, ColorCorrectionController.getColorSpaceTransform(mSaturationLevel));
//                builder.set(CaptureRequest.COLOR_CORRECTION_TRANSFORM, colorSpaceTransform);
//            }
        }

        // edge mode
        builder.set(CaptureRequest.EDGE_MODE, mEdgeMode);
        // noise reduction mode
        builder.set(CaptureRequest.NOISE_REDUCTION_MODE, mNoiseReductionMode);
        builder.set(mAnrIntensityKey, new float[] {mAnrIntensity});
        builder.set(mAnrMotionSensitivityKey, new float[] {mAnrMotionSensitivity});

        // Tone Map Curve
        builder.set(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_CONTRAST_CURVE);
        builder.set(CaptureRequest.TONEMAP_CURVE, mToneMapCurveController.getToneMapCurve(mBrightnessLevel, mContrastLevel));
//        builder.set(mContrastLevelKey, new int [] {mContrastLevel});

        // Testing sharpness strength
        builder.set(mSharpnessStrengthKey, new int [] {mSharpnessLevel});

        // Testing UseSaturation
        builder.set(mUseSaturationKey, new int [] {mSaturationLevel});

        // Testing EIS
//        CaptureRequest.Key<int[]> EisEnableKey = new CaptureRequest.Key<>("org.codeaurora.qcamera3.sessionParameters.EisEnable", int[].class);
//        builder.set(EisEnableKey, new int [] {0});
//
//        CaptureRequest.Key<int []> LdcEnableKey = new CaptureRequest.Key<>("org.codeaurora.qcamera3.sessionParameters.LDCEnable", int[].class);
//        builder.set(LdcEnableKey, new int [] {0});

        // Exposure Metering Mode
//        builder.set(mExposureMeteringModeKey, new int [] {1});
    }

    class LiveViewRunnable implements Runnable {
        private final ArrayList<Surface> mSurfaceList;
        LiveViewRunnable(ArrayList<Surface> surfaceList) {
            mSurfaceList = surfaceList;
        }

        void dumpIntValues(int [] values) {
            StringBuilder strBuilder = new StringBuilder(String.format(Locale.US, " (size: %d)  ", values.length));
            for (int v : values) {
                strBuilder.append(String.format(Locale.US, "%d ", v));
            }
            Log.i(TAG, strBuilder.toString());
        }

        void dumpByteValues(byte [] values) {
            StringBuilder strBuilder = new StringBuilder(String.format(Locale.US, " (size: %d)  ", values.length));
            for (byte v : values) {
                strBuilder.append(String.format(Locale.US, "0x%02x ", v));
            }
            Log.i(TAG, strBuilder.toString());
        }

        @Override
        public void run() {
            Log.i(TAG, "setRepeatingRequest()");
            try {
                CaptureRequest.Builder builder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                for (Surface surface: mSurfaceList) {
                    builder.addTarget(surface);
                }
                builder.setTag(LiveViewTag);
                applyLiveViewRequest(builder);
                CaptureRequest request = builder.build();
                if (mDumpCaptureRequestTags) {
                    List<CaptureRequest.Key<?>> keyList = request.getKeys();
                    Log.i(TAG, String.format("----- get %d keys ---------------", keyList.size()));
                    for (CaptureRequest.Key<?> k : keyList) {
                        Object o = request.get(k);
                        Log.i(TAG, " Key: " + k.getName() + String.format("   Value (%s): %s", o.getClass().getSimpleName(), o.toString()));
                        if (o.getClass().getSimpleName().equals(int[].class.getSimpleName())) {
                            dumpIntValues((int[]) o);
                        } else if (o.getClass().getSimpleName().equals(byte[].class.getSimpleName())) {
                            dumpByteValues((byte[]) o);
                        }
                    }
                }
                mCameraSession.setRepeatingRequest(request, mCaptureCallback, mCameraHandler);
            }
            catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

    private LiveViewRunnable mLiveViewRunnable;

    public void startLiveView(ArrayList<Surface> surfaceList) {
        Log.d(TAG, "startLiveView()");
        mLiveViewRunnable = new LiveViewRunnable(surfaceList);
        mCameraHandler.post(mLiveViewRunnable);
    }

    private void buildAndSendLiveViewRequest() {
        if (mCameraDevice != null) {
            mCameraHandler.post(mLiveViewRunnable);
        }
    }

    class SnapshotRunnable implements Runnable {
        private final Surface mSurface;
        SnapshotRunnable(Surface snapshotSurface) {
            mSurface = snapshotSurface;
        }
        @Override
        public void run() {
            try {
                CaptureRequest.Builder builder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                builder.addTarget(mSurface);
                builder.setTag(SnapShotTag);

                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
                builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, mExposureTime);    // nanoseconds
                builder.set(CaptureRequest.SENSOR_SENSITIVITY, mIso);    // ISO 100
                mCameraSession.capture(builder.build(), mCaptureCallback, mCameraHandler);
            }
            catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }
    public void takeSnapshot(Surface snapshotSurface) {
        Log.d(TAG, "takeSnapShot()");
        mCameraHandler.post(new SnapshotRunnable(snapshotSurface));
    }

    public void takeSnapshotEx(Surface snapshotSurface, Integer iso, Long exposureTime) {
        Log.i(TAG, "takeSnapshotEx");
        mIso = iso;
        mExposureTime = exposureTime;
        mCameraHandler.post(new SnapshotRunnable(snapshotSurface));
    }

    /**
     * OneShot capture.
     *
     */
    class OneShotRunnable implements Runnable {
        private final Surface mSurface;

        OneShotRunnable(Surface surface) {
            mSurface = surface;
        }

        @Override
        public void run() {
            try {
                CaptureRequest.Builder builder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                builder.addTarget(mSurface);
                builder.setTag(OneShotTag);

                applyLiveViewRequest(builder);
//                mCameraSession.capture(builder.build(), mCaptureCallback, mCameraHandler);
                mCameraSession.setRepeatingRequest(builder.build(), mCaptureCallback, mCameraHandler);
            }
            catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

    public void oneShotRequest(Surface snapshotSurface) {
        Log.i(TAG, "[CameraController] oneshotRequest");
        mCameraHandler.post(new OneShotRunnable(snapshotSurface));
    }

    public void setSceneMode(int mode) {
        if (mode == 0) {
            mSceneMode = false;
        }
        else {
            mSceneMode = true;
        }
        buildAndSendLiveViewRequest();
    }

    public int getSceneMode() {
        if (mSceneMode) {
            return 1;
        }
        else {
            return 0;
        }
    }

    private final CameraDevice.StateCallback mCameraDeviceListener = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            Log.d(TAG, "CameraDevice.StateCallback.onOpened()");
            mCameraDevice = camera;
            mCallback.onCameraOpened();
        }

        @Override
        public void onClosed(CameraDevice camera) {
            Log.d(TAG, "CameraDevice.StateCallback.onClosed()");
            mCloseWaiter.open();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            Log.d(TAG, "CameraDevice.StateCallback.onDisconnected()");
            camera.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            Log.e(TAG, "CameraDevice.StateCallback.onError(), error=" + error);
            camera.close();
            mCameraDevice = null;
            mCallback.onCameraError();
        }
    };

    private final CameraCaptureSession.StateCallback mCameraSessionListener = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            mCameraSession = session;
            mCameraHandler.post(() -> {
               if (null == mCameraDevice) {
                   return;
               }
               mCallback.onSessionConfigured();
            });
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            Log.e(TAG, "onConfigureFailed()");
            if (mCameraDevice != null) {
                mCameraDevice.close();
            }
            mCameraDevice = null;
            mCallback.onSessionConfigureFailed();
        }

        @Override
        public void onClosed(CameraCaptureSession session) {
            Log.i(TAG, "session onClosed()");
        }

        @Override
        public void onReady(CameraCaptureSession session) {
            Log.i(TAG, "session onReady()");
        }
    };

    /**
     * Camera Session Capture Callback handler
     *
     */
    private final CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        static final long MICRO_SECOND = 1000;
        static final long MILLI_SECOND = MICRO_SECOND * 1000;
        static final long ONE_SECOND = MILLI_SECOND * 1000;

        String toExposureText(Long exposureTime) {
            String exposureText;
            if (exposureTime > ONE_SECOND) {
                exposureText = String.format(Locale.US, "%.2f s", exposureTime / 1e9);
            }
            else if (exposureTime > MILLI_SECOND) {
                exposureText = String.format(Locale.US, "%.2f ms", exposureTime / 1e6);
            }
            else if (exposureTime > MICRO_SECOND) {
                exposureText = String.format(Locale.US, "%.2f us", exposureTime / 1e3);
            }
            else {
                exposureText = String.format(Locale.US, "%d ns", exposureTime);
            }
            return exposureText;
        }

        @Override
        public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
            super.onCaptureStarted(session, request, timestamp, frameNumber);
//            Log.i(TAG, "onCaptureStarted()");
        }

        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            String tag = (String)request.getTag();
            if (tag != null) {
                if (SnapShotTag.equals(tag)) {
                    Log.i(TAG, "Snapshot completed! frame no.= " + result.getFrameNumber());
                    Long exposureTime = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
                    Integer gain = result.get(CaptureResult.SENSOR_SENSITIVITY);
                    Log.i(TAG, "Exposure Time = " + toExposureText(exposureTime));
                    Log.i(TAG, String.format("ISO = %d", gain));
                    mCallback.onCaptureComplete(exposureTime, gain, result.getFrameNumber());
                }
                else if (LiveViewTag.equals(tag)) {
                    AeStatistic.log(result, "CAP_STAT", 10);
                    long frameNo = result.getFrameNumber();
                    if (frameNo % 10 == 0) {
                        Long exposureTime = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
                        Integer iso = result.get(CaptureResult.SENSOR_SENSITIVITY);
                        CapResult.Builder builder = new CapResult.Builder()
                                .setIso(iso)
                                .setExposureTime(exposureTime)
                                .setFrameNo(frameNo).setWbGains(result.get(CaptureResult.COLOR_CORRECTION_GAINS))
                                .setColorTransform(result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM))
                                .setAwbState(result.get(CaptureResult.CONTROL_AWB_STATE));
                        mCallback.onUpdateExposureInfo(builder.build());

                        if (mExposureTime != exposureTime) {
                            mExposureTime = exposureTime;
                        }
                        if (mIso != iso) {
                            mIso = iso;
                        }
//                        Log.i(TAG, "Frame no. " + frameNo + " | iso:" + iso + " exp.:" + exposureTime);
                    }

                    // for Contrast and Saturation adjustment
                    if (mColorMatrixElements == null) {
                        ColorSpaceTransform colorSpaceTransform = result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM);
                        if (colorSpaceTransform != null) {
                            mColorMatrixElements = new int[18];
                            colorSpaceTransform.copyElements(mColorMatrixElements, 0);
                        }
                    }
                }
                else if (OneShotTag.equals(tag)) {
                    Log.i(TAG, "OneShot completed! frame No.= " + result.getFrameNumber());
                }
            }
        }

        @Override
        public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request, CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);
            Log.e(TAG, "onCaptureFailed()");
            String tag = (String)request.getTag();
            if (tag != null) {
                Log.i(TAG, "request tag = " + tag);
            }
            long frameNo = failure.getFrameNumber();
            int sequenceId = failure.getSequenceId();
            boolean wasCaptured = failure.wasImageCaptured();
            Log.e(TAG, String.format("frame No.=%d, sequence id = %d, wasCaptured = %s", frameNo, sequenceId, (wasCaptured)? "TRUE" : "FALSE"));
            Log.e(TAG, String.format("reason = %s", (failure.getReason() == CaptureFailure.REASON_ERROR)? "ERROR" : "FLUSHED"));
        }

        @Override
        public void onCaptureBufferLost(CameraCaptureSession session, CaptureRequest request, Surface target, long frameNumber) {
//            Log.e(TAG, String.format("onCaptureBufferLost(), frameNumber=%d", frameNumber));
        }

        @Override
        public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request, CaptureResult partialResult) {
//            Log.i(TAG, "onCaptureProgressed()");
        }

        @Override
        public void onCaptureSequenceAborted(CameraCaptureSession session, int sequenceId) {
            Log.i(TAG, String.format("onCaptureSequenceAborted() sequenceId=%d", sequenceId));
        }
    };

    @Override
    public void execute(Runnable command) {
        mCameraHandler.post(command);
    }

    HashMap<String, Integer> mEdgeModeMap = new HashMap<String, Integer>() {{
        put("OFF", CaptureRequest.EDGE_MODE_OFF);
        put("FAST", CaptureRequest.EDGE_MODE_FAST);
        put("HIGH_QUALITY", CaptureRequest.EDGE_MODE_HIGH_QUALITY);
        put("ZERO_SHUTTER_LAG", CaptureRequest.EDGE_MODE_ZERO_SHUTTER_LAG);
    }};

    HashMap<String, Integer> mNoiseReductionMap = new HashMap<String, Integer>() {{
        put("OFF", CaptureRequest.NOISE_REDUCTION_MODE_OFF);
        put("FAST", CaptureRequest.NOISE_REDUCTION_MODE_FAST);
        put("HIGH_QUALITY", CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY);
        put("MINIMAL", CaptureRequest.NOISE_REDUCTION_MODE_MINIMAL);
        put("ZERO_SHUTTER_LAG", CaptureRequest.NOISE_REDUCTION_MODE_ZERO_SHUTTER_LAG);
    }};

    @Override
    public void setEdgeMode(String mode) {
        if (mEdgeModeMap.containsKey(mode)) {
            mEdgeMode = mEdgeModeMap.get(mode);
            buildAndSendLiveViewRequest();
        }
        else {
            Log.e(TAG, "Invalid edge mode: " + mode);
        }
    }

    public String[] getSupportEdgeMode() {
//        return mEdgeModeMap.keySet().toArray(new String[0]);
        return new String[]{"OFF", "FAST", "HIGH_QUALITY", "ZERO_SHUTTER_LAG"};
    }

    @Override
    public void setNoiseReduction(String mode) {
        if (mNoiseReductionMap.containsKey(mode)) {
            mNoiseReductionMode = mNoiseReductionMap.get(mode);
            buildAndSendLiveViewRequest();
        }
        else {
            Log.e(TAG, "Invalid noise reduction mode: " + mode);
        }
    }

    public String[] getSupportNoiseReductionMode() {
//        return mNoiseReductionMap.keySet().toArray(new String[0]);
        return new String[]{"OFF", "FAST", "HIGH_QUALITY", "MINIMAL", "ZERO_SHUTTER_LAG"};
    }

    public void setNoiseReduction(boolean enable) {
        mNoiseReductionMode = (enable)? CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY : CaptureRequest.NOISE_REDUCTION_MODE_OFF;
        buildAndSendLiveViewRequest();
    }

    public void setNoiseReductionLevel(float value) {
        Log.d(TAG, String.format("[CameraControl] NR level=%f", value));
        mAnrIntensity = (value * 20.0f) / 16.0f;
        mAnrMotionSensitivity = (value * 10.0f) / 8.0f;
        Log.d(TAG, String.format("[CameraControl] ANR intensity=%f, motion sensitivity=%f", mAnrIntensity, mAnrMotionSensitivity));
        buildAndSendLiveViewRequest();
    }

    public void fixedWBGains(float gainR, float gainB) {
        Log.d(TAG, String.format("<CameraController> fixedWBGains, R:%f  B:%f ", gainR, gainB));
        mFixedWBGains = true;
        mWbGains = new RggbChannelVector(gainR, 1.0f, 1.0f, gainB);
        buildAndSendLiveViewRequest();
    }

    public void autoWB() {
        mFixedWBGains = false;
    }

    public boolean isWBGainsFixed() {
        return mFixedWBGains;
    }
}
