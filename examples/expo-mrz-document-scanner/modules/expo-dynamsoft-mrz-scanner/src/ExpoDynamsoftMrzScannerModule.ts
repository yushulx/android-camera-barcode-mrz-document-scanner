import { NativeModule, requireNativeModule } from 'expo';

/** Parsed MRZ document fields, as displayed by the example UI. */
export type IdFieldMap = {
  /** PASSPORT, ID or VISA. */
  documentType: string;
  /** Full name formatted as `LAST, FIRST`. */
  name: string;
  /** Male / Female / —. */
  sex: string;
  documentNumber: string;
  issuingState: string;
  nationality: string;
  /** YYYY-MM-DD or — when absent. */
  dateOfBirth: string;
  dateOfExpiry: string;
  /** Computed age or — when absent. */
  age: string;
  [key: string]: string;
};

export type InitLicenseResult = {
  success: boolean;
  message: string;
};

export type ScanFileOptions = {
  /**
   * Local file URI or absolute path of the image to parse.
   * `content://`, `file://` and raw absolute paths are accepted.
   */
  uri: string;
};

export type IdScanResult = {
  fields: IdFieldMap;
  /** JPEG data URL of the cropped portrait photo, when one was detected. */
  portraitBase64?: string;
  /** JPEG data URL of the deskewed document image, when one was detected. */
  documentImageBase64?: string;
};

/**
 * Bridge to the in-app Expo native module implemented in
 * `android/.../ExpoDynamsoftMrzScannerModule.kt` and
 * `ios/ExpoDynamsoftMrzScannerModule.swift`.
 *
 * Both entry points run the Dynamsoft MRZ Scanner **native** SDK
 * (Android AAR / iOS framework). No JavaScript SDK is involved.
 */
declare class ExpoDynamsoftMrzScannerModule extends NativeModule<{}> {
  /** Initialize the Dynamsoft license. Called once before any scanning. */
  initLicense(license?: string): Promise<InitLicenseResult>;

  /**
   * Open the full-screen native camera scanner. Resolves with the parsed MRZ
   * fields (plus portrait and deskewed-document images when detected) once the
   * user confirms, and rejects (message `canceled`) when the user cancels.
   */
  startScan(): Promise<IdScanResult>;

  /** Open the system photo picker and parse the selected still image. */
  scanFromGallery(): Promise<IdScanResult>;

  /** Parse an image that the JS layer already has a path for. */
  scanFile(options: ScanFileOptions): Promise<IdScanResult>;
}

export default requireNativeModule<ExpoDynamsoftMrzScannerModule>(
  'ExpoDynamsoftMrzScanner'
);
