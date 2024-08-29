# Passport MRZ Recognition on Android
This project demonstrates how to recognize the Machine Readable Zone (MRZ) of a passport on Android using the [Dynamsoft Label Recognizer SDK](https://www.dynamsoft.com/label-recognition/docs/mobile/programming/android/).

## Prerequisites
- Obtain a [Dynamsoft Capture Vision Trial License](https://www.dynamsoft.com/customer/license/trialLicense/?product=dcv&package=cross-platform)

## Getting Started
1. Open the project in Android Studio.
2. Replace the placeholder with your license key in `MainActivity.java`:

    ```java
    LicenseManager.initLicense("LICENSE-KEY",
				this,
				(isSuccess, error) -> {
					if (!isSuccess) {
						runOnUiThread(() -> {
							((TextView)findViewById(R.id.tv_message))
									.setText("License initialization failed: "+error.getMessage());
						});
						error.printStackTrace();
					}
				});
    ```

3. Compile the project and run it on your Android device.

    ![US Passport MRZ Recognition on Android](https://www.dynamsoft.com/codepool/img/2021/07/android-passport-mrz-ocr.jpg)


## Blog
[How to Recognize Passport MRZ on Android Mobile Apps](https://www.dynamsoft.com/codepool/android-ocr-recognition-passport-mrz.html)
