package com.graystone.camera03;

import android.hardware.camera2.params.ColorSpaceTransform;
import android.util.Log;

import androidx.annotation.NonNull;

public class ColorCorrectionController {
    private final static String TAG = "Camera03";
    private final static ColorSpaceTransform[] mColorSpaceTransformArray = new ColorSpaceTransform [] {
            // Saturation -4
            new ColorSpaceTransform(new int[] {
                    334050, 1000000, 557650, 1000000, 108300, 1000000,
                    284050, 1000000, 607650, 1000000, 108300, 1000000,
                    284050, 1000000, 557650, 1000000, 158300, 1000000
            }),
            // Saturation -3
            new ColorSpaceTransform(new int[] {
                    474250, 1000000, 440250, 1000000,  85500, 1000000,
                    224250, 1000000, 690250, 1000000,  85499, 1000000,
                    224250, 1000000, 440250, 1000000, 335500, 1000000
            }),
            // Saturation -2
            new ColorSpaceTransform(new int[] {
                    649500, 1000000, 293500, 1000000, 56999, 1000000,
                    149500, 1000000, 793501, 1000000, 56999, 1000000,
                    149500, 1000000, 293500, 1000000, 557000, 1000000
            }),
            // Saturation -1
            new ColorSpaceTransform(new int[] {
                    824751, 1000000, 146751, 1000000,  28499, 1000000,
                     74750, 1000000, 896751, 1000000,  28498, 1000000,
                     74750, 1000000, 146750, 1000000, 778500, 1000000
            }),
            // Saturation 0
            new ColorSpaceTransform(new int[] {
                    10, 10,   0, 10,   0, 10,
                     0, 10,  10, 10,   0, 10,
                     0, 10,   0, 10,  10, 10
            }),
            // Saturation +1
            new ColorSpaceTransform(new int[] {
                    1070101, 1000000,  -58699, 1000000,  -11402, 1000000,
                     -29900, 1000000, 1041302, 1000000,  -11402, 1000000,
                     -29900, 1000000,  -58700, 1000000, 1088600, 1000000
            }),
            // Saturation +2
            new ColorSpaceTransform(new int[] {
                    1140201, 1000000, -117399, 1000000,  -22802, 1000000,
                     -59800, 1000000, 1082602, 1000000,  -22803, 1000000,
                     -59800, 1000000, -117400, 1000000, 1177200, 1000000
            }),
            // Saturation +3
            new ColorSpaceTransform(new int[] {
                    1210301, 1000000, -176099, 1000000,  -34202, 1000000,
                     -89700, 1000000, 1123903, 1000000,  -34203, 1000000,
                     -89700, 1000000, -176100, 1000000, 1265800, 1000000
            }),
            // Saturation +4
            new ColorSpaceTransform(new int[] {
                    1280401, 1000000, -234799, 1000000,  -45602, 1000000,
                    -119600, 1000000, 1165203, 1000000,  -45603, 1000000,
                    -119600, 1000000, -234800, 1000000, 1354400, 1000000
            })
    };

    public static ColorSpaceTransform getColorSpaceTransform(int level) {
        Log.d(TAG, "SAT = " + level);
        if (level < 0)  level = 0;
        if (level > 8)  level = 8;
        return mColorSpaceTransformArray[level];
    }


    private final float [] mRgbToYuv = new float [] {
            0.299f, -0.169f, 0.5f,
            0.587f, -0.331f, -0.419f,
            0.114f, 0.5f, -0.081f
    };

    private final float [] mYuvToRgb = new float [] {
            1.0f, 1.0f, 1.0f,
            -0.00093f, -0.3437f, 1.77216f,
            1.401687f, -0.71417f, 0.00099f
    };

    private final float [] mAdjustMatrix = new float[] {
            1.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 0.0f,
            0.0f, 0.0f, 1.0f
    };

    public int [] getAdaptedColorSpaceTransform(int [] elements, float saturation) {
        int [] result = new int[18];
        float [] cc = new float[9];
        mAdjustMatrix[0] = 1.0f;
        mAdjustMatrix[4] = saturation;
        mAdjustMatrix[8] = saturation;
        for (int i=0; i<9; i++) {
            cc[i] = elements[i*2] / (float)elements[i*2+1];
        }
//        cc[0] = elements[0] / (float)elements[1];
//        cc[1] = elements[2] / (float)elements[3];
//        cc[2] = elements[4] / (float)elements[5];
//        cc[3] = elements[6] / (float)elements[7];
//        cc[4] = elements[8] / (float)elements[9];
//        cc[5] = elements[10] / (float)elements[11];
//        cc[6] = elements[12] / (float)elements[13];
//        cc[7] = elements[14] / (float)elements[15];
//        cc[8] = elements[16] / (float)elements[17];

        float [] rgb = Transform3x3Matrix(Multiply3x3Matrix(Multiply3x3Matrix(Multiply3x3Matrix(cc, mRgbToYuv), mAdjustMatrix), mYuvToRgb));
        for (int i=0; i<9; i++) {
            result[i*2] = Math.round(rgb[i] * 1000000.0f);
            result[i*2+1] = 1000000;
        }
        return result;
    }

    private float [] Multiply3x3Matrix(float [] m1, float [] m2) {
        float [] result = new float[9];

        for (int col=0; col<3; col++) {
            for (int row=0; row<3; row++) {
                result[col*3+row] = m1[col*3] * m2[row] + m1[col*3+1] * m2[row+3] + m1[col*3+2] * m2[row+6];
            }
        }

//        result[0] = m1[0] * m2[0] + m1[1] * m2[3] + m1[2] * m2[6];
//        result[1] = m1[0] * m2[1] + m1[1] * m2[4] + m1[2] * m2[7];
//        result[2] = m1[0] * m2[2] + m1[1] * m2[5] + m1[2] * m2[8];
//
//        result[3] = m1[3] * m2[0] + m1[4] * m2[3] + m1[5] * m2[6];
//        result[4] = m1[3] * m2[1] + m1[4] * m2[4] + m1[5] * m2[7];
//        result[5] = m1[3] * m2[2] + m1[4] * m2[5] + m1[5] * m2[8];
//
//        result[6] = m1[6] * m2[0] + m1[7] * m2[3] + m1[8] * m2[6];
//        result[7] = m1[6] * m2[1] + m1[7] * m2[4] + m1[8] * m2[7];
//        result[8] = m1[6] * m2[2] + m1[7] * m2[5] + m1[8] * m2[8];

        return result;
    }

    private float [] Transform3x3Matrix(float [] m1) {
        float [] result = new float[9];
        for (int col=0; col<3; col++) {
            for (int row=0; row<3; row++) {
                result[col*3+row] = m1[row*3+col];
            }
        }
        return result;
    }
}
