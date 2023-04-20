package com.graystone.camera03;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.widget.Toast;

public class UiHandler extends Handler {
    private final Context mContext;

    UiHandler(Context context, Looper looper) {
        super(looper);
        mContext = context;
    }

    @Override
    public void handleMessage(Message msg) {
        super.handleMessage(msg);

        if (msg.what == 0) {
            String pathname = (String)msg.obj;
            Toast.makeText(mContext, pathname, Toast.LENGTH_SHORT).show();
        }
    }
}
