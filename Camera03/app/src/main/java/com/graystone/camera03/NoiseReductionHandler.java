package com.graystone.camera03;

import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

public class NoiseReductionHandler implements AdapterView.OnItemSelectedListener {
    private final String TAG = "Camera03";
    private final INoiseReduction mNoiseReduction;

    NoiseReductionHandler(Spinner spinner, ArrayAdapter<CharSequence> adapter, INoiseReduction noiseReduction) {
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(this);
        mNoiseReduction = noiseReduction;
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        Log.d(TAG, String.format("onItemSelected() position = %d, id = %d", position, id));
        ((TextView)parent.getChildAt(0)).setTextSize(8);
        String noiseReductionMode = (String)parent.getItemAtPosition(position);
        Log.i(TAG, "selected noise reduction mode: " + noiseReductionMode);
        mNoiseReduction.setNoiseReduction(noiseReductionMode);
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        Log.d(TAG, "[NoiseReductionController] onNothingSelected()");
    }
}
