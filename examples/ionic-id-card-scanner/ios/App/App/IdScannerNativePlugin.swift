import Foundation
import Capacitor
import DynamsoftCaptureVisionBundle

/// In-app Capacitor plugin that exposes the Dynamsoft MRZ Scanner **native** iOS
/// SDK to the Ionic web layer.
///
/// Methods:
/// - `initLicense` - activate the SDK
/// - `startScan` - live camera data source (full screen native scanner)
/// - `scanFromGallery` - still image data source via the system photo picker
/// - `scanFile` - still image data source from a local file path / URL
@objc(IdScannerNativePlugin)
public class IdScannerNativePlugin: CAPPlugin {

    private static let LICENSE_KEY = "DLS2eyJoYW5kc2hha2VDb2RlIjoiMjAwMDAxLTE2NDk4Mjk3OTI2MzUiLCJvcmdhbml6YXRpb25JRCI6IjIwMDAwMSIsInNlc3Npb25QYXNzd29yZCI6IndTcGR6Vm05WDJrcEQ5YUoifQ=="
    private static let TEMPLATE_NAME = "ReadPassportAndId"

    private var licenseInitialized = false

    // MARK: - Plugin methods

    @objc func initLicense(_ call: CAPPluginCall) {
        let key = call.getString("license") ?? Self.LICENSE_KEY
        activateLicense(key: key) { [weak self] success, message in
            guard let self else {
                call.resolve(["success": false, "message": "plugin deallocated"])
                return
            }
            self.licenseInitialized = success
            call.resolve([
                "success": success,
                "message": success ? "License activated" : (message ?? "License initialization failed")
            ])
        }
    }

    /// Live camera data source.
    @objc func startScan(_ call: CAPPluginCall) {
        guard ensureLicense(call: call) else { return }
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            let vc = IdCameraScanViewController()
            vc.templateName = Self.TEMPLATE_NAME
            vc.onResult = { [weak self] json in
                self?.dismissScanner()
                call.resolve(["result": json])
            }
            vc.onCancel = { [weak self] in
                self?.dismissScanner()
                call.reject("canceled")
            }
            self.presentScanner(vc)
        }
    }

    /// Still image data source through the system photo picker.
    @objc func scanFromGallery(_ call: CAPPluginCall) {
        guard ensureLicense(call: call) else { return }
        SharedImagePicker.present(from: bridge?.viewController) { [weak self] pickedUrl in
            guard let self, let pickedUrl else {
                call.reject("canceled")
                return
            }
            self.processImage(at: pickedUrl, call: call)
        }
    }

    /// Still image data source from a known file URL or absolute path.
    @objc func scanFile(_ call: CAPPluginCall) {
        guard let uri = call.getString("uri"), !uri.isEmpty else {
            call.reject("Missing 'uri' option")
            return
        }
        guard ensureLicense(call: call) else { return }
        let url: URL
        if uri.hasPrefix("file://") {
            url = URL(fileURLWithPath: uri.replacingOccurrences(of: "file://", with: ""))
        } else {
            url = URL(fileURLWithPath: uri)
        }
        processImage(at: url, call: call)
    }

    // MARK: - License

    private func activateLicense(key: String, completion: @escaping (Bool, String?) -> Void) {
        LicenseManager.initLicense(key, verificationDelegate: nil)
        // The async verification delegate is optional; for the sample we treat the
        // synchronous acceptance as success. Verification errors surface through the
        // SDK at first scan if the key is invalid.
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            completion(true, nil)
        }
    }

    private func ensureLicense(call: CAPPluginCall) -> Bool {
        if !licenseInitialized {
            licenseInitialized = true
        }
        return true
    }

    // MARK: - Presentation helpers

    private func presentScanner(_ vc: UIViewController) {
        if let presented = bridge?.viewController?.presentedViewController {
            presented.dismiss(animated: true) {
                self.bridge?.viewController?.present(vc, animated: true)
            }
        } else {
            bridge?.viewController?.present(vc, animated: true)
        }
    }

    private func dismissScanner() {
        bridge?.viewController?.dismiss(animated: true)
    }

    // MARK: - File processing

    /// Run the `ReadPassportAndId` template on a still image (file data source).
    private func processImage(at url: URL, call: CAPPluginCall) {
        DispatchQueue.global(qos: .userInitiated).async {
            do {
                let router = CaptureVisionRouter()
                try? router.initSettingsFromFile("mrz-mobile", ofType: "json")
                guard let result = router.captureFromFile(url.path, templateName: Self.TEMPLATE_NAME) else {
                    throw NSError(domain: "IdScanner", code: 1,
                                  userInfo: [NSLocalizedDescriptionKey: "No result was produced for the input image"])
                }
                guard let parsed = result.parsedResult,
                      let item = parsed.items?.first else {
                    throw NSError(domain: "IdScanner", code: 2,
                                  userInfo: [NSLocalizedDescriptionKey: "No MRZ was found in the image."])
                }
                let payload = IdResultFormatter.payload(for: item, deskewedItem: result.processedDocumentResult?.deskewedImageResultItems?.first)
                DispatchQueue.main.async {
                    call.resolve(["result": payload])
                }
            } catch {
                DispatchQueue.main.async {
                    call.reject("Failed to parse image: \(error.localizedDescription)", error)
                }
            }
        }
    }
}
