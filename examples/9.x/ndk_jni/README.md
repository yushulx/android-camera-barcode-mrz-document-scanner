# Optimized Android Camera2 Barcode Scanner with JNI C++ Code
This sample project demonstrates how to enhance barcode detection and decoding performance on Android by leveraging the Camera2 API and JNI C++ code.

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


3. Build and run the app in Android Studio.

    ![Android Camera2 Barcode](https://www.dynamsoft.com/codepool/img/2019/05/android-camera2-barcode.gif)

## Blog
[Using Android NDK to Optimize Barcode Reading Performance](https://www.dynamsoft.com/codepool/android-ndk-optimize-camera2-barcode.html)




