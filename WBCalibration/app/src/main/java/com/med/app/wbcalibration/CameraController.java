package com.med.app.wbcalibration;

import android.content.Context;
import android.graphics.Rect;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.RggbChannelVector;
import android.hardware.camera2.params.SessionConfiguration;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;

import com.med.hpframework.util.WBCalibration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Executor;

public class CameraController implements ICameraControl, WBCalibration.ResultCallback {
    interface StateCallback {
        void onCameraDisconnected();
        void onCameraError(int error);
        void onSessionConfigureFailed();
        void onPermissionDenied();
    }

    private static final String TAG = IConstant.TAG;
    private static final long CAMERA_CLOSE_TIMEOUT = 2000; // milliseconds

    /**
     * The id of the opened camera.
     */
    private String mCameraId;
    private CameraManager mCameraManager = null;
    private CameraDevice mCameraDevice = null;
    private CameraCaptureSession mCameraSession = null;
    private ArrayList<OutputConfiguration> mOutputList;

    private final ConditionVariable mCloseWaiter = new ConditionVariable();

    private final Handler mHandler;
    private final StateCallback mStateCallback;
    private final Executor mExecutor = new Executor() {
        @Override
        public void execute(Runnable command) {
            mHandler.post(command);
        }
    };

    private boolean mFixedWbGains = false;
    private RggbChannelVector mWbGains;

    /**
     * CameraDevice state listener.
     */
    private final CameraDevice.StateCallback mCameraDeviceListener = new CameraDevice.StateCallback()
    {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            mCameraDevice = camera;
            startLiveViewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            camera.close();
            mCameraDevice = null;
            mStateCallback.onCameraDisconnected();
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            camera.close();
            mCameraDevice = null;
            mStateCallback.onCameraError(error);
        }

        @Override
        public void onClosed(CameraDevice camera) {
            mCloseWaiter.open();
        }
    };

    /**
     * CameraCaptureSession state listener.
     */
    private final CameraCaptureSession.StateCallback mCameraSessionListener = new CameraCaptureSession.StateCallback()
    {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            mCameraSession = session;
            mHandler.post(() -> {
                if (mCameraDevice != null) {
                    ArrayList<Surface> surfaceList = new ArrayList<>();
                    for (OutputConfiguration o : mOutputList) {
                        surfaceList.add(o.getSurface());
                    }
                    startLiveView(surfaceList);
                }
            });
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            Log.e(TAG, "!!! session configure failed.");
            if (mCameraDevice != null) {
                mCameraDevice.close();
            }
            mCameraDevice = null;
            mStateCallback.onSessionConfigureFailed();
        }
    };

    private CameraCaptureSession.CaptureCallback mCaptureCallback;

    /**
     * Constructor of CameraController.
     * @param callback The listener.
     */
    CameraController(StateCallback callback) {
        HandlerThread thread = new HandlerThread("camera thread");
        thread.start();
        mHandler = new Handler(thread.getLooper());

        mStateCallback = callback;
    }

    /**
     * Hook the capture result listener.
     * @param captureCallback The listener.
     */
    public void setCaptureCallback(CameraCaptureSession.CaptureCallback captureCallback) {
        mCaptureCallback = captureCallback;
    }

    /**
     * To get a list of available camera devices.
     * @param context The application context.
     * @return A list of camera id.
     */
    public ArrayList<String> getCameraList(Context context) {
        ArrayList<String> cameraIdList = new ArrayList<>();

        mCameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        if (mCameraManager != null) {
            try {
                String[] cameraIds = mCameraManager.getCameraIdList();
                cameraIdList.addAll(Arrays.asList(cameraIds));
            } catch (CameraAccessException e) {
                e.printStackTrace();
                mStateCallback.onPermissionDenied();
            }
        }

        return cameraIdList;
    }

    /**
     * Open the camera device and create a capture session.
     * @param cameraId The id of the target camera device.
     * @param outputList The list contained the output surface.
     * @throws SecurityException Thrown if camera access permission is not granted.
     */
    public void openCamera(String cameraId, ArrayList<OutputConfiguration> outputList) throws SecurityException {
        mCameraId = cameraId;
        mOutputList = outputList;
        mHandler.post(() -> {
           if (mCameraDevice != null) {
               throw new IllegalStateException("Camera already open");
           }
           try {
               mCameraManager.openCamera(mCameraId, mCameraDeviceListener, mHandler);
           }
           catch (CameraAccessException e) {
               e.printStackTrace();
               mStateCallback.onPermissionDenied();
           }
        });
    }

    /**
     * Close the camera and wait for the close callback to be called in the camera thread.
     * Times out after @{value CAMERA_CLOSE_TIMEOUT} ms.
     */
    public void closeCameraAndWait() {
        mCloseWaiter.close();
        mHandler.post(() -> {
            if (mCameraDevice != null) {
                mCameraDevice.close();
            }
            mCameraDevice = null;
            mCameraSession = null;
        });
        boolean closed = mCloseWaiter.block(CAMERA_CLOSE_TIMEOUT);
        if (!closed) {
            Log.e(TAG, "Timeout closing camera");
        }
    }

    /**
     * To create a capture session.
     */
    @Override
    public void createCaptureSession(SessionConfiguration config) {
        if (mCameraDevice != null) {
            try {
                mCameraDevice.createCaptureSession(config);
            } catch (CameraAccessException e) {
                e.printStackTrace();
                mCameraDevice.close();
                mCameraDevice = null;
                mStateCallback.onPermissionDenied();
            }
        }
    }

    @Override
    public CaptureRequest.Builder getBuilder(int templateType) throws CameraAccessException {
        return mCameraDevice.createCaptureRequest(templateType);
    }

    class LiveViewCreator implements Runnable {
        private final ArrayList<Surface> mSurfaceList;

        LiveViewCreator(ArrayList<Surface> surfaceList) {
            mSurfaceList = surfaceList;
        }

        @Override
        public void run() {
            try {
                CaptureRequest.Builder builder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                for (Surface surface : mSurfaceList) {
                    builder.addTarget(surface);
                }
                builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                if (!mFixedWbGains) {
                    builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO);
                }
                else {
                    builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF);
                    builder.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX);
                    Log.d(TAG, String.format("WB gains: %f, %f", mWbGains.getRed(), mWbGains.getBlue()));
                    builder.set(CaptureRequest.COLOR_CORRECTION_GAINS, mWbGains);
                }

                CaptureRequest request = builder.build();
                mCameraSession.setRepeatingRequest(request, mCaptureCallback, mHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
                mStateCallback.onPermissionDenied();
            }
        }
    }
    private LiveViewCreator mLiveViewCreator;
    public void startLiveView(ArrayList<Surface> surfaceList) {
        mLiveViewCreator = new LiveViewCreator(surfaceList);
        startLiveView();
    }

    private void startLiveView() {
        mHandler.post(mLiveViewCreator);
    }

    private void startLiveViewSession() {
        SessionConfiguration config = new SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR,
                mOutputList,
                mExecutor,
                mCameraSessionListener);
        createCaptureSession(config);
    }

    /**
     * To get the characteristics of the specified camera.
     * @param id The id of the camera.
     * @return A CameraCharacteristics object.
     */
    public CameraCharacteristics getCameraCharacteristics(String id) {
        CameraCharacteristics characteristics = null;
        try {
            characteristics = mCameraManager.getCameraCharacteristics(id);
        } catch (CameraAccessException e) {
            e.printStackTrace();
            mStateCallback.onPermissionDenied();
        }

        return characteristics;
    }

    /**
     * Interface definition for a callback to provide result.
     */
    interface WBCResultListener {
        void onCalibrationDone();
        void onCalibrationFailed(String errMessage);
    }
    private WBCResultListener mResultListener;

    /**
     * Execute white-balance calibration.
     * The new R/B gain will be applied if calibration is success.
     * @param previewSurface The preview surface.
     * @param listener A callback listener.
     */
    public void doWBCalibration(Surface previewSurface, WBCResultListener listener) {
        CameraCharacteristics characteristics = getCameraCharacteristics(mCameraId);
        Rect rect = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        int colorFilter = characteristics.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT);

        mResultListener = listener;
        WBCController1 controller1 = new WBCController1(rect.width(), rect.height(), colorFilter);
        controller1.startCalibration(previewSurface, this, this);
    }

    /**
     * Execute white-balance calibration.
     * @param iso The ISO value for calibration.
     * @param exposureTime The exposure time for calibration.
     * @param listener The callback listener.
     */
    public void doWBCalibration(int iso, long exposureTime, WBCResultListener listener) {
        CameraCharacteristics characteristics = getCameraCharacteristics(mCameraId);
        Rect rect = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        int colorFilter = characteristics.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT);

        mResultListener = listener;
        WBCController2 controller2 = new WBCController2(rect.width(), rect.height(), colorFilter, this);
        controller2.startCalibration(iso, exposureTime, this);
    }

    /**
     * Execute white-balance calibration.
     * @param listener The result listener.
     */
    public void doWBCalibration(WBCResultListener listener) {
        mResultListener = listener;
    }

    @Override
    public void onCalibrationDone(float gainR, float gainB) {
        mFixedWbGains = true;
        mWbGains = new RggbChannelVector(gainR, 1.0f, 1.0f, gainB);
        startLiveViewSession();
        mResultListener.onCalibrationDone();
    }
}
