# Document Scanner — React Native Example

A production-ready React Native app that uses the [Dynamsoft Capture Vision](https://www.dynamsoft.com/capture-vision/docs/introduction/) SDK to scan, crop, and export documents from your device camera.

https://github.com/user-attachments/assets/34a3c1e7-b313-45cc-b22d-4b55adc0fafe

## Features

| Feature | Description |
|---|---|
| Auto-detect edges | The SDK automatically detects document boundaries in the live camera feed |
| Manual crop | Drag corner handles to fine-tune the crop region before confirming |
| Color modes | Switch between full color, grayscale, and binary (black & white) output |
| PNG export | Save the processed document image to device storage |

## Getting Started

### 1. Install dependencies

```bash
npm install
```

### 2. iOS — install Pods

```bash
cd ios && pod install && cd ..
```

### 3. Run on Android

```bash
npm run android
```

### 4. Run on iOS

```bash
npm run ios
```

## License Setup

The app ships with a [trial license key](https://www.dynamsoft.com/customer/license/trialLicense/?product=dcv&package=cross-platform). To use your own key, open [src/App.tsx](src/App.tsx) and replace the string passed to `LicenseManager.initLicense()`:

```tsx
LicenseManager.initLicense('YOUR_LICENSE_KEY_HERE')
```


## Project Structure

```
src/
├── App.tsx              # Root navigator, HomeScreen, license initialization
├── Scanner.tsx          # Live camera screen — auto-detects and captures document
├── Editor.tsx           # Quad editor — drag corners to adjust crop region
└── NormalizedImage.tsx  # Result screen — color modes and PNG export
```

