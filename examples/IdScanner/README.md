# Android MRZ Scanner

A real-time Android passport, ID card, and VISA scanner that extracts Machine-Readable Zone (MRZ) data and detects portrait photos using the **Dynamsoft Capture Vision** SDK.

https://github.com/user-attachments/assets/579e96cd-b7a9-44c3-8816-5419fee637f3

## Features

- **Live Camera MRZ Scanning** — point the camera at a passport or ID card MRZ zone and get instant results
- **Multi-format Support** — TD1 (ID cards), TD2 (travel documents), TD3/MRP (passports), and generic formats
- **Portrait Detection & Extraction** — automatically detects, validates, and crops the document photo
- **Privacy-First** — all processing runs on-device; no data leaves the phone
- **Rich Field Extraction** — name, document number, nationality, issuing state, date of birth, expiry date, sex, and age

## Prerequisites

- **Android Studio** Hedgehog (2023.1.1) or newer
- **Android SDK** 34 with build tools 34.0.0+
- **minSdk 21** / **targetSdk 34**
- A **Dynamsoft trial license** ([get one here](https://www.dynamsoft.com/customer/license/trialLicense/?product=dcv&package=cross-platform))

## Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/yushulx/android-camera-barcode-mrz-document-scanner.git
cd android-camera-barcode-mrz-document-scanner/examples/IdScanner
```

### 2. Set your license key

Open `app/src/main/java/com/test/mrzscanner/MrzParser.java` and replace the `LICENSE_KEY` constant with your own key from the [Dynamsoft Customer Portal](https://www.dynamsoft.com/customer/license/trialLicense/?product=dcv&package=cross-platform).

### 3. Build and run

```bash
./gradlew assembleDebug
./gradlew installDebug
```

Or open the project in Android Studio and click **Run**.

## Project Structure

```
IdScanner/
├── app/
│   ├── build.gradle                 # App-level dependencies & config
│   └── src/main/
│       ├── AndroidManifest.xml      # Activities, camera permission
│       ├── java/com/test/mrzscanner/
│       │   ├── HomeActivity.java    # Launcher / home screen
│       │   ├── MainActivity.java    # Live camera scan controller
│       │   ├── MrzParser.java       # MRZ parsing & license init
│       │   └── ScanResultActivity.java  # Results display
│       └── res/
│           ├── layout/              # XML layouts for 3 screens
│           └── values/              # Colors, strings, themes
├── build.gradle                     # Top-level build config (Dynamsoft Maven)
├── settings.gradle                  # Root project name
└── gradle.properties
```

## Blog
[Build an Android Passport Scanner with MRZ and Portrait Detection](https://www.dynamsoft.com/codepool/android-mrz-scanner-app-face-detection.html)
