package com.graystone.camera03;

public interface ICameraControl {
    void setExposureTime(long nanosecond);
    long getExposureTime();
    void setIso(int iso);
    int  getIso();
    boolean autoExposureEnabled();
    void setSaturationLevel(int level);
    void setBrightnessLevel(int level);
    void setContrastLevel(int level);
    void setBrightnessAndContrast(int brightness, int contrast);
}
