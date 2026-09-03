import { registerPlugin } from '@capacitor/core';

/**
 * Parsed MRZ / ID-card fields returned by the Dynamsoft native scanner.
 */
export interface IdFields {
  /** TD1 / TD2 / TD3 document type, e.g. `ID`, `PASSPORT` or `VISA`. */
  documentType: string;
  /** Human readable name, e.g. `Smith, John`. */
  name: string;
  sex: string;
  age: string;
  documentNumber: string;
  issuingState: string;
  nationality: string;
  dateOfBirth: string;
  dateOfExpiry: string;
}

/** Result of one successful scan (camera or file data source). */
export interface IdScanResult {
  fields: IdFields;
  /** Base64 JPEG of the detected portrait photo, when available. */
  portraitBase64?: string;
  /** Base64 JPEG of the deskewed document image, when available. */
  documentImageBase64?: string;
}

export interface ScanFileOptions {
  /**
   * Local file URI or absolute path of the image to process.
   * `content://`, `file://` and raw absolute paths are accepted.
   */
  uri: string;
}

export interface InitLicenseResult {
  success: boolean;
  message: string;
}

/**
 * Bridge to the in-app Capacitor plugin. Both entry points run the Dynamsoft
 * MRZ Scanner **native** SDK (Android AAR / iOS framework) - no JavaScript SDK.
 */
export interface IdScannerNativePlugin {
  /** Initialize the Dynamsoft license. Called once before any scanning. */
  initLicense(): Promise<InitLicenseResult>;

  /**
   * Open the full-screen native camera scanner. The promise resolves with the
   * MRZ fields (and portrait) of the confirmed document, and rejects when the
   * user cancels.
   */
  startScan(): Promise<IdScanResult>;

  /** Open the system photo picker and process the selected document image. */
  scanFromGallery(): Promise<IdScanResult>;

  /** Process an image that the web layer already has a path for. */
  scanFile(options: ScanFileOptions): Promise<IdScanResult>;
}

export const IdScannerNative = registerPlugin<IdScannerNativePlugin>('IdScannerNative');

/** Dynamsoft Capture Vision trial license shared by the example projects. */
export const LICENSE_KEY =
  'DLS2eyJoYW5kc2hha2VDb2RlIjoiMjAwMDAxLTE2NDk4Mjk3OTI2MzUiLCJvcmdhbml6YXRpb25JRCI6IjIwMDAwMSIsInNlc3Npb25QYXNzd29yZCI6IndTcGR6Vm05WDJrcEQ5YUoifQ==';
