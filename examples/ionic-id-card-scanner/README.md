# Ionic ID Card Scanner (Capacitor + Dynamsoft Native SDK)

An Ionic Vue cross-platform ID card, passport and visa MRZ scanner that extracts holder fields and the portrait photo built with **Ionic** and **Capacitor**. All scanning runs on the
**Dynamsoft native SDKs** through a small in-app Capacitor plugin — no JavaScript scanning SDK and
no third-party scanning plugin is used.

## Features

- **Two data sources** — live camera scanning and still images from the gallery / file system
- **Native camera preview** — powered by Dynamsoft Camera Enhancer (CameraX on Android, AVFoundation on iOS)
- - **MRZ parsing** - TD1 / TD2 / TD3 documents are parsed on-device
- **Portrait extraction** - the photo on the document is detected and cropped automatically
- **Privacy-first** — every frame is processed on-device

## SDK versions

| Platform | SDK | Version |
|---|---|---|
| Android | com.dynamsoft:mrzscannerbundle | 3.4.1300 |
| iOS | DynamsoftMRZScannerBundle | 3.4.1300 |

## Prerequisites

- Node.js 20+, Ionic CLI (optional) and Capacitor
- Android Studio with Android SDK 36 / Xcode for iOS
- A physical device (the camera does not work in an emulator or the iOS simulator)
- [Get a 30-day free trial license](https://www.dynamsoft.com/customer/license/trialLicense/?product=dcv&package=cross-platform)

## Project layout

```
src/                 # Ionic web layer (TypeScript UI, two data-source cards)
  scanner/           # registerPlugin() bridge definition
android/             # Android native project
  app/src/main/java/.../  # in-app Capacitor plugin + native scanner
ios/                 # iOS native project
  App/App/*.swift    # in-app Capacitor plugin + native scanner
```

## How it works

1. The web layer calls `initLicense()`, then `startScan()` (camera) or `scanFromGallery()` /
   `scanFile({uri})` (file data source).
2. The Capacitor bridge forwards the call to the native plugin.
3. The native plugin runs the `ReadPassportAndId` template with the Dynamsoft SDK and returns the
   result as JSON to the web layer for display.

## Run it

```bash
npm install

# build the web app and sync native projects
npm run build
npx cap sync

# open in Android Studio / Xcode
npx cap open android
npx cap open ios
```

## iOS note

The iOS SDK is wired in with CocoaPods. Run `pod install` inside `ios/App` once after cloning (or after changing the Podfile), then open `App.xcworkspace` (not the `.xcodeproj`). Camera permission is already declared in `Info.plist`.

## License

The sample uses a public Dynamsoft trial license (also embedded in
`android/.../strings.xml` and the native plugin). Replace it with your own key before shipping.
