# Android MRZ Scanner

This project demonstrates how to recognize the **Machine Readable Zone (MRZ)** of passports and ID cards on Android. It leverages the **Dynamsoft MRZ Scanner SDK** for robust MRZ parsing and **Google ML Kit** for high-quality document scanning and face detection.

https://github.com/user-attachments/assets/da6d63c4-8221-42ad-87bb-40db94360043

## Key Features

- **Live Camera Scanning**: Real-time detection and parsing of MRZ codes directly from the video feed.
- **Document Scanning**: Captures high-resolution document images using Google ML Kit's Document Scanner API.
- **Face Extraction**: Automatically detects and crops the user's face from the passport photo using Google ML Kit Face Detection.
- **Detailed Parsing**: Extracts and displays key fields such as Name, Document Number, Date of Birth, Expiry Date, Nationality, and more.

## Tech Stack

- **[Dynamsoft MRZ Scanner SDK](https://www.dynamsoft.com/mrz-scanner/docs/mobile/programming/android/user-guide/index.html)**: The core engine for accurate MRZ recognition.
- **Google ML Kit**:
    - [Document Scanner](https://developers.google.com/ml-kit/vision/doc-scanner)
    - [Face Detection](https://developers.google.com/ml-kit/vision/face-detection)
    - [Text Recognition v2](https://developers.google.com/ml-kit/vision/text-recognition/v2)

## Prerequisites
- A valid [Dynamsoft Capture Vision Trial License](https://www.dynamsoft.com/customer/license/trialLicense/?product=dcv&package=cross-platform)

## Getting Started

1.  **Clone the repository** and open the `examples/MrzScanner` project in Android Studio.
2.  **Configure the License Key**:
    Open `app/src/main/java/com/test/mrzscanner/MrzParser.java` and find the `LICENSE_KEY` constant. Replace it with your own license key.

    ```java
    private static final String LICENSE_KEY = "YOUR_LICENSE_KEY";
    ```

3.  **Run the Application**:
    Connect your Android device and run the project.
    - Select **"Document Scan"** to capture a still image and process it.
    - Select **"Live Scan"** to scan MRZ codes in real-time using the camera.

## Blog
[How to Build an Android MRZ Scanner with Dynamsoft MRZ SDK](https://www.dynamsoft.com/codepool/android-mrz-scanner-app-development.html)
