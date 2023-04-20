package com.graystone.camera03;

import android.graphics.ImageFormat;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class SnapshotProcessor implements ImageReader.OnImageAvailableListener {
    private static final String TAG = "Camera03";

    public interface OnSnapshotFinishedListener {
        void onSnapshotFinished();
    }

    private final OnSnapshotFinishedListener mListener;
    private ImageReader mImageReader;
    private final ImageReader mImageReaderRaw;
    private final ImageReader mImageReaderJpeg;
    private Long mExposureTime = 1000L;
    private Integer mIso = 100;
    private final ISaveFile mSaveFile;

    SnapshotProcessor(CameraController.CameraAttrib cameraAttrib, OnSnapshotFinishedListener listener, @NonNull Handler handler, ISaveFile saveFile) {
        mListener = listener;
        mSaveFile = saveFile;

        Size jpegSize = cameraAttrib.maximumSize(ImageFormat.JPEG, new Size(1280, 720));
        Size rawSize = cameraAttrib.maximumSize(ImageFormat.RAW_SENSOR, new Size(1280, 270));

        mImageReaderJpeg = ImageReader.newInstance(jpegSize.getWidth(), jpegSize.getHeight(), ImageFormat.JPEG, 4);
        mImageReaderJpeg.setOnImageAvailableListener(this, handler);
        mImageReaderRaw = ImageReader.newInstance(rawSize.getWidth(), rawSize.getHeight(), ImageFormat.RAW_SENSOR, 4);
        mImageReaderRaw.setOnImageAvailableListener(this, handler);
        mImageReader = mImageReaderRaw;
    }

    public Surface getSurface() {
        return mImageReader.getSurface();
    }

    public void updateIsoExposureTime(Long exposureTime, Integer iso) {
        mIso = iso;
        mExposureTime = exposureTime;
    }

    enum ImageType {
        RAW,
        JPEG,
        BMP,
        PNG;
    }

    public void changeImageType(ImageType type) {
        if (ImageType.JPEG == type) {
            mImageReader = mImageReaderJpeg;
        }
        else if (ImageType.RAW == type) {
            mImageReader = mImageReaderRaw;
        }
        else {
            Log.e(TAG, "unsupported image type. " + type.name());
        }
    }

    private String makeFilename() {
        Date currentTime = Calendar.getInstance().getTime();
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US);
        return simpleDateFormat.format(currentTime);
    }

    private void writeImage(byte [] data, ImageType imageType) {
        ISaveFile.FileType fileType = ISaveFile.FileType.RAW;
        String title = makeFilename() + String.format(Locale.US, "_ISO%d+%d", mIso, mExposureTime);
        if (imageType == ImageType.JPEG) {
            fileType = ISaveFile.FileType.JPEG;
        }
        mSaveFile.write(title, fileType, data);
    }

    private byte [] readByteData(Image.Plane plane) {
        ByteBuffer byteBuffer = plane.getBuffer();
        Log.d(TAG, "  length: " + byteBuffer.remaining() + "bytes");
        Log.d(TAG, "  pixel stride: " + plane.getPixelStride() + " bytes");
        Log.d(TAG, "  row stride: " + plane.getRowStride() + " bytes");

        byte [] bytes = new byte[byteBuffer.remaining()];
        byteBuffer.get(bytes);
        return bytes;
    }

    @Override
    public void onImageAvailable(ImageReader reader) {
        Log.d(TAG, "SnapshotProcessor.onImageAvailable()");
        synchronized (this) {
            Image img = reader.acquireLatestImage();
            if (img != null) {
                Log.d(TAG, "Got a image.");
                Log.d(TAG, "Snapshot image format: " + CameraInfo.ImageFormatToString(img.getFormat()));
                Image.Plane[] planes = img.getPlanes();
                Log.d(TAG, "  num. of planes: " + planes.length);
                int imgFormat = img.getFormat();
                switch (imgFormat)
                {
                    case ImageFormat.RAW_SENSOR:
                        // for RAW data
                        writeImage(readByteData(planes[0]), ImageType.RAW);
                        break;
                    case ImageFormat.JPEG:
                        writeImage(readByteData(planes[0]), ImageType.JPEG);
                        break;
                    default:
                        Log.i(TAG, "Cannot save an unsupported format.");
                }
                mListener.onSnapshotFinished();
                img.close();
            }
        }
    }
}
