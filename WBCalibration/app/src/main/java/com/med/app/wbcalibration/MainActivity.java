package com.med.app.wbcalibration;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.RggbChannelVector;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;
import com.med.util.WBCalibration;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements CameraController.StateCallback, SurfaceHolder.Callback, ImageReader.OnImageAvailableListener {
    private static final String TAG = "WBCalibration";
    private static final int REQUEST_PERMISSIONS_REQUEST_CODE = 34;

    private SurfaceView mPreviewSurface;
    private TextView mTextView;
    private Handler mHandler;

    private CameraController mCameraController;

    private WBCalibration mWbCalibration;

    private ImageReader mImageReader;
    private boolean mCalibrateWB = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        HandlerThread handlerThread = new HandlerThread("Aux Thread");
        handlerThread.start();
        mHandler = new Handler(handlerThread.getLooper());

        setContentView(R.layout.activity_main);

        mPreviewSurface = findViewById(R.id.preview);
        mPreviewSurface.getHolder().addCallback(this);
        mTextView = findViewById(R.id.info);

        mCameraController = new CameraController(this);
        mCameraController.setCaptureCallback(mCaptureCallback);

        // Create WB button
        ImageButton wbBtn = findViewById(R.id.WBCalibrateBtn);
        wbBtn.setOnClickListener(v -> mCalibrateWB = true);

        if (needToGrantCameraPermissions()) {
            requestCameraPermissions();
        }
    }

    /**
     * To check the current state of the camera permissions.
     * @return true if the CAMERA permission need to be granted.
     */
    private boolean needToGrantCameraPermissions() {
        int permissionState = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        // Check if the Camera permission is already available.
        if (permissionState == PackageManager.PERMISSION_GRANTED) {
            // Camera permission are available.
            Log.d(TAG, "CAMERA permission has already been granted.");
            return false;
        }

        // Camera permission has not been granted.
        Log.d(TAG, "CAMERA permission has NOT been granted.");
        return true;
    }

    /**
     * To request Camera permission.
     */
    private void requestCameraPermissions() {
        // Provide an additional rationale to the user. This would happen if the user denied the
        // request previously, but didn't check the "Don't ask again" checkbox.
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
            Snackbar.make(findViewById(R.id.mainActivity), "This app requries camera access in order to demo...", Snackbar.LENGTH_INDEFINITE)
                    .setAction("OK", view -> {
                        // Request Camera permission
                        ActivityCompat.requestPermissions(MainActivity.this,
                                new String[]{Manifest.permission.CAMERA}, REQUEST_PERMISSIONS_REQUEST_CODE);
                    }).show();
        }
        else {
            // Request Camera permission. It's possible this can be auto answered if device policy
            // sets the permission in a given state or the user denied the permission
            // previously and checked "Never ask again".
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.CAMERA}, REQUEST_PERMISSIONS_REQUEST_CODE);
        }
    }

    /**
     * Find and open the camera if it exists.
     */
    private void findAndOpenCamera() {
        if (needToGrantCameraPermissions()) {
            requestCameraPermissions();
        }
        else {
            ArrayList<String> cameraIdList = mCameraController.getCameraList(this);
            Log.d(TAG, "Num of camera: " + cameraIdList.size());
            if (cameraIdList.size() > 0) {
                // dump camera characteristics
                for (String id : cameraIdList) {
                    dumpCameraCharacteristics(id);
                }

                String selectedCameraId = cameraIdList.get(0);
                CameraCharacteristicsWrapper wrapper = new CameraCharacteristicsWrapper(mCameraController.getCameraCharacteristics(selectedCameraId));
                Rect sensorArraySize = wrapper.getSensorActiveArraySize();
                mWbCalibration = new WBCalibration(sensorArraySize.width(), sensorArraySize.height(), wrapper.getSensorColorFilter());
                mImageReader = ImageReader.newInstance(sensorArraySize.width(), sensorArraySize.height(), ImageFormat.RAW_SENSOR, 4);
                mImageReader.setOnImageAvailableListener(this, mHandler);

                mCameraController.openCamera(selectedCameraId);
            }
            else {
                Toast.makeText(this, "No camera device found.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void dumpCameraCharacteristics(String cameraId) {
        CameraCharacteristics characteristics = mCameraController.getCameraCharacteristics(cameraId);
        Log.d(TAG, "Camera ID: " + cameraId);
        if (characteristics != null) {
            CameraCharacteristicsWrapper wrapper = new CameraCharacteristicsWrapper(characteristics);
            Log.d(TAG, "  LENS FACING: " + wrapper.getFacingName());
            Log.d(TAG, "  SENSOR ARRAY: " + wrapper.getSensorActiveArraySize());
            Log.d(TAG, "  SENSOR COLOR FILTER: " + wrapper.getSensorColorFilterName());
        }
        else {
            Log.d(TAG, "Failed to get the camera characteristics.");
        }
    }

    private void createLiveViewSession() {
        ArrayList<OutputConfiguration> outputList = new ArrayList<>();
        outputList.add(new OutputConfiguration(mPreviewSurface.getHolder().getSurface()));
        outputList.add(new OutputConfiguration(mImageReader.getSurface()));
        mCameraController.createCaptureSession(outputList);
    }

    /**
     * To get byte[] from Image plane.
     * @param plane The Image plane.
     * @return A byte[].
     */
    private byte[] readBytes(Image.Plane plane) {
        ByteBuffer byteBuffer = plane.getBuffer();

        byte [] bytes = new byte[byteBuffer.remaining()];
        byteBuffer.get(bytes);
        return bytes;
    }

    private String mCaptureInfo;
    private final CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            long frameNo = result.getFrameNumber();
            if (frameNo % 10 == 0) {
                long exposureTime = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
                int iso = result.get(CaptureResult.SENSOR_SENSITIVITY);
                RggbChannelVector wbGains = result.get(CaptureResult.COLOR_CORRECTION_GAINS);
                mCaptureInfo = String.format(Locale.US, "frame: %d\n", frameNo) +
                        String.format(Locale.US, "Exp. time: %f ms\n", exposureTime / 1000000.0f) +
                        String.format(Locale.US, "Gain: %.2f\n", iso / 100.0f) +
                        String.format(Locale.US, "WB %s\n", new CaptureResultWrapper(result).getControlAwbMode()) +
                        String.format(Locale.US, "   %f, %f, %f",
                                wbGains.getRed(), (wbGains.getGreenEven()+wbGains.getGreenOdd())/2.0f, wbGains.getBlue());
                runOnUiThread(() -> mTextView.setText(mCaptureInfo));
            }
        }
    };

    @Override
    public void onPause() {
        super.onPause();
        if (mCameraController != null) {
            mCameraController.closeCameraAndWait();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // Hide the navigation bar.
        Window window = getWindow();
        WindowManager.LayoutParams params = window.getAttributes();
        params.systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE;
        window.setAttributes(params);
    }

    @Override
    public void onCameraOpened() {
        createLiveViewSession();
    }

    @Override
    public void onCameraDisconnected() {
        Toast.makeText(this, "Camera Disconnected.", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onCameraError(int error) {
        Toast.makeText(this, String.format(Locale.US, "Camera error. (error code %d", error), Toast.LENGTH_LONG).show();
    }

    @Override
    public void onSessionConfigured() {
        ArrayList<Surface> surfaceList = new ArrayList<>();
        surfaceList.add(mPreviewSurface.getHolder().getSurface());
        surfaceList.add(mImageReader.getSurface());
        mCameraController.startLiveView(surfaceList);
    }

    @Override
    public void onSessionConfigureFailed() {
        Toast.makeText(this, "Capture session configure failed", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onPermissionDenied() {
        Toast.makeText(this, "Camera access permission denied.", Toast.LENGTH_LONG).show();
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        findAndOpenCamera();
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {

    }

    @Override
    public void onImageAvailable(ImageReader reader) {
        synchronized (this) {
            Image img = reader.acquireLatestImage();
            if (img != null) {
                if (mCalibrateWB) {
                    int imageFormat = img.getFormat();
                    if (imageFormat == ImageFormat.RAW_SENSOR) {
                        Image.Plane[] planes = img.getPlanes();
                        byte[] byteRaw = readBytes(planes[0]);
                        if (mWbCalibration != null) {
                            mWbCalibration.calibrate(byteRaw);
                            mCameraController.fixedWBGains(mWbCalibration.getGainR(), mWbCalibration.getGainB());
                        }
                    }
                    mCalibrateWB = false;
                }
                img.close();
            }
        }
    }
}