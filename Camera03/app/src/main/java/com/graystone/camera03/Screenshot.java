package com.graystone.camera03;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Handler;
import android.util.Log;
import android.view.PixelCopy;
import android.view.SurfaceHolder;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class Screenshot implements PixelCopy.OnPixelCopyFinishedListener, IWriteToStream {
    private static final String TAG = "Camera03";
    private Bitmap mBitmap;
    private final Handler mHandler;
    private final ISaveFile mSaveFile;

    Screenshot(ISaveFile saveFile, Handler handler) {
        mHandler = handler;
        mSaveFile = saveFile;
    }

    private String makeFilename() {
        Date currentTime = Calendar.getInstance().getTime();
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US);
        return simpleDateFormat.format(currentTime);
    }

    public void capture(SurfaceHolder holder) {
        Rect frameRect = holder.getSurfaceFrame();
        mBitmap = Bitmap.createBitmap(frameRect.width(), frameRect.height(), Bitmap.Config.ARGB_8888);
        PixelCopy.request(holder.getSurface(), mBitmap, this, mHandler);
    }

    @Override
    public void onPixelCopyFinished(int copyResult) {
        Log.i(TAG, "onPixelCopyFinished");
        mSaveFile.write(makeFilename(), ISaveFile.FileType.PNG, this);
    }

    @Override
    public void writeToStream(OutputStream outputStream) {
        mBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
    }
}
