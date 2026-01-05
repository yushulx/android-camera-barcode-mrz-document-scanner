# Android Document Scanner

This project demonstrates and compares two powerful document scanning solutions for Android: **Dynamsoft Capture Vision** and **Google ML Kit Document Scanner**.

The app provides a modern UI to launch either scanner, allowing you to evaluate their performance, accuracy, and user experience side-by-side.

https://github.com/user-attachments/assets/0e1fba71-238c-48ca-8df3-8a719d95ae14

## Features

### Dynamsoft Document Scanner
- **Professional Grade**: Advanced boundary detection and image processing.
- **Customizable**: Full control over the scanning UI and workflow.
- **High Accuracy**: Robust detection even in challenging lighting or backgrounds.
- **Image Enhancement**: Built-in filters for grayscale, color, and binary normalization.

### Google ML Kit Document Scanner
- **Fast & Efficient**: Powered by Google's on-device machine learning.
- **Easy Integration**: Minimal code required to add a high-quality scanner.
- **Multi-page Support**: Scan up to 10 pages in a single session.
- **PDF/JPEG Output**: Automatically generates both image and PDF formats.


## Prerequisites
- Dynamsoft License (Optional for trial, see [Dynamsoft Customer Portal](https://www.dynamsoft.com/customer/license/trialLicense/?product=dcv&package=cross-platform)). Set the license key in `ScannerFragment.java`.
   
    ```java
    if (savedInstanceState == null) {
            LicenseManager.initLicense("LICENSE-KEY", (isSuccess, error) -> {
                if (!isSuccess && error != null) {
                    error.printStackTrace();
                }
            });
        }
    ```
   

## Getting Started

1. Clone the repository.
2. Open the project in Android Studio.
3. Build and run the app on your physical device.

## Dependencies

- `com.dynamsoft:capturevisionbundle:3.2.5000`
- `com.google.android.gms:play-services-mlkit-document-scanner:16.0.0`

