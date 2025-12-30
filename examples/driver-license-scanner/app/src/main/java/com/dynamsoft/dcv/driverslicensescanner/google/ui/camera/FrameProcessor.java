package com.dynamsoft.dcv.driverslicensescanner.google.ui.camera;

import java.nio.ByteBuffer;

public interface FrameProcessor {
    void process(ByteBuffer data, int width, int height, int rotationDegrees);
}
