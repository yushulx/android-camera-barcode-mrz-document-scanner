# 1D/2D Barcode Detection with Android ARCore

This sample project demonstrates how to use [ARCore](https://developers.google.com/ar) alongside the [Dynamsoft Barcode Reader](https://www.dynamsoft.com/barcode-reader/sdk-mobile/) to detect multiple barcodes and create anchors for them within an AR scene. The project is based on the [ARCore ML sample](https://github.com/googlesamples/arcore-ml-sample.git).

https://github.com/yushulx/android-arcore-barcode-qr-detection/assets/2202306/be665855-4720-4896-a838-f9105cd5f9c2

## Prerequisites
- An ARCore-compatible device running [Google Play Services for AR](https://play.google.com/store/apps/details?id=com.google.ar.core) (ARCore) 1.24 or later.
- Android Studio 4.1 or later.

## Getting Started
1. Obtain a trial license key for the Dynamsoft Barcode Reader from [here](https://www.dynamsoft.com/customer/license/trialLicense?product=dbr&package=android&utm_source=github).
2. Replace the placeholder with your license key in `MainActivity.kt`.
    
    ```kotlin
    BarcodeReader.initLicense("LICENSE-KEY") { isSuccessful, e ->
      runOnUiThread {
        if (!isSuccessful) {
          e.printStackTrace()
          Log.e(TAG, "Failed to verify the license: $e")
        }
      }
    }
    ```
3. Build and run the sample on your ARCore-compatible device.

    ![android-arcore-barcode-qr-detection](https://github.com/yushulx/android-arcore-barcode-qr-detection/assets/2202306/2b9114a2-24fe-4b7a-93e1-b145a060bb89)



## Blog
[Labeling Multiple Barcodes with Augmented Reality and Dynamsoft Barcode Reader](https://www.dynamsoft.com/codepool/augmented-reality-arcore-barcode-qr-detection.html)
