package com.graystone.camera03;

import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.util.Log;
import android.util.Size;
import android.view.Surface;


import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

public class PreviewRawProcessor implements ImageReader.OnImageAvailableListener {
    private static final String TAG = "Camera03";
    private final ImageReader mImageReader;
    private final ISaveFile mSaveFile;
    private final ICameraControl mCameraControl;

    private boolean mTakeSnapshot = false;
    private boolean mTakeRawInMemory = false;

    interface TakeRawCallback {
        void onRawReady(byte[] rawData);
    }

    private TakeRawCallback mTakeRawCallback = null;

    PreviewRawProcessor(CameraController.CameraAttrib attrib, Handler handler, ISaveFile saveFile, ICameraControl cameraControl) {
        mSaveFile = saveFile;
        mCameraControl = cameraControl;

        Size size = attrib.maximumSize(ImageFormat.RAW_SENSOR, new Size(720, 720));
        mImageReader = ImageReader.newInstance(size.getWidth(), size.getHeight(), ImageFormat.RAW_SENSOR, 4);
        mImageReader.setOnImageAvailableListener(this, handler);
    }

    public ArrayList<Surface> getSurfaceList() {
        ArrayList<Surface> surfaceList = new ArrayList<>();
        surfaceList.add(mImageReader.getSurface());
        return surfaceList;
    }

    public void takeSnapShot() {
        mTakeSnapshot = true;
    }

    public void takeRawInMemory(TakeRawCallback callback) {
        mTakeRawCallback = callback;
        mTakeRawInMemory = true;
    }

    private byte [] readBytes(Image.Plane plane) {
        ByteBuffer byteBuffer = plane.getBuffer();

        byte [] bytes = new byte[byteBuffer.remaining()];
        byteBuffer.get(bytes);
        return bytes;
    }

    @Override
    public void onImageAvailable(ImageReader reader) {
        synchronized (this) {
            Image img = reader.acquireLatestImage();
            if (img != null) {
                if (mTakeSnapshot) {
                    int imageFormat = img.getFormat();
                    Log.d(TAG, "[PreviewRaw] image format: " + CameraInfo.ImageFormatToString(imageFormat));
                    Image.Plane[] planes = img.getPlanes();
                    Log.d(TAG, "num of planes: " + planes.length);
                    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US);
                    String timestamp = simpleDateFormat.format(Calendar.getInstance().getTime());
                    String imgResolution = String.format(Locale.US, "%dx%d", img.getWidth(), img.getHeight());
                    String exposureInfo = String.format(Locale.US, "-Iso+%d-Exp+%d-", mCameraControl.getIso(), mCameraControl.getExposureTime());
                    if (imageFormat == ImageFormat.RAW_SENSOR) {
                        String filename = timestamp + exposureInfo + imgResolution;
                        byte [] rawData = readBytes(planes[0]);
                        mSaveFile.write(filename, ISaveFile.FileType.RAW, rawData);
                    } else if (imageFormat == ImageFormat.JPEG) {
                        String filename = timestamp + exposureInfo + imgResolution;
                        mSaveFile.write(filename, ISaveFile.FileType.JPEG, readBytes(planes[0]));
                    } else {
                        Log.e(TAG, "Unsupported image format!");
                    }
                    mTakeSnapshot = false;
                }
                if (mTakeRawInMemory) {
                    int imageFormat = img.getFormat();
                    if (imageFormat == ImageFormat.RAW_SENSOR) {
                        Image.Plane[] planes = img.getPlanes();
                        byte[] rawData = readBytes(planes[0]);
                        if (mTakeRawCallback != null) {
                            mTakeRawCallback.onRawReady(rawData);
                        }
                    }
                    mTakeRawInMemory = false;
                }
                img.close();
            }
        }
    }
}
