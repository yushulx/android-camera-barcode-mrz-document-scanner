# React Native Barcode Scanner with Real-time Overlays

A React Native application that provides real-time barcode scanning with visual overlays using the [Dynamsoft Barcode Reader SDK](https://www.npmjs.com/package/dynamsoft-barcode-reader-bundle-react-native). The app displays detected barcode information and draws precise contours around detected barcodes using their corner coordinates.

https://github.com/user-attachments/assets/72a7383d-c46c-4f07-813c-4bd8bfd4f211

## Features

- **Real-time Barcode Detection**: Scans multiple barcode formats (QR codes, Code 128, EAN, UPC, etc.)
- **Visual Overlays**: Displays barcode text and format information positioned at detected locations
- **Precise Contours**: Draws green outline contours with red corner dots around detected barcodes
- **Stable Rendering**: Anti-flashing overlay system for smooth user experience
- **Coordinate Transformation**: Accurate mapping between camera coordinates and screen coordinates
- **Multiple Barcode Support**: Simultaneously detects and displays multiple barcodes

## Prerequisites

- React Native development environment set up
- A [30-day free trial license](https://www.dynamsoft.com/customer/license/trialLicense/?product=dcv&package=cross-platform) for Dynamsoft Barcode Reader.

## Installation

1. **Clone the repository**
2. **Install dependencies**
   ```bash
   npm install
   # or
   yarn install
   ```

3. **License Configuration**
   
   The app uses a Dynamsoft license key. For production use, replace the license in `src/App.tsx`:
   ```typescript
   const License = 'YOUR_LICENSE_KEY_HERE';
   ```

## Running the App

### Android
```bash
npx react-native run-android
```

### iOS
```bash
npx react-native run-ios
```

![react-native barcode scanner](https://www.dynamsoft.com/codepool/img/2025/07/react-native-barcode-scanner-dynamsoft.jpg)

**Note**: For optimal performance and camera access, run on a physical device rather than an emulator.


## Configuration Options

### Barcode Detection Settings

The app is configured to detect all standard barcode formats. To modify detection settings, update the preset template:

```typescript
cvr.startCapturing(EnumPresetTemplate.PT_READ_BARCODES)
```

### Overlay Appearance

Customize overlay styles in the `StyleSheet`:

```typescript
const styles = StyleSheet.create({
  contourLine: {
    backgroundColor: '#00FF00', // Green contour lines
  },
  cornerDot: {
    backgroundColor: '#FF0000', // Red corner dots
  },
  barcodeOverlay: {
    backgroundColor: 'rgba(0, 0, 0, 0.7)', // Semi-transparent background
  },
});
```
