# expo-mrz-document-scanner

Expo (React Native) example that reads the Machine-Readable Zone (MRZ) of passports
and ID cards with the **Dynamsoft MRZ Scanner native SDK** on Android and iOS — no
third-party scanning plugin. The native bridge (the "plugin" layer) lives inside the
app and is implemented by hand in this project.

This app mirrors [`ionic-id-card-scanner`](../ionic-id-card-scanner): live camera
capture with document/MRZ/portrait overlays, parsed-field results, a
perspective-corrected document image and a cropped portrait photo.

## What a scan returns

```ts
{
  fields: {
    documentType: "PASSPORT" | "ID" | "VISA",
    name: "LAST, FIRST",            // from the MRZ identifiers
    sex: "Male" | "Female",
    documentNumber: "…",            // passport / document / id number
    issuingState: "…",
    nationality: "…",
    dateOfBirth: "YYYY-MM-DD",
    dateOfExpiry: "YYYY-MM-DD",
    age: "42"
  },
  portraitBase64?: "data:image/jpeg;base64,…",      // cropped portrait (when found)
  documentImageBase64?: "data:image/jpeg;base64,…"  // deskewed document (when found)
}
```

Camera scans additionally run the auxiliary-**PortraitZone** detection of the
`ReadPassportAndId` template: the portrait region is located on the document, validated
against the detected document quad, perspective-corrected and cropped.

## How the native bridge is organized

```
modules/expo-dynamsoft-mrz-scanner/
├── expo-module.config.json        # module registered for apple + android only
├── src/ExpoDynamsoftMrzScannerModule.ts         # JS/TS surface of the module
├── android/                        # Android library (Kotlin)
│   ├── build.gradle                # Dynamsoft mrzscannerbundle + appcompat
│   └── src/main/
│       ├── AndroidManifest.xml     # CAMERA + photo permissions, IdScanActivity
│       ├── java/expo/modules/dynamsoftmrzscanner/
│       │   ├── ExpoDynamsoftMrzScannerModule.kt  # initLicense / startScan / scanFromGallery / scanFile
│       │   ├── IdScanActivity.kt                 # live camera scanner (portrait zone pipeline)
│       │   ├── IdScannerEngine.kt                # shared CaptureVisionRouter + license + templates
│       │   ├── IdResultFormatter.kt              # ParsedResultItem → display fields
│       │   ├── IdScanResultPayload.kt / IdScanResultContract.kt
│       │   └── License.kt                        # ← replace the trial key here
│       └── res/                    # scanner layout + status UI
└── ios/
    ├── ExpoDynamsoftMrzScanner.podspec           # DynamsoftMRZScannerBundle dependency
    ├── ExpoDynamsoftMrzScannerModule.swift       # module entry points
    ├── IdCameraScanViewController.swift          # live camera scanner
    ├── IdResultFormatter.swift                   # fields + ImageData → UIImage decoder
    └── SharedImagePicker.swift                   # system photo picker
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

The apps embed a Dynamsoft MRZ Scanner **trial license** key:

- Android: `modules/expo-dynamsoft-mrz-scanner/android/src/main/java/expo/modules/dynamsoftmrzscanner/License.kt`
- iOS: `modules/expo-dynamsoft-mrz-scanner/ios/ExpoDynamsoftMrzScannerModule.swift`

Replace it with your own key before shipping. A Dynamsoft account is required to obtain one.

## Blog
[How to Build an Expo MRZ Document Scanner for Android and iOS](https://www.dynamsoft.com/codepool/expo-mrz-scanner.html)
