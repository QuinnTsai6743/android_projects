package com.graystone.camera03;

import android.hardware.camera2.params.TonemapCurve;
import android.util.Log;

import java.util.HashMap;

public class ToneCurveController {
    private static final String TAG = "Camera03";

    private static final float[] mOriginCurve = new float[] {
            0.0f,        0.0f,        0.032258064f, 0.08993158f, 0.06451613f, 0.19061583f, 0.09677419f, 0.27077225f,
            0.12903225f, 0.3431085f,  0.16129032f,  0.39687195f, 0.19354838f, 0.44770283f, 0.22580644f, 0.4907136f,
            0.2580645f,  0.5356794f,  0.29032257f,  0.57087f,    0.32258064f, 0.6041056f,  0.3548387f,  0.6344086f,
            0.38709676f, 0.6656892f,  0.41935483f,  0.6911046f,  0.4516129f,  0.714565f,   0.48387095f, 0.7380254f,
            0.516129f,   0.7614858f,  0.5483871f,   0.7820137f,  0.58064514f, 0.80254155f, 0.61290324f, 0.82013685f,
            0.6451613f,  0.83968717f, 0.67741936f,  0.856305f,   0.7096774f,  0.87194526f, 0.7419355f,  0.88856304f,
            0.7741935f,  0.9042033f,  0.8064516f,   0.9178886f,  0.83870965f, 0.9325513f,  0.87096775f, 0.94525903f,
            0.9032258f,  0.9599218f,  0.9354839f,   0.97262955f, 0.9677419f,  0.98533726f, 1.0f,        1.0f
    };

    private static final HashMap<Integer, Float> mContrastFactorMap = new HashMap<Integer, Float>() {{
        put(-4, 0.68f);
        put(-3, 0.76f);
        put(-2, 0.84f);
        put(-1, 0.92f);
        put(0, 1.0f);
        put(1, 1.08f);
        put(2, 1.16f);
        put(3, 1.24f);
        put(4, 1.32f);
    }};

    private static final HashMap<Integer, Float> mBrightnessFactorMap = new HashMap<Integer, Float>() {{
        put(-4, -0.24f);
        put(-3, -0.18f);
        put(-2, -0.12f);
        put(-1, -0.06f);
        put(0, 0.0f);
        put(1, 0.06f);
        put(2, 0.12f);
        put(3, 0.18f);
        put(4, 0.24f);
    }};

    /**
     * Generate a new tone curve for the brightness and contrast adjustment.
     * @param brightness The level of the brightness. Its range is from -4 to +4
     * @param contrast The level of the contrast. Its range is from -4 to +4.
     * @return The new tone map curve.
     */
    private float[] generate_curve(int brightness, int contrast) {
        int arraySize = mOriginCurve.length;
        float [] result = new float[arraySize];
        Log.i(TAG, String.format("generate_curve() brightness=%d  contrast=%d", brightness, contrast));

//        Float contrastF = mContrastFactorMap.get(contrast);
//        float contrastFactor = (contrastF == null)? 1.0f : contrastF;
        float contrastFactor = 1.0f + (contrast * 0.08f);
        Float brightnessF = mBrightnessFactorMap.get(brightness);
        float brightnessFactor = (brightnessF == null)? 0.0f : brightnessF;

        for (int idx=0; idx<arraySize; idx+=2) {
            // input node of the tone curve
            result[idx] = mOriginCurve[idx];
            // output node of the tone curve
            result[idx+1] = Math.min(0.5f, Math.max(-0.5f, (mOriginCurve[idx+1] - 0.5f) * contrastFactor + brightnessFactor)) + 0.5f;
        }

        return result;
    }

    /**
     * Generates a tone curve for the contrast adjustment.
     * @param inputCurve The base tone curve.
     * @param contrastFactor The factor to adjust contrast. Its valid range is from 0.5 to 1.5.
     * @return The new tone curve.
     */
    private float [] generate_tone_curve(float[] inputCurve, float contrastFactor) {
        float [] outputCurve = inputCurve.clone();
        float validContrastFactor = Math.min(1.5f, Math.max(0.5f, contrastFactor));

        for (int idx=0; idx<inputCurve.length; idx+=2) {
            // output node of the tone curve
            outputCurve[idx+1] = Math.min(0.5f, Math.max(-0.5f, (inputCurve[idx+1] - 0.5f) * validContrastFactor)) + 0.5f;
        }

        return outputCurve;
    }

    /**
     * Get a new tone map curve for the brightness and contrast adjustment.
     * @param brightness The level of the brightness. Valid range is from -4 to +4.
     * @param contrast The level of the contrast. Valid range is from -4 to +4.
     * @return The new tone map curve.
     */
    public TonemapCurve getToneMapCurve(int brightness, int contrast) {
//        float [] toneMapCurve = generate_curve(brightness, contrast);
        float contrastFactor = 1.0f + (contrast * 0.08f);
        float [] toneMapCurve = generate_tone_curve(mOriginCurve, contrastFactor);
        return new TonemapCurve(toneMapCurve, toneMapCurve, toneMapCurve);
    }
}
