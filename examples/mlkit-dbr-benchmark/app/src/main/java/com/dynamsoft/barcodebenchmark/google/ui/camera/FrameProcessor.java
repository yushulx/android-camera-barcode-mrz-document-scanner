package com.dynamsoft.barcodebenchmark.google.ui.camera;

import java.nio.ByteBuffer;

public interface FrameProcessor {
    void process(ByteBuffer data, int width, int height, int rotationDegrees);
}
