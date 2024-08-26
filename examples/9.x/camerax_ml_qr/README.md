# Android Camera Preview and QR Code Scanner
This project provides a basic implementation of an Android camera preview using both [CameraX](https://developer.android.com/training/camerax) and [Dynamsoft Camera Enhancer v2.x](https://www.dynamsoft.com/camera-enhancer/docs/mobile/programming/android/guide/guide.html). It also demonstrates QR code scanning functionality, which can be replaced with other image processing SDKs as needed.

https://github.com/user-attachments/assets/00ca0807-6691-4345-9cd8-f2385a532690

## Features
- **Camera Preview**: Seamless camera preview implementation.
- **QR Code Scanning**: Utilizes Dynamsoft Barcode Reader for efficient QR code detection.
- **Result Overlay**: Displays scan results directly on the camera preview.
- **Auto-Torch**: Automatically manages the camera's flashlight for better scanning in low-light conditions.
- **Zoom Gesture**: Enables zoom functionality using pinch-to-zoom gestures.
- **QR Code Detection with YOLO Tiny**: Implements YOLO Tiny for real-time QR code detection.

## How to Enable QR Code Scanning
To activate the QR code scanning functionality using Dynamsoft Barcode Reader, you'll need a [Dynamsoft Barcode Reader license key](https://www.dynamsoft.com/customer/license/trialLicense?product=dbr). Initialize the barcode reader with your license key as follows:
    
```java
BarcodeReader.initLicense(
"LICENSE-KEY",
    new DBRLicenseVerificationListener() {
        @Override
        public void DBRLicenseVerificationCallback(boolean isSuccessful, Exception e) {
        }
    });
```

## How to Access Camera Frames for Image Processing

**Using CameraX**

With CameraX, you can process camera frames as shown below:

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

**Using Dynamsoft Camera Enhancer**

For Dynamsoft Camera Enhancer, use the following callback to process camera frames:

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
