package com.med.app.wbcalibration;

import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;

import com.med.hpframework.util.WBCalibration;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.Executor;

public class WBCController1 implements ImageReader.OnImageAvailableListener {
    private static final String TAG = IConstant.TAG;
    private CameraCaptureSession mSession;
    private final ImageReader mImageReader;
    private final WBCalibration mWBCalibration;
    private final Handler mHandler;
    private ICameraControl mCameraControl;
    private final ExecutionTimeScope mTimeScope = new ExecutionTimeScope("WB 1");
    private int mWBCState = 0;
    private WBCalibration.ResultCallback mResultCallback;
    private final Executor mExecutor = new Executor() {
        @Override
        public void execute(Runnable command) {
            mHandler.post(command);
        }
    };
    ArrayList<OutputConfiguration> mOutputList = new ArrayList<>();

    WBCController1(int sensorWidth, int sensorHeight, int colorFilter) {
        HandlerThread thread = new HandlerThread("calibration thread 1");
        thread.start();
        mHandler = new Handler(thread.getLooper());
        mWBCalibration = new WBCalibration(sensorWidth, sensorHeight, colorFilter);
        mImageReader = ImageReader.newInstance(sensorWidth, sensorHeight, ImageFormat.RAW_SENSOR, 4);
        mImageReader.setOnImageAvailableListener(this, mHandler);
    }

    public void startCalibration(Surface previewSurface, ICameraControl cameraControl, WBCalibration.ResultCallback callback) {
        mTimeScope.begin();
        mCameraControl = cameraControl;
        mResultCallback = callback;

        mOutputList.add(new OutputConfiguration(previewSurface));
        mOutputList.add(new OutputConfiguration(mImageReader.getSurface()));

        SessionConfiguration config = new SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR,
                mOutputList,
                mExecutor,
                mSessionListener);
        mCameraControl.createCaptureSession(config);
    }

    private final CameraCaptureSession.StateCallback mSessionListener = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            mSession = session;
            mHandler.post(()-> createCaptureRequest());
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            onFailed("Capture Session Configure Failed");
        }
    };

    private final CameraCaptureSession.CaptureCallback mCaptureListener = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
            if (aeState != null) {
                if (mWBCState == 0 && aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                    mTimeScope.addStamp("AE stable");
                    mWBCState = 1;
                }
            }
            else {
                Log.e(TAG, "AE state is null");
            }
        }

        @Override
        public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request, CaptureFailure failure) {
            onFailed("Capture Failed");
        }
    };

    private void createCaptureRequest() {
        try {
            CaptureRequest.Builder builder = mCameraControl.getBuilder(CameraDevice.TEMPLATE_PREVIEW);
            for (OutputConfiguration o : mOutputList) {
                builder.addTarget(o.getSurface());
            }
            builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

            mSession.setRepeatingRequest(builder.build(), mCaptureListener, mHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
            onFailed("Camera Access Exception");
        }
    }

    private void onFailed(String errMessage) {
        Log.e(TAG, errMessage);
    }

    private void onSuccess() {
        mTimeScope.end();
    }

    /**
     * To read byte data from a image plane.
     * @param plane The image plane.
     * @return The byte data.
     */
    private byte[] readBytes(Image.Plane plane) {
        ByteBuffer byteBuffer = plane.getBuffer();
        byte [] byteRaw = new byte[byteBuffer.remaining()];
        byteBuffer.get(byteRaw);
        return byteRaw;
    }

    @Override
    public void onImageAvailable(ImageReader reader) {
        synchronized (this) {
            Image img = reader.acquireLatestImage();
            if (img != null) {
                int format = img.getFormat();
                if (mWBCState == 1 && format == ImageFormat.RAW_SENSOR) {
                    mWBCState = 2;
                    mTimeScope.addStamp("take RAW");
                    Image.Plane[] planes = img.getPlanes();
                    mWBCalibration.calibrate(readBytes(planes[0]), mResultCallback);
                    onSuccess();
                }
                img.close();
            }
        }
    }
}
