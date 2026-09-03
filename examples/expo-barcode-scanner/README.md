# expo-barcode-scanner

Expo (React Native) example that decodes barcodes with the **Dynamsoft Capture Vision
native SDK** on Android and iOS — no third-party scanning plugin. The native bridge
(the "plugin" layer) lives inside the app and is implemented by hand in this project.

This app mirrors [`ionic-vue-qr-code-scanner`](../ionic-vue-qr-code-scanner): the same
UI flow (live camera or image file → scan → results list) driven by a hand-written
native module instead of a Capacitor plugin.

## Data sources

| Source      | Behavior                                                                              |
| ----------- | ------------------------------------------------------------------------------------- |
| Camera      | Full-screen native scanner (`ScannerActivity` / `BarcodeCameraScanViewController`). Decoded barcodes are drawn live; tapping **Confirm** returns them. |
| Image File  | System photo picker; the picked image is decoded on the native side.                  |

All three bridge methods reject with a `canceled` message when the user backs out.

## How the native bridge is organized

```
modules/expo-dynamsoft-barcode-scanner/
├── expo-module.config.json        # module registered for apple + android only
├── src/ExpoDynamsoftBarcodeScannerModule.ts   # JS/TS surface of the module
├── android/                        # Android library (Kotlin)
│   ├── build.gradle                # Dynamsoft capturevisionbundle + appcompat
│   └── src/main/
│       ├── AndroidManifest.xml     # CAMERA + photo permissions, ScannerActivity
│       ├── java/expo/modules/dynamsoftbarcodescanner/
│       │   ├── ExpoDynamsoftBarcodeScannerModule.kt   # initLicense / startScan / scanFromGallery / scanFile
│       │   ├── ScannerActivity.kt                     # live camera scanner (DCE + DBR)
│       │   ├── ScannerEngine.kt                       # shared CaptureVisionRouter + license
│       │   ├── BarcodeResults.kt / ScanResultContract.kt
│       │   └── License.kt                             # ← replace the trial key here
│       └── res/                    # scanner layout + status UI
└── ios/
    ├── ExpoDynamsoftBarcodeScanner.podspec           # DynamsoftCaptureVisionBundle dependency
    ├── ExpoDynamsoftBarcodeScannerModule.swift       # module entry points
    ├── BarcodeCameraScanViewController.swift         # live camera scanner
    └── SharedImagePicker.swift                       # system photo picker
```

The module is a **local Expo module**: it is autolinked from `modules/` when the app is
built, and imported from `App.tsx` by relative path — no package publishing needed.

## Run it

```bash
npm install

# Android phone / emulator
npx expo run:android

# iPhone (Xcode + signing team required)
npx expo run:ios --device
```

Release builds (self-contained JS bundle, no Metro needed):

```bash
cd android && ./gradlew :app:assembleRelease   # install: adb install -r app/build/outputs/apk/release/app-release.apk
# or: npx expo run:ios --configuration Release --device
```

## License

The apps embed a Dynamsoft Capture Vision **trial license** key:

- Android: `modules/expo-dynamsoft-barcode-scanner/android/src/main/java/expo/modules/dynamsoftbarcodescanner/License.kt`
- iOS: `modules/expo-dynamsoft-barcode-scanner/ios/ExpoDynamsoftBarcodeScannerModule.swift`

Replace it with your own key before shipping. A Dynamsoft account is required to obtain one.
