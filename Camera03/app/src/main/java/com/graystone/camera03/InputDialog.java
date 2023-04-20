package com.graystone.camera03;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.widget.EditText;

public class InputDialog implements DialogInterface.OnClickListener {

    interface EventListener {
        void OnOk(String text, String tag);
        void OnCancel(String tag);
    }

    private final EditText mEditText;
    private final AlertDialog mDialog;
    private final String mTag;
    private final EventListener mListener;

    InputDialog(Context context, String tag, String title, EventListener listener) {
        mTag = tag;
        mListener = listener;
        mEditText = new EditText(context);
        mDialog = new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage("---")
                .setView(mEditText)
                .setPositiveButton("Ok", this)
                .setNegativeButton("Cancel", this)
                .create();
    }

    public void show() {
        mDialog.show();
    }

    public void setMessage(CharSequence message) {
        mDialog.setMessage(message);
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            String inputText = String.valueOf(mEditText.getText());
            mListener.OnOk(inputText, mTag);
        }
        else if (which == DialogInterface.BUTTON_NEGATIVE) {
            mListener.OnCancel(mTag);
        }
    }
}
