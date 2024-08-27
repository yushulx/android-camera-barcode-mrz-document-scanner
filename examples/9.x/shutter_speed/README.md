# Fast Moving QR Code Detection with Android Camera2 API
This sample project demonstrates how to scan barcodes from fast-moving objects by adjusting the shutter speed using the Android Camera2 API. The implementation is based on the [Android Camera2Basic](https://github.com/googlesamples/android-Camera2Basic) sample and [Dynamsoft Barcode Reader v9.x](https://www.dynamsoft.com/barcode-reader/sdk-mobile/). 

## Prerequisites
- Obtain a [Dynamsoft Barcode Reader Trial License](https://www.dynamsoft.com/customer/license/trialLicense/?product=dbr&package=mobile)

## Getting Started
1. Clone the repository and open the project in Android Studio.
2. Set your license key in `Camera2BasicFragment.java`:

    ```java
    try {
            BarcodeReader.initLicense(
                    "LICENSE-KEY",
                    new DBRLicenseVerificationListener() {
                        @Override
                        public void DBRLicenseVerificationCallback(boolean isSuccessful, Exception e) {
                        }
                    });
            mBarcodeReader = new BarcodeReader();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    ```

3. Build and run the app in Android Studio. The app should be able to scan barcodes from fast-moving objects efficiently.

    ![Android Camera2 shutter speed](https://www.dynamsoft.com/codepool/img/2019/05/android-camera2-barcode.gif)


## Blog
[High-Speed Barcode and QR Code Detection on Android Using Camera2 API](https://www.dynamsoft.com/codepool/android-barcode-detection-fast-moving-object.html)


