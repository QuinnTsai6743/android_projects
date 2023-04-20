package com.graystone.utility;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;


public class Utf8Converter {
    private static final String TAG = "Camera03";
    private static final char INITIAL1 = 'U';
    private static final char INITIAL2 = '+';

    private final ArrayList<Byte> mByteList = new ArrayList<Byte>();

    public void append(byte byte1, byte byte2) {
        append(((int)byte1 << 8) | byte2);
    }

    public void append(String unicode) throws Exception {
        if (INITIAL1 != unicode.charAt(0) && INITIAL2 != unicode.charAt(1)) {
            throw new Exception("Invalid String.");
        }
        try {
            int value = Integer.parseInt(unicode.substring(2), 16);
            append(value);
        }
        catch (NumberFormatException e) {
            throw new Exception("Invalid String.");
        }
    }

    public int size() {
        return mByteList.size();
    }

    public void toArray(byte[] bytes) {
        int j = 0;
        for (Byte b : mByteList) {
            bytes[j++] = b;
        }
    }

    public void toArray(Byte[] bytes) {
        mByteList.toArray(bytes);
    }

//    public Byte[] toArray() {
//        Byte[] byteArray = new Byte[mByteList.size()];
//        mByteList.toArray(byteArray);
//
//        return byteArray;
//    }

    private void append(int unicode) {
        mByteList.add((byte)( 0x00E0 | (unicode & 0x00f000) >> 12));
        mByteList.add((byte)( 0x0080 | (unicode & 0x000fc0) >> 6));
        mByteList.add((byte)( 0x0080 | (unicode & 0x00003f)));
    }

    static class WriteTask implements Runnable {
        private final Context mContext;
        private final String mFilename;
        private final String mMimeType;
        private final byte[] mData;

        WriteTask(Context context, String filename, String mimeType, byte[] data) {
            mContext = context;
            mFilename = filename;
            mMimeType = mimeType;
            mData = Arrays.copyOf(data, data.length);
        }

        @Override
        public void run() {
            Log.d(TAG, "[WriteTask] run!");
            ContentValues contentValues = new ContentValues();
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, mFilename);
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, mMimeType);
            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/Camera03/");

            Uri uri = mContext.getContentResolver().insert(MediaStore.Files.getContentUri("external"), contentValues);
            Log.d(TAG, "[Utf8Converter] Write to " + uriToRealPath(uri));
            try {
                OutputStream outputStream = mContext.getContentResolver().openOutputStream(uri);
                outputStream.write(mData);
                outputStream.close();
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }

        private String uriToRealPath(Uri uri) {
            String[] proj = { MediaStore.Files.FileColumns.DATA };
            Cursor cursor = mContext.getContentResolver().query(uri, proj, null, null, null);
            if (cursor != null) {
                try {
                    if (cursor.getCount() > 0) {
                        cursor.moveToFirst();
                        int columnIdx = cursor.getColumnIndex(proj[0]);
                        return cursor.getString(columnIdx);
                    }
                }
                finally {
                    cursor.close();
                }
            }
            return "???";
        }
    }

    static public void testRun(Context context) {
        Log.i(TAG, "testRun!!");
        Utf8Converter utf8Converter = new Utf8Converter();
        try {
            utf8Converter.append("U+7F8E");
            utf8Converter.append("U+817F");
            utf8Converter.append("U+5973");
            utf8Converter.append("U+8D85");
            utf8Converter.append("U+4EBA");
            byte[] bytes = new byte[utf8Converter.size()];
            utf8Converter.toArray(bytes);
            new Thread(new WriteTask(context, "Test001.txt", "text/plain", bytes)).start();
        }
        catch (Exception e) {
            Log.e(TAG, "Exception! " + e.getMessage());
        }
    }
}
