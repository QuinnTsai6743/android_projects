package com.graystone.camera03;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceView;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;
import java.util.Locale;

public class PreviewProcessor implements ImageReader.OnImageAvailableListener {
    private static final String TAG = "Camera03";
    private final ImageReader mImageReader;
    private final SurfaceView mSurfaceView;
//    private int mOrientation;
//    private final Matrix mRotateMatrix = new Matrix();
    private boolean mTakeSnapshot = false;
    private int mSnapshotCount = 0;
    private final Context mContext;
    private final SharedPreferences mSharedPreferences;
    private final ISaveFile mSaveFile;

    PreviewProcessor(Context context, CameraController.CameraAttrib attrib, @NonNull SurfaceView view, Handler handler, ISaveFile saveFile) {
        mContext = context;
        mSurfaceView = view;
//        mOrientation = Configuration.ORIENTATION_LANDSCAPE;
        mSaveFile = saveFile;
        mSharedPreferences = context.getSharedPreferences(context.getString(R.string.preference_file_key), Context.MODE_PRIVATE);
        mSnapshotCount = mSharedPreferences.getInt(context.getString(R.string.preview_snapshot_count_key), 0);

        Size size = attrib.maximumSize(ImageFormat.JPEG, new Size(720, 720));
        mImageReader = ImageReader.newInstance(size.getWidth(), size.getHeight(), ImageFormat.JPEG, 4);
        mImageReader.setOnImageAvailableListener(this, handler);

//        mRotateMatrix.postRotate(90.0f);
    }



    public Surface getSurface() {
        return mImageReader.getSurface();
    }

//    public void setOrientation(int orientation) {
//        mOrientation = orientation;
//    }

    public void takeSnapshot() {
        Log.i(TAG, "[Preview] take snapshot");
        synchronized (this) {
            mTakeSnapshot = true;
        }
    }

    @Override
    public void onImageAvailable(ImageReader reader) {
        synchronized (this) {
            Image img = reader.acquireLatestImage();
            if (img != null) {
                Canvas canvas = mSurfaceView.getHolder().lockCanvas();
                if (canvas != null) {
                    ByteBuffer buffer = img.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);
                    if (mTakeSnapshot) {
                        mSnapshotCount++;
                        mSaveFile.write(
                                String.format(Locale.US, "preview-%dx%d-%03d", img.getWidth(), img.getHeight(), mSnapshotCount),
                                ISaveFile.FileType.JPEG,
                                bytes);
                        mTakeSnapshot = false;
                        SharedPreferences.Editor editor = mSharedPreferences.edit();
                        editor.putInt(mContext.getString(R.string.preview_snapshot_count_key), mSnapshotCount);
                        editor.apply();
                    }
                    Bitmap bmpIn = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, null);
                    Bitmap bmpResized;
//                    if (mOrientation == Configuration.ORIENTATION_PORTRAIT) {
//                        Bitmap bmpRotated = Bitmap.createBitmap(bmpIn, 0, 0, bmpIn.getWidth(), bmpIn.getHeight(), mRotateMatrix, true);
//                        bmpResized = Bitmap.createScaledBitmap(bmpRotated, canvas.getWidth(), canvas.getHeight(), true);
//                    }
//                    else {
//                        bmpResized = Bitmap.createScaledBitmap(bmpIn, canvas.getWidth(), canvas.getHeight(), true);
//                    }
                    bmpResized = Bitmap.createScaledBitmap(bmpIn, canvas.getWidth(), canvas.getHeight(), true);
                    canvas.drawBitmap(bmpResized, 0, 0, new Paint());
                    mSurfaceView.getHolder().unlockCanvasAndPost(canvas);
                }
                img.close();
            }
        }
    }
}
