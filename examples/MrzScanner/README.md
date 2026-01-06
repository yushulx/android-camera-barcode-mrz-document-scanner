# Passport MRZ Recognition on Android
This project demonstrates how to recognize the **Machine Readable Zone (MRZ)** of a passport on Android using the [Dynamsoft MRZ SDK](https://www.dynamsoft.com/mrz-scanner/docs/mobile/programming/android/user-guide/index.html).

https://github.com/user-attachments/assets/da6d63c4-8221-42ad-87bb-40db94360043

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


## Blog
[How to Recognize Passport MRZ on Android Mobile Apps](https://www.dynamsoft.com/codepool/android-ocr-recognition-passport-mrz.html)
