# android-camera2-preview-qr-code-scanner
This is a skeleton project that demonstrates how to implement a basic Android camera preview by using [CameraX](https://developer.android.com/training/camerax) and [Dynamsoft Camera Enhancer](https://www.dynamsoft.com/camera-enhancer/docs/programming/android/guide/guide.html?ver=latest) respectively. The project also features QR code scan. You can replace this part with other image processing code.

## How to Enable QR Code Scan
Apply for a [valid license key](https://www.dynamsoft.com/customer/license/trialLicense?product=dbr) to activate Dynamsoft Barcode Reader:
    
```java
reader.initLicense("LICENSE-KEY"); 
```

## How to Get Camera Frames to Do Image Processing

**CameraX**

```java
analysisUseCase.setAnalyzer(cameraExecutor,
    imageProxy -> {
        // image processing
        Log.i(TAG, "image processing.................");
        // image processing

        // Must call close to keep receiving frames.
        imageProxy.close();
    });
```

**Dynamsoft Camera Enhancer**

```java
@Override
public void frameOutputCallback(DCEFrame dceFrame, long l) {
    // image processing
    Log.i(TAG, "image processing.................");
    // image processing
}
```
