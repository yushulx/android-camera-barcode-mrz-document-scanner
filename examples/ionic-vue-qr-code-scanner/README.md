# Ionic Vue QR Code Scanner (Capacitor + Dynamsoft Native SDK)

An Ionic Vue cross-platform QR code and barcode scanner built with **Ionic** and **Capacitor**. All scanning runs on the
**Dynamsoft native SDKs** through a small in-app Capacitor plugin — no JavaScript scanning SDK and
no third-party scanning plugin is used.

## Features

- **Two data sources** — live camera scanning and still images from the gallery / file system
- **Native camera preview** — powered by Dynamsoft Camera Enhancer (CameraX on Android, AVFoundation on iOS)
- - **Multi-format** - QR Code, DataMatrix, PDF417, Aztec, Code 39/93/128, EAN, UPC, ITF, Codabar
- **Real-time overlay** - decoded barcode contours are drawn on the native preview
- **Privacy-first** — every frame is processed on-device

## SDK versions

| Platform | SDK | Version |
|---|---|---|
| Android | com.dynamsoft:capturevisionbundle | 3.6.2000 |
| iOS | DynamsoftCaptureVisionBundle | 3.6.2000 |

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
3. The native plugin runs the `ReadBarcodes` template with the Dynamsoft SDK and returns the
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

## Blog
[How to Build an Ionic Vue QR Code Scanner with the Dynamsoft Capture Vision Native SDK](https://www.dynamsoft.com/codepool/ionic-vue-qr-code-scanner.html)
