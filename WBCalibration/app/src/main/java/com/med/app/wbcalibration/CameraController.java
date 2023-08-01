package com.med.app.wbcalibration;

import android.content.Context;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Executor;

public class CameraController implements Executor {

    interface StateCallback {
        void onCameraOpened();
        void onCameraDisconnected();
        void onCameraError(int error);
        void onSessionConfigured();
        void onSessionConfigureFailed();
        void onPermissionDenied();
    }

    private static final String TAG = "WBCalibration";
    private static final long CAMERA_CLOSE_TIMEOUT = 2000; // milliseconds

    /**
     * The id of the opened camera.
     */
    private String mCameraId;
    private CameraManager mCameraManager = null;
    private CameraDevice mCameraDevice = null;
    private CameraCaptureSession mCameraSession;

    private final ConditionVariable mCloseWaiter = new ConditionVariable();

    private final Handler mHandler;
    private final StateCallback mStateCallback;

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
            mStateCallback.onCameraOpened();
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
                    mStateCallback.onSessionConfigured();
                }
            });
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            if (mCameraDevice != null) {
                mCameraDevice.close();
            }
            mCameraDevice = null;
            mStateCallback.onSessionConfigureFailed();
        }
    };

    private CameraCaptureSession.CaptureCallback mCaptureCallback = null;

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

    public void openCamera(String cameraId) throws SecurityException {
        mCameraId = cameraId;
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
     * @param outputList The list contained output configurations.
     */
    public void createCaptureSession(ArrayList<OutputConfiguration> outputList) {
        if (mCameraDevice != null) {
            try {
                SessionConfiguration config = new SessionConfiguration(
                        SessionConfiguration.SESSION_REGULAR,
                        outputList,
                        this,
                        mCameraSessionListener);
                mCameraDevice.createCaptureSession(config);
            } catch (CameraAccessException e) {
                e.printStackTrace();
                mCameraDevice.close();
                mCameraDevice = null;
                mStateCallback.onPermissionDenied();
            }
        }
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

    /**
     * Using fixed WB gains.
     * @param gainR R gain.
     * @param gainB B gain.
     */
    public void fixedWBGains(float gainR, float gainB) {
        mFixedWbGains = true;
        mWbGains = new RggbChannelVector(gainR, 1.0f, 1.0f, gainB);
        startLiveView();
    }

    /**
     * Enable auto white balance.
     */
    public void autoWB() {
        mFixedWbGains = false;
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

    @Override
    public void execute(Runnable command) {
        mHandler.post(command);
    }
}
