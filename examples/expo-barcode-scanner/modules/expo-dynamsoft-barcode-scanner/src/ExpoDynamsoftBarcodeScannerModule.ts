import { NativeModule, requireNativeModule } from 'expo';

/** A decoded barcode returned by the Dynamsoft Capture Vision native SDK. */
export type BarcodeResult = {
  /** Decoded barcode text. */
  text: string;
  /** Numeric barcode format enum, e.g. the value of `BF_QR_CODE`. */
  format?: number;
  /** Human readable format, e.g. `QR_CODE`. */
  formatString: string;
  /** Four corner points in the coordinate space of the scanned frame. */
  points: Array<{ x: number; y: number }>;
};

export type InitLicenseResult = {
  success: boolean;
  message: string;
};

export type ScanFileOptions = {
  /**
   * Local file URI or absolute path of the image to decode.
   * `content://`, `file://`, `assets-library://` and raw absolute paths are accepted.
   */
  uri: string;
};

export type ScanResult = {
  results: BarcodeResult[];
};

/**
 * Bridge to the in-app Expo native module implemented in
 * `android/.../ExpoDynamsoftBarcodeScannerModule.kt` and
 * `ios/ExpoDynamsoftBarcodeScannerModule.swift`.
 *
 * Both entry points run the Dynamsoft Capture Vision **native** SDK
 * (Android AAR / iOS framework). No JavaScript SDK is involved.
 */
declare class ExpoDynamsoftBarcodeScannerModule extends NativeModule<{}> {
  /** Initialize the Dynamsoft license. Called once before any scanning. */
  initLicense(license?: string): Promise<InitLicenseResult>;

  /**
   * Open the full-screen native camera scanner. Resolves with every barcode
   * decoded in the confirmed frame, and rejects (message `canceled`) when the
   * user cancels.
   */
  startScan(): Promise<ScanResult>;

  /** Open the system photo picker and decode the selected still image. */
  scanFromGallery(): Promise<ScanResult>;

  /** Decode an image that the JS layer already has a path for. */
  scanFile(options: ScanFileOptions): Promise<ScanResult>;
}

export default requireNativeModule<ExpoDynamsoftBarcodeScannerModule>(
  'ExpoDynamsoftBarcodeScanner'
);