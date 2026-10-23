# Android Capture Vision Examples

Sample Android applications built with the [Dynamsoft Capture Vision SDK](https://www.dynamsoft.com/capture-vision/docs/introduction/) family — **barcode reading**, **document scanning**, and **MRZ (Machine Readable Zone)** — using CameraX, Camera Enhancer, Jetpack Compose, ARCore, Expo, React Native and Ionic/Capacitor.

The native Android samples are pinned to the current release line:

| Product | Maven artifact | Version |
| --- | --- | --- |
| Dynamsoft Barcode Reader Bundle | `com.dynamsoft:barcodereaderbundle` | **11.6.4000** |
| Dynamsoft Capture Vision Bundle | `com.dynamsoft:capturevisionbundle` | **3.6.4000** |
| Dynamsoft MRZ Scanner Bundle | `com.dynamsoft:mrzscannerbundle` | **3.6.2000** |

The Expo, React Native and Ionic samples are left on the last published versions of their wrappers (`capturevisionbundle` 3.6.2000 / `mrzscannerbundle` 3.4.1300, `dynamsoft-capture-vision-react-native` 3.4.x, `dynamsoft-barcode-reader-bundle-react-native` 11.4.x, `dynamsoft-mrz-scanner-bundle-react-native` 3.2.x). Those wrappers bundle their own AAR, so they cannot resolve `3.6.4000` until Dynamsoft publishes matching releases; they will be bumped then. The SDK column of each table lists what the sample resolves today.

## Prerequisites

- Android Studio (JDK 17) and Android SDK Platform 35 or newer. The Ionic samples are built with Capacitor 8, whose `capacitor-android` module targets Java 21 — use JDK 21 for those two (JDK 17 fails with `invalid source release: 21`).
- A [Dynamsoft Capture Vision Suite license key](https://www.dynamsoft.com/customer/license/trialLicense/?product=dcv&package=cross-platform). Each sample ships a 30-day trial key in its launcher activity (`LicenseManager.initLicense(...)`); replace it with your own key before shipping.
- Create `local.properties` with `sdk.dir=/path/to/Android/Sdk` if Android Studio has not generated one yet.

Gradle builds resolve the SDK from Dynamsoft's Maven repository (`https://download2.dynamsoft.com/maven/aar`), which the sample `settings.gradle` files already declare.

## Examples

### Native Android (Java / Kotlin)

| Sample | What it shows | SDK |
| --- | --- | --- |
| [ARCore](examples/ARCore/README.md) | Spatial barcode scanning: ARCore planes + DBR decoding, barcodes anchored to real-world positions | `barcodereaderbundle:11.6.4000` |
| [dense_qr](examples/dense_qr/README.md) | Google ML Kit vs. Dynamsoft Barcode Reader on high-density QR codes, still image and camera stream | `barcodereaderbundle:11.6.4000` |
| [driver-license-scanner](examples/driver-license-scanner/README.md) | Driver's license capture: barcode + PDF417 parsing into AAMVA fields | `barcodereaderbundle:11.6.4000` |
| [mlkit-dbr-benchmark](examples/mlkit-dbr-benchmark/README.md) | Benchmark harness comparing DBR and ML Kit across still images, camera, and multi-frame input | `barcodereaderbundle:11.6.4000` |
| [IPCam-Barcode-Scanner-Android](examples/IPCam-Barcode-Scanner-Android/README.md) | Barcode scanning from an IP camera: Media3 RTSP playback plus frame decoding | `barcodereaderbundle:11.6.4000` |
| [Android-USB-Camera-Barcode-Scanner](examples/Android-USB-Camera-Barcode-Scanner/README.md) | Barcode reading from a USB (UVC) camera using prebuilt UVCCamera AARs | `barcodereaderbundle:11.6.4000` |
| [DocumentScanner](examples/DocumentScanner/README.md) | Two document scanners side by side: Dynamsoft Capture Vision (`DocumentScanner`) and Google ML Kit Document Scanner | `capturevisionbundle:3.6.4000` |
| [DynamsoftDocumentScanner](examples/DynamsoftDocumentScanner/README.md) | Full document workflow: capture, detect, normalize, edit, and export to PDF/images | `capturevisionbundle:3.6.4000` |
| [MrzScanner](examples/MrzScanner/README.md) | MRZ recognition for passports and ID cards, with parsed field display | `mrzscannerbundle:3.6.2000` |
| [IdScanner](examples/IdScanner/README.md) | MRZ scanning plus portrait extraction for passport / ID / visa documents | `mrzscannerbundle:3.6.2000` |
| [Android-USB-Camera-MRZ-Scanner](examples/Android-USB-Camera-MRZ-Scanner/README.md) | MRZ reading from a USB (UVC) camera using prebuilt UVCCamera AARs | `mrzscannerbundle:3.6.2000` |

> `IPCam-Barcode-Scanner-Android`, `Android-USB-Camera-Barcode-Scanner` and `Android-USB-Camera-MRZ-Scanner` used to be standalone repositories. They are vendored under `examples/` so the tutorials and the code they link to stay in the same place.

### Expo

| Sample | What it shows | SDK |
| --- | --- | --- |
| [expo-barcode-scanner](examples/expo-barcode-scanner/README.md) | Expo module bridging the native Capture Vision SDK — no third-party scanning plugin | `capturevisionbundle:3.6.2000` |
| [expo-document-scanner](examples/expo-document-scanner/README.md) | Expo document scanner: capture, crop, and export | `dynamsoft-capture-vision-react-native:^3.4.1000` |
| [expo-mrz-document-scanner](examples/expo-mrz-document-scanner/README.md) | Expo MRZ scanner: passport / ID MRZ reading with parsed fields | `mrzscannerbundle:3.4.1300` |

### React Native

| Sample | What it shows | SDK |
| --- | --- | --- |
| [react-native-barcode-scanner](examples/react-native-barcode-scanner/README.md) | Live barcode scanning with real-time overlays drawn over detected barcodes | `dynamsoft-barcode-reader-bundle-react-native:^11.4.1200` |
| [react-native-document-scanner](examples/react-native-document-scanner/README.md) | Document capture, edge detection, and PDF export | `dynamsoft-capture-vision-react-native:^3.4.1000` |
| [react-native-mrz-scanner](examples/react-native-mrz-scanner/README.md) | MRZ scanning with the prebuilt MRZ scanner UI | `dynamsoft-mrz-scanner-bundle-react-native:3.2.5002` |

### Ionic / Capacitor

| Sample | What it shows | SDK |
| --- | --- | --- |
| [ionic-vue-qr-code-scanner](examples/ionic-vue-qr-code-scanner/README.md) | Vue + Capacitor QR/barcode scanner calling the native Capture Vision SDK through a Capacitor plugin | `capturevisionbundle:3.6.2000` |
| [ionic-id-card-scanner](examples/ionic-id-card-scanner/README.md) | Vue + Capacitor MRZ / ID card scanner with portrait extraction | `mrzscannerbundle:3.4.1300` |

> The legacy `examples/9.x` folder (Dynamsoft Barcode Reader 9.x API) has been removed. Use the samples above, all of which target the current 11.6 / 3.6 API.

## Running a sample

```bash
cd examples/<sample>
./gradlew assembleDebug          # builds app/build/outputs/apk/debug/app-debug.apk
# or open the folder in Android Studio and press Run
```

For the JavaScript samples (`expo-*`, `react-native-*`, `ionic-*`) install the JS dependencies first:

```bash
npm install
npx expo run:android             # expo-*
npx react-native run-android     # react-native-*
npx cap run android              # ionic-*
```

## License

Sample code is provided for demonstration purposes. The Dynamsoft SDKs bundled through Maven require a valid license at runtime.
