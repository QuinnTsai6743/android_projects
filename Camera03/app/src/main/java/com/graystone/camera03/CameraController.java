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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.Executor;

public class CameraController implements Executor, IEdgeMode, INoiseReduction {
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
    }

    private static final String TAG = "Camera03";
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
    private Integer mNoiseReductionMode = CaptureRequest.NOISE_REDUCTION_MODE_OFF;

    private boolean mWbReset = false;

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

    public boolean isAeOn() {
        return mAeOn;
    }

    public void setIso(int iso) {
        if (iso != mIso) {
            mIso = iso;
            buildAndSendLiveViewRequest();
        }
    }

    public int getIso() {
        return mIso;
    }

    public void setExposureTime(long nanosecond) {
        if (mExposureTime != nanosecond) {
            mExposureTime = nanosecond;
            buildAndSendLiveViewRequest();
        }
    }

    public long getExposureTime() {
        return mExposureTime;
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

    class LiveViewRunnable implements Runnable {
        private final ArrayList<Surface> mSurfaceList;
        LiveViewRunnable(ArrayList<Surface> surfaceList) {
            mSurfaceList = surfaceList;
        }
        @Override
        public void run() {
            Log.i(TAG, "setRepeatingRequest()");
            try {
                CaptureRequest.Builder builder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                for (Surface surface: mSurfaceList) {
                    builder.addTarget(surface);
                }
                builder.setTag("Live-view");
                builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
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
                if (!mWbReset) {
                    builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO);
                }
                else {
                    Log.d(TAG, "WB gains: 1.0, 1.0, 1.0, 1.0");
                    builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF);
                    builder.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX);
                    builder.set(CaptureRequest.COLOR_CORRECTION_GAINS, new RggbChannelVector(1.0f, 1.0f, 1.0f, 1.0f));
                }

                // edge mode
                builder.set(CaptureRequest.EDGE_MODE, mEdgeMode);
                // noise reduction mode
                builder.set(CaptureRequest.NOISE_REDUCTION_MODE, mNoiseReductionMode);

                mCameraSession.setRepeatingRequest(builder.build(), mCaptureCallback, mCameraHandler);
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
                builder.setTag("Snapshot");

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
                if ("Snapshot".equals(tag)) {
                    Log.i(TAG, "Snapshot completed! frame no.= " + result.getFrameNumber());
                    Long exposureTime = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
                    Integer gain = result.get(CaptureResult.SENSOR_SENSITIVITY);
                    Log.i(TAG, "Exposure Time = " + toExposureText(exposureTime));
                    Log.i(TAG, String.format("ISO = %d", gain));
                    mCallback.onCaptureComplete(exposureTime, gain, result.getFrameNumber());
                }
                else if ("Live-view".equals(tag)) {
                    AeStatistic.log(result, "AEC_STAT", 3);
                    long frameNo = result.getFrameNumber();
                    if (frameNo % 10 == 0) {
                        Long exposureTime = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
                        Integer iso = result.get(CaptureResult.SENSOR_SENSITIVITY);
//                        RggbChannelVector vector = result.get(CaptureResult.COLOR_CORRECTION_GAINS);
//                        ColorSpaceTransform transform = result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM);
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

    public void resetWbGains(boolean reset) {
        Log.d(TAG, "<CameraController> resetWbGains " + reset);
        mWbReset = reset;
        buildAndSendLiveViewRequest();
    }

    public boolean isWbGainsReset() {
        return mWbReset;
    }
}
