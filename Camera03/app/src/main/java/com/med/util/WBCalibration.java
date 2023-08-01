package com.med.util;

import android.hardware.camera2.CameraCharacteristics;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class WBCalibration {
    private static final String TAG = WBCalibration.class.getSimpleName();

    private final int mSensorWidth;
    private final int mSensorHeight;
    private final int mColorFilter;

    private static final float NUM_OF_CHANNELS = 4.0f;

    private float mGainR = 1.0f;
    private float mGainB = 1.0f;

    public WBCalibration(int sensorWidth, int sensorHeight, int colorFilter) {
        mSensorWidth = sensorWidth;
        mSensorHeight = sensorHeight;
        mColorFilter = colorFilter;
    }

    /**
     * WhiteBalance Calibration
     * @param byteRaw 16-bit RAW data
     */
    public void calibrate(byte[] byteRaw) {
        int sumOfLeftTop = 0;
        int sumOfRightTop = 0;
        int sumOfLeftBottom = 0;
        int sumOfRightBottom = 0;
        float K;
        float factorR;
        float factorG;
        float factorB;

        Log.i(TAG, String.format("Sensor active array: %d x %d, color filter: %d", mSensorWidth, mSensorHeight, mColorFilter));

        // from byte to short for 16-bit RAW
        short[] shortRaw = new short[byteRaw.length / 2];
        ByteBuffer.wrap(byteRaw).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shortRaw);

        for (int row=0; row<mSensorHeight; row++) {
            for (int col=0; col<mSensorWidth; col++) {
                int idx = row * mSensorHeight + col;
                if (row%2 == 0) {
                    if (col%2 == 0) {
                        sumOfLeftTop += shortRaw[idx];
                    }
                    else {
                        sumOfRightTop += shortRaw[idx];
                    }
                }
                else {
                    if (col%2 == 0) {
                        sumOfLeftBottom += shortRaw[idx];
                    }
                    else {
                        sumOfRightBottom += shortRaw[idx];
                    }
                }
            }
        }

        // get the value of K
        K = (sumOfLeftTop + sumOfRightTop + sumOfLeftBottom + sumOfRightBottom) / NUM_OF_CHANNELS;
        switch(mColorFilter)
        {
            case CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGGB:
                factorR = K / (float)sumOfLeftTop;
                factorG = K / ((sumOfRightTop + sumOfLeftBottom)/2.0f);
                factorB = K / (float)sumOfRightBottom;
                break;
            case CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GRBG:
                factorR = K / (float)sumOfRightTop;
                factorG = K / ((sumOfLeftTop + sumOfRightBottom)/2.0f);
                factorB = K / (float)sumOfLeftBottom;
                break;
            case CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GBRG:
                factorR = K / (float)sumOfLeftBottom;
                factorG = K / ((sumOfLeftTop + sumOfRightBottom)/2.0f);
                factorB = K / (float)sumOfRightTop;
                break;
            case CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_BGGR:
            default:
                factorR = K / (float)sumOfRightBottom;
                factorG = K / ((sumOfRightTop + sumOfLeftBottom)/2.0f);
                factorB = K / (float)sumOfLeftTop;
        }

        Log.d(TAG, String.format("factor R: %f,  G: %f,  B: %f", factorR, factorG, factorB));
        mGainR = factorR / factorG;
        mGainB = factorB / factorG;
        Log.i(TAG, String.format("gain R: %f,  B: %f", mGainR, mGainB));
    }

    public float getGainR() { return mGainR; }
    public float getGainB() { return mGainB; }
}
