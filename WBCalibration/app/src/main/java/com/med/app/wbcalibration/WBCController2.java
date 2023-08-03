package com.med.app.wbcalibration;

import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import androidx.annotation.NonNull;

import com.med.hpframework.util.WBCalibration;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.Executor;

public class WBCController2 implements ImageReader.OnImageAvailableListener {
    private static final String TAG = IConstant.TAG;
    private final WBCalibration.ResultCallback mResultCallback;
    private final WBCalibration mWbCalibration;
    private final ImageReader mImageReader;
    private final ExecutionTimeScope mTimeScope = new ExecutionTimeScope("WB 2");
    private final Handler mHandler;
    private final Executor mExecutor = new Executor() {
        @Override
        public void execute(Runnable command) {
            mHandler.post(command);
        }
    };
    private int mIso;
    private long mExposureTime;
    private ICameraControl mCameraControl;

    private final CameraCaptureSession.StateCallback mSessionListener = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            try {
                CaptureRequest.Builder builder = mCameraControl.getBuilder(CameraDevice.TEMPLATE_STILL_CAPTURE);
                builder.addTarget(mImageReader.getSurface());
                builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_OFF);
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
                builder.set(CaptureRequest.SENSOR_SENSITIVITY, mIso);
                builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, mExposureTime);

                session.captureSingleRequest(builder.build(), mExecutor, mCaptureListener);
                mTimeScope.addStamp("capture request");
            } catch (CameraAccessException e) {
                e.printStackTrace();
                onFailed("Camera Access Exception");
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            onFailed("session configure failed");
        }
    };

    private final CameraCaptureSession.CaptureCallback mCaptureListener = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            mTimeScope.addStamp("capture completed");
        }

        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);
            onFailed("capture failed");
        }
    };

    WBCController2(int sensorWidth, int sensorHeight, int colorFilter, WBCalibration.ResultCallback callback) {
        HandlerThread thread = new HandlerThread("wb thread");
        thread.start();
        mHandler = new Handler(thread.getLooper());

        mWbCalibration = new WBCalibration(sensorWidth, sensorHeight, colorFilter);
        mResultCallback = callback;
        mImageReader = ImageReader.newInstance(sensorWidth, sensorHeight, ImageFormat.RAW_SENSOR, 4);
        mImageReader.setOnImageAvailableListener(this, mHandler);
    }

    public void startCalibration(int iso, long exposureTime, ICameraControl cameraControl) {
        mTimeScope.begin();
        ArrayList<OutputConfiguration> outputList = new ArrayList<>();
        outputList.add(new OutputConfiguration(mImageReader.getSurface()));

        mIso = iso;
        mExposureTime = exposureTime;
        mCameraControl = cameraControl;

        // create a new capture session
        SessionConfiguration config = new SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR,
                outputList,
                mExecutor,
                mSessionListener);
        cameraControl.createCaptureSession(config);
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

    private void onFailed(String message) {
        Log.e(TAG, message);
    }

    private void onSuccess() {
        mTimeScope.end();
    }

    @Override
    public void onImageAvailable(ImageReader reader) {
        synchronized (this) {
            Image img = reader.acquireLatestImage();
            if (img != null) {
                int format = img.getFormat();
                if (format == ImageFormat.RAW_SENSOR) {
                    mTimeScope.addStamp("take RAW");
                    Image.Plane[] planes = img.getPlanes();
                    mWbCalibration.calibrate(readBytes(planes[0]), mResultCallback);
                    onSuccess();
                }
                img.close();
            }
        }
    }
}
