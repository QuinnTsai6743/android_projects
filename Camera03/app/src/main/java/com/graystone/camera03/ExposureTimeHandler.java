package com.graystone.camera03;

import android.util.Log;
import android.view.View;

public class ExposureTimeHandler implements View.OnClickListener, InputDialog.EventListener {
    private static final String TAG = "Camera03";

    private ICameraControl mCameraControl = null;
//    private boolean mUsingExposureLine = true;
    private final long NANOSECONDS_PER_SECOND = 1000000000;
    private final long NANOSECONDS_PER_MILLISECOND = 1000000;
    private double mFrameRate = 30.0;
    private int mVTS = 732;

    public void setCameraControl(ICameraControl cameraControl) {
        mCameraControl = cameraControl;
    }

    public void setVTS(int VTS) {
        mVTS = VTS;
    }

    public void setFrameRate(double frameRate) {
        mFrameRate = frameRate;
    }

//    public void usingExposureLine(boolean enable) {
//        mUsingExposureLine = enable;
//    }

    private long linesToNanoseconds(int lines) {
        long exposureTime = Double.valueOf((lines * NANOSECONDS_PER_SECOND) / (mVTS * mFrameRate)).longValue() + 1;
//        Log.d(TAG, "Calculated exposure time: " + exposureTime);
        return exposureTime;
    }

    private int nanosecondsToLines(long nanoseconds) {
        int lines = Double.valueOf((nanoseconds * (mVTS * mFrameRate)) / NANOSECONDS_PER_SECOND).intValue();
//        Log.d(TAG, "Calculated exposure lines: " + lines);
        return lines;
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        Log.d(TAG, "View Id = " + id);
        String name = v.getResources().getResourceEntryName(v.getId());
        Log.d(TAG, "Name= " + name);
        if ("btnExpDec".equals(name)) {
            if (mCameraControl != null) {
                long exposureTime = mCameraControl.getExposureTime();
                int lines = nanosecondsToLines(exposureTime);
                int newLines = (lines > 4)? lines - 1 : 4;
                Log.d(TAG, "Change Exp. lines: " + lines + " --> " + newLines);
                mCameraControl.setExposureTime(linesToNanoseconds(newLines));
            }
        }
        else if ("btnExpInc".equals(name)) {
            if (mCameraControl != null) {
                long exposureTime = mCameraControl.getExposureTime();
                int lines = nanosecondsToLines(exposureTime);
                int newLines = (lines > mVTS)? mVTS : lines + 1;
                Log.d(TAG, "Change Exp. lines: " + lines + " --> " + newLines);
                mCameraControl.setExposureTime(linesToNanoseconds(newLines));
            }
        }
    }

    @Override
    public void onOkClick(String text, String tag) {
        if ("ExpLineInputDialog".equals(tag)) {
            Log.i(TAG, "Exposure Lines string=" + text);
            try {
                int lines = Integer.parseInt(text);
                Log.d(TAG, "New Exp. lines= " + lines);
                mCameraControl.setExposureTime(linesToNanoseconds(lines));
            }
            catch (NumberFormatException e) {
                Log.e(TAG, "NumberFormatException!");
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onCancelClick(String tag) {

    }
}
