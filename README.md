# android-camera2-preview-qr-code-scanner
This is a skeleton project that demonstrates how to implement a basic Android camera preview by using [CameraX](https://developer.android.com/training/camerax) and [Dynamsoft Camera Enhancer](https://www.dynamsoft.com/camera-enhancer/docs/programming/android/guide/guide.html?ver=latest) respectively. The project also features QR code scan. You can replace this part with other image processing code.

![Android QR Code Scanner](https://www.dynamsoft.com/codepool/img/2021/12/android-qr-code-scanner-yolo.webp)

## Features
- Camera preview
- QR code scanning by Dynamsoft Barcode Reader
- Result overlay
- Auto-torch
- Zoom by fingers
- QR code detection by YOLO tiny

## How to Enable QR Code Scan
Apply for a [valid license key](https://www.dynamsoft.com/customer/license/trialLicense?product=dbr) to activate Dynamsoft Barcode Reader:
    
```java
BarcodeReader.initLicense(
"DLS2eyJoYW5kc2hha2VDb2RlIjoiMjAwMDAxLTE2NDk4Mjk3OTI2MzUiLCJvcmdhbml6YXRpb25JRCI6IjIwMDAwMSIsInNlc3Npb25QYXNzd29yZCI6IndTcGR6Vm05WDJrcEQ5YUoifQ==",
    new DBRLicenseVerificationListener() {
        @Override
        public void DBRLicenseVerificationCallback(boolean isSuccessful, Exception e) {
        }
    });
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



## Blog
- [The Quickest Way to Create an Android QR Code Scanner](https://www.dynamsoft.com/codepool/android-qr-code-scanner.html)
- [Real-time Android QR Code Recognition with YOLO and Dynamsoft Barcode Reader](https://www.dynamsoft.com/codepool/android-qr-code-recognition-yolo-dynamsoft-barcode.html)
- [Android QR Code Detection with TensorFlow Lite](https://www.dynamsoft.com/codepool/tensorflow-lite-android-qr-code-detection-localization.html)
