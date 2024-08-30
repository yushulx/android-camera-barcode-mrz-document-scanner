# Driver's License Recognition on Android
This project demonstrates how to recognize driver's licenses on Android using the Dynamsoft Barcode Reader and Google Mobile Vision.

https://github.com/user-attachments/assets/2ec16f20-2571-4977-b0fd-c4a584641343

## Prerequisites
- Obtain a [Dynamsoft Capture Vision Trial License](https://www.dynamsoft.com/customer/license/trialLicense/?product=dcv&package=cross-platform)

## Getting Started
1. Open the project in Android Studio.
2. Replace the placeholder with your license key in `MainActivity.java`:

    ```java
    LicenseManager.initLicense("LICENSE-KEY", this, (isSuccessful, error) -> {
                if (!isSuccessful) {
                    error.printStackTrace();
                    runOnUiThread(() -> ((TextView) findViewById(R.id.tv_license_error)).setText("License initialization failed: "+error.getMessage()));
                }
            });
    ```

3. Compile the project and run it on your Android device.

## Driver's License Recognition

### Testing Image

<kbd><img src="https://www.dynamsoft.com/codepool/wp-content/uploads/2020/05/driver-license.jpg" width="50%">

### Recognition Results
**Google Mobile Vision**

<kbd><img src="https://www.dynamsoft.com/codepool/img/2020/05/google-driver-license.png" width="40%">
  
**Dynamsoft Barcode Reader**

<kbd><img src="https://www.dynamsoft.com/codepool/img/2020/05/dynamsoft-driver-license.png" width="40%">


## Blog
[How to Recognize US Driver's License on Android Mobile Apps](https://www.dynamsoft.com/codepool/how-to-recognize-driver-license-on-android.html)
