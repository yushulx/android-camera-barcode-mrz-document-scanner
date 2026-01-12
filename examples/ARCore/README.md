# Spatial Barcode Scanner with Android ARCore

A spatial barcode scanner that combines [Google ARCore](https://developers.google.com/ar) with [Dynamsoft Capture Vision SDK](https://www.dynamsoft.com/capture-vision/docs/core/introduction/) to detect and track multiple barcodes in 3D space. This project demonstrates advanced AR capabilities including plane detection guidance, position-based duplicate filtering, and 3D anchor collision detection.

https://github.com/user-attachments/assets/03342d5c-f145-4b4a-bd0c-f106c3180fe4

## Key Features

- **Spatial Awareness**: Scan identical barcodes at different physical locations as separate items
- **Collision Detection**: Prevent overlapping AR markers with 3D spatial distance checking
- **Plane Detection Guidance**: Visual feedback ensures stable anchor placement before scanning
- **Format Display**: History panel shows both barcode format and content (e.g., `[QR_CODE] https://example.com`)
- **Adaptive Markers**: Marker size scales based on barcode dimensions
- **Comprehensive History**: Track all scanned barcodes with share functionality
- **Screenshot Capture**: Save AR scene with anchored markers to gallery

## Prerequisites

- An ARCore-compatible Android device running [Google Play Services for AR](https://play.google.com/store/apps/details?id=com.google.ar.core) version 1.24 or later
- Android Studio 4.1 or later
- Kotlin 1.5 or later

## Getting Started

1. **Obtain a License Key**  
   Get a free trial license for Dynamsoft Capture Vision SDK from [here](https://www.dynamsoft.com/customer/license/trialLicense?product=dcv&package=mobile).

2. **Configure the License**  
   Replace the placeholder with your license key in `MainActivity.kt`:
    
    ```kotlin
    LicenseManager.initLicense("YOUR-LICENSE-KEY", this) { isSuccessful, e ->
        runOnUiThread {
            if (!isSuccessful) {
                e?.printStackTrace()
                Log.e(TAG, "Failed to verify the license: $e")
            }
        }
    }
    ```

3. **Build and Run**  
   - Open the project in Android Studio
   - Connect your ARCore-compatible device
   - Build and run the app

4. **Usage Instructions**  
   - Move your device slowly until the status banner turns green ("Surface detected")
   - Tap the **Scan** button to detect barcodes
   - AR markers will be anchored to detected barcodes
   - Tap **History** to view all scanned barcodes with their formats
   - Tap **Save** to capture a screenshot of the AR scene
   - Tap **Clear** to remove all anchors and reset


## Blog

[Building a Spatial Barcode Scanner with ARCore and Dynamsoft Capture Vision](https://www.dynamsoft.com/codepool/augmented-reality-arcore-barcode-qr-detection.html)

## Resources

- [Google ARCore Documentation](https://developers.google.com/ar)
- [Dynamsoft Capture Vision SDK Documentation](https://www.dynamsoft.com/capture-vision/docs/core/introduction/)
- [ARCore Supported Devices](https://developers.google.com/ar/devices)

