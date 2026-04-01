# Android Document Scanner

A professional document scanning app powered by **Dynamsoft Capture Vision SDK**.

## Features

- **Auto & Manual Capture**: Smart quad stabilization detects stable document boundaries and auto-captures, or tap to capture manually. Direct capture falls back to a raw frame when no document quad is detected.
- **Configurable Stabilization**: Adjust IoU threshold, area delta, frame count via in-app settings.
- **Gallery Import**: Load images from your device gallery for document detection and normalization.
- **Multi-Page Scanning**: Capture multiple pages in a single session with a thumbnail preview bar.
- **Edit Quad**: Drag document boundary corners on the original image to adjust the crop region, then apply perspective correction.
- **Retake & Continue**: Retake replaces the current page in-place; Continue Shooting goes back to the camera to add more pages.
- **Image Filters**: Apply Color, Grayscale, or Binary filters per page.
- **Rotate**: Rotate document images by 90 degrees.
- **Drag-and-Drop Reorder**: Sort captured pages by drag-and-drop.
- **PDF Export**: Save all pages as a multi-page PDF and view/share with any PDF reader.
- **Image Export**: Save all pages as individual JPEG images to the device gallery (Pictures/DocScanner).

## Prerequisites
- Dynamsoft License (Optional for trial, see [Dynamsoft Customer Portal](https://www.dynamsoft.com/customer/license/trialLicense/?product=dcv&package=cross-platform)). Set the license key in `ScannerFragment.java`.
   
    ```java
    LicenseManager.initLicense("LICENSE-KEY", (isSuccess, error) -> {
        if (!isSuccess && error != null) {
            error.printStackTrace();
        }
    });
    ```

## Getting Started

1. Clone the repository.
2. Open the project in Android Studio.
3. Build and run the app on your physical device.

## Dependencies

- `com.dynamsoft:capturevisionbundle:3.2.5000`


