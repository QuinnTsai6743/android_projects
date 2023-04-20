package com.graystone.camera03;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

public class StreamController implements ISaveFile {
    interface OnWriteFileDoneListener {
        void onWriteFileDone(String pathname);
    }

    static private final String TAG = "Camera03";
    private final Context mContext;
    private final OnWriteFileDoneListener mListener;

    StreamController(Context context, OnWriteFileDoneListener listener) {
        mContext = context;
        mListener = listener;
    }

    class WriteTask implements Runnable {
        private final String mTitle;
        private final ISaveFile.FileType mFileType;
        private final IWriteToStream mWriteToStream;
        private final byte[] mData;

        WriteTask(String title, ISaveFile.FileType fileType, IWriteToStream writeToStream, byte[] data) {
            mTitle = title;
            mFileType = fileType;
            mWriteToStream = writeToStream;
            mData = (data != null)? Arrays.copyOf(data, data.length) : null;
        }

        @Override
        public void run() {
            try {
                Log.d(TAG, "[WriteTask] run");
                String filename = mTitle + "." + mFileType.fileExt();
                ContentValues contentValues = new ContentValues();
                contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
                contentValues.put(MediaStore.MediaColumns.MIME_TYPE, mFileType.mimeType());
                Uri uri;
                if (mFileType == FileType.TXT) {
                    contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/Camera03/");
                    uri = mContext.getContentResolver().insert(MediaStore.Files.getContentUri("external"), contentValues);
                }
                else {
                    contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Camera03/");
                    uri = mContext.getContentResolver().insert(MediaStore.Images.Media.getContentUri("external"), contentValues);
                }
                String realPath = getRealPathFromURI(uri);
                Log.d(TAG, "  Output uri: " + uri.toString());
                Log.d(TAG, "    Real path : " + realPath);

                OutputStream outputStream = mContext.getContentResolver().openOutputStream(uri);
                if (mWriteToStream != null) {
                    mWriteToStream.writeToStream(outputStream);
                }
                else if (mData != null){
                    outputStream.write(mData);
                }
                outputStream.close();
                Log.i(TAG, ">>>>> [StreamController] write done");

                if (mListener != null) {
                    mListener.onWriteFileDone(realPath);
                }
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
        private String getRealPathFromURI(Uri contentUri) {
            String[] proj = { mFileType.dataColumnName() };
            Cursor cursor = mContext.getContentResolver().query(contentUri, proj, null, null, null);
            if (cursor != null) {
                try {
                    if (cursor.getCount() > 0) {
                        cursor.moveToFirst();
                        int columnIndex = cursor.getColumnIndex(mFileType.dataColumnName());
//                        Log.d(TAG, cursor.getString(columnIndex));
                        return cursor.getString(columnIndex);
                    }
                }
                finally {
                    cursor.close();
                }
            }

            return "";
        }
    }

    @Override
    public void write(String title, ISaveFile.FileType fileType, @NonNull IWriteToStream writeToStream) {
        new Thread(new WriteTask(title, fileType, writeToStream, null)).start();
    }

    @Override
    public void write(String title, ISaveFile.FileType fileType, byte[] data) {
        new Thread(new WriteTask(title, fileType, null, data)).start();
    }
}
