package com.graystone.camera03;

import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

public class EdgeModeHandler implements AdapterView.OnItemSelectedListener {
    private final String TAG = "Camera03";
    private final IEdgeMode mEdgeMode;

    EdgeModeHandler(Spinner spinner, ArrayAdapter<CharSequence> adapter, IEdgeMode edgeMode) {
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(this);
        mEdgeMode = edgeMode;
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        Log.d(TAG, String.format("onItemSelected() position = %d, id = %d", position, id));
        ((TextView)parent.getChildAt(0)).setTextSize(8);
        String edgeMode = (String)parent.getItemAtPosition(position);
        Log.i(TAG, "selected edge mode: " + edgeMode);
        mEdgeMode.setEdgeMode(edgeMode);
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        Log.d(TAG, "[EdgeModeController] onNothingSelected()");
    }
}
