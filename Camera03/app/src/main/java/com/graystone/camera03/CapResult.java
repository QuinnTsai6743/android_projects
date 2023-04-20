package com.graystone.camera03;

import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.params.ColorSpaceTransform;
import android.hardware.camera2.params.RggbChannelVector;
import android.util.Log;

import java.util.Arrays;

public class CapResult {
    private int mIso;
    private long mExposureTime;
    private long mFrameNo;
    private float mRGain;
    private float mGEvenGain;
    private float mGOddGain;
    private float mBGain;
    private int[] mColorTransform;
    private int mAwbState;

    public static class Builder {
        static final String TAG = "Camera03";

        private int mIso = 100;
        private long mExposureTime = 5000000L;
        private long mFrameNo = 0L;
        private float mRGain = 1.0f;
        private float mGEvenGain = 1.0f;
        private float mGOddGain = 1.0f;
        private float mBGain = 1.0f;
        private int[] mColorTransform;
        private int mAwbState = CaptureResult.CONTROL_AWB_STATE_INACTIVE;

        public Builder() {

        }

        public Builder setIso(int iso) {
            mIso = iso;
            return this;
        }

        public Builder setExposureTime(long exposureTime) {
            mExposureTime = exposureTime;
            return this;
        }

        public Builder setFrameNo(long frameNo) {
            mFrameNo = frameNo;
            return this;
        }

        public Builder setWbGains(RggbChannelVector vector) {
            mRGain = vector.getRed();
            mGEvenGain = vector.getGreenEven();
            mGOddGain = vector.getGreenOdd();
            mBGain = vector.getBlue();
            return this;
        }

        public Builder setColorTransform(ColorSpaceTransform colorSpaceTransform) {
            mColorTransform = new int[18];
            colorSpaceTransform.copyElements(mColorTransform, 0);
            return this;
        }

        public Builder setAwbState(Integer awbState) {
            mAwbState = awbState;
            return this;
        }

        CapResult build() {
            CapResult capResult = new CapResult();
            capResult.mIso = mIso;
            capResult.mExposureTime = mExposureTime;
            capResult.mFrameNo = mFrameNo;
            capResult.mRGain = mRGain;
            capResult.mGEvenGain = mGEvenGain;
            capResult.mGOddGain = mGOddGain;
            capResult.mBGain = mBGain;
            capResult.mColorTransform = mColorTransform.clone();
            capResult.mAwbState = mAwbState;
            return capResult;
        }
    }

    public int getIso() {
        return mIso;
    }

    public long getExposureTime() {
        return mExposureTime;
    }

    public long getFrameNo() {
        return mFrameNo;
    }

    public float[] getWbGains() {
        return new float[] {mRGain, mGEvenGain, mGOddGain, mBGain};
    }

    public int[] getColorTransform() {
        return mColorTransform.clone();
    }

    public int getAwbState() {
        return mAwbState;
    }
}
