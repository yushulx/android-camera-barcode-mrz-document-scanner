import ExpoModulesCore
import DynamsoftCaptureVisionBundle

/// In-app Expo module that exposes the Dynamsoft MRZ Scanner **native** iOS SDK
/// to the React Native layer.
///
/// Methods:
/// - `initLicense` - activate the SDK
/// - `startScan` - live camera data source (full screen native scanner)
/// - `scanFromGallery` - still image data source via the system photo picker
/// - `scanFile` - still image data source from a local file path / URL
public class ExpoDynamsoftMrzScannerModule: Module {

  private enum Constants {
    static let licenseKey =
      "DLS2eyJoYW5kc2hha2VDb2RlIjoiMjAwMDAxLTE2NDk4Mjk3OTI2MzUiLCJvcmdhbml6YXRpb25JRCI6IjIwMDAwMSIsInNlc3Npb25QYXNzd29yZCI6IndTcGR6Vm05WDJrcEQ5YUoifQ=="
    static let templateName = "ReadPassportAndId"
  }

  public func definition() -> ModuleDefinition {
    Name("ExpoDynamsoftMrzScanner")

    AsyncFunction("initLicense") { (license: String?, promise: Promise) in
      let key = (license?.isEmpty == false) ? license! : Constants.licenseKey
      LicenseManager.initLicense(key, verificationDelegate: nil)
      promise.resolve([
        "success": true,
        "message": "License activated"
      ])
    }

    // Live camera data source.
    AsyncFunction("startScan") { (promise: Promise) in
      DispatchQueue.main.async {
        guard let host = self.appContext?.utilities?.currentViewController() else {
          promise.reject("NO_VC", "No view controller available to present the scanner")
          return
        }
        let vc = IdCameraScanViewController()
        vc.templateName = Constants.templateName
        vc.onResult = { payload in
          host.dismiss(animated: true)
          promise.resolve(payload)
        }
        vc.onCancel = {
          host.dismiss(animated: true)
          promise.reject("canceled", "User canceled the scan")
        }
        self.presentScanner(vc, from: host)
      }
    }

    // Still image data source through the system photo picker.
    AsyncFunction("scanFromGallery") { (promise: Promise) in
      DispatchQueue.main.async {
        guard let host = self.appContext?.utilities?.currentViewController() else {
          promise.reject("NO_VC", "No view controller available to present the picker")
          return
        }
        SharedImagePicker.present(from: host) { [weak self] url in
          guard let url else {
            promise.reject("canceled", "User canceled the picker")
            return
          }
          self?.processImage(at: url, promise: promise)
        }
      }
    }

    // Still image data source from a known file URL or absolute path.
    AsyncFunction("scanFile") { (uri: String, promise: Promise) in
      guard !uri.isEmpty else {
        promise.reject("MISSING_URI", "Missing 'uri' option")
        return
      }
      let url: URL
      if uri.hasPrefix("file://") {
        url = URL(fileURLWithPath: uri.replacingOccurrences(of: "file://", with: ""))
      } else if uri.hasPrefix("assets-library://") || uri.hasPrefix("ph://") {
        promise.reject("UNSUPPORTED_URI", "Photo library URIs are not supported by scanFile; use scanFromGallery instead.")
        return
      } else {
        url = URL(fileURLWithPath: uri)
      }
      processImage(at: url, promise: promise)
    }
  }

  private func presentScanner(_ vc: UIViewController, from host: UIViewController) {
    if let presented = host.presentedViewController {
      presented.dismiss(animated: true) {
        host.present(vc, animated: true)
      }
    } else {
      host.present(vc, animated: true)
    }
  }

  /// Run the `ReadPassportAndId` template on a still image (file data source).
  private func processImage(at url: URL, promise: Promise) {
    DispatchQueue.global(qos: .userInitiated).async {
      do {
        let router = CaptureVisionRouter()
        if let templatePath = Self.mrzTemplatePath() {
          try? router.initSettingsFromFile(templatePath)
        }
        let result = router.captureFromFile(url.path, templateName: Constants.templateName)
        guard let parsed = result.parsedResult,
              let item = parsed.items?.first else {
          throw NSError(domain: "ExpoMrzScanner", code: 2,
                        userInfo: [NSLocalizedDescriptionKey: "No MRZ was found in the image. Make sure the Machine-Readable Zone is clearly visible."])
        }
        var payload: [String: Any] = ["fields": IdResultFormatter.fieldMap(for: item)]
        if let deskewed = result.processedDocumentResult?.deskewedImageResultItems?.first,
           let data = deskewed.imageData,
           let url = IdResultFormatter.jpegDataUrl(from: data) {
          payload["documentImageBase64"] = url
        }
        DispatchQueue.main.async {
          promise.resolve(payload)
        }
      } catch {
        DispatchQueue.main.async {
          promise.reject("PARSE_FAILED", "Failed to parse image: \(error.localizedDescription)")
        }
      }
    }
  }

  /// Locate the MRZ template shipped inside DynamsoftMRZScannerBundle.framework.
  private static func mrzTemplatePath() -> String? {
    (Bundle.allFrameworks + Bundle.allBundles).lazy
      .compactMap { $0.path(forResource: "mrz-mobile", ofType: "json") }
      .first
  }
}
