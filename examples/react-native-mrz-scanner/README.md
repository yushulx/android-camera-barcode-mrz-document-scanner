# React Native MRZ Scanner

A React Native sample app that scans the Machine Readable Zone (MRZ) of passports, ID cards, and other ICAO-compliant travel documents using the [Dynamsoft MRZ Scanner](https://www.dynamsoft.com/use-cases/mrz-scanner/) SDK.

## Features

- Real-time MRZ scanning via device camera
- Supports TD1 (ID cards), TD2, and TD3 (passports) MRZ formats
- Structured result card displaying parsed fields (name, document number, nationality, dates, etc.)
- Clear error and cancellation states
- Works on Android and iOS

## Requirements

| Requirement | Version |
|-------------|---------|
| Node.js | ≥ 18 |
| React Native | 0.79 |
| Android | API 21+ (Android 5.0) |
| iOS | 13.0+ |
| JDK | 17 |
| Android Studio | Latest |
| Xcode | Latest (macOS only, for iOS) |

## Getting Started

### 1. Install dependencies

```bash
npm install
```

### 2. Obtain a license

The app ships with a **trial license** that requires a network connection.

To get a free 30-day trial license key, visit:  
https://www.dynamsoft.com/customer/license/trialLicense/?product=dcv&package=cross-platform

Replace the `license` value in `src/App.tsx`:

```ts
const config: MRZScanConfig = {
  license: 'YOUR_LICENSE_KEY_HERE',
};
```

### 3. Run on Android

Make sure a physical device is connected (USB debugging enabled) or an emulator is running, then:

```bash
# Start Metro bundler (separate terminal)
npm start

# Build and install
npm run android
```

### 4. Run on iOS

```bash
cd ios && pod install && cd ..
npm run ios
```

## Key Dependencies

| Package | Purpose |
|---------|---------|
| `dynamsoft-mrz-scanner-bundle-react-native` | MRZ scanning SDK |
| `dynamsoft-capture-vision-react-native` | Underlying camera / vision engine |
| `react-native` 0.79 | Framework |
