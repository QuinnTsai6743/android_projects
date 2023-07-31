package com.graystone.camera03;

import android.graphics.Rect;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.params.RggbChannelVector;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class AWBCalculator {
    private static final String TAG = "AWBCalculator";
    private static final float WBFactor = 1.0f;
    private static final float EvenGreen = 1.0f;
    private static final float OddGreen = 1.0f;

    private final int mSensorWidth;
    private final int mSensorHeight;
    private final Rect mRoi;
    private float mGainB = 1.0f;
    private float mGainR = 1.0f;

    private final int mColorFilter;

    /**
     * Constructor.
     * @param sensorWidth Sensor width.
     * @param sensorHeight Sensor height.
     * @param sensorColorFilter Color filter type. (CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT)
     */
    public AWBCalculator(int sensorWidth, int sensorHeight, int sensorColorFilter) {
        mColorFilter = sensorColorFilter;
        mSensorWidth = sensorWidth;
        mSensorHeight = sensorHeight;
        mRoi = new Rect(
                sensorWidth/4,
                sensorHeight/4,
                sensorWidth-(sensorWidth/4),
                sensorHeight-(sensorHeight/4));
    }

    /**
     * This method is used to get the R/B gains
     * @return A RggbChannelVector object contained the result.
     */
    public RggbChannelVector getWbGains() {
        return new RggbChannelVector(mGainR, EvenGreen, OddGreen, mGainB);
    }

    /**
     * To do WB calibration.
     * @param rawData The RAW data for calibrating WB gains.
     */
    public void calculate(byte[] rawData) {
//        algorithm1(rawData, mSensorWidth, mSensorHeight, mRoi);
        algorithm2(rawData, mSensorWidth, mSensorHeight);
    }

    /**
     * Calibration algorithm 1.
     * @param rawData Sensor RAW data.
     * @param rawWidth Sensor width.
     * @param rawHeight Sensor height.
     * @param roi Specified the ROI for WB calibration.
     */
    private void algorithm1(byte[] rawData, int rawWidth, int rawHeight, Rect roi) {
        Log.i(TAG, String.format("length of RAW: %d", rawData.length));
        Log.i(TAG, String.format("Sensor dimension: %d x %d", rawWidth, rawHeight));
        Log.i(TAG, "ROI: " + roi.toString());

        // byte array to short array (for 16-bit RAW)
        short[] shortRaw = new short[rawData.length / 2];
        ByteBuffer.wrap(rawData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shortRaw);

        PixelSum pixelSum = new PixelSum(mColorFilter);

        for (int row=0; row<rawHeight; row++) {
            for (int col = 0; col < rawWidth; col++) {
                if (roi.contains(row, col)) {
                    int idx = row * rawHeight + col;
                    pixelSum.add(row, col, shortRaw[idx]);
                }
            }
        }

        Log.i(TAG, String.format("Avg.  B: %f", pixelSum.getAverageOfB()));
        Log.i(TAG, String.format("Avg. Gb: %f", pixelSum.getAverageOfGb()));
        Log.i(TAG, String.format("Avg. Gr: %f", pixelSum.getAverageOfGr()));
        Log.i(TAG, String.format("Avg.  R: %f", pixelSum.getAverageOfR()));
        mGainB = WBFactor * pixelSum.getAverageOfGb() / pixelSum.getAverageOfB();
        mGainR = WBFactor * pixelSum.getAverageOfGr() / pixelSum.getAverageOfR();
        Log.i(TAG, String.format("WB gain, R: %f,  B: %f", mGainR, mGainB));
    }

    /**
     * Calibration Algorithm.
     * @param byteRaw Sensor RAW data.
     * @param rawWidth Sensor width.
     * @param rawHeight Sensor height.
     */
    private void algorithm2(byte[] byteRaw, int rawWidth, int rawHeight) {
        short[] shortRaw = new short[byteRaw.length / 2];
        ByteBuffer.wrap(byteRaw).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shortRaw);

        PixelSum pixelSum = new PixelSum(mColorFilter);

        for (int row=0; row<rawHeight; row++) {
            for (int col=0; col<rawWidth; col++) {
                int idx = row * rawHeight + col;
                pixelSum.add(row, col, shortRaw[idx]);
            }
        }

        float K = (pixelSum.getSumOfR() + pixelSum.getSumOfGr() + pixelSum.getSumOfGb() + pixelSum.getSumOfB()) / 4.0f;
        float gainR = K / pixelSum.getSumOfR();
        float gainGr = K / pixelSum.getSumOfGr();
        float gainGb = K / pixelSum.getSumOfGb();
        float gainB = K / pixelSum.getSumOfB();
        float gainG = Math.min(gainGr, gainGb);
        mGainB = gainB / gainG;
        mGainR = gainR / gainG;
        Log.i(TAG, String.format("Algo2: R: %f,  Gr: %f,  Gb: %f,  B: %f", mGainR, gainGr/gainG, gainGb/gainG, mGainB));
    }

    /**
     * To count the sum and average of pixel values.
     */
    static class IntSum {
        private int mSum = 0;
        private int mCount = 0;

        void add(int value) {
            mSum += value;
            mCount += 1;
        }

        float getAverage() { return (float)mSum / (float)mCount; }
        int getSum() { return mSum; }
    }

    /**
     * To count the sum and average by position of pixel.
     */
    static class PixelSum {
        private final IntSum mSumLeftTop = new IntSum();
        private final IntSum mSumRightTop = new IntSum();
        private final IntSum mSumLeftBottom = new IntSum();
        private final IntSum mSumRightBottom = new IntSum();
        private final IntSum mR;
        private final IntSum mGr;
        private final IntSum mGb;
        private final IntSum mB;

        PixelSum(int colorFilter) {
            switch(colorFilter)
            {
                case CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGGB:
                    mR = mSumLeftTop;
                    mGr = mSumRightTop;
                    mGb = mSumLeftBottom;
                    mB = mSumRightBottom;
                    break;
                case CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GRBG:
                    mGr = mSumLeftTop;
                    mR = mSumRightTop;
                    mB = mSumLeftBottom;
                    mGb = mSumRightBottom;
                    break;
                case CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GBRG:
                    mGb = mSumLeftTop;
                    mB = mSumRightTop;
                    mR = mSumLeftBottom;
                    mGr = mSumRightBottom;
                    break;
                default:
                    mB = mSumLeftTop;
                    mGb = mSumRightTop;
                    mGr = mSumLeftBottom;
                    mR = mSumRightBottom;
            }
        }

        void add(int row, int col, int value) {
            if (row%2 == 0) {
                if (col%2 == 0) {
                    mSumLeftTop.add(value);
                }
                else {
                    mSumRightTop.add(value);
                }
            }
            else {
                if (col%2 == 0) {
                    mSumLeftBottom.add(value);
                }
                else {
                    mSumRightBottom.add(value);
                }
            }
        }

        float getAverageOfR() { return mR.getAverage(); }
        float getAverageOfGr() { return mGr.getAverage(); }
        float getAverageOfGb() { return mGb.getAverage(); }
        float getAverageOfB() { return mB.getAverage(); }

        int getSumOfR() { return mR.getSum(); }
        int getSumOfGr() { return mGr.getSum(); }
        int getSumOfGb() { return mGb.getSum(); }
        int getSumOfB() { return mB.getSum(); }
    }
}
