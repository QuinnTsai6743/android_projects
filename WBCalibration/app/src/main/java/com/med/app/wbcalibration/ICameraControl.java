package com.med.app.wbcalibration;

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.SessionConfiguration;

public interface ICameraControl {
    void createCaptureSession(SessionConfiguration config);
    CaptureRequest.Builder getBuilder(int templateType) throws CameraAccessException;
}
