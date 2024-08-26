# High-density QR Code Detection 
This app is built upon [ML Kit's vision sample](https://github.com/googlesamples/mlkit/tree/master/android/vision-quickstart). It demonstrates how to use ML Kit's Barcode Scanning API and the Dynamsoft Barcode Reader API to detect high-density QR codes from static images.


<img src="https://www.dynamsoft.com/codepool/img/2021/10/high-density-qr-code-detection.jpg" width="220"/> 

## Pre-requisites
- Obtain a [Dynamsoft Barcode Reader license key](https://www.dynamsoft.com/customer/license/trialLicense?product=dbr) to activate the Dynamsoft barcode scanning functionality.

## Getting Started
1. Set your Dynamsoft license key in `app/src/main/java/com/google/mlkit/vision/demo/java/dynamsoftbarcodescanner/DynamsoftBarcodeProcessor.java`:

    ```java
    BarcodeReader.initLicense(
        "LICENSE-KEY",
        new DBRLicenseVerificationListener() {
            @Override
            public void DBRLicenseVerificationCallback(boolean isSuccessful, Exception e) {
            }
        });
    ```

2. Build and run the project in Android Studio.

## Public Image Dataset
You can test high-density QR code detection using the following public image dataset:

[https://boofcv.org/notwiki/regression/fiducial/qrcodes_v3.zip](https://boofcv.org/notwiki/regression/fiducial/qrcodes_v3.zip)

## High-Density QR Code Detection Performance
The performance of high-density QR code detection has been evaluated using the public image dataset `qrcodes_v3/qrcodes/detection/high_version`. The results are visualized in the chart below:

![high-density QR code detection performance](https://www.dynamsoft.com/codepool/img/2021/10/high-density-qr-detection-performance.jpg)


## Blog
[How to Detect High Density QR Code on Android Devices](https://www.dynamsoft.com/codepool/high-density-qr-code-detection.html)

