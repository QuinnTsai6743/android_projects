package com.graystone.camera03;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.res.ResourcesCompat;

import android.Manifest;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.RggbChannelVector;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Range;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements CameraController.StateCallback,
        SurfaceHolder.Callback,
        SnapshotProcessor.OnSnapshotFinishedListener,
        SensorEventListener,
        AdapterView.OnItemSelectedListener,
        InputDialog.EventListener,
        StreamController.OnWriteFileDoneListener {

    private static final String TAG = "Camera03";
    private static final int REQUEST_PERMISSIONS_REQUEST_CODE = 34;

    private SensorManager mSensorManager;
    private final float[] mAccelerometerReading = new float[3];
    private final float[] mMagnetometerReading = new float[3];
    private final float[] mRotationMatrix = new float[3];
    private final float[] mOrientationAngles = new float[3];

//    private OrientationEventListener mOrientationEventListener;

    private boolean mSnapshotMode = false;
    private SurfaceView mSurfaceViewAux;
    private PreviewProcessor mPreviewProcessor;
    private boolean mUsingAuxView = true;
    private SnapshotProcessor mSnapshotProcessor;
    private CameraController mCameraController;
    private String mCameraId;
    private SnapshotProcessor.ImageType mSnapshotType = SnapshotProcessor.ImageType.RAW;

    private TextView mTextView;
    private ImageButton mAELockBtn;
    private boolean mAELock = false;

    private Range<Integer> mIsoRange;
    private Range<Long> mExposureTimeRange;

    private boolean mIsoTest = false;
    private int mIso = 100;
    private long mExposureTime = 5000000L;  // 5 milliseconds
    private int mRegValue = 16;

    private InputDialog mExpTimeDialog;
    private InputDialog mIsoDialog;

    private boolean mCameraOpened = false;
    private ImageView mCameraBtn;
    private Drawable mIconCameraOn;
    private Drawable mIconCameraOff;
//    private Icon mIconCameraOn;
//    private Icon mIconCameraOff;
    private ImageView mAwbBtn;
    private Drawable mIconAwbOn;
    private Drawable mIconAwbOff;

//    private Screenshot mPreviewSnapshot;
    private StreamController mStreamController;

    private Handler mHandler;
    private Handler mUiHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        HandlerThread handlerThread = new HandlerThread("AuxThread");
        handlerThread.start();
        mHandler = new Handler(handlerThread.getLooper());
        mUiHandler = new UiHandler(this, Looper.getMainLooper());

        setContentView(R.layout.activity_main);
        Log.i(TAG, "========================================");
        Log.i(TAG, "MainActivity onCreate()");
        ContextWrapper contextWrapper = new ContextWrapper(getApplicationContext());
        File dataDir = contextWrapper.getDataDir();
        Log.i(TAG, "Data Dir: " + dataDir.toString());
        File filesDir = contextWrapper.getFilesDir();
        Log.i(TAG, "Files Dir: " + filesDir.toString());

        Uri externalUri = MediaStore.Files.getContentUri("external");
        Log.i(TAG, "External Uri: " + externalUri.toString());
        Uri internalUri = MediaStore.Files.getContentUri("internal");
        Log.i(TAG, "Internal Uri: " + internalUri.toString());

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        mIconCameraOn = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_baseline_camera_alt_24, null);
        mIconCameraOff = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_baseline_camera_alt_off_24, null);
//        mIconCameraOn = Icon.createWithResource(this, R.drawable.ic_baseline_camera_alt_24);
//        mIconCameraOff = Icon.createWithResource(this, R.drawable.ic_baseline_camera_alt_off_24);
        mIconAwbOn = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_baseline_settings_backup_restore_24, null);
        mIconAwbOff = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_baseline_settings_backup_restore_on_24, null);

        createButtons();

//        mOrientationEventListener = new OrientationEventListener(this) {
//            @Override
//            public void onOrientationChanged(int orientation) {
//                Log.d(TAG, "==> Orientation = " + orientation);
//            }
//        };

        mSurfaceViewAux = findViewById(R.id.previewAux);
        mStreamController = new StreamController(this, this);
//        mPreviewSnapshot = new Screenshot(streamController, mHandler);

        mTextView = findViewById(R.id.text1);

        SurfaceView surfaceView = findViewById(R.id.preview);
        surfaceView.getHolder().addCallback(this);

        Spinner spinner = findViewById(R.id.imageFormat);
        ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(this, R.layout.spinner_item, new String[]{"RAW", "JPEG"});
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(this);

        mExposureTimeRange = new Range<Long>(1000000L, 60000000L);
        mIsoRange = new Range<Integer>(100, 400);

        if (!checkCameraPermissions()) {
            requestCameraPermissions();
        }

        mCameraController = new CameraController(this);
        new EdgeModeHandler(findViewById(R.id.edgeMode),
                new ArrayAdapter<CharSequence>(this, R.layout.spinner_item, mCameraController.getSupportEdgeMode()),
                mCameraController);
        new NoiseReductionHandler(findViewById(R.id.noiseReduction),
                new ArrayAdapter<CharSequence>(this, R.layout.spinner_item, mCameraController.getSupportNoiseReductionMode()),
                mCameraController);

        ArrayList<CameraController.CameraAttrib> arrayList = mCameraController.findCamera(getApplicationContext());
        Log.d(TAG, "Num of cameras: " + arrayList.size());
        if (arrayList.size() > 0) {
            for (CameraController.CameraAttrib c : arrayList) {
                Log.d(TAG, "Camera id: " + c.getCameraId());
                Log.d(TAG, " LENS FACING: " + c.getLensFacing());
                Log.d(TAG, " SENSOR ARRAY: " + c.getSensorActiveArraySize());
            }

            CameraController.CameraAttrib cameraAttrib = arrayList.get(0); // Pick up the first camera
//        CameraController.CameraAttrib cameraAttrib = arrayList.get(1); // Pick up the 2nd camera
            mCameraId = cameraAttrib.getCameraId();
            Log.d(TAG, "Pick up the camera " + mCameraId);

            Range<Long> exposureTimeRange = cameraAttrib.getExposureTimeRange();
            if (exposureTimeRange != null) {
                mExposureTimeRange = exposureTimeRange;
            }
            Range<Integer> isoRange = cameraAttrib.getSensitivityRange();
            if (isoRange != null) {
                mIsoRange = isoRange;
            }
            Log.d(TAG, "Sensor Sensitivity Range: " + mIsoRange);
            Log.d(TAG, "Sensor Exposure Time Range: " + mExposureTimeRange);
            mExpTimeDialog.setMessage(String.format(Locale.US, "Valid range is\n%d ns ~ %d ns", mExposureTimeRange.getLower(), mExposureTimeRange.getUpper()));
            mIsoDialog.setMessage(String.format(Locale.US, "Valid range is\n%d ~ %d", mIsoRange.getLower(), mIsoRange.getUpper()));

            cameraAttrib.dumpAllSupportedSize();
            Log.i(TAG, "Max Regions AWB: " + cameraAttrib.getMaxRegionsAwb());
            Log.i(TAG, "Max Regions  AE: " + cameraAttrib.getMaxRegionsAe());
            Log.i(TAG, "Flash Available: " + cameraAttrib.isFlashAvailable());

            mPreviewProcessor = new PreviewProcessor(this, cameraAttrib, surfaceView, mHandler, mStreamController);
            mSnapshotProcessor = new SnapshotProcessor(cameraAttrib, this, mHandler, mStreamController);
        }
    }

    private void createButtons() {
        ImageView btn00 = findViewById(R.id.btn0);
        btn00.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startSnapshotSession();
                    }
                });

        mAELockBtn = findViewById(R.id.btn1);
        mAELockBtn.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mAELock = !mAELock;
                        if (mAELock) {
                            mAELockBtn.setImageResource(R.drawable.ic_baseline_lock_24);
                        }
                        else {
                            mAELockBtn.setImageResource(R.drawable.ic_baseline_lock_open_24);
                        }
                        mCameraController.lockAE(mAELock);
                    }
                });

        mAwbBtn = findViewById(R.id.btn1_2);
        mAwbBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "<btn1_2 clicked>");
                if (mCameraController.isWbGainsReset()) {
                    mAwbBtn.setImageDrawable(mIconAwbOn);
                    mCameraController.resetWbGains(false);
                }
                else {
                    mAwbBtn.setImageDrawable(mIconAwbOff);
                    mCameraController.resetWbGains(true);
                }
            }
        });

        ImageButton btn02 = findViewById(R.id.btn2);
        btn02.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "switch preview window");
                mUsingAuxView = !mUsingAuxView;
                createLiveViewSession();
            }
        });

        ImageButton btn03 = findViewById(R.id.btn3);
        btn03.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "BTN03 click");
                if (!mUsingAuxView) {
                    mPreviewProcessor.takeSnapshot();
                }
                else {
                    new Screenshot(mStreamController, mHandler).capture(mSurfaceViewAux.getHolder());
//                    screenshot.capture(mSurfaceViewAux.getHolder());
                }
            }
        });

        mCameraBtn = findViewById(R.id.btn4);
        mCameraBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mCameraController.isCameraOpened()) {
                    mCameraController.openCamera(mCameraId);
                    mCameraOpened = true;
                }
                if (mCameraOpened) {
                    mCameraBtn.setImageDrawable(mIconCameraOn);
                }
                else {
                    mCameraBtn.setImageDrawable(mIconCameraOff);
                }
            }
        });

        mIsoDialog = new InputDialog(this, "IsoInputDialog", "Change ISO", this);
        ImageView isoIcon = findViewById(R.id.isoIcon);
        isoIcon.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Log.i(TAG, "ISO icon click!");
                        if (mCameraOpened) {
                            mIsoDialog.show();
                        }
                    }
                }
        );
        mExpTimeDialog = new InputDialog(this, "ExpTimeInputDialog", "Change Exposure Time", this);
        ImageView expIcon = findViewById(R.id.exposureTimeIcon);
        expIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "exposure-time icon click!");
                if (mCameraOpened) {
                    mExpTimeDialog.show();
                }
            }
        });

        ImageView isoInc = findViewById(R.id.btnIsoInc);
        isoInc.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (mCameraOpened) {
                            int iso = mCameraController.getIso();
                            mCameraController.setIso(mIsoRange.clamp(iso + 50));
                        }
                    }
                }
        );
        ImageView isoDec = findViewById(R.id.btnIsoDec);
        isoDec.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (mCameraOpened) {
                            int iso = mCameraController.getIso();
                            mCameraController.setIso(mIsoRange.clamp(iso - 50));
                        }
                    }
                }
        );
        ImageView expInc = findViewById(R.id.btnExpInc);
        expInc.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (mCameraOpened) {
                            long expTime = mCameraController.getExposureTime();
                            mCameraController.setExposureTime(mExposureTimeRange.clamp(expTime + 500000));
                        }
                    }
                }
        );
        ImageView expDec = findViewById(R.id.btnExpDec);
        expDec.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (mCameraOpened) {
                            long expTime = mCameraController.getExposureTime();
                            mCameraController.setExposureTime(mExposureTimeRange.clamp(expTime - 500000));
                        }
                    }
                }
        );
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause()");

        // Don't receive any more updates from either sensor.
        mSensorManager.unregisterListener(this);

//        mOrientationEventListener.disable();

        if (mCameraController != null) {
            mCameraController.closeCameraAndWait();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mCameraOpened) {
            mCameraBtn.setImageDrawable(mIconCameraOn);
        }
        else {
            mCameraBtn.setImageDrawable(mIconCameraOff);
        }


        // Hide the navigation bar.
//        View decorView = getWindow().getDecorView();
//        int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN;
//        decorView.setSystemUiVisibility(uiOptions);
        Window window = getWindow();
        WindowManager.LayoutParams params = window.getAttributes();
        params.systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE;
        window.setAttributes(params);

        // Orientation detection
        Sensor accelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accelerometer != null ) {
            mSensorManager.registerListener(this, accelerometer,
                    SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI);
        }
        Sensor magneticField = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        if (magneticField != null) {
            mSensorManager.registerListener(this, magneticField,
                    SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI);
        }

//        mOrientationEventListener.enable();
    }

    @Override
    public void onConfigurationChanged(@NotNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            Log.d(TAG, "Orientation: Landscape");
            Toast.makeText(this, "landscape", Toast.LENGTH_SHORT).show();
        }
        else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            Log.d(TAG, "Orientation: Portrait");
            Toast.makeText(this, "portrait", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Return the current state of the camera permissions.
     * @return true if the CAMERA permission has been granted.
     */
    private boolean checkCameraPermissions() {
        int permissionState = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);

        // Check if the Camera permission is already available.
        if (permissionState != PackageManager.PERMISSION_GRANTED) {
            // Camera permission has not been granted.
            Log.e(TAG, "CAMERA permission has NOT been granted.");
            return false;
        }
        else {
            // Camera permission are available.
            Log.i(TAG, "CAMERA permission has already been granted.");
            return true;
        }
    }

    private void requestCameraPermissions() {
        // Provide an additional rationale to the user. This would happen if the user denied the
        // request previously, but didn't check the "Don't ask again" checkbox.
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
            Log.i(TAG, "Displaying camera permission rationale to provide additional context.");
            Snackbar.make(findViewById(R.id.panels), "This app requires camera access in order to demo...", Snackbar.LENGTH_INDEFINITE)
                    .setAction("OK", new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            // Request Camera permission
                            ActivityCompat.requestPermissions(MainActivity.this,
                                    new String[]{Manifest.permission.CAMERA}, REQUEST_PERMISSIONS_REQUEST_CODE);
                        }
                    }).show();
        }
        else {
            Log.i(TAG, "Requesting camera permission");
            // Request Camera permission. It's possible this can be auto answered if device policy
            // sets the permission in a given state or the user denied the permission
            // previously and checked "Never ask again".
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.CAMERA}, REQUEST_PERMISSIONS_REQUEST_CODE);
        }
    }

    @Override
    public void onCameraOpened() {
        Log.d(TAG, "onCameraOpened()");
        createLiveViewSession();
    }

    @Override
    public void onCameraError() {
        Toast.makeText(getApplicationContext(), "Camera error.", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onSessionConfigured() {
        Log.d(TAG, "onSessionConfigured()");

        if (mSnapshotMode) {
            if (mIsoTest) {
                mCameraController.takeSnapshotEx(mSnapshotProcessor.getSurface(), mIso, mExposureTime);
            }
            else {
                mCameraController.takeSnapshot(mSnapshotProcessor.getSurface());
            }
        }
        else {
            if (mUsingAuxView) {
                mCameraController.startLiveView(mSurfaceViewAux.getHolder().getSurface());
            }
            else {
                mCameraController.startLiveView(mPreviewProcessor.getSurface());
            }
        }
    }

    @Override
    public void onSessionConfigureFailed() {
        Toast.makeText(this, "Camera session configuration fail.", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onPermissionDenied() {
        Toast.makeText(this, "Camera access permission denied.", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        Log.d(TAG, String.format("onItemSelected() position = %d, id = %d", position, id));
        String imageType = (String)parent.getItemAtPosition(position);
        Log.i(TAG, "selected image type: " + imageType);
        if ("JPEG".equals(imageType)) {
            mSnapshotType = SnapshotProcessor.ImageType.JPEG;
//            mSnapshotProcessor.changeImageType(SnapshotProcessor.ImageType.JPEG);
        }
        else if ("RAW".equals(imageType)) {
            mSnapshotType = SnapshotProcessor.ImageType.RAW;
//            mSnapshotProcessor.changeImageType(SnapshotProcessor.ImageType.RAW);
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        Log.d(TAG, "onNothingSelected()");
    }

    @Override
    public void OnOk(String text, String tag) {
        if ("ExpTimeInputDialog".equals(tag)) {
            Log.i(TAG, "[exposure time] input string=" + text);
            try {
                long exposureTime = Long.parseLong(text);
                mCameraController.setExposureTime(exposureTime);
            }
            catch (NumberFormatException e) {
                Log.e(TAG, "NumberFormatException!");
                e.printStackTrace();
            }
        }
        else if ("IsoInputDialog".equals(tag)) {
            Log.i(TAG, "[ISO] input string=" + text);
            try {
                int iso = Integer.parseInt(text);
                mCameraController.setIso(iso);
            }
            catch (NumberFormatException e) {
                Log.e(TAG, "NumberFormatException!");
                e.printStackTrace();
            }
        }
    }

    @Override
    public void OnCancel(String tag) {

    }

    @Override
    public void onWriteFileDone(String pathname) {
        Message msg = mUiHandler.obtainMessage(0, pathname);
        msg.sendToTarget();
//        Toast.makeText(this, pathname, Toast.LENGTH_SHORT).show();
    }

    class UpdateExposureTime implements Runnable {
        long mExposureTime;
        int mIso;
        long mFrameNo;
        float [] mGain;
        int [] mColorTransform;
        int mAwbState;
        UpdateExposureTime(CapResult capResult) {
            mExposureTime = capResult.getExposureTime();
            mIso = capResult.getIso();
            mFrameNo = capResult.getFrameNo();
            mGain = capResult.getWbGains();
            mColorTransform = capResult.getColorTransform();
            mAwbState = capResult.getAwbState();
        }

        @Override
        public void run() {
            double milli_sec = mExposureTime / 1e6;
            int gain1 = mIso / 100;
            int gain2 = mIso % 100;
            float[] cc = new float[] {
                    (float)mColorTransform[0]/mColorTransform[1], (float)mColorTransform[2]/mColorTransform[3], (float)mColorTransform[4]/mColorTransform[5],
                    (float)mColorTransform[6]/mColorTransform[7], (float)mColorTransform[8]/mColorTransform[9], (float)mColorTransform[10]/mColorTransform[11],
                    (float)mColorTransform[12]/mColorTransform[13], (float)mColorTransform[14]/mColorTransform[15], (float)mColorTransform[16]/mColorTransform[17]
            };
            String info = String.format(Locale.US, " Exp. Time = %.4f ms\n Gain %d.%02d\n Frame Cnt. %d", milli_sec, gain1, gain2, mFrameNo) +
                        String.format(Locale.US, "\n R %.2f  Gr %.2f Gb %.2f B %.2f (%s)", mGain[0], mGain[1], mGain[2], mGain[3], CameraInfo.AwbState(mAwbState)) +
                        String.format(Locale.US, "\n %6.4f %6.4f %6.4f", cc[0], cc[1], cc[2]) +
                        String.format(Locale.US, "\n %6.4f %6.4f %6.4f", cc[3], cc[4], cc[5]) +
                        String.format(Locale.US, "\n %6.4f %6.4f %6.4f", cc[6], cc[7], cc[8]);
//                        String.format(Locale.US, "\n %3d/%3d %3d/%3d %3d/%3d", mColorTransform[ 0], mColorTransform[ 1], mColorTransform[ 2], mColorTransform[ 3], mColorTransform[ 4], mColorTransform[ 5]) +
//                        String.format(Locale.US, "\n %3d/%3d %3d/%3d %3d/%3d", mColorTransform[ 6], mColorTransform[ 7], mColorTransform[ 8], mColorTransform[ 9], mColorTransform[10], mColorTransform[11]) +
//                        String.format(Locale.US, "\n %3d/%3d %3d/%3d %3d/%3d", mColorTransform[12], mColorTransform[13], mColorTransform[14], mColorTransform[15], mColorTransform[16], mColorTransform[17]);
            mTextView.setText(info);
        }
    }
    @Override
    public void onUpdateExposureInfo(CapResult capResult) {
        runOnUiThread(new UpdateExposureTime(capResult));
    }

    @Override
    public void onCaptureComplete(Long exposureTime, Integer ISO, Long frameNo) {
        mSnapshotProcessor.updateIsoExposureTime(exposureTime, ISO);
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {

    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
        Log.d(TAG, String.format("surfaceChanged() format=%s, %d x %d", CameraInfo.ImageFormatToString(format), width, height));
        int orientation = getApplication().getResources().getConfiguration().orientation;
        Log.d(TAG, String.format("App orientation: %s (%d)", (orientation == Configuration.ORIENTATION_PORTRAIT)? "Portrait" : " Landscape", orientation));
//        if (width > height) {
//            mPreviewProcessor.setOrientation(Configuration.ORIENTATION_LANDSCAPE);
//        }
//        else {
//            mPreviewProcessor.setOrientation(Configuration.ORIENTATION_PORTRAIT);
//        }
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        Log.d(TAG, "surfaceDestroyed()");
    }

    @Override
    public void onSnapshotFinished() {
        if (mIsoTest) {
            mRegValue++;
            Log.d(TAG, String.format("next shot (reg value=%d)", mRegValue));
            mIso = (mRegValue * 100 / 16) + 1;
            Log.d(TAG, String.format("new ISO=%d", mIso));
            mCameraController.takeSnapshotEx(mSnapshotProcessor.getSurface(), mIso, mExposureTime);
            if (mRegValue >= 62) {
                mIsoTest = false;
            }
        }
        else {
            // start live-view
            createLiveViewSession();
        }
    }

    private void createLiveViewSession() {
        mSnapshotMode = false;
        ArrayList<OutputConfiguration> outputList = new ArrayList<>();
        if (mUsingAuxView) {
            outputList.add(new OutputConfiguration(mSurfaceViewAux.getHolder().getSurface()));
        }
        else {
            outputList.add(new OutputConfiguration(mPreviewProcessor.getSurface()));
        }
        mCameraController.startCameraSession(outputList);
    }

    private void startSnapshotSession() {
        mSnapshotMode = true;
        mCameraController.abortCaptures();
        mSnapshotProcessor.changeImageType(mSnapshotType);
        ArrayList<OutputConfiguration> outputList = new ArrayList<>();
        outputList.add(new OutputConfiguration(mSnapshotProcessor.getSurface()));
        mCameraController.startCameraSession(outputList);
    }

    // Get readings from accelerometer and magnetometer. To simplify calculations,
    // consider storing these readings as unit vectors.
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, mAccelerometerReading, 0, mAccelerometerReading.length);
        }
        else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, mMagnetometerReading, 0, mMagnetometerReading.length);
        }
    }

    // Compute the three orientation angles based on the most recent readings from
    // the device's accelerometer and magnetometer.
    public void updateOrientationAngles() {
        // Update rotation matrix, which is needed to update orientation angles.
        SensorManager.getRotationMatrix(mRotationMatrix, null, mAccelerometerReading, mMagnetometerReading);
        // "mRotationMatrix" now has up-to-date information
        SensorManager.getOrientation(mRotationMatrix, mOrientationAngles);
        // "mOrientationAngles" now has up-to-date information.
        Log.i(TAG, "Orientation angles: " + Arrays.toString(mOrientationAngles));
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Do something here if sensor accuracy changes.
        // You must implement this callback in your code.
    }
}