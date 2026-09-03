import Foundation
import Capacitor
import DynamsoftCaptureVisionBundle

/// In-app Capacitor plugin that exposes the Dynamsoft Capture Vision **native**
/// iOS SDK to the Ionic web layer.
///
/// Methods:
/// - `initLicense` - activate the SDK
/// - `startScan` - live camera data source (full screen native scanner)
/// - `scanFromGallery` - still image data source via the system photo picker
/// - `scanFile` - still image data source from a local file path / URL
@objc(BarcodeScannerNative)
public class BarcodeScannerNativePlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "BarcodeScannerNative"
    public let jsName = "BarcodeScannerNative"
    public let pluginMethods: [CAPPluginMethod] = [
        .init(name: "initLicense", returnType: CAPPluginReturnPromise),
        .init(name: "startScan", returnType: CAPPluginReturnPromise),
        .init(name: "scanFromGallery", returnType: CAPPluginReturnPromise),
        .init(name: "scanFile", returnType: CAPPluginReturnPromise)
    ]


    private static let LICENSE_KEY = "DLS2eyJoYW5kc2hha2VDb2RlIjoiMjAwMDAxLTE2NDk4Mjk3OTI2MzUiLCJvcmdhbml6YXRpb25JRCI6IjIwMDAwMSIsInNlc3Npb25QYXNzd29yZCI6IndTcGR6Vm05WDJrcEQ5YUoifQ=="
    private static let TEMPLATE_NAME = "ReadBarcodes_Default"

    private var licenseInitialized = false

    @objc func initLicense(_ call: CAPPluginCall) {
        let key = call.getString("license") ?? Self.LICENSE_KEY
        LicenseManager.initLicense(key, verificationDelegate: nil)
        licenseInitialized = true
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
            call.resolve(["success": true, "message": "License activated"])
        }
    }

    /// Live camera data source.
    @objc func startScan(_ call: CAPPluginCall) {
        guard ensureLicense(call: call) else { return }
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            let vc = BarcodeCameraScanViewController()
            vc.onResult = { [weak self] payload in
                self?.dismissScanner()
                call.resolve(["result": payload])
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
        SharedImagePicker.present(from: bridge?.viewController) { [weak self] url in            guard let self else { return }
            guard let url else {
                call.reject("canceled")
                return
            }
            self.decodeImage(at: url, call: call)
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
        decodeImage(at: url, call: call)
    }

    private func ensureLicense(call: CAPPluginCall) -> Bool {
        if !licenseInitialized {
            licenseInitialized = true
        }
        return true
    }

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

    /// Run the barcode template on a still image (file data source).
    private func decodeImage(at url: URL, call: CAPPluginCall) {
        DispatchQueue.global(qos: .userInitiated).async {
            let router = CaptureVisionRouter()
            if let templates = Bundle.allFrameworks.lazy
                .compactMap({ $0.path(forResource: "dbr-bundle-mobile-templates", ofType: "json") })
                .first {
                try? router.initSettingsFromFile(templates)
            }
            let result = router.captureFromFile(url.path, templateName: Self.TEMPLATE_NAME)
            guard let decoded = result.decodedBarcodesResult,
                  let items = decoded.items else {
                DispatchQueue.main.async {
                    call.reject("No barcodes were found in the image.")
                }
                return
            }
            var array: [[String: Any]] = []
            for item in items {
                var entry: [String: Any] = [
                    "text": item.text ?? "",
                    "format": Int(item.format.rawValue),
                    "formatString": item.formatString ?? ""
                ]
                var points: [[String: Any]] = []
                for value in item.location.points {
                    let p = value.cgPointValue
                    points.append(["x": p.x, "y": p.y])
                }
                entry["points"] = points
                array.append(entry)
            }
            DispatchQueue.main.async {
                call.resolve(["results": array])
            }
        }
    }
}
