import { registerPlugin } from '@capacitor/core';

/**
 * A decoded barcode returned by the Dynamsoft Capture Vision native SDK.
 */
export interface BarcodeResult {
  /** Decoded barcode text. */
  text: string;
  /** Barcode format enum, e.g. `BF_QR_CODE`. */
  format: string;
  /** Human readable format, e.g. `QR_CODE`. */
  formatString: string;
  /** Four corner points in the coordinate space of the scanned frame. */
  points: Array<{ x: number; y: number }>;
  /** Optional base64 JPEG of the frame the barcode was decoded from. */
  frameBase64?: string;
}

export interface InitLicenseOptions {
  /** Dynamsoft Capture Vision license key. */
  license: string;
}

export interface InitLicenseResult {
  success: boolean;
  message: string;
}

export interface ScanFileOptions {
  /**
   * Local file URI or absolute path of the image to decode.
   * Both `file://` URIs and raw absolute paths are accepted.
   */
  uri: string;
}

export interface ScanResult {
  results: BarcodeResult[];
}

/**
 * Bridge to the in-app Capacitor plugin implemented in
 * `android/.../BarcodeScannerNativePlugin.java` and `ios/App/App/BarcodeScannerNativePlugin.swift`.
 *
 * Both entry points run the Dynamsoft Capture Vision **native** SDK (Android AAR / iOS framework).
 * No JavaScript SDK and no third-party plugin is involved.
 */
export interface BarcodeScannerNativePlugin {
  /** Initialize the Dynamsoft license. Called once before any scanning. */
  initLicense(options: InitLicenseOptions): Promise<InitLicenseResult>;

  /**
   * Open the full-screen native camera scanner.
   * The promise resolves with every barcode decoded in the confirmed frame,
   * and rejects when the user cancels.
   */
  startScan(): Promise<ScanResult>;

  /**
   * Open the system photo picker and decode the selected still image.
   * This is the "file" data source.
   */
  scanFromGallery(): Promise<ScanResult>;

  /**
   * Decode an image that the web layer already has a path for.
   * This is the "file" data source without going through the picker.
   */
  scanFile(options: ScanFileOptions): Promise<ScanResult>;
}

export const BarcodeScannerNative = registerPlugin<BarcodeScannerNativePlugin>('BarcodeScannerNative');

/** Put your own license key here. The key below is the public Dynamsoft trial key. */
export const LICENSE_KEY =
  'DLS2eyJoYW5kc2hha2VDb2RlIjoiMjAwMDAxLTE2NDk4Mjk3OTI2MzUiLCJvcmdhbml6YXRpb25JRCI6IjIwMDAwMSIsInNlc3Npb25QYXNzd29yZCI6IndTcGR6Vm05WDJrcEQ5YUoifQ==';
