package com.graystone.camera03;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.params.OutputConfiguration;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceView;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

public class PreviewRawProcessor implements ImageReader.OnImageAvailableListener {
    private static final String TAG = "Camera03";
    private final ImageReader mImageReader;
    private final SurfaceView mSurfaceView;
    private boolean mTakeSnapshot = false;
    private final Context mContext;
    private final ISaveFile mSaveFile;

    PreviewRawProcessor(Context context, CameraController.CameraAttrib attrib, @NonNull SurfaceView surfaceView, Handler handler, ISaveFile saveFile) {
        mContext = context;
        mSurfaceView = surfaceView;
        mSaveFile = saveFile;

        Size size = attrib.maximumSize(ImageFormat.RAW_SENSOR, new Size(720, 720));
        mImageReader = ImageReader.newInstance(size.getWidth(), size.getHeight(), ImageFormat.RAW_SENSOR, 4);
        mImageReader.setOnImageAvailableListener(this, handler);
    }

    public ArrayList<OutputConfiguration> buildOutputList() {
        ArrayList<OutputConfiguration> outputList = new ArrayList<>();
        outputList.add(new OutputConfiguration(mSurfaceView.getHolder().getSurface()));
        outputList.add(new OutputConfiguration(mImageReader.getSurface()));
        return outputList;
    }

    public ArrayList<Surface> getSurfaces() {
        ArrayList<Surface> surfaceList = new ArrayList<>();
        surfaceList.add(mSurfaceView.getHolder().getSurface());
        surfaceList.add(mImageReader.getSurface());
        return surfaceList;
    }

    public void takeSnapshot() {
        Log.i(TAG, "[PreviewRaw] take snapshot");
        synchronized (this) {
            mTakeSnapshot = true;
        }
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
                    if (imageFormat == ImageFormat.RAW_SENSOR) {
                        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US);
                        simpleDateFormat.format(Calendar.getInstance().getTime());
                        mSaveFile.write(
                                String.format(Locale.US, "%s-%dx%d.raw", simpleDateFormat, img.getWidth(), img.getHeight()),
                                ISaveFile.FileType.RAW,
                                readBytes(planes[0])
                        );
                    } else {
                        Log.e(TAG, "Unsupported image format!");
                    }
                    mTakeSnapshot = false;
                }
                img.close();
            }
        }
    }
}
