# Driver License Scanner

This is an Android example application that demonstrates how to scan and parse driver licenses using **Dynamsoft Barcode Reader** and **Google ML Kit**.

https://github.com/user-attachments/assets/b7ba3eb5-276d-498c-bcb2-d98db875c41d

## Features

-   **Multiple Scanning Solutions**:
    -   **Dynamsoft Barcode Reader**: High-performance scanning for PDF417 and other driver license barcodes.
    -   **Google ML Kit**: Uses Google's on-device vision APIs for barcode detection.
-   **Data Parsing**: Automatically parses the scanned data to extract key information such as:
    -   Full Name
    -   Address (Street, City, State, Zip)
    -   License Number
    -   Date of Birth
    -   Expiration Date
    -   Issue Date
    -   Height, Sex, etc.
-   **Supported Formats**:
    -   AAMVA DL/ID (North American Driver Licenses)
    -   South Africa Driver Licenses

## Prerequisites

- Obtain a [30-day Trial License](https://www.dynamsoft.com/customer/license/trialLicense/?product=dcv&package=cross-platform) for Dynamsoft Barcode Reader.

## Getting Started

### 1. Clone the Repository

```bash
git clone https://github.com/yushulx/android-camera-barcode-mrz-document-scanner.git
cd android-camera-barcode-mrz-document-scanner/examples/driver-license-scanner
```

### 2. Open in Android Studio

Open the `driver-license-scanner` folder in Android Studio.

### 3. License Key

The application requires a valid license key from Dynamsoft to function. A trial license is included in `MainActivity.java` for demonstration purposes.

```java
// MainActivity.java
LicenseManager.initLicense(
    "YOUR_LICENSE_KEY",
    this, (isSuccessful, error) -> { ... }
);
```


### 4. Build and Run

Connect your Android device and run the application from Android Studio. Or command line:

```bash
.\gradlew.bat assembleDebug
.\gradlew.bat installDebug
```

## Usage

1.  Grant camera permission when prompted.
2.  Point the camera at the barcode on a driver's license.
3.  The app will automatically scan and display the parsed information.

