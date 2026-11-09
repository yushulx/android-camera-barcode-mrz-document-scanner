# ID Card Scanner — React Native Example

A React Native app that captures both sides of an ID card, stores a straightened image of each side, and reads the three-line MRZ on the back with the [Dynamsoft Capture Vision](https://www.dynamsoft.com/capture-vision/docs/introduction/) SDK.

The camera, the live document detection, the MRZ OCR and the MRZ parsing all run inside `dynamsoft-capture-vision-react-native`. There is no `react-native-vision-camera`, no frame processor plugin, and no third-party MRZ parser.

## Features

| Feature | Description |
|---|---|
| Live card detection | The front is detected and deskewed while the camera streams, so the saved image is cropped and straight |
| MRZ reading | `ReadPassportAndId` reads TD1/TD2 ID cards and TD3 passports from the live camera stream |
| Field extraction | Dynamsoft Code Parser turns the MRZ into `Surname`, `GivenName`, `IDNumber`, `DateOfBirth` and `DateOfExpiry` |
| Stable capture | A read is only accepted once two frames agree, and document capture waits for cross-verification of the card corners |
| Matching sides | The raw frame the MRZ was read from is normalized too, so both sides end up as a card-shaped crop |
| Card library | Scanned cards are stored on the device and listed on the home screen |
| Editing | Every field stays editable before the card is saved |

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

The app needs a physical device: the simulator has no camera.

## MRZ Models

The Capture Vision bundle ships the OCR engine but not the MRZ recognition models. They live in the Dynamsoft MRZ Scanner bundle for the same version, which [android/app/build.gradle](android/app/build.gradle) adds as a native dependency:

```gradle
implementation("com.dynamsoft:mrzscannerbundle:3.6.2000")
```

On iOS the same models come from the `DynamsoftMRZScannerBundle` pod, already declared in [ios/Podfile](ios/Podfile):

```ruby
pod 'DynamsoftMRZScannerBundle', '3.6.2000'
```

Without either dependency `CaptureVisionRouter` starts the MRZ template without an error and returns an empty result for every frame — `items=[]`, `errorCode=0`.

## License Setup

The app ships with a [trial license key](https://www.dynamsoft.com/customer/license/trialLicense/?product=dcv&package=cross-platform). To use your own key, open [src/App.tsx](src/App.tsx) and replace the string passed to `LicenseManager.initLicense()`:

```tsx
LicenseManager.initLicense('YOUR_LICENSE_KEY_HERE')
```

The trial key is validated online at startup, so the first launch needs a network connection.

## Project Structure

```
src/
├── App.tsx                  # License gate, stack navigator, screen param types
├── screens/
│   ├── HomeScreen.tsx       # Stored cards, tap to open, long press to delete
│   ├── CardScreen.tsx       # Front/back images, editable fields, save
│   └── CameraScreen.tsx     # Live camera, card detection, MRZ capture
├── components/
│   ├── Card.tsx             # Row in the home list
│   └── TextButton.tsx       # Header text button
├── services/
│   ├── CardDraft.ts         # Card being edited, shared across screens
│   ├── IDCardManager.ts     # AsyncStorage persistence
│   └── mrz.ts               # Parsed MRZ -> card fields
└── models/
    └── ScannedIDCard.ts     # Card record and field list
```
