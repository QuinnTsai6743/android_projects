/**
 * File: WBCalibration.java
 * Brief: This class provide a method to do white-balance calibration.
 *
 * Name:
 * Date:
 */

package com.med.hpframework.util;

import android.hardware.camera2.CameraCharacteristics;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * This class provide a method to do white-balance calibration.
 * You should give the width, height and color filter arrangement which
 * can be obtained from CameraCharacteristics when creating an instance.
 */
public class WBCalibration {
    /**
     * The logging tag used by this class.
     */
    private static final String TAG = WBCalibration.class.getSimpleName();

    /**
     * Holds the value of sensor array width.
     */
    private final int mSensorWidth;

    /**
     * Holds the value of sensor array height.
     */
    private final int mSensorHeight;

    /**
     * The color filter arrangement. Obtained from CameraCharacteristics.
     */
    private final int mColorFilter;

    /**
     * The number of color channels.
     */
    private static final float NUM_OF_CHANNELS = 4.0f;

    /**
     * A simple constructor.
     * @param sensorWidth The width of sensor array.
     * @param sensorHeight The height of sensor array.
     * @param colorFilter The color filter arrangement of sensor array.
     */
    public WBCalibration(int sensorWidth, int sensorHeight, int colorFilter) {
        mSensorWidth = sensorWidth;
        mSensorHeight = sensorHeight;
        mColorFilter = colorFilter;
    }

    /**
     * To do the white-balance calibration and pass the result.
     * @param byteRaw A 16-bit RAW data for calibration.
     * @param callback A callback to pass the calibration result.
     */
    public void calibrate(byte[] byteRaw, ResultCallback callback) {
        int sumOfLeftTop = 0;
        int sumOfRightTop = 0;
        int sumOfLeftBottom = 0;
        int sumOfRightBottom = 0;
        float K;
        float factorR, factorG, factorB;
        float gainR, gainB;

        Log.d(TAG, String.format("Sensor active array: %d x %d, color filter: %d", mSensorWidth, mSensorHeight, mColorFilter));

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
        // get the factorR, factorG and factorB
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
        gainR = factorR / factorG;
        gainB = factorB / factorG;
        Log.d(TAG, String.format("gain R: %f,  B: %f", gainR, gainB));
        if (callback != null) {
            callback.onCalibrationDone(gainR, gainB);
        }
    }

    /**
     * Interface definition for a callback to provide the calibration result.
     */
    public interface ResultCallback {
        /**
         * Called when the white-balance calibration is done.
         * @param gainR The R gain.
         * @param gainB The B gain.
         */
        void onCalibrationDone(float gainR, float gainB);
    }
}
