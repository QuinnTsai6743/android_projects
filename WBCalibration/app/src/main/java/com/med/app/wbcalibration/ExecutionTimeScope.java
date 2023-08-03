package com.med.app.wbcalibration;

import android.util.Log;

import java.util.ArrayList;
import java.util.Locale;

public class ExecutionTimeScope {
    private static final String TAG = IConstant.TAG;

    private final ArrayList<String> mTimestampList = new ArrayList<>();
    private final String mTag;
    private long mStartTime;

    ExecutionTimeScope(String tag) {
        mTag = tag;
    }

    public void begin() {
        mTimestampList.clear();
        mStartTime = System.currentTimeMillis();
        addStamp("-- begin --");
    }

    public void end() {
        long endTime = System.currentTimeMillis();
        addStamp("-- end --");
        for (String s : mTimestampList) {
            Log.d(TAG, s);
        }
        Log.d(TAG, String.format("[%s] cost %d ms", mTag, endTime-mStartTime));
    }

    public void addStamp(String message) {
        mTimestampList.add(String.format(Locale.US, "%d [%s] %s", System.currentTimeMillis(), mTag, message));
    }
}
